#!/usr/bin/env python3
"""Timed, fail-fast Android IME stress with identity, state, input and resource evidence."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import threading
import time
import traceback
import xml.etree.ElementTree as ET

P = argparse.ArgumentParser()
P.add_argument('--serial', default='192.168.3.75:5555')
P.add_argument('--seconds', type=float, default=10800)
P.add_argument('--interval', type=float, default=3)
P.add_argument('--sample-seconds', type=float, default=30)
P.add_argument('--sha256', default='811f6dec318dfae45d1776fb5bf717f86f2f143e61daceb1ac567f615ed9c227')
P.add_argument('--output', type=Path, required=True)
A = P.parse_args()
A.output.mkdir(parents=True, exist_ok=False)
PKG = 'com.newlink.kemi.kboard'
EDITOR = 'com.newlink.notes/.NotesActivity'
MAIN_ACTIVITY = PKG + '/org.fcitx.fcitx5.android.ui.main.MainActivity'
MAIN_IME = PKG + '/org.fcitx.fcitx5.android.input.FcitxInputMethodService'
RELAY = PKG + '/org.fcitx.fcitx5.android.input.DisplaySwitchInputMethodService'
ADB = ['adb', '-s', A.serial]
stop = threading.Event()
faults = []
lock = threading.Lock()
started = time.monotonic()
last_action = 0.0
phase = 'preflight'
round_no = 0
operations = Counter()
failed_operations = Counter()
samples = []
initial_pid = None
initial_start_ticks = None
initial_path = None
last_cpu = None
last_cpu_time = None
log_proc = None


def wall():
    return datetime.now(timezone.utc).astimezone().isoformat(timespec='seconds')


def put(name, value):
    with lock:
        p = A.output / name
        tmp = p.with_suffix(p.suffix + '.tmp')
        tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2))
        tmp.replace(p)


def journal(name, value):
    with lock:
        with (A.output / name).open('a') as f:
            f.write(json.dumps(value, ensure_ascii=False) + '\n')


def fail(reason):
    with lock:
        if reason not in faults:
            faults.append(reason)
    stop.set()


def run(*args, timeout=30):
    return subprocess.check_output(ADB + list(args), text=True, timeout=timeout).strip()


def healthy():
    if stop.is_set():
        raise RuntimeError('; '.join(faults) or 'stopped')
    pid = run('shell', 'pidof', PKG)
    if initial_pid is not None and pid != initial_pid:
        raise AssertionError(f'process restart/missing: {initial_pid} -> {pid}')


def ime():
    return run('shell', 'dumpsys', 'input_method')


def is_visible(dump):
    return ('mInputShown=true' in dump,
            'mWindowVisible=true' in dump and 'mDecorViewVisible=true' in dump)


def wait_visible(expected):
    deadline = time.monotonic() + 12
    while True:
        healthy()
        dump = ime()
        if is_visible(dump) == (expected, expected):
            (A.output / 'latest-ime.txt').write_text(dump)
            token = re.search(r'mCurToken=([^\n]+)', dump)
            journal('states.jsonl', dict(at=wall(), elapsed_s=round(time.monotonic()-started, 3),
                phase=phase, round=round_no, expected_visible=expected,
                actual_visible=is_visible(dump), token=token.group(1) if token else None,
                input_started='mInputStarted=true' in dump))
            return dump
        if time.monotonic() >= deadline:
            (A.output / 'failed-ime.txt').write_text(dump)
            raise AssertionError(f'visibility expected={expected} actual={is_visible(dump)}')
        time.sleep(.2)


def quiet_hidden():
    end = time.monotonic() + A.interval
    while time.monotonic() < end:
        healthy()
        dump = ime()
        if is_visible(dump) != (False, False):
            (A.output / 'late-rebound-ime.txt').write_text(dump)
            raise AssertionError('late keyboard rebound after hide/focus loss')
        time.sleep(.3)


def ui():
    run('shell', 'uiautomator', 'dump', '/sdcard/kboard-stress-ui.xml')
    xml = run('shell', 'cat', '/sdcard/kboard-stress-ui.xml')
    (A.output / 'latest-ui.xml').write_text(xml)
    return ET.fromstring(xml)


def text_is(expected):
    nodes = [n for n in ui().iter('node')
             if n.attrib.get('resource-id') == 'com.newlink.notes:id/search_input']
    assert len(nodes) == 1, 'Notes search editor missing (focus/window interference)'
    actual = nodes[0].attrib.get('text', '')
    # Empty EditText exports its hint as accessibility text on this ROM.
    if actual == '搜索便签':
        actual = ''
    assert actual.lower() == expected, f'input mismatch: {actual!r} != {expected!r}'


def action(name, command, verify=None):
    global last_action
    healthy()
    time.sleep(max(0, A.interval - (time.monotonic() - last_action)))
    healthy()
    last_action = time.monotonic()
    t = time.monotonic()
    row = dict(at=wall(), elapsed_s=round(t-started, 3), round=round_no, phase=phase, action=name)
    try:
        run(*command)
        if verify:
            verify()
        healthy()
    except BaseException as e:
        failed_operations[name] += 1
        row.update(success=False, error=str(e))
        journal('operations.jsonl', row)
        raise
    operations[name] += 1
    row.update(success=True, duration_s=round(time.monotonic()-t, 3), pid=initial_pid)
    journal('operations.jsonl', row)
    heartbeat()


def show():
    ui()  # wait for the real editor/window transition to reach UI idle
    action('show', ['shell', 'input', 'tap', '300', '180'], lambda: wait_visible(True))


def exercise_input():
    text_is('')
    # Use only real IME touch keys: injected printable KeyEvents deliberately switch the
    # app to physical-keyboard/floating-candidate mode and would invalidate touch coordinates.
    action('soft-key-q', ['shell', 'input', 'tap', '270', '700'], lambda: text_is('q'))
    action('soft-key-w', ['shell', 'input', 'tap', '450', '700'], lambda: text_is('qw'))
    action('soft-backspace-1', ['shell', 'input', 'tap', '1820', '700'], lambda: text_is('q'))
    action('soft-backspace-2', ['shell', 'input', 'tap', '1820', '700'], lambda: text_is(''))


def screenshot(name):
    with (A.output / name).open('wb') as f:
        subprocess.run(ADB + ['exec-out', 'screencap', '-p'], stdout=f, check=True, timeout=30)


def heartbeat():
    put('heartbeat.json', dict(status='failed' if faults else 'running', at=wall(),
        elapsed_s=round(time.monotonic()-started, 1), target_s=A.seconds,
        phase=phase, round=round_no, pid=initial_pid, successful_actions=sum(operations.values()),
        actions=dict(operations), failed_actions=dict(failed_operations), faults=list(faults)))


def sample():
    global last_cpu, last_cpu_time
    healthy()
    path = run('shell', 'pm', 'path', PKG).removeprefix('package:')
    assert path == initial_path, f'APK replaced: {path}'
    pid = initial_pid
    stat = run('shell', 'su', '0', 'cat', f'/proc/{pid}/stat').rsplit(')', 1)[1].split()
    assert stat[19] == initial_start_ticks, 'PID identity changed'
    status = run('shell', 'su', '0', 'cat', f'/proc/{pid}/status')
    fd = run('shell', 'su', '0', 'ls', f'/proc/{pid}/fd').splitlines()
    mem = run('shell', 'dumpsys', 'meminfo', PKG)
    (A.output / 'latest-meminfo.txt').write_text(mem)
    def number(pattern, source=mem):
        m = re.search(pattern, source, re.M)
        assert m, f'missing metric: {pattern}'
        return int(m.group(1))
    native = re.search(r'^\s*Native Heap\s+(.+)$', mem, re.M).group(1).split()
    ticks = int(stat[11]) + int(stat[12])
    now = time.monotonic()
    cpu = None if last_cpu is None else 100*(ticks-last_cpu)/hz/(now-last_cpu_time)
    last_cpu, last_cpu_time = ticks, now
    window_state = is_visible(ime())
    row = dict(at=wall(), elapsed_s=round(now-started, 3), phase=phase, round=round_no, pid=pid,
               system_visible=window_state[0], window_visible=window_state[1],
               pss_kb=number(r'TOTAL PSS:\s*(\d+)'), rss_kb=number(r'TOTAL RSS:\s*(\d+)'),
               native_pss_kb=int(native[0]), native_alloc_kb=int(native[6]),
               java_pss_kb=number(r'Java Heap:\s*(\d+)'),
               threads=number(r'^Threads:\s*(\d+)', status), fd_count=len(fd),
               cpu_pct_one_core=None if cpu is None else round(cpu, 3),
               views=number(r'Views:\s*(\d+)'), view_roots=number(r'ViewRootImpl:\s*(\d+)'))
    samples.append(row)
    journal('resources.jsonl', row)
    heartbeat()


def sampler():
    while not stop.wait(A.sample_seconds):
        try:
            sample()
        except BaseException as e:
            fail(f'resource/identity monitor: {e}')
            heartbeat()
            return


def capture_logs():
    with (A.output / 'logcat.txt').open('w') as f:
        for line in log_proc.stdout:
            f.write(line)
            if re.search(r'FATAL EXCEPTION|ANR in |am_anr|Window token is not set yet|Input dispatching timed out', line):
                f.flush()
                journal('fatal-lines.jsonl', dict(at=wall(), line=line.rstrip()))
                fail('fatal/ANR/window-token signature in live logcat')
        if not stop.is_set():
            fail('logcat stream ended unexpectedly')


exit_code = 1
start_wall = wall()
try:
    assert run('shell', 'settings', 'get', 'secure', 'default_input_method') == MAIN_IME
    initial_path = run('shell', 'pm', 'path', PKG).removeprefix('package:')
    assert run('shell', 'sha256sum', initial_path).split()[0] == A.sha256, 'Wrong APK hash'
    hz = int(run('shell', 'getconf', 'CLK_TCK'))
    run('shell', 'am', 'start', '-W', '--activity-single-top', '-n', EDITOR)
    show()
    text_is('')
    initial_pid = run('shell', 'pidof', PKG)
    assert initial_pid.isdigit(), 'Expected a single KBoard process'
    initial_start_ticks = run('shell', 'su', '0', 'cat', f'/proc/{initial_pid}/stat').rsplit(')', 1)[1].split()[19]
    # Preflight/launch are not counted toward the requested continuous stress window.
    started, start_wall = time.monotonic(), wall()
    operations.clear()
    put('identity.json', dict(start=start_wall, pid=initial_pid, start_ticks=initial_start_ticks,
                             apk_path=initial_path, apk_sha256=A.sha256, serial=A.serial,
                             device_start=run('shell', 'date', '+%Y-%m-%dT%H:%M:%S%z'),
                             target_s=A.seconds, interval_s=A.interval, cpu_ticks_per_second=hz))
    log_proc = subprocess.Popen(ADB + ['logcat', '-b', 'all', '-v', 'threadtime', '-T', '1'],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    log_thread = threading.Thread(target=capture_logs, daemon=True)
    log_thread.start()
    sample()
    sample_thread = threading.Thread(target=sampler, daemon=True)
    sample_thread.start()
    screenshot('start.png')
    while time.monotonic() - started < A.seconds:
        round_no += 1
        for phase in ('show-hide', 'home-resume', 'focus-switch', 'relay-recreate'):
            if time.monotonic() - started >= A.seconds:
                break
            healthy()
            exercise_input()
            if phase == 'show-hide':
                action('hide', ['shell', 'input', 'keyevent', 'BACK'], lambda: wait_visible(False))
                quiet_hidden()
            elif phase == 'home-resume':
                action('home', ['shell', 'input', 'keyevent', 'HOME'], lambda: wait_visible(False))
                quiet_hidden()
                action('resume-editor', ['shell', 'am', 'start', '-W', '--activity-single-top', '-n', EDITOR])
            elif phase == 'focus-switch':
                action('focus-settings', ['shell', 'am', 'start', '-W', '--activity-single-top', '-n', MAIN_ACTIVITY],
                       lambda: wait_visible(False))
                quiet_hidden()
                action('focus-editor', ['shell', 'am', 'start', '-W', '--activity-single-top', '-n', EDITOR])
            else:
                old_token = re.search(r'mCurToken=([^\n]+)', ime()).group(1)
                def relay_returned():
                    deadline = time.monotonic()+12
                    while run('shell', 'settings', 'get', 'secure', 'default_input_method') != MAIN_IME:
                        healthy()
                        if time.monotonic() >= deadline:
                            raise AssertionError('Relay failed to return')
                        time.sleep(.2)
                action('relay', ['shell', 'ime', 'set', RELAY], relay_returned)
            show()
            if phase == 'relay-recreate':
                new_token = re.search(r'mCurToken=([^\n]+)', ime()).group(1)
                assert old_token != new_token, 'IME token was not recreated'
            journal('cycles.jsonl', dict(at=wall(), round=round_no, phase=phase, success=True))
        if round_no % 10 == 0:
            screenshot(f'round-{round_no:04d}.png')
    healthy()
    action('final-home', ['shell', 'input', 'keyevent', 'HOME'], lambda: wait_visible(False))
    quiet_hidden()
    screenshot('final-hidden.png')
    sample()
    assert run('shell', 'sha256sum', initial_path).split()[0] == A.sha256, 'Final APK changed'
    assert time.monotonic()-started >= A.seconds
    exit_code = 0
except BaseException as e:
    fail(f'{type(e).__name__}: {e}')
    (A.output / 'failure-trace.txt').write_text(traceback.format_exc())
    try:
        (A.output / 'failure-ime.txt').write_text(ime())
        screenshot('failure.png')
    except BaseException:
        pass
finally:
    stop.set()
    if 'sample_thread' in globals():
        sample_thread.join(timeout=35)
    if log_proc:
        log_proc.terminate()
        log_proc.wait(timeout=10)
        log_thread.join(timeout=5)
    try:
        (A.output / 'exit-info.txt').write_text(run('shell', 'dumpsys', 'activity', 'exit-info', PKG))
    except BaseException as e:
        faults.append(f'exit evidence: {e}')
    summary = dict(status='passed' if exit_code == 0 and not faults else 'failed',
        start=start_wall, end=wall(), elapsed_s=round(time.monotonic()-started, 3), target_s=A.seconds,
        pid=initial_pid, apk_sha256=A.sha256, rounds=round_no, successful_actions=dict(operations),
        failed_actions=dict(failed_operations), resource_samples=len(samples), faults=faults)
    put('summary.json', summary)
    put('heartbeat.json', summary)
    print(json.dumps(summary, ensure_ascii=False), flush=True)
raise SystemExit(0 if summary['status'] == 'passed' else 1)

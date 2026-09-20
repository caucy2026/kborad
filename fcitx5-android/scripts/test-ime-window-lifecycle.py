#!/usr/bin/env python3
"""Real-device IME visibility regression. Uses an existing editor; never clears app data."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--editor', default='com.newlink.notes/.NotesActivity')
p.add_argument('--package', default='com.newlink.kemi.kboard')
p.add_argument('--x', type=int, default=300)
p.add_argument('--y', type=int, default=180)
p.add_argument('--cycles', type=int, default=20)
p.add_argument('--interval', type=float, default=3.0, help='Minimum seconds between UI actions')
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
adb = ['adb', '-s', a.serial]
last_action = 0.0


def pace():
    global last_action
    time.sleep(max(0.0, a.interval - (time.monotonic() - last_action)))
    last_action = time.monotonic()


main = a.package + '/org.fcitx.fcitx5.android.input.FcitxInputMethodService'
relay = a.package + '/org.fcitx.fcitx5.android.input.DisplaySwitchInputMethodService'


def run(*args):
    return subprocess.check_output(adb + list(args), text=True, timeout=30)


def visible(expected, label):
    deadline = time.monotonic() + 12
    while True:
        dump = run('shell', 'dumpsys', 'input_method')
        system_shown = 'mInputShown=true' in dump
        # mIsInputViewShown is a cached layout decision and stays true after hide.
        view_shown = 'mWindowVisible=true' in dump and 'mDecorViewVisible=true' in dump
        if system_shown == expected and view_shown == expected:
            (a.output / (label + '.txt')).write_text(dump)
            return
        if time.monotonic() >= deadline:
            (a.output / (label + '-FAILED.txt')).write_text(dump)
            raise AssertionError(f'{label}: system={system_shown}, view={view_shown}')
        time.sleep(.15)


def show(label):
    # Wait for the editor/IME transition to reach UI idle before injecting a new tap.
    run('shell', 'uiautomator', 'dump', '/sdcard/kboard-regression-ui.xml')
    pace()
    run('shell', 'input', 'tap', str(a.x), str(a.y))
    visible(True, label)


def screenshot(name):
    with (a.output / (name + '.png')).open('wb') as f:
        subprocess.run(adb + ['exec-out', 'screencap', '-p'], stdout=f, check=True, timeout=30)


original_ime = run('shell', 'settings', 'get', 'secure', 'default_input_method').strip()
assert original_ime == main, f'Expected active KBoard before test, got {original_ime}'
start = run('shell', 'date', '+%Y-%m-%dT%H:%M:%S%z').strip()
rows = []
log_file = (a.output / 'logcat.txt').open('w')
log = subprocess.Popen(adb + ['logcat', '-v', 'threadtime', '-T', '1'], stdout=log_file, stderr=subprocess.STDOUT)
error = None
try:
    run('shell', 'am', 'start', '-W', '-n', a.editor)
    show('initial-shown')
    screenshot('initial-shown')
    pid = run('shell', 'pidof', a.package).strip()
    for phase in ('show-hide', 'home-resume', 'relay-recreate'):
        for cycle in range(1, a.cycles + 1):
            label = f'{phase}-{cycle:02d}'
            pace()
            if phase == 'show-hide':
                run('shell', 'input', 'keyevent', 'BACK')
                visible(False, label + '-hidden')
            elif phase == 'home-resume':
                run('shell', 'input', 'keyevent', 'HOME')
                visible(False, label + '-home-hidden')
                pace()
                run('shell', 'am', 'start', '-W', '-n', a.editor)
            else:
                run('shell', 'ime', 'set', relay)
                deadline = time.monotonic() + 12
                while run('shell', 'settings', 'get', 'secure', 'default_input_method').strip() != main:
                    if time.monotonic() >= deadline:
                        raise AssertionError('Relay did not return to primary IME')
                    time.sleep(.15)
            show(label + '-shown')
            current_pid = run('shell', 'pidof', a.package).strip()
            assert current_pid == pid, f'Unexpected process restart: {pid} -> {current_pid}'
            row = dict(phase=phase, cycle=cycle, pid=current_pid)
            rows.append(row)
            (a.output / 'progress.json').write_text(json.dumps(rows, indent=2))
            print(json.dumps(row), flush=True)
        screenshot(phase + '-final')
    run('shell', 'input', 'keyevent', 'HOME')
    visible(False, 'final-home-hidden')
    # Observe a quiet interval after focus loss to catch delayed unwanted reopening.
    for i in range(10):
        time.sleep(.2)
        visible(False, f'quiet-{i:02d}')
    screenshot('final-home-hidden')
except Exception as exc:
    error = str(exc)
    raise
finally:
    run('shell', 'ime', 'set', original_ime)
    log.terminate()
    log.wait(timeout=10)
    log_file.close()
    text = (a.output / 'logcat.txt').read_text(errors='replace')
    failures = re.findall(r'^.*(?:FATAL EXCEPTION|ANR in |am_anr|Window token is not set yet|Input dispatching timed out).*$', text, re.M)
    summary = dict(start=start, end=run('shell', 'date', '+%Y-%m-%dT%H:%M:%S%z').strip(),
                   completed=len(rows), error=error, fatal_anr_lines=failures)
    (a.output / 'summary.json').write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary), flush=True)
    if failures and error is None:
        raise AssertionError('FATAL/ANR found; inspect saved logcat')

import json
import re
import subprocess
import time
from pathlib import Path

OUT = Path('test-reports/late-session-20-20260916')
OUT.mkdir(parents=True, exist_ok=False)
PREFIX = ['adb', '-s', '172.21.16.24:5555']
PKG = 'com.newlink.kemi.kboard'
MAIN = PKG + '/org.fcitx.fcitx5.android.input.FcitxInputMethodService'
RELAY = PKG + '/org.fcitx.fcitx5.android.input.DisplaySwitchInputMethodService'

def adb(*args):
    return subprocess.check_output(PREFIX + list(args), timeout=25).decode('utf-8', 'replace').strip()

def shot(name):
    adb('shell', 'screencap', '-p', '/sdcard/kboard-stress.png')
    adb('pull', '/sdcard/kboard-stress.png', str(OUT / name))

def show(round_no, phase):
    adb('shell', 'am', 'start', '-W', '--display', '0', '-n', 'com.newlink.notes/.NotesActivity')
    time.sleep(0.6)
    for attempt in range(8):
        adb('shell', 'input', '-d', '0', 'tap', '300', '185')
        time.sleep(0.3)
        state = adb('shell', 'dumpsys', 'input_method')
        if 'mInputShown=true' in state and 'mCurMethodId=' + MAIN in state:
            (OUT / f'{round_no:02d}-{phase}-ime.txt').write_text(state, encoding='utf-8')
            return
    shot(f'{round_no:02d}-{phase}-failed.png')
    raise RuntimeError(f'Keyboard not shown at round {round_no} {phase}')

original = adb('shell', 'settings', 'get', 'secure', 'default_input_method')
initial_pid = adb('shell', 'pidof', PKG)
marker = 'LATE_SESSION_20_' + str(time.time_ns())
results = []
error = None
with (OUT / 'logcat.txt').open('w', encoding='utf-8') as log:
    collector = subprocess.Popen(PREFIX + ['logcat', '-v', 'threadtime'], stdout=log)
    try:
        adb('shell', 'log', '-t', 'KBoardRegression', marker)
        for n in range(1, 21):
            show(n, 'before')
            adb('shell', 'input', '-d', '0', 'tap', '270', '700')
            if n in (1, 20):
                shot(f'{n:02d}-typed.png')
            adb('shell', 'input', '-d', '0', 'tap', '1820', '700')
            adb('shell', 'input', '-d', '0', 'keyevent', '4')
            adb('shell', 'am', 'start', '-W', '--display', '0', '-n',
                'com.android.settings/.Settings' if n % 2 else 'com.newlink.kemi.kbrowser/.App')
            adb('shell', 'ime', 'set', RELAY)
            time.sleep(0.3)
            adb('shell', 'ime', 'set', MAIN)
            show(n, 'after')
            pid = adb('shell', 'pidof', PKG)
            results.append({'round': n, 'shown_before': True, 'shown_after': True, 'pid': pid})
            print(f'round {n}/20, keyboard shown twice, PID {pid}', flush=True)
            if pid != initial_pid:
                raise RuntimeError('KBoard PID changed')
        shot('final-keyboard.png')
    except Exception as exc:
        error = str(exc)
    finally:
        adb('shell', 'ime', 'set', original)
        time.sleep(0.5)
        collector.terminate()
        collector.wait(timeout=10)

text = (OUT / 'logcat.txt').read_text(encoding='utf-8', errors='replace')
offset = text.find(marker)
text = text[offset:] if offset >= 0 else text
signatures = {key: text.count(key) for key in (
    'FcitxDaemon$DisconnectedException', 'Required value was null', 'ANR in ' + PKG)}
signatures['kboard_fatal'] = len(re.findall(r'FATAL EXCEPTION:[\s\S]{0,400}Process: com\.newlink\.kemi\.kboard', text))
summary = dict(rounds=results, error=error, initial_pid=initial_pid,
               final_pid=adb('shell', 'pidof', PKG), signatures=signatures,
               lifecycle_releases=len(re.findall(r'KBoardImeLifecycle: release service', text)),
               original_ime=original, final_ime=adb('shell', 'settings', 'get', 'secure', 'default_input_method'))
summary['passed'] = len(results) == 20 and error is None and not any(signatures.values()) and offset >= 0 and summary['original_ime'] == summary['final_ime']
(OUT / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False, indent=2))
raise SystemExit(0 if summary['passed'] else 1)

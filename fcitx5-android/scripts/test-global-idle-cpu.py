#!/usr/bin/env python3
"""Regression gate for CPU churn after hiding KBoard's global keyboard.

Precondition: the global keyboard is visible on the target display. The test verifies
that its aquarium renderer is alive, hides the IME with HOME, then measures process
CPU from /proc. It never clears application data or changes the default IME.
"""

import argparse
import json
import subprocess
import time
from pathlib import Path


PACKAGE = "com.newlink.kemi.kboard"
EXPECTED_IME = PACKAGE + "/org.fcitx.fcitx5.android.input.FcitxInputMethodService"


def adb(serial: str, *args: str, binary: bool = False):
    result = subprocess.run(
        ["adb", "-s", serial, *args],
        check=True,
        capture_output=True,
        text=not binary,
    )
    return result.stdout


def ime_state(serial: str) -> dict:
    output = adb(serial, "shell", "dumpsys", "input_method")
    return {
        "input_shown": "mInputShown=true" in output,
        "window_visible": "mWindowVisible=true" in output,
    }


def process_snapshot(serial: str, pid: str) -> tuple[int, float]:
    output = adb(serial, "shell", "su", "0", "cat", f"/proc/{pid}/stat", "/proc/uptime")
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    fields = lines[0][lines[0].rfind(")") + 2 :].split()
    return int(fields[11]) + int(fields[12]), float(lines[1].split()[0])


def thread_names(serial: str, pid: str) -> str:
    return adb(serial, "shell", "su", "0", "ps", "-T", "-p", pid)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--display", type=int, default=2)
    parser.add_argument("--sample-seconds", type=int, default=30)
    parser.add_argument("--interval", type=float, default=3.0)
    parser.add_argument("--max-mean-cpu", type=float, default=5.0)
    args = parser.parse_args()

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    failures = []
    samples = []

    default_ime = adb(args.serial, "shell", "settings", "get", "secure", "default_input_method").strip()
    if default_ime != EXPECTED_IME:
        failures.append(f"unexpected default IME: {default_ime}")
    pid = adb(args.serial, "shell", "pidof", PACKAGE).strip()
    if not pid:
        failures.append("KBoard process is not running")
    initial_state = ime_state(args.serial)
    initial_threads = thread_names(args.serial, pid) if pid else ""
    if not initial_state["input_shown"] or not initial_state["window_visible"]:
        failures.append("global keyboard is not visible at test start")
    if "kboard-aquarium" not in initial_threads:
        failures.append("aquarium renderer is not active at test start")

    (output_dir / "global-visible.png").write_bytes(
        adb(args.serial, "exec-out", "screencap", "-d", str(args.display), "-p", binary=True)
    )
    adb(args.serial, "shell", "input", "-d", str(args.display), "keyevent", "KEYCODE_HOME")
    time.sleep(3.0)
    hidden_state = ime_state(args.serial)
    if hidden_state["input_shown"] or hidden_state["window_visible"]:
        failures.append(f"IME remained visible after HOME: {hidden_state}")
    if pid and "kboard-aquarium" in thread_names(args.serial, pid):
        failures.append("aquarium renderer survived after IME hide")

    deadline = time.monotonic() + args.sample_seconds
    previous = process_snapshot(args.serial, pid) if pid else None
    while previous and time.monotonic() < deadline:
        time.sleep(min(args.interval, max(0.0, deadline - time.monotonic())))
        current = process_snapshot(args.serial, pid)
        delta_ticks = current[0] - previous[0]
        delta_time = current[1] - previous[1]
        cpu = delta_ticks / delta_time if delta_time > 0 else 0.0  # Android CLK_TCK is 100.
        samples.append(round(cpu, 3))
        previous = current

    current_pid = adb(args.serial, "shell", "pidof", PACKAGE).strip()
    if current_pid != pid:
        failures.append(f"process restarted: {pid} -> {current_pid}")
    mean_cpu = round(sum(samples) / len(samples), 3) if samples else None
    if mean_cpu is None or mean_cpu >= args.max_mean_cpu:
        failures.append(f"hidden mean CPU {mean_cpu}% is not below {args.max_mean_cpu}%")
    (output_dir / "after-home.png").write_bytes(
        adb(args.serial, "exec-out", "screencap", "-d", str(args.display), "-p", binary=True)
    )

    result = {
        "serial": args.serial,
        "package": PACKAGE,
        "pid": pid,
        "defaultIme": default_ime,
        "initialImeState": initial_state,
        "hiddenImeState": hidden_state,
        "sampleSeconds": args.sample_seconds,
        "intervalSeconds": args.interval,
        "cpuPercentOfOneCore": samples,
        "meanCpuPercentOfOneCore": mean_cpu,
        "maxCpuPercentOfOneCore": max(samples) if samples else None,
        "failures": failures,
        "status": "PASS" if not failures else "FAIL",
    }
    (output_dir / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print(
        "APP_RELEASE_CASE_RESULT "
        + json.dumps(
            {
                "case_id": "KBOARD-GLOBAL-HIDE-IDLE-CPU",
                "status": result["status"],
                "reason": "; ".join(failures) if failures else "renderer stopped and hidden CPU stayed below gate",
                "evidence": [str(output_dir / "result.json"), str(output_dir / "global-visible.png")],
            },
            ensure_ascii=False,
        )
    )
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())

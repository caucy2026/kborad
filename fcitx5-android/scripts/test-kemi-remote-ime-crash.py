#!/usr/bin/env python3
import argparse
import json
import re
import subprocess
import time
from pathlib import Path


KBOARD = "com.newlink.kemi.kboard"
REMOTE = "com.newlinksz.kemi.remote"
REMOTE_ACTIVITY = f"{REMOTE}/com.carriez.flutter_hbb.MainActivity"
MAIN_ACTIVITIES = (
    "com.newlink.notes/.NotesActivity",
    "com.newlink.kemi.kbrowser/.App",
    "com.android.settings/.Settings",
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--rounds", type=int, default=100)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)

    adb_prefix = ["adb", "-s", args.serial]

    def adb(*command: str, timeout: int = 20) -> str:
        completed = subprocess.run(
            [*adb_prefix, *command],
            check=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
        )
        return completed.stdout.strip()

    adb("get-state")
    baseline = {
        "package": adb("shell", "dumpsys", "package", KBOARD),
        "default_ime": adb("shell", "settings", "get", "secure", "default_input_method"),
    }
    (args.output / "baseline-package.txt").write_text(
        baseline["package"], encoding="utf-8"
    )

    log_path = args.output / "logcat.txt"
    with log_path.open("w", encoding="utf-8") as log_file:
        logcat = subprocess.Popen(
            [*adb_prefix, "logcat", "-v", "threadtime"],
            stdout=log_file,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        try:
            marker = f"START rounds={args.rounds} timestamp_ns={time.time_ns()}"
            adb("shell", "log", "-t", "KBoardStress", marker)
            adb("shell", "am", "force-stop", REMOTE)
            adb("shell", "am", "start", "--display", "2", "-n", REMOTE_ACTIVITY)
            time.sleep(1.0)
            initial_pid = adb("shell", "pidof", KBOARD)
            pid_changes = []
            shown_checks = 0

            for round_number in range(1, args.rounds + 1):
                adb("shell", "am", "start", "--display", "2", "-n", REMOTE_ACTIVITY)
                time.sleep(0.25)
                adb("shell", "input", "-d", "2", "tap", "220", "1130")
                adb("shell", "input", "-d", "2", "tap", "900", "260")
                time.sleep(0.20)
                if round_number % 10 == 0:
                    visible_state = adb("shell", "dumpsys", "input_method")
                    if "mInputShown=true" in visible_state:
                        shown_checks += 1
                adb("shell", "input", "-d", "0", "text", f"{round_number:04d}5678")
                adb("shell", "input", "-d", "0", "keyevent", "67")
                adb("shell", "input", "-d", "0", "keyevent", "67")
                adb("shell", "input", "-d", "0", "keyevent", "67")
                adb("shell", "input", "-d", "0", "keyevent", "4")
                time.sleep(0.10)
                adb("shell", "input", "-d", "2", "tap", "1868", "120")
                time.sleep(0.15)
                adb(
                    "shell",
                    "am",
                    "start",
                    "--display",
                    "0",
                    "-n",
                    MAIN_ACTIVITIES[(round_number - 1) % len(MAIN_ACTIVITIES)],
                )
                adb("shell", "am", "start", "--display", "2", "-n", REMOTE_ACTIVITY)
                time.sleep(0.25)

                current_pid = adb("shell", "pidof", KBOARD)
                if not current_pid:
                    raise RuntimeError(f"KBoard process missing after round {round_number}")
                if initial_pid and current_pid != initial_pid:
                    pid_changes.append(
                        {"round": round_number, "from": initial_pid, "to": current_pid}
                    )
                    initial_pid = current_pid

                if round_number % 10 == 0:
                    print(f"round={round_number}/{args.rounds} pid={current_pid}", flush=True)
            adb("shell", "log", "-t", "KBoardStress", "END")
        finally:
            logcat.terminate()
            try:
                logcat.wait(timeout=5)
            except subprocess.TimeoutExpired:
                logcat.kill()
                logcat.wait(timeout=5)

    log_text = log_path.read_text(encoding="utf-8", errors="replace")
    marker_offset = log_text.rfind(f"KBoardStress: {marker}")
    if marker_offset >= 0:
        log_text = log_text[marker_offset:]
    signatures = {
        "fatal": len(
            re.findall(
                rf"FATAL EXCEPTION:[\s\S]{{0,400}}Process: {re.escape(KBOARD)}",
                log_text,
            )
        ),
        "disconnected_exception": log_text.count("FcitxDaemon$DisconnectedException"),
        "anr": log_text.count(f"ANR in {KBOARD}"),
        "process_died": len(
            re.findall(rf"Process {re.escape(KBOARD)} .* has died", log_text)
        ),
        "retired_layout_drop": log_text.count(
            "Drop layout switch for retired KeyboardWindow"
        ),
    }
    final_state = adb("shell", "dumpsys", "input_method")
    summary = {
        "rounds": args.rounds,
        "default_ime_before": baseline["default_ime"],
        "default_ime_after": adb(
            "shell", "settings", "get", "secure", "default_input_method"
        ),
        "final_pid": adb("shell", "pidof", KBOARD),
        "pid_changes": pid_changes,
        "periodic_input_shown_checks": shown_checks,
        "signatures": signatures,
        "final_ime_has_kboard": KBOARD in final_state,
    }
    (args.output / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 1 if any(signatures[key] for key in ("fatal", "disconnected_exception", "anr")) else 0


if __name__ == "__main__":
    raise SystemExit(main())

# KBoard real multi-touch harness

Two test-only APKs keep the served `InputConnection` and `UiAutomation` injector in separate packages.

Build without touching product APKs:

```bash
export KBOARD_REAL_INPUT_BUILD_ROOT=/Volumes/ORICO/kemi-build-cache/app-release-gate/kboard/android/real-input-harness
./gradlew :tools:real-input-receiver:assembleDebug :tools:real-input-injector:assembleDebug --no-daemon
```

Install and launch the receiver on the target display:

```bash
adb -s SERIAL install -r "$KBOARD_REAL_INPUT_BUILD_ROOT/receiver/outputs/apk/debug/real-input-receiver-debug.apk"
adb -s SERIAL uninstall com.newlink.kboard.testinjector || true
adb -s SERIAL install "$KBOARD_REAL_INPUT_BUILD_ROOT/injector/outputs/apk/debug/real-input-injector-platform.apk"
adb -s SERIAL shell am start --display 2 -n com.newlink.kboard.testreceiver/.ReceiverActivity --ez clear true
```

Calibrate key centers from the current 1920x1280 screenshot. Inject a two-pointer chord, then read JSONL evidence:

```bash
adb -s SERIAL shell am instrument -w -r \
  -e display 2 -e points '0:CTRL_X:CTRL_Y,1:C_X:C_Y' \
  -e holdMs 120 -e stepMs 24 -e rounds 100 \
  com.newlink.kboard.testinjector/.MultiTouchInstrumentation
adb -s SERIAL exec-out run-as com.newlink.kboard.testreceiver cat files/events.jsonl
```

`points` preserves the listed pointer IDs and order. The injector emits `DOWN`, ordered `POINTER_DOWN`, reverse `POINTER_UP`, and final `UP`; `resultJson` reports every round and injection acceptance. The receiver records key action, key code, meta state, repeat count, timestamps, display and accepted result. `commitText`/`setComposingText` records contain only length and cursor, never text.

The preferred injector artifact is platform-signed because Android 12 vendor builds expose the
event display setter as a hidden API on different runtime classes. The implementation probes the
runtime `MotionEvent` public/declared method, then its class hierarchy field as a compatibility
fallback. Injection or setup failure finishes instrumentation with code `1`; success uses `-1`.

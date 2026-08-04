---
name: kemi-android-validation
description: 'Build, install, launch, and verify the KEMI fcitx5 Android keyboard on a connected device. Use when changing IME UI, ASR, permissions, settings localization, gettext PO/MO catalogs, native Fcitx behavior, or when the user asks to push an APK and check logs/screenshots.'
argument-hint: '[general|asr|localization]'
---

# KEMI Android Validation

Use this workflow after runtime-affecting changes. Do not stop at `BUILD SUCCESSFUL`.

## 1. Preflight

From `fcitx5-android/`:

```bash
git status --short
adb devices
```

Confirm at least one device is in `device` state. Do not clear app data unless the user explicitly approves it.

## 2. Build

Prefer the repository bootstrap command:

```bash
./scripts/assemble-debug-local.sh
```

If dependencies are already prepared, use the verified environment described in [../../../fcitx5-android-port-plan.md](../../../fcitx5-android-port-plan.md).

Require `BUILD SUCCESSFUL`, then locate the APK instead of guessing its versioned filename:

```bash
find app/build/outputs/apk/debug -name '*.apk' -type f -print
```

## 3. Install And Restart

```bash
adb install -r <debug-apk>
adb shell am force-stop org.fcitx.fcitx5.android.debug
adb logcat -c
adb shell am start -n \
  org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.ui.main.MainActivity
```

When validating keyboard input, also confirm and set the IME if needed:

```bash
adb shell ime enable \
  org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService
adb shell ime set \
  org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService
```

## 4. Exercise The Changed Behavior

Choose the relevant checks.

### General

- Perform the actual user workflow on the device.
- Capture a screenshot with `adb exec-out screencap -p > /tmp/kemi-<case>.png`.
- Use `uiautomator dump` when exact visible labels matter.

### Localization

Verify both Android and Fcitx localization paths:

```bash
adb shell getprop persist.sys.locale
adb logcat -d | grep -A5 'Starting fcitx with'
xxd -l 8 app/src/main/assets/usr/share/locale/zh_CN/LC_MESSAGES/fcitx5.mo
```

Expected Fcitx locale is `zh_CN:zh`. A valid little-endian GNU MO starts with `de12 0495`. Open all three KEMI entries: Global Options, Input Methods, and Addons. Check nested dynamic settings, not only page titles.

### ASR

- Open a real text field so the IME is visible.
- Test short tap, valid hold, movement cancellation, permission denial, offline behavior, and a successful recognition when the environment permits.
- Confirm the voice icon enters and leaves the green active state.
- Verify final text is committed only at the intended session boundary.
- Follow the protocol and risk controls in [../../../iflytek_asr_interface_doc.md](../../../iflytek_asr_interface_doc.md).

## 5. Inspect Logs

```bash
adb logcat -d | grep -iE \
  'FATAL EXCEPTION|AndroidRuntime|ActivityNotFoundException|SecurityException|cleartext|WebSocket|ASR|Starting fcitx|locale='
```

A passing result requires no relevant crash signature, expected state changes in logs, and matching screenshot/UI evidence.

## 6. Report And Record

Summarize:

- Build and install result
- APK path and package used
- Exact workflow exercised
- Screenshot/UI text evidence
- Relevant log evidence
- Untested scenarios or external blockers

For completed project changes, update the canonical [../../../cl.md](../../../cl.md). Do not create a second changelog inside `fcitx5-android/`.

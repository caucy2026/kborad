# KBoard Agent Guidelines

## Start Here

- Read [cl.md](cl.md) for the latest verified changes and open issues.
- Read [fcitx5-android-port-plan.md](fcitx5-android-port-plan.md) for build, deployment, and native translation troubleshooting.
- Read [iflytek_asr_interface_doc.md](iflytek_asr_interface_doc.md) before changing ASR behavior, permissions, authentication, or network policy.
- Use the upstream [fcitx5-android/README.md](fcitx5-android/README.md) for standard dependencies and submodule setup.

## Repository Boundaries

- `/Users/newlink/kemi/kboard` is the documentation and GitHub backup repository.
- `fcitx5-android/` is also an independent local Git repository. The root repository tracks a source snapshot while its `.gitignore` hides the nested checkout locally.
- Before committing, run `git status --short` in both repositories. Commit app development in `fcitx5-android` first. Update the root snapshot only when the user explicitly asks to back up or publish code.
- In the root repository, stage backup files explicitly. Do not use broad `git add -A`, remove the nested `.git`, or include unrelated `.gitignore` and submodule state.
- [cl.md](cl.md) is the only changelog. Do not create another `fcitx5-android/cl.md`.

## Architecture

- Android app, IME service, settings, and ASR UI: `fcitx5-android/app/`.
- Fcitx native engine and libraries: `fcitx5-android/lib/`.
- Optional input-method plugins: `fcitx5-android/plugin/`.
- Build modules are declared in `fcitx5-android/settings.gradle.kts`; native entry points are under each module's `src/main/cpp/`.
- Treat upstream submodules as external code. Avoid changing them unless the task specifically requires an upstream/native fix.

## Build And Validation

Run from `fcitx5-android/`:

```bash
./scripts/assemble-debug-local.sh
```

If local dependencies are already prepared, the verified direct build is documented in [fcitx5-android-port-plan.md](fcitx5-android-port-plan.md). Locate the APK from `app/build/outputs/apk/debug/`; do not assume a fixed hash-based filename.

- A successful build is not sufficient for runtime changes. Continue through install, process restart, real interaction, screenshot or UI dump, and filtered logcat verification.
- Debug package: `org.fcitx.fcitx5.android.debug`.
- Main activity: `org.fcitx.fcitx5.android.ui.main.MainActivity`.
- IME service: `org.fcitx.fcitx5.android.input.FcitxInputMethodService`.
- Do not run `pm clear` without approval; it deletes keyboard settings and user data.
- Use the `kemi-android-validation` skill for the full device loop.

## Localization

- Android static labels belong in `app/src/main/res/values*/strings.xml`.
- Input-method and addon display names returned by Fcitx use `NameLocalization.kt` when no native localized name is available.
- Dynamic Fcitx configuration labels come from gettext PO/MO catalogs, not Android strings.
- Never copy a `.po` file and rename it `.mo`. A valid little-endian GNU MO begins with bytes `de 12 04 95`; validate representative translations before deployment.
- Generated assets under `app/src/main/assets/usr/` and build directories are not source changes.

## ASR Safety

- Preserve the complete permission and policy chain: `RECORD_AUDIO`, `INTERNET`, `ACCESS_NETWORK_STATE`, `VoicePermissionActivity`, and the domain-scoped network security config.
- Keep hold threshold, movement cancellation, permission throttling, offline precheck, lifecycle cleanup, and Chinese error mapping together when modifying voice interaction.
- Diagnose from device logs. Specifically check for `ActivityNotFoundException`, `SecurityException`, cleartext policy failures, WebSocket errors, and `FATAL EXCEPTION`.

## Documentation

- Record completed user-facing behavior, root cause, changed files, executable validation, and remaining risk in [cl.md](cl.md).
- Put reusable ASR protocol/debugging details in [iflytek_asr_interface_doc.md](iflytek_asr_interface_doc.md).
- Put build, NDK, gettext, submodule, APK, and device deployment findings in [fcitx5-android-port-plan.md](fcitx5-android-port-plan.md).

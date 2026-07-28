# KEMI Keyboard Preview V1

## Scope

This record covers the KEMI keyboard changes prepared on 2026-07-27 and 2026-07-28. The work adds an in-IME floating mode and a six-row global desktop keyboard while retaining the normal docked keyboard as the default. It does not replace the Fcitx input engine, candidate data, or Android IME registration flow.

## Recent Change Record

| Commit | Change |
| --- | --- |
| `624c238e` | Added the six-row global desktop keyboard and reproducible local build entry point. |
| `98117fed` | Removed custom controls that overlapped the Android hide-keyboard and IME-switch controls. |
| `1a257f6c` | Refined desktop key sizing, operation buttons, language switching, voice-button styling, and full-width behavior. |
| `a6d506fb` | Moved the real Fcitx candidate row from the top of the IME surface to the area immediately above F1. |
| `9d9b60cb` | Put the real Fcitx preedit string above the candidate row, producing `ni hao ni`, candidates, then F1. |

The 2026-07-28 follow-up removes the final two layers of desktop horizontal padding, restores visible ASR progress in global mode, requires a validated network before enabling voice input, and enlarges the floating resize controls.

## Global Desktop Keyboard

- `DesktopKeyboard` is a dedicated six-row keyboard: function keys, number/symbols, QWERTY, ASDF, ZXCV, and modifiers/navigation.
- The bottom row contains one wider Ctrl key, Option, a language icon, a language-labelled space key, Command, and arrow keys. The right Option key was removed.
- The language icon and `Ctrl+Space` both dispatch `LangSwitchAction`. The space label follows the active Fcitx input method and displays `English` or `拼 音`.
- Letter labels remain uppercase while unshifted actions send lowercase characters. Shifted symbols and modifier state are handled by `DesktopKeyboard` before dispatch to the normal `BaseKeyboard` action path.
- The desktop keyboard now consumes the complete IME width. `InputView` contributes zero desktop side padding and `DesktopKeyboard` contributes zero internal horizontal padding; only normal per-key spacing remains. Row height is still derived from the 15-key-unit layout width, so all six rows scale together without independent stretching.
- The operation area below the keys keeps the system hide-keyboard and IME-switch zones clear. Exit-global-mode is aligned with the Option key and voice input is aligned with the Command key.

### Fcitx composition placement

The global keyboard does not maintain a separate text buffer. It displays the existing Fcitx event data:

- `InputPanelEvent.preedit` is rendered by `PreeditUi`.
- `CandidateListEvent` is rendered by `KawaiiBarComponent` and the existing horizontal candidate component.
- `DesktopKeyboard.firstRowTopOnScreen()` provides the actual F1 row coordinate after layout.
- `InputView.updateDesktopCompositionPosition()` places candidates directly above F1 and places preedit directly above candidates.

The resulting visual order is `ni hao ni` -> `你好你 / 你好 / ...` -> F1. Positioning is recomputed after keyboard or preedit layout changes instead of relying on an estimated keyboard height.

## Voice Input In Global Mode

Normal and global voice buttons share the same `voiceInputGestureCallback` and the same lazily created `IflytekAsrClient`. The global `ToolButton` uses its native `CustomGestureView` dispatch path, so Down and Up behavior matches the normal keyboard instead of manually translating raw `MotionEvent` objects.

The ASR state flow is:

1. Down verifies `RECORD_AUDIO` permission and validated network availability, cancels stale work, and calls `IflytekAsrClient.start()`.
2. `Starting`, `Listening`, and `Finishing` update the existing `IdleUi` transcript with connecting, listening, and finalizing feedback.
3. In global mode the idle Kawaii bar remains hidden during normal typing, but becomes visible above F1 while ASR is non-idle. This fixes the prior state where ASR connected successfully but all progress and partial text were invisible.
4. Up calls `IflytekAsrClient.stop()` and the final result is committed through the active `InputConnection`.

Voice availability requires both `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_VALIDATED` on Wi-Fi, cellular, Ethernet, or VPN. `ConnectivityManager.NetworkCallback` refreshes both normal and global controls when capabilities change. Without a validated network the microphone is disabled, uses 0.38 alpha and `#777777`, and cannot start ASR.

## Mode Switching

- The keyboard starts docked. `floating_keyboard` is persisted with a default of `false`.
- The existing toolbar now contains a floating-keyboard control. Its normal icon enters floating mode; its active icon carries a diagonal slash and returns to docked mode.
- The toolbar control remains available in floating mode. Undo, redo, cursor movement, and clipboard buttons are hidden in that state to keep the compact toolbar focused.
- `FcitxInputMethodService.toggleFloatingKeyboard()` delegates the mode switch to `InputView`; `KeyboardWindow` selects `FloatingText` only for text input, while number and phone inputs continue using the number keyboard.
- Voice input behavior remains tied to the editor capability: regular editors retain voice input and password editors retain the hide-keyboard action.

## Floating Panel

- Floating mode uses an independent four-row `FloatingLayout`, not a scaled copy of the docked layout.
- The panel has a 24 dp rounded outline, clipping, and 8 dp elevation. Its preedit area follows the same width and position as the keyboard panel.
- Saved dimensions are expressed as screen-relative percentages. Width is constrained to 35-65 percent of the display, and height to 70-110 percent of the configured keyboard height. New installations start at 42 percent width and 92 percent height.
- A bottom-center 112 by 48 dp drag zone moves the panel. Position is bounded to the IME surface and stored as normalized X/Y values so it can be restored after the view is recreated.
- Releasing the drag zone within 28 dp of the bottom docks the keyboard. Releasing higher saves the floating position.
- The resize button uses a 112 by 48 dp touch target. It exposes four 48 dp corner targets; 12 dp internal padding keeps the resize artwork at its original visual scale while doubling the draggable edge area. Dragging a handle resizes within the saved percentage limits, then hides the handles and persists the result on release.
- The floating-only hide button is a transparent line-chevron. It is a root-level overlay aligned with the panel's lower-left edge and offset down by 12 dp, which keeps it visible outside the rounded clipping boundary.

## Key Layout And Reuse Rules

Both layouts intentionally use the same reuse policy for letter keys.

| Row | Docked keyboard | Floating keyboard |
| --- | --- | --- |
| First row | `Q-P` retain the right-upper digits `1-0`, swipe digit entry, and existing letter popup behavior. | `Q-P` retain the right-upper digits `1-0` and existing reuse behavior. |
| Second row | `A-L` are single-action letter keys. They have no right-upper punctuation, swipe action, or popup keyboard. | `A-L` are single-action letter keys with the same restrictions. |
| Third row | `Z-M` are single-action letter keys. Independent punctuation and emoji keys remain separate controls. | `Z-M` are single-action letter keys; backspace remains at the right. |
| Bottom row | Existing docked symbols, emoji, language, space, cursor, and layout controls stay in their original arrangement. | Emoji, language, centered space, `?123`, and return are used; the language key is always visible. |

`PlainAlphabetKey` implements the second- and third-row rule with `Appearance.Text` and a single press action. It deliberately has no alternate text, swipe action, preview, or popup. Caps state and the uppercase preference still transform both standard and plain alphabet keys.

## Visual Resources And Labels

- `bkg_floating_keyboard_handle.xml`: visible drag line background.
- `ic_floating_keyboard_24.xml` and `ic_floating_keyboard_docked_24.xml`: enter/exit floating mode toolbar icons.
- `ic_keyboard_arrow_down_24.xml`: floating panel hide chevron.
- `ic_resize_24.xml` and `ic_resize_corner_24.xml`: resize toggle and corner-handle artwork.
- `KeyView` renders the compact floating `?123` and return keys as 48 dp circular controls. The punctuation key uses the alternate-key color; return uses the accent color.
- English and Simplified Chinese resources provide labels for floating, docked, move, and resize actions.

## Files Changed

- `app/src/main/java/org/fcitx/fcitx5/android/data/prefs/AppPrefs.kt`: floating mode, dimensions, and position preferences.
- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`: mode-toggle entry point.
- `app/src/main/java/org/fcitx/fcitx5/android/input/InputView.kt`: panel geometry, dragging, resizing, clipping, docking, and hide control.
- `app/src/main/java/org/fcitx/fcitx5/android/input/bar/KawaiiBarComponent.kt`: toolbar callback wiring and voice/hide preservation.
- `app/src/main/java/org/fcitx/fcitx5/android/input/bar/ui/idle/ButtonsBarUi.kt`: floating toolbar state and control visibility.
- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyDefPreset.kt`: configurable alphabet text size, plain alphabet keys, and compact layout-switch styling.
- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyView.kt`: circular punctuation and return rendering.
- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyboardWindow.kt`: floating text layout selection.
- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/DesktopKeyboard.kt`: six-row desktop layout, modifier handling, language labels, screen-coordinate anchors, and width-driven scaling.
- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/TextKeyboard.kt`: dedicated compact layout and the first-row-only reuse policy in both modes.
- `app/src/main/res/drawable/`: floating, docked, hide, resize, corner, and drag-line drawables.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-zh-rCN/strings.xml`: accessibility labels.

## Build From GitHub

The repository has fixed Git submodule revisions. Clone the backup branch, initialize them recursively, then use the project build entry point:

```shell
git clone --branch kboard-preview-v1 --single-branch \
    git@github.com:caucy2026/kborad.git kemi-keyboard
cd kemi-keyboard
./scripts/assemble-debug-local.sh
```

The entry point synchronizes and initializes all recursive Git submodules, downloads and installs the pinned KDE Extra CMake Modules 6.9.0 under `.local-deps/`, supplies its `ECM_DIR` to Gradle, and creates lightweight local `msgfmt` and `msgmerge` wrappers required by the native build. The ECM download uses a temporary file, retries transient network failures, and stops the build cleanly if dependency preparation cannot complete.

The generated debug APK is written beneath `app/build/outputs/apk/debug/`. Its filename and `BuildConfig` include the checked-out Git commit through `git describe` and `git rev-parse HEAD`. A build from a later backup commit therefore has a different hash in its filename and metadata even when the application source tree is unchanged; that metadata difference is expected and does not change the keyboard implementation.

## Validation

The following was run from a new clone of GitHub backup commit `fe87b20cc1e67ae0772f356e180648c2a99d10d3` after deleting its `.local-deps/` directory:

```shell
git submodule update --init --recursive
./scripts/assemble-debug-local.sh
```

All 19 direct and nested submodules were checked out at the commits recorded by the repository. ECM 6.9.0 was downloaded from scratch, native code was configured and built, and the build completed with `BUILD SUCCESSFUL` in 51 seconds. The produced APK was:

```text
app/build/outputs/apk/debug/org.fcitx.fcitx5.android-fe87b20-arm64-v8a-debug.apk
SHA-256: e48e6df08a493c84d72791c97d3a0b118a5c836f605630bf5bee34386e9a8635
```

The prior device build was installed on `192.168.1.6:5555`, and the current IME was explicitly set to:

```text
org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService
```

A Display 0 screenshot confirmed the docked layout: first-row digits remain visible, while the second and third letter rows show no right-upper symbols.

### 2026-07-28 global keyboard validation

The current debug APK was built with `./scripts/assemble-debug-local.sh`, installed on `192.168.0.111:5555`, and selected as:

```text
org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService
```

`InputMethodManager` reported `mCurTokenDisplayId=0` and `mInputShown=true`. Display 0 screenshots verified:

- the first and last desktop keys are approximately 12 px from the screen edges, representing key spacing rather than container padding;
- all six rows remain visible and proportionally scaled;
- `ni hao ni` appears above the Chinese candidate row, which appears directly above F1;
- holding the global microphone displays `正在连接语音服务...` above F1 and changes the microphone to its green active state;
- the enlarged floating resize button and four corner targets are visible without scaling up the corner artwork.

Runtime logs recorded `gesture=Down`, successful iFlytek authentication, `WebSocket started`, `state=Listening`, and `gesture=Up`. No `FATAL EXCEPTION` was recorded. The device uses Wi-Fi ADB and is not rooted, so physically removing network access would also terminate the verification channel; the disabled offline appearance should additionally be checked by disconnecting Internet from the device after installation.

## Startup I/O Optimization

On 2026-07-27, device-encrypted preference synchronization was changed to write only entries whose effective value differs from the destination store. The preference and theme abstractions compare the serialized value before adding it to the `SharedPreferences.Editor`; an empty update returns without creating an editor transaction.

This preserves direct-boot behavior because every missing or stale entry is still written before use, while avoiding redundant XML writes on later process starts. It does not defer Fcitx startup, change the input method layout, alter candidate behavior, or skip `DataManager.sync()`.

Validation on `192.168.1.6:5555` used two consecutive force-stop cold launches. On the second launch, the device-encrypted preference file remained unchanged:

```text
before: 1785106042:4147
after:  1785106042:4147
```

The optimization build completed successfully, Fcitx reached `ReadyEvent` after cold startup, and KEMI was restored as the selected default IME. Activity cold-launch time still varied around 3.9 seconds, so larger cold-start gains require a separately benchmarked Baseline Profile and first-frame trace phase.

## Signed Release APK

The local release workflow is `scripts/assemble-release-local.sh`. It builds the optimized `arm64-v8a` release variant and copies only a signed result to `build/kboard.apk`.

The signing identity is intentionally external to the repository. Gradle receives `SIGN_KEY_FILE` (or `SIGN_KEY_BASE64`), `SIGN_KEY_PWD`, and `SIGN_KEY_ALIAS` through the local environment. The script refuses to produce a delivery artifact when these values are absent.

The 2026-07-27 release build was verified with `apksigner`: APK Signature Schemes v1 and v2 both verified with one RSA-4096 signer. The resulting artifact was:

```text
build/kboard.apk
SHA-256: c5ad1b7b2afd3aa33aacf01ab65b61d85fac64b16e9174efd68b3ef3df1120c9
size: 46,032,515 bytes
package: org.fcitx.fcitx5.android
version: 102 (b5b9be12)
ABI: arm64-v8a
```

Device validation on `192.168.1.6:5555` installed the release APK, enabled its IME ID `org.fcitx.fcitx5.android/.input.FcitxInputMethodService`, and selected it as the default input method. `InputMethodManager` reported the same ID as its current method and an active `FcitxInputMethodService` record in the release process.
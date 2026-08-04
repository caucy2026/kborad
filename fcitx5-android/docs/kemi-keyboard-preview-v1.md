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

## Global Desktop Physical-Key Design

Status: first prototype implemented on 2026-07-28. This section defines both the current prototype and the intended production behavior. It applies only while `DesktopKeyboard` is active. The normal docked keyboard, floating keyboard, candidate items, toolbar, and settings pages keep their current visual and audio behavior.

### Product character

The target is a quiet, low-profile scissor-switch keyboard rather than an exaggerated mechanical gaming keyboard. Every global key should communicate three physical events: the keycap is initially raised, finger pressure drives it into the keyboard bed, and releasing the finger lets it return. The effect must remain clear during fast typing without moving neighboring keys or becoming visually noisy.

The global keyboard should have these characteristics:

- short, crisp travel with restrained rebound;
- stable key spacing and hit targets throughout animation;
- distinct Down and Up sound instead of one repeated system click;
- heavier acoustic response for wide keys while preserving one visual system;
- latched modifiers that look selected, not permanently held down;
- no physical response for disabled controls;
- no behavior or resource change outside global mode.

### Current baseline and isolation boundary

`KeyView` currently moves its inner `appearanceView` down by 2 dp over 35 ms and returns it over 70 ms. `CustomGestureView` plays one system sound on Down through `InputFeedbacks`; the available categories are Standard, SpaceBar, Delete, and Return. There are no custom WAV, OGG, or MP3 key sounds in the project.

The physical design must not replace these defaults globally. The intended boundary is:

- add a default/no-op interaction style and an optional physical interaction style to the shared key view path;
- have `DesktopKeyboard` opt its child keys into the physical style when attached;
- let all other keyboards continue using the existing animation and `InputFeedbacks` behavior;
- give the exit-global and desktop ASR buttons the same optional physical controller only while global mode is active;
- restore default delegates when global mode exits or a view is detached.

### Keycap construction

The outer touch view remains fixed and owns the complete hit target. Only the inner appearance surface moves. A desktop keycap is visually composed of:

1. a fixed black keyboard bed visible through the key gaps;
2. a darker side wall that represents key travel;
3. a top face containing the label or icon;
4. a subtle top-edge highlight and bottom-edge shadow that establish height.

The top face, text, alternate symbol, and icon move as one unit. Width, height, margins, constraints, and measured row height never animate. Scale animation is excluded because it changes the perceived key gaps and does not resemble a key moving vertically in a switch.

### Motion specification

| Token | Proposed value | Purpose |
| --- | --- | --- |
| Resting travel | 3 dp | Visible side-wall height at rest. |
| Pressed travel | 2.5-3 dp | Makes the keycap appear flush with the keyboard bed. |
| Down duration | 45-55 ms | Immediate response without snapping. |
| Up duration | 95-120 ms | Faster than a decorative animation but long enough to read as rebound. |
| Up overshoot | 0.3-0.5 dp | Small spring return; must not look bouncy. |
| Resting shadow | approximately 3 dp | Matches visible travel. |
| Pressed shadow | 0-0.5 dp | Side wall and shadow nearly disappear at bottom-out. |

Down uses a fast-out curve. Up uses a critically damped or slightly under-damped spring with no more than one visible overshoot. A new Down event during Up cancels the return and starts from the current position, preventing jumps during fast typing.

Required event behavior:

- `ACTION_DOWN`: cancel return animation, play Down feedback, and move to bottom-out;
- `ACTION_MOVE`: remain pressed while inside touch slop; leaving the key starts release feedback once;
- `ACTION_UP`: play Up feedback once and return to rest;
- `ACTION_CANCEL`: use the same visual return as Up and never leave a stuck key;
- focus loss, mode exit, or view detach: reset immediately to rest without sound;
- repeat keys: remain visually down for the complete hold and return only on final Up or Cancel.

### Surface states

| State | Visual behavior | Audio behavior |
| --- | --- | --- |
| Resting | Raised face, visible side wall, normal label color. | None. |
| Pressed | Face lowered, shadow compressed, surface slightly darker. | One Down sound. |
| Releasing | Face returns with restrained spring motion. | One quieter Up sound. |
| Latched modifier | Raised face with persistent active outline or indicator. | Normal Down/Up only when toggled. |
| Disabled | Gray, reduced alpha, no travel or highlight. | No sound. |
| ASR active | Button returns to rest after release; microphone remains green by state. | Release sound only after recording stops. |

Ctrl, Option, Shift, and Command are toggled modifiers in the current desktop layout. Their selected state must be independent from `isPressed`: after the finger releases, the keycap rises and an active outline or small indicator remains. This avoids depicting a latched modifier as a physically jammed key.

### Key-family tuning

All six rows share the same travel and timing. Differences are limited to sound body and selected-state treatment.

| Family | Included keys | Physical treatment |
| --- | --- | --- |
| Standard | Letters, digits, symbols, Esc, F1-F12 | Base travel and standard acoustic body. |
| Wide control | Tab, Caps, Shift, Ctrl, Option, Command | Same travel, slightly heavier sound. |
| Space | Language-labelled space key | Same travel, lower and longer body resonance. |
| Delete | Backspace | Tight Down, quiet repeat tick, separate Up. |
| Return | Enter | Heavier Down than Standard, controlled Up. |
| Navigation | Four arrows and language icon | Standard travel, lighter utility sound. |
| Operation | Exit-global and ASR | Transparent at rest; circular physical surface appears on Down. |

The stacked Up/Down arrow group must animate each arrow independently. Its parent group may not translate or resize. Backspace and arrows remain visually depressed throughout key repeat; repeated actions use a quiet repeat tick rather than replaying the full Down sample.

### Desktop-only acoustic system

The physical sound is a pair of events, not a single click:

- Down combines switch actuation, keycap travel, and bottom-out impact;
- Up represents spring return and top-out, with less energy and a slightly higher tone;
- recommended Down-to-Up loudness ratio is approximately 1:0.45;
- leading silence must be removed so sound begins with the visual Down event;
- samples must be dry, short, and free of room reverb because the device enclosure adds its own resonance.

Proposed sample families:

| Sound family | Down variants | Up variants | Extra sample |
| --- | --- | --- | --- |
| Standard | 3 | 3 | None |
| Wide control | 2 | 2 | None |
| Space | 2 | 2 | None |
| Delete | 2 | 2 | 1 quiet repeat tick |
| Return | 2 | 2 | None |
| Navigation/utility | 2 | 2 | None |

Assets should be self-recorded or have documented redistribution permission. The preferred source format is mono PCM WAV at 48 kHz and 16 bit. Each sample should be approximately 15-60 ms, begin without silence, peak near -6 dBFS, and avoid normalization that makes every key family equally loud.

Playback uses a desktop-only `SoundPool`-based engine preloaded before the global keyboard accepts input. It uses sonification audio attributes, honors the existing sound preference and volume, and allows 8-12 overlapping streams for fast typing. A stream is never stopped merely because the next key starts.

To avoid a machine-gun effect:

- select variants in a non-repeating round-robin or bounded random sequence;
- allow no more than approximately +/-2 percent playback-rate variation;
- allow no more than approximately +/-1 dB level variation;
- optionally pan by key center with a maximum stereo offset of 0.12;
- reduce aggregate gain when many streams overlap to prevent clipping.

No sound file should imitate or be copied from a named commercial keyboard product. The intended reference is the physical behavior of a quiet scissor switch, not a branded sound signature.

### Haptic and audio synchronization

Visual, audio, and haptic feedback start from the same gesture event:

```text
0 ms      Down sound + press haptic + keycap starts descending
45-55 ms  keycap reaches bottom-out
hold      keycap remains down; repeat action may emit quiet repeat ticks
Up        recorder stop if ASR, then Up sound + release haptic + spring return
95-120 ms keycap reaches rest
```

The existing key-up haptic preference remains authoritative. This design does not force release vibration when the user has disabled it. Sound similarly follows the existing enabled, disabled, or system-following preference.

### ASR audio protection

The desktop microphone is the only key whose acoustic ordering interacts with recording:

- Down sound and press animation occur before `IflytekAsrClient.start()` opens the recorder;
- while ASR is Starting, Listening, or Finishing, other desktop key sounds are suppressed;
- on finger Up, request ASR stop first;
- play microphone Up sound only after audio capture is closed, preventing it from entering the final transcript;
- the offline/disabled microphone produces no animation, haptic, or sound.

### Performance requirements

- no drawable, decoder, or audio-player allocation in a touch callback;
- sound assets are decoded before first use;
- visual motion uses the inner appearance surface and hardware-backed properties;
- all keys reuse shared style tokens and cached drawable states;
- rapid alternation between keys must not restart layout, candidate placement, or F1 positioning;
- global-mode exit releases or pauses desktop-only audio resources without changing normal keyboard feedback.

### Acceptance criteria

Implementation is complete only after the following checks pass on the AP device:

1. Capture Down, hold, Up, Cancel, and rapid re-press for a letter, Space, Ctrl, Backspace, Enter, an arrow, exit-global, and ASR.
2. Confirm every key returns to exactly the original screen coordinate and no adjacent key shifts.
3. Confirm Ctrl, Option, Shift, and Command rise after Up while retaining a separate selected indicator.
4. Hold Backspace and each arrow: the key remains down, repeat ticks do not replay the full impact, and one Up occurs at release.
5. Type at least ten keys per second: no missing sounds, clipping, stuck shadows, or animation queue buildup.
6. Verify audio starts within 20 ms of Down under a warm process and does not block Fcitx event dispatch.
7. Verify ASR Down and Up sounds are absent from recognized speech and disabled ASR is silent.
8. Compare normal docked and floating keyboard screenshots, sounds, and touch logs before and after the change; they must be unchanged.
9. Test both device speakers and headphones at low and high configured key-sound volume.
10. Build, install, collect Display 0 screenshots, record slow-motion video, and check logcat for crashes or leaked audio resources.

### Initial design decisions

The first implementation should use the values in this section as one coherent prototype rather than exposing many settings. Travel, timing, shadows, sample selection, and gain can be tuned from AP-device video and audio recordings. User-facing profiles or separate physical-sound controls are intentionally deferred until the base global keyboard feels consistent.

### First prototype implementation

The first implementation establishes the interaction and lifecycle boundary without adding unlicensed audio assets:

- every `KeyView` created by `DesktopKeyboard` opts into 3 dp travel, 50 ms Down, and 110 ms restrained overshoot return;
- the fixed outer view remains the touch target while the complete inner appearance surface, label, and icon move together;
- `CustomGestureView` retains its existing behavior by default and exposes optional physical Down and Up sound behavior only to opted-in desktop controls;
- Down selects the existing Android Standard, SpaceBar, Delete, or Return system effect by key family;
- Up uses the Standard system effect at 0.38 gain as a temporary top-out prototype;
- exit-global and desktop ASR use the same 3 dp motion while remaining transparent at rest and circular while held;
- the ASR release sound is deferred until the client returns to Idle, and global key sound is suppressed while desktop ASR is non-idle;
- hiding the desktop ASR button or leaving global mode restores the default `ToolButton` behavior.

This is deliberately not the final acoustic design described above. The repository still contains no licensed or self-recorded physical-key samples, so sample families, `SoundPool`, non-repeating variants, stereo position, and repeat ticks remain pending. No external audio was downloaded or synthesized as a substitute.

The AP device validation used independent Display 0 `motionevent DOWN` and `UP` commands rather than swipe injection, because this ROM routes long swipe injection through its PortUI overlay. A held Q key moved approximately 6 physical pixels on the 2x-density display, matching 3 dp. The cropped Q region had different content while held, and the resting and settled-release crops had identical MD5 values, confirming exact return without layout drift. Backspace remained at the pressed depth during a sustained Down and returned after Up. Ctrl also moved through the same travel, then rose while retaining its separate selected color. Exiting global mode restored the unchanged normal keyboard, and entering global mode again re-enabled Q travel. Global ASR still authenticated, opened its WebSocket, entered Listening, and returned from Up without a fatal exception.

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

The repository has fixed Git submodule revisions. Clone the single `main` branch, initialize them recursively, then use the project build entry point:

```shell
git clone --branch main --single-branch \
    git@github.com:caucy2026/kborad.git kemi-keyboard
cd kemi-keyboard/fcitx5-android
./scripts/assemble-debug-local.sh
```

The entry point synchronizes and initializes all recursive Git submodules, reads the pinned Android Platform, Build-Tools, NDK, and CMake versions from `Versions.kt`, and installs missing Android components through `sdkmanager`. It also downloads and installs KDE Extra CMake Modules 6.9.0 under `.local-deps/`, supplies its `ECM_DIR` to Gradle, and creates lightweight local `msgfmt` and `msgmerge` wrappers required by the native build. The ECM download uses a temporary file, retries transient network failures, and stops the build cleanly if dependency preparation cannot complete. A colleague only needs JDK 17 and Android Studio or Android SDK Command-line Tools; project-specific native dependencies are prepared by the build entry point.

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

## Changes From 2026-08-02/03: Home Return Key Styling And Desktop Command-Key Combos

> Status: these changes are included in the canonical root repository and maintained on `main`. The sections below document what they change and why.

### Background

Two issues were reported on the AP device:

1. The normal home-page Return/confirm key was hard to hit and looked out of proportion. It was rendered as a small 48 dp accent-colored circle, while the space key uses a wide rounded rectangle. The desired result is a Gboard-like full keycap: same size/shape as a normal physical Enter key, with a color that is clearly more visible than neighboring keys but not jarring, and with content that reads like a physical keyboard `Enter + ↵`.
2. On the global desktop keyboard, Apple-style modifier combinations such as `⌘ + C` did not work; pressing them produced a plain `c` instead of copy.

### Change 1: Home Return key styling and content

#### `KeyDefPreset.kt` — new `MainReturnKey` preset

- `ReturnKey` is now `open` and accepts a `variant: Variant = Variant.Accent` parameter, which is forwarded to its `Appearance.Image`.
- A new `MainReturnKey(percentWidth)` subclass is added; it constructs `ReturnKey(percentWidth, Variant.Alternative)`.
- This gives the home Return key a distinct, detectable variant without touching the shared `R.id.button_return` id or the number/floating keyboards that still use the plain `ReturnKey` (accent circular look).

#### `TextKeyboard.kt` — home page uses `MainReturnKey`

- The second-row `ReturnKey(0.151f)` in the docked `TextKeyboard.Layout` is replaced with `MainReturnKey(0.151f)`. This is the only place that changes: the floating layout's `ReturnKey(0.14f)`, `NumberKeyboard`, and picker/expanded layouts keep the original accent circular Return.

#### `KeyView.kt` — full keycap rendering for the home Return key

Previously both `R.id.button_punctuation` and `R.id.button_return` were drawn as a max-48 dp circle (`insetOvalDrawable`), with return using `accentKeyBackgroundColor`.

Now `onSizeChanged` branches on `R.id.button_return`:

- When `def.variant == Variant.Alternative` (i.e. the home `MainReturnKey`):
  - draws a Gboard-style full keycap via `borderedKeyBackgroundDrawable` / `shadowedKeyBackgroundDrawable` with the standard `radius` (12 dp in Gboard theme), `dp(1)` border/shadow width, and the normal `hMargin`/`vMargin` — same geometry as the letter/space keycaps;
  - background color is `ColorUtils.blendARGB(theme.altKeyBackgroundColor, Color.BLACK, 0.12f)` — the light-blue alternate key color darkened 12%, so it is clearly more prominent than the white space bar and the pale blue Tab/Shift/Backspace, but stays within the same low-saturation blue-grey family instead of jumping to a saturated accent;
  - uses `setupPressHighlight()` (standard full-keycap press/ripple).
- Otherwise (number/floating/etc.) it keeps the original 48 dp accent circle, and `R.id.button_punctuation` keeps its 48 dp circle with `altKeyBackgroundColor`.

#### `KeyView.kt` — `ImageKeyView` content for the home Return key

- A new drawable `app/src/main/res/drawable/ic_keyboard_return_long_30.xml` was added: the standard return arrow with a longer stem (30 dp wide) so the arrow is proportional to the `Enter` label instead of looking cramped.
- In `ImageKeyView.init`, when `def.viewId == R.id.button_return && def.variant == Variant.Alternative`:
  - the icon is swapped to the long arrow (`30 x 20 dp`);
  - a bold 14 sp `Enter` label is added;
  - label and arrow are laid out horizontally, centered, in a `LinearLayout` inside the keycap, mimicking a physical keyboard Enter key.
- All other image keys (including return keys on number/floating keyboards) keep the plain centered icon path.

#### `TextKeyboard.kt` — freeze the home Return icon

- `onReturnDrawableUpdate(returnDrawable)` no longer overwrites the icon for the home `MainReturnKey` (`def.variant == Variant.Alternative`), so the `Enter + ↵` label is not replaced by the IME-action icon (Go/Search/Send/Done) when the focused editor declares such an action.
- Number/floating keyboards still update their return icon dynamically as before.

### Change 2: Desktop keyboard Apple modifier combinations

#### `DesktopKeyboard.kt` — drop `Virtual` when a shortcut modifier is held

Root cause: every key action (including letters) was dispatched with `KeyState.Virtual` appended, and the native layer treats a `Virtual + Unicode` key as direct text commit, discarding Meta/Ctrl/Alt — so `⌘ + C` became a literal `c`.

The `onAction` state computation was changed:

```kotlin
val shortcutModifiers = setOf(KeyState.Ctrl, KeyState.Alt, KeyState.Meta)
val states = if (modifierStates.any { it in shortcutModifiers }) {
    KeyStates(*modifierStates.toTypedArray())
} else {
    KeyStates(*(modifierStates + KeyState.Virtual).toTypedArray())
}
```

- When Ctrl, Option (Alt), or Command (Meta) is latched, `Virtual` is omitted so the combination is delivered as a real `KeyEvent` with modifier state (and can reach shortcuts such as `⌘+C`, `⌘+A`, `Ctrl+...`).
- Plain typing (no shortcut modifier) keeps the previous `Virtual` behavior unchanged.
- The existing Ctrl+Space language-switch shortcut and Shift handling are untouched.

### Validation

- `./gradlew :app:compileReleaseKotlin --offline` → `BUILD SUCCESSFUL`.
- Signed release built via `scripts/assemble-release-local.sh` → `build/kboard.apk`.
- Installed and validated on `192.168.3.46:5555`:
  - home Return key renders as a full keycap with `Enter + long-arrow`, noticeably darker blue-grey than the space bar and other function keys;
  - tapping it still submits and dismisses the keyboard; no `FATAL EXCEPTION`.
- Installed on `192.168.43.11:5555` (this device ships a system-preinstalled `KBoard` under `/system/app/KBoard` with a different signature; after `adb root` + overlayfs remount the system copy was bypassed and the new release APK installed into `/data/app/...`, version `0.1.2-123-g9e33d2c2`).
- `192.168.3.63:5555` could not be upgraded in place because it also carries the differently-signed preinstalled system `KBoard`; installing over it returns `INSTALL_FAILED_UPDATE_INCOMPATIBLE` until that device is handled with the same root/remount procedure or a matching signature.
- The `⌘ + C` / `⌘ + A` behavior was verified on the AP device once the KBoard `Virtual` regression was removed; the remaining end-to-end behavior depends on the remote-desktop client forwarding `metaState` (tracked separately, outside this keyboard repo).
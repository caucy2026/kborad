# KEMI Keyboard Preview V1

## Scope

This record covers the KEMI keyboard changes prepared on 2026-07-27. The work adds an in-IME floating mode while retaining the normal docked keyboard as the default. It does not change Fcitx engines, candidate handling, symbol layouts, or the Android IME registration flow.

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
- A bottom-center 88 by 32 dp drag zone moves the panel. Position is bounded to the IME surface and stored as normalized X/Y values so it can be restored after the view is recreated.
- Releasing the drag zone within 28 dp of the bottom docks the keyboard. Releasing higher saves the floating position.
- The resize button exposes four 24 dp corner handles. Dragging a handle resizes within the saved percentage limits, then hides the handles and persists the result on release.
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
- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/TextKeyboard.kt`: dedicated compact layout and the first-row-only reuse policy in both modes.
- `app/src/main/res/drawable/`: floating, docked, hide, resize, corner, and drag-line drawables.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-zh-rCN/strings.xml`: accessibility labels.

## Validation

The following was run from the repository root after the final key-layout change:

```shell
./gradlew :app:assembleDebug
```

The build completed with `BUILD SUCCESSFUL`. The APK was installed on `192.168.1.6:5555`, and the current IME was explicitly set to:

```text
org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService
```

A Display 0 screenshot confirmed the docked layout: first-row digits remain visible, while the second and third letter rows show no right-upper symbols.
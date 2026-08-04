# Gboard 真机基线与 fcitx5-android 改造分析

> 采集日期: 2026-07-24
> 设备: V900 / Android 12 / API 31
> ADB: 192.168.3.46:5555
> 显示: Display 0 + Display 2, 1920x1280, 320 dpi

## 1. Gboard 基线

### 1.1 版本与系统状态

- 包名: `com.google.android.inputmethod.latin`
- Service: `com.android.inputmethod.latin.LatinIME`
- 版本: `17.1.5.887912998-beta-arm64-v8a`
- versionCode: `175733014`
- 当前 IME Display: Display 0
- `mCurTokenDisplayId=0`
- `mInputShown=true`
- Display 2 不显示 IME

基线截图暂存于:

- `/tmp/gboard-baseline/d0-current.png`
- `/tmp/gboard-baseline/d0-pinyin-nihao.png`
- `/tmp/gboard-baseline/d2-current.png`

### 1.2 空闲键盘结构

主屏横屏下，从上至下为:

1. 工具栏
2. 第一行: Tab、Q-P、Backspace
3. 第二行: Caps、A-L、Return
4. 第三行: Shift、Z-M、逗号、句号、单引号
5. 底栏: `?123`、Emoji、语言、空格、左光标、右光标、`?123`
6. 系统导航栏

观测到的视觉特征:

- 键盘背景是浅蓝灰色。
- 普通按键为白色圆角矩形。
- 功能键为浅蓝色圆角矩形。
- 空格是白色长圆角矩形，显示当前输入法“拼音”。
- Enter 是浅蓝色宽圆角矩形，不是圆形按钮。
- 工具栏使用无边框图标按钮。
- 字母按键右上角显示数字副符号。
- 横屏按键间距明显大于当前 fcitx5 默认值。

按截图估算（后续用截图像素对比校准）:

- 工具栏高度约 48dp。
- 字母区每行高度约 66-70dp。
- 普通键圆角约 12dp。
- 横向可见间距约 6dp。
- 纵向可见间距约 6dp。
- 底部系统导航区域约 40dp。

### 1.3 拼音候选状态

输入 `nihao` 后:

- 拼音串 `ni hao` 单独显示在候选栏上方。
- 候选栏显示“你好、鸟、你、尼、泥、逆、腻、拟、呢”。
- 首候选位于最左侧，不使用卡片背景。
- 候选词之间使用细竖线分隔。
- 右侧有向下展开按钮。
- 工具栏被候选栏替换。
- 第三行左侧 Shift 键变为“词”。
- 空格仍显示“拼音”。
- 键盘主体高度基本不变，新增拼音串区域会进一步压缩应用内容。

### 1.4 双屏行为

- 当前焦点窗口在 Display 0，Gboard 仅显示在 Display 0。
- Display 2 保持桌面，不出现镜像键盘。
- 系统 `InputMethodManager` 记录当前客户端 `displayId=0`。
- 后续必须在 Display 2 的真实输入框上重新采集，确认焦点转移后的 IME 路由。

## 2. fcitx5-android 控制路径

### 2.1 输入视图入口

`FcitxInputMethodService.replaceInputView()` 创建 `InputView`，并通过 `setInputView()` 安装。

关键文件:

- `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/InputView.kt`

`InputView` 负责:

- 工具栏、键盘窗口、预编辑区域和弹窗的整体层级。
- 横竖屏键盘高度。
- 左右和底部留白。
- 主题背景。

当前横屏键盘高度默认值是屏幕高度的 49%，工具栏高度为 40dp。

### 2.2 键盘布局

关键文件:

- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyboardWindow.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/TextKeyboard.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyDef.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyDefPreset.kt`

`TextKeyboard.Layout` 是直接决定四行键位和宽度的静态定义。当前布局与 Gboard 平板布局差异较大，需重排:

- Backspace 从第三行移至第一行。
- Return 从底栏移至第二行。
- 第三行增加中文候选状态键和标点键。
- 底栏增加 Emoji 与左右光标键。
- 第一、二行增加左侧功能键。

### 2.3 按键视觉

关键文件:

- `app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyView.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/data/theme/Theme.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/data/theme/ThemePrefs.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/data/theme/ThemePreset.kt`

当前默认参数:

- `keyBorder=false`
- 横屏水平 margin: 3dp
- 横屏垂直 margin: 4dp
- 圆角: 4dp
- Ripple: false

Gboard 风格第一版建议:

- 普通键显示独立背景。
- 横屏水平 margin 调整为约 6dp。
- 横屏垂直 margin 调整为约 6dp。
- 圆角调整为约 12dp。
- 开启 Ripple 或保留 Gboard 类似的实色按压态。
- 移除 `KeyView.onSizeChanged()` 中 Enter 的椭圆特殊背景。
- 将 Space 和 Enter 都按普通圆角矩形绘制。
- 新增专用浅色、深色 Gboard-like 主题预设，不复制 Gboard 专有资源。

### 2.4 工具栏与候选栏

关键文件:

- `app/src/main/java/org/fcitx/fcitx5/android/input/bar/KawaiiBarComponent.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/bar/ui/IdleUi.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/bar/ui/CandidateUi.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/candidates/CandidateItemUi.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/candidates/horizontal/HorizontalCandidateComponent.kt`
- `app/src/main/java/org/fcitx/fcitx5/android/input/preedit/PreeditUi.kt`

现有状态机已经支持:

- 空闲工具栏与候选栏切换。
- 候选展开按钮。
- 候选点击和长按。
- 独立预编辑区域。
- 剪贴板、编辑、状态、语音入口。

主要改造:

- 工具栏高度由 40dp 校准到约 48dp。
- 候选项改为 Gboard 的左对齐、无卡片、竖线分隔样式。
- 预编辑区域固定为单行左对齐。
- 候选状态下将第三行左功能键切换成“词”。
- 空闲状态工具栏按真机截图排列。

### 2.5 已有行为可直接复用

`BaseKeyboard` 和 `CustomGestureView` 已支持:

- 空格左右滑动移动光标。
- Backspace 长按重复删除。
- Backspace 横向滑动选择并删除文本。
- Shift 单击、双击和长按锁定。
- 字母上滑输入副符号。
- 按键按下预览。
- 长按弹出扩展字符键盘。
- 多指触摸和 Vivo 触摸兼容处理。
- 重复按键震动。

因此第一阶段不修改 fcitx5 C++ 引擎，也不重写触摸框架。

## 3. 第一阶段实现范围

目标是先做到“看起来像、基本操作也像”，并保持拼音引擎稳定。

1. 新增 Gboard-like 浅色和深色主题。
2. 重排横屏 `TextKeyboard.Layout`。
3. 新增 Tab、Emoji、左右光标和候选状态键定义。
4. 将 Enter 与 Space 改为普通圆角矩形。
5. 调整横屏按键间距和圆角。
6. 工具栏高度、图标排列与候选栏样式对齐真机截图。
7. 用预编辑状态驱动第三行左功能键在 Shift/“词”之间切换。
8. 保留现有空格滑动、退格重复、Shift 状态和长按弹窗。

## 4. 验证矩阵

每个提交按以下流程验证:

```text
修改 -> assembleDebug -> adb install -r -> 切换 fcitx5
-> 打开同一个便签输入框 -> 主屏截图 -> logcat
-> 输入 nihao -> 候选态截图 -> 与 Gboard 基线并排比较
```

必须覆盖:

- Display 0 空闲键盘。
- Display 0 拼音候选态。
- Shift None/Once/Lock 三态。
- Backspace 点按和长按。
- 空格滑动光标。
- `?123` 数字符号页。
- Emoji、语言切换和 Enter 动作。
- Display 2 输入框弹出、触摸和关闭。
- 崩溃、ANR、`InputMethodService` 生命周期日志。

## 5. 当前结论

第一阶段可在 Kotlin UI 层完成，预计无需修改 fcitx5/libime C++。主要风险不是拼音引擎，而是:

- 平板专用键位宽度在不同横屏尺寸上的约束。
- 候选状态引起的总高度变化。
- Display 2 焦点下 IME 窗口路由。
- 当前偏好系统允许用户覆盖尺寸和主题，需定义 Gboard-like 默认值与用户自定义之间的优先级。

只有在实际验证中发现候选提交、输入法状态或 Display 路由受 UI 改造影响时，才向 `CommonKeyActionListener`、广播组件或系统窗口层扩展修改范围。

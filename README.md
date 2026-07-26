# KEMI 语音输入法（KBoard）

KEMI 是基于 [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) 二次开发的 Android 中文输入法项目，面向 V900 双屏平板及 arm64 Android 设备。项目集成 Fcitx5 中文输入能力、讯飞流式 ASR、中文设置界面和真机调试闭环。

## 主要功能

- Fcitx5 拼音、五笔拼音等输入法支持。
- 按住说话的讯飞流式语音输入，实时显示识别结果，松手后提交最终文本。
- 语音权限、断网预检、鉴权失败、网络策略和麦克风异常的中文提示。
- KEMI 品牌化设置页，全局选项、输入法、附加组件及 Fcitx 动态配置中文化。
- 语音识别期间使用绿色活动态图标，结束或取消后恢复。
- 已在 Android 12、arm64-v8a 的 V900 双屏平板上完成构建、安装和运行验证。

## 项目结构

```text
kborad/
├── fcitx5-android/              # Android IME 完整源码
│   ├── app/                     # 应用、键盘 UI、设置与 ASR
│   ├── lib/                     # Fcitx5、libime 和中文扩展
│   ├── plugin/                  # 可选输入法插件
│   └── scripts/                 # 本地依赖与构建工具
├── cl.md                        # 唯一变更日志
├── fcitx5-android-port-plan.md  # 移植、构建、部署与 gettext 调试
├── iflytek_asr_interface_doc.md # 讯飞 ASR 协议与真机排错
└── AGENTS.md                    # AI 编程代理项目约定
```

## 构建环境

推荐环境：

- JDK 17
- Android SDK / Build Tools 36
- Android NDK `28.0.13004108`
- CMake 3.31.6
- Python 3
- macOS、Linux 或 Windows

首次构建需要网络下载 Gradle 依赖和 ECM。本项目提供本地依赖准备脚本，并内置 PO 到 GNU MO 的后备编译器，避免 Fcitx 中文翻译被错误打包。

## 快速构建

```bash
git clone https://github.com/caucy2026/kborad.git
cd kborad/fcitx5-android
./scripts/assemble-debug-local.sh
```

构建完成后，从以下目录查找实际 APK 文件：

```bash
find app/build/outputs/apk/debug -name '*.apk' -type f -print
```

APK 文件名包含当前 Git 版本，不要写死文件名。

## 安装与启用

```bash
adb install -r <debug-apk>

adb shell ime enable \
  org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService

adb shell ime set \
  org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService

adb shell am start -n \
  org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.ui.main.MainActivity
```

## 验证要求

运行时改动不能只验证编译成功。标准闭环为：

1. 构建 APK。
2. 安装到设备。
3. 强制停止并重启应用或输入法进程。
4. 实际操作修改路径。
5. 用截图或 UI dump 确认界面状态。
6. 检查 logcat 中无相关崩溃和安全异常。

可在 VS Code Copilot 中使用项目 skill：

```text
/kemi-android-validation general
/kemi-android-validation localization
/kemi-android-validation asr
```

## 文档

- [变更日志](cl.md)
- [V900 移植、构建与部署](fcitx5-android-port-plan.md)
- [讯飞 ASR 接口与调试闭环](iflytek_asr_interface_doc.md)
- [Gboard 基线分析](gboard-baseline-analysis.md)
- [上游 fcitx5-android 构建说明](fcitx5-android/README.md)

## 当前状态

- Debug APK 构建通过。
- APK 可安装并启用为系统输入法。
- Fcitx 运行 Locale 验证为 `zh_CN:zh`。
- Android 静态文本和 Fcitx 动态配置均已验证中文显示。
- ASR 权限、联网判断、明文网络白名单及主要崩溃路径已完成真机修复。

仍需持续回归双屏 Display 2 输入焦点，以及 ASR 鉴权失败、WebSocket 中断和 Final 超时等异常场景。

## 开源说明

本项目包含来自 fcitx5-android、Fcitx5、libime 及相关插件的代码。各模块的许可声明与上游版权信息保留在源码目录中，使用和分发时请遵守对应许可证。

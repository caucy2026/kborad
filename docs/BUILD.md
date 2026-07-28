# KEMI 键盘 — 新人编译指南

## 快速开始（一行命令）

```bash
git clone --branch kboard-preview-v1 git@github.com:caucy2026/kborad.git
cd kborad
./scripts/assemble-debug-local.sh
```

APK 输出路径: `app/build/outputs/apk/debug/*arm64-v8a-debug.apk`

## 你需要什么

| 依赖 | 说明 |
|------|------|
| Git | 必须，子模块需要 |
| JDK 17 | `JAVA_HOME` 或 `PATH` 中有 `javac` |
| Android SDK | `ANDROID_HOME` 指向 SDK 根目录 |
| 网络 | 首次构建需要从 GitHub / Gradle / Maven 下载依赖 |

> **你不需要手动安装**: NDK、CMake、ECM、gettext——脚本会自动处理所有这些。
>
> **你可以安装 Android Studio**: 它会自动配好 JDK + SDK，脚本能自动发现。

## 构建脚本做了什么

`./scripts/assemble-debug-local.sh` 按顺序执行：

1. **`setup-local-native-deps.sh`** — 准备原生依赖
   - 检测并自动初始化 22 个 Git 子模块（已锁定的跳过更新）
   - 检测 Android SDK，自动安装固定版本的 platform/build-tools/NDK/CMake
   - 下载并构建 ECM 6.9.0
   - 生成 gettext wrapper 脚本
2. **Gradle 构建** — `./gradlew :app:assembleDebug`
   - 最多重试 3 次（网络波动容错）
   - 使用仓库锁定的 Gradle 9.4.1 wrapper

### 固定工具链版本

| 组件 | 版本 |
|------|------|
| compileSdk | 36 |
| Build-Tools | 36.1.0 |
| NDK | 28.0.13004108 |
| CMake | 3.31.6 |
| ECM | 6.9.0 |
| Gradle | 9.4.1 |

> 这些版本定义在 `build-logic/convention/src/main/kotlin/Versions.kt`，脚本自动读取。

## 已知问题

### ✅ 本地构建 (macOS / Linux) — 正常

本地有完整网络访问时，一行命令即可构建成功。已验证：

```
BUILD SUCCESSFUL in ~5s (增量) / ~7-15min (全量首次)
196 actionable tasks
APK: ~59MB (arm64-v8a-debug)
```

### ⚠️ GitHub Actions CI — 待修复

CI 环境（ubuntu-22.04 / ubuntu-24.04）存在**网络限制**，无法访问外部 Maven 仓库：

| 组件 | 本地 | CI | 说明 |
|------|------|-----|------|
| Git 子模块 | ✅ | ✅ 已修复 | actions/checkout 递归克隆 + 脚本跳过已存在模块 |
| ECM 6.9.0 | ✅ | ✅ 已修复 | 通过 actions/checkout 预置源码，不再依赖 curl 下载 |
| Android SDK | ✅ | ✅ | setup-android action 安装 |
| Gradle 9.4.1 | ✅ | ✅ | setup-gradle action 安装 |
| Gradle 插件依赖 | ✅ | ❌ | CI 无法访问 plugins.gradle.org、Maven Central、Google Maven |

**CI 当前状态**: 所有前置步骤通过，但在 Gradle 的 `kotlin-dsl` 插件解析阶段失败，因为该插件需要从 Gradle Plugin Portal 下载，而 CI runner 无法连接外部 Maven 仓库。

**临时结论**: 本地构建完全可用；CI 构建需要解决 Maven 仓库访问问题（配置代理 / 自托管 runner / 离线依赖缓存）。

### 常见本地问题

| 问题 | 解决方案 |
|------|----------|
| `git submodule update` 失败 | 网络问题，脚本已内置 3 次重试。仍失败则开代理 |
| `sdkmanager not found` | 安装 Android Studio 或 Android SDK Command-line Tools |
| `ANDROID_HOME not set` | `export ANDROID_HOME=/path/to/sdk` |
| ECM 下载失败 | 脚本内置 5 次 curl 重试，如持续失败检查网络 |
| `local.properties` 缺失 | 脚本会自动从 `ANDROID_HOME` 推导，也可手动创建 |
| Gradle 下载超时 | wrapper 可能被墙，设置 HTTP 代理或使用已缓存的发行版 |
| `prebuilt` 目录有未追踪内容 | 正常现象，不要提交它；已在 `.gitignore` 中 |

## 项目结构（关键文件）

```
fcitx5-android/
├── scripts/
│   ├── assemble-debug-local.sh      # ★ 唯一构建入口
│   └── setup-local-native-deps.sh   # 原生依赖准备
├── build-logic/
│   └── convention/src/main/kotlin/
│       └── Versions.kt              # 固定工具链版本
├── .github/workflows/
│   └── kemi-reproducible-build.yml   # CI 定义
├── app/                             # 主应用模块
├── lib/                             # 核心库 (fcitx5 引擎)
├── plugin/                          # 输入法插件
└── gradle/wrapper/                  # Gradle wrapper (已签入)
```

## 提交规范

- **只推送到** `backup-kborad` (git@github.com:caucy2026/kborad.git)
- **不要推送** `origin`
- **不要提交** `lib/fcitx5/src/main/cpp/prebuilt`
- **不要提交** `local.properties`
- 修改 JSON/脚本后运行 `./scripts/assemble-debug-local.sh` 验证

## 参考

- 上游项目: https://github.com/fcitx5-android/fcitx5-android
- CI 状态: https://github.com/caucy2026/kborad/actions
- 键盘预览变更记录: [docs/kemi-keyboard-preview-v1.md](docs/kemi-keyboard-preview-v1.md)

# fcitx5-android 移植到 V900 平板计划

> 日期: 2026-07-23
> 目标设备: V900 双屏平板 (192.168.3.46:5555)

---

## 1. 目标设备信息

| 项目 | 实测值 |
|------|--------|
| **芯片平台** | V900，HiSilicon hi3781v730 |
| **CPU** | 8核 Cortex-A73 (CPU part 0xd09) |
| **GPU** | Mali-G52, OpenGL ES 3.2 |
| **Android** | 12 (API 31)，userdebug 版本 |
| **构建指纹** | huanglong/hi3781v730_tablet/hi3781v730:12/SP1A.210812.016/eng.yance.20260722.222148:userdebug/dev-keys |
| **RAM** | ~5.6 GB (MemTotal: 5745828 kB) |
| **存储** | 106GB，可用 102GB |
| **ABI** | arm64-v8a |
| **Kernel** | aarch64 |
| **主屏 (Display 0)** | 1920×1280, dpi 320, 内置屏幕 |
| **副屏 (Display 2)** | 1920×1280, dpi 320, HDMI 外接 |
| **当前输入法** | Google Latin IME (AOSP 原生键盘) |

---

## 2. 移植方案

### 第一阶段：源码获取与环境准备

1. Clone fcitx5-android 主仓库
2. Clone 插件仓库（拼音等输入模块）
3. 确认 NDK/CMake 工具链
4. 分析构建依赖

### 第二阶段：适配编译

1. 配置 arm64-v8a 交叉编译
2. 设置 minSdk=31, targetSdk=34
3. 处理平台兼容性问题
4. 编译 APK

### 第三阶段：签名部署

1. 使用 debug.keystore 或 AOSP 平台密钥签名
2. adb install 到平板
3. 启用输入法

### 第四阶段：验证测试

1. 双屏输入法弹出测试
2. 拼音/英文输入功能测试
3. 横屏布局适配验证

---

## 3. 关键风险点

- fcitx5-android 当前 minSdk 可能高于 31，需要降级适配
- NDK 版本兼容性
- 双屏场景下 IME window 弹出位置
- Hisilicon 芯片可能有 GPU 渲染兼容问题（键盘 UI）

---

## 5. 执行记录 (2026-07-23)

### 5.1 构建环境准备

| 依赖 | 状态 | 备注 |
|------|:----:|------|
| JDK 17 | ✅ | Temurin-17.0.19 |
| Android SDK 36 | ✅ | 已通过 sdkmanager 安装 |
| NDK 28.0.13004108 | ✅ | 已安装 |
| CMake 3.31.6 | ✅ | Android SDK 自带 |
| Build-tools 36.1.0 | ✅ | 已安装 |
| Gradle 9.4.1 | ✅ | wrapper 自动下载 |
| ECM 6.14.0 | ✅ | 手动编译安装到 /tmp/ecm-install |
| gettext (msgmerge/msgfmt) | ✅ | 使用 dummy 脚本替代（Android 不需要翻译） |

### 5.2 网络环境

- 系统代理: 127.0.0.1:7897 (Clash)
- Git 克隆: 需要通过代理，需设置 `http.sslVerify=false`
- Gradle 依赖: 通过 `gradle.properties` 配置 JVM 代理参数
- GitHub SSH: 可用 (`git@github.com`)

### 5.3 关键问题及解决

| 问题 | 解决方案 |
|------|---------|
| Gradle Plugin Portal 找不到 Kotlin 插件 | build-logic/settings.gradle.kts 添加 mavenCentral() 到 pluginManagement |
| git clone 超时 | 配置 http.proxy + http.sslVerify=false |
| 缺少 ECM | 下载源码，用 Android SDK cmake 编译安装 |
| 缺少 gettext | 创建 dummy msgmerge/msgfmt 脚本 |
| libime 缺少 kenlm 子模块 | `git submodule update --init --recursive` |
| fcitx5 缺少 yoga 子模块 | 同上 |
| Maven 依赖下载 TLS 错误 | gradle.properties 添加 JVM 代理参数 |

### 5.4 构建命令

```bash
cd /Users/newlink/kemi/kboard/fcitx5-android
export ECM_DIR=/tmp/ecm-install/share/ECM/cmake
export PATH="/tmp/gettext-install/bin:$PATH"
./gradlew :app:assembleDebug
```

### 5.5 APK 信息

- 路径: `app/build/outputs/apk/debug/org.fcitx.fcitx5.android-0eb0e06-arm64-v8a-debug.apk`
- 大小: 57MB
- 包名 (debug): `org.fcitx.fcitx5.android.debug`
- IME Service: `org.fcitx.fcitx5.android.input.FcitxInputMethodService`

### 5.6 部署命令

```bash
# 安装
adb -s 192.168.3.46:5555 install -r <apk>

# 启用输入法
adb shell ime enable org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService

# 设为默认
adb shell ime set org.fcitx.fcitx5.android.debug/org.fcitx.fcitx5.android.input.FcitxInputMethodService

# 启动配置界面
adb shell monkey -p org.fcitx.fcitx5.android.debug -c android.intent.category.LAUNCHER 1
```

### 5.7 部署状态

- ✅ APK 编译成功
- ✅ 安装到平板 (192.168.3.46)
- ✅ 输入法已启用
- ✅ 已设为默认输入法
- ✅ 应用正常运行（PID 8900, FcitxApplication 初始化成功）
- ⏳ 待测试: 实际输入功能、拼音输入、双屏适配

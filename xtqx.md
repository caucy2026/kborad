# Go3DGlobe 系统权限调试手册

> **核心原则**: 系统权限不一定要安装到 `/system/priv-app`，通过正确的 ADB 设置、平台签名、或 `appops` 即可授予等效权限。

---

## 1. AndroidManifest 权限清单

```xml
<manifest>
    <!-- 双屏异显必需：允许在其他窗口之上绘制（副屏 overlay/webcam 弹出层） -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <!-- 网络 -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- 非强制特性声明（避免 Play Store 过滤） -->
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
</manifest>
```

| 权限 | 用途 | 获取方式 |
|------|------|---------|
| `SYSTEM_ALERT_WINDOW` | webcam弹出层、watch悬浮窗、DeepSeek浮动窗 | 见 §2 |
| `INTERNET` | 瓦片下载、webcam拉取、云端数据更新 | 自动授予 |
| `ACCESS_NETWORK_STATE` | 检测离线/在线状态 | 自动授予 |

---

## 2. SYSTEM_ALERT_WINDOW — 三种授予方案

### 方案A：ADB 手动授权（最简单，开发调试用）

```bash
# Android 6.0+ 需要在设置中手动开启"显示在其他应用上层"
adb shell appops set com.globe.dualscreen SYSTEM_ALERT_WINDOW allow

# 验证
adb shell appops get com.globe.dualscreen SYSTEM_ALERT_WINDOW
# 输出: SYSTEM_ALERT_WINDOW: allow
```

### 方案B：Settings 数据库写入（RK356x 定制系统推荐）

```bash
# 直接写入系统设置数据库，跳过用户交互
adb shell settings put secure overlay_display_devices 1920x1280/320

# 将 app 加入白名单
adb shell content insert --uri content://settings/secure \
    --bind name:s:overlay_packages \
    --bind value:s:com.globe.dualscreen
```

### 方案C：平台签名（生产环境）

```
1. 获取设备 platform.pk8 + platform.x509.pem
2. 用 signapk.jar 对 APK 签名
3. AndroidManifest.xml 设置 sharedUserId="android.uid.system"
4. 普通 install 即可（无需推到 /system）
```

> ⚠️ `sharedUserId="android.uid.system"` 会导致 WebView 在 uid=1000 下被 Android 安全策略封杀。
> 解决方案：DeepSeek WebView 使用独立进程 `android:process=":web"`，以普通 uid 运行。

---

## 3. 双屏异显配置

### 3.1 设备硬件要求

| 项目 | RK356x 实测值 |
|------|-------------|
| 主屏 (Display 0) | 1920×1280, 横屏, 内置屏幕 |
| 副屏 (Display 2) | 1920×1280, 横屏, HDMI 外接 |
| Display 1 | 某些芯片保留（MIPI DSI 内屏），跳过 |

### 3.2 检测副屏

```bash
# 枚举所有 Display
adb shell dumpsys display | grep -E "Display Device|mViewports|DisplayId|width|height"

# 预期输出:
# Display 0: 内置屏幕, 1920×1280
# Display 2: HDMI 屏幕, 1920×1280
```

### 3.3 代码中查找副屏

```kotlin
val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
val myDisplayId = display?.displayId ?: 0
val secondaryDisplay = displayManager.displays.firstOrNull {
    it.displayId != myDisplayId && it.isValid
}
```

### 3.4 启动副屏 Activity

```kotlin
val intent = Intent(this, GlobeActivity::class.java).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
}
val options = ActivityOptions.makeBasic().apply {
    // 反射设置 launchDisplayId（API 26+ 推荐用 launchDisplayId 但部分设备需反射）
    val method = ActivityOptions::class.java.getDeclaredMethod(
        "setLaunchDisplayId", Int::class.javaPrimitiveType!!)
    method.isAccessible = true
    method.invoke(this, secondaryDisplay.displayId)
}
startActivity(intent, options.toBundle())
```

### 3.5 ADB 手动启动到指定屏

```bash
# 主屏启动 (Display 0)
adb shell am start -n com.globe.dualscreen/.MainActivity --display 0

# 副屏启动 (Display 2)
adb shell am start -n com.globe.dualscreen/.GlobeActivity --display 2

# 测试防呆机制（主屏 Activity 在副屏启动 → 应自动拒绝）
adb shell am start -n com.globe.dualscreen/.MainActivity --display 2
```

---

## 4. 网络配置

### 4.1 明文 HTTP 流量

```xml
<application android:usesCleartextTraffic="true">
```

RK356x 设备部分 CDN/API 使用 HTTP（非 HTTPS），必须开启。

### 4.2 WiFi ADB 调试

```bash
# 首次连接（需 USB）
adb tcpip 5555
adb connect 192.168.3.46:5555

# 日常使用
adb disconnect && adb connect 192.168.3.46:5555

# 多设备管理
adb -s 192.168.3.46:5555 shell      # 指定设备
adb -s 192.168.3.54:5555 shell      # 另一台
```

---

## 5. 存储权限

### 5.1 内部缓存（无需额外权限）

```
context.cacheDir/
├── webcams_cache.json      # 云端摄像头数据缓存
├── webcams_version.txt     # 版本号缓存
├── webcams_failures.json   # 摄像头失败追踪
├── owm_cloud/              # 天气云图瓦片缓存 (30min有效)
└── ...
```

### 5.2 外部存储（可选，调试用）

```xml
<!-- 如需读写 /sdcard 用于日志导出 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

> Android 10+ 不再需要此权限即可读写应用专属外部存储目录。

---

## 6. GPU/OpenGL 配置

### 6.1 硬件要求

```
GPU: Mali-G52
OpenGL ES: 3.2
RAM: ≥ 4GB
```

### 6.2 检查命令

```bash
adb shell dumpsys SurfaceFlinger | grep "GLES:"
# GLES: ARM, Mali-G52, OpenGL ES 3.2 v1.r32p0-01eac0.25c0364...

adb shell cat /proc/cpuinfo | grep -E "CPU part|processor"
# CPU part: 0xd05 (Cortex-A55)
# processor: 0..3 (4核心)
```

---

## 7. WebView 特殊处理

### 7.1 系统 UID 下的 WebView 限制

```
问题: sharedUserId="android.uid.system" (uid=1000)
      → WebView 渲染进程无法启动
      → 报错: "crash webview in system uid"

解决: DeepSeekWebActivity 使用独立进程
      android:process=":web"
      → 以普通 uid 运行 WebView
```

### 7.2 WebView 调试

```bash
# 开启 WebView 远程调试
adb shell cat /sys/class/android_usb/android0/enable

# Chrome 访问 chrome://inspect 查看 WebView
```

---

## 8. 调试开关（SharedPreferences）

```kotlin
// globe_prefs.xml
debug_log_enabled    = false   // 调试日志开关（默认关，避免卡顿）
tile_source          = "gaode" // 瓦片源: "gaode" | "tianditu"
tianditu_token       = ""      // 天地图 API token
```

---

## 9. 快速部署脚本

```bash
#!/bin/bash
# deploy.sh — 一键编译+安装+启动
DEVICE=${1:-192.168.3.46:5555}

cd /Users/newlink/kemi/Go3DGlobe

# 编译
./gradlew assembleDebug || exit 1

# 安装
adb -s $DEVICE install -r app/build/outputs/apk/debug/app-debug.apk

# 授权（首次或权限丢失时）
adb -s $DEVICE shell appops set com.globe.dualscreen SYSTEM_ALERT_WINDOW allow

# 重启
adb -s $DEVICE shell am force-stop com.globe.dualscreen
sleep 1
adb -s $DEVICE shell am start -n com.globe.dualscreen/.MainActivity --display 0

echo "✅ 部署完成 $DEVICE"
```

---

## 10. 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 副屏黑屏 | `SYSTEM_ALERT_WINDOW` 未授予 | `adb shell appops set ... allow` |
| 副屏不显示 | Display ID 不是 2 | `dumpsys display` 确认实际 ID |
| WebView 白屏 | uid=1000 安全限制 | 使用 `:web` 独立进程 |
| APK 安装失败 DELETE_FAILED_INTERNAL_ERROR | 带 SYSTEM 标记的 APK | `adb root && remount && rm /system/priv-app/Go3DGlobe/*` |
| 瓦片不加载 | 离线状态下 elevation coverage 缺失 | V3.8.0 已修复（始终注册 coverage） |
| 云端更新闪退 | JSON 字段不匹配 (lon/lng) | V3.8.1 已修复（optDouble 容错） |

---

## 11. 验证清单

部署到新设备后逐项检查：

```
□ ADB 连接正常: adb devices
□ SYSTEM_ALERT_WINDOW: adb shell appops get com.globe.dualscreen SYSTEM_ALERT_WINDOW
□ 双屏检测: adb shell dumpsys display | grep "Display "
□ 副屏启动: adb shell am start -n com.globe.dualscreen/.GlobeActivity --display 2
□ WebView 可用: 点击 DeepSeek 按钮 → 不白屏
□ 瓦片加载: 缩放地球 → 地图正常显示
□ 摄像头: 点击全球眼 → 9宫格正常
□ 云端更新: 设置→更新→不闪退
□ WiFi ADB: 断开 USB 后 adb connect 192.168.3.46:5555 可连
```

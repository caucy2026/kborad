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
| gettext (msgmerge/msgfmt) | ✅ | 必须生成有效 GNU MO；项目内 `compile_mo.py` 可作为无系统 gettext 时的后备实现 |

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
| 缺少 gettext | 优先安装 GNU gettext；后备包装器必须将 PO 编译为二进制 MO，禁止直接复制改名 |
| libime 缺少 kenlm 子模块 | `git submodule update --init --recursive` |
| fcitx5 缺少 yoga 子模块 | 同上 |
| Maven 依赖下载 TLS 错误 | gradle.properties 添加 JVM 代理参数 |

### 5.4 构建命令

```bash
cd /Users/newlink/kemi/kboard/fcitx5-android
export ECM_DIR=/tmp/ecm/install/share/ECM/cmake
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

### 5.8 Fcitx 动态配置中文失效闭环（2026-07-26）

#### 现象

- Android 系统语言为 `zh-CN`。
- 设置首页的 Android 字符串正常显示中文。
- 点击“全局选项”后，`Hotkey`、`Behavior`、`Reset state on Focus In` 等 Fcitx 动态配置全部显示英文。

#### 排除项

```bash
adb shell getprop persist.sys.locale
adb logcat -d | grep -A5 "Starting fcitx with"
```

实测系统为 `zh-CN`，Fcitx 启动参数为 `locale=zh_CN:zh`，因此不是系统 Locale 或 Java 资源匹配问题。

#### 根因

旧的本地 `msgfmt` 占位脚本只执行 PO 文件复制，导致输出文件虽命名为 `.mo`，内容仍是文本：

```bash
xxd -l 16 app/src/main/assets/usr/share/locale/zh_CN/LC_MESSAGES/fcitx5.mo
# 错误文件以 23 20 开头，即文本 "# "
```

Fcitx 的 libintl 无法加载这种伪 MO，于是回退到源码中的英文。Android 界面资源和 Fcitx gettext 是两条独立本地化链路。

#### 修复

- `scripts/compile_mo.py` 负责将 PO 编译为 little-endian GNU MO。
- `scripts/setup-local-native-deps.sh` 的 `msgfmt` 包装器在普通 PO 编译场景调用该脚本。
- 删除旧生成的 `.mo` 后重新执行 `:app:assembleDebug`，避免 Gradle/Ninja 复用错误产物。

#### 判定标准

```bash
xxd -l 8 app/src/main/assets/usr/share/locale/zh_CN/LC_MESSAGES/fcitx5.mo
# 正确 little-endian GNU MO 魔数：de12 0495

python3 - <<'PY'
import gettext
path = "app/src/main/assets/usr/share/locale/zh_CN/LC_MESSAGES/fcitx5.mo"
with open(path, "rb") as stream:
	translations = gettext.GNUTranslations(stream)
print(translations.gettext("Hotkey"))
print(translations.gettext("Behavior"))
PY
# 期望：快捷键、行为
```

最终还需安装 APK、强制停止进程、重新启动，并用 UI dump 或截图确认动态配置页已显示中文。仅检查 `strings.xml` 或构建成功不足以证明修复有效。

### 单仓库合并操作（2026-08-04）

原根项目与 `fcitx5-android/` 各有一套独立 Git 历史，无共同祖先。合并过程：

1. 将 `fcitx5-android` 提交树完整导入为根仓库的 `fcitx5-android/` 子目录：
   ```bash
   git rm -r --cached fcitx5-android
   git read-tree --prefix=fcitx5-android/ origin/main
   ```

2. 用无关历史合并保留源码提交 `3c62d79d` 作为第二父节点，不改变工作树：
   ```bash
   git merge --strategy=ours --allow-unrelated-histories --no-edit origin/main
   ```

3. 在根 `.gitmodules` 中为所有 gitlink 添加 `fcitx5-android/` 前缀路径映射，复制嵌套 `.gitmodules` 中的 URL 和 shallow 属性。

4. 将旧嵌套仓库的 `modules/` 子模块元数据迁移到根 `.git/modules/fcitx5-android/`，批量重写各 gitfile 指针和 `core.worktree`：
   ```bash
   # 记录映射
   while read -r gitfile; do
     worktree="${gitfile%/.git}"
     old_gitdir=$(git -C "$worktree" rev-parse --absolute-git-dir)
     printf '%s\t%s\n' "$worktree" "$old_gitdir"
   done < <(find fcitx5-android -type f -name .git)
   # 移动 modules 目录 → .git/modules/fcitx5-android/
   # 更新所有 gitfile 和 core.worktree 配置
   ```

5. 将旧嵌套 `.git` 移出工作树归档（`/Volumes/ORICO/kemi/.kboard-fcitx5-android.git-archive-20260804`），确保 `fcitx5-android/` 不再作为独立仓库。

6. 适配构建脚本：`setup-local-native-deps.sh` 的 `bootstrap_submodules()` 检测根仓库顶层 ≠ `$ROOT_DIR` 时，计算 `submodule_path` 并限定子模块操作范围（`-- fcitx5-android`）。

验证：
- `git -C fcitx5-android rev-parse --show-toplevel` 返回 `/Volumes/ORICO/kemi/kboard`
- `git submodule status --recursive` 无 `-U+` 前缀
- `./scripts/assemble-debug-local.sh` 构建成功

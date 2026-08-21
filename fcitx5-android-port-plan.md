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

### 5.9 中文快速输入响应优化（2026-08-11）

#### 目标与基线

目标设备为 `192.168.3.62:5555`（Android 12、arm64-v8a、Display 0 + HDMI Display 2）。基线在拼音模式连续输入：

```text
zhonghuarenmingongheguo
```

共注入 23 键。按键注入结束后，最终首选“中华人民共和国”仍滞后约 2.04 秒；该轮产生 23 次 InputPanel 和 23 次 CandidateList 热事件。短前缀的候选规模尤其异常：

| 输入 | native 候选总量 | Android 首次消费量 |
|------|----------------:|-------------------:|
| `z` | 2638 | 最多 16 |
| `zh` | 1858 | 最多 16 |
| 完整测试串 | 94 | 最多 16 |

因此，卡顿不是 RecyclerView 单点问题，而是每次按键都在串行 `fcitx-main` 线程完成大候选集合的生成、排序、去重、C++ 对象包装、JNI 事件构造和 Kotlin 分发。

#### 热路径

```text
CommonKeyActionListener
  -> FcitxInputMethodService.postFcitxJob()
  -> FcitxDispatcher（单线程 fcitx-main）
  -> sendKeyToFcitxString()
  -> PinyinEngine::keyEvent()
  -> PinyinContext::update()
  -> PinyinEngine::updateUI()
  -> AndroidFrontend::updateInputPanel()/getCandidates()
  -> Fcitx.JNI.handleFcitxEvent()
  -> SharedFlow / Candidate RecyclerView
```

关键源码：

- Kotlin/JNI 事件入口：`fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/core/Fcitx.kt`
- Android native frontend：`fcitx5-android/app/src/main/cpp/androidfrontend/androidfrontend.cpp`
- Fcitx Pinyin UI 包装：`fcitx5-android/lib/fcitx5-chinese-addons/src/main/cpp/fcitx5-chinese-addons/im/pinyin/pinyin.cpp`
- libime 候选生成与排序：`fcitx5-android/lib/libime/src/main/cpp/libime/src/libime/pinyin/pinyincontext.cpp`
- libime Android 候选补丁：`fcitx5-android/patches/libime-android-pinyin-fastpath.patch`
- Fcitx Pinyin Android decoder 补丁：`fcitx5-android/patches/fcitx5-chinese-addons-android-decoder-frontier.patch`

#### 第一阶段：移除默认热事件字符串格式化

原 JNI 回调无条件执行：

```kotlin
Timber.d("Handling $event")
```

字符串模板会立即调用 `FcitxEvent.toString()`。CandidateList/InputPanel 事件包含预编辑文本、动作和候选内容，该工作发生在 `fcitx-main`，会直接阻塞后续按键。

修复方法：

1. 增加 `@Volatile verboseEventLog`，保证设置线程和 native 回调线程之间可见。
2. `start()` 和 `setLogRule()` 统一通过 `configureLogging(verbose)` 同步 Java 事件日志与 native log stream。
3. 默认不构造事件字符串；只有开发者明确开启详细日志时才执行完整 `Timber.d`。

该修改不改变候选生成、排序、学习或提交行为。实测默认模式下同一轮输入的 `fcitx-main Handling ...` 日志由 46 条降为 0；开启详细日志后仍可恢复诊断能力。

#### 第二阶段：Android 候选 top-256 部分排序

仅优化日志仍不能解决 native 大候选集合。`PinyinContext::update()` 原来对 `beginSize` 之后的全部 `SentenceResult` 执行：

```cpp
std::sort(candidateBegin, candidates.end(), std::greater<>());
```

随后 Fcitx Pinyin 层会遍历全部结果，生成 `PinyinCandidateWord`、去重表、注释和符号候选；但 Android frontend 初次最多只封送 16 项。

Android 专用策略：

1. 保留 decoder 产生的 n-best 前缀（`[0, beginSize)`），不改变首选句来源。
2. 候选后缀不超过 256 项时继续使用原全量排序。
3. 超过 256 项时使用 `std::partial_sort` 选出分数最高的 256 项，并删除尾部低分项。
4. 后续原有去重、`wordCandidateLimit`、候选包装和分页逻辑保持不变。
5. 代码受 `#if defined(__ANDROID__)` 限定，桌面和其他平台仍使用原算法。

伪代码：

```cpp
if (candidateCount > 256) {
    partial_sort(candidateBegin, candidateBegin + 256, candidateEnd, greater);
    erase(candidateBegin + 256, candidateEnd);
} else {
    sort(candidateBegin, candidateEnd, greater);
}
```

选择 256 而不是直接截成 16 的原因：

- 首屏仍只消费 16 项，能覆盖实际可见候选。
- 保留足够的展开和分页候选，降低罕见字被过早丢弃的风险。
- `partial_sort` 按原分数选 top-N，不是按生成顺序粗暴截断。
- 完整测试串只有 94 项，不触发裁剪，因此“中华人民共和国”等长句候选集合保持原样。

该阶段减少的是数千候选的全量排序以及后续大部分去重/包装开销，但仍会先构造全部 `SentenceResult`，因此只是中间版本。

#### 第三阶段：生成期有界堆与移动端 decoder frontier

再次逐键计时后确认，top-256 部分排序仍有两个问题：

1. `latticeNode.toSentenceResult()` 会沿完整 lattice 路径创建数组，旧实现仍对数千节点全部执行，再在排序后丢弃。
2. decoder 默认使用桌面参数 `beamSize=20`、`frameSize=40`；长拼音后半段单键可达到 137–359 ms，连续触控会在 `fcitx-main` 形成明显积压。

最终 Android 专用策略如下：

1. 候选枚举阶段维护容量 256 的最小堆，先用 `latticeNode.score() + adjust` 与堆顶比较。
2. 分数无法进入 top-256 的节点不调用 `toSentenceResult()`，从源头避免路径遍历和对象分配。
3. 保留 decoder 产生的 n-best 句子前缀，256 项排序、原去重、学习、分页及提交逻辑继续使用原实现。
4. Fcitx Pinyin 在 Android 构造时将 decoder frontier 设为 `beamSize=8`、`frameSize=16`；`nbest`、词频模型和分数公式不变。
5. 两项修改均受 `#if defined(__ANDROID__)` 约束，桌面及其他平台保持上游默认行为。
6. 两个上游 gitlink 的修改分别保存为根仓库 patch，由 `setup-local-native-deps.sh` 逐项幂等校验和应用。

固定容量堆的核心判断为：

```cpp
const auto score = latticeNode.score() + adjust;
if (heap.size() == 256 && score <= heap.front().score()) {
    return; // 不构造 SentenceResult
}
```

设备端回归测试 `FcitxTest.testPinyinFastPathCandidateQuality` 显式创建并聚焦 Android input context、激活拼音、预热一次模型，然后同步走 `Fcitx.sendKey -> native Pinyin -> getCandidates`。这避免 Display 2 上 ADB 合成触控不能可靠点击 IME 键位的问题，同时仍覆盖实际 native/JNI 候选热路径。

`192.168.3.62` 的同口径结果：

| 版本 | `zhonghuarenmingongheguo` 23 键 |
|------|-----------------------------------------:|
| 生成期有界堆，decoder 20/40 | 2312.87 ms |
| 有界堆，decoder 10/20 | 1219.89 ms |
| 有界堆，decoder 8/16 | 1087.12–1106.88 ms |

8/16 相比 20/40 缩短约 52%。扩展回归集覆盖“你好世界、中华人民共和国、北京、上海、中国、我爱北京、今天天气很好”；期望词均在前 16 项，其中“中华人民共和国”以及后五组实际首选保持正确。冷进程第一次访问某些首字母仍可能触发约 1 秒的字典/模型缓存成本，该成本不应与稳定连续输入耗时混为一谈，后续可单独评估后台预热，避免把卡顿简单转移到键盘启动阶段。

#### 历史性能测试口径（Debug，仅供早期基准）

> 本节保留第三阶段性能基准的复现方法，不是当前交付流程。自 2026-08-17 起，设备安装、正式验收和用户交付只允许 Release；不得把 Debug APK 安装到 `.62`、`.63` 或用户手机。

从 `fcitx5-android/` 执行：

```bash
./scripts/assemble-debug-local.sh
```

不要假定 APK 含固定 Git 哈希，应从 `app/build/outputs/apk/debug/` 查找实际 arm64-v8a 产物。验证必须包含：

1. `adb install -r` 安装新 APK，并确认目标包进程已重启。
2. 主屏输入完整测试串，确认首选仍为“中华人民共和国”。
3. 输入 `z`、`zh` 和完整测试串，记录 CandidateList 总量与最后一次候选事件时间。
4. Display 2 聚焦真实输入框，确认 `mCurTokenDisplayId=2`、`mInputShown=true`。
5. 使用真实副屏触控执行快速连打；该 ROM 的 `adb shell input -d 2 tap` 对 IME 键位只产生按压视觉态，不能代替整句触控计时。
6. 过滤 `FATAL EXCEPTION`、`AndroidRuntime` 及 Fcitx native 错误。

可重复的设备端热路径测试：

```bash
./scripts/assemble-debug-local.sh :app:assembleDebugAndroidTest
adb -s 192.168.3.62:5555 install -r app/build/outputs/apk/debug/*arm64-v8a-debug.apk
adb -s 192.168.3.62:5555 install -r app/build/outputs/apk/androidTest/debug/*debug-androidTest.apk
adb -s 192.168.3.62:5555 shell am instrument -w -r \
  -e class 'org.fcitx.fcitx5.android.FcitxTest#testPinyinFastPathCandidateQuality' \
  org.fcitx.fcitx5.android.debug.test/androidx.test.runner.AndroidJUnitRunner
```

当前验证状态：第三阶段已完成 arm64 NDK/Debug 构建、`192.168.3.62` 覆盖安装和设备端七组候选回归；测试返回 `OK (1 test)`，过滤日志无输入法相关崩溃。Display 2 浏览器输入框确认 `mCurTokenDisplayId=2`、`mInputShown=true`、`mIsInputViewShown=true`。正式 Release `b97ee4b1` 已安装到 `192.168.3.63`，正式服务已启用并设为默认。为避免触发该 ROM 已确认的侧载包清理，本轮正式安装后不再启动定制双屏浏览器。

#### 签名核验

本轮 debug APK 与仓库现有 Release APK 的实际证书相同：

```text
DN: CN=Android Debug, O=Android, C=US
SHA-256: fc84f538928007fb20d1ee43b8fb6bde465708c694b86fdd6a012fef19e2d5aa
```

该证书不是 AOSP platform 证书。签名应以 `apksigner verify --print-certs <apk>` 的实际输出为准，不应依据旧文档名称推断。

#### 子模块集成注意事项

`libime` 与 `fcitx5-chinese-addons` 指向上游 gitlink，不能把只存在本机的子模块提交写入根仓库 gitlink，否则 GitHub 上的项目无法克隆该提交。项目采用根仓库跟踪补丁的方式交付：

- 候选有界堆保存在 `patches/libime-android-pinyin-fastpath.patch`。
- decoder frontier 保存在 `patches/fcitx5-chinese-addons-android-decoder-frontier.patch`。
- `scripts/setup-local-native-deps.sh` 在子模块初始化后逐项执行 `git apply --check` 并应用补丁。
- 脚本用反向检查识别“已经应用”，重复执行不会二次修改。
- 新机器仍从官方 libime gitlink 获取源码，再自动应用 KBoard 补丁，发布构建可复现。

执行本地构建后工作树会显示：

```text
m fcitx5-android/lib/libime/src/main/cpp/libime
m fcitx5-android/lib/fcitx5-chinese-addons/src/main/cpp/fcitx5-chinese-addons
```

这是补丁已应用到子模块工作树的预期状态，不应暂存 gitlink。正式提交只包含根仓库中的补丁、应用脚本、Kotlin 修改与文档；生成目录 `lib/fcitx5/src/main/cpp/prebuilt` 同样不能纳入版本控制。

### V900 Android 12 候选标识、异常连键与 D0/D2 切屏（2026-08-17）

#### 候选视觉状态

- “直命中”不是简单的 `position == 0`，还必须存在活动组合态。软键盘同时跟踪 `ClientPreeditEvent` 与 `InputPanelEvent` 的 `preedit`、`auxUp`、`auxDown`；组合态结束后仍保留的候选属于联想，不着色。
- 软件候选栏使用 `AutoScaleTextView`。该控件的 `onDraw()` 调用 `drawText(text.toString(), ...)`，颜色 Span 不参与绘制；改变候选颜色必须调用 `setTextColor()` 更新 `currentTextColor`。
- 硬件键盘浮动候选使用普通 `TextView`，可通过 Span 单独设置候选正文颜色。两条 UI 路径必须一起验证。

#### 物理键盘去毛刺

- 过滤维度为 `(deviceId, keyCode)`，只处理真实物理设备的可打印键；虚拟键、`FLAG_VIRTUAL_HARD_KEY`、修饰键和控制键不进入过滤。
- 当上一可打印键已经释放、另一可打印键在 `[0, 12ms)` 内按下时，丢弃该按下以及同键对应的释放。负间隔表示真实按键重叠，不能丢弃；`repeatCount > 0` 也必须保留。
- 每次 `onStartInput()` 清空状态，避免跨编辑器残留。日志只记录实际被丢弃的异常按下，不在正常按键热路径输出。

#### 厂商双屏协议与 token 中继

V900 系统包 `com.newlink.device.ime` 暴露受控广播：

```text
action: com.newlink.action.SET_DISPLAY_IME_POLICY
package: com.newlink.device.ime
extras: display_id=2, mode=local|fallback
```

- `local` 对应 Display 2 本地 IME（D2），`fallback` 对应回退到默认显示屏（D0）。
- Android 12 仅在创建新 IME token 时应用该策略；`requestHideSelf()`/`forceShowSelf()` 会复用旧 token，不能迁移。
- KBoard 使用 ordered broadcast，等待厂商 receiver 应用策略后切到同包 `DisplaySwitchInputMethodService`。中继获得新 token 后立即调用 `switchInputMethod()` 返回主 `FcitxInputMethodService`，因此最终默认输入法仍是 KBoard。
- 中继必须在目标设备一次性启用：

```bash
adb -s 192.168.3.62:5555 shell ime enable \
  org.fcitx.fcitx5.android/.input.DisplaySwitchInputMethodService
adb -s 192.168.3.62:5555 shell ime set \
  org.fcitx.fcitx5.android/.input.FcitxInputMethodService
```

验收时分别点击 D2 与 D0 的切屏键，并检查：

```bash
adb -s 192.168.3.62:5555 shell settings get secure default_input_method
adb -s 192.168.3.62:5555 shell dumpsys window windows
```

期望默认输入法始终是主 KBoard，IME window 的 `mDisplayId` 按顺序为 2→0→2。中继 APK 组件由 `BIND_INPUT_METHOD` 权限保护，不创建输入视图，也不读取、提交或保存用户文本。

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

### V900 全键盘视觉比例（2026-08-17）

- 全键盘必须使用 `KeyboardWindow` 注入的活动主题，不得固定传入 `AMOLEDBlack`；`InputView.setDesktopKeyboardMode()` 中也不得再覆盖为纯黑背景。
- 六行桌面键盘不能使用不受限制的整屏 `matchParent`。高度以可用宽度、15 键单位和 6 行主键为基础计算，并限制在屏高 35%–72%，当前 V900 左右各保留 12dp；全键盘键帽使用 3dp 间距与 10dp 圆角，不能沿用普通三行键盘的 6dp 间距。
- 颜色职责保持一致：`keyboardColor` 为面板底色，`keyBackgroundColor` 为字符键，`altKeyBackgroundColor` 为功能键，`accentKeyBackgroundColor` 为激活态；候选直命中仍独立使用 `#4285F4`，不受本次视觉改版影响。
- 验收时必须同时观察空闲态、拼音候选态和修饰键选中态，并确认退出全键盘、语音、Ctrl+Space、方向键、D0/D2 切屏入口行为未改变。

### Release-only 发布、平台签名与远程回车（2026-08-17）

#### 当前发布口径

- 所有设备安装、交互验收和交付只使用 `org.fcitx.fcitx5.android` Release；Debug 包不得作为设备验证或交付产物。
- 从 `fcitx5-android/` 执行 `./scripts/assemble-release-local.sh`。签名参数通过本机环境变量注入，不把口令写入源码、脚本或文档。
- 不假定 APK 文件名。构建后从 `app/build/outputs/apk/release/` 读取实际的 `org.fcitx.fcitx5.android-<git>-arm64-v8a-release.apk`。
- 发布前必须用 `aapt dump badging` 核对包名、`versionCode`、`versionName` 和 ABI，再用 `apksigner verify --verbose --print-certs` 核对 v1/v2 签名。
- 当前正式签名为 AOSP Android 平台证书，SHA-256：`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。早期文档中的 `fc84f5...` 是另一把 Android Debug 证书，只适用于对应历史产物，不能再用于当前设备覆盖安装。
- 2026-08-17 当前安装到 `.63` 的正式版本为 `f6b7271c`；安装后首次启动或首次唤起 KBoard 会自动启用同包中继，默认输入法仍必须保持主 `FcitxInputMethodService`。

#### `.63` Mali-G52 水族渲染验证（2026-08-21）

- `.63` 为 Android 12/API 31、arm64-v8a、Mali-G52、OpenGL ES 3.2，D0/D2 均为 1920×1280@60Hz；全局键盘内部水面限制到 1440 宽。
- Release 构建成功不代表 GLSL 可在目标驱动运行。Mali 编译器会拒绝保留标识符 `patch`，首版因此在 `KBoardAquarium` 日志中停止；改用 `koiPatch` 后程序正常链接。
- `f4da0687` Release 的 D0 实测内部 surface 为 1440×471，10 条鱼连续 29.3–29.9 FPS，未发生 EGL/GL 或应用崩溃。`ccbb58d7` 首次共享 `swimPhase` 时发生双重门控，`c8c710b0` 虽恢复速度但仍缺少左右胸鳍物理和群体行为，`027a4630` 建立了五层池塘代理。当前 `44fdc79e` 进一步把投喂急转、摆鳍、推力和到点制动闭环：左右胸鳍以固定鳍根做三维铰接形变，背对触点时张鳍制动并把尾鳍推力重定向为转身，对准后才释放高速推进；水面改为低振幅微涟漪。最终 Release 已在 `.63` 启动并以普通字母键实测，平均触点距离从 `0.615` 降至 3.11 秒 `0.115`，最远鱼 `0.181`，10 条鱼保持 29.2–29.9 FPS，无 EGL/GL 或应用崩溃。APK SHA-256 为 `b8b433d9466867207498ed7c815921208f079dedae0faea8d83477961e0c10b8`。
- 全局语音键恢复为底部白色麦克风，语音链恢复 160ms 按住阈值、移动取消、partial 实时字幕、松开校准、final 预览 600ms 后单次提交；真机确认 `RECORD_AUDIO`、`INTERNET`、`ACCESS_NETWORK_STATE` 权限链未被水族界面改动破坏。远程触发会把现场音频发送至讯飞，必须得到该项明确授权后再做完整会话测试。
- 设备安装与性能验收仍按 Release-only 流程执行；水滴音的听感必须由真机扬声器人工确认，日志只能证明音轨创建和播放路径没有异常。
- 2026-08-21 后续水族迭代把内部宽度从 1440 降到 1080；同一 Mali-G52 在 1080×451、10 条鱼时恢复 29.3–29.9 FPS。最终 `e2e1813b` 使用连续 6×5 尾幕网格、根尖延迟行波、摆尾完成事件推进、C 型卷尾与一次性启动偏航脉冲、慢速胸鳍，Release 构建成功并覆盖安装到 `.63`。本轮遵照“用户自己测试、只推送”未远程启动或触摸，最终 Shader 观感仍以现场验收为准。

#### RustDesk/KEMI 远程回车

- KEMI 远程客户端包名为 `com.newlinksz.kemi.remote`。它的 Flutter/RustDesk 输入代理把组合文本、Android 编辑器动作和特殊键分成不同通路；编辑器动作可能返回成功，但 Windows 主机仍未收到 Enter。
- 仅当 `EditorInfo.packageName` 精确匹配该包时，KBoard 将回车作为一次完整的 `KEYCODE_ENTER` 按下/释放发送，使远端映射为 `VK_ENTER`；不再同时发送 `performEditorAction()`，避免 Mac 端重复提交。
- 普通 Android 应用继续遵循 `IME_ACTION_GO/SEARCH/SEND/NEXT/DONE`；目标编辑器拒绝标准动作时再兜底实体 Enter。中文仍保持两阶段确认：存在预编辑时先确认候选，预编辑为空时再提交表单或发送 Enter。
- 远程回车必须分别连接 Windows 和 Mac 做真实交互复测。Windows 无效而 Mac 有效通常表示远端特殊键路由问题，不能通过对所有应用同时发送“编辑器动作 + 实体键”解决，否则会产生双回车风险。

#### 中继自动启用与全键盘单音效

- `DisplaySwitchInputMethodService` 与主服务位于同一 APK，不是需要用户另行安装的输入法。平台签名 V900 包通过 `WRITE_SECURE_SETTINGS` 调用系统 `ime enable` 接口，仅启用这个固定同包组件；不得直接改写 enabled IME 字符串，也不得修改默认输入法或关闭其他输入法。
- Android 安装后在应用进程首次启动前不会执行代码；主 KBoard 首次被系统唤起时自动完成中继启用，用户无需进入输入法列表单独授权。非平台签名设备无法获得该签名权限，必须保持失败可见，不得绕过系统安全模型。
- 全键盘机械视觉仍保留按下位移和释放回弹，但声音只在 `ACTION_DOWN` 播放一次。桌面字符键、功能键、退出键和语音键的 `physicalReleaseSoundEnabled` 均关闭，语音完成后也不再补播释放音。

#### Android 12 双屏输入连接边界

- V900 的 `local`/`fallback` 策略决定“当前输入客户的 IME window 显示在哪个屏幕”，不会迁移输入客户本身。
- 当 `mCurClient.displayId=2` 时，`local` 使 `mCurTokenDisplayId=2`，`fallback` 使 `mCurTokenDisplayId=0`；`.62` 连续 12 次双向切换全部成功。
- 当 `mCurClient.displayId=0` 时，切换为 `local` 后 token 仍由 Android 12 绑定在 D0。日志中出现切屏请求和 IME window 重建不代表最终迁移成功，验收必须同时检查 `mCurClient.displayId` 和 `mCurTokenDisplayId`。
- 要支持 D0 输入框的键盘显示到 D2，需要目标屏代理输入客户和转发协议；仅调整中继时序、广播延迟或重选 IME 无法绕过这个系统约束。

# KBoard 输入法项目变更日志（cl）

> 记录范围：只包含 /Users/newlink/kemi/kboard 与 fcitx5-android 输入法项目。
> 记录起点：从“移植输入法”开始。

---

## V1.0 - 2026-07-23

### 主题
fcitx5-android 移植到 V900 双屏平板并完成首轮可运行。

### 过程
- 在 V900 设备（Android 12 / arm64-v8a）建立构建环境。
- 补齐构建依赖链路：SDK/NDK/CMake/ECM/gettext（含替代方案）。
- 修复构建阻塞项（子模块、插件解析、网络代理/TLS）。
- 产出 debug APK 并部署到平板，切换为默认输入法。

### 修改
- 调整 fcitx5-android 构建配置与依赖解析流程，确保可编译。
- 固化部署与启用命令（install / ime enable / ime set / 启动验证）。

### 验证
- :app:assembleDebug 通过。
- adb install 成功。
- 输入法服务启用成功并可设为默认。

### 待办
- 双屏 display 路由与输入焦点行为需要专项回归。

---

## V1.1 - 2026-07-24

### 主题
Gboard 真机基线采集与第一阶段 UI 对齐范围确定。

### 过程
- 采集 Gboard 在主屏空闲态、拼音候选态、双屏状态下的实际表现。
- 对照现有输入法 UI 架构，定位可改动层级（InputView / KeyboardWindow / Bar / Candidate）。
- 制定第一阶段策略：优先 Kotlin UI 层，先不改 C++ 引擎。

### 修改
- 输出第一阶段改造清单：键位排布、按键样式、工具栏高度、候选栏样式。
- 明确交互保留项：空格滑动、退格重复、Shift 三态、长按弹窗等。

### 验证
- 基线截图与行为矩阵建立完成，可用于逐项回归对比。

### 待办
- Display 2 输入框焦点下的 IME 弹出与触摸路由闭环验证。

---

## V1.2 - 2026-07-24

### 主题
讯飞 ASR 链路沉淀与模式规范化（按住说话 / 自动聆听）。

### 过程
- 梳理参数读取与鉴权链路：Settings.Global -> HTTP auth -> WebSocket。
- 梳理识别会话状态机：started、partial、final、end 包与异常处理。
- 明确两种模式的差异参数（如 cloud_vad_eos）与提交时机。

### 修改
- 沉淀可复用 ASR 接口文档。
- 统一事件语义为 Partial / Final / Error，降低 UI 与协议耦合。

### 验证
- 识别协议字段、签名逻辑、会话流程已形成统一文档。

### 待办
- 断网、鉴权失败、WS 中断场景的自动化回归与监控补齐。

---

## V1.3 - 2026-07-24

### 主题
ASR 交互与 KEMI 品牌化收口。

### 过程
- 按需求调整语音交互：说话过程中实时显示 partial，结束后显示最终校准文本再统一提交。
- 完成输入法界面文案品牌化，统一到 KEMI。
- 完成图标方案切换（蓝底 + 白圆 + KEMI 字样）并重新打包安装。

### 修改
- 语音回调显示路径从编辑区 composing 收口到输入法栏实时字幕。
- 调整 final 提交节奏：先展示 final，再延时 commit。
- 更新多语言 strings 的品牌文案与 app name debug 后缀策略。

### 验证
- 构建通过，APK 安装到目标机成功。
- 文案值扫描无旧品牌残留（键名保留不影响显示）。
- 语音链路日志与回调路径已对齐设计。

### 待办
- 补全真机可视化证据：按住说话过程帧 + 松手校准后帧（同一次会话）。

---

## V1.4 - 2026-07-26

### 主题
ASR 稳定性、中文风控提示与语音按钮视觉态收口。

### 过程
- 依据真机崩溃日志逐段恢复 ASR 链路：权限 Activity、录音权限、网络权限、明文网络策略、鉴权与 WebSocket。
- 将语音入口改为按住达到阈值后启动，补充移动取消、短按取消和权限页弹出节流。
- 增加联网预检查；断网时直接给出中文提示，不进入录音和鉴权流程。
- 梳理连接中、听写中、收尾中、权限不足、参数缺失、麦克风异常等用户提示。

### 修改
- Manifest 声明 `RECORD_AUDIO`、`INTERNET`、`ACCESS_NETWORK_STATE` 和 `VoicePermissionActivity`。
- application 绑定 `networkSecurityConfig`，仅对讯飞所需域名开放明文 HTTP/WS。
- `KawaiiBarComponent` 增加按住阈值、取消阈值、权限节流、网络预检、中文错误映射和生命周期清理。
- `IdleUi`、`ToolButton` 增加语音活动态图标着色，识别期间使用系统绿色 `0xFF34C759`。
- debug/release 与 monochrome 四套启动图标统一为 MIC + 键盘结构。

### 验证
- 修复 `ActivityNotFoundException`、缺少 `INTERNET`、明文策略拦截和缺少 `ACCESS_NETWORK_STATE` 引发的崩溃。
- 多轮执行构建、安装、强制停止、启动、logcat 检查；未再出现对应崩溃签名。
- 断网、权限不足、网络策略异常均有中文可恢复提示。

### 待办
- 建立 ASR 断网、鉴权失败、WebSocket 中断和 Final 超时的自动化回归用例。

---

## V1.5 - 2026-07-26

### 主题
KEMI 设置页品牌化与动态名称中文化。

### 过程
- 将设置首页两个一级分类统一为 `KEMI` 和 `Android`。
- 排查“入口中文、点击后英文”的差异，确认输入法与附加组件名称来自 Fcitx 运行时，而非 Android `strings.xml`。
- 从实际打包的 Fcitx 配置中提取输入法和附加组件名称，建立中文显示映射。

### 修改
- `MainFragment` 将 `Fcitx` 分类改为 `KEMI`。
- 补齐默认与 `zh-rCN` 资源中的全局选项、输入法、附加组件及相关提示。
- 新增 `NameLocalization`，覆盖英文、拼音、双拼、五笔、五笔拼音、简繁转换、剪贴板、标点、快速输入等运行时名称。
- 输入法列表、配置页标题、附加组件列表和禁用确认弹窗统一使用中文显示名称。

### 验证
- 真机首页显示：`KEMI / Android`、`全局选项 / 输入法 / 附加组件`。
- 输入法页面显示：`英文 / 拼音 / 五笔拼音`。
- 附加组件页面显示：`Android 前端 / 简繁转换 / 剪贴板 / 输入法选择器 / 拼音扩展功能` 等中文名称。

### 待办
- 新增输入法或附加组件时，同步检查运行时名称是否需要加入映射。

---

## V1.6 - 2026-07-26

### 主题
修复 Fcitx 动态配置页全部回退英文，并统一 GitHub 完整项目备份。

### 过程
- 系统与应用 Locale 均确认是 `zh-CN`，Fcitx 启动日志确认 `locale=zh_CN:zh`。
- APK 中存在 `zh_CN/LC_MESSAGES/*.mo`，但文件头为 `# SOME DESCRIPTI...`，证明其实际是被改名的 PO 文本，不是 GNU MO 二进制。
- 追踪到 `setup-local-native-deps.sh` 的 `msgfmt` 占位脚本：它直接复制输入文件，使构建表面成功但 gettext 加载失败。
- 将原本嵌套且被根 `.gitignore` 忽略的项目源码作为完整快照同步到 GitHub `main`，清理旧 backup 分支。

### 修改
- 新增 `scripts/compile_mo.py`，将 PO 正确编译为 little-endian GNU MO。
- 修正本地依赖脚本，普通 PO 编译调用真实 MO 编译器；desktop/xml 模板继续走原有生成路径。
- 清理旧生成翻译并重新构建，最终 MO 魔数为 `de 12 04 95`。
- GitHub `main` 直接包含完整 `fcitx5-android` 项目结构；删除旧 `backup-full`、`backup-snapshot`、`kemi-latest` 分支。

### 验证
- Python gettext 从新 MO 读取 `Hotkey -> 快捷键`、`Behavior -> 行为`、`Share Input State -> 共享输入状态`。
- 真机全局选项显示“快捷键、行为、默认激活输入法、重新聚焦时重置状态、共享输入状态”。
- `:app:assembleDebug` 成功，APK 安装成功，logcat 无 FATAL EXCEPTION。
- 运行日志确认 `locale=zh_CN:zh`，证明修复点是翻译产物而非系统语言。

### 待办
- CI 增加 MO 魔数检查，阻止 PO 文本再次以 `.mo` 扩展名进入 APK。
- 在无本机 gettext 的环境中持续验证项目内编译器与全量 PO 兼容性。

---

## V1.7 - 2026-07-26

### 主题
输入法精简到仅中文拼音+英文，启动速度定量分析与反思。

### 过程
- 用户要求：默认只有中文拼音和英文，切换按钮只在这两者间循环；其他输入法（五笔/双拼/自然码/Rime）不编译进去，期望提速。
- 资产盘点：`inputmethod/` 下 6 个 .conf（pinyin/db/wbx/wbpy/zrm/shuangpin），`table/` 下 4 个 .dict（合计 ~8.5MB），`addon/table.conf`，`:plugin:rime` Gradle 模块。
- Fcitx5 引擎依赖链分析：pinyin → core + punctuation（pinyinhelper 为可选），table 依赖 pinyinhelper 而非反向；删除 table 不影响拼音。
- 确认精简对启动速度的理论影响：`DataManager.sync()` 少拷贝词典文件约省 30-60ms，`startupFcitx` 少加载 libtable 约省 50-80ms，总计后台约 80-140ms。但主线程反射阻塞（~1000ms）和键盘视图构造（~290ms）与输入法数量无关，精简本身对首帧提升有限。
- 启动瓶颈精确测量（分段耗时日志）：`setupScope` 中 `DynamicScope` 对首个 `Dependent` 触发 Kotlin `findSuperGenericTypeRecursively` 反射耗时 **817ms**（第一个），后续每个组件 11-46ms，是冷启动 2226ms 的头号瓶颈。
- 验证了几个优化方向：
  - 反射 type 覆盖（10 个类 `::class` + 3 个基类 `javaClass.kotlin`）：`setupScope` 1032ms → 31ms，`onWindowShown` 2226ms → 1139ms ✅ 安全
  - NumberKeyboard 懒加载（split lazy HashMap）：省约 31ms ✅ 安全
  - LinearLayout 行布局替代 ConstraintLayout：省约 60ms，但 margin 计算参数有误 ❌
  - `onCreate` 预构造 InputView：阻塞 `onCreate` 导致总时间变差 ❌
  - `placeholder.post` 异步占位：破坏输入视图替换流程 ❌
- 最终决定：先做安全的精简（删资产文件+Rime编译停止），不动 Kotlin 布局和生命周期。后续单独实施反射修复。

### 修改
- `zh_CN` 默认配置改为 `DefaultInputMethod=pinyin` + `ExtraLayout=us`（移除 rime）。
- 删除 `inputmethod/wbpy.conf`、`db.conf`、`wbx.conf`、`zrm.conf`、`shuangpin.conf`。
- 删除 `table/wbpy.main.dict`（5.4MB）、`wbx.main.dict`（1.7MB）、`db.main.dict`（145KB）、`zrm.main.dict`（1.0MB）。
- 删除 `addon/table.conf`。
- `settings.gradle.kts` 注释 `include(":plugin:rime")`，不编译 Rime 插件（省 ~30MB APK 体积）。
- 保留 `inputmethod/pinyin.conf`、`addon/pinyin.conf`、`addon/pinyinhelper.conf`——pinyin 依赖链完整。
- 所有 Kotlin 层改动已通过 `git checkout` 完全还原，当前代码与 9767433 commit 一致（仅多 assets 精简 + Rime 注释 + zh_CN 修改）。

### 验证
- `:app:assembleDebug` BUILD SUCCESSFUL。
- `adb shell pm clear` 清旧数据 + install + `ime set` + 启动 Notes 点击搜索框：键盘正常弹出，`mInputShown=true`。
- logcat 确认 `Loaded addon pinyin`，无 table/rim/wubi/wbpy 加载日志。
- 语言切换键应在拼音和英文之间循环（待用户真机确认）。

### 待办
- 反射 type 覆盖优化（安全、已验证有效、约节约 1000ms）：待单独实施。
- APK 大小对比（精简前 vs 精简后）。
- 拼音+英文双输入法切换的真机交互验收（确认循环行为正确、无残留输入法选项）。

---

## V1.8 - 2026-07-27

### 主题
降低 RustDesk 跨屏唤起 KBoard 时的输入法引擎停启延迟。

### 过程
- RustDesk 的目标屏 `KeyboardProxyActivity` 可在约 0.4-0.5 秒内启动，但首次 `showSoftInput()` 返回 `false`，整体显示需要约 2.8-3.7 秒。
- 日志确认 Android 在 Display 0 与 Display 2 之间迁移 IME 时会短暂销毁并重建 `FcitxInputMethodService`。
- 原有 `FcitxDaemon.disconnect()` 在最后一个客户端断开时同步执行 `realFcitx.stop()`；停止操作被正在加载的拼音任务阻塞约 2.3 秒，新 Service 随后只能重新启动 Fcitx。

### 修改
- `FcitxDaemon` 在最后一个客户端断开后增加 2 秒停止宽限期。
- 新客户端在宽限期内连接时取消待执行的停止任务，跨屏 Service 重建可直接复用热态 Fcitx。
- 显式重启、导入配置时的强制停止接口保持原有语义。

### 验证
- `:app:compileDebugKotlin` 通过。
- `./gradlew clean` 后执行 `./scripts/assemble-debug-local.sh`，全量构建成功并安装到 `192.168.0.111:5555`。
- 设备安装 APK 与本地产物 SHA-256 一致。
- RustDesk 从 Display 0 连续 5 次在 Display 2 打开并收起键盘，状态均完成 `opening -> visible -> closing -> hidden`，无透明 Activity 残留或崩溃。
- 点击到 `visible` 实测约 1.11-1.42 秒，未再出现跨屏期间的 Fcitx stop/start；修复前约为 2.8-3.7 秒。

### 待办
- 在 RustDesk 从 Display 2 启动、键盘目标为 Display 0 的方向执行同口径计时。
- 剩余约 0.6-0.9 秒主要来自设备 ROM 的跨屏 IME token 迁移与绘制；继续优化前必须保留每次新建目标屏代理 Activity 的可靠路径。

---

## V1.9 - 2026-08-03

### 主题
悬浮键盘拖动条减薄，并将四角缩放指示改为圆角外侧的醒目可拖动控件。

### 过程
- 确认底部拖动条实际厚度由 48dp 触控区与 layer-list 上下留白共同决定，不能缩短整个触控区，否则会同时压缩右侧调整大小按钮。
- 确认原四角指示位于 `keyboardView` 内部，会受悬浮窗圆角裁剪，无法形成 Gboard 式外侧指示。
- 将角标移到输入视图外层后，真机发现顶部外露部分超出 IME 原可触摸上边界；同步扩展调整模式下的 `visibleTopInsets` 后恢复完整拖动能力。

### 修改
- `bkg_floating_keyboard_handle.xml` 保留 48dp 触控区，将灰色拖动条可见厚度从约 20dp 降为 8dp。
- `ic_resize_corner_24.xml` 改为白色衬边加蓝色粗线的圆角 bracket，四个方向复用旋转。
- `InputView.kt` 将四个 48dp 角标移到圆角裁剪层外，中心对齐四角并随悬浮窗同步平移；调整模式为外侧角标保留屏幕边距。
- `FcitxInputMethodService.kt` 仅在调整模式将 IME 可触摸上边界向外扩展 24dp，使顶部外侧角标可直接拖动。

### 验证
- `:app:assembleDebug` 构建成功，APK 安装到 `192.168.3.63:5555` 成功。
- Notes 真机截图确认底部拖动条明显变薄，四个蓝白圆角指示完整位于悬浮倒角外侧且无裁剪。
- 分别从底部角标和顶部外侧角标执行拖动，键盘宽高均发生变化，松手后调整模式正常退出。
- Android/Kotlin/XML 诊断无错误，过滤 logcat 无相关崩溃。

### 待办
- 在其他主题和更高屏幕密度设备上回归蓝白角标的对比度与外侧间距。

---

## V1.10 - 2026-08-04

### 主题
发布可追溯的 KEMI arm64 Release APK，并完成正式包覆盖安装与 GitHub 备份。

### 过程
- 先提交已验证的悬浮键盘改动，再重新构建 Release，避免 APK 使用旧提交哈希标识脏工作区代码。
- 对比设备现有正式包与本机签名证书，确认标准 Android keystore 证书一致，可使用 `adb install -r` 无损覆盖安装。
- 使用仓库 `assemble-release-local.sh` 构建签名 APK，并校验包名、版本、ABI、签名和 SHA-256。

### 修改
- 源码提交为 `3c62d79d`（`feat: refine floating keyboard resize controls`）。
- Release 产物为 `bin/KEMI-0.1.2-126-g3c62d79d-arm64-v8a-release.apk`。
- 校验文件为 `bin/KEMI-0.1.2-126-g3c62d79d-SHA256SUMS.txt`。
- APK 包名为 `org.fcitx.fcitx5.android`，仅包含 `arm64-v8a` ABI。
- GitHub `main` 包含完整项目、文档和 APK；源码改动对应提交 `3c62d79d`。

### 验证
- Release 构建 `BUILD SUCCESSFUL`，APK 签名验证通过，SHA-256 校验返回 `OK`。
- `adb -s 192.168.3.63:5555 install -r` 覆盖安装成功，设备版本为 `0.1.2-126-g3c62d79d`。
- 正式输入法 `org.fcitx.fcitx5.android/.input.FcitxInputMethodService` 已设为默认。
- Notes 真机输入框中 `mInputShown=true`、`mIsInputViewShown=true`，截图确认 KEMI 正常显示，过滤 logcat 无相关崩溃。
- GitHub 完整项目已推送成功。

### 待办
- 后续 Release 应继续遵循“先提交源码、再构建带提交哈希的 APK、最后生成校验文件”的顺序。

---

## V1.11 - 2026-08-04

### 主题
统一 KBoard 为单一根仓库和单一 GitHub `main` 分支。

### 过程
- 原根项目与 `fcitx5-android` 各自拥有独立且无共同祖先的 Git 历史，普通快进无法同时保留完整文档/APK 与源码提交。
- 将 `fcitx5-android` 提交树完整导入根仓库子目录，并使用一次无关历史合并把源码提交 `3c62d79d` 纳入 `main` 祖先，未使用强推。
- 将旧快照中展开的上游源码规范化为 19 个 gitlink，并在根 `.gitmodules` 中配置带 `fcitx5-android/` 前缀的对应路径。
- 将旧嵌套 `.git` 的 22 个顶层及递归子模块元数据迁移到根 `.git/modules/`；剩余旧仓库元数据移到项目外只读归档。

### 修改
- 根仓库成为唯一 Git 顶层，`fcitx5-android/` 不再作为独立仓库维护。
- GitHub Actions workflow 移到根 `.github/workflows/`，构建与产物路径适配 `fcitx5-android/` 子目录。
- `setup-local-native-deps.sh` 可从统一根仓库限定并初始化 `fcitx5-android/` 下的递归子模块。
- README、BUILD 指南和两个 `AGENTS.md` 统一规定只使用根仓库 `main`，不再创建 preview、backup 或 release 分支。
- 删除 GitHub 临时分支 `kemi-release-0.1.2-126`。

### 验证
- 本地从根目录和 `fcitx5-android/` 查询 Git 顶层均为 `/Volumes/ORICO/kemi/kboard`，本地分支只有 `main`。
- `git submodule status --recursive` 无缺失、冲突或提交偏移，19 个根 gitlink 及其递归依赖可正常识别。
- `./scripts/assemble-debug-local.sh` 成功生成 `org.fcitx.fcitx5.android-ef8b63f9-arm64-v8a-debug.apk`。
- GitHub 远端分支检查仅返回 `refs/heads/main`。

### 待办
- 不再为发布创建独立分支；Release APK、校验文件、源码和文档均直接提交到根仓库 `main`。

---

## V1.12 - 2026-08-11

### 主题
降低快速中文输入时 `fcitx-main` 热线程的事件日志开销，并完成 V900 副屏回归。

### 过程
- 从触摸按键、Kotlin dispatcher、JNI 回调追踪到 native Pinyin `keyEvent()` / `updateUI()` 与 Android frontend 候选同步。
- 基线连续输入 `zhonghuarenmingongheguo`：23 键注入结束后，最终“中华人民共和国”候选仍滞后约 2.04 秒；同一轮产生 46 条 InputPanel/CandidateList 热事件日志。
- 定位到 JNI 事件回调会在单线程 `fcitx-main` 上无条件执行 `FcitxEvent.toString()`；短前缀 `z` 的 native 候选总量为 2638，但 Android frontend 最多只封送前 16 项。
- 第二阶段继续追入 libime `PinyinContext::update()`，确认候选在进入 Android frontend 前已对完整集合执行全量排序、去重与对象包装。
- 核验 debug APK 与现有正式 Release APK 的证书：两者均为 `CN=Android Debug, O=Android, C=US`，SHA-256 为 `fc84f538928007fb20d1ee43b8fb6bde465708c694b86fdd6a012fef19e2d5aa`，不是 AOSP platform 证书。

### 修改
- `Fcitx.kt` 增加线程可见的详细日志开关，启动和运行时日志规则统一同步该开关。
- 默认模式不再格式化或输出完整 Fcitx 事件；仅在开发者明确开启详细日志时恢复原诊断信息。
- libime 在 Android 构建中对超大候选集合改用 top-256 `partial_sort`，保留分数最高的 256 项用于候选栏和后续分页，不再对数千项执行全量排序及下游包装；非 Android 平台保持原行为。
- native 修改以根仓库 `patches/libime-android-candidate-top256.patch` 保存，由 `setup-local-native-deps.sh` 幂等应用，避免根 gitlink 引用 GitHub 无法获取的本地子模块提交。
- 不调整词频模型、候选分数或前 256 项排序，避免直接截断可见候选造成首屏质量下降。

### 验证
- `./scripts/assemble-debug-local.sh` 构建成功，APK 安装到 `192.168.3.62:5555` 成功。
- 第二阶段 native 修改通过 NDK 编译与 `./scripts/assemble-debug-local.sh` 完整构建。
- 主屏连续输入 `zhonghuarenmingongheguo`，首选候选正确为“中华人民共和国”；过滤日志确认默认模式下 `fcitx-main` 的 `Handling ...` 热事件为 0。
- Display 2 启动 `com.newlink.browser/.BrowserActivity`，输入法状态确认 `mCurTokenDisplayId=2`、`mInputShown=true`，截图确认优化版 KBoard 以拼音模式显示在副屏。
- 过滤 logcat 未发现输入法相关 `FATAL EXCEPTION`。

### 待办
- 第二阶段 APK 的设备安装和量化复测因 ADB 操作审批服务中断而未完成，不能把本地构建通过等同于真机性能达标。
- top-256 已消除全量排序及大部分下游包装；若仍不达标，下一步需要把上游 lattice 枚举改成有界堆，从源头减少 `SentenceResult` 构造，并配套候选质量回归集。
- 该 ROM 的 ADB 合成触摸可聚焦 Display 2 输入框，但对副屏 IME 键位的点击只产生按压视觉态，无法替代真实触控完成整句计时；副屏本轮验证范围为显示归属、服务状态和拼音 UI。

---

## V1.13 - 2026-08-11

### 主题
发布中文响应优化正式版、部署到 V900 设备并归档 GitHub 交付物。

### 过程
- 先将 Kotlin 热事件门控、libime top-256 补丁、幂等补丁应用脚本和排障文档提交为源码提交 `8511604e`，再构建 Release，保证 APK 内版本可追溯。
- 使用与仓库历史正式包一致的 Android Debug 证书签名；`apksigner` 验证 v1/v2 签名通过。
- 首次安装到 `192.168.3.63:5555` 成功，正式 IME 被系统识别、设为默认并启动进程，无相关 `FATAL EXCEPTION`。
- 设备启动定制双屏浏览器后，ROM 将用户侧载的正式包从用户 0 包列表移除并回退到 LatinIME；发布流程因此调整为先归档和推送，再进行最终重装，不使用 `pm clear`。

### 修改
- Release APK：`bin/KEMI-8511604e-arm64-v8a-release.apk`。
- SHA-256 文件：`bin/KEMI-8511604e-SHA256SUMS.txt`。
- 包名：`org.fcitx.fcitx5.android`；版本名：`8511604e`；版本码：`102`；ABI：仅 `arm64-v8a`。
- APK SHA-256：`b59a9585d89e0dbc5c287175eae4d9838867e34b436af6b2d9495f0bf8cb86dd`。
- 签名证书 SHA-256：`fc84f538928007fb20d1ee43b8fb6bde465708c694b86fdd6a012fef19e2d5aa`。

### 验证
- `./scripts/assemble-release-local.sh`：`BUILD SUCCESSFUL`，native libime 补丁在构建开始时自动应用并重新编译。
- `apksigner verify --verbose --print-certs`：单签名者，v1/v2 验证通过。
- `aapt dump badging`：正式包名、`versionCode=102`、`versionName=8511604e`、`targetSdkVersion=36` 正确。
- `unzip -l`：APK 仅包含 `arm64-v8a` native 库；SHA-256 校验与归档文件一致。
- `adb install -r` 在 `192.168.3.63` 返回 `Success`；IME service 启动日志无崩溃。

### 待办
- 在不会触发 ROM 侧载包清理的 Notes/业务输入框中，用真实触控完成 top-256 版本的快速整句验收。
- 若设备在日常启动双屏浏览器后仍自动移除 KBoard，需要将正式 APK加入 ROM 白名单/预装策略；重复 ADB 安装只能恢复当前用户态，不能解决 ROM 策略本身。

---

## V1.14 - 2026-08-11

### 主题
继续降低 Android 中文连续输入的 native decoder 与候选构造延迟，并建立可重复的设备端性能回归。

### 过程
- 复查 top-256 中间实现，确认它仍先为数千 lattice 节点构造完整 `SentenceResult`，只减少了后续全量排序和包装。
- 增加逐键设备计时后发现，默认 decoder 20/40 搜索宽度下，长拼音后半段单键达到 137–359 ms；完整 23 键同步热路径为 2312.87 ms。
- 依次实测 Android decoder 10/20 与 8/16；前者为 1219.89 ms，后者稳定为 1087.12–1106.88 ms。
- 修复 androidTest 独立 APK 缺少 build-type 资源、未创建 active input context、未显式激活拼音的问题，使测试实际进入 native Pinyin 热路径而不是空上下文。

### 修改
- libime Android 候选枚举改为容量 256 的最小堆；先比较 `latticeNode.score() + adjust`，落在窗口外的节点不再构造 `SentenceResult`。
- Fcitx Pinyin Android 构造时将 decoder frontier 从默认 `beamSize=20`、`frameSize=40` 调整为 8/16；保留 `nbest`、词频、评分、学习和提交语义。
- native 修改拆分为 `libime-android-pinyin-fastpath.patch` 与 `fcitx5-chinese-addons-android-decoder-frontier.patch`，由 `setup-local-native-deps.sh` 幂等应用；非 Android 构建保持上游行为。
- `FcitxTest` 增加同步逐键耗时和七组候选质量回归，并补齐测试 input context 的 activate/focus 流程。
- `app/src/androidTest/res/values/test_resources.xml` 为独立测试 APK 提供最小资源别名，恢复 `assembleDebugAndroidTest`。

### 验证
- `./scripts/assemble-debug-local.sh :app:assembleDebugAndroidTest` 成功，主 APK 与 androidTest APK 均生成。
- 新 Debug APK 和测试 APK 覆盖安装到 `192.168.3.62:5555` 成功。
- `testPinyinFastPathCandidateQuality` 返回 `OK (1 test)`；“中华人民共和国”仍为首选，“北京、上海、中国、我爱北京、今天天气很好”均保持首选，“你好世界”仍在前 16 项。
- 完整 `zhonghuarenmingongheguo` 23 键从同口径 2312.87 ms 降至 1087.12–1106.88 ms，缩短约 52%。
- Display 2 输入框状态确认 `mCurTokenDisplayId=2`、`mInputShown=true`、`mIsInputViewShown=true`；过滤日志未发现相关 `FATAL EXCEPTION`。

### 待办
- 冷进程首次访问某些首字母仍可能有约 1 秒字典/模型缓存成本；如继续优化，应评估不阻塞键盘启动的后台预热，不能把首次按键卡顿简单转移到输入法启动。
- 第三阶段尚未构建正式 Release 或部署到 `192.168.3.63`；正式发布需重新生成可追溯版本、校验签名与 SHA-256，并避开该 ROM 的侧载包清理时序。

---

## V1.15 - 2026-08-11

### 主题
发布第三阶段中文响应优化正式版，并部署到 V900 `.63` 设备及备份 GitHub。

### 过程
- 以已验证源码提交 `b97ee4b1` 构建 Release，确保 APK `versionName` 可追溯到对应源码。
- 沿用项目上一正式版证书，构建后重新核验包名、版本、ABI、签名方案和证书指纹。
- 正式产物归档到根仓库 `bin/`，生成独立 SHA-256 文件后再提交和推送。

### 修改
- Release APK：`bin/KEMI-b97ee4b1-arm64-v8a-release.apk`。
- SHA-256 文件：`bin/KEMI-b97ee4b1-SHA256SUMS.txt`。
- 包名：`org.fcitx.fcitx5.android`；版本名：`b97ee4b1`；版本码：`102`；ABI：仅 `arm64-v8a`。
- APK SHA-256：`5be1afbd21dc5a02122a095dd3b354a4e5df92dc59ffc34c795670b2c4091d23`。
- 签名证书 SHA-256：`fc84f538928007fb20d1ee43b8fb6bde465708c694b86fdd6a012fef19e2d5aa`。

### 验证
- `./scripts/assemble-release-local.sh` 构建成功。
- `apksigner verify --verbose --print-certs`：v1/v2 验证通过，单签名者证书与上一正式版一致。
- `aapt dump badging`：包名、`versionCode=102`、`versionName=b97ee4b1`、`targetSdkVersion=36` 正确。
- `unzip -l`：APK native 库仅包含 `arm64-v8a`；归档文件 SHA-256 与构建产物一致。
- `adb install -r` 在 `192.168.3.63:5555` 返回 `Success`，设备报告 `versionName=b97ee4b1`、用户 0 `installed=true`。
- 正式服务已在系统 `ime list` 中登记、启用并设为默认；`default_input_method` 与 `mCurMethodId` 均为 `org.fcitx.fcitx5.android/.input.FcitxInputMethodService`，进程已启动。
- 安装后过滤日志未发现 KBoard 相关 `FATAL EXCEPTION`；未启动会触发 ROM 侧载包清理的定制双屏浏览器。
- `git push origin main` 成功，GitHub `git@github.com:caucy2026/kborad.git` 的 `main` 已从 `e6845076` 更新到正式发布提交 `e6019715`，完整源码、文档、校验文件和 APK 均已备份。

### 待办
- 若 `.63` 日常启动定制双屏浏览器后再次移除侧载 KBoard，仍需通过 ROM 白名单或预装策略解决；重复安装不能消除系统清理策略。

---

## V1.16 - 2026-08-11

### 主题
使用 root 从 V900 `.62` 删除旧系统 KBoard，并安装第三阶段正式版。

### 过程
- 只读确认旧包为系统应用 `org.fcitx.fcitx5.android`，版本 `0.1.2-125-g9cfa2c8f`，安装路径为 `/system/app/KBoard/KBoard.apk`。
- 删除前将旧 APK 拉取到本机 `/private/tmp/KBoard-old-0.1.2-125-g9cfa2c8f.apk`，其 SHA-256 为 `2583f1e9ef5f0f3eaf0b5ac25a7c2f837a8ec3d19452c9746b33d3233fdc3310`。
- 使用设备支持的 `adb remount`/overlayfs 将 `/system` 精确重挂载为可写；未执行 `adb disable-verity`，也未执行 `pm clear`。
- 停止旧进程后仅删除 `/system/app/KBoard`，重启确认旧系统包不再注册，再安装正式版 `b97ee4b1`。

### 修改
- `.62` 不再使用 `/system/app/KBoard/KBoard.apk` 中的旧系统版本。
- 新版作为用户应用安装到 `/data/app/.../org.fcitx.fcitx5.android.../base.apk`，并重新启用、设为默认输入法。
- 本次源码无功能改动，仅补充设备部署与验收记录。

### 验证
- `adb install` 返回 `Success`；设备报告 `versionName=b97ee4b1`、`versionCode=102`、用户 0 `installed=true`。
- `default_input_method` 为 `org.fcitx.fcitx5.android/.input.FcitxInputMethodService`，KBoard 进程正常运行。
- 在副屏浏览器地址栏聚焦后，输入法状态为 `displayId=2`、`mCurTokenDisplayId=2`、`mInputShown=true`、`mVisibleBound=true`。
- 安装、启动及副屏唤起后的过滤日志未发现 `FATAL EXCEPTION`、`ActivityNotFoundException` 或 `SecurityException`。

### 待办
- 本次系统应用删除由 overlayfs whiteout 实现；刷机、恢复出厂设置或清除 overlay/scratch 后，底层旧 APK 可能重新出现，需要重新执行移除或在 ROM 镜像中永久删除。
- 新版当前位于 `/data/app`；如果 ROM 的侧载包清理策略也在 `.62` 启用，仍应把正式版加入 ROM 白名单或预装镜像。

---

## V1.17 - 2026-08-17

### 主题
修复 V900 Android 12 双屏焦点切换导致 KBoard `bindInput` 生命周期竞态崩溃。

### 过程
- `.63` 的 crash buffer 记录到唯一一次 KBoard `FATAL EXCEPTION`：`ImsConfigurationTracker.onBindInput` 抛出 `onBindInput can be called only after onInitialize()`。
- 崩溃发生在 `com.newlinksz.kemi.remote/.KeyboardProxyActivity` 于双屏间切换焦点之后；进程 PID 从 `6442` 被系统重启为 `9525`，不是中文 decoder、ASR 或 native signal 崩溃。
- 对照 Android 12/API 31 与新版 AOSP：API 31 对未初始化 tracker 直接抛异常，新版实现对同一陈旧回调直接返回。
- 修复仅面向 `.63` 使用的 Android 12/API 31；不改变候选生成、提交、学习、ASR 或其他 Android 版本的正常绑定逻辑。

### 修改
- `FcitxInputMethodService` 在 API 31 使用兼容 `InputMethodImpl` 包装系统 `bindInput()`。
- 仅当异常消息完全匹配、堆栈确实来自 `android.inputmethodservice.ImsConfigurationTracker.onBindInput` 时忽略该 Android 12 框架竞态；其他 `IllegalStateException` 原样抛出，避免隐藏应用错误。
- 将 `super.onCreate()` 提前到 IME 服务初始化首行，先建立系统输入法窗口与生命周期，再连接 Fcitx daemon，缩短厂商回调观察到半初始化状态的窗口。
- 新增纯 Kotlin 单元测试，覆盖 API 31 精确匹配、其他 Android 版本和其他异常三组边界。
- 交付策略收口为只构建、安装和保留 Release；验证期间未启用的 Debug 包已从 `.63` 卸载。
- 修复源码提交为 `e6f7d561`；Release 归档为 `bin/KEMI-e6f7d561-arm64-v8a-release.apk`，SHA-256 为 `dd3d487d1125411c62c23490e7ddfba90a89eae3d9a3596592ba48094c0d1139`。

### 验证
- `:app:testDebugUnitTest --tests org.fcitx.fcitx5.android.input.Android12ImeFrameworkCompatTest`：通过。
- `./scripts/assemble-debug-local.sh`：完整构建通过，仅用于编译验证；Debug APK 未作为交付物保留在设备。
- `:app:testReleaseUnitTest` 不存在于当前 Gradle 任务图；纯 Kotlin 边界测试由现有 Debug unit-test 任务执行，正式产物仍通过完整 Release 构建验证。
- `./scripts/assemble-release-local.sh`：`BUILD SUCCESSFUL`；APK 为 `versionName=e6f7d561`、`versionCode=102`、仅 `arm64-v8a`，v1/v2 签名验证通过，证书 SHA-256 仍为 `fc84f538928007fb20d1ee43b8fb6bde465708c694b86fdd6a012fef19e2d5aa`。
- `.63` 覆盖安装返回 `Success`，正式 KBoard 保持默认；安装后进程 PID 为 `25333`。
- 使用 root 对非导出的 `KeyboardProxyActivity` 执行 8 次主屏/副屏启动投递，再执行 3 轮 LatinIME/KBoard 解绑重绑与跨屏组合；KBoard PID 始终为 `25333`，清空后的 crash buffer 无新增异常。
- 副屏便签标题输入框确认 `displayId=2`、`mCurTokenDisplayId=2`、`mInputShown=true`、`mVisibleBound=true`，截图确认 Release KBoard 实际显示；最终默认输入法仍为正式 KBoard。

### 待办
- 应用侧兼容等价于新版 AOSP 对陈旧 `bindInput` 的容错，但无法修正厂商 ROM 内部错误的回调顺序；若 ROM 可重编译，长期方案仍是回移 AOSP tracker 防护。
- 原始竞态具有偶发性，本轮压力复测没有再次命中兼容告警；仍需通过长期运行确认远程控制高频跨屏场景不再产生同类 crash。

---

## V1.18 - 2026-08-17

### 主题
借鉴豆包候选视觉层级，过滤硬件异常连键，并在 Android 12 上实现保持 KBoard 的 D0/D2 双向切屏。

### 过程
- 以用户提供的豆包输入法截图为交互基准：有拼音预编辑时只强调第一个直命中汉字，提交后继续出现的联想候选不强调；公开资料未找到豆包对该视觉规则的正式说明，因此没有把观察结果描述成厂商协议。
- 复查软键盘候选栏和硬件键盘浮动候选窗两条渲染路径，确认两者使用不同 TextView；软键盘的 `AutoScaleTextView` 自定义 Canvas 绘制会忽略颜色 Span，必须同步修改实际画笔颜色。
- 对 `.62` 的厂商系统组件进行授权后的只读分析，确认 `com.newlink.action.SET_DISPLAY_IME_POLICY` 可设置 Display 2 的 `local`/`fallback` 策略；临时导出的系统 APK 仅位于 `/private/tmp`，未复制进项目或 Git。
- Android 12 只在创建新 IME token 时读取显示策略。直接隐藏/显示不会迁移，切到其他输入法再延时返回又会因旧服务 token 失效而停留在其他输入法，因此增加同包一次性 IME 中继，拿到新 token 后立即返回主 KBoard。
- 发布阶段停止使用 `/Users/newlink/.android/debug.keystore` 的标准 Android Debug 证书，改用项目既有 `/Users/newlink/kemi/keystore/debug.keystore` 中的 AOSP Android 平台证书。由于签名不同，`.62` 与 `.63` 均先删除旧签名包再安装正式 Release；这会重置 KBoard 的应用本地设置。

### 修改
- 软键盘候选栏与硬件浮动候选窗统一使用 `#4285F4` 标识第一个直命中候选；预编辑结束后首个联想候选恢复主题文字色。
- 分别跟踪 client preedit 与 input-panel 的 preedit/auxUp/auxDown，取并集判断“正在直接转换”，避免异步事件互相覆盖颜色状态。
- 新增 `HardwareKeyAnomalyFilter`：同一物理设备上，不同可打印键在上一键释放后小于 12 ms 再按下时丢弃异常按下及对应释放；边界 12 ms、重按、修饰键、控制键、不同设备和按键重叠均保留。
- 键盘右下角增加双屏切换键；D2 发送 `fallback` 切到 D0，D0 发送 `local` 切回 D2。
- 新增受 `android.permission.BIND_INPUT_METHOD` 保护的 `DisplaySwitchInputMethodService`。它不创建输入界面、不处理或保存文本，只用于一次 token 中继；设备部署时需一次性启用该服务。
- 正式归档：`bin/KEMI-b6430413-arm64-v8a-release.apk`；SHA-256：`c5bc158796dc8099533bbc600369746aba7abcda340e9ff7f4a4ebca35e39372`；校验文件：`bin/KEMI-b6430413-SHA256SUMS.txt`。

### 验证
- `HardwareKeyAnomalyFilterTest` 与 `Android12ImeFrameworkCompatTest` 通过；完整 Release 构建通过。
- `aapt` 确认正式包为 `versionName=b6430413`、`versionCode=102`、仅 `arm64-v8a`；`apksigner` 确认 v1/v2 有效，平台签名证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- Android 12/API 31 的 `192.168.3.62` 换签重装 Release 成功；主 KBoard 和同包切屏中继均处于 enabled，默认输入法始终为 `org.fcitx.fcitx5.android/.input.FcitxInputMethodService`。
- 软键盘输入 `nihao`：首个直命中候选为蓝色，其他候选为黑色；提交后“你、的、。、了、不”等联想候选全部恢复黑色。
- 硬件输入 `nihao`：浮动候选窗首个“你好”为蓝色；提交后“的、。、了、不”等联想候选保持黑色。
- 点击切屏键完成 D2→D0→D2 往返；`dumpsys window` 分别确认 `mDisplayId=0` 和 `mDisplayId=2`，两次切换后默认输入法均未变成系统 LatinIME。
- 最终平台签名包在 `.62` 再次确认 D0/D2 新 token 与返回主 KBoard，最终聚焦副屏时 `mCurTokenDisplayId=2`；测试后恢复 `com.huanglong.portui/.MainActivity`。测试期间出现的唯一新 crash 属于 `com.newlink.notes` 自身空指针，不是 KBoard。
- 同一正式 APK 已只推送安装到 `192.168.3.63`，设备报告 `versionName=b6430413` 且主 KBoard 为默认；按“只推送、不测试”要求未做交互测试。`.63` 的切屏中继未启用，等待该设备的单独明确授权。
- 本轮未安装或交付 Debug APK；单元测试由项目现有的 Debug unit-test 任务承载，但设备与归档产物均只有 Release。

### 待办
- 12 ms 是面向当前故障驱动的保守去毛刺阈值；极少数真实超高速“完全释放后立即按另一键”的输入可能被过滤。若后续有原始驱动时间戳，应按设备统计分布复核阈值，不应扩大到几十毫秒。
- 切屏中继需要设备部署阶段一次性加入 enabled IME 列表；若未启用，KBoard 会提示“屏幕切换服务未启用”并保留当前输入法，不会切到第三方输入法。
- 同包中继会作为第二个 KBoard 项出现在系统已启用输入法列表中；它被手工选中时也会立即返回主 KBoard，这是 Android 12 公共 IME API 下换取可靠 token 重建的可见代价。
- 当前切屏依赖 V900 ROM 的 `com.newlink.device.ime` 系统组件；其他 Android 12 设备若没有该组件，按钮不能迁移显示屏。

---

## V1.19 - 2026-08-17

### 主题
重做全键盘配色与面板比例，保持输入、候选、语音和双屏功能不变。

### 过程
- 源码确认全键盘绕过当前主题，固定使用 `AMOLEDBlack`；`InputView` 又对键盘容器、背景和桌面操作区重复写入纯黑色。
- 全键盘切换时高度使用 `matchParent`、左右边距强制为 0，六行键盘因此可能扩展成整屏黑色面板，视觉重量和键帽比例均与普通键盘脱节。
- 保留原有六行键位、功能键宽度、修饰键状态、物理按压反馈、候选栏、语音入口和语言切换行为，仅调整视觉主题与几何约束。

### 修改
- 全键盘改为复用当前活动主题：默认浅灰蓝键盘底、白色字符键、浅蓝灰功能键和蓝色强调态；候选栏、工具栏、按键和操作区使用同一配色体系。
- 清除全键盘路径中的 `Color.BLACK` 与 `AMOLEDBlack` 强制引用；退出键、语音键及不可用态图标改用当前主题的文字层级色。
- 全键盘高度根据 15 键宽、6 行键和工具区反推，并限制在屏幕高度的 35%–72%；增加左右 12dp 留白，使主键接近方形而非横向铺满。
- 全键盘单独采用 3dp 键帽间距和 10dp 圆角，在不缩小触控区域的前提下放大可见键帽；底部操作区保持 64dp、操作按钮保持 56dp，字母字号调整为 18sp、数字符号为 17sp。

### 验证
- `:app:compileReleaseKotlin` 与 `./scripts/assemble-release-local.sh` 均通过；最终正式 APK 为 `versionName=c4d4eb75`、`versionCode=102`、仅 `arm64-v8a`，平台证书 v1/v2 签名验证通过；未构建或安装 Debug APK。
- 正式 Release 覆盖安装到 `.62` 成功，默认输入法保持主 KBoard；副屏普通键盘与全键盘入口正常显示统一浅色主题，当前 KBoard 进程过滤日志无 `FATAL EXCEPTION`、`SecurityException` 或 `IllegalStateException`。
- 第一轮真机全键盘截图确认黑色背景已移除、六行宽度关系正确。根据用户反馈继续把全键盘专用键帽间距由 6dp 收紧到 3dp、圆角调整为 10dp，并重新构建安装；该 ROM 的 ADB 合成触控在 IME 区域存在坐标偏移，最终键帽观感留在 `.62` 由真实触控确认。

### 待办
- 全键盘高度仍受系统显示尺寸与密度影响；除当前 1920×1280、320dpi V900 外，若以后适配窄屏设备，应补充纵向与小尺寸截图基线。

---

## V1.20 - 2026-08-17

### 主题
修复回车键在目标编辑器拒绝 IME 动作时无响应的问题。

### 过程
- 源码确认软键盘与全键盘的回车均进入 `FcitxInputMethodService.handleReturnKey()`；无拼音预编辑时，会按输入框声明执行“前往、搜索、发送、下一项或完成”。
- `.62` 当时的浏览器输入框声明 `IME_ACTION_GO`，语义确实是“前往/确认”，不是普通换行。
- 旧实现调用 `InputConnection.performEditorAction()` 后忽略布尔返回值；部分 Windows 远程输入链路或目标编辑器返回 `false` 时，KBoard 不再补发任何按键，因此表现为回车无效。
- 用户交叉验证发现连接 Mac 时回车有效、连接 Windows 时无效；设备上的连接客户端为 `com.newlinksz.kemi.remote` 1.4.92（RustDesk/Flutter 系）。RustDesk 的 Android 软键盘文本、实体键和特殊键使用不同通路，代理层可能接受 Android 编辑器动作却没有向 Windows 主机发出 `VK_ENTER`。

### 修改
- 新增统一的编辑器动作执行入口；目标编辑器接受动作时保持原行为，拒绝动作时自动补发物理 `KEYCODE_ENTER`。
- 对 `com.newlinksz.kemi.remote` 做窄范围兼容：回车直接发送一次完整的 `KEYCODE_ENTER` 按下/释放，不再依赖可能“假成功”的编辑器动作；其他 Android 应用继续遵循标准 `IME_ACTION`。
- 多行文本、`IME_ACTION_NONE`、`IME_ACTION_UNSPECIFIED` 和明确禁止回车动作的输入框仍走实体 Enter，不改变换行行为。
- 有中文预编辑时仍由 Fcitx 先确认当前候选，避免未上屏拼音被直接当作表单确认提交。

### 验证
- 只执行完整 Release 构建，`./scripts/assemble-release-local.sh` 返回 `BUILD SUCCESSFUL`；未构建或安装 Debug APK。
- 正式 APK 为 `versionName=ffbcefe4`、`versionCode=102`、仅 `arm64-v8a`；v1/v2 签名验证通过，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- Release 覆盖安装到 Android 12 的 `.62` 返回 `Success`，默认输入法仍为 `org.fcitx.fcitx5.android/.input.FcitxInputMethodService`。
- 由于问题由 Windows 端远程输入链路触发，最终“确认/发送”效果等待用户分别在 Windows 与苹果端实际操作复核。
- Windows 专用转发修复再次完成完整 Release 构建；最终 APK 为 `versionName=ad8046a4`，平台证书 v1/v2 签名有效，覆盖安装 `.62` 返回 `Success`，并重新选定正式 KBoard；Windows 与 Mac 远端交互仍等待用户实测确认。
- 最终 APK 已归档为 `bin/KEMI-ad8046a4-arm64-v8a-release.apk`，SHA-256 为 `c1e9a5d3682daa156e27ae09b7b3bc44e4e93ab5a81e5a5fc63fe25ce3f446ea`；同一 Release 覆盖安装到 `.63` 返回 `Success`，设备报告 `versionName=ad8046a4`，默认输入法仍为主 `FcitxInputMethodService`，未执行远端交互测试。

### 待办
- 如果目标应用错误地在执行动作后返回 `false`，兜底 Enter 可能造成一次额外提交；Android 的返回值契约要求成功处理后返回 `true`，因此该风险主要取决于目标应用实现。
- 远程客户端兼容按包名生效；若以后更换应用 ID，需要同步更新匹配条件。当前不同时发送编辑器动作和实体键，避免 Mac 端出现重复提交。
- 若中文预编辑存在时希望一次按键同时“上屏候选并提交表单”，需要单独定义交互规则；当前保持输入法通用的两阶段确认行为。

---

## V1.21 - 2026-08-17

### 主题
让平台签名安装包首次运行即自动启用双屏中继，并把全键盘按下/释放双音效收口为单音效。

### 过程
- 对比 `.62` 与 `.63`：两台设备都有主 KBoard、中继服务和厂商 `com.newlink.device.ime`，但 `.62` 的 enabled IME 列表包含中继，`.63` 只包含主 KBoard，因此 `.63` 点击切屏按钮在 token 重建前被拦截。
- 中继不是独立 APK，也不读取文本；它是同一 KBoard APK 内部的一次性 `InputMethodService`，只负责在 Android 12 厂商显示策略改变后获取新 IME token，再立即返回主 KBoard。
- 直接写 `Settings.Secure.ENABLED_INPUT_METHODS` 虽返回成功，但 Android 12 的 InputMethodManager 会清理未经服务校验的修改；最终改为由应用调用系统 `ime enable` 接口，让 InputMethodManagerService 正式校验和持久化。
- 全键盘的机械键样式在按下播放主音效、释放播放 38% 音量的第二音效；退出键和桌面语音键也存在释放音效路径，形成一次点击两声。

### 修改
- Manifest 申请平台签名权限 `WRITE_SECURE_SETTINGS`；KBoard 首次启动或首次唤起时，仅启用同包固定组件 `DisplaySwitchInputMethodService`，保留所有已有输入法，不修改默认输入法。
- 自动启用命令路径和组件参数均为应用常量，不接收外部输入；非平台签名设备拿不到权限时只记录告警并保持原有安全提示，不尝试越权。
- 全键盘字符键、功能键、退出键和桌面语音键只在按下播放一次音效；释放仍保留键帽回弹和可配置触觉反馈，但不再播放第二声音。

### 验证
- `:app:compileReleaseKotlin` 与完整 `./scripts/assemble-release-local.sh` 均通过；未构建或安装 Debug APK。
- 最终 APK 为 `versionName=f6b7271c`、`versionCode=102`、仅 `arm64-v8a`，平台证书 v1/v2 签名有效。
- `.63` 安装前 enabled IME 列表缺少中继；覆盖安装后仅启动一次 KBoard 主界面，未执行 `adb ime enable`，列表即自动增加 `DisplaySwitchInputMethodService`，默认输入法仍为主 `FcitxInputMethodService`。
- `dumpsys package` 确认 `.63` 的平台签名包获得 `WRITE_SECURE_SETTINGS: granted=true`；最终 Release 已安装为 `f6b7271c`。
- 正式归档为 `bin/KEMI-f6b7271c-arm64-v8a-release.apk`，SHA-256 为 `36ba35b0fec95b3e5479c1fbcb19f2438f8a34cdbcd20552687d4eace8cbb92d`。

### 待办
- APK 安装完成但进程尚未启动时，Android 不会执行应用代码；自动启用发生在用户首次打开 KBoard 或系统首次唤起主输入法时，不需要额外设置授权或部署命令。
- `WRITE_SECURE_SETTINGS` 是强权限，本实现严格限定为启用同包固定中继；若更换为非平台签名证书，Android 会拒绝授予，跨屏需使用系统设置手工启用。
- 声音通路已从源码收口为每次触摸一次播放；不同 ROM 的系统音效采样听感仍需真机人工确认。

---

## V1.22 - 2026-08-17

### 主题
完成 `.62` Android 12 双屏输入法连续切换复测，明确现有显示策略的适用边界，本轮不修改跨屏源码。

### 过程
- 在 `.62` 分别让输入客户位于 D0 和 D2，同时检查 KBoard 请求日志、`dumpsys input_method` 的客户屏幕与 IME token 屏幕，并对照两屏截图。
- 按钮事件没有丢失：日志能稳定记录 `current=2 target=0 mode=fallback` 和 `current=0 target=2 mode=local`，每次请求后 IME window 都发生重建。
- 当输入框属于 D2 时，系统可以在 fallback/local 策略之间切换，从而把键盘显示在 D0 或 D2；当输入框属于 D0 时，Android 12 会将键盘重新绑定到 D0，不会把 D0 客户的输入连接搬到 D2。

### 修改
- 本轮按用户决定不调整代码，仅固化实机验证结论和发布边界。
- 现有中继自动启用、全键盘单音效、远程回车和中文输入行为保持不变。

### 验证
- D2 输入框的单次 D2→D0 和 D0→D2 都成功，`mCurClient.displayId` 始终为 2，`mCurTokenDisplayId` 按预期在 2 和 0 之间变化。
- 对同一 D2 输入框连续切换 12 次，12/12 成功，未发现中继竞态或按钮事件丢失。
- D0 输入框发出 D0→D2 请求后，请求日志和窗口重建存在，但最终客户与 IME token 仍为 D0，复现了用户所述的“有时不能跨屏”。
- 完整 `./scripts/assemble-release-local.sh` 构建通过，未构建或安装 Debug APK；正式包为 `versionName=473941fc`、`versionCode=102`、仅 `arm64-v8a`，v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 发布产物已归档为 `bin/KEMI-473941fc-arm64-v8a-release.apk`，APK SHA-256 为 `b98c834c5a71aa9d36be480e6f9cdc8481bed4d242fe0e3e498c73cf3234576c`。

### 待办
- 当前跨屏能力的可靠范围是“D2 输入客户的键盘在 D0/D2 显示”；D0 输入客户不能仅靠 Android 显示策略迁移到 D2。
- 若后续要求任意屏幕上的输入框都能让键盘 D0/D2 双向迁移，需要设计目标屏代理输入连接和文本/按键转发，不能再将它作为现有中继的小修补。

---

## V1.23 - 2026-08-21

### 主题
仅为全局键盘加入沉浸式轻量 3D 水族背景、触摸涟漪与单次真实水滴反馈，并在 Android 12 的 `.63` 真机完成 Release 性能验证。

### 过程
- 通过 ADB 读取 `.63` 的真实硬件环境：Android 12/API 31、arm64-v8a、Mali-G52、OpenGL ES 3.2，D0/D2 均为 1920×1280@60Hz。
- 第一版 Release 在桌面模拟器构建正常，但 `.63` 的 Mali GLSL 编译器把变量名 `patch` 识别为保留字，渲染线程停止；依据 `KBoardAquarium` 错误日志改名并重新构建，真机恢复水面与鱼群。
- 按真机观感迭代鱼体、层级、键帽和声音：鱼群位于按键下方，键帽由不透明改为约 56%–66% 不透明；鱼体缩小并加长尾鳍/背鳍；水滴声从单一合成音改为 4 个轻微不同的程序化变体。
- 全局键盘区域改为深水色全宽铺底，移除左右白框和底部白色留边；普通中文键盘、数字键盘、候选生成、语音和双屏逻辑不接入渲染引擎。

### 修改
- 新增 `DesktopAquariumView`：独立 EGL/GLES 3 渲染线程、1440 内部最大宽度、水面焦散、最多 4 组涟漪、10 条鱼群模拟、投喂聚集和 FPS 自适应降级。
- 10 条鱼分别使用不同体型、主色、花纹色、强调色、纹理算法、深度和摆尾相位；鱼体网格采用更细长的身体与延伸鳍面，双频尾摆和鳍面扰动形成更柔和的游动。
- 全局键帽使用半透明深色 3D 面层、描边、高光、3dp 按压行程和以触点为中心的 `RippleDrawable`；水面底层同步产生亮环、回弹圈和内侧暗纹。
- `InputFeedbacks` 预生成 4 条 44.1kHz 静态 PCM 水滴音轨，组合入水瞬态、气泡升频共振、水体低频与双次回声；轮转播放避免连续按键机械重复，且不回退到系统点击音、不在释放时播放第二声。
- 最终源码提交为 `be056495`，Release APK 位于 `fcitx5-android/build/kboard.apk`。

### 验证
- 仅执行 `./scripts/assemble-release-local.sh`，`BUILD SUCCESSFUL`；未安装 Debug APK。
- APK 为 `versionName=be056495`、`versionCode=102`、仅 `arm64-v8a`；v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`；APK SHA-256 为 `36d8435495a76f204161b9e3e5e1fdd350eeed2e649f1397503416e63c863494`。
- `adb install -r` 到 `192.168.3.63:5555` 返回 `Success`，设备报告 `versionName=be056495`。
- `.63` D0 便签真机截图确认：全局键盘全宽深色铺底、无白框、大小彩色鱼群位于按键下方、半透明键帽可看到鱼体且字符保持清晰。
- 10 条鱼满负载连续日志为 58.5–59.5 FPS，内部 surface 为 1440×471；未出现 `FATAL EXCEPTION`、EGL/GL shader、`AudioTrack` 或水声初始化错误。

### 待办
- 水滴音色的“真实/解压”属于听觉主观验收，ADB 无法远程听音，仍需用户在 `.63` 扬声器上确认音量和尾音；当前声音仍遵循系统输入音效开关和音量设置。
- 本轮交互与截图验证在 `.63` D0 完成；D2 使用相同 1920×1280@60Hz 硬件路径，但仍应在最终发布前补一次真实触控和声音复测。
- GLES 3 路径已针对 Mali-G52 验证；其他 GPU 若着色器失败会记录明确日志且不阻塞输入，需按目标设备驱动另做兼容，而不能让渲染工作回到 IME 主线程。

---

## V1.24 - 2026-08-21

### 主题
细化全局键盘金鱼的灵动游姿和底部活动范围，将涟漪改为方向性非圆水面，并恢复清晰可见的全局语音入口。

### 过程
- 真机截图确认旧版鱼群虽位于按键下层，但部分时段集中在中上部；全局语音键沿用普通主题色，在深水背景上对比不足，视觉上近似“被拿掉”。
- 保持原输入和候选链不变，调整全局模式的 GLES 鱼体网格、运动范围、水面 Shader、语音过程显示和桌面语音键配色。
- 初版长鳍使用单三角形且叠加双频尾摆，真机静态轮廓像纸片、尾根会与身体视觉割裂，游动时长期高频扇动；依据 `.63` 截图和用户现场观感继续改成分区圆弧网格及连续行波。
- 在 Android 12 的 `.63` D0 打开便签并弹出全局键盘，完成多轮静态截图、12 秒录屏、包权限检查和过滤日志验证。

### 修改
- 鱼体网格按鱼身、双瓣圆弧尾鳍和一对圆弧胸鳍分区，加入眼睛、头部高光、半透明鳍膜筋纹与动态高光；尾根保留与鱼身的重叠和跟随权重，尾尖才获得最大振幅，修复尾巴与身体割裂。
- 游动从加速度平移改为“受限角速度转头—尾摆产生前向推力—鱼身沿曲线跟随”，速度方向始终与鱼头方向一致；鱼身后半段到尾尖只保留一条行波，空闲约 0.6–0.9Hz、巡游约 1Hz，触摸后短时约 1.4Hz并逐渐回落；胸鳍使用更慢的独立频率，转弯加入轻微 Z 轴侧倾。
- 将鱼群垂直边界扩展到归一化坐标 `-0.97..0.93`，并固定约三分之一鱼的空闲目标位于下半区，使其能持续游入 `Ctrl / Option / 空格 / 方向键` 等最下排按键下方；点击任意键仍可把全部鱼吸引到真实触点。
- 水面 Shader 在点击位置生成阻尼高度波列，并沿按触点确定的水流轴作 `0.84/1.16` 非等比变换，再叠加 2/3/5/9 阶方向扰动、漂移、折射和法线高光，轮廓不再是规则圆圈。
- 水族渲染固定为 30Hz，性能降级阈值调整为 21/26/28.5 FPS；IME 主线程、按键命中和声音播放不等待渲染线程。
- 全局语音键恢复为深蓝键面、青色按压高光和白色麦克风；恢复 160ms 按住阈值和可实际收到 Move 的移动取消，识别期间持续展示 partial，松开进入“校准中”，final 文本显示 600ms 后只提交一次。桌面空闲栏会为 partial/final 临时显示，提交后再隐藏。
- 真实水滴声重新建模为五段：入水噪声瞬态、变频气泡共振、水体低频、表面余波和微小次滴；4 个轻微音高变体轮转，时长 0.42 秒、默认音量 56%，仍只在按下播放一次。

### 验证
- 仅执行完整 `./scripts/assemble-release-local.sh`，返回 `BUILD SUCCESSFUL`；未构建或安装 Debug APK。
- 第一轮完整 Release 在 Kotlin 编译期发现 ASR lazy 回调自引用，去掉不必要的客户端状态读取后重新构建成功；未把失败产物安装到设备，未构建或安装 Debug。
- 正式源码提交和设备版本均为 `f4da0687`，`versionCode=102`、仅 `arm64-v8a`；APK v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`，APK SHA-256 为 `2b9bca4d4ba1f258e665f1ef825fdc3b5b7d370c0a17a54baa32417ff3e96c7c`。
- `adb install -r` 到 `192.168.3.63:5555` 返回 `Success`；截图确认鱼群可见于最下排半透明按键下方，底部白色麦克风可见。
- `KBoardAquarium` 连续日志为 29.3–29.9 FPS、10 条鱼、1440×471 surface；未出现 `FATAL EXCEPTION`、EGL/GL shader 或渲染线程错误。
- `dumpsys package` 确认设备上的 `RECORD_AUDIO`、`INTERNET`、`ACCESS_NETWORK_STATE` 均有效。远程长按会采集现场声音并上传讯飞，安全审批因缺少对此第三方传输的明确授权而拒绝执行；未绕过限制，语音真机会话仍待授权后验证。

### 待办
- 水滴声音的“解压感”和长鳍摆动的主观观感仍需用户在 `.63` 现场触控、听音确认；ADB 日志只能验证播放和渲染路径稳定。
- 当前只在 D0 做本轮视觉验证；D2 共用相同输入法代码和 GPU，但最终交付前仍建议做一次 D2 真实触摸复核。
- 方向性水波使用 `mediump` 浮点以适配 Mali-G52；不同 GPU 的精度和驱动编译器可能产生轻微形态差异，若出现 Shader 错误应保留输入链路并针对目标 GPU 调整，不得回退为 Android 圆圈动画。

---

## V1.25 - 2026-08-21

### 主题
消除全局键盘金鱼“模型平移、鱼尾另行动画”的漂移感，改为由可见尾鳍划水直接驱动前进和转向。

### 过程
- 复查鱼群更新和 GLES 顶点着色器后确认旧实现存在两套互不一致的时间轴：CPU 把速度插值到目标速度并每帧直接平移鱼体，GPU 再按 `uTime` 独立计算尾摆；4% 的速度脉冲不足以让位移与尾鳍动作形成可感知因果，因此鱼看起来一直在飘。
- 将鱼群状态从二维速度向量改为朝向、前向速度和独立划水相位，并用同一相位同时计算物理推进与 GPU 鱼身行波。
- 按用户要求只生成并覆盖安装 Release 到 `.63`，未启动输入法、未远程点击、未代替用户做主观体验测试。

### 修改
- 每条鱼维护 `heading / forwardSpeed / swimPhase`；空闲与投喂分别使用低频和加速后的划水节奏，每个半拍在尾鳍经过中线时按 `cos²(phase)` 产生一次推进力。
- 前进速度由划水推力累积并受水阻衰减，划水到两端时只保留 8% 的短时水中惯性；删除“插值到目标速度后恒速搬动模型”的原路径。
- 转向角速度同样乘以本次划水的抓水力度，尾鳍或胸鳍没有有效划水时不能快速改变方向；转弯侧倾来自实际施加的转向量。
- 新增 `uSwimPhase` uniform。GPU 鱼身、尾鳍和胸鳍全部读取 CPU 的同一划水相位，删除 Shader 内按 `uTime` 独立生成尾摆频率的逻辑，形成“尾巴摆动—产生推力—鱼体前进”的单一因果链。
- 修改范围仅为全局键盘的 `DesktopAquariumView`；普通中文键盘、候选、语音、声音、输入分发和双屏切换均未改动。

### 验证
- 两次完整执行 `./scripts/assemble-release-local.sh` 均返回 `BUILD SUCCESSFUL`，未构建或安装 Debug APK。
- 最终源码提交和 APK `versionName` 均为 `ccbb58d7`，`versionCode=102`、仅 `arm64-v8a`；APK v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- Release APK 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `4f8de83ed70eef4a725bde8fcd067facb90e73cb51f09cdc055acfe908741ac8`。
- `adb install -r` 覆盖安装到 `192.168.3.63:5555` 返回 `Success`；遵照用户“自己测试、只推送”的要求，没有启动应用或进行交互验收。

### 待办
- Kotlin/Release 构建不能代替目标 Mali-G52 驱动的运行时 Shader 编译；本轮刻意未启动应用，首次运行是否有 GLES 编译错误以及实际游动观感由用户现场验证。
- 当前推进模型保留 8% 的划水间水体惯性以避免 30Hz 下逐帧停顿；若现场仍感觉像滑动，应优先继续降低惯性比例和增强半拍推进脉冲，而不能重新引入独立目标速度平移。
- 碰到渲染边界时保留低概率的安全反射和减速，正常巡游主要依靠边界内的漫游目标转向；若长时间观察能看到边缘反射突兀，再增加提前避障力场。

---

## V1.26 - 2026-08-21

### 主题
修正 V1.25 划水模型的双重衰减问题，在保持动作与位移同源的同时恢复明显、连续且具有纵深的金鱼游动。

### 过程
- 用户现场确认 V1.25 金鱼近似僵住。复算发现划水 `cos²` 既限制推力，又再次限制每帧位移，形成双重门控；配合过低的初始速度、空闲推力和速度上限，空闲可见位移仅约 `0.057 NDC/s`。
- 对照上一版巡游速度后保留共享划水相位，但把水动力链改为“尾摆产生加速度—水体积分成连续速度—位置只积分一次”，不再按相位二次削减位移。
- 按用户既定要求只构建并覆盖安装 Release 到 `.63`，没有代替用户启动或测试主观观感。

### 修改
- 空闲初始速度提高到 `0.14–0.205 NDC/s`，稳态可见速度约 `0.217 NDC/s`，约为 V1.25 的 3.8 倍；空闲上限为 `0.30`，投喂上限为 `0.74`。
- 空闲划水改为约 `0.74–0.94Hz`，投喂约 `1.3–1.6Hz`；尾摆强度只用于产生加速度，水阻在两次划水之间形成自然惯性，位置按实际前向速度连续积分一次。
- 转向仍受划水抓水力度控制，但胸鳍基础控制量由 14% 提高到 28%，避免低速或尾摆端点无法转身；朝向每帧归一化，防止长时间运行的浮点角度累计。
- 顶点 Shader 增加鱼头与尾部反相横摆、后半身 Z 轴纵深波，并让尾鳍纵深振幅随实际速度和投喂活动变化；位移、身体 S 形行波、尾鳍和胸鳍继续共用同一个 `uSwimPhase`。

### 验证
- 数值复算：V1.25 空闲可见速度约 `0.057 NDC/s`，本版约 `0.217 NDC/s`，倍率约 `3.77x`。
- 完整 `./scripts/assemble-release-local.sh` 返回 `BUILD SUCCESSFUL`，未构建或安装 Debug APK。
- 源码提交和 APK `versionName` 均为 `c8c710b0`，`versionCode=102`、仅 `arm64-v8a`；v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- APK SHA-256 为 `199e09aba6030be02f639727b661a7217f59377822fef57361ab5a21528b52e2`；覆盖安装到 `192.168.3.63:5555` 返回 `Success`。

### 待办
- 本轮按要求没有启动输入法；Mali-G52 Shader 运行时状态、速度体感和 3D 游姿仍由用户现场确认。
- 如果现场速度合适但动作仍显机械，下一轮只调整鱼身行波的相位延迟和各网格区域权重，不再同时改变推进速度，避免视觉与运动参数互相掩盖。

---

## V1.27 - 2026-08-21

### 主题
将全局键盘金鱼从独立移动模型重构为池塘式个体代理：个人路线、局部群游、跟随玩耍与尾鳍/左右胸鳍共同驱动的水动力统一运行。

### 过程
- 根据现场反馈停止继续调整单一速度参数，重新划分“行为意图—肌肉执行器—水动力—位置积分—GPU 形变”五层，禁止路线、同伴和边界直接修改鱼的位置或朝向。
- 对照真实鱼类身体/尾鳍推进、胸鳍低速机动和制动机制，以及局部感知的群游模型，重写 `DesktopAquariumView` 的个体状态和每帧更新流程。
- 首次 Release 编译通过后继续核对动作与受力相位，发现 CPU 尾鳍推力采样点与 Shader 尾尖行波有延迟差、胸鳍画面还叠加随机相位；在最终版本中把推力采样移到尾鳍段，并删除胸鳍随机相位。

### 修改
- 每条鱼拥有独立的不规则闭合路线、路线中心与半径、方向、个性巡游速度、活动水层和行为计时；路线进度只按实际游过的距离推进，不能按时间拖动模型。
- 10 条鱼组成两个松散鱼群，在 `ROUTE / FOLLOW / PLAY` 之间以 6.5–13 秒的个体周期切换：跟随时保持领游鱼身后的尾流间距，玩耍时同群伙伴相互绕游；所有状态仍保留自己的路线以便自然回归。
- 局部感知按前向视野计算：所有鱼参与近距离分离和前方制动，同群可见邻居参与方向协调与聚合；行为结果只生成期望方向和速度。
- 新增尾鳍力度、尾摆相位、左/右胸鳍力度、胸鳍相位、制动力、前向速度和角速度。尾鳍与双胸鳍产生前向推力，胸鳍展开产生制动，左右胸鳍差动与尾鳍抓水产生转矩；速度和朝向分别由加速度、角加速度积分得到。
- 前进速度限制为非负，不允许倒游；边缘使用前视预测、胸鳍制动和水动力转向，不再反射速度或瞬间翻转朝向。仅保留越界后的安全位置夹取和减速，不改变方向。
- GLES 新增 `uFinPhase / uTailDrive / uLeftFinDrive / uRightFinDrive / uBrakeDrive`；CPU 与 GPU 使用完全相同的尾鳍采样相位和胸鳍相位。鱼身呈连续后向行波，鱼头反相摆动，尾鳍有纵深扫水，左右胸鳍分别按实际推进、差动转向和制动力度展开。
- 修改范围仍仅限全局键盘水族层；按键、中文输入、候选、语音、声音和跨屏逻辑未改变。

### 验证
- 两次完整 `./scripts/assemble-release-local.sh` 均返回 `BUILD SUCCESSFUL`，未构建或安装 Debug APK。
- 源码提交和 APK `versionName` 均为 `027a4630`，`versionCode=102`、仅 `arm64-v8a`；v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 离线 30Hz 积分校验：稳定巡游均速约 `0.201 NDC/s`、尾摆周期范围约 `0.181–0.223`；投喂均速约 `0.405 NDC/s`；90° 水动力转向约 3.2 秒。位置更新只有一次非负前向速度积分，朝向更新只有一次角速度积分。
- APK 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `ec4ee8a671bddb1431f3063aa6912f441c45cd982d23c84e647e383a84fa966f`；覆盖安装到 `192.168.3.63:5555` 返回 `Success`。
- 按用户既定要求只推送，没有启动输入法或代替用户做主观交互测试。

### 待办
- 行为周期最长 13 秒，现场应连续观察至少 45 秒，确认能看到个人巡游、同群跟随、双鱼绕游和重新归队，而不能只看启动瞬间。
- 越界安全夹取只减速、不反转；正常情况下前视避边应提前接管。若现场出现鱼贴边停住，应增强预测距离或转向力矩，不能恢复反射或直接调头。
- 两群划分、群体权重和玩耍半径属于观感参数；确认基础物理正确后再单独调整社交密度，不能同时改变推进系数。
- 最终 Release 未在目标 Mali-G52 上启动，Shader 运行时编译与实际 3D 动作仍待用户现场确认。

---

## V1.28 - 2026-08-21

### 主题
根据 `.63` 真机截图和投喂数据重做金鱼急转、摆鳍、冲刺与到点减速的耦合，并把点击水面收敛为低振幅微涟漪。

### 过程
- 在 Android 12/API 31、Mali-G52 的 `.63` D0 主屏打开便签与全局键盘，先截取待机画面，再用普通字母键触发投喂，连续抓取触摸、1 秒和约 2.6 秒画面；没有触发麦克风或上传现场音频。
- 首个候选版虽然恢复了明显尾摆和大位移，但高速鱼会越过投喂点。加入设备端投喂指标后确认：旧候选在 0.8 秒时平均距离由 `0.701` 增到 `0.709`，背对触点的鱼先向错误方向加速，最远距离继续增大。
- 根因是尾鳍推力在急转制动时仍完整沿旧朝向输出，而左右胸鳍只产生制动力和偏航转矩，视觉上形成“边转边滑远”。最终版让展开的胸鳍把大部分尾鳍推力重定向为转身，鱼对准目标后才释放大位移。

### 修改
- 点击后 10 条鱼立即进入 4.6 秒投喂状态，目标速度提高到 `0.80–0.92 NDC/s`，肌肉响应由巡游的 `4.5/s` 提高到 `9.2/s`；投喂期间暂时降低群游分离干扰，最近触点成为全鱼共同目标。
- 背对触点时双胸鳍快速张开制动，差动胸鳍和尾鳍抓水产生最高 `±4.2 rad/s` 的偏航角速度；胸鳍制动会按比例重定向尾鳍前向推力，禁止鱼在转身阶段沿旧朝向滑远。朝向仍只由角加速度积分，不直接设置模型角度。
- 尾摆频率改为随实际尾鳍力度约 `0.70–2.32Hz`，尾尖横向/纵深摆幅和后半身行波同步增强；前向位移仍只来自尾鳍/胸鳍推力减去水阻后的速度积分，快速摆尾才允许产生大位移。
- 左右胸鳍增加固定根部和沿鳍展增长的铰接权重，根部与鱼身连接、外缘做更大的 Y/Z 立体扇动；两侧分别读取实际左/右胸鳍力度，不再整体平移整片鳍面。
- 接近触点时按当前速度计算提前减速半径，目标速度随剩余距离下降，尾鳍投喂增益同时衰减，双胸鳍展开制动，避免穿过触点后反复大圈回转。
- 水面 Shader 删除高振幅、多重亮环和大范围折射，改为缓慢漂移、轻微不规则、约 2.35 秒衰减的低振幅波前；折射和波峰高光幅度同步降低，只保留轻微涟漪飘动。
- 增加低频投喂诊断日志，仅记录鱼群平均/最远距离、平均速度和鳍驱动，不记录输入内容。修改范围仍只在全局键盘水族层，普通键盘、中文输入、候选、语音、声音和跨屏逻辑未改动。

### 验证
- 多次完整执行 `./scripts/assemble-release-local.sh` 均返回 `BUILD SUCCESSFUL`；全过程只构建和安装 Release，没有生成或安装 Debug APK。
- 最终源码提交和 APK `versionName` 均为 `44fdc79e`，`versionCode=102`、`primaryCpuAbi=arm64-v8a`；APK v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 最终 Release APK 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `b8b433d9466867207498ed7c815921208f079dedae0faea8d83477961e0c10b8`；`adb install -r` 覆盖安装到 `192.168.3.63:5555` 返回 `Success`。
- 修正后同一次真机投喂中，平均距离从 `0.615` 降为 0.8 秒 `0.536`、1.57 秒 `0.356`、2.34 秒 `0.189`、3.11 秒 `0.115`；3.11 秒时最远一条鱼距离为 `0.181`，10 条鱼均已进入触点附近。
- 截图对比显示待机时鱼群分散覆盖键盘，约 1 秒正在从各方向游向触点，约 2.6–3.1 秒全部聚到 `U/J` 一带的测试触点；渲染保持 10 条鱼，首次 5 秒窗口 `29.2 FPS`，随后稳定 `29.9 FPS`，没有 EGL/GL、`FATAL EXCEPTION` 或鱼数降级。

### 待办
- 胸鳍与尾鳍的实际观感仍受屏幕观看距离、按键透明度和个体尺寸影响；若继续调整，应只改网格铰接或振幅，不能绕开当前推力/转矩链直接平移模型。
- 4.6 秒内连续点击不同远端键位会立即切换最新目标，鱼群会再次急转；这是“最近触点优先”的既定行为，但极端连续点击下的主观节奏仍需用户现场判断。
- 微涟漪刻意降低亮度和折射，静态截图可能不明显；若真机认为过弱，只提高波峰高光，不能恢复大范围镜头式扭曲或规则高亮圆环。

---

## V1.29 - 2026-08-21

### 主题
把全局键盘水池扩展到底部操作区，恢复连续手指跟随与离手散开，并将金鱼推进和尾鳍外形改为可见摆尾驱动、始终头朝前的柔性尾膜模型。

### 过程
- 用户连续指出鱼群仍像模型直接加速、会倒着游，鱼身与尾巴比例不自然，尾鳍整体拍动像蜻蜓翅膀；对照用户提供的参考画面重新检查 CPU 运动积分、GLSL 朝向矩阵、尾鳍网格和根尖相位。
- 找到两个结构性问题：导航虽然不直接写位置，但旧水动力仍按每帧尾尖速度连续加力，因果脉冲不明显；GLSL `mat2` 按列主序解释，旧列顺序把鱼身按 `-heading` 绘制，而 CPU 按 `+heading` 前进，斜向运动时会呈现尾朝前。
- 第一轮把推进改为尾膜经过中线、完成半次摆尾后才结算一次动量；第二轮发现尾鳍被放到接近鱼身长度、尾尖相位领先尾根且 Z 轴整片翻动过大，继续按参考比例和后向行波重建。
- 同一批次还收口了全屏池塘触控：按下产生一次涟漪和水滴声，移动持续更新鱼群目标，抬手后个体散开并重新进入巡游、跟随或玩耍；底部操作条也属于鱼群可游和可响应的水域。

### 修改
- `DesktopAquariumView` 的触控命令由单次投喂扩展为 `DOWN / MOVE / UP`；手指滑动时鱼群只跟随最新触点，抬手后为每条鱼生成独立散开目标，并恢复各自路线、同群跟随和双鱼绕游。
- 世界位置只由非负 `forwardSpeed` 积分；尾鳍代表点每经过一次可见中线才产生一次前向动量脉冲，两次脉冲之间只受线性/二次水阻和胸鳍制动。触点、路线和同伴只能改变肌肉目标，不能直接改变位置。
- 修正 GLSL 列主序旋转矩阵，使本地鱼头 `+X` 与 CPU 的 `(cos(heading), sin(heading))` 前进方向完全一致；背对目标时先张开胸鳍制动、差动转身，前进速度始终钳制为非负，不允许倒游。
- 鱼身恢复为约占总长六成的纺锤体；尾鳍约占总长四成，由 6 个纵向截面和 5 个横向采样组成一张连续尾幕，只在末端保留浅缺口，不再存在两片可分别拍动的尾叶。尾根先弯、尾尖延迟约 `1.34rad`，主要做平面 S 形扫水，删除左右尾叶周期性反向 Z 轴翻片。
- 尾摆约 `1.6–5.4Hz`，CPU 推进采样与 Shader 尾膜约 72% 长度处使用同一相位；最大尾尖单侧幅度从过大的 `0.55` 收敛到 `0.40` 局部单位。速度增长只能来自更密集的可见摆尾脉冲。
- 大角度转身新增连续尾幕 C 型卷曲：卷曲量随方向误差和胸鳍制动增长，最大约 `5.2rad`；第一次完成的摆尾通过同一个事件增加偏航角速度，投喂时最高角速度为 `8.8rad/s`，对准目标后增加角阻尼快速刹停。`heading` 仍只由角速度积分，Shader 或导航都不能瞬间改朝向。
- 修正首次转身仍需等待半个尾摆周期的问题：大角度转向开始时立即锁存一次卷尾启动事件，把原前进速度降到 42%，同帧施加 `2.8 + 2.2 × tailDrive` 的一次性初始偏航脉冲；锁存期间不能逐帧重复，后续角速度仍由完整摆尾脉冲累积。
- 左右胸鳍从最高约 5.1Hz 的大幅对称拍动降为 `0.7–2.25Hz` 的慢速辅助动作，平面振幅和 Z 轴翻动降到原来的约三分之一，避免胸鳍与尾幕共同形成蜻蜓翅膀轮廓。
- 全局键盘水面覆盖到父布局底边，底部功能区触摸继续转发给水族引擎；内部渲染宽度由 1440 限制到 1080，以在 Mali-G52 上稳定保持 30Hz。
- 水滴反馈改为 BigSoundBank `Drops of water #1` 的 4 个 CC0 现场录音切片，使用 `SoundPool` 预加载，音量系数降到 30%；每次按下只播放一个样本，移动和释放均不叠加第二声。新增可重复生成脚本 `scripts/prepare-aquarium-water-touch.py`。

### 验证
- `:app:compileReleaseKotlin` 和两轮完整 `./scripts/assemble-release-local.sh` 均成功；只编译 Release，没有构建或安装 Debug APK。
- 最终源码提交和 APK `versionName` 均为 `e2e1813b`，`versionCode=102`、`primaryCpuAbi=arm64-v8a`；APK v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 最终 Release 位于 `fcitx5-android/build/kboard.apk`，APK SHA-256 为 `a7f37aaaf60949c8d5adc9367bc2d23fb78970d52964654ccb6b4a135452ef00`；覆盖安装到 `192.168.3.63:5555` 返回 `Success`。
- 在最终尾膜重建前的同硬件候选版上，1080×451 surface、10 条鱼连续性能为 29.3–29.9 FPS；底部条 2.4 秒连续滑动期间投喂日志持续更新，证明底部触控和移动事件已进入引擎。
- 资料复核支持当前耦合方向：鱼类前进速度与尾摆频率紧密相关，金鱼属于尾鳍较大的鲤科鱼；实现不把论文参数直接当作视觉常量，而以目标设备 30Hz 采样和用户参考画面为最终约束。

### 待办
- 遵照用户“自己测试、只推送”的要求，最终 `e2e1813b` 没有由 ADB 启动输入法或自动触摸；连续尾幕和 C 型转身在 Mali-G52 上的 Shader 运行结果与实际观感由用户现场确认，Release 编译成功不能替代该项验收。
- 30Hz 下 `5.4Hz` 尾摆约有 5.6 帧/周期，已避免上一候选 `7.2Hz` 的拍翼混叠；若现场仍显快，应降低冲刺频率并延长连续摆尾次数，不能再增大整片尾鳍 Z 轴翻转。
- 水滴样本为 CC0，但仍需保留来源和生成脚本；扬声器响度、触水质感与键帽透明度属于现场主观验收项。

---

## V1.30 - 2026-08-21

### 主题
修复全局水族键盘触摸首帧无涟漪、旋转后鱼体比例不恢复、语音键不参与水族交互、底栏遮住第六行及跨页面布局不一致，并统一普通/全局键盘的按住语音反馈。

### 过程
- 复查水面 Shader 后确认旧触点项含 `sin(age)`，按下瞬间 `age=0` 时振幅必为零；波前扩散后才出现很弱的高光，经过半透明键帽后首帧不可见。
- 旋转问题来自 `TextureView` 首次限制为 1080 宽后，尺寸变化只更新 GLES viewport，没有同步重新配置 `SurfaceTexture` 缓冲；返回原方向时可能继续使用旋转后的像素比例。
- 语音按钮位于 `DesktopKeyboard` 外部覆盖层，原始触摸没有经过水族观察器；旧实现同时在 ASR 状态变化时切换整条候选栏的 `VISIBLE/INVISIBLE`，会使全局画面产生瞬时跳动或缩放感。
- `.63` 真机截图确认源码中的第六行并未删除，但 V900 ROM 的 ConstraintLayout 父边约束没有按动态 padding 预留操作栏，导致 `Ctrl / Option / 中英 / 空格 / Cmd / 方向键` 整行落到 64dp 底栏后面。
- `KeyboardWindow.onStartInput()` 每次换输入框都按输入类型强制选择普通或数字键盘，且旋转重建 InputView 后不保留用户主动进入的全局模式，因此不同入口会出现不同按钮排列。

### 修改
- 水面 fragment Shader 新增约 0.16–0.30 秒的非对称触水凹陷、偏心高光和轻微不规则接触波带；第一帧即可见，随后自然交给原有低振幅非圆形波前，不恢复规则 Android 圆圈动画。
- `DesktopAquariumView.onSurfaceTextureSizeChanged()` 同步重配受 1080 宽限制的底层缓冲和渲染视口，横竖屏双向旋转均按当前宽高恢复鱼体比例。
- 全局语音按钮以不消费事件的 `OnTouchListener` 把 DOWN/MOVE/UP 镜像到水族引擎；鱼群可追随语音触点并产生一次水滴反馈，按住识别、移动取消和松开收尾仍由原 ASR 手势处理器独占。
- `BaseKeyboard` 增加默认关闭的真实底部约束占位，只有 `DesktopKeyboard` 传入 44dp；水族 Surface 仍铺到底边，第六行被硬约束到操作栏上方。应用操作栏由 64dp/56dp 收窄为 44dp/40dp。
- 第六行明确显示 `Ctrl / Alt / 中/英 / 空格 / Cmd / 方向键`；中英键由易混淆的图标改为文字，`Option` 标签统一为 `Alt`，修饰键行为未改变。
- 用户主动进入全局模式后，以进程内状态跨输入框焦点和旋转重建保持同一套六行布局，直到主动退出；未进入全局模式的普通/数字输入框仍按原 `EditorInfo.inputType` 选择布局。
- 普通与全局语音共用固定流程：按下立即显示“正在听，请继续说…”，Starting/Listening 保持该提示，partial 实时覆盖字幕，松开进入“正在校准…”，final 预览 600ms 后只提交一次。全局模式只隐藏空闲控件，不再隐藏/显示候选栏根视图，水族 Surface 尺寸保持固定。

### 验证
- 多次 `:app:compileReleaseKotlin` 和最终 `./scripts/assemble-release-local.sh` 均为 `BUILD SUCCESSFUL`；只构建 Release，未生成或安装 Debug。
- `.63` 修复前截图只显示五条键盘行；约束修复版截图完整显示第六行 `Ctrl / Option / 中/英 / English / Cmd / 方向键`，鱼仍可见于最下层。最终源码再把 `Option` 文案统一为 `Alt`，未改变布局宽度或键值。
- 最终 APK `versionName=808aa72a`、`versionCode=102`、包名 `org.fcitx.fcitx5.android`；v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- Release 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `ca5d149b2713172df7af2bfa89fb0e96e4d68501e913251be31a412e157bfd22`；覆盖安装到 `192.168.3.63:5555` 返回 `Success`。
- 安装后只读取截图，没有远程触发麦克风。普通按键命中、中文候选、回车、跨屏、权限、鉴权、网络和 ASR WebSocket 协议代码均未改动。

### 待办
- 触摸首帧涟漪、旋转往返比例、语音键鱼群追随和真实水滴听感需用户在 `.63` 现场验收；Release/Kotlin 编译不能代替 Mali-G52 运行时 Shader 观感。
- 完整语音会话会采集现场声音并发送至讯飞，本轮未远程执行。需现场分别在普通键盘和全局键盘确认提示、partial、松开校准和 final 单次发送，并留意短按取消与移动取消是否符合习惯。
- 全局模式只在用户主动进入后跨页面保持；进程被系统杀死后恢复普通键盘，这是刻意的临时模式边界。

---

## V1.31 - 2026-08-21

### 主题
修复全局键盘语音按下仍跳动、提示与 partial 不可见、final 未替换临时识别文本的问题，并让鱼群在语音键附近分散停留、全局中英键显示当前状态。

### 过程
- 普通键盘和全局键盘虽然共用 ASR 回调，但提示仍写入 `IdleUi`；当候选栏当前显示候选或标题子页时，`IdleUi` 处于未展示状态，因此全局按钮已收到事件却看不到“正在听”、partial 和校准文本。
- 按下路径无条件调用 `asrClient.cancel()`，即使客户端本来已经是 `Idle` 也会异步再次发布 `Idle`；这个回调与刚显示的按下提示竞争并把它清掉。
- 项目已有 `begin/update/commit/cancelVoiceComposing` 临时组合文本接口，但 ASR partial/final 只更新候选栏并直接 `commitText`，没有形成“实时临时文本—final 纠正替换—单次确认”的闭环。
- 鱼群投喂目标只在触点周围约 `0.05–0.06` NDC 范围变化，目标设备上的鱼身和长尾明显大于该间隔；语音键又靠近底边，边界钳制进一步让多个目标重合。

### 修改
- `KawaiiBarComponent` 的根视图改为固定 `FrameLayout`，内部保留原候选 `ViewAnimator`，仅全局模式增加同尺寸语音状态覆盖层；显示/隐藏只切换内部 `INVISIBLE/VISIBLE`，不改变根高度、键盘约束或水族 Surface 尺寸。
- 普通键盘继续使用原 `IdleUi`；全局键盘固定覆盖层显示按下提示、partial、校准和 final。`Idle` 状态不再抢先清空 final，取消、错误、短按和输入框重启分别负责自己的清理。
- 达到原 160ms 按住阈值后才调用 `beginVoiceComposing()`；partial 同时调用 `updateVoiceComposing()`，松开显示“正在校准…”，final 预览 600ms 后用 `commitVoiceComposing()` 替换临时文本并只确认一次。权限、网络检查、移动取消、讯飞鉴权和 WebSocket 协议未改。
- 鱼群使用黄金角分配、四层个体半径和屏幕边缘向内反射的目标点，在语音键附近约 `0.14–0.26` NDC 范围停留，不再完全叠到同一点。
- 全局语言键由“中/英”改为“英中”；根据当前 `InputMethodEntry.languageCode/uniqueName`，当前生效的“英”或“中”使用 `#4285F4`，另一字符保持普通功能键文字色。切换动作仍为原 `LangSwitchAction`。

### 验证
- `:app:compileReleaseKotlin` 和完整 `./scripts/assemble-release-local.sh` 均成功，Lint Vital、R8、原生 arm64 组件和 APK 签名流程通过；项目没有定义 `:app:testReleaseUnitTest` 任务。
- 最终 Release 包名 `org.fcitx.fcitx5.android`、`versionName=feca3b94`、`versionCode=102`、ABI `arm64-v8a`；v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- APK 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `f90251d76c7f4b47e95d209e84988fec29aecf86fe69af172d0a8a77a48d4915`；覆盖安装到 `192.168.3.63:5555` 返回 `Success`。设备确认默认输入法仍为主服务，近期日志未见 `FATAL EXCEPTION`。

### 待办
- 未远程按下语音键，避免在没有本轮明确音频授权的情况下采集并上传现场声音。全局状态覆盖层无尺寸变化已经由代码与 Release 构建确认，但“完全无视觉跳动”、partial 实时显示和 final 实际纠正仍需用户在 `.63` 现场完成一次普通/全局对照验收。
- `Starting` 阶段尚未打开麦克风；若用户在鉴权完成前就松手，本次按住会干净取消，不显示伪校准结果。需要有效识别时应按住至提示期间完成说话再松开。

---

## V1.32 - 2026-08-21

### 主题
消除全局键盘第一次按语音时仍出现的整体放大/位移动作，使进入全局模式后的键盘尺寸在语音前、中、后始终一致。

### 过程
- 读取用户刚完成首次语音操作后的 `.63` 日志，水族内部 Surface 在操作前后持续保持 `1080×451`、29.8–29.9 FPS，证明 GPU 缓冲本身没有变大；视觉变化来自第一次显示语音状态时内部控件切换 `VISIBLE/INVISIBLE` 后触发的外层重新布局或 Insets 动画。
- 上一版虽然固定了候选栏根高度，但仍会在按下时隐藏候选 `ViewAnimator`、显示语音 `TextView`；Android 的 visibility 变化可以请求 layout，`InputView` 的布局监听随后会重新读取一次桌面高度。

### 修改
- 全局语音状态层从创建起就保持 `VISIBLE` 和完整测量尺寸，空闲时 `alpha=0`、语音时 `alpha=1`；候选 `ViewAnimator` 始终可见在其下方。语音全过程只改变绘制属性和文字，不再改变任何相关 View 的 visibility、尺寸或约束。
- `InputView` 按 `orientation / screenWidthDp / screenHeightDp / densityDpi` 锁存一次全局键盘高度；同一屏幕配置内的候选更新、ASR 状态和普通 layout 回调只能复用同一高度，只有真实旋转或分辨率配置变化才重新计算。
- 保持用户当前看到的较大全局键盘尺寸，不改变普通键盘高度偏好、普通候选栏、按键事件或 ASR 协议。

### 验证
- `:app:compileReleaseKotlin` 与完整 `./scripts/assemble-release-local.sh` 均成功；Lint Vital、R8、arm64 原生组件及 v1/v2 签名验证通过。
- 正式 Release `versionName=f85c2c28`、`versionCode=102`，APK SHA-256 为 `46c8ad924e5818fd9cd401f0e7d9e6005021e921c41606fc226bde16e7e17e2e`；覆盖安装 `192.168.3.63:5555` 返回 `Success`。

### 待办
- 安装后未远程按语音，避免采集现场声音。用户需在 `.63` 重新打开全局键盘，比较出现时、第一次按下、识别中及松开后的键帽顶部位置；四个阶段应保持完全一致。

---

## V1.33 - 2026-08-21

### 主题
依据 `.63` 第一次点击语音的完整日志，隔离全局键盘 ASR 与目标编辑器的实时组合文本，消除客户端 `adjustPan` 造成的视觉放大。

### 过程
- 对照首次语音按下前后日志确认：水族 Surface 始终为 `1080×451`，渲染维持 28.8–29.9 FPS；没有 IME Insets、窗口 relayout、Surface 重建或键盘高度变化，因此 V1.32 已锁住输入法自身几何。
- 目标便签窗口使用 `adjustPan`。旧路径达到 160ms 阈值后立即调用 `beginVoiceComposing()`，partial 再持续改写 InputConnection；客户端第一次建立组合区时会平移自己的内容窗口，视觉上像全局键盘或整个画面突然放大。
- 将普通键盘与全局键盘的编辑器预览策略分开：普通键盘保留实时组合文本和 final 整体纠正；全局键盘只在固定覆盖层显示相同实时 ASR 过程，识别期间不写目标编辑器。

### 修改
- 进入全局模式时预热 ASR 客户端，把 Handler/网络客户端惰性初始化移出第一次语音按下帧。
- 全局模式不再调用 `beginVoiceComposing()`、`updateVoiceComposing()` 或 `cancelVoiceComposing()`；按下提示、partial、松开校准和 final 仍在永久测量的固定覆盖层展示。
- 全局模式 final 预览 600ms 后通过 `commitText()` 一次性写入纠正结果；普通模式继续使用 `begin/update/commitVoiceComposing`，未改变原来的实时编辑器反馈和纠错闭环。
- 权限检查、160ms 按住阈值、移动取消、离线预检、讯飞鉴权、WebSocket、鱼群触摸和普通键盘布局均未修改。

### 验证
- `:app:compileReleaseKotlin` 与完整 `./scripts/assemble-release-local.sh` 均成功；Lint Vital、R8 和 arm64 原生组件通过。
- 正式 Release `versionName=e2d28913`、`versionCode=102`；APK v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- APK 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `85278f459789be6ce881e42befc6035de41ca2b234a1d728cdbe9e5b8a9879bc`；覆盖安装到 `192.168.3.63:5555` 返回 `Success`。

### 待办
- 遵循不远程采集现场音频的边界，本轮没有代替用户按语音键。用户需现场确认全局模式第一次按下不再发生客户端画面位移，同时检查 partial、校准和 final 一次性发送。
- 全局模式刻意不把 partial 写进应用编辑框，以稳定 `adjustPan` 客户端；实时识别文字仍完整显示在输入法固定提示层。普通键盘仍保留编辑框内实时组合文本。

---

## V1.34 - 2026-08-21

### 主题
消除全局键盘中英切换时的一次性布局变化，按当前语言调整“中/英、英/中”顺序，并输出可供其他项目完整复刻的摸鱼水族设计文档。

### 过程
- `.63` 中英切换前后的 `KBoardAquarium` 日志持续为 `surface=1080×451`、29.9 FPS，证明 GLES 画布没有缩放；源码排查发现全局空格键和语言键在 `IMChangeEvent` 中调用普通 `TextView.setText()`，`AutoScaleTextView` 会显式 `requestLayout()`，因此首次切换仍可能使整棵 IME 与 `adjustPan` 客户端重新布局。
- 旧语言键使用 `SpannableString` 标蓝，但 `AutoScaleTextView.onDraw()` 自行调用 `Canvas.drawText()`，不会读取颜色 Span；因此需要只在全局键盘启用的稳定自绘通路，不能全局改变普通键盘和候选文字绘制。
- 对今天水族键盘从 GPU 架构、鱼体网格、水动力、群游、触摸、涟漪、真实水滴声、布局隔离到真机性能的全部成果重新按可移植实现顺序整理，并固化源码/资源哈希。

### 修改
- `AutoScaleTextView` 增加可选 `setLayoutStableText()`：保持原测量尺寸，只重新计算自身绘制变换并 `invalidate()`，不向父布局发出 `requestLayout()`；未调用该接口的所有页面继续走原实现。
- 全局空格键预留 `English` 的稳定测量宽度，中文/英文状态只切换自绘内容，不重新测量键盘。
- 全局语言键中文当前态显示“中/英”，英文当前态显示“英/中”；当前语言始终排在前面并使用 `#4285F4`，斜线和另一语言使用 `#F4F8FC`。颜色由稳定自绘逐字符实现，不再使用无效 Span。
- 新增 `kemi-rd/gm/KBoard摸鱼水族键盘复刻设计.md`，包含可执行源码清单与 SHA-256、30Hz/EGL 架构、触摸状态机、个体/群游状态、完整水动力公式、6×5 连续尾幕、C 型急转、非圆涟漪 Shader、水滴音源、参数表、移植步骤、验收矩阵和常见失败诊断。

### 验证
- `:app:compileReleaseKotlin` 与完整 `./scripts/assemble-release-local.sh` 均为 `BUILD SUCCESSFUL`；只构建 Release，没有构建或安装 Debug。
- 正式 Release `versionName=bf7a8e13`、`versionCode=102`、包名 `org.fcitx.fcitx5.android`、ABI `arm64-v8a`；v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- APK 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `bbdd0e5af70bf3d0eaf2cd9402afd86ccf3b7edd47bbdfa98c309bcc59b0cf85`；覆盖安装到 `192.168.3.63:5555` 返回 `Success`。
- 变更只由 `DesktopKeyboard` 调用稳定自绘接口；普通键盘、候选、数字键盘、其他设置页面和 ASR 协议未改变。

### 待办
- 本轮未远程触发语音，也未替用户点击中英键。用户需现场确认首次中英切换不再移动画面，并确认中文显示“中/英”、英文显示“英/中”、首字符为蓝色。
- 复刻文档能保证源码、参数和集成顺序一致，但其他 GPU 的 GLSL 驱动行为、屏幕透明合成和扬声器听感仍必须按文档验收矩阵在目标硬件确认。

---

## V1.35 - 2026-08-21

### 主题
全局键盘增加按住式组合键功能预览，并让控制键状态严格跟随手指按下和松开。

### 过程
- 参考用户提供的键帽功能示意，只提取“控制键按下后在目标键显示常用组合功能”的交互，不采纳图片中的宣传文字或其他页面指令。
- 排查旧全局修饰键后确认其使用普通 `Click`：动作发生在抬手后，并以一次性粘滞状态等待下一个键；这无法满足“按下立即显示、松开立即恢复”，也不利于一只手按住 Ctrl、另一只手连续输入多个组合键。
- 将修饰键改接 `CustomGestureView` 的真实 `DOWN/UP/CANCEL` 生命周期，并利用现有多指分发按物理持有状态组合 Ctrl、Alt、Cmd、Shift。

### 修改
- Ctrl、Alt、Cmd、Shift 按下时立即高亮；所有支持的目标键同步显示第二行中文功能提示，松开最后一个对应控制键时立即取消并恢复原键帽。
- 支持 Ctrl、Alt、Cmd、Shift 及 Ctrl+Shift、Cmd+Shift；复合映射基于基础映射覆盖，例如 Ctrl+S 为“保存”、Ctrl+Shift+S 为“另存为”。
- 覆盖常用跨应用功能：全选、复制、剪切、粘贴、撤销、重做、查找、替换、保存、打开、新建文档、打印、标签页/窗口切换、关闭、刷新、文本格式、导航、缩放以及 macOS 截图等。
- 组合键仍发送真实 modifier + key 事件，功能提示不直接执行应用命令；最终行为以当前 Windows/macOS 系统和前台应用为准。
- 第二行提示在全局键盘挂载时预先测量；状态变化只更新稳定自绘文字、alpha 和字符绘制偏移，不切换 visibility、不请求父布局，避免再次引发画布跳动。
- 修改范围限定在 `DesktopKeyboard`、桌面修饰键定义和 `TextKeyView` 的可选提示层；普通键盘、候选区、ASR、鱼群物理、水面 Shader 均未改变。

### 验证
- `:app:compileReleaseKotlin` 成功；完整 `./scripts/assemble-release-local.sh` 成功，Lint Vital、R8 和 arm64 原生组件全部通过，只构建 Release。
- 正式 Release `versionName=e1d14853`、`versionCode=102`、包名 `org.fcitx.fcitx5.android`；APK v1/v2 签名有效，平台证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- APK 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `b5b243d78902f0e1d9ca450ccef0c9a9f08480fe949e5efb6b3b158748c17f21`；覆盖安装到 `192.168.3.63:5555` 返回 `Success`，设备查询为 `versionName=e1d14853`。

### 待办
- 本轮按用户一贯要求只推送正式包，没有代替用户操作组合键。现场应重点确认：按下即显示、控制键保持按住时连续 C/V 都有效、松开即恢复、Ctrl+Shift 覆盖文案正确。
- 快捷键含义存在应用差异，例如 Ctrl+B 在编辑器通常是粗体、在其他软件可能有不同用途；KBoard 显示通用约定并发送真实键值，不伪造应用能力。

---

## V1.36 - 2026-08-21

### 主题
修复全局键盘组合提示初始化崩溃，并完成提示文字居中和白色视觉收口。

### 过程
- 63 在 `e1d14853` 显示全局键盘时崩溃；17:39:45 的 `AndroidRuntime` 明确记录 `DesktopKeyboard.getTextKeys()` 在构造阶段抛出 `NullPointerException`。
- 根因是 `init` 块调用 `configureHeldModifierKeys()` 时访问了源码顺序更靠后的 `textKeys by lazy` 委托；此时委托字段本身尚未初始化，不是 lazy 计算内容为空，也与 Android 12、GPU 或水族 Shader 无关。
- 修复版先在 63 成功进入全局键盘并保持进程存活，再在 62 对最终视觉版执行普通键盘→全局键盘、Ctrl 长按、Ctrl 松开的完整截图回归。

### 修改
- 构造阶段的修饰键绑定改为直接遍历已创建的 `allViews.filterIsInstance<TextKeyView>()`，不再访问后初始化的 lazy 属性；正常挂载后的状态更新仍复用缓存 `textKeys`。
- 组合功能提示增加 `Gravity.CENTER`，确保每段文字在所属键帽内位于主字符下方并水平居中。
- 提示色由浅蓝调整为与主键体系一致的白色 `#F4F8FC`，仍以 8.5dp 小字号保持主次层级。

### 验证
- `:app:compileReleaseKotlin` 和完整 `./scripts/assemble-release-local.sh` 均成功，只构建 Release。
- 最终 Release `versionName=e5ec4f17`、`versionCode=102`；APK SHA-256 为 `89cd6861c6b3f7256c0e83f47b9b76b88d79785be13e2584200c1a8a6d9b673e`。
- 63、62 覆盖安装均返回 `Success`；63 的修复中间版成功显示全局键盘且无新增退出记录，随后 63 的整机网络变为不可达，无法完成最终白色版截图。
- 62 最终版实测：普通键盘正常；进入全局键盘无崩溃，进程 PID 8044 持续存活；Ctrl 按住显示白色居中提示，松开后提示全部取消并恢复原键帽；日志只存在 17:40:08 旧故障版历史堆栈，没有最终版新增 FATAL。

### 待办
- 63 恢复网络后补查 `ApplicationExitInfo` 和最终版截图；当前无法从“主机不可达”判断是设备重启、网络断连或 ADB 服务异常，不能把网络故障归因为输入法。
- 继续由用户验证 Windows/macOS 前台应用对真实组合键的具体解释；提示语义是通用约定，不保证所有应用完全一致。

---

## V1.37 - 2026-08-21

### 主题
将全局键盘组合功能预览、初始化崩溃修复和白色居中提示正式归档到 `bin/`，并备份到 GitHub 根仓库 `main`。

### 过程
- 正式归档复用已经在 62、63 覆盖安装并完成真机验证的同一份 `e5ec4f17` Release APK，不因随后的纯文档提交号改变二进制版本。
- 63 恢复上线后重新连接 ADB：已安装 `versionName=e5ec4f17`、`versionCode=102`，输入法进程 PID 28709 存活，最近 300 行日志无新的 `FATAL EXCEPTION`。
- 62 的 Ctrl 按住/松开回归已确认：按住时功能文字以白色在各自键帽内居中，松开后全部取消并恢复原键帽。

### 修改
- 新增正式 APK：`bin/KEMI-e5ec4f17-arm64-v8a-release.apk`。
- 新增独立校验文件：`bin/KEMI-e5ec4f17-SHA256SUMS.txt`。
- 源码实现、崩溃根因、白色居中视觉规则和风险边界已分别固化在 V1.35、V1.36、`desktop-aquarium-engine.md` 和 `kemi-rd/gm/KBoard摸鱼水族键盘复刻设计.md`。

### 验证
- APK SHA-256：`89cd6861c6b3f7256c0e83f47b9b76b88d79785be13e2584200c1a8a6d9b673e`，与 62、63 已安装验证包完全一致。
- `aapt` 确认包名 `org.fcitx.fcitx5.android`、`versionCode=102`、`versionName=e5ec4f17`、ABI 仅 `arm64-v8a`。
- `apksigner` 确认 v1/v2 签名均有效，签名证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。

### 待办
- 应用对 Ctrl/Alt/Cmd/Shift 组合键的解释可能不同；KBoard 发送真实键值并展示通用语义，不伪造目标应用未提供的功能。
- 继续遵守只发布 Release 的约定，不将 Debug 包加入 `bin/` 或推送到设备。

---

## V1.38 - 2026-08-21

### 主题
正式版本号加一，以项目既有多 ABI 编码规则发布 KBoard 0.1.3 Release。

### 过程
- 项目的 Android `versionCode` 不是单一连续整数：计算式为 `baseVersionCode * 10 + abiId`，其中 arm64-v8a 的 `abiId=2`。
- 因此将基础版本从 10 增加到 11，arm64 正式包的安装版本由 102 正确升级为 112；同时将语义版本从 `0.1.2` 提升为 `0.1.3`。
- 版本变更先独立提交为 `d24ea522`，再从该确定源码提交执行完整签名 Release 构建，使 APK `versionName` 与源码可直接对应。

### 修改
- `fcitx5-android/build-logic/convention/src/main/kotlin/Versions.kt`：`baseVersionCode` 由 10 调整为 11，`baseVersionName` 由 `0.1.2` 调整为 `0.1.3`。
- 新增正式 APK：`bin/KEMI-0.1.3-112-d24ea522-arm64-v8a-release.apk`。
- 新增校验文件：`bin/KEMI-0.1.3-112-d24ea522-SHA256SUMS.txt`。

### 验证
- `./scripts/assemble-release-local.sh` 完整构建成功，Lint Vital、R8、arm64 原生组件和 APK 签名流程通过；没有构建 Debug APK。
- `aapt` 确认包名 `org.fcitx.fcitx5.android`、`versionCode=112`、`versionName=d24ea522`、ABI 仅 `arm64-v8a`。
- `apksigner` 确认 v1/v2 签名有效，平台证书 SHA-256 仍为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- APK SHA-256：`c47a06fb00ae67a0955445b5985ee0613236b3b5494fc99520f257c12bb21d1a`，`bin` 副本与构建输出一致。
- `adb install -r` 覆盖安装到 `192.168.3.63:5555` 返回 `Success`，未清除应用数据；设备查询确认 `versionCode=112`、`versionName=d24ea522`。

### 待办
- 按用户要求只部署正式包，不代替用户做键盘交互测试；63 上的按键、组合键提示、语音和水族动画体验由用户现场验收。
- 后续发布继续使用 `versionCode=baseVersionCode*10+abiId` 规则，不得把 arm64 的 112 直接改为 113，否则会与 x86 ABI 编号冲突。

---

## V1.39 - 2026-08-23

### 主题
全局水族键盘增加明显的个体快慢差异、随机单鱼闪光和投喂瞬时 C-start 反应。

### 过程
- 复查现有物理闭环确认：尾膜过中线冲程才能增加前向动量，左右胸鳍差动产生偏航，没有直接平移和倒游；但 FOLLOW/PLAY 的共享速度上限会压平个体差异，而投喂首次动力冲程仍受点击前的随机尾摆相位影响。
- 新方案没有给世界坐标或 `heading` 赋目标值；快慢差异同时进入尾摆节奏、冲程推力和最高速度，急转仍由可见卷尾和胸鳍展开产生。
- 闪光放在既有鱼体片元 Shader 内，避免额外 CPU 粒子、网格或渲染 Pass，且任意时刻只激活一条鱼。

### 修改
- 为 10 条鱼增加不重复、非单调的速度性格序列，分别派生 `cruiseSpeed`、`tailTempo`、`thrustScale` 和 `maxForwardSpeed`；巡游、跟随、玩耍、散开和投喂全部保留本鱼快慢性格。
- `DOWN` 时把尾膜放到距下一次过中线仅 0.050–0.086rad 的位置，立即卷尾、张开差动胸鳍；前 0.46 秒尾/胸鳍节奏额外乘 1.22，肌肉响应提高到 `11.5/s`。
- 卷尾启动偏航脉冲提高为 `(5.8+3.4×tailDrive)×tailTempo`，前 0.46 秒角速度上限 `14.5rad/s`，后续投喂为 `11.2rad/s`；旧方向速度和胸鳍制动仍限制转向时的侧滑。
- 增加随机单鱼闪光状态：每次持续 1.15–2.10 秒，间隔 1.40–4.00 秒；头部、背部和尾膜三个 seed 星芒异步闪烁，不影响鱼群物理。
- 变更仅位于 `DesktopAquariumView.kt`，普通键盘、候选栏、ASR、组合键和输入事件未修改；复刻文档和引擎文档已同步新参数。

### 验证
- `:app:compileReleaseKotlin` 成功；完整 `./scripts/assemble-release-local.sh` 成功，Lint Vital、R8、arm64 原生组件和 APK 签名流程通过，全程没有构建 Debug。
- 候选 Release 为 `versionName=2ecfa902`、`versionCode=112`、包名 `org.fcitx.fcitx5.android`、ABI 仅 `arm64-v8a`；v1/v2 签名有效，平台证书 SHA-256 仍为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- APK 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `1f4a5e489f5a24c6c87191a2006ec9f63b3c3a6401f02c21d67fba6dceac8fa0`。
- 安装验证时 63 在 ADB 命令期间掉出设备列表，随后 62/63 均 ping 100% 丢包且 ADB 无设备；当前无证据说明 APK 已安装，也无证据将整机网络中断归因于输入法。

### 待办
- 62 或 63 恢复上线后，必须覆盖安装该 Release，进入全局键盘确认 Mali-G52 Shader 链接成功、随机单鱼闪光可见、10 条鱼持续约 30Hz、投喂时 `speedRange` 非零且快慢明显。
- 真机重点观察 180° 背向鱼：应先在下一帧卷尾/张胸鳍，瞬间转身后才头朝前冲刺；若观感跳变、转过头或低于 28.5 FPS，优先调整偏航上限/闪光片元成本，不得恢复直接位移。

---

## V1.40 - 2026-08-23

### 主题
全局水族键盘增加触点水草、按体型碰撞和多阶段双鱼游戏。

### 过程
- 复查发现原 PLAY 只有单一绕行，鱼间分离统一使用 `0.155`，没有体现最大鱼和最小鱼的渲染体积差异；直接把水草写入全屏水面片元 Shader 又会让每个像素重复计算植物形状，增加 Mali-G52 负担。
- 新方案给水草单独建立小型 GLES 网格 Pass，仍保持 `水面 -> 水草 -> 鱼 -> 原生键帽` 的层级；水草和鱼群只提供导航目标/避障力，不直接修改鱼的位置或朝向。

### 修改
- 每次 `DOWN` 在触点生成一簇水草，最多循环保留 4 簇；每簇包含 7 片分段叶、0.52 秒生长、22–32 秒寿命、3.2 秒淡出、随水流异步摆动，以及同一 Draw Call 内的 3 个上浮小气泡。
- 触摸期间所有鱼仍执行原投喂 C-start；释放后约一半鱼在 9.5 秒内按黄金角、个体尺寸和三档半径围绕最新水草游动，其余鱼执行散开/原行为，避免十条鱼全部堆在草根。
- 鱼间碰撞改为 `0.025 + renderedScale×0.88` 的个体半径，两鱼安全距离为半径之和；水草根部另加 `0.055` 软避障半径，并对正前方接近的鱼增加制动。
- PLAY 每 3.8 秒在相互绕游、追尾嬉戏、并排交叉三种目标间轮换；目标间距由双方碰撞半径之和决定，实际移动仍只能由尾鳍冲程和胸鳍转矩产生。
- 变更仍只在 `DesktopAquariumView.kt`；普通键盘、按键事件、候选栏、语音、组合键和切屏没有修改。

### 验证
- `:app:compileReleaseKotlin` 成功；水面、水草、鱼体共 6 个 Shader 通过 `glslc` 的 310 ES 等价语法检查。
- 无签名环境下执行 `:app:assembleRelease` 成功，Kotlin、R8、Lint Vital、arm64 原生组件和 Release 打包流程通过；没有构建 Debug APK。
- 源码提交为 `dd5d2700`，验证包为 `org.fcitx.fcitx5.android-dd5d2700-arm64-v8a-release-unsigned.apk`，SHA-256 为 `574f0379eec1c1a08237b7989a66f589095f6c15e5de6cdf1d7ac10e578902d5`。该包未签名，不进入 `build/kboard.apk`，不能安装或作为正式产物交付。

### 待办
- 本机未配置 `SIGN_KEY_FILE/SIGN_KEY_PWD/SIGN_KEY_ALIAS`，正式签名构建尚未执行；不得探查系统钥匙串或改用其他证书。签名环境恢复后再生成平台签名 Release。
- 最终复查时 62 已恢复为 ADB `device`，63 仍不在列表；但新包未签名，不能覆盖安装。签名包生成后必须在 62 验证 Mali-G52 上水草 Shader 链接、最多 4 簇时持续约 30Hz、触点/草根位置一致、大小鱼不穿模、草根不叠鱼、两鱼游戏不抽搐，以及所有原键盘功能不受影响。

---

## V1.41 - 2026-08-23

### 主题
按 KEMI/RustDesk 联调文档补齐全局键盘的标准桌面功能键转发。

### 过程
- 逐项核对 `/Users/newlink/kemi/RustDesk/client/kemi-docs/KBOARD-REMOTE-FUNCTION-KEY-FIX.md`：KEMI 端 `RemoteFunctionKeyMapper` 与 `KeyboardProxyActivity` 已支持 F1–F12、Esc、Tab、Caps Lock、Enter、退格、方向键及 Ctrl/Alt/Shift/Command 状态，对应 KEMI 提交为 `b7d9116fd`。
- KBoard `DesktopKeyboard.onAction()` 仍会给不带 Ctrl/Alt/Meta 的功能键增加 `KeyState.Virtual`；服务端 Virtual 分支无 Unicode 且没有专用 case 时直接丢弃，因此 Esc、F1–F12、Caps Lock、Tab、Up 和 Down 无法到达 KEMI。
- 选择在全局桌面键盘源头标记原始控制键，不在全局 `FcitxInputMethodService` 中堆叠特例，以避免破坏中文预编辑和普通键盘。

### 修改
- `DesktopKeyboard` 新增 `RawDesktopControlKeySyms`：Esc、F1–F12、Backspace、Tab、Caps Lock、Return 和四个方向键。
- 命中上述集合时不再附加 `KeyState.Virtual`，事件进入现有非 Virtual 通路，由 `sym.keyCode` 生成标准 Android `KeyEvent` 并通过当前 `InputConnection` 交给 KEMI。
- Ctrl、Alt 和 Meta 仍强制原始事件；Shift 单独按住普通字符时仍保持文本输入语义，Shift+功能键则因功能键命中原始集合而完整保留 meta 状态。
- 修改范围只有 `DesktopKeyboard.kt`；普通中文/英文键盘、候选、ASR、水族动画、双屏切换和 KEMI 代理代码未改动。

### 验证
- `:app:compileReleaseKotlin` 成功。
- `:app:assembleRelease` 完整成功，R8、Lint Vital、arm64 原生组件和 Release 打包通过；没有构建 Debug APK。
- 源码提交为 `d74b7ff9`；无签名 Release 为 `org.fcitx.fcitx5.android-d74b7ff9-arm64-v8a-release-unsigned.apk`，SHA-256 为 `8a95d963b53da32f07e1f9388f2cf29faa608416f844eee30c708dd423ace3ec`。该包仅用于证明 Release 代码可完整编译，不作为正式交付或设备安装包。

### 待办
- 用户指定的 `/Users/newlink/kemi/keystore` 目录当前只有 `debug.keystore`，未配置 `SIGN_KEY_FILE/SIGN_KEY_PWD/SIGN_KEY_ALIAS`；在不猜测口令、不误用调试证书的前提下，尚不能生成正式签名 APK。获得正确别名/口令后，必须先核对证书 SHA-256 是否为当前 KBoard 正式证书 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 功能键跨系统最终语义由 Windows/macOS/Linux 和前台应用决定。签名 Release 完成后还需按对接文档在真实远程会话验证 Esc/F1/F5/F12、Tab/Shift+Tab、方向键长按、Alt+F4、Ctrl/Command 组合键，并排查双发与卡键。

---

## V1.42 - 2026-08-23

### 主题
水草改为严格跟随手指生命周期，并增加角色化投喂、抢食闪光和三种散场。

### 过程
- 旧 V1.40 每次点击保留一簇 22–32 秒的水草，松手后还有 9.5 秒围草兴趣期，与“手指放开后水草跟着消失”的新交互冲突。
- 将植物语义从“池塘持久装饰”改为“手指在水中的临时投喂提示”，渲染、跟手与碰撞必须共用同一 `touchHeld` 生命周期，避免只隐藏图像却留下不可见碰撞体。
- 差异化设计不增加新渲染 Pass，不直接改鱼坐标/朝向；所有角色和散场仍只生成导航目标，实际位移由可见尾摆冲程、胸鳍偏航与水阻积分产生。

### 修改
- 四个长寿命 `PlantPatch` 循环槽收口为唯一 `touchPlant`：DOWN 生成，MOVE 移动根部，UP/CANCEL 将 `start` 立即置为无效值并清除触摸闪光。下一帧同时跳过水草 Draw Call 和草根碰撞。
- 水草首帧以 30% 尺寸/透明度可见，0.24 秒长满；按住期间仍使用 7 叶片、5 段、3 气泡的 228 顶点小网格，每帧最多 1 次水草 Draw Call。
- 每次触摸轮换四类鱼群角色：抢食鱼占近槽，顺游/逆游鱼以不同角速度绕手指，谨慎鱼在 0.90 秒内从外圈靠近；角色轮换偏移使同一条鱼下次可扮演不同角色。
- 触摸时随机选一条幸运鱼，它靠近触点后复用现有 `uSparkle` 显示“抢到食物”星芒；不增加网格、CPU 粒子或动力。
- 松手后依次轮换放射散开、两群反向分流、螺旋扩散；每轮确保 ROUTE/FOLLOW/PLAY 混合，鱼保留松手瞬间的速度与朝向。

### 验证
- `:app:compileReleaseKotlin` 成功。
- 功能源码提交为 `0e318aeb`，单鱼闪光互斥约束提交为 `79a3f763`；完整 `:app:assembleRelease` 成功，R8、Lint Vital、arm64 原生组件和 Release 打包通过，未构建 Debug APK。
- 无签名 Release 为 `org.fcitx.fcitx5.android-79a3f763-arm64-v8a-release-unsigned.apk`，SHA-256 为 `37abd036eb5c7899b5b2b03b67b673ad62f65dece647509b53b7327fab504d61`；该包不用于安装或交付。

### 待办
- 当前仍缺少可核验正式证书的签名别名/口令，未生成签名 APK，也未安装到 62/63。不得以未签名包或未核对指纹的 `debug.keystore` 替代正式 Release。
- 真机应验证：水草跟手且松手后 1 帧内完全消失；不存在隐形草根碰撞；四类投喂角色和三种散场可辨识；幸运鱼闪光不常亮；10 条鱼保持约 30Hz，键盘输入、语音、组合键和功能键不受影响。

---

## V1.43 - 2026-08-23

### 主题
全局水族键盘增加“星期数字”趣味入场：周一到周日由鱼群依次游成 1–7，再短暂停留并慢速散开。

### 过程
- 产品语义采用周一=1、周二=2……周日=7，而 Android `Calendar.DAY_OF_WEEK` 使用周日=1；引擎创建时通过 `((day+5)%7)+1` 完成本地映射，不依赖网络、权限或持久化状态。
- 不能用瞬间改坐标把鱼摆成数字，否则会破坏既有“位移必须来自尾鳍冲程和胸鳍转矩”的物理约束；新功能只改变每条鱼的导航目标和期望速度。
- 10 条鱼按七段数字的启用笔画均匀分配槽位，再用初始位置到槽位的贪心近邻匹配减少交叉路线；采样点沿笔画法线交错增加厚度，缓解数字 1 两条竖线上大小鱼拥挤。

### 修改
- 新增 `FORM -> HOLD -> DEPART -> DONE` 入场状态机：成形最少 1.25 秒、最长 5.20 秒，最大槽位距离不超过 0.145 时可提前完成；保持 1.45 秒；随后按各鱼独立路线在 3.20 秒内慢速游散。
- FORM/HOLD 暂停普通群聚、方向协调、随机闪光和下层领地约束，避免数字被其他行为拉散；鱼体尺寸碰撞、边界预测、水阻、非负前进速度和一次位置积分仍然保留。
- 数字槽只进入现有 `desiredSpeed/turnDemand` 链路，鱼必须靠可见尾摆完成推进、靠尾鳍和左右胸鳍完成转身；没有位置或朝向跳变，也没有增加新的 GLES Draw Call。
- 任意触摸会立即结束数字入场，保留鱼群当时的速度和朝向，并进入原有投喂 C-start、水草、涟漪和按键处理；装饰动画不拦截、不延迟输入。
- 每次重新进入全局键盘并新建水族引擎时读取当时本地日期，因此跨日后重新进入会显示新的数字；普通键盘、候选、ASR、组合键和桌面功能键没有修改。

### 验证
- `:app:compileReleaseKotlin` 成功；完整 `:app:assembleRelease` 成功，Kotlin、R8、Lint Vital、arm64 原生组件和 Release 打包流程全部通过，未构建 Debug APK。
- 源码提交为 `5dc9bafa`；无签名 Release 为 `org.fcitx.fcitx5.android-5dc9bafa-arm64-v8a-release-unsigned.apk`，大小 46,118,149 字节，SHA-256 为 `1219e71a92c1678ecaa068ba444ad0ce155ac007dc1ff03bda2aa1f31ac10377`。
- 2026-08-23 为星期日，当前真机进入全局键盘时预期先形成数字 7；代码会输出 `weekdayIntro digit=7 phase=FORM/HOLD/DEPART/DONE` 供过滤日志核对。

### 待办
- 当前仍缺少可核验正式证书的签名别名/口令，未安装该无签名包，也未把它放入 `bin/`；不得用 `/Users/newlink/kemi/keystore/debug.keystore` 或其他证书替代正式签名。
- 正式签名恢复后需在 Android 12/Mali-G52 真机依次验证周一到周日的 1–7 辨识度、10 条鱼碰撞与约 30Hz 性能，并确认 FORM/HOLD/DEPART 任意阶段触摸都能立即输入。设备日期测试可能影响系统服务，应在可控测试环境执行并在结束后恢复自动日期。

---

## V1.44 - 2026-08-24

### 主题
恢复 KBoard 实际正式签名配置，生成星期数字水族正式 Release，并无损覆盖安装到 63。

### 过程
- 首次把 `5dc9bafa` 的无签名 Release 直接交给 63 时，Android 12 明确返回 `INSTALL_PARSE_FAILED_NO_CERTIFICATES`；系统权限不能替代 APK 证书校验。
- 用户确认 `/Users/newlink/kemi/keystore/debug.keystore` 虽保留历史文件名，但就是 KBoard 当前正式签名文件，并提供实际 alias/口令。发布身份必须按证书指纹判断，不能按文件名猜测。
- `keytool` 核对别名为 `androiddebugkey`，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`，与 63 原安装包及 KBoard 历史正式基线完全一致，因此可以 `install -r` 保留数据覆盖，不需要卸载或写入 `/system`。
- 63 上 KBoard 当前实际为 `/data/app` 下普通 UID `10134`，无 `SYSTEM/PRIVILEGED` 包标志；项目需要的签名级/系统能力和普通安装位置是不同概念，不能用 root 或 appops 绕过 APK 签名。

### 修改
- 使用项目既有 `assemble-release-local.sh`，通过外部环境注入 `SIGN_KEY_FILE/SIGN_KEY_PWD/SIGN_KEY_ALIAS` 完整构建正式 Release；没有修改签名脚本、Gradle 配置或应用源码，也没有构建 Debug。
- `fcitx5-android-port-plan.md` 补充 KBoard 实际 keystore 路径、alias、证书指纹、验签与安装命令；密码只指向私密权限文档，不写入可推送项目仓库。
- 私密 `/Users/newlink/kemi/priv/xtqx.md` 补充 KBoard 可直接复用的正式 Release 签名、验签、覆盖安装步骤，并明确文件名不能替代证书身份判断。

### 验证
- 完整 `./scripts/assemble-release-local.sh` 为 `BUILD SUCCESSFUL`，Kotlin、R8、Lint Vital、arm64 原生组件、Release 签名和打包全部通过。
- 正式 APK 为 `fcitx5-android/build/kboard.apk`，包名 `org.fcitx.fcitx5.android`、`versionCode=112`、`versionName=330ca8a4`、大小 46,193,440 字节，SHA-256 为 `318486abb3ca3025ef654ca988925c2503df47fcb021c038d23d63396fbcff47`。
- `apksigner` 确认 v1/v2 均有效，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- `adb -s 192.168.3.63:5555 install -r` 返回 `Success`；设备查询确认 `versionName=330ca8a4`、`versionCode=112`、更新时间 `2026-08-24 10:12:33`，未卸载、未清数据。

### 待办
- 按用户要求本轮完成推送和版本核对，没有代替用户操作键盘。2026-08-24 为星期一，现场重新进入全局键盘应先看到鱼群游成数字 1，再保持并散开。
- `keytool` 对该旧 JKS 的证书自签名算法给出 MD5withRSA 安全警告；当前 APK v1/v2 验签和 Android 12 覆盖安装均成功，但未来若轮换密钥必须专项设计签名 lineage/重装迁移，不能直接换证书破坏升级链。

---

## V1.45 - 2026-08-24

### 主题
修复 63 全局水族渲染中断，移除不协调水草，重做真实触水涟漪，并让普通键盘切入全局键盘时按当天星期编队后随机戏耍。

### 过程
- 63 上“金鱼、涟漪和音效同时消失”的直接原因不是三个功能分别失效，而是水草 program 在 Mali-G52 链接失败：片元与顶点 Shader 的 `uSeed` 精度不一致触发 `IllegalStateException`，整个 Aquarium renderer 随之停止。
- 先用 `4e29c75b` 对齐水草 Shader 精度确认根因，再按产品决定用 `a167e34d` 完整删除水草 program、网格、uniform、Draw Call、触摸状态和碰撞体，避免隐藏图像后仍留下渲染成本或隐形障碍。
- 旧涟漪只是低对比亮度扰动，连续截图中几乎不可见。`d2d5fa9f` 将水面模型改为触点凹陷、外扩前导波、后随快/慢双频波列、波峰高光、波谷阴影、法线折射和随水流轻微非圆漂移；仍复用 4 个 uniform 槽，不增加 View 或粒子系统。
- 每次普通键盘切入全局键盘都会停止旧引擎并新建 AquariumEngine，重新读取本地 `Calendar.DAY_OF_WEEK`。星期数字完成 FORM/HOLD/DEPART 后，10 条鱼以随机偏移保证同时分配到 ROUTE/FOLLOW/PLAY，并获得独立持续时间；只改变导航意图，不改坐标、速度或朝向。

### 修改
- `DesktopAquariumView.kt`：彻底移除水草渲染与碰撞链；保留金鱼、真实水滴声、最多 4 组涟漪、触摸投喂、差异化散场和随机单鱼闪光。
- `WATER_FRAGMENT_SHADER`：波前速度改为 `0.245 UV/s`，前导带宽 `0.030`，后随相位为 `72/37 rad·UV⁻¹`，寿命 1.55–2.25 秒，折射量为 `0.0065/0.0085`；接触早期单独计算中心凹陷和扩张 crown。
- 星期编队结束日志增加活动统计，例如 `activities=route:4,follow:3,play:3`，便于确认编队不是结束后静止或全部进入同一种行为。
- 普通键盘、候选栏、ASR、组合键、桌面功能键、切屏和输入事件链均未修改。

### 验证
- `:app:compileReleaseKotlin` 成功；`./scripts/assemble-release-local.sh` 完整成功，Kotlin、R8、Lint Vital、arm64 原生组件和 Release 签名均通过，没有构建 Debug。
- 正式 APK：`fcitx5-android/build/kboard.apk`，包名 `org.fcitx.fcitx5.android`、`versionName=d2d5fa9f`、`versionCode=112`、大小 46,191,156 字节、SHA-256 `ecc801e8f8dfe3343123365aa2cf95a3df36ca1c448f83aadcbaa3c825134d10`。
- `apksigner` 确认 v1/v2 有效，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`；63 覆盖安装返回 `Success`，未卸载、未清数据。
- 2026-08-24 星期一，63 从普通键盘切入全局键盘实测日志依次为 `digit=1 phase=FORM/HOLD/DEPART/DONE`，DONE 为 `route:4,follow:3,play:3`；10 条鱼在 1080×451 内部 surface 持续 28.8–29.9 FPS，无 EGL、GLSL、renderer stopped 或应用崩溃。
- 字符键受控触摸日志同时出现 `touchFeed` 和 AudioTrack 创建；连续截图可见 H/J 键下的中心暗凹、偏置亮脊和向外衰减波列，验证新版涟漪实际进入水面，而非仅有日志。

### 待办
- ADB 日志只能证明音轨创建和播放路径正常，真实水滴音量与音色仍以设备扬声器现场听感为准。
- 当前 10 条鱼对数字 1 的七段槽位形成已通过星期一真机验证；星期二到星期日的 2–7 仍应在不改系统日期的自然日期或隔离测试设备上逐日核对辨识度。

---

## V1.46 - 2026-08-24

### 主题
修复全局键盘“只显示第一个候选”的视觉问题，并保持普通键盘候选主题不变。

### 过程
- 63 当前截图显示候选事件实际已经包含“你、n、能、拿、牛、年、那”等多项，因此不是拼音引擎、JNI 事件或 RecyclerView 把候选截成一个。
- 全局键盘使用固定深色水族背景，但候选项继续读取当前普通浅色主题的深色 `candidateTextColor`；只有首个直命中项使用独立蓝色，所以其余候选虽然存在，视觉上接近不可见。
- 颜色修复必须跟随全局模式生命周期，而不能修改主题预设，否则普通键盘、展开候选页和用户自定义主题都会被一起改变。

### 根因证据与调用链
- Native `AndroidInputContext.updateCandidatesBulk()` 会把当前候选总数和首批最多 16 项送入 JNI；`FcitxEvent.CandidateListEvent.Data` 在 Kotlin 层同时保留 `total` 和完整 `candidates` 数组。
- `HorizontalCandidateComponent.onCandidateUpdate()` 原样把数组交给适配器，`HorizontalCandidateViewAdapter.getItemCount()` 返回 `candidates.size`。截图中各候选分隔线和深色字形轮廓都存在，排除了“引擎只生成一项”“JNI 只传一项”和“列表只绑定一项”。
- 首个候选之所以可见，是活动 `ClientPreedit/InputPanel` 组合态把第 0 项标记为 direct hit，`CandidateItemUi` 使用固定 `#4285F4`；其余项回退到浅色主题的深色 `theme.candidateTextColor`，与全局水族固定深色表面形成错误的低对比组合。
- 模式切换链为 `KeyboardWindow.notifyBarLayoutChanged()` → `InputView.setDesktopKeyboardMode()` → `KawaiiBarComponent.setDesktopKeyboardMode()`。因此把颜色覆盖接在这一链路上，可以精确限定全局键盘，而无需修改候选引擎、全局主题或普通键盘。

### 修改
- `CandidateItemUi` 和 `CandidateViewHolder` 增加可选候选正文/注释颜色覆盖，并把覆盖色纳入 ViewHolder 重绑定状态，保证普通键盘与全局键盘来回切换时同一批候选也会立即重绘。
- `HorizontalCandidateViewAdapter` 增加颜色覆盖接口；全局模式普通候选使用白色，候选注释使用 80% 白色，首个活动组合态直命中仍保持 `#4285F4` 蓝色。
- `KawaiiBarComponent.setDesktopKeyboardMode()` 只在进入全局键盘时启用覆盖，退出时清空覆盖并恢复当前主题；候选数量、排序、选择索引、展开分页和输入引擎均未修改。

### 逐文件改动

| 文件 | 具体职责 | 修改结果 |
|---|---|---|
| `input/bar/KawaiiBarComponent.kt` | 接收普通/全局键盘模式切换 | 进入全局模式时下发高对比覆盖，退出时同步清除；不改变候选栏尺寸、状态机和语音覆盖层。 |
| `input/candidates/CandidateItemUi.kt` | 绘制候选正文和注释 | 增加可空的正文/注释覆盖色；direct hit 优先级仍最高，并继续显式调用 `AutoScaleTextView.setTextColor()`。 |
| `input/candidates/CandidateViewHolder.kt` | 缓存候选绑定状态 | 把两种覆盖色加入差异判断；即使候选文本和索引没变，普通/全局切换也会触发重绘，避免 RecyclerView 复用旧颜色。 |
| `input/candidates/horizontal/HorizontalCandidateViewAdapter.kt` | 管理横向候选 ViewHolder | 保存覆盖色并在变化时 `notifyDataSetChanged()`；每个绑定都携带当前模式颜色，不修改候选数组和稳定 ID。 |
| `input/candidates/horizontal/HorizontalCandidateComponent.kt` | 连接候选事件与适配器 | 全局正文设为 `Color.WHITE`，注释设为 `0xCCFFFFFF`；普通模式传 `null`，回退到用户主题。 |

### 行为边界与风险控制
- 只改变横向候选的绘制颜色，不改 `CandidateWord`、候选数量、排序、学习、选择索引、长按操作、展开分页或提交文本，因而不会改变输入结果。
- direct hit 仍仅在预编辑/面板组合态存在时着蓝；选择“你”上屏后，联想项全部为白色，不会错误地把联想第一项继续标成命中蓝色。
- 展开候选页继续使用自身主题逻辑；普通键盘恢复 `null` 覆盖后继续服从内置或自定义主题，避免为了全局深色表面永久改坏其他页面。
- 模式切换会执行一次整栏重绑定，但只发生在用户进入或退出全局键盘时；普通按键输入仍走原有候选刷新路径，不在高频输入链增加额外布局层或网络/native 操作。

### 验证
- `:app:compileReleaseKotlin` 和完整 `./scripts/assemble-release-local.sh` 均成功，没有构建或安装 Debug。
- 正式 APK 为 `fcitx5-android/build/kboard.apk`：`versionName=529adb53`、`versionCode=112`、大小 46,191,389 字节、SHA-256 `35008f75e71616782fa8c657f752b502cd942444f4e6a4bd4d3c2430b86d07d7`；v1/v2 验签成功，证书 SHA-256 仍为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 63 无损覆盖安装返回 `Success`。全局键盘输入 `n` 后截图清晰显示“你、n、能、拿、年、那、内、…”，点击“你”上屏后联想栏清晰显示“好、帮、读、单独、的、们、是、不”；普通键盘同一输入仍沿用原浅色主题。
- 过滤日志无 `FATAL EXCEPTION`、EGL/GLSL 或 `Aquarium renderer stopped`。

### 真机回归矩阵

| 场景 | 期望 | 63 实测 |
|---|---|---|
| 普通中文键盘输入 `n` | 首项蓝色，其余沿用浅色主题深色字 | 通过，整行候选可见，普通主题未改变。 |
| 普通键盘切到全局后输入 `n` | 首项蓝色，其余候选白色 | 通过，显示“你、n、能、拿、年、那、内、…”等。 |
| 全局模式选择“你” | 提交“你”，展示多项白色联想 | 通过，显示“好、帮、读、单独、的、们、是、不”。 |
| 候选存在时观察水族与键盘 | 鱼、按键和候选同时正常 | 通过，未出现候选层遮挡、渲染停止或崩溃。 |
| Release 覆盖安装 | 保留应用数据与默认输入法升级链 | 通过，`install -r` 成功，包名、版本和证书一致。 |

### 待办
- 候选栏仍按现有单行宽度最多展示首屏可容纳项，更多项通过右侧展开入口查看；本次修复的是“已有候选因低对比度不可见”，没有改变候选生成数量或分页策略。

---

## V1.47 - 2026-08-24

### 主题
按 KEMI/RustDesk 联调规范重构全局键盘修饰键生命周期，并修复 V900 隐藏键盘时的触摸穿透。

### 过程
- 对照 `/Users/newlink/kemi/RustDesk/client/kemi-docs/KBOARD-REMOTE-MODIFIER-MOUSE-UPDATE.md` 复查：原全局键盘按住 Ctrl、Alt、Shift、⌘ 时只维护 KBoard 内部高亮与后续键盘组合状态，没有向当前 `InputConnection` 发送独立的修饰键 DOWN；远程鼠标走 KEMI 的另一条输入通道，因此鼠标点击期间远端并不知道修饰键仍被按住。
- 远程复选必须把修饰键建模为跨触摸生命周期的标准 Android `KeyEvent`，不能使用会立即完成整套组合的 `sendCombinationKeyEvents()`，也不能用 `commitText()`、编辑器动作、广播或无障碍事件替代。
- 63 的隐藏键盘穿透来自 IME 窗口在同一 ACTION_UP 分发栈内立即消失，V900 ROM 会把手势尾部重新命中 KEMI 下层按钮；修复原则是完整消费当前手势，在根 View 上延后 100ms 单次隐藏。

### 修改
- 新增 `ModifierStateAction(state, down)`；`DesktopKeyboard` 在修饰键真实 DOWN/UP/CANCEL 时发送状态边沿，同键重复 DOWN 去重，两枚 Shift 共用一份远端按下状态，最后一枚 Shift 松开时才发送 UP。
- `CommonKeyActionListener` 将状态边沿直接交给输入法服务，不进入 Fcitx native job；服务映射为左 Ctrl/Alt/Shift/Meta 标准 keyCode，保留虚拟键盘 deviceId、软键盘 flags、当前完整 metaState 和配对 downTime。
- `DesktopKeyboard.onDetach()`、输入法窗口隐藏、输入结束、解绑和服务销毁都会先补发所有未配对 UP，再清理状态，防止远端卡 Ctrl/⌘；布局切换和重复生命周期回调由服务端集合再次去重。
- `CustomGestureView.Event` 增加 `cancelled`，ACTION_CANCEL 仍给修饰键产生释放，但隐藏键盘的下滑手势遇到 CANCEL 只复位视觉、不执行隐藏。
- 工具栏、候选栏下滑入口和浮动键盘隐藏按钮统一调用 `requestHideSelfAfterTouch()`：按钮先禁用，根 View 延后 100ms 隐藏，重复请求合并；InputView detach 会取消尚未执行的任务并恢复按钮。

### 验证
- `git diff --check` 通过。
- `:app:compileReleaseKotlin` 成功，66 个任务完成；全程没有构建 Debug。
- 完整 `./scripts/assemble-release-local.sh` 成功，Kotlin、R8、Lint Vital、arm64 原生组件与 Release 签名流程通过；正式 APK 为 `fcitx5-android/build/kboard.apk`，包名 `org.fcitx.fcitx5.android`、`versionName=a83a4179`、`versionCode=112`、SHA-256 `d82f37e40b1c8cdb243a3524aaa5a04fbbe48b833f4d5347e1679a79291a6873`。
- `apksigner` 确认 v1/v2 有效，证书 SHA-256 仍为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。63 使用 `install -r` 无损覆盖返回 `Success`，设备查询为 `versionName=a83a4179`、`versionCode=112`，默认输入法仍是主 `FcitxInputMethodService`。
- 63 全局水族键盘实测保留六行键位、鱼群、语音和中英状态。受控 Ctrl DOWN 截图显示 Ctrl 高亮且标准组合功能提示同步出现，UP 后高亮和提示恢复；Alt 的 DOWN→CANCEL 后也恢复原键面，没有卡住修饰状态。
- 点击一次隐藏键盘后 `mInputShown=false`；过滤日志未见 `FATAL EXCEPTION`、`AndroidRuntime`、`PointerDown`、`open_timeout` 或 Aquarium/EGL/GLSL 异常。本次只验证代码路径和单次真机动作，没有代替用户操作真实远程文件。

### 待办
- 在 63 对 Ctrl/Alt/Shift/⌘ 各执行 50 次按下/松开，确认 KEMI 接收端 DOWN/UP 数量一致；分别连接 Windows 与 macOS 验证 Ctrl/⌘+鼠标复选、Shift 范围选择、滚轮和拖动。当前真机只完成单次 Ctrl DOWN/UP 与 Alt DOWN/CANCEL 的 UI/生命周期验证，不能替代远端文件管理器语义。
- 在 D0/D2 两个方向各执行 30 次“打开键盘 -> 隐藏”，确认 KEMI 不断开、不换页、不重新弹出键盘，且日志没有本次触摸造成的下层 `PointerDown`。
- 继续回归 Ctrl+C/V、Command+C/V、Alt+F4、Shift+Tab、中文拼音、候选、Enter、语音和普通键盘；本次不改变这些功能的输入协议。

---

## V1.48 - 2026-08-24

### 主题
正式版本号加一，发布 KBoard 0.1.4 / arm64 versionCode 122 到 `bin/`。

### 过程
- 项目继续使用多 ABI 版本编码：`versionCode = baseVersionCode * 10 + abiId`，arm64-v8a 的 `abiId=2`。因此基础版本从 11 增加到 12 后，正式 arm64 版本由 112 正确升级为 122，不能写成会破坏 ABI 编码规则的 113。
- 语义基础版本同步从 `0.1.3` 增加到 `0.1.4`；版本变更独立提交为 `207449fb`，APK 的 `versionName` 与该源码提交一致。
- 构建继续使用已确认的正式升级 keystore、alias 和证书链，仅执行签名 Release 发布流程，没有生成或安装 Debug APK。

### 修改
- `Versions.kt`：`baseVersionCode 11 -> 12`，`baseVersionName 0.1.3 -> 0.1.4`。
- 正式 APK 归档为 `bin/KEMI-0.1.4-122-207449fb-arm64-v8a-release.apk`。
- 新增独立校验文件 `bin/KEMI-0.1.4-122-207449fb-SHA256SUMS.txt`。

### 验证
- `./scripts/assemble-release-local.sh` 完整成功，Kotlin、R8、Lint Vital、arm64 原生构建、签名和 APK 打包均通过。
- `aapt` 确认包名 `org.fcitx.fcitx5.android`、`versionCode=122`、`versionName=207449fb`、minSdk 23、targetSdk 36；APK 中 native ABI 仅为 `arm64-v8a`。
- `apksigner` 确认 v1/v2 签名有效，证书 SHA-256 仍为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 正式 APK 大小 46,191,978 字节，SHA-256 为 `67d93022042ed0ef9e36e900382753bdbfd5ca90290603bc917d9f6085d4644b`；`bin` 副本与 `fcitx5-android/build/kboard.apk` 逐字节一致。

### 待办
- 本轮用户要求的是正式释放到 `bin/`，没有要求安装设备或推送 GitHub，因此没有改动 63 当前已安装版本，也没有执行远端仓库写入。
- 后续覆盖安装必须使用本次 `bin` 中的签名 APK并保留数据；安装后应确认设备版本为 `versionCode=122`、`versionName=207449fb`。

---

## V1.49 - 2026-08-24

### 主题
移除全局水族键盘的视觉涟漪，保留按键单次水滴声，降低连续输入时的 GPU 开销。

### 过程
- 用户真机体验确认涟漪影响系统体验。源码检查发现每个水面像素每帧最多执行 4 路触点波列、接触凹陷、坡度法线和折射计算，同时主线程还需维护涟漪循环槽并向 GLES 上传 4 组 uniform。
- 初版修正把视觉与配套水滴声一并移除；用户在 63 现场立即指出按键水波纹声音也消失，确认产品要求是“只去掉画面涟漪、保留按键触水声”。随即恢复音频链，避免扩大改动范围。
- 最终按性能优先完整移除视觉涟漪，而不是只把波纹透明度调为零；保留鱼群触点跟随、C-start、投喂角色、松手散开、静态水面氛围、按下单次水滴声、键帽触觉、语音和普通键盘功能。

### 修改
- `DesktopAquariumView.kt`：删除 `Ripple` 状态、4 个循环槽、`uResolution/uRipples` 位置、每帧 ripple 数组填充/上传和片元 Shader 波列、法线、折射、凹陷/波峰计算；水面仅保留单 `uTime` 的低成本渐变、慢速流光、弱焦散和暗角。
- `DesktopKeyboard.kt`：按下仍把 DOWN/MOVE/UP 镜像给鱼群，并继续触发一次水滴播放；初始化和重新挂载仍异步预加载水滴音频。
- `InputFeedbacks.kt`：保留水族专用 `SoundPool`、4 个样本轮转、上一 stream 停止和 30% 音量策略；普通键盘声音与触觉反馈逻辑不变。
- 同步更新 `desktop-aquarium-engine.md`、`kemi-rd/gm/KBoard摸鱼水族键盘复刻设计.md` 和构建部署文档，明确当前复刻基线只移除视觉 ripple，不移除水滴音频。

### 验证
- 静态扫描确认 `DesktopKeyboard`、`InputFeedbacks` 和水族引擎中已无 `Ripple/uRipples/MAX_RIPPLES/prepareRippleSound/rippleSound` 执行引用；项目其他页面原有 Android `RippleDrawable` 不属于水面涟漪，未改动。
- `./gradlew :app:compileReleaseKotlin` 成功；只执行 Release 变体，没有生成或安装 Debug APK。
- `./scripts/assemble-release-local.sh` 完整成功；正式包为 `versionName=80325aee`、`versionCode=122`，v1/v2 签名有效，证书链保持不变。APK SHA-256 为 `f658a757aeb09768c687d1e5be12782b8524d1f53d84009cfa6634a3b58f3003`。
- APK 资源表确认 4 个水滴 WAV 均被保留；63 使用 `install -r` 无损覆盖返回 `Success`，设备查询为 `80325aee/122`，默认输入法仍是主 `FcitxInputMethodService`。视觉涟漪已从源码执行路径彻底移除；水滴声音量/听感由用户在 63 现场继续确认。

### 待办
- 水面仍以固定 30Hz 绘制鱼群和低成本流光，因此本次只消除触点相关的额外片元开销，不是关闭整个 GLES 水族层；若系统仍有压力，应先用真机 FPS/SurfaceFlinger 数据定位，不得直接牺牲按键输入或水滴声音链路。
- 水滴声属于保留功能，最终 Release 必须包含 4 个 WAV，并在系统输入音效开启时于 DOWN 只播放一次；MOVE/UP 不得叠加声音。

---

## V1.50 - 2026-08-24

### 主题
修复远程桌面隐藏后重新显示 IME 时先闪普通键盘、再切全局键盘的问题。

### 过程
- 远程桌面重新请求显示键盘时，Android 可能在 KBoard 进程仍存活的情况下重建 `InputView`。全局模式请求已经保存在 `KeyboardWindow.desktopModeRequested`，但 `onCreateView()` 固定先挂载 `TextKeyboard`。
- `InputView` 创建完成后，`onStartInput()` 才读取全局模式并通过主线程 executor 异步切换到 `DesktopKeyboard`，因此普通键盘成为一个真实可绘制的中间帧；远程桌面只是触发 IME 显示，问题所有权在 KBoard。

### 修改
- `KeyboardWindow.onCreateView()` 首次挂载布局时直接读取 `desktopModeRequested`：已选全局模式就创建 `DesktopKeyboard`，否则保持原 `TextKeyboard`。
- 后续 `onStartInput()` 的输入类型/全局模式选择、用户主动切换和进程重启后回普通键盘的原策略不变。

### 验证
- `git diff --check` 与 Release Kotlin 编译待执行；完成签名 Release 后覆盖安装 63，并由用户在真实远程桌面链路重复“隐藏 -> 显示”确认不再闪普通键盘。

### 待办
- 本修复只消除 KBoard 自己创建的普通键盘中间帧。如果日志显示远程桌面连续创建两个不同 EditorInfo/输入会话，仍需分别记录 `onStartInputView` 次数，但不能再通过固定首挂普通键盘放大闪烁。
- KBoard 进程被系统完全杀死后仍按既有安全策略回到普通键盘；本次只保证同一进程内用户明确选择的全局模式跨 InputView 重建保持。

---

## 维护规则（当前生效）

- 只记录输入法项目，不写其他项目记录。
- 每条记录固定包含：主题、过程、修改、验证、待办。
- 新增内容按时间追加，不覆盖上一条历史。
- 根目录 `cl.md` 是唯一变更日志，项目目录不再保留重复副本。

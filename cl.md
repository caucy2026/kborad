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

## 维护规则（当前生效）

- 只记录输入法项目，不写其他项目记录。
- 每条记录固定包含：主题、过程、修改、验证、待办。
- 新增内容按时间追加，不覆盖上一条历史。
- 根目录 `cl.md` 是唯一变更日志，项目目录不再保留重复副本。

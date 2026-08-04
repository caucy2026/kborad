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
- GitHub `main` 已更新到源码提交 `3c62d79d`；完整项目、文档和 APK 备份在 `kemi-release-0.1.2-126` 分支。

### 验证
- Release 构建 `BUILD SUCCESSFUL`，APK 签名验证通过，SHA-256 校验返回 `OK`。
- `adb -s 192.168.3.63:5555 install -r` 覆盖安装成功，设备版本为 `0.1.2-126-g3c62d79d`。
- 正式输入法 `org.fcitx.fcitx5.android/.input.FcitxInputMethodService` 已设为默认。
- Notes 真机输入框中 `mInputShown=true`、`mIsInputViewShown=true`，截图确认 KEMI 正常显示，过滤 logcat 无相关崩溃。
- GitHub 源码主分支与完整 Release 备份分支均推送成功。

### 待办
- 后续 Release 应继续遵循“先提交源码、再构建带提交哈希的 APK、最后生成校验文件”的顺序。

---

## 维护规则（当前生效）

- 只记录输入法项目，不写其他项目记录。
- 每条记录固定包含：主题、过程、修改、验证、待办。
- 新增内容按时间追加，不覆盖上一条历史。
- 根目录 `cl.md` 是唯一变更日志，项目目录不再保留重复副本。

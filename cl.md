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

## 维护规则（当前生效）

- 只记录输入法项目，不写其他项目记录。
- 每条记录固定包含：主题、过程、修改、验证、待办。
- 新增内容按时间追加，不覆盖上一条历史。
- 根目录 `cl.md` 是唯一变更日志，项目目录不再保留重复副本。

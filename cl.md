# KBoard 输入法项目变更日志（cl）

## 2026-09-22 - 系统边缘返回事件误拦截修正

- H730 的边缘返回也可能带 FLAG_VIRTUAL_HARD_KEY，原 IME BACK 隐藏分支在键盘隐藏后仍消费事件，影响前台应用返回。
- 移除该分支的强制隐藏/消费行为；鼠标 BACK 放行及桌面键盘独立导航发送通道保持原样。修改 FcitxInputMethodService.kt，增加 NavigationBackEventPolicyTest.kt。
- 1.4.3/172 平台签名候选已在 172.21.16.24 覆盖安装，构建及 JVM 测试通过。设备复测未见旧拦截日志或崩溃，但未取得浏览器退出成功的证据；普通 BACK 仍走既有 Fcitx/父类处理，不能据此宣称整个返回链路已验收。
- 风险待验：键盘显示/隐藏时系统返回、双屏导航隐藏、远程桌面自身返回按钮。Release 构建不代表商城正式发布或全量设备验收通过。

## 2026-09-22 - 新增可拖动极简键盘（候选，未发布）

### 当日最终状态与备份范围

- 备份前合并云端三个提交（截至 `79c4e787`），保留跨屏显示判定、商城及CI更新；冲突仅为日志同位置追加和默认语言资源，日志保留双方，默认英文保留并补入新增语音提示。合并后未重新构建或设备验收，既有APK和测试结论仅对应合并前候选。
- 源码与文档已在本地提交 `14dd962e`（18个文件）；本次按用户要求备份到 `git@github.com:caucy2026/kborad.git` 的 `main`，不安装设备、不进行正式发布。下方保留各候选演进记录，旧尺寸、返回行为和连接阻断不代表最终状态。
- 最终极简布局：320dp宽、112dp内容高，导航栏间距另计；返回普通键盘、语音、删除、图标Enter四等分并统一主题，alpha=0.85，空/过期/已消费剪贴板建议隐藏，点击输入后标记消费，保留系统剪贴板和历史。
- 63当前仍为已安装的alpha85候选；最新voice-feedback候选仅完成Release构建、正式证书验签及4项语音路由单测，尚未安装。10轮拖动返回普通键盘通过属于alpha85布局专项，不能计为新语音候选的端到端通过。完整语音、目标端收字及全模式矩阵仍待验证。
- 本次云端备份包含源码、资源、测试和文档；未将未验收APK标为正式发布包，也不纳入无关旧APK删除、原生子模块工作树改动、预编译输出或截图。

### 实现、迭代及验证证据

- 14:37–14:38 极简语音“无效果”现场：按钮DOWN/UP正常收到，多次按住1.2–1.9秒后UP仍为Starting，既有分支取消鉴权/连接并清空提示；普通模式随后成功final length=10，极简稍后也成功final length=8。因此已证实至少存在“连接未就绪被松手静默取消”，不是所有极简点击都无监听，也不能凭final长度认定远端实际收字。
- 语音提示修复候选构建成功，4项VoiceOutputRoutePolicyTest通过（不代表新增状态/网络端到端验收）；正式平台证书v1/v2验签通过。候选 `bin/KBoard-1.4.2-202-voice-feedback-release.apk`，SHA256 `e13e1d74799e89fc05c8ecc5cab43f2ada9cbce31f39d775639c0660ecfe254a`。尚未覆盖63，现场由远程任务协调用户测试窗口；默认输入法及现场配置未动。真机Starting释放/Listening释放/取消/失败重试和目标端收字仍BLOCK。
- 本轮最小候选：`KawaiiBarComponent` 将Starting与Listening提示区分，按下先提示“正在连接语音，请按住稍候”，实际Listening才提示“正在听”；Starting松手仍取消，不允许松手后暗中开麦，但增加明确重试提示。`IflytekAsrClient.finish()` 对空文本发受generation保护的错误回调，清理校准UI并提示没有识别到语音，避免永远校准且无结果。权限、160ms阈值、24px移动取消、WebSocket started后开麦和600ms最终预览不变；未变更键盘几何/远程协议。构建和实机验收尚在进行，此项仅修状态提示与静默失败，不声称解决全部启动延迟/远端传递问题。
- 极简退出布局修正：`InputView.setMinimalKeyboardMode()` 在退出时同步清零拖动位移、取消轮廓/矩形裁切、清除悬浮阴影、恢复全宽，再应用普通或桌面约束；`updateFloatingKeyboardLayout()` 的延迟定位回调增加极简/桌面模式和浮动偏好一致性检查，避免旧回调覆盖新模式。只修复极简尺寸/位移残留，不修改普通和全局键帽高度比例。
- 最终透明度按最新要求为15%透明（极简alpha=0.85），退出恢复1.0。新候选 `bin/KBoard-1.4.2-202-minimal-alpha85-release.apk` SHA-256 `09714735f8f9bda843fc385c186cc6d9642b9d9a5937b234eb973a30562a4b98`，正式平台证书v1/v2通过；63覆盖安装成功，设备base.apk回读哈希相同。远程客户端未修改、未安装，本轮实装为1.4.126+290，hash `b7f74de268266fb59ef1503541171484ea7fcb2927cc328a7668f5a2444493c5`。
- 新候选63实际远程物理Overlay执行10轮“极简拖动→普通”，每操作间隔3秒，模式键按压120ms。10次日志均恢复panel=1920x741、content=1920x549、offset=0,0、clip=false、alpha=1；完整普通键盘显示正常。初次工具图片预览仅显示黑色上半区，随后读取原图并逐像素核对发现10张PNG的键盘区域(0,539)-(1920,1280)完全一致，区域SHA256均为 `7723bda5db002ba3a54c74be6e90556e7ff76457c7b09ab4cd28ba895305c2e7`，不是设备黑屏。该狭义拖动返回专项10/10通过，不代表全量PASS。底部隐藏操作已执行；语音、剪贴板精确输出及全矩阵验收未完成。证据 `/Volumes/ORICO/kemi-build-cache/app-release-gate/kboard/android/202-minimal-acceptance-20260922/layout/`。
- 语音路由复核：最终回调延迟600ms后经 `commitTextFrom(overlayRequestId,text)`，物理请求ID有会话归属校验，组件销毁取消语音提交任务；尚无同一远端编辑焦点epoch校验，不能声称焦点切换期间异步语音绝不误投。用户后续明确要求扩展屏VSCode隔离文件语音测试，已交远程任务接管63继续验证，不录未知环境音。完整门禁仍BLOCK。
- 统一样式/剪贴板修正候选：四键采用 Normal 主题键帽；删除与 Enter 保留原行为和反馈，仅使用极简独立 View ID，避免普通回车 ID 触发圆形/Enter文字特殊渲染。剪贴板遵循原版 suggestion 开关、时间有效期及 consumed 状态；空/过期/已消费建议隐藏，点击输入后调用 consumeSuggestion，保留历史记录。语音过程中不触发剪贴板输入。
- 极简麦克风在离线时不再直接禁用，以便原共享语音处理器显示明确错误提示；完整ASR链路仍复用既有实现。不能据此断言已解决用户报告的全部语音无响应。
- 候选 `bin/KBoard-1.4.2-202-minimal-uniform-release.apk`，SHA256 `b4ec8bbf07214895b94b0ad9f0ae9b25da8a905b7c4e6598d9f5411e501e4334`，正式证书验签、构建及4项VoiceOutputRoutePolicyTest通过，63覆盖安装成功。截图确认四键同色背景、纯图标回车及空剪贴板隐藏。剪贴板实际消费完整闭环尚未确认；真实录音至讯飞的操作被安全审批拒绝，已询问用户明确授权，未绕过执行。因此整体验收仍BLOCK，不称全部修复。
- 极简单排功能键迭代：面板限制 320dp 宽；上排为剪贴板摘要和拖动柄，下排返回普通键盘/语音/删除/Enter 四等分，复用普通键帽。极简语音取消桌面专属深色物理键背景，静态图标统一使用主题文字色。新增独立极简入口与返回键盘矢量图标。返回明确恢复普通字母键盘并关闭悬浮模式，不再按历史模式返回全局。剪贴板点击通过既有 `commitTextFrom(overlayRequestId, text)` 输入当前预览对应原文，空剪贴板和语音过程不触发粘贴，不打开列表。
- 单排候选正式构建/验签及63覆盖安装成功，截图核对四键排列，实际点击返回按钮恢复普通键盘。`bin/KBoard-1.4.2-202-minimal-row-release.apk` SHA-256 `3a09d15b51194d24f2c7ffed5c8216ef3c1eb26d432f5543f30a029339c3410e`。完整语音与远程粘贴链路尚未验收，不能称全量通过。
- 极简视觉复核：63 截图确认原面板约 1190px 宽，删除大矩形与回车小圆形失衡。仅极简模式改为 40% 屏宽、320–384dp 范围、112dp 内容高度；导航栏 Insets 另计，避免压缩键帽命中高度。移除极简自定义键帽尺寸参数，继承当前普通键盘主题；回车沿用原行为/图标/ID/反馈，仅改成与删除协调的矩形键帽，摘要文字缩至 14sp。普通和全局按键布局不变。
- 紧凑版 Release 构建/正式证书验签通过，63 覆盖安装成功；副屏截图确认 768px 宽、删除和 Enter 等宽矩形，按钮位于导航栏上方。包为 `bin/KBoard-1.4.2-202-minimal-compact-release.apk`，SHA-256 `89d97d534bd5ce687d5ee0086fda24336e4edf4c214ffb9d0e43d252c79f8569`。本轮为布局与启动验证，不代表完整语音/跨屏回归通过。
- 63 后续连接恢复：12:29 正式签名候选 `install --no-incremental -r` 成功，设备端 APK SHA-256 回读与 `328c60f0…42058813` 一致，默认输入法不变，未卸载或清数据。副屏便签启动后 IME 显示于 D2，读取到 75px 导航栏 Insets。用户随后要求优先调查异常提示，极简交互验收尚未完成。
- 警告线索：保留日志中 09-21 20:24:04 出现 `Ignoring showSoftInput` 和 `reportStartInput/setInputMethod ... invalid token`，同段存在双屏配置变化；09-22 12:28:27 出现 `Unable to send config for IME proc ... no app thread`。这些仅为会话/配置切换异常线索，尚未对应到用户所见提示，不能判定根因或已修复。此次安装前后退出记录为 `installPackageLI`，不应计为崩溃。原始证据位于 `/Volumes/ORICO/kemi-build-cache/app-release-gate/kboard/android/202-63-minimal-20260922/`。
- 在普通、悬浮、全局之外新增独立 `Minimal` 显示模式；普通工具栏增加“极简键盘”入口，进入前同时保存显示模式和实际布局，返回键可准确恢复普通字母、数字、悬浮或全局键盘，进程重建后也不会递归返回极简模式。
- 极简界面仅保留剪贴板摘要/入口、语音实时状态、返回上一键盘、按住语音、删除和确定。删除与确定复用既有 `BackspaceKey`/`ReturnKey` 输入及反馈链路；剪贴板复用 `ClipboardWindow` 和弱引用更新监听；语音复用既有权限、网络、partial、松手校准、final 提交及清理流程，没有新增第二套 ASR 客户端。
- 极简键盘使用独立 62% 自适应宽度（360–720dp）和 160dp 高度，拖动柄可在当前宿主窗口范围内自由移动并做边界钳制；不读取或改写普通悬浮键盘的宽高、停靠和位置偏好。物理 Overlay 仍保持紧凑窗口高度，避免为了拖动把透明窗口扩大到全屏并截获下层触摸。
- 生命周期保护：布局分离时注销剪贴板监听，InputView 销毁沿用统一 `dispose()`；切换/旋转后的延迟定位带模式校验，极简拖动不会触发输入、按键音、悬浮停靠或全局水族响应。
- 复核补充：切换极简模式时取消进行中的语音启动/提交任务并解绑旧语音按钮；候选栏状态变化不再重新显示隐藏工具栏。打开完整剪贴板时恢复其返回栏，剪贴板数据库读取和更新监听随键盘分离取消，避免挂起任务持有旧 View。全局模式长按退出按钮进入极简模式。
- 拖动命中：`FcitxInputMethodService.onComputeInsets()` 仅在极简模式按实际面板位置设置触摸区域，拖动请求重新计算，透明区域不作为键盘命中区；保留原普通/全局分支。此行为仍需真机验证。
- 自动验证：Release 构建及新增/相关 `KeyboardPresentationModeTest`、`PhysicalOverlayWindowPolicyTest`、`VoiceOutputRoutePolicyTest` 共 13 项通过。此前全量 JVM 98 项中 97 项通过，唯一失败为既有 `ThemeSerializationTest.version2` 的“v2 不应迁移”夹具断言，失败栈不涉及本次输入、布局、语音或剪贴板文件。
- 63 真机门禁阻断：2026-09-22，`adb connect 192.168.3.63:5555` 及两次 TCP 5555 检查均超时。未覆盖安装、未清除数据、未进行交互和截图验收，不能宣称真机通过；未用其他设备替代指定的 63。
- 交付候选：`bin/KBoard-1.4.2-202-minimal-candidate-release.apk`，包名 `com.newlink.kemi.kboard`，1.4.2 / 202，arm64-v8a。最终源码重新构建 Release 成功，13 项相关单元测试通过；v1/v2 验签通过，平台证书 SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。APK SHA-256 `328c60f0437a8f6b7b9e5b349c28b7494b66ca20e34cfbc9861cb3fa42058813`。构建日志 `/Volumes/ORICO/kemi-build-cache/kboard-minimal/release-final.log`。按发布稳定性门禁保留为待真机候选，不视为正式验收发布。

## 2026-09-21 - 远程切屏焦点修复与候选验证

- 修复远程代理输入框被外层容器抢焦点、切屏期间重复恢复的问题；保留远端物理 Overlay/Host rehome 兼容路径，并增加新代理私有输入连接协商。
- KBoard 1.4.3/172 与远程桌面 1.4.126/263 已用平台证书保留数据安装到16.24；基本输入、普通/全键盘、本地代理双向切屏冒烟通过。真实远程切屏待人工确认。
- 未提交工作流、发布脚本、APK、日志、截图、签名文件；完整远程JVM仍有既有商城提示语言断言失败。

## 2026-09-20 - 隐藏键修复误吞PAD物理右键（候选，未发布）

- 75真实HL mouse BTN_RIGHT双边沿产生SOURCE_MOUSE/KEYCODE_BACK及BUTTON_BACK事件；IME已隐藏仍进入新增onKeyDown BACK分支，客户端没有收到PhysicalMouse，Windows无菜单。原失败证据保留于287-nav75-20260920/deadzone-fix/mouse-right-failure。
- 根因：导航隐藏拦截未区分输入来源，错误消费鼠标生成的BACK。这属于本轮隐藏修复引入的关联回归。
- 最小修复：FcitxInputMethodService.onKeyDown/onKeyUp仅对鼠标SOURCE_MOUSE/SOURCE_MOUSE_RELATIVE来源BACK返回false，成对交回前台应用；系统导航隐藏仍走原分支。未改键盘布局、字符/语音协议、视频层和普通按键。
- 正式Release签名与安装校验通过，75候选SHA256 feab4546407872aa28a6ec91f3a19355f585a2ef2797c331b5572df187241e2a。原失败动作首次复测已在Windows看到上下文菜单及客户端down/up；正在逐变体10次验收。隐藏/HOME计数必须随新候选重测，整体仍BLOCK。

## 2026-09-20 - 输入后首次隐藏被SystemUI防误触吞掉（候选，继续验收）

- 根因证据：75 的 SystemUI `DeadZone` 在输入后短按隐藏按钮中心时记录 `consuming errant click: (86.0,46.0)`；导航栏 y=1184..1280，中心 y=1230 落在最大64px防误触区。该次无 BACK 到达IME，稍后点击或按钮下沿可到达，不能靠修改测试坐标判通过。
- 最小修复：仅 Android12/API31、hi3781v730、system UID、IME在D0时复用既有非聚焦隐藏命中层；普通同屏仍不启用跨屏HOME观察器，不改输入、布局和视频。隐藏/切走移除命中层。
- 正式候选SHA256 `092ac309f9e0601f444d985b04f0ee0b71fa9cec930072b96d7f926708bc8bf9`，1.4.1+182，平台证书一致，75覆盖安装并回读哈希一致。
- 主屏普通便签+同屏浮动模式，真实qwe空格→原中心隐藏→重开→4次删除→隐藏→空文本，10/10通过；每轮XML证明输入和删除。其他模式、HOME、跨屏继续执行，整体未通过不得发布。
- 证据：既有287-nav75-20260920下 `deadzone-fix/main-floating`；旧包失败与DeadZone日志保留。

## 2026-09-20 - 75导航验收迭代记录（未发布，整体BLOCK）

- 用户最新要求每项及适用变体各10次，每项独立报告；失败保留原证据，修复后新包重新计数。
- 再次显示隐藏失效：onWindowShown重建D2编辑器→D0键盘的隐藏桥接；普通同屏不得启用跨屏分支。
- HOME上滑第4轮失败：系统接管时最后MOVE仅5.36px；观察器之前忽略CANCEL携带的最终位置。CrossDisplayNavigationObserver改用最终触点并保留系统原事件返回值。新候选普通隐藏/上滑各10次通过，后续包仍需复测。
- 覆盖安装首开隐藏失效：桥接路由原为进程内变量。改为依据当前编辑器包唯一任务显示屏恢复；同一包多屏任务无法确定时不猜测，保留显式切屏状态，该变体尚待验证。
- 全局键盘本地HOME无效：此前只向InputConnection发送HOME，普通编辑框不能执行系统导航。仅非远程编辑器按下时记录目标屏，抬起时发指定显示屏系统导航双边沿；KEMI和物理Overlay保留原输出路径。SystemMouseInjector复用既有平台INJECT_EVENTS能力，无新权限；未改变键盘布局。
- 当前候选KBoard1.4.1+182 SHA-256 `8a5fa8e91ba2108897bca1abee1f4e7b597dd61b38c3b5cecb769bb262daf690`，正式平台证书c8a2e9bc…92ab8，75覆盖安装成功。
- 本地全局HOME在副屏便签→主屏键盘下10/10：每轮确认SecondaryDisplayLauncher恢复、mInputShown=false，系统记录display=2、key=3完整双边沿。仅此子项已完成，不能说全量通过。
- 同包远程输入、普通/浮动/全局完整模式矩阵、HDMI、扩展、鼠标、语音和资源仍在执行；禁止发布。
- 证据：`/Volumes/ORICO/kemi-build-cache/app-release-gate/kemi/android/287-nav75-20260920/`，各失败目录保留。逐项报告在RustDesk/client/kemi-docs/keyboard-quality/DELIVERY-RESULTS-20260920.md。

## 2026-09-20 - 跨屏键盘再次显示时恢复隐藏入口（候选，未交付）

- 用户前提必须保持：普通便签在副屏获得输入焦点，键盘通过切屏按钮移至主屏。编辑器 D2 / IME D0 是合法跨屏状态，不应通过恢复同屏来冒充修复。
- 复现：63 的 +182 候选在该状态点击主屏左下角隐藏后 `mInputShown=true`，窗口树中没有 `KBoard navigation hide bridge`。
- 代码缺口：桥接只在切屏时建立，窗口隐藏后移除；下次 `onWindowShown` 只确认已存在的桥接，没有重建。修改 `DesktopNavigationHideBridge.kt` 保存进程内明确切屏方向，每次窗口显示时在 D0 跨屏路由重建并确认，反向切屏立即清理；`FcitxInputMethodService.kt` 接入。未修改键盘布局、按键、语音和远程输出协议。
- 验证：`:app:compileDebugKotlin --offline` 成功。正式签名因自动审批拒绝私密口令访问尚未执行；随后 63 ADB 返回 Host is down。候选未安装，不得称隐藏或 HOME 已修复。
- 最短待验：副屏焦点→切主屏→隐藏→再次弹出→再次隐藏；保持同样前提验证 HOME 上滑；切回副屏及主屏普通输入验证原行为。HOME 原因仍待现场闭环，不按推测增加手势拦截。

## 2026-09-20 - 副屏编辑器切主屏后左下角隐藏键候选（远程桌面回归，禁止发布）

- 现场与根因：在 Display 2 的便签输入框唤起 KBoard，再把键盘切到 Display 0 后，IME 窗口和 token 已位于主屏，但输入连接仍属于 Display 2。V900 Android 12 的主屏导航栏会把左下角 BACK 事件发送给主屏前台应用，而不是 D2 所属的输入法会话，因此按钮可见但无法隐藏键盘；单纯调用 `requestHideSelf()`、设置 BACK disposition 或改变 IME 焦点属性均不能可靠解决，后者还会破坏跨屏窗口稳定性，未保留这些实验方案。
- 修复：新增 `DesktopNavigationHideBridge`。仅在 KBoard 执行 D2→D0 路由时，使用现有 system UID 与 `INTERNAL_SYSTEM_WINDOW` 权限，在 Display 0 系统左下角导航隐藏键上建立透明、不可聚焦、固定小范围的命中层；点击后同时释放桌面组合键/鼠标状态、关闭当前 IME 窗口并请求系统收起输入法。D0 确认显示前有 6 秒失败超时，反向切屏、窗口隐藏或点击成功后立即移除，避免残留和遮挡其他导航按钮。
- 修改文件：`AndroidManifest.xml`（声明平台签名级内部窗口权限）、`DesktopNavigationHideBridge.kt`（D0 隐藏键桥接及生命周期清理）、`FcitxInputMethodService.kt`（跨屏路由接入、窗口显示确认和统一隐藏入口）。未修改便签、KEMI 远程办公、普通键盘布局、全局键盘按键协议、水族背景和语音逻辑。
- 构建与签名：正式 Release 和 `DesktopKeyboardModeStateTest` 联合构建通过；包名 `com.newlink.kemi.kboard`、版本 `1.4.1`（versionCode 182）、UID 1000，v1/v2 验签通过，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`；APK SHA-256 为 `d23056c9b9da2eceeb50c1a673d7d0b9f00cc3404a88c32cd9623f3c605b5d39`。
- 63 号普通便签定向验证：正式包覆盖安装成功且未清数据。按“Display 2 打开便签搜索框→弹出普通键盘→点击切屏→Display 0 点击左下角隐藏键”路径，点击后 `mShowRequested=false / mInputShown=false`，桥接层按 `armed → confirmed → disarmed` 清理，未见 KBoard FATAL、ANR、`WindowLeaked` 或输入分发超时。
- 发布阻断：随后在真实 KEMI 扩展远程桌面复测发现，当前 canonical 源码缺失 2026-09-18 记录中的物理 Overlay 实现，并恢复成“发送 `SWITCH_EXPANDED_KEYBOARD` 后继续迁移系统 IME token”的旧路径。D2 键盘切到 D0 后，D0 远程视频虽仍保持约 27 FPS，但可见区域被系统 IME 从全屏压缩到约 y=568 上方；这违反“键盘不得改变远程视频 Activity/Surface 尺寸”的发布门禁。因此 versionCode 182 仅是失败候选，不得作为正式修复或发布依据；必须恢复独立物理 Overlay 路径并同时通过 KB-OVL-003、KB-OVL-006、KB-OVL-010 后才能重新发布。

## 2026-09-18 - 138 平台签名测试包部署与版本入口实测

- 用户提供签名口令后仅经不回显交互传给签名工具，未持久化口令。新包 v1/v2/v3 验签通过，平台指纹 c8a2e9...92ab8，APK SHA256 `1BB439F8F57020253066D4DC441EBC56B6FD99211F0EF6A8B4AE597D391DA354`。
- 138 首次增量安装因设备离线失败，重连确认旧版未变后用 `install --no-incremental -r` 成功；当前 1.4.2/162、system UID1000、默认主输入法保持，未卸载或清数据。
- 界面实测：关于页正常，接口 available=false 无红点，短按提示“当前已是最新版本”；持续按住 8.5 秒弹工程密码框，松手未跳商城。随后取消密码框、留在关于页供用户查看。
- 局限：商城当前没有更高版本，未验证有新版红点/跳转/实际升级，不制造假更新。截图与记录见 `test-reports/version-entry-20260918/deploy-138/`；未提交或推送 Git。

## 2026-09-18 - 138 版本入口测试包构建，待平台签名部署

- 用户要求部署到 172.21.16.138；ADB 核对现有 1.4.1/152、system UID1000，旧包平台证书指纹与指定值一致，已备份旧 APK。
- 同步当前本地应用源码/资源到已有 WSL 构建副本前先备份；assembleDebug 成功（196 tasks，4m29s），生成 `bin/KBoard-1.4.2-version-entry-138-debug.apk`，版本 1.4.2/162、arm64。
- 阻断：产物仍使用普通 Debug 证书，需要私密凭证存储中的平台签名口令才能重签；已请求用户提供私密文件路径。未安装、卸载、清数据或修改设备默认输入法，未提交推送。
- 证据：`test-reports/version-entry-20260918/deploy-138/README.md`、构建日志和旧 APK 备份；不宣称新界面已经在 138 验收。

## 2026-09-18 - 版本行更新红点及 8 秒长按工程入口

- 行为：关于页版本号有商城新版时显示红点，短按跳转 KEMI 商城；确认无新版时提示“当前已是最新版本”，未知/失败可重试，检查中不重复请求。“稍后”仅忽略自动弹窗，不隐藏更新红点。
- 工程入口：版本行由连续点击 7 次改为持续按住 8 秒弹出原密码框；中途移出、滑动、多指、取消、离开页面停止计时，长按触发后松手不执行短按。
- 修改：AboutFragment、MainActivity、MarketUpdateController、中英文资源；新增 VersionPreference、VersionHoldGesture、MarketVersionState 与 5 项 JVM 测试；同步发布说明。
- 审查修正：红点改为原位更新，避免更新结果触发 RecyclerView 重绑定而中断正在进行的长按；手动检查的后续跳转绑定 About 页视图生命周期。
- 验证：先记录缺少实现的失败基线，最终 Debug/Release Kotlin 编译成功；新增和既有定向测试共 20 项通过。证据见 `test-reports/version-entry-20260918/REPORT.md`。
- 限制：.24 当前普通 UID10052 与正式 system UID 不兼容，本次未部署、清数据、卸载、提交或推送。实际红点/触摸/商城交互尚待真机验收；长时间保持同页前台的缓存过期刷新仍需补测及完善，不以本次定向测试代替发布验收。

## 2026-09-18 - 最近商业 UI 与候选显示改动专项回归

- 范围：在 16.24 回归商业首页/工程密码入口、外观主题、两条隐私策略入口、英文输入与退格、中文候选，以及全局键盘往返后的候选颜色恢复和键盘显示/隐藏。
- 自动化：`EngineeringAccessGateTest` 3 项、`DesktopKeyboardModeStateTest` 2 项、`CommercialPrivacyPolicyFragmentTest` 2 项通过；Gradle 构建共 238 tasks 成功。`CommercialThemeFragmentTest` 1 项因导航动画完成前立即查找开关而失败，单独重复 3 次均在 `assertNotNull(switch)`，手工等待页面稳定后的主题开关和 5 次进出均通过，判定为测试时序缺陷而非产品崩溃。
- 设备兼容：设备基线是普通 UID 的 `1.4.1/152`，正式 system UID 包无损覆盖返回 `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`；未卸载或清数据，改用同源码、同平台证书、仅移除 `sharedUserId` 的普通 UID `1.4.2/162` 兼容测试变体完成本轮 UI/输入逻辑回归。
- 结果：最近四项用户功能手工回归通过；`nihao` 候选在普通模式和全局键盘往返后均清晰可见，输入法进程不重启；FATAL、KBoard ANR、DisconnectedException、Required value was null、NPE、ActivityNotFoundException 与 StaticLayout 匹配数均为 0，默认输入法未改变。
- 限制：该结果不替代正式 system UID/系统镜像验收，也未覆盖 5 小时 Monkey、麦克风/ASR、商城升级或完整双屏发布门禁。完整报告和证据见 `test-reports/ui-regression-20260918/REPORT.md`。

## 2026-09-17 - 隐私政策改为应用内说明页

- 行为调整：“关于 KBoard”和“隐私”页面中的“隐私政策”均改为应用内导航，不再发送浏览器 `ACTION_VIEW` 或打开外部网页；新页面标题为“隐私策略”，正文为“KEMI Kboard不要求联网权限，也不搜集任何个人信息。”。
- 修改范围：`AboutFragment.kt`、`CommercialSettingsFragments.kt`、`SettingsRoute.kt` 及中英文 `strings.xml`；新增 `CommercialPrivacyPolicyFragmentTest.kt`，覆盖两个入口、目标路由和正文显示。
- 自动验证：先在缺少新路由和文案资源时确认测试编译失败，再完成实现；`:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin` 和 `:app:assembleDebugAndroidTest` 均成功。16.24 真机运行 2 项 instrumentation 测试，结果 `OK (2 tests)`。
- 构建与签名：测试包为 `bin/KBoard-1.4.2-local-privacy-policy-arm64-v8a-platform-signed-test.apk`，包名 `com.newlink.kemi.kboard`、版本 `1.4.2/162`、ABI `arm64-v8a`；APK SHA-256 `04D6D6F1BA0203B77F64D018D3A5F69300E7F88467CD2DB9B314FBC02E8F8AB9`，v1/v2/v3 验签通过，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 真机验证：覆盖安装到 `172.21.16.24` 返回 `Success`，未清除用户数据；两个入口均显示同一应用内页面及准确正文，未拉起浏览器。定向日志未发现 FATAL、KBoard ANR、`ActivityNotFoundException`、旧隐私网址或 `ACTION_VIEW`；应用冷启动成功，默认输入法仍为主 `FcitxInputMethodService`。测试辅助包验证后已卸载，主应用保持运行。
- 发布口径风险：当前工程的在线语音识别链仍声明并使用 `INTERNET`；因此“不要求联网权限”与现有 ASR 网络能力存在表述冲突，商业发布前需确认该文案是否仅指键盘基础输入功能，或同步调整在线语音能力。

## 2026-09-17 - 修复商业版“外观主题”页面点击崩溃

- 根因：商业主题页以代码创建 `MaterialSwitch`，H730 Android 12 上 `SwitchCompat` 默认尝试测量内部 ON/OFF 文本，但 `textOn` 与 `textOff` 均为空，最终在 `StaticLayout` 中对空 `CharSequence` 调用 `length()`，触发主线程 NPE。
- 修复：明确设置 `showText = false`，保留外部“跟随系统夜间模式”标题、开关状态与主题选择逻辑，仅关闭未使用的开关内部文字布局；真机首次回归又发现 `MaterialSwitch` 在当前非 Material 主题下无法解析颜色属性，开关本体不可见且不可点击，因此改用与现有 AppCompat 主题匹配的 `SwitchCompat`，同时消除对应 `ResourcesCompat` 警告。
- 构建与签名：复用已验证 WSL Android/NDK 环境执行 `:app:assembleDebug`，196 tasks 构建成功；新增 Android 回归测试的 `:app:compileDebugAndroidTestKotlin` 也构建成功。最终平台签名包为 `bin/KBoard-1.4.2-theme-page-fix-arm64-v8a-platform-signed-test.apk`，包名 `com.newlink.kemi.kboard`、版本 `1.4.2/162`、ABI `arm64-v8a`，APK SHA-256 `31A53123A8C0951567C967095743A22290B852A6E764BF4C5CD0F5FADB59BDF2`。v1/v2/v3 验签通过，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 真机验证：覆盖安装到 `172.21.16.24` 返回 `Success`，未清数据；“外观主题”页面正常显示主题卡片及开关，开关可从 `true` 切换到 `false` 并恢复为 `true`。额外重复返回/进入页面 5 轮，应用 PID 始终为 679；默认输入法仍为主 `FcitxInputMethodService`，目标 NPE、FATAL、ANR 与主题资源警告均为 0。
- 项目约束：根目录 `AGENTS.md` 已记录根目录 `debug.keystore` 是 H730 平台/正式签名文件、alias、证书指纹和口令保密要求；测试包覆盖安装前也必须执行正式证书校验。

## 2026-09-16 - 修复普通键盘中文候选文字偶发不可见

- 现场证据确认候选引擎正常：异常时输入 `nihao` 后候选栏肉眼为空，但空格仍会提交“你好”；详细日志同时包含 `CandidateListEvent(total=261, candidates=[你好, ...])`。问题属于候选文字渲染，不是拼音词库或 Fcitx 候选生成失败。
- 根因：全局键盘为深色水族背景向候选适配器设置白色文字覆盖色；输入视图和候选组件可独立重建，但 `KeyboardWindow` 与 `InputView` 都把“模式值未变化”当成完整 no-op，导致新一代普通浅色候选栏在部分生命周期路径没有重新收到清除覆盖色的指令，白字残留后视觉上像空栏。
- 修复：新增 `DesktopKeyboardModeState`，将逻辑模式变化与视图样式同步分离；每次键盘窗口附着或布局通知都重发当前模式，`InputView` 即使收到重复的普通模式也强制清除候选颜色覆盖，其他桌面布局变化仍只在实际模式切换时执行。
- 自动测试：新增 `DesktopKeyboardModeStateTest`，修复前因状态同步器不存在而失败；修复后两项通过，明确覆盖“重复 normal 仍重新应用 normal 候选样式”。
- 构建与签名：WSL 原生 Debug 构建成功；平台签名测试包 `bin/KBoard-1.4.2-candidate-color-fix-arm64-v8a-platform-signed-test.apk`，SHA-256 `73973DB665CD60E047E6CA044B8E080E9DDD07DB3E8D4C92387279959C451C27`，v1/v2/v3 验签通过，证书 SHA-256 保持 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 真机验证：覆盖安装到 `172.21.16.24` 返回 Success，默认主 IME 未改变。普通中文模式真实触摸输入 `nihao` 可见“你好”等候选；随后进入全局键盘、退出回普通键盘并再次输入 `nihao`，候选文字仍以深色正常显示，未复现白字残留。未执行 `pm clear`，未触发语音。

## 2026-09-16 - 商业版设置首页与密码保护的完整工程入口

- 设置首页收敛为输入语言、键盘设置、外观与主题、隐私、关于 KBoard 五个用户入口；上游工程配置不再直接暴露给普通用户。
- “关于 KBoard”的版本项连续点击 7 次后弹出工程密码输入框，固定密码 `2580`；验证成功后进入完整工程设置，解锁状态仅在当前应用进程内保留。
- 工程设置恢复全部原入口：全局选项、输入法、附加组件、完整主题、虚拟键盘、候选窗口、剪贴板、符号、插件、高级和开发者选项。
- 新增 `EngineeringAccessGateTest`，覆盖第 7 次点击、错误密码拒绝及正确密码解锁；复用既有 WSL 原生构建环境执行定向单测与 `:app:assembleDebug`，联合构建通过（203 tasks）。Windows SDK 下同名 CMake 目录仅有元数据，不能用于 native 打包；完整环境仍位于 WSL `/opt/android-sdk`。产物为 `bin/KBoard-1.4.2-commercial-ui-engineering-unlock-arm64-v8a-debug.apk`，SHA-256 `D0A71B689BD8EFEBD468FA2342DD54E7B20B2395BA8C3C2F6565F87447F63DFB`；尚未做真机 UI 验证。
- 平台签名测试包覆盖安装到 `172.21.16.24` 后完成基础回归：商业首页、7 次点击、密码 `2580`、完整工程入口、英文触摸输入、退格、显示/隐藏和进程稳定性通过；无目标 FATAL/ANR。首轮拼音 `nihao` 可形成 `ni hao` 预编辑但候选栏肉眼为空；后续已确认是全局键盘白色候选覆盖色在普通浅色候选栏残留，并由同日候选颜色同步修复完成真机回归，详情见 `test-reports/commercial-ui-basic-20260916/REPORT.md`。

## 2026-09-16 - 旧 Session 修复包 20 轮定向压力回归

- 在 16.24 执行便签输入/退格、隐藏、设置/浏览器切换、同包中继/主 IME 往返重建，共20轮；40次键盘显示检查通过，36次服务释放，PID始终10501，两类目标异常、KBoard FATAL/ANR均0，默认输入法恢复不变。
- 新增脚本 `fcitx5-android/scripts/test-late-session-20.py`；报告及日志、逐轮dumpsys和截图见 `test-reports/late-session-20-20260916/REPORT.md`。
- 本轮定向回归通过。远程应用停留启动页，未覆盖远程设备号输入路径；未强制内存回收，未做五小时Monkey，未宣称穷尽所有迟到回调竞态。

## 2026-09-16 - 防护旧 IME Session 的迟到光标回调

- 根因：客户 E4_kboard.log 的 20:14:42 FATAL 经 `onUpdateCursorAnchorInfo → updateDecorLocation → getContentView` 触发；销毁后的窗口引用为空，`checkNotNull` 抛出 `Required value was null`。此路径独立于已修复的 KeyboardWindow 排队任务。
- 修改：`FcitxInputMethodService.kt` 在光标回调入口拒绝已释放或缺失窗口引用的状态；位置计算直接捕获可空窗口引用，不可用时返回 false。正常存活窗口的候选定位计算保持原有逻辑。
- 测试：新增 `LateCursorCallbackTest.kt`，通过 JVM 无构造实例调用真实方法验证迟到回调；修复前三项失败，其中位置计算抛出对应 IllegalStateException，修复后三项通过；原 KeyboardWindowTaskGate 两项亦通过。此 JVM 测试不模拟 Android framework 完整生命周期或正常 View 测量。
- 构建：复用 WSL 构建副本，核对应用 Kotlin 源码和 Manifest 与本地一致（忽略 CRLF）；定向单测与 assembleDebug 联合通过，203 tasks。平台签名证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`，v1/v2/v3 验签通过。
- 产物：`bin/KBoard-1.4.2-162-late-ime-session-20260916-platform-signed-test.apk`，包名 `com.newlink.kemi.kboard`，arm64-v8a；SHA-256 `B36B9330950B6A9B580E7A99ED3AA1E127614787AB115187A2D457496CA6D5C9`。
- 部署：`172.21.16.24:5555` 初次拒绝连接，重试恢复后 install -r 返回 Success；安装版本 1.4.2/162、UID 1000、lastUpdateTime=2026-09-16 10:09:24，默认主 IME 不变，进程 PID 10501。
- 真机限制：尝试便签搜索框唤起时，设备存在外部 Monkey 单事件启动 KEMI 远程的操作，界面持续变化且 UIAutomator 无法取得 idle；未中断外部操作，未判定键盘交互通过。近期日志未检出 FATAL、DisconnectedException、Required value was null 或 KBoard ANR；完整五小时长测尚未执行。

## 2026-09-16 - 全局键盘增加上下屏切换并恢复主屏系统隐藏键

- 全局键盘操作栏：在原有“退出全局模式、语音、回车”之间加入上下屏切换按钮，底部保持单行四个等宽大按钮，按钮高度、主键区高度、水族背景和鼠标区均未改变。新按钮复用普通键盘已有的 `ScreenSwitchAction`，继续走同一套 D0/D2 路由、中继判断、Insets 刷新和失败处理，不另建第二套跨屏逻辑。
- 主屏隐藏根因：V900 Android 12 主屏导航栏左下角隐藏箭头会向当前输入法发送 `KEYCODE_BACK`；KBoard 过去先把该事件交给 Fcitx/编辑器，全屏桌面键盘下框架默认处理又不会可靠关闭 IME，导致箭头可见但点击无效。
- 主屏隐藏修复：`FcitxInputMethodService` 在首次 BACK 按下时明确执行 `requestHideSelf(0)`，并消费对应按下/释放事件，避免事件继续进入远端编辑器。全局键盘内的远程 BACK 仍通过 `sendDesktopSystemKeyState()` 独立发送给远端，不会被本地隐藏逻辑截获。
- 修改文件：`InputView.kt`（四按钮单行布局和屏幕切换按钮）、`DesktopKeyboard.kt` / `KeyboardWindow.kt`（复用屏幕切换动作）、`FcitxInputMethodService.kt`（主屏系统隐藏 BACK 路由）。
- 63 号真机验证：正式版覆盖安装且默认输入法配置保留；普通键盘和全局键盘分别在 Display 0 点击系统左下角隐藏箭头，均从 `mShowRequested=true / mInputShown=true` 变为 `false / false`，截图确认键盘完全收起。全局新按钮可产生 `current=0 target=2` 的跨屏请求；同代码在 Display 2 实测可将 IME token 从 D2 切回 D0。全程按 3 秒操作间隔执行，未发现 FATAL、ANR 或 `WindowLeaked`。
- 构建与身份：`HardwareKeyAnomalyFilterTest`、`DesktopKeyPolicyTest` 通过；正式 Release 构建成功，包名 `com.newlink.kemi.kboard`、版本 `1.4.1`（versionCode 152）、`android.uid.system`，v1/v2 验签通过，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`，APK SHA-256 为 `5ee04329829cce42d26eacbd4c538466204292fd787879e2b56af9163a9f9845`。

## 2026-09-16 - 全局摸鱼键盘可见态 CPU 降载与有效按键音恢复

- 现场与根因：75 号 V900（Android 12、Display 2、1920×1280）全局键盘稳定显示 10 条鱼、29.9Hz 时，10 组 3 秒间隔的 `/proc` 采样平均占用 `60.282%` 单核（`57.911%`–`63.057%`），PSS `108773KB`。`simpleperf` 对同型号现场的前置分析显示主要热点在 `RenderThread`、`CanvasContext::draw`、Skia OpenGL 绘制和圆角按键绘制；水族线程只占较小部分。根因是水族 `TextureView` 每帧更新时，六行完全静态的圆角按键也被反复记录和栅格化。
- CPU 修复：桌面模式附着时把六行静态按键缓存为硬件合成层，按下、组合键提示等真实变化仍会使对应行失效重绘；退出桌面模式或销毁时立即释放这些层，避免隐藏后保留 GPU 内存。水族目标刷新率从 30Hz 调整为 24Hz，并同步调整自适应鱼群数量阈值；鱼的路线、速度和动作仍按时间步进，不因刷新率降低而变慢。
- 按键音修复：全局键盘不再在原始 `ACTION_DOWN` 时播放水滴音，只在未取消且确实分发了按键动作后播放一次。兼容部分 Android/OEM 包装层 `performClick()` 返回值不可靠的情况，改为在分发前确认存在点击监听器；取消滑动、越界、空白触摸、语音按钮和被误触过滤器丢弃的硬件事件保持无声。水滴样本预加载和首次成功播放各保留一条一次性日志，播放音量从 `0.30` 微调为 `0.38`，不增加逐键日志开销。
- 修改文件：`BaseKeyboard.kt`（按键行合成层生命周期）、`DesktopKeyboard.kt`（桌面模式缓存和有效动作音频回调）、`DesktopAquariumView.kt`（24Hz 与自适应阈值）、`CustomGestureView.kt`（按手势最多一次的有效动作反馈）、`InputFeedbacks.kt`（SoundPool 就绪/播放自检与水滴音量）。
- 75 号真机结果：相同页面、相同副屏和 10 条鱼下，候选版稳定 `23.9Hz`，10 组 3 秒间隔采样平均 `22.010%` 单核（`21.184%`–`22.713%`），较同机旧版降低 `63.49%`，超过“至少降低 30%”目标；PSS 为 `103564KB`，未以明显内存增长换取降载。切回普通键盘后连续 5 组采样均为 `0.000%`，`kboard-aquarium` 线程已退出。
- 行为验证：真实字母键成功写入编辑框，并出现 `Aquarium key sound playback verified`；重启进程后先执行取消滑动，仅出现样本就绪日志、没有播放日志，随后有效按键才出现一次播放确认。普通/全局模式按 3 秒间隔往返 5 轮，PID 保持不变，无 FATAL、ANR 或 `WindowLeaked`，普通键盘布局与输入未改变。
- 构建与身份：JVM 回归 `HardwareKeyAnomalyFilterTest`、`DesktopKeyPolicyTest` 通过；最终正式 Release 保持包名 `com.newlink.kemi.kboard`、版本 `1.4.1`（versionCode 152）、`android.uid.system`、v1/v2 验签和平台证书 SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`，APK SHA-256 为 `e136539609e3b7fff49997ccb548680f43007e081c29fd9eb2909ce721536468`。75 号仍安装历史普通 UID 包，按既有迁移约束未卸载或清数据；真机性能验证使用同源码、同 Release 签名但不声明 shared UID 的兼容测试产物，正式产物不改变 system UID 发布规则。
## 2026-09-15 - 修复双屏 KEMI 重开时旧布局任务访问已断开的 Fcitx

- 现场：客户使用 `KBoard-1.4.1-152-d7bea470-arm64-v8a-systemuid-platform-signed-release.apk`，在副屏反复打开/退出 KEMI 远程、设备号输入数字和退格、收起键盘并切换主屏应用后，KBoard 偶发弹出崩溃日志。
- 根因：`KeyboardWindow.switchLayout()` 提交到主线程的布局任务没有绑定到所属 `InputView` 生命周期；旧 IME Service 被替换并断开 Fcitx 后，排队任务仍进入 `attachLayout()`，由 `runImmediately()` 抛出未捕获的 `FcitxDaemon.DisconnectedException`。
- 修复：新增 `KeyboardWindowTaskGate`；布局任务捕获所属 generation，`KeyboardWindow.dispose()` 先 retire 门控，旧 generation 的任务执行前直接丢弃。没有吞掉存活实例的连接异常，也没有修改键盘布局、输入逻辑或 Fcitx native 行为。
- 部署兼容：恢复主 Manifest 的 `android:sharedUserId="android.uid.system"`；测试包使用项目根目录 `debug.keystore` 平台证书签名，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 自动验证：新增 JVM 回归测试，修复前因门控缺失失败，修复后两项通过；`:app:testDebugUnitTest` 定向测试与 `:app:assembleDebug` 联合构建通过（203 tasks）。
- 真机：修复包 `1.4.2`（versionCode 162）无损覆盖到 `172.21.16.24`，UID 1000、默认 IME 保持不变。按客户路径完成最终100轮压力测试；期间268次 Service release、68次 InputView重建、36条 invalid-token，KBoard PID始终为11550，FATAL/DisconnectedException/ANR/进程死亡均为0。
- 证据：`test-reports/kboard-crash-regression-20260915-fix-100-final/` 保存完整 logcat、设备包基线和 JSON 汇总；首次脚本运行因 Windows GBK 解码停止，不计入最终结果，修正为 UTF-8 后重新完整执行。
- 风险：自动脚本以固定坐标操作当前 KEMI 1.4.122 页面；其中73次日志明确记录代理 IME 接受显示请求，快速循环时部分请求在采样前被后续隐藏/切页覆盖。最终结论覆盖本机高频竞态回归，不替代客户设备、客户系统镜像上的长时间验收。
## 2026-09-13 - 修复退出全局键盘后后台 CPU 持续占用

- 现场：75 号 V900（Android 12、Display 2、1920×1280）上进入全局键盘后再隐藏，水族渲染线程虽然已经退出，KBoard 仍持续占用约 16%–22% 的单核 CPU；从普通键盘直接隐藏仅约 0.04%。
- 定位过程：先做 A/B 对照，确认只有“进入全局键盘再隐藏”能够稳定复现；第一轮根据透明区域遍历栈尝试仅隐藏 `TextureView`，30 秒平均 CPU 仍为 `19.542%`，门禁明确判定失败并撤回该方案。随后用 `simpleperf` 采集 10 秒、9400 个样本，确认主热点依次为 `View.transformFromViewToWindowSpace`、`View.gatherTransparentRegion`、`ConstraintLayout.onMeasure`，并定位到 `InputView` 的布局回调，而不是已经停止的 `kboard-aquarium` 线程。
- 根因：`InputView.updateDesktopCompositionPosition()` 由布局监听器触发，却无条件重新写入候选栏 `topMargin`。这会形成“布局完成 → 回调 → 再次请求布局”的循环；IME 隐藏后桌面模式状态仍保留，循环因此继续在不可见窗口中运行。真机 `simpleperf` 热点集中在 `InputView` 回调、`ConstraintLayout.onMeasure`、`View.transformFromViewToWindowSpace` 和 `gatherTransparentRegion`。
- 修复：新增 IME 窗口可见状态门控，窗口隐藏后不再提交桌面候选栏定位任务；定位时只在 `topMargin` 或工具栏位移确实变化时才更新，切断自触发的重复布局。水族渲染、按键布局、鼠标协议、语音和普通键盘均未改变。
- 回归：新增 `fcitx5-android/scripts/test-global-idle-cpu.py`，要求全局键盘可见且水族线程已启动，发送 HOME 隐藏后验证窗口不可见、水族线程退出、进程不重启，并按 3 秒间隔读取 `/proc` 计算 CPU；门禁为 30/60 秒平均单核 CPU `<5%`。
- Release 验证：正式签名 Release 构建通过（287 tasks），包名 `com.newlink.kemi.kboard`、版本 `1.4.1`（versionCode 152）、APK SHA-256 `6164490bd7e5373f222427d9420ad09712e597ea9f42d66dc3d30ae533c480be`；v1/v2 验签通过，证书 SHA-256 保持 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 真机结果：75 号机覆盖安装成功且仍为默认输入法；Display 2 普通/全局模式往返后，30 秒平均 CPU `0.649%`，追加 10 轮模式往返后 60 秒平均 `0.308%`，除隐藏收尾的首个采样外其余采样均为 `0%`；PID 保持 `16518`，无新 FATAL、ANR 或异常退出。
- 风险：首个隐藏后 3 秒窗口仍可能包含一次约 6% 的正常收尾采样；后续采样归零。本回归脚本依赖测试设备 root 读取 `/proc`，不属于应用运行时依赖。

## 2026-09-14 - 单次打开键盘闪现联合诊断

- 留存远控交接文档并校验 SHA256；只读核对两轮日志与 KBoard/远控源码，现象为一次点击后出现、消失、自动再次出现。
- 确认服务销毁重建早于远控 fallback restartInput，不能将其认定为最初触发源或把 framework_destroy 当崩溃。hidden 状态首次源屏按下不进入既有焦点保护，是待验证候选窗口；尚不能确认 IMMS/窗口策略责任。
- 分析、置信度、证据限制、最小验证及风险见 diagnostics/keyboard-flash-20260914/analysis.md；105 ms 跨屏绑定来自未落盘的交接观察，不宣称本轮独立验证。
- 本轮未操作设备或远程会话，未改业务代码、编译安装、发布或改系统配置；仅新增诊断材料和本日志条目。


## 2026-09-13 - 后台 CPU 任务生命周期审查

- 本地源码发现：ASR read<=0 缺少退出处理，持续即时错误可形成忙循环；键盘隐藏只向桌面鱼缸转发停止，未取消语音录音和延迟任务。视图保留且漏发触摸取消时存在后台任务残留路径。
- 长按连发仅检查协程活跃和 enabled，隐藏无统一取消；回调持续超过 50 ms 时缺少挂起点。正常手势和 detach 已有取消，异常条件尚未实机复现。
- 已审查鱼缸、触摸板、音效、商城检查和原生事件循环，未找到同等明确的持续空转证据；.24 当次隐藏复核为 0%，无 ASR/鱼缸线程。
- 证据与建议：diagnostics/cpu-20260913/logic-review.md。本轮仅源码审查和设备只读复核，未修代码或安装；本地源码未与安装的 1.4.1 APK 逐项比对，不能当作现场根因已确认。


## 2026-09-13 - .24 设备 KBoard CPU 现场诊断

- 设备：172.21.16.24:5555，实际安装 1.4.1 / versionCode 152，包名 com.newlink.kemi.kboard，PID 6000，system UID。
- 验证：top 连续采样隐藏、显示空闲、24 轮字母/退格及收起后状态。隐藏 0–0.4%；显示空闲约 60 秒均 0%；快速输入完整 2 秒采样峰值 85%，随后 6.5% 并持续回到 0%；收起后再次回到 0%。单核口径 100%，设备整机上限 800%。
- 结论：本次未复现持续高 CPU；短时负载与实际输入关联，未采调用栈，不能确定具体热点函数。未修改源码或安装 APK；测试对话框取消未保存，恢复原前台应用。
- 证据：diagnostics/cpu-20260913/report.md 及原始 top、设备信息、截图和日志。hidden-top.txt 尾部包含打开设置的操作，报告已明确区分。
- 风险：未覆盖语音、动态主题、桌面模式、副屏及长时运行，不据此排除其它场景问题。


## 2026-09-12 - 按版本建立发布归档并新增商城自动检查

- 修改：新增 `release/README.md` 台账和 `release/1.4.2/` 的 build/tests/approval/market/h730 分类，最终源码 commit、APK 哈希与发布审批尚待实际产生，不预填发布成功。
- 功能：新增 `MarketUpdatePolicy.kt`、`MarketUpdateController.kt`，在 MainActivity 前台异步检查真实 APK versionCode；高版本提示后跳转指定 KEMI 商城详情，由用户手动升级。增加中英文资源和 12 项策略测试，不提供手动检查入口，不下载或安装 APK。
- 约束：固定 HTTPS 接口、包名与商城协议；失败静默并有界退避，忽略同版本后不重复提示；不在输入法服务抢焦点。用户仅使用键盘而未打开设置 APP 时，当前接入点不会触发检查。
- 验证：Release/Debug Kotlin 编译通过；全量 JVM 47 项中 46 通过、1 项旧主题迁移测试失败，新增 12 项全部通过。主题格式已为 2.1，但旧 2.0 测试仍要求无迁移；保留失败，未修改主题行为或跳过测试。
- 证据：`release/1.4.2/tests/source-validation/` 保存 XML、HTML 和结果说明，build 下保留成功/失败日志。公开更新接口与 .24 设备仅做只读预检。
- 未完成：最终源码定版、正式候选 APK、真实主动提示/商城跳转及升级验收；本次没有部署、发布、服务器替换或提交推送，记录仍按用户要求保留本地。

## 2026-09-12 - 确认测试授权与正式候选验收基线

- 主题：用户确认 .24 覆盖安装及压力测试规则；默认不清数据、不卸载、不恢复出厂、不刷机，必要时逐次说明影响并申请授权。
- 修改：更新 `docs/kboard-release-workflow.md`，将 300 次自动生命周期、20 次模式往返、30 次跨屏往返、底行每键 20 次和功能完整一轮设为每个正式候选包基线。
- 分工：干净系统镜像上的首次启动、麦克风默认授权及默认双屏能力由 H730 系统团队验证；缺少与候选 APK 对应的报告不得通过正式发布门禁。
- 验证：核对现有测试脚本仅覆盖安装且无自动卸载/清数据回退；现有验收门禁要求 fresh_image_permissions 为 PASS 并提供证据链接。本次仅落实规则，不代表测试已经执行或通过。
- 待办：收集系统团队报告联系人、证据存放位置及设备 fingerprint；本次不提交或推送仓库。

## 2026-09-12 - 测试与正式版本统一使用 system UID

- 主题：用户确认将 android.uid.system 从实验配置转为后续测试版和正式版的统一身份。
- 修改：在 app/src/main/AndroidManifest.xml 的共享主清单中固定 android:sharedUserId="android.uid.system"，所有构建变体继承；不调整包名或源码目录。
- 签名：交付到 H730 的测试包与正式包均须使用对应平台证书；普通 Android Debug 证书不能替代平台签名。
- 验证：主清单 XML 与变体覆盖检查；本次不重新编译或部署，不声称已完成设备升级验证。
- 风险：历史普通 UID 包不能假定可无损覆盖为 system UID；迁移需单独验证，不自动卸载或清数据。麦克风默认授权仍须独立验收。

## 2026-09-12 - 更新检查交互最终澄清

- 主题：用户明确只需启动后自动检查并主动提示，取消手动检查入口和独立后台弹窗开关。
- 过程：核对原接入标准中的异步检查、版本比较、静默失败与同版本去重策略；普通提示不以 force_update 为前提。
- 修改：同步项目商城发布 skill、Android 接口参考和发布工作流验收项；保留点击提示跳转商城、由用户手动升级，不增加键盘下载/安装路径。
- 验证：skill 结构校验与文档差异检查；本轮没有实现或编译 Android 功能，没有设备部署、商城发布或提交推送。
- 待办：后续实现自动检查、生命周期安全提示及商城跳转，并完成真机验证。前一条记录中的手动检查需求及独立弹窗字段阻断项由本条取代。

## 2026-09-12 - Android 商城发布 skill 提取与升级职责确认

- 主题：从用户提供的商城发布 skill 提取 Android 部分，排除其它平台，原文件不修改。
- 过程：使用 skill-creator 整理项目专用 skill；只读核对官方 Android 自检文档，确认商城包名 com.newlink.featuredapps 及 kemiappstore://app/{app_id} 详情链接。
- 修改：新增 `.agents/skills/kboard-market-publish/`；更新发布工作流文档，记录后台控制主动提示、APP 内手动检查均跳转商城，由用户手动升级；不采用键盘下载或安装的回退路径。
- 验证：skill 结构校验通过（Windows Python 使用 UTF-8 模式）；本轮未修改 Android 源码、安装设备或发布商城。
- 待办：官方文档未给出独立主动弹窗字段，需核实后台配置/源码，不能将 force_update 混用为弹窗开关；确认后再实现客户端及真机验证。

## 2026-09-12 - 正式发布工作流基础与 1.4.2 版本准备

- 用户确认使用当前电脑 WSL 环境、172.21.16.24 专用测试机，审批人为 wangruiqing995-blip，交付目标为商城和 H730 系统预置；版本改为 1.4.2，arm64 versionCode 为 162。
- 新增 `scripts/release/` 构建入口、APK/JUnit/压力测试/人工验收门禁及本地测试、导出脚本；发布使用同一系统签名 Release APK，证据绑定 commit、run_id 和 SHA256。缺失/失败/跳过测试不算通过。
- 新增门禁脚本 CI 自测，原根目录 Debug CI 加入 PR 触发、真实 JVM 执行及结果校验；不会向 GitHub 提供平台私钥或内网设备访问。
- `docs/kboard-release-workflow.md` 记录执行命令、验收矩阵、双渠道交付约定和待确认资料；商城 API、系统构建/刷机规则、UID 决策及可认证审批尚待接通。
- 验证：门禁正反例自测、bash 语法检查，以及已有 1.4.1 系统签名 Release APK 的实际元数据/签名校验；不代表 1.4.2 已构建或通过真机验收。未安装 PAD、未上传商城、未替换服务器 APK、未提交推送仓库。
- 保留本地已有 system UID 实验 Manifest 及其它未提交改动；正式 UID 方案必须确认后才能放行构建。JVM 执行器既有问题和实际 lint/设备适配仍需首轮运行验证。

## 2026-09-12 - system UID 实验测试包

- 按用户指定的验证方案，在应用 Manifest 根节点增加 `android:sharedUserId="android.uid.system"`。
- 此改动仅用于生成独立测试 APK，验证 H730 系统 UID 安装行为；它不替代 `default-permissions` 的麦克风运行时权限预授权。
- 由于已有同包应用采用普通 UID，测试包不能假定可通过 `adb install -r` 原位升级；未获得明确授权前不卸载设备现有正式包。

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
- 首版 `64b1471b` 直接首挂全局键盘后，63 日志暴露更早的构造时序：`KeyboardWindow.onAttached()` 在 `InputView` 构造中调用 `setDesktopKeyboardMode()`，但 `keyboardView` 尚未初始化，17:14:27 主线程以 `NullPointerException` 崩溃。该问题由本次首帧顺序变更直接触发。

### 修改
- `KeyboardWindow.onCreateView()` 首次挂载布局时直接读取 `desktopModeRequested`：已选全局模式就创建 `DesktopKeyboard`，否则保持原 `TextKeyboard`。
- `InputView.setDesktopKeyboardMode()` 在内部 `keyboardView` 尚未初始化时只记录最后一次待应用模式；根视图、约束和操作栏全部创建完成后立即一次性应用。这样既不绘制普通键盘中间帧，也不在半构造对象上修改桌面样式。
- 后续 `onStartInput()` 的输入类型/全局模式选择、用户主动切换和进程重启后回普通键盘的原策略不变。

### 验证
- `64b1471b` 构建与签名虽通过，但 63 真实重建 InputView 时按上述 NPE 崩溃，已判定为失败版本，不得继续发布或归档。
- 修复提交 `6c5be9c4` 的 Release Kotlin 编译及完整 Release 构建通过；正式 APK 为 `versionName=6c5be9c4`、`versionCode=122`，SHA-256 为 `044f9ef21f98f11853f8c73b96d97bbddc9430143eb95e3730102862fd08bb53`。
- `apksigner` 验证 v1/v2 签名和既定正式证书通过，APK 内 4 个水滴 WAV 均存在；63 `install -r` 返回 `Success`，设备回读版本为 `6c5be9c4/122`，默认输入法仍为主 KBoard。
- 安装后清空旧 logcat，并由 `com.newlinksz.kemi.remote` 真实重新拉起 KBoard：系统状态为 `mInputShown=true`，修复版进程持续存活；多次 InputMethod View/水族 Surface 重建及连续触摸后未再出现 `InputView.setDesktopKeyboardMode()` NPE、`FATAL EXCEPTION` 或进程重启。
- 水族运行日志为 10 条鱼、`1080×425`、`29.9–30.1 FPS`；每次 DOWN 均创建 AudioTrack 并记录 `touchFeed`，证明水滴声音调用链和鱼群触摸链均保留。是否仍有肉眼可见的普通键盘中间帧以现场观感复核为准。

### 待办
- 本修复只消除 KBoard 自己创建的普通键盘中间帧。如果日志显示远程桌面连续创建两个不同 EditorInfo/输入会话，仍需分别记录 `onStartInputView` 次数，但不能再通过固定首挂普通键盘放大闪烁。
- KBoard 进程被系统完全杀死后仍按既有安全策略回到普通键盘；本次只保证同一进程内用户明确选择的全局模式跨 InputView 重建保持。

---

## V1.51 - 2026-08-25

### 主题
修复 Android 12 双屏远程桌面反复显示全局键盘时的 View 泄漏和 OOM，发布 KBoard 0.1.6 / arm64 versionCode 142 正式版。

### 过程
- 63 在副屏远程桌面连接后反复点击底部键盘按钮，旧版本约 1 分钟可残留 2 万多个 View；原始 100 次压力测试在约第 57 轮达到 384 MiB Java heap 上限并触发 `OutOfMemoryError`，崩溃栈最终落在被重复保留的 ConstraintLayout/View 创建路径。
- 对 Java heap 执行标准 HPROF 转换和 Eclipse MAT 引用链分析，确认第一条应用侧强保留链为进程级 `ConnectivityManager` 回调表 -> `KawaiiBarComponent.voiceNetworkCallback` -> `KawaiiBarComponent` -> 完整 `InputView`。每个未注销回调会保留一棵完整键盘界面，原始 heap 中一个旧实例约包含 211 个 ConstraintLayout/View 对象。
- 注销应用侧回调后，完整键盘树已不再重复，但 Android 12 双屏固件仍会在每次远程键盘显示时销毁并重建 `FcitxInputMethodService`，且系统以 JNI Global 保留旧 `IInputMethodSessionWrapper` -> `InputMethodSessionImpl` -> Service。系统 `InputMethodService.onDestroy()` 只关闭窗口，没有清空 `mRootView`、`mInputFrame`、`mCandidatesFrame`、`mWindow` 等直接引用，因此仍会留下小型框架 View 树。
- 该行为在相同远程桌面路径上使用 Gboard 对照时不产生完整键盘树累积；KBoard 必须在应用可控边界主动把旧 Service 收缩成不携带 UI 的轻量外壳，不能等待该固件回收 Session。

### 修改
- `KawaiiBarComponent` 改用 application Context 获取 `ConnectivityManager`，记录网络回调注册状态，并新增幂等 `dispose()`：注销语音网络回调、剪贴板监听和三组偏好监听，取消剪贴板/语音任务，清空语音按钮的触摸与手势引用。
- `InputView.dispose()` 改为幂等完整释放：停止事件处理与水族渲染、注销组件、清空 Scope/View 容器，并显式调用 `KawaiiBarComponent.dispose()`；`InputDeviceManager.clearViews()` 同步解除输入区和候选区引用。
- `FcitxInputMethodService` 增加进程内弱引用所有者。Android 12 双屏固件先创建新 Service、后销毁旧 Service 时，新实例会先使旧实例执行一次 `releaseOwnedResources("superseded")`，避免短暂重叠窗口继续持有完整键盘。
- Service 释放路径统一处理延迟隐藏请求、桌面修饰键、InputView/CandidatesView、偏好与主题监听、协程任务、Fcitx daemon 连接和宿主 FrameLayout；`onDestroy()` 后清空自身 content/decor 引用。
- 仅在 Android 12 且 `super.onDestroy()` 完成之后，以字段类型清除 `InputMethodService` 的 10 个私有 View 引用，并清除已失效的私有 `mWindow`。每轮设备日志均记录 `cleared Android 12 framework View fields=10 mWindow=true`；其他 Android 版本不执行该反射兼容分支。
- 输入法窗口隐藏时立即停掉水族 GLES 渲染线程和触摸命令队列，并保留稳定过渡帧；窗口重新显示时再激活，避免固件保留 `SurfaceTexture` 时后台持续离屏渲染。
- 版本基础码由 13 增加为 14，基础版本名由 0.1.5 增加为 0.1.6；按 ABI 编码规则 arm64-v8a 正式 `versionCode=142`。

### 验证
- 修复预检：63 从基线 812 Views / 2 ViewRoot 连续执行 10 个“隐藏 -> 显示”循环并强制 GC，结果仍为 812 Views / 2 ViewRoot；旧 Service 每轮均成功清除 10 个框架 View 字段和 `mWindow`。
- 完整闭环：在 `192.168.3.63:5555` 的 Display 2 远程桌面工具栏坐标 `(240,1155)` 连续执行 100 个完整隐藏/显示循环，共 200 次按钮点击。PID 始终为 `30104`，第 20、30、40、60、100 轮稳定采样均为 812 Views / 2 ViewRoot；事务尚未落稳的个别瞬时采样最高为 836 Views / 5 ViewRoot，等待后全部回落。
- 第 100 轮完成并执行 heap GC 后仍为 812 Views / 2 ViewRoot，View 净增量为 0；PSS 采样约 99–140 MiB，未出现原来的线性上升、384 MiB OOM、进程重启或界面崩溃。
- crash buffer 为 0 行，`FATAL EXCEPTION`、`OutOfMemoryError` 和目标进程 AndroidRuntime 异常均为 0；最终默认输入法和窗口状态仍为 KBoard、`mInputShown=true`。
- 正式 `./scripts/assemble-release-local.sh` 完整成功，只生成 Release。APK 包名 `org.fcitx.fcitx5.android`、`versionCode=142`、`versionName=398998f5`、minSdk 23、targetSdk 36、ABI 仅 `arm64-v8a`，大小 46,193,191 字节。
- `apksigner` 确认 v1/v2 签名有效，证书 SHA-256 保持 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`；正式 APK SHA-256 为 `6179c85d97857e04ccfdb6518066d23699bf6856b6b2f9a3c3199d2136b662ff`。
- 发布物为 `bin/KEMI-0.1.6-142-398998f5-arm64-v8a-release.apk`，对应校验文件为 `bin/KEMI-0.1.6-142-398998f5-SHA256SUMS.txt`；`bin` 副本与构建产物逐字节一致。

### 待办
- 该 Android 12 双屏固件仍由系统 JNI Global 保留已经销毁的输入法 Session，因此 `AppContexts` 会随固件反复重建 Service 增加；110 轮期间从 6 增至 172。应用无权释放系统持有的 Binder/JNI Session，本修复把这些对象收缩为不再携带 View、窗口、监听器、协程和 GLES 线程的轻量外壳。后续固件若可修改，应在输入法切换/代理销毁时正确执行 Session `finishSession()` 并释放 JNI Global。
- Android 12 专项反射依赖框架私有字段；当前目标固件实测 100 轮稳定，后续 ROM 升级必须重新核对字段和迟到回调。反射失败时会记录警告并跳过对应字段，不应扩展到其他 Android 版本。
- 本轮已在 63 完成 100 轮压力测试和正式 Release 构建；发布归档不再次覆盖安装，避免在用户验收中的当前设备状态上进行额外写入。

---

## V1.52 - 2026-08-25

### 主题
修正 Android 12 迟到输入法回调导致的二次崩溃，收口 Fcitx、候选、键盘、水族和 ASR 的代际所有权，并在 63 完成 100 次真实可见切换压力测试。

### 过程
- 复盘 V1.51 的私有字段清理方案后确认：V900 Android 12 双屏固件会在 `InputMethodService.onDestroy()` 之后继续通过旧 `IInputMethodSessionWrapper` 调用 `updateFullscreenMode()` 等框架方法。把 `mRootView`、`mInputFrame`、`mCandidatesFrame` 或 `mWindow` 反射置空虽然能缩小旧 Service，却破坏了这些合法迟到回调的框架前置条件，造成包名仍指向 KBoard 的系统崩溃。
- 新方案保留 Android 框架拥有的轻量窗口壳，只释放 KBoard 自己拥有的 InputView、候选、监听器、任务和 GLES 资源；不再修改 `InputMethodService` 私有字段。
- 进一步检查发现旧 Service 排队的 Fcitx 任务、候选分页、状态栏操作、ASR 主线程回调和水族 Shader 创建失败路径都可能跨越 Service/InputView 代际。所有这些路径必须在执行点再次校验所有权，而不能只在入队或创建时校验一次。
- 63 压力测试最初只读取 `dumpsys input_method.mIsInputViewShown`，截图复核发现 Android 12 会在键盘实际已经隐藏时短暂继续报告 `true`。正式计数因此改为同时要求前台窗口是 `ClipboardEditActivity`、served view 是有效 `EditText`、token 位于 Display 0，并用阶段截图验证键盘真实可见；无效样本全部作废，不计入 100 次通过。

### 修改
- `FcitxApplication`：Release 未捕获异常在退出前以 `KBoardCrash` 输出完整线程与堆栈，避免自定义崩溃页调用 `exitProcess()` 后丢失根因。
- `FcitxDaemon`：每个 Service 使用唯一连接名和连接对象身份校验；客户端表改为并发映射，`runImmediately/runOnReady/runIfReady` 在真正执行前再次验证连接，退休代际抛出明确的 `DisconnectedException` 或安全丢弃。
- `FcitxInputMethodService`：Fcitx 作业改由当前 Service 生命周期拥有并保持串行；释放后拒绝新任务，取消缓存键、异常过滤器、对话框和迟到切换输入法回调；删除 Android 12 私有 View/Window 反射清空逻辑，保留能承接系统迟到回调的轻量框架壳。
- 候选、展开候选、状态区和输入法选择器统一通过 `service.postFcitxJob()`，旧 UI 代际不能再直接向共享 native 生命周期投递操作；已断开的候选分页返回 `LoadResult.Invalid()`。
- `InputView/KeyboardWindow/BaseKeyboard/TextKeyboard/CustomGestureView`：增加幂等的永久释放链，注销键盘偏好监听，解除按键监听器，停止长按/重复输入和动画，并清空旧键盘容器。
- `KawaiiBarComponent/IflytekAsrClient`：ASR 使用 application Context；释放时取消录音、鉴权、WebSocket、提交任务和输入预览，恢复物理键盘声音；所有 State/Partial/Final/Error 主线程回调绑定会话 generation，旧会话回调不能更新新界面。
- `DesktopAquariumView`：无论 EGL 初始化、Shader 编译、Program 链接、绘制还是交换缓冲在哪一步失败，都在当前 GL Context 释放已创建的 Shader、Program、Engine 和 Surface，避免快速切换时累积 GPU 资源。
- `DisplaySwitchInputMethodService`：销毁时移除厂商双屏中继 Handler 的全部待执行回调，防止旧中继实例回跳。

### 验证
- 当前正式 Release 为 `fcitx5-android/build/kboard.apk`：包名 `org.fcitx.fcitx5.android`、`versionCode=142`、`versionName=76957053`、大小 46,193,543 字节、SHA-256 `75d468f678c9197a8f1c6fe31be83f8dd2d0b15fa940ed3801512a5e31f9db3f`。
- `apksigner` 验证 v1/v2 签名有效，证书 SHA-256 保持 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`；63 已安装 `base.apk` 与本地正式 Release 的 SHA-256 完全一致。
- 63 真机完成 100 次真实可见切换：默认键盘隐藏/唤起 25 次、默认键盘 Activity 结束/重建 25 次、全局键盘与普通键盘往返 50 次。每轮校验前台窗口、EditText 输入连接、IME token 和可见截图；有效样本 100/100。
- 测试期间 KBoard PID 始终为 `8723`，进程重启 0 次；`FATAL EXCEPTION`、`KBoardCrash`、`OutOfMemoryError` 和目标包 ANR 均为 0。
- 内存和 View/ViewRoot 在 Activity 与全局布局重建时短暂波动，随后回落，不再呈现旧版本每轮线性增长。补测结束为约 139,976 KiB TOTAL PSS、825 Views、4 ViewRoot；测试起点已包含此前多轮运行，数据仅用于确认趋势，不作为冷启动基线。

### 发布物
- 将上述已在 63 验证的正式 APK 归档为 `bin/KEMI-0.1.6-142-76957053-arm64-v8a-release.apk`。
- 校验文件为 `bin/KEMI-0.1.6-142-76957053-SHA256SUMS.txt`；归档 APK、本地构建产物和 63 已安装 APK 三者 SHA-256 一致。
- 推送前远端 `main` 新增 `66b449e2`，把后续源码构建的 `applicationId` 改为 `com.newlink.kemi.kboard`；本次归档 APK 是该提交之前已经在 63 完整验证的 `org.fcitx.fcitx5.android` 包，不能当作新 applicationId 的构建产物。生命周期源码已无冲突地合并到新配置之上，但新包名 APK 必须重新构建、签名和专项安装验证后才能发布。

### 待办与风险
- 额外专项动作发现：全局键盘被 Back 隐藏后，再直接点击同一个已保持焦点的编辑框，偶尔不会真实重新显示键盘，但 Android 12 仍报告 `mIsInputViewShown=true`。该路径的 25 个样本已全部作废，没有混入 100 次通过统计。后续应依据窗口 Insets/实际 IME Surface，而不是单独相信 `mIsInputViewShown`，定位并修复重新请求显示逻辑。
- Android 12 固件仍可能由系统 JNI Global 保留旧 Session；应用不能释放系统 Binder/JNI 对象。当前策略是让旧 Service 只保留框架迟到回调所需的轻量壳，所有 KBoard 自有资源必须按代际释放。
- 本轮压力测试使用项目内 `ClipboardEditActivity` 建立稳定、可重复的输入焦点；远程桌面 D0/D2 的业务级焦点迁移仍需结合 KEMI 客户端单独回归，不能用内部编辑器结果替代跨屏端到端验收。

---

## V1.53 - 2026-08-26

### 主题
修复 Android 12 新 IME 服务在窗口 token 挂载前收到显示请求导致的首次使用崩溃，并建立旧包名正式维护构建路径。

### 现场、根因与既有测试盲区
- 63 于 2026-08-26 10:29:34 的 KBoard 崩溃栈为 `IllegalStateException: Window token is not set yet.`，调用链为 `SoftInputWindow.show()` -> `InputMethodService.showWindow()` -> `InputMethodService$InputMethodImpl.showSoftInput()`；包名和进程均明确属于 `org.fcitx.fcitx5.android`。
- 崩溃前 KEMI `KeyboardProxyActivity` 在约 30–60ms 间隔内连续调用 `restartInput/showSoftInput`。Android 12 固件同时销毁旧 `FcitxInputMethodService`、创建多个新代际，并在新代际完成 `attachToken()` 前投递了显示请求。
- 既有 `onShowInputRequested()` 防护只处理旧 Service 销毁后私有 `SettingsObserver` 为空的 NPE。本次异常发生在该方法正常返回之后的框架 `SoftInputWindow.show()`，因此旧防护不可能捕获。
- 此前 300 次测试主要复用已经完成 token 挂载的服务实例，没有反复覆盖“新 Service + token 未挂载 + 立即显示”的首次时序；循环次数很多不代表覆盖了这个竞态。

### 修改
- `FcitxInputMethodService.onCreateInputMethodInterface()` 的 Android 12 专用 `InputMethodImpl` 增加 `showSoftInput()` 边界保护。只拦截 SDK 31、异常消息精确为 `Window token is not set yet.`，且堆栈同时包含 `SoftInputWindow.show()` 和 `InputMethodService.showWindow()` 的平台异常；其他 Android 版本、其他消息或其他调用栈仍原样抛出。
- 无效请求被拒绝后不销毁服务、不重建 View，也不保存 `ResultReceiver`。系统完成 `attachToken()` 后的下一次请求仍走原框架路径，避免因一次过早回调终止整个输入法进程。
- `Android12ImeFrameworkCompatTest` 增加精确命中、错误 SDK、错误消息、错误窗口类和错误 Service 调用点的正反单测，防止以后扩大为吞掉所有 `IllegalStateException`。
- `app/build.gradle.kts` 保持主线默认 `applicationId=com.newlink.kemi.kboard`，新增显式 `-PkboardApplicationId=...` 维护参数。本次用 `org.fcitx.fcitx5.android` 构建，才能无损覆盖 62/63 仍在使用的旧升级链；默认新包构建行为不变。

### 内存与生命周期边界
- 本修复没有新增 View、Context、协程、监听器、Handler、静态强引用或长期集合；异常对象和 `ResultReceiver` 只存在于单次 Binder 回调栈，返回后不持有。
- 既有 `releaseOwnedResources()` 仍负责幂等释放 InputView、候选、偏好/主题监听、桌面按键状态、ASR/水族资源、协程任务和 Fcitx 连接；本次没有重新引入 Android 12 私有窗口字段反射清空。
- 62 的 100 个有效冷启动样本中，View 为 726–740，PSS 为 91,416–96,433 KiB，没有随轮次线性增长；每轮为新进程/新 Service，主要用于验证首次创建，不替代同进程远程代理的长期泄漏测试。

### 构建与验证
- `Android12ImeFrameworkCompatTest` 定向单测通过；项目没有 Release 单测变体，因此测试使用 JVM `testDebugUnitTest`，未生成或安装 Debug APK。`compileReleaseKotlin` 和完整正式 `assemble-release-local.sh` 均成功。
- 正式 APK 为 `fcitx5-android/build/kboard.apk`：包名 `org.fcitx.fcitx5.android`、`versionCode=142`、`versionName=c7bdcd1e`、arm64-v8a、SHA-256 `5b1bec0e976741320984cce04b3721b025fd849d65158dbc720c98835e753650`。
- `apksigner` 验证 v1/v2 有效，证书 SHA-256 为既有升级证书 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 62 以 3 秒间隔执行 D0 冷启动：每轮 force-stop 后重新创建 KBoard 进程、Service、EditText 输入连接和 IME token。得到 100/100 个有效首次显示样本，`KBoardCrash`、`FATAL EXCEPTION`、`Window token is not set yet`、OOM 均为 0。
- 原第 46 轮在 3 秒采样时未显示。事件日志证明同一时刻外部 DOCX Intent 启动 `org.kemi.koffice` 的 `ExternalDocumentActivity/LOActivity`，KBoard 获得焦点约 1.1 秒后被系统收走 top-resumed；PID 无异常栈。该外部焦点干扰样本作废并补跑 1 轮通过，没有用 99/100 掩盖。
- D2 隔离页虽实际位于 Display 2，但该固件仍把 served input connection 留给 D0 KOffice，`mInputShown=false`，因此这些样本未计入结果，也没有冒充跨屏验收。
- 同一正式 APK 在 62 验证后重新 `install -r` 到 63。63 回读 `c7bdcd1e/142`，默认输入法仍为 KBoard，设备 `base.apk` SHA-256 与本地/62 验证文件逐字节一致；按用户要求未在 63 继续代操作测试。

### 待办与风险
- 62 当时没有已连接的 KEMI 远程会话，因此 100 个有效样本验证的是 Android 12 冷启动首次显示和内存稳定性，不等价于 `KeyboardProxyActivity` 的 D0/D2 端到端路径。用户在 63 的第一次真实远程打开仍是最终验收。
- KEMI 代理在 30–60ms 内密集请求显示会放大固件竞态。KBoard 现在能安全拒绝 token 未就绪的一次请求，但远程端仍应避免无边界重试，并以实际 Insets 可见状态而不是 `showSoftInput(true)` 作为成功依据。
- 如果以后出现相同文字但堆栈不包含 `SoftInputWindow.show()` 与 `InputMethodService.showWindow()`，当前防护会有意继续抛出，必须按新现场重新分析，不能扩大为通用异常吞噬。

---

## V1.54 - 2026-08-27

### 主题
在全局水族键盘顶部增加跨屏虚拟鼠标触控区，并通过当前远程输入连接提供左键、中键、右键。

### 过程
- 先拉取服务器 `origin/main`，确认远端新增 `be7ce351 build: set KBoard version to 1.4.1`；在保留本地 Android 12 首次显示修复的前提下完成合并，合并基线为 `c8fcc553`。
- Android `InputConnection` 没有标准相对鼠标移动接口，因此采用仅面向当前编辑器的 `performPrivateCommand`。KBoard 只向当前输入连接发送相对位移和按钮状态，共享桌面的 `KeyboardProxyActivity` 再按 requestId、sessionId、visible、remote 四重门禁转发给 RustDesk 会话。
- 触控位移在 KBoard 侧按显示帧合并，避免每个 MotionEvent 都跨 Binder/MethodChannel 发送；共享桌面侧保留小数余量并使用 RustDesk 已有 `move_relative` 协议，不增加服务端私有鼠标协议。
- 鼠标按钮采用真实 DOWN/UP 生命周期。允许一个手指按住左/中/右键、另一个手指在触控区移动；重复 DOWN 被去重，隐藏键盘、切换输入、结束输入或销毁服务时统一补发 UP。

### 修改
- 新增 `DesktopTouchpadView.kt`：仅挂载到 `DesktopKeyboard`，绘制半透明磨砂面板、相对滑动区和左/中/右三键；快速滑动带有限加速，单帧位移限制为 ±240，按钮按下显示蓝色/绿色反馈。
- 新增 `RemoteMouseInputProtocol.kt`，固定私有命令 action、move/button 类型、位移字段和按钮白名单。
- `DesktopKeyboard` 把触控板放入全局键盘顶部组合区；`InputView` 仅为全局模式增加 112dp 触控预算并放宽沉浸高度，普通文字、数字、浮动键盘高度路径不变。
- `KeyAction`、`CommonKeyActionListener` 和 `FcitxInputMethodService` 增加鼠标动作转发；Service 保存已被接收端确认的按钮 DOWN，并在所有既有桌面输入释放路径同时释放修饰键和鼠标键。
- 增加中英文资源：触控提示、无障碍描述及左/中/右键名称。
- 共享桌面端同步修改 `KeyboardProxyActivity.kt`、`KeyboardProxyManager.kt`、`server_page.dart` 和 `input_model.dart`，把私有输入命令转换为现有 RustDesk 相对移动及鼠标按键消息；按钮 UP 使用无条件释放通道，避免权限或会话切换造成远端卡键。

### 验证
- KBoard `./gradlew :app:compileReleaseKotlin` 成功，66 个任务完成；没有构建 Debug APK。
- 共享桌面端 Flutter Release 构建参与的 `:app:compileReleaseKotlin` 成功，262 个任务完成；Android 接收层只有项目原有 `SOFT_INPUT_ADJUST_RESIZE` 弃用提示。
- `dart format` 完成；定向 `dart analyze server_page.dart input_model.dart` 无新增 error/warning，只报告原文件已有的 57 条 Flutter RawKey/Material API 弃用 info。
- 两个仓库的 `git diff --check` 均通过。

### 待办与风险
- 该功能必须同时部署本次 KBoard 和 KEMI 共享桌面端代码。只安装 KBoard 会显示触控区，但旧 KEMI 不识别私有命令，远端鼠标不会移动。
- 尚未完成 D0/D2 真机端到端验收。正式验收至少覆盖慢速/快速移动、三键单击、左键按住拖动、修饰键加鼠标、双指“按键+移动”、隐藏键盘/断线时无卡键，以及普通键盘和语音输入不变。
- 私有命令只在当前 InputConnection 内传递，并由 KEMI 会话门禁拒绝旧请求；不要改为全局广播、无障碍注入或 root 级输入注入，否则会扩大权限和跨会话误操作风险。

---

## V1.55 - 2026-08-27

### 主题
重做全局键盘虚拟鼠标区域的视觉和紧凑屏布局，并在 63 完成远程鼠标移动、三键状态、模式切换及稳定性闭环。

### 过程
- 首次真机截图确认原触控板并非单纯“颜色不明显”：水族 `GLSurfaceView` 的独立合成层覆盖了顶部 Canvas，同时 V900 Android 12 将组合区限制为约 48dp，原先上下排列的三键被裁到可视区域之外。
- 尝试扩大父容器高度后，真机仍按固件紧凑 IME 高度合成。最终改为响应式布局：高度足够时采用“上滑动区、下三键”，紧凑高度下采用“左侧 64% 滑动区、右侧 36% 左/中/右键”，不增加整个输入法高度，也不压缩普通键盘。
- 通过 Display 2 操作触控区、Display 0 观察远程 macOS 光标和界面反馈，并开启仅测试时生效的私有命令接收日志，确认事件不是只在 KBoard 内部绘制。

### 修改
- `DesktopTouchpadView.kt`：滑动区改为高对比蓝灰实体面板、青色描边、中心方向引导和明确提示；三键使用独立深色块及描边，按下时左/中键变蓝、右键变绿；增加紧凑横排/宽松上下排两套响应式布局，并修正紧凑模式文字位置。
- `DesktopTouchpadView` 在该固件改用软件 Canvas 层，避免动态尺寸建立前缓存零尺寸硬件层；移动事件仍按 VSync 合并，不增加 Binder 发送频率。
- `DesktopKeyboard.kt`：水族 Surface 和过渡层从触控板下沿开始布局，并把组合区、触控板显式置顶；触控板区域不再触发鱼群投喂、涟漪或水滴音，键区和鱼池原交互保持不变。
- `FcitxInputMethodService.kt`：增加 `KBoardRemoteMouse` 条件诊断日志。只有系统显式打开该 tag 的 DEBUG 时才记录移动和按钮是否被当前 KEMI 输入连接接受，正式默认不输出高频移动日志。

### 验证
- 仅构建正式版：`./scripts/assemble-release-local.sh -PkboardApplicationId=org.fcitx.fcitx5.android` 成功，287 个 Release 任务完成；未构建、未安装 Debug APK。
- 最终 APK 为 `fcitx5-android/build/kboard.apk`，包名 `org.fcitx.fcitx5.android`、`versionCode=152`、`versionName=1.4.1`，正式证书 SHA-256 保持 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`，APK SHA-256 为 `b69ab2236efa6a951d6d0385ed8e03c6c83852b8ae7bb369fe194a0d1d2a64f8`。
- 最终 Release 已通过 `adb install -r` 安装至 `192.168.3.63:5555`。Display 2 截图确认左侧滑动区、左键、中键、右键全部可见，三键白字完整，按住左键显示蓝色按压态，释放后恢复。
- 横向滑动产生连续相对位移，专用日志每帧均为 `accepted=true`；远程 Display 0 光标从左上区域移动到页面中部并触发悬停反馈。
- 左、中、右键均得到成对 `down=true/false accepted=true`；额外长按左键约 2.3 秒后释放，DOWN/UP 完整且未卡键。
- 以 3 秒间隔完成 3 次“隐藏全局键盘 -> 从远程工具栏重新显示”循环，最终 `mCurTokenDisplayId=2`、`mInputShown=true`；crash buffer、目标包 FATAL、ANR 均为空。
- 从全局模式切回普通键盘截图确认普通布局、工具栏、语音键和输入按键均未改变；随后恢复全局模式成功。测试结束已把 `KBoardRemoteMouse` 日志级别恢复为 INFO。

### 待办与风险
- Shell `input` 只能生成单指触摸，无法可靠构造“一个手指持续按住鼠标键、第二个手指同时移动”的真实多点序列；该代码路径保留现有 pointerId 分离和统一补发 UP 机制，仍建议用户手动验收一次双指拖动手感。
- KEMI/RustDesk 在 macOS 某些应用区域可能不显示系统右键菜单，中键也可能无界面动作；本轮以接收端返回 `accepted=true` 和完整 DOWN/UP 证明链路，业务端不同远程系统的具体中键行为仍由 RustDesk/目标系统决定。

---

## V1.56 - 2026-08-27

### 主题
按人体工学重构全局键盘虚拟鼠标区：扩大为整行连续触控板、独立三键操作带，并修复候选工具栏遮挡鼠标键。

### 过程
- 复核 V1.55 真机界面后确认，旧方案只是把约 48dp 高的狭窄区域横向切成滑动区和三颗按钮；触控行程太短，手指难以精确控制远程鼠标，也没有充分使用全局键盘顶部空间。
- 参考 Android、Microsoft 和 Apple 的触控/指针交互规范：交互目标至少约 44–48dp，常用目标应更大；按下即提供清晰反馈；手势应沿用用户熟悉的轻触、滑动和按住拖动语义。
- 首版整行方案在 63 截图发现三颗鼠标键只露出上边框。根因是全局键盘候选/工具栏以 48dp 覆盖在桌面组合区底部，触控视图仍把按钮绘制在该覆盖层下面；因此同时修正全局高度预算和触控板内部安全区，而不是继续调颜色掩盖裁切。

### 修改
- `InputView.kt` 只在全局模式把桌面触控预算从 112dp 调整为 208dp；其中上部 160dp 供鼠标操作，下部 48dp 明确保留给候选/工具栏。普通文字、数字和浮动键盘仍使用原高度路径。
- `DesktopKeyboard.kt` 在 `onMeasure()` 前建立 208dp/43% 上限的动态组合区，并保证六排物理键每排至少 42dp；避免 V900 Android 12 在布局完成后才改高度而继续使用旧的 48dp 测量结果。
- `DesktopTouchpadView.kt` 改成上、下两层：上方整宽连续触控板，下方独立左/中/右键。按钮全部不小于 48dp；按使用频率分配宽度为左键 46%、中键 22%、右键 32%。
- 增加触点光晕和中心触点，手指 DOWN 即反馈；触控板短按等价于左键单击，滑动继续使用逐帧合并和有限加速。按住任意实体鼠标键进行双指拖动时禁用触控板轻触单击，避免第二指释放意外打断已按住的按钮。
- 增加“轻触触控板执行左键”中英文提示。鱼群、候选、语音、全局物理键、普通键盘以及现有远程私有协议均未改动。

### 验证
- 只构建正式版：正式签名的 `./scripts/assemble-release-local.sh -PkboardApplicationId=org.fcitx.fcitx5.android` 完整成功，287 个 Release 任务完成；未生成 Debug APK。
- APK 为 `fcitx5-android/build/kboard.apk`，包名 `org.fcitx.fcitx5.android`、`versionCode=152`、`versionName=1.4.1`，SHA-256 `f0373aaa6adf26a3889a991e46592cc79f65b480d8d24f29e61a9e0782e515a5`。
- 正式 APK 已 `install -r` 到 `192.168.3.63:5555`。Display 2 截图确认整宽触控板、48dp 三键和全部文字完整可见，不再被候选工具栏裁切；触控 DOWN 可见蓝色光晕和白色触点。
- 触控板横向滑动连续产生相对位移，全部 `accepted=true`；短按触控板产生一组成对左键 DOWN/UP；独立左、中、右键各产生一组成对 DOWN/UP，全部被当前远程输入连接接受。
- 以至少 3 秒间隔切回普通键盘截图，普通布局、工具栏、语音入口和按键尺寸保持原样；随后恢复全局模式。测试日志中目标包 FATAL、ANR 均为 0，测试结束恢复鼠标诊断日志为 INFO。

### 待办与风险
- 触控板采用相对位移，最终速度仍同时受 KBoard 加速度曲线、KEMI/RustDesk 转发和远端系统鼠标设置影响；如果用户希望更慢或更快，应增加可配置灵敏度，而不是再次缩小触控区域。
- 48dp 候选/工具栏是输入法功能区，在无候选时视觉上表现为暗色留白；不能把它并入鼠标命中区，否则中文候选出现时会与鼠标手势争抢触摸。
- ADB 单指事件已验证滑动、轻触和三键；真实“双指按住左键并拖动”仍需人工手势确认手感，但新增保护已确保第二指释放不会合成额外左键单击。

---

## V1.57 - 2026-08-28

### 主题
将全局键盘触控板改为 Android 12 系统级虚拟鼠标，并按请求键盘的屏幕注入，同时扩大可滑动区域并修复鼠标事件回灌风险。

### 过程
- 复核确认 `/Users/newlink/kemi/keystore/debug.keystore` 的文件名只是历史遗留，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`，与 63 的 Android 平台证书完全一致；正式身份以证书内容为准，不能按文件名判断。此前变更记录中“不得使用该文件”的临时结论由本条纠正。
- 原方案依赖当前编辑器的 `performPrivateCommand`，必须同时修改 KEMI/RustDesk 接收端才能移动远端光标。本轮利用平台签名授予的 `android.permission.INJECT_EVENTS`，构造 `SOURCE_MOUSE` 的 `MotionEvent`，使目标屏收到与本地插入鼠标同类的悬停、按下、释放事件；私有协议仅作为无系统权限环境的降级路径。
- 53 真机包含 D0、D2 和 RustDesk 虚拟 Display 49。远程键盘代理位于请求屏幕的对屏，因此对已知远程编辑器包采用明确 D0↔D2 映射，不从活动 Display 列表猜测，避免误将鼠标注入 Display 49。
- 初版真机注入暴露了事件回灌：系统鼠标落在键盘所在屏时，注入事件可能再次进入触控板并递归生成鼠标事件。修复后触控板只接收 `SOURCE_TOUCHSCREEN`，全局键盘明确拒绝 `SOURCE_MOUSE`，切断反馈环；输入结束、解绑和服务释放时同时清空合并中的位移，避免下次打开产生残留跳动。

### 修改
- `AndroidManifest.xml` 增加签名级 `android.permission.INJECT_EVENTS` 请求。
- 新增 `SystemMouseInjector.kt`：维护每个 Display 的绝对光标位置，把触控板相对位移转换为系统 `HOVER_MOVE`，并为左、中、右键生成完整 DOWN/BUTTON_PRESS 和 BUTTON_RELEASE/UP 序列；事件带目标 `displayId`、`SOURCE_MOUSE` 和鼠标工具类型。
- `FcitxInputMethodService.kt` 优先走系统注入；普通编辑器注入 IME 所在屏，KEMI/RustDesk 跨屏代理按 D0↔D2 注入请求屏；权限或框架注入不可用时回退既有私有命令。输入结束和解绑时释放按钮并清理待发送位移。
- `InputView.kt` 与 `DesktopKeyboard.kt` 仅把全局模式触控预算从 208dp 扩到 280dp，约 232dp 用作连续滑动区、48dp 保留候选/工具栏；普通键盘高度路径不变。
- `DesktopTouchpadView.kt` 和 `DesktopKeyboard.kt` 增加输入源隔离，系统鼠标事件不能再次驱动触控板。

### 验证
- 53 使用相同平台证书安装独立测试包后，`INJECT_EVENTS` 与 `WRITE_SECURE_SETTINGS` 均为 `granted=true`；系统鼠标指针可见并随触控板移动，左键一次点击仅产生一组成对 DOWN/UP，修复后无递归按钮日志、无 `SecurityException`、无 FATAL。
- 完整签名 Release 构建成功，287 个任务通过且没有生成 Debug APK。最终 APK 为 `fcitx5-android/build/kboard.apk`，包名 `org.fcitx.fcitx5.android`、`versionCode=152`、`versionName=1.4.1`，证书 SHA-256 为上述平台证书，APK SHA-256 为 `12fc5607013a8f571504f720a65885ac8faf2527d32852ee25c9cbeded94155b`。
- 63 原安装包位于 `/data/app` 且使用错误证书，两项签名权限均未授予。卸载旧数据包、安装平台签名 Release 后已恢复 KBoard 为默认输入法；设备回读 `WRITE_SECURE_SETTINGS: granted=true`、`INJECT_EVENTS: granted=true`，安装后近期日志无目标包 FATAL 或权限异常。

### 待办与风险
- 53 当前没有建立中的真实远程桌面会话，因此已验证系统注入、显示路由条件和事件闭环，但 D0/D2 远程业务端的最终光标手感仍由用户在 63 实际会话验收。
- 系统注入属于平台签名能力，若未来更换证书或改用普通签名，Android 会拒绝 `INJECT_EVENTS`，届时只会回退到要求接收端配合的私有协议。
- 本次因 63 旧包证书不同必须卸载后重装，旧 KBoard 应用数据已由 Android 删除；后续只要保持当前平台证书即可使用 `install -r` 无损升级。

---

## V1.58 - 2026-08-28

### 主题
继续扩大全局键盘鼠标滑动区，重排完整尺寸方向键，并在鼠标控制条增加发送给远程会话的 HOME/BACK 标准键。

### 过程
- 63 的 1920×1280 截图确认鼠标面板上方存在约 48dp 无效带。根因是 KawaiiBar 已被移动到第一排按键上方，但桌面键盘窗口仍在顶部为它重复预留一次空间。
- 将桌面窗口起点改到父容器顶部，并把回收的 48dp 全部并入鼠标 header；外层总高度保持不变，因此 F1 以下六排按键的位置和高度不变，普通键盘仍从 KawaiiBar 下方开始。
- 原方向区把 ↑/↓ 塞进一个普通行高，各只有半高且难以命中。改成完整倒 T：↑ 位于上一行，←/↓/→ 位于最下一行，四键均为完整行高并继续支持长按连发。
- 第一版倒 T 真机截图发现 ↑/↓ 中心约差 36px；按两行实际权重反算后，将 `/`、↑、右 Shift 调整为 1.26/1.40/1.14 单位，使 1920px 下 ↑/↓ 中心误差小于 1px。
- 用户进一步明确 HOME/BACK 必须发送给远程会话，不能触发 KBoard 本机的 Android 导航。因此按钮通过当前远程 `InputConnection` 发送标准 `KEYCODE_HOME`、`KEYCODE_BACK` DOWN/UP，由 KEMI/RustDesk 转交远端系统处理。

### 修改
- `InputView.kt`：桌面模式取消顶部重复候选栏占位，鼠标 header 从 280dp 增加至 328dp；高度公式同步扣除重复 KawaiiBar，保证物理键盘整体不下移。退出全局模式时恢复普通键盘原有顶部约束。
- `DesktopKeyboard.kt`：方向区改为完整尺寸倒 T；适度缩短过宽空格和右 Shift，保留 Ctrl、Alt、中英、Command、语音及全部字符键；接入 HOME/BACK 系统键动作。
- `DesktopTouchpadView.kt`：控制条由三键扩展为 `HOME / BACK / 左键 / 中键 / 右键` 五键，保持每键完整 48dp 高度和独立按压反馈；HOME/BACK 与鼠标键均保留真实按下/释放生命周期。
- `KeyAction.kt`、`CommonKeyActionListener.kt`、`FcitxInputMethodService.kt`：增加远程导航键转发、重复 DOWN 防护，并把 DOWN 与当时的远程 `InputConnection` 绑定；输入结束时向原连接补发 UP，既避免卡键，也不会误发给下一次远程会话。`SystemMouseInjector` 只负责鼠标，不处理 HOME/BACK。
- 中英文资源增加 HOME/BACK 固定标签；普通键盘资源和布局未改。

### 验证
- 平台签名 Release 完整构建成功，287 个任务通过，没有生成 Debug APK；`git diff --check` 通过。
- 最终 APK 为 `fcitx5-android/build/kboard.apk`，包名 `org.fcitx.fcitx5.android`、`versionCode=152`、`versionName=1.4.1`，SHA-256 为 `4ca6b2c0df7b85a39eb5f9f6b159dfadb7df29b5277bced60126a1398de5c2c9`。
- 正式 APK 已覆盖安装到 63，设备保持 `INJECT_EVENTS: granted=true`。真机截图确认滑动面板顶边从约 y=216 上移至 y=120，增加约 96px/48dp，F1 行仍在约 y=768；HOME、BACK 和鼠标三键全部完整可见。
- 最终截图中 ↑/↓ 中心均约为 x=1684，视觉和命中区域上下对齐；四个方向键均为完整行高。安装后 crash buffer 未发现 KBoard FATAL。

### 待办与风险
- 为避免影响用户当前远程页面，本轮没有自动点击 HOME/BACK；标准远程按键事件构造、原连接绑定和释放生命周期已闭环，最终远端系统的 Home/Back 语义由用户手动验收。
- HOME/BACK 不再走本地 `INJECT_EVENTS`，不会把 63 的 D0/D2 切回本地桌面；远端 Windows/macOS/Android 对这两个标准键的最终行为由 RustDesk 映射和远端系统决定。

---

## V1.59 - 2026-08-29

### 主题
修复 V900 Android 12 副屏 Display 2 全局键盘底部功能键被 75px 系统导航栏遮挡的视觉与触摸问题，并将四个 KBoard 功能键重排为单行等宽布局。

### 过程
- ADB 确认 D2 导航栏为 `frame=[0,1205][1920,1280]`，高度 75px；IME 窗口仍可绘制到 y=1280，但 y=1205–1280 的触摸归系统导航栏所有。
- 全局模式原先在 `InputView.updateKeyboardSize()` 中直接贴父容器底部，绕过了普通键盘的 `bottomPaddingSpace`；跨 D0/D2 复用 View 时还可能保留上一个 Display 的 Insets。
- 首版单纯增加底部避让会压缩六排物理键和鼠标区。最终改为只从可伸缩的上方触控区扣除当前 Display 的底部 Insets，物理键行高和底部操作行保持不变。
- 真机截图发现四个 KBoard 操作键的旧布局视觉上类似 2×2，不适合 1920px 宽屏。已统一为“退出全局 / 切换屏幕 / 语音 / 隐藏键盘”单行等宽卡片；其下独立的一行为 Android 系统导航栏，按要求不隐藏、不改为沉浸模式。

### 修改
- `BaseInputView.kt`：按当前 View 所在 Display 合并 `navigationBars`、忽略可见性的稳定导航 Insets、`mandatorySystemGestures` 及当前 Display 的 real/app metrics 差值；不写死 75px。
- `InputView.kt`：全局键盘和操作行统一锚定在 `bottomPaddingSpace` 之上，底部距离每次直接赋予当前 Display Insets，不累加；进入全局、窗口显示和延迟布局后都会重新申请 Insets。
- `FcitxInputMethodService.kt`：在 `onWindowShown`、`onStartInputView` 和配置变化时重新向当前 inputView 请求 Insets，避免跨屏或旋转后沿用旧 Display 缓存。
- `DesktopKeyboard.kt`、`KeyboardWindow.kt`：将当前底部 Insets 传给全局组合区，只收缩可弹性的触控板 header，不压缩六排键盘。
- `ToolButton.kt`、`KawaiiBarComponent.kt`：四个底部键使用一致的实体卡片背景、边框和按下反馈；语音按钮在手势结束或 ASR 回到 Idle 后也恢复同一背景，不再出现第三个键透明。

### 验证
- 正式 Release 使用已有平台证书覆盖安装到 `192.168.3.63:5555`，包名 `org.fcitx.fcitx5.android`、`versionCode=152`、`versionName=1.4.1`；没有 `pm clear`，默认输入法和用户配置保留。
- D2 实测日志为 `navigationBottom=75`，D0 为 `navigationBottom=96`；D2 最终四个 KBoard 功能键全部在 y=1205 之上且为单行等宽，视觉位置与命中区一致。
- 在 KEMI 双屏桌面共享保持运行时，以 D2 本地编辑器固定副屏 IME token，完成 300 轮“全局退回普通 -> 等待 3 秒 -> 普通进入全局 -> 等待 3 秒”真实点击，共 600 次模式按钮操作。结果 `completed=300 failures=0`，起始/结束 PID 均为 `25612`，全程 `mCurTokenDisplayId=2`、`mInputShown=true`。
- 内存抽样在约 136–140MB PSS 间波动，最终 `TOTAL PSS=133441KB`、`TOTAL RSS=243888KB`；最终 `ViewRootImpl=2`（编辑页 + IME），没有随轮次线性增长。
- 测试时段过滤 `FATAL EXCEPTION`、KBoard ANR、`WindowLeaked`、输入分发超时和 IME 显示超时均为 0；最终 D2 系统导航区仍为 `[0,1205][1920,1280]`。

### 待办与风险
- KEMI 共享桌面的“键盘”按钮在当前会话中仍会先创建 D0 IME token，即使 KEMI 窗口本身是 D2；通过 KBoard 跨屏中继可在 D2 本地编辑器持续聚焦时正确重建为 D2 token。该 KEMI 唤起行为没有通过修改远程客户端规避，本轮 300 次结果不将“KEMI 直接唤起 D2”冒充为已验收。
- Android 系统导航栏依然是独立的最底一行；这是为了保留系统导航和遵循“不强制沉浸、不隐藏导航栏”约束，不是 KBoard 再增加了一行功能键。

---

## V1.60 - 2026-08-29

### 主题
扩大全局键盘鼠标区并恢复六排物理键尺寸，将 Enter 移至底部操作行，同时修复“浮动键盘快速切全局”主线程崩溃。

### 过程
- 63 真机截图确认，首版全屏高度计算把同一份底部导航 Insets 重复扣除：`bottomPaddingSpace` 已避让一次，`DesktopKeyboard` 又从可伸缩触控头部让出一次，外层 `keyboardView` 仍再次减去 Insets，最终把整块键盘上移空间浪费掉并压矮 A/B/C/D 等六排主键。
- 按要求保留鼠标区与 F1 之间的 48dp 输入/候选缓冲区，不通过删除缓冲区放大按键；改为让全局外层继续使用物理屏完整高度，底部安全距离只在内部约束中应用一次。
- 真机复现到一次独立竞态：先进入浮动键盘、退出浮动后立即切全局，旧的 `post` 布局回调会在全屏尺寸上继续执行浮动键盘位置限制，产生 `min=56、max=0` 的空区间并由 Kotlin `coerceIn()` 抛出 `IllegalArgumentException`。

### 修改
- `InputView.kt`：全局键盘外层不再重复减去导航栏 Insets；触控区、候选缓冲区和底部安全锚点保持原设计，回收的整条系统栏高度全部归还六排物理键。
- 在不改变六排物理键高度和 F1 上方输入/候选缓冲区的前提下，将鼠标滑动区由 320dp 收到 272dp、底部操作带由 64dp 收到 56dp，并将全局窗口上限设为物理屏高度减 56dp；三处改动等量配平，使远程桌面顶部重新露出而不压缩 A/B/C/D 等主键。
- 全局底部操作行调整为“退出 / 语音 / Enter”三颗等宽大键；原主键区 Enter 的宽度按比例归还 Caps、A-L 和标点键；Enter 使用独立青绿色强调色并继续经过 `DesktopKeyboard.onAction()`，保留 Ctrl/Alt/Shift/Cmd 组合状态。
- 底部 Enter 关闭本地按下与释放两条物理键音效通道，只保留远端实际回车链路的单次反馈，避免一次点击听到两个声音；事件发送与组合键状态不变。
- `KeyDrawable.kt` / `KeyView.kt` 为水族深色键帽增加可选静止配色参数；仅 Backspace 使用明显可辨但不刺眼的暖红棕渐变与红色描边，尺寸、触摸区域、白字、按压描边及连删行为均保持不变。
- 修正首次配色未命中的原因：全局 Backspace 是 `DesktopSymKey`，原键定义没有 `button_backspace` 标识，不能依赖外层 View 的默认 id 判断；现由 `DesktopSymKey` 接收可选 `viewId`，并在 Backspace 定义处明确写入唯一 id，配色绑定不再依赖显示文字或位置。
- 全局语音键按住时只改变麦克风图标颜色，不播放按下/释放音效，不触发触觉、Ripple、键帽位移或鱼群反应；普通键盘语音行为不变。
- `InputView.updateFloatingKeyboardPosition()` 增加当前模式复查、非浮动位移归零和空区间安全夹取；所有延迟浮动布局回调在执行前再次确认仍处于浮动且非全局模式，避免旧回调污染新布局。

### 验证
- 使用现有平台证书完成正式 Release 构建，`assembleRelease`、R8 和 `lintVitalRelease` 成功；APK 输出为 `fcitx5-android/build/kboard.apk`，没有生成或安装 Debug APK。
- 正式 APK 通过 `adb install -r` 覆盖安装到 `192.168.3.63:5555`，未清除 KBoard 数据。真机截图确认全局键盘从屏幕顶部开始使用空间，A/B/C/D 与其余六排按键恢复大尺寸，F1 上方输入缓冲区保留，底部三颗操作键完整位于系统栏之上。
- 最终 Release 再次通过 `adb install -r` 覆盖安装到 63；APK 回读为包名 `org.fcitx.fcitx5.android`、`versionName=1.4.1`、`versionCode=152`，SHA-256 为 `c107380f320cc3837161e97486a544635a9ebd921bf47a8e939932d731470817`。稳定画面确认顶部保留约 56dp 远程内容、鼠标区仍具备完整滑动面积、六排键高未变、底部三键未被导航栏遮挡；安装后日志没有新增目标包 FATAL/ANR。
- 已按实际崩溃路径执行一次“普通 -> 浮动 -> 恢复 -> 全局”快速切换；全局键盘正常显示，`mInputShown=true`，修复安装后没有新增 `Cannot coerce value to an empty range`、目标包 FATAL 或 ANR。09:47:34 的旧 FATAL 属于修复前 PID 17306，堆栈已留档并与修复点一致。
- 63 副屏 11:31 现场出现“当前权限无法继续 / 设置数据库参数失败：database is locked”弹窗。窗口焦点和 owner 均属于 `com.newlinksz.kemi.remote`（UID 10089），KBoard 为独立的 `org.fcitx.fcitx5.android`（UID 10091）且现场只拥有标准 `InputMethod` 窗口；KBoard 源码与日志均没有该提示或 SQLite 错误。同期 KEMI `KeyboardProxyActivity` 被连续重建并存在多条自身 `InputService` DEAD connection，因此直接原因是 KEMI 客户端数据库并发写入；本轮反复覆盖安装和模式切换可能触发其已有竞争窗口，但不是 KBoard 访问或锁定了 KEMI 私有数据库。本轮未修改 KEMI 项目。

### 待办与风险
- 本轮只复现一次刚才的精确崩溃路径，没有用 Monkey 或高频无间隔脚本代替真实交互；后续压力回归应继续保持用户要求的约 3 秒操作间隔。
- Backspace 最终配色已通过唯一 `viewId` 绑定并完成 Release 编译、覆盖安装；因副屏随后被 KEMI 数据库锁弹窗遮挡，本轮没有将遮挡状态下的截图冒充为最终视觉验收，颜色观感仍留给用户实际界面确认。
- 官方可安装技能列表中只有 Figma 设计/实现类技能，没有直接针对 Android IME `ConstraintLayout`、多 Display Insets 和系统输入窗口的人体工学布局技能；本轮按 1920×1280 真机像素、系统窗口 frame 和实际触摸安全区进行约束验收。

---

## V1.61 - 2026-09-04

### 主题
修复平台签名 KBoard 冷启动后无法自动启用同包跨屏中继的问题，并在 62 真机验证系统权限路径。

### 过程
- 62 当前系统默认输入法已是主 `FcitxInputMethodService`，但 `enabled_input_methods` 只有主服务，系统可发现的 `DisplaySwitchInputMethodService` 没有被启用，因此用户看不到可用的跨屏中继。
- `dumpsys package` 确认正式包虽安装在 `/data/app`、UID 为普通应用 UID，但平台签名已经使 `WRITE_SECURE_SETTINGS` 获得 `granted=true`；系统签名能力本身没有问题。
- 冷启动旧版 `c7bdcd1e/142` 后捕获到 `Failed to enable same-package display-switch IME relay: exit=255`。根因是旧实现从应用进程启动 `/system/bin/ime enable`，子进程仍继承 KBoard 应用 UID；该命令的 Binder shell 接口只接受 shell/root 身份，平台签名不会把子进程变成 shell。
- 在 62 保留主 KBoard、只通过 Secure Settings 追加同包中继后，Android 12 的 InputMethodManagerService 立即识别两个服务，默认输入法仍保持主 KBoard，证明既有平台签名权限足以完成自动配置。

### 修改
- `DisplaySwitchRelayManager.kt` 移除 `/system/bin/ime` 子进程，改为使用已获授的 `WRITE_SECURE_SETTINGS` 通过 `Settings.Secure.putString()` 更新当前用户的 `ENABLED_INPUT_METHODS`。
- 新实现严格保留已有输入法及 subtype 字段，只追加固定的同包 `DisplaySwitchInputMethodService`，不修改 `DEFAULT_INPUT_METHOD`；写入后重新读取并确认中继存在。
- 增加列表处理单元测试源码，覆盖保留第三方 IME/subtype、空列表追加、已有中继不重复及 subtype 后缀识别。

### 验证
- 只构建正式 Release；`assembleRelease`、R8、`lintVitalRelease` 均成功，输出为 `fcitx5-android/build/kboard.apk`，未构建或安装 Debug APK。
- 在 62 先将启用列表恢复为只有主 KBoard，再通过 `adb install -r` 无损覆盖正式 Release；未执行 `pm clear`，默认输入法和用户配置均保留。
- 覆盖后冷启动，`enabled_input_methods` 自动变为主服务加同包跨屏中继；`default_input_method` 仍为主 `FcitxInputMethodService`。
- 62 回读版本为 `1.4.1/152`，`WRITE_SECURE_SETTINGS` 与 `INJECT_EVENTS` 均为 `granted=true`；再次重启后列表无重复项，crash buffer、`AndroidRuntime` 和 `KBoardCrash` 均无新增异常。

### 待办与风险
- 应用安装后若既未被系统绑定为默认 IME、也未启动任何 KBoard 组件，Android 不会仅因平台签名自动创建应用进程；V900 量产系统已默认绑定主 KBoard，因此开机首次绑定会执行自动补齐，不要求用户进入设置手工启用中继。
- 本轮只验证系统权限与中继自动启用闭环，没有自动点击跨屏按钮改变用户当前 Display；D0/D2 的既有中继切换逻辑未修改。

---

## V1.62 - 2026-09-06

### 主题
修复 Android 12 远程办公压力场景中 IME 在 token 尚未设置时显示窗口的主线程崩溃，建立输入生命周期门控。

### 过程
- 读取 `/private/tmp/kemi-three-platform-stress-20260906-final2/android75-logcat.txt`，正式窗口内确认 5 次 `com.newlink.kemi.kboard` 主线程崩溃：04:47:11.631、05:00:55.847、05:41:46.253、06:48:30.334、07:08:53.106；共同路径为 `showSoftInputWithToken -> showSoftInput -> showWindow -> SoftInputWindow.show`，异常为 `Window token is not set yet.`。
- Android 12 的显示 Binder 消息与 `initializeInternal/attachToken`、输入连接启动、旧服务销毁存在顺序竞争。上游 `showWindow` 在真正调用 `SoftInputWindow.show` 之前已经设置 `mInShowWindow/mWindowVisible` 等状态，事后吞异常既不能恢复 token，也可能留下错误的可见状态。
- 75 系统原 APK 包名已为 `com.newlink.kemi.kboard`，版本仍为 1.4.1/152；DEX 中有旧的 after-destroy 兼容字符串，但没有当前源码中的 before-attachToken 捕获字符串，不能只凭相同版本名判断修复已部署。原始堆栈没有组件实例信息，无法把五次崩溃逐一归属主服务或切屏中继；两条显示入口均处理。

### 修改
- 新增 `ImeShowGate.kt` 和 `TokenReadyInputMethodService.kt`。只有已完成 token 绑定、当前输入生命周期有效、`currentInputStarted` 为真、当前 `InputConnection` 和窗口 attributes token 非空时，才进入框架显示流程。
- 未就绪请求合并为一个当前生命周期的显示意图；`attachToken` 与 `onStartInput` 的就绪事件触发一次主线程消息交接，再经系统 `requestShowSelf` 获取新的显示调用上下文。没有计时重试或显示异常捕获。原 ResultReceiver 当次返回未改变状态，不保留旧 WithToken 上下文；显式/隐式/强制标志按两套 Android API 的不同含义转换。
- 隐藏意图、隐藏窗口、输入视图结束、失焦结束、解绑、重绑和服务销毁/被替换时作废旧请求代次。主服务释放资源前即退役显示入口，迟到的就绪回调不能重新显示旧窗口；解绑先保存 uid，再执行父类清理与 native deactivate。
- `LifecycleInputMethodService.kt` 接入新基类，`FcitxInputMethodService.kt` 接入生命周期和触摸隐藏取消；删除 `Android12ImeFrameworkCompat.kt` 及其测试中原有两类显示异常匹配代码。保留与本次无关的 Android 12 bind-before-initialize 精确兼容。
- `DisplaySwitchInputMethodService.kt` 明确拒绝显示请求与 `showWindow`：中继只交接 token，不应创建键盘 UI。
- 新增 `ImeShowGateTest.kt`（10 项，含连续 20 次显示隐藏）及可复用真机脚本 `scripts/test-ime-window-lifecycle.py`（逐次断言系统和真实窗口可见状态、PID、日志，测试结束恢复默认 IME）。

### 验证
- `ImeShowGateTest` 10/10 与保留的 `Android12ImeFrameworkCompatTest` 3/3 通过；签名 Release 构建、R8、lintVital、`git diff --check` 通过，未安装 Debug APK。
- 最终验证包为 `com.newlink.kemi.kboard`，1.4.1/152，arm64-v8a；v1/v2 签名验证通过，证书 SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。APK SHA-256 `811f6dec318dfae45d1776fb5bf717f86f2f143e61daceb1ac567f615ed9c227`，固定备份 `/private/tmp/kboard-token-regression/final-811f6dec.apk`，设备回读哈希一致。
- 75（`192.168.3.75:5555`，API 31，D0）最终隔离窗口为 **08:50:47–08:58:00 +0800**：约 3 秒动作间隔，**20 次显示/隐藏 + 20 次 HOME/恢复 + 20 次中继/主服务重建，60/60 通过**。每次同时断言 `mInputShown` 与 `mWindowVisible/mDecorViewVisible`，重建后 token 实际变化；起止 PID 均为 **26438**。
- 回归窗口完整 logcat 中 **FATAL EXCEPTION、ANR、Window token is not set yet、Input dispatching timed out 均为 0**；最终 HOME 后连续静置检查和清理后 dumpsys 均确认隐藏，未发生迟到回弹。默认 IME 保持主服务，未清除数据。
- 证据目录 `/private/tmp/kboard-token-regression/final-paced/`：`summary.json`、`progress.json`、逐轮 dumpsys、`logcat.txt`、`device-identity.json`、`exit-info.txt`、两类 JUnit XML 和 `apk-signature.txt`；`show-hide-final.png`、`home-resume-final.png`、`relay-recreate-final.png`、`final-home-hidden.png` 已保存，实际查看显示/隐藏截图正常。
- 早先 run4 已完成 52 轮，但在 08:45:36 被另一重复任务的覆盖安装中断，系统记录 `stop ... due to installPackageLI`，不是 FATAL/ANR；协调暂停并发操作后重新完成上述同一最终 APK 的 60 轮，不把中断轮次冒充最终通过。

### 待办与风险
- token/输入连接门控限定于 V900 Android 12/API 31；其他 Android 版本保留系统 InputMethodImpl 显示行为，仅保留销毁保护。已保留系统显式/隐式/强制显示策略，不反射修改框架私有状态。
- 本轮以 Notes 真实输入连接和同包中继重建进行定向回归；不能替代 Windows/macOS 实际远程连接/断开的完整三小时压力复测。
- 全量 JVM 测试初次运行 22 项中 1 项失败：既有 `ThemeSerializationTest.version2` 在第 101 行断言“旧主题不需要迁移”失败，主题代码不在本次修改范围；定向输入法测试和 Release 构建通过。
- canonical main 工作区原有的中继自动启用、native 子模块及 cl.md 改动保留；本次构建使用该既有构建环境，本次提交只包含窗口生命周期修复、相应测试、脚本和文档。没有发布或上传 APK。

---

## V1.63 - 2026-09-06

### 主题
完成固定签名 APK 的 Android75 三小时输入生命周期压力验证，并保存旁路资源观察证据。

### 过程
- 保持 V1.62 最终 APK（SHA-256 811f6dec318dfae45d1776fb5bf717f86f2f143e61daceb1ac567f615ed9c227）不变，真实软键输入、显示隐藏、HOME恢复、焦点切换和中继重建交替执行，动作间隔约3秒。
- 旁路监控只读采样，不重启应用、不改变75焦点；最初约8分钟缺少双端连续资源覆盖，报告明确标注。

### 修改
- 新增 scripts/stress-ime-lifecycle.py 与 scripts/monitor-dual-platform-resources.py，可复用逐动作断言、PID/APK核验、资源与日志采样。
- 新增 docs/测试报告/2026-09-06-Android75-三小时稳定性/：详细报告、资源与堆分配CSV、两张曲线、摘要和原始证据SHA清单；不提交APK或生成构建产物。

### 验证
- 09:40:24–12:40:40 +0800，10816.211秒，3290次动作全部成功；显示隐藏127、HOME恢复127、焦点切换126、中继重建126，共506个阶段闭环；888次可见断言匹配。
- PID26438始终一致、重启0；FATAL、ANR、Window token is not set yet、输入分发超时、迟到回弹均0，设备起止APK哈希一致，最终HOME截图确认隐藏。
- KBoard PSS97.570→97.921MiB，Native allocated38.747→37.312MiB，Java PSS12.426→12.133MiB；FD112→105、峰190后回落，线程46→52后段稳定。352点主采样与旁路曲线支持本轮资源正常。
- 两个脚本Python语法解析通过，报告统计与原始操作/状态断言交叉核对，曲线和最终截图已目视检查。

### 待办
- 本轮是D0 Notes输入生命周期压力，不替代D2、中文候选、ASR、Windows或真实远程连接/断开的专项验证。
- 旁路旧macOS包不含新增autoreleasepool修复，实际堆分配388.5→513.1MiB，仍有持续增长风险；不得将KBoard通过泛化为双端无泄漏。PAD共享内存前段增长、后段平台；旁路一次短生命周期未知子进程采样失败保留在报告。相关风险已回传主任务。
- 仅本地main提交，不推送、不发布。原始证据在 /private/tmp/kboard-3h-20260906-attempt1/。

---

## V1.64 - 2026-09-11

### 主题
修复摸鱼全局键盘 Caps 无效，并统一修复桌面控制键与 Ctrl/Alt/Command 组合键可能被 Fcitx 吞掉的问题。

### 过程
- 逐项检查全局键盘的字符键、Caps、Esc、Tab、F1-F12、Enter、Backspace、方向键、Shift/Ctrl/Alt/Command、HOME/BACK、鼠标键、语音和中英切换事件路径。
- 原 Caps 与功能键被标记为 Fcitx 虚拟按键；无 Unicode 的控制键可能在本地输入法阶段被消费，远端收不到标准 Android `KeyEvent`。同时 Caps 没有本地锁定状态，所以之后由 KBoard 提交的英文字母仍是小写。
- 进一步发现 Ctrl/Alt/Command 虽有正确的独立 DOWN/UP 生命周期，但字符主键仍走 Fcitx；界面能显示“复制/粘贴”等提示，不代表远端收到完整组合键。

### 修改
- 新增 `DesktopKeyPolicy.kt`，集中定义桌面控制键路由、组合键直发判定、字符到物理主键映射，以及英文 Caps/Shift 异或大小写规则。
- `DesktopKeyboard.kt` 为 Caps 增加持续选中态与本地英文大小写状态；中文输入时继续提交小写拼音，不让 Caps 破坏候选输入。
- Caps、Tab、F1-F12 始终向当前远程 `InputConnection` 发送标准成对 DOWN/UP；Esc、Enter、Backspace、方向键在没有预编辑时直发，在中文预编辑期间仍交给 Fcitx 完成取消、上屏、删除和候选选择。
- Ctrl/Alt/Command 与字母、数字、符号或控制键组成 chord 时，主键强制绕过 Fcitx；Shift 元状态一并保留，覆盖 Cmd+C/V/A、Cmd+Shift+3/4/5、Ctrl+C/V、Alt+F4、Shift+Tab 等桌面组合。
- 独立 Ctrl/Alt/Shift/Command 仍保持原有真实 DOWN/UP 生命周期；隐藏、切布局和输入结束时的补 UP 逻辑不变，避免远端卡住修饰键。HOME/BACK、鼠标、语音、中英切换、普通键盘布局和水族背景未改。
- 新增 `DesktopKeyPolicyTest.kt`，覆盖所有可见控制键、中文预编辑分流、Caps/Shift 英文大小写、中文拼音保护、组合修饰键识别、字符物理键映射，以及组合键在预编辑期间强制直发。

### 验证
- Debug 测试源码和应用 Kotlin 均编译通过；但本机 Gradle 9.4.1 在启动测试 JVM 时连续两次无法加载 `worker.org.gradle.process.internal.worker.GradleWorkerMain`，因此不能把测试源码编译通过记录为 JUnit 已执行通过。
- 使用既有正式平台证书完成最终增量 Release 构建；`assembleRelease`、R8、`lintVitalRelease` 共 287 个任务成功，未安装 Debug APK。
- 最终正式产物包名 `com.newlink.kemi.kboard`、版本 `1.4.1/152`，v1/v2 签名有效，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 最终 APK 位于 `fcitx5-android/build/kboard.apk`，SHA-256 为 `1089f12cc9c64e868e21736402e9dedab8bfdc7bd450334588c2fb212446fed7`。
- 构建复核期间曾误传 legacy applicationId override，生成过 `org.fcitx.fcitx5.android` 临时包；该包未安装、未复制到 bin、未发布且已被上述正式包覆盖，不作为有效交付物。
- 正式 APK 已通过 `adb install -r` 覆盖安装到 Android 12 设备 `192.168.3.63:5555`，未执行 `pm clear`；设备回读为 `com.newlink.kemi.kboard`、`1.4.1/152`，`WRITE_SECURE_SETTINGS` 与 `INJECT_EVENTS` 均为 `granted=true`。
- 测试前设备默认输入法为历史兼容包 `org.fcitx.fcitx5.android/.input.FcitxInputMethodService`。验收时切换到正式包主服务及同包跨屏中继，确认 `mCurMethodId=com.newlink.kemi.kboard/...FcitxInputMethodService`、PID `13029`、D0 输入窗口可见。为避免设备继续启动未包含本修复的旧包，最终默认输入法保持为正式 `com.newlink.kemi.kboard` 主服务；enabled 列表保留正式主服务、正式中继及原两个 `org.fcitx` 服务，旧包未删除，可随时回滚且没有清除任一包数据。
- 在 Notes 空白搜索框进入摸鱼全局键盘后，以真实触摸坐标执行 `Caps -> A -> Caps -> A`，字段结果为 `Aa`：首次 Caps 后大写、再次 Caps 后恢复小写，功能链路通过。进程 PID 始终为 `13029`；测试后 `ApplicationExitInfo` 没有新增崩溃、ANR 或异常退出记录。
- 63 已建立真实 Windows 远端会话（远端 ID `238638760`），通过全局键盘真实触摸分别按住并释放 Ctrl、Alt、Command。三类修饰键的 DOWN/UP 均依次出现在 `KeyboardProxyActivity input_connection`、`KeyboardProxyManager forwarded`、Flutter `dart_received accepted=true` 和 `dart_dispatch`，证明 KBoard 到 KEMI 远程输入通道的修饰键生命周期完整；此前真实 macOS 会话中的 Command 也取得同样完整链路。测试期间 KBoard 进程持续存活，没有新增 FATAL 或 ANR。
- 源码复核确认：Ctrl/Alt/Command 任一处于按下状态时，单字符 `FcitxKeyAction` 会由 `DesktopKeyPolicy.shortcutKeySym()` 转为物理主键，并以 `DesktopKeyAction(shortcutChord=true)` 进入 `sendDesktopKeyPress()`；`shouldSendDirectly()` 对该标志无条件直发，因此字符主键不会再次进入 Fcitx 预编辑。主键 DOWN/UP 的 `metaState` 由服务端仍处于按下状态的修饰键集合生成。

### 待办与风险
- Windows/macOS 真实会话已经证明 Ctrl/Alt/Command 的修饰键 DOWN/UP 能完整到达远程通道，源码也证明 `shortcutChord=true` 的字符主键必然直发；但本轮日志证据没有逐项覆盖 Cmd+C/V/A、Cmd+Shift+3/4/5、Ctrl+C/V、Alt+F4、Shift+Tab 的远端应用语义，因此不将每一个具体快捷操作标记为人工验收通过。
- 自动化尝试用分离的 `input motionevent` 构造组合键不能形成 Android 多触点；`adb input keycombination` 会绕过或破坏 KBoard 自身的触摸修饰键状态，Windows 上的 Meta+D 实际只输入了 `d`；底层 `sendevent` 尝试也没有被 InputReader 转换成有效按键事件。这些系统注入结果均明确判为无效，不作为 KBoard 组合键通过或失败的依据。真实验证必须使用全局键盘控件产生的触摸事件，或增加不会绕过 KBoard 的专用测试入口。
- Caps 的本地视觉/英文状态从本次点击开始与远端同步；如果连接远端时远端本来就处于 Caps 开启状态，标准输入协议没有反向状态查询，首次显示可能与远端初始锁定态不同，按一次 Caps 后恢复同步。

---

## V1.65 - 2026-09-11

### 主题
修复摸鱼全局键盘英文字符与空格在真实远程代理输入框中无响应，并在 Android 12 设备 75 与 macOS 远端完成中英文联合验证。

### 过程
- 63 的真实 Windows 会话中，Ctrl、Alt、Command 的独立 DOWN/UP 能完整进入 KEMI 远程通道，但全局英文键盘点击 A 和空格没有改变远端输入框；普通键盘 Backspace 能清除通过系统键事件写入的测试字符，证明远端焦点与代理连接有效。
- 源码定位到全局英文字符仍以 `FcitxKeyAction` 进入本地 Fcitx。代理编辑器虽然实现了 `commitText` 转发，但英文物理键进入 Fcitx 后不保证形成最终提交，因此键帽有触摸反馈而远端没有字符。
- 直接把所有字符改为物理键会破坏中文拼音预编辑、候选和删除语义，因此修复按当前输入法语言分流：英文使用标准 Android `KeyEvent`，中文继续使用 Fcitx。

### 修改
- `DesktopKeyPolicy.kt` 增加桌面英文可打印键直发策略；中文输入法明确保留预编辑路径。
- `DesktopKeyboard.kt` 在全局英文模式将单字符字母、数字、符号及空格转换成 `DesktopKeyAction`；Ctrl+Space 仍优先切换中英文，中文小写拼音、候选栏和预编辑逻辑不变。
- `CommonKeyActionListener.kt` 将动作携带的 `metaState` 传给物理键发送接口。
- `FcitxInputMethodService.kt` 合并服务端真实按住的 Ctrl/Alt/Shift/Meta 状态与动作携带的 CapsLock 等锁定状态，使英文物理字符、Caps、Shift 和组合键共享一致的元状态。
- `DesktopKeyPolicyTest.kt` 增加英文可打印键直发、中文保留 Fcitx 的针对性测试。

### 验证
- Release Kotlin 与测试源码编译通过；正式 `assembleRelease`、R8、`lintVitalRelease` 共 287 个任务成功。Gradle 9.4.1 的测试执行器仍因无法加载 `worker.org.gradle.process.internal.worker.GradleWorkerMain` 退出，因此未将测试源码编译成功表述为 JUnit 已运行通过。
- 正式 APK 为 `fcitx5-android/build/kboard.apk`，包名 `com.newlink.kemi.kboard`、`versionName=1.4.1`、`versionCode=152`，SHA-256 `805cf30e1a0fc4b4d0c9d5efa0f29676aa3ba389235dd4bbf3c2edcc31479a53`；v1/v2 签名有效，证书 SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- APK 已通过 `adb install -r` 无损覆盖到 `192.168.3.75:5555`，未执行 `pm clear`；默认输入法仍是正式主服务，同包跨屏中继继续启用，设备安装 APK 哈希与本地制品一致。
- 75 连接真实 macOS 远端 `260262802` 后，全局英文键盘输入 `A -> Space -> A`，远端显示 `a a`；Backspace 能清空；`Caps -> A` 显示 `A`。切换中文后输入 `ni` 会进入本地预编辑并出现候选，未在选词前泄漏到远端。
- 联合验证后 KBoard PID 为 `11176`，输入窗口仍在 Display 2 可见；`ApplicationExitInfo` 没有新增异常退出，最近一条是覆盖安装产生的 `installPackageLI` 正常停止。

### 待办与风险
- 仓库现有单元测试能验证路由策略、KeySym 映射和元状态计算，但没有能够真实构造两个独立触点、穿过 `CustomGestureView` 命中不同键帽并检查远端结果的可靠 instrumentation 测试。
- `adb input keycombination` 绕过 KBoard；两个并发 `adb input` 进程在设备上也没有形成稳定的同一多触点手势，实测只得到小写 `a`。这些方法不能作为 Shift/Ctrl/Alt/Meta 按住再点 A 的通过证据。
- 多指组合键的可靠验收方式仍是真人同时按住修饰键和字符键，结合 KBoard/KEMI 的 DOWN、主键、UP 日志及远端可见结果；若要自动化，需要新增能向同一个 IME View 注入单个多指 `MotionEvent` 序列的专用 Android instrumentation 测试入口。
- 本机构建时系统数据卷仅剩约 116MiB。已清理未被使用、可再生成的 Gradle 8.13 缓存约 4.8GiB，并将构建临时目录转移到 ORICO；APFS 系统更新快照使 `df` 未立即回收对应物理空间。没有删除源码、正式制品、签名材料、测试报告或用户文件。

---

## 维护规则（当前生效）

- 只记录输入法项目，不写其他项目记录。
- 每条记录固定包含：主题、过程、修改、验证、待办。
- 新增内容按时间追加，不覆盖上一条历史。
- 根目录 `cl.md` 是唯一变更日志，项目目录不再保留重复副本。

---

## V1.67 - 2026-09-17

### 主题
修复 KEMI 扩展桌面模式下键盘的跨屏切换按钮无效。

### 过程
- 75 真机复现：KBoard 自带切屏按钮只移动输入法窗口，KEMI 的扩展屏键盘宿主没有同步切换；过早关闭旧宿主或重复刷新 IME 令牌会使目标屏不显示键盘。
- 扩展模式的编辑器通过 `privateImeOptions` 标记，KBoard 只在此模式发出受签名权限保护的有序广播；KEMI 确认自己是当前扩展会话后切换宿主。普通单屏和其他应用仍使用原切屏路径。

### 修改
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`：扩展模式先通知 KEMI，再按原系统方法移动 IME 令牌。
- `fcitx5-android/app/src/main/AndroidManifest.xml`：声明签名级广播权限。

### 验证
- 按既有 Release 流程构建成功，覆盖安装到 75；未清数据，默认输入法保持 KBoard。
- 与 KEMI 1.4.126+269 配合，副屏到主屏、主屏到副屏连续 5 轮共 10 次切换通过；每次目标显示的 IME 令牌与可见状态一致。收回扩展后，KEMI 恢复原 `KeyboardProxyActivity` 单屏键盘路径。

### 待办
- 本条仅证明 75 上的扩展键盘切屏和单屏路径回归；100 轮完整扩展/收回耐久及跨平台验收仍需单独结果。

---

## 2026-09-18：当日物理屏键盘改造总结、架构边界与风险追踪

### 最终需求与验收口径

- KEMI 远程会话保持原视频 Activity、编辑焦点和 HDMI Surface，不通过透明 Activity 或新的全屏显示层抢占目标屏；KBoard 在指定物理屏底部显示原有完整 `InputView`。
- 普通、数字、符号、拼音候选和桌面全键盘继续共用原组件与原布局。跨屏只迁移承载窗口并重建 View，不复制第二套键盘定义。
- Overlay 上的文字、退格、Enter、方向键、组合键、HOME/BACK、鼠标移动和鼠标键必须回到发起会话；普通应用输入必须继续走 Android `InputConnection`，两条输出通道不得串线。
- 按键音只在动作真正被接受并分发后播放。误触、滑出取消、空白触点、重复 DOWN 或被策略拒绝的手势不得播放按键音。水族与触控板刷新率降为 24Hz，并缓存静态键盘行以降低持续合成和 CPU/GPU 占用。
- 63 上的 `56445eaf...` 得到用户“初步测试可以”的现场结论。75 已完成 100 轮 Overlay 打开/关闭及 200 次跨屏切换，但这只覆盖窗口与会话生命周期；全部真实输入、组合键和长期资源曲线仍未完成，不能把本条写成全面验收通过。

### 当前有效架构

1. `KBoardOverlayService` 是受签名权限保护的 Binder 入口。它同时校验调用 UID、`com.newlinksz.kemi.remote` 包名和允许的签名指纹，管理唯一 `requestId/sessionId` 所有权、调用方死亡、目标屏移除、窗口附着、切屏与关闭回调。
2. 服务使用目标物理 Display 的 `createDisplayContext()` 和 `WindowManager` 添加 `TYPE_APPLICATION_OVERLAY`。窗口透明、位于底部、`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`，仅键盘区域接收触摸；不创建 VirtualDisplay，不启动透明 Activity，不请求目标应用焦点。
3. `FcitxInputMethodService` 仍是唯一主输入法引擎和组件所有者。物理 Overlay 直接请求它为目标 Display 创建原始 `InputView + KeyboardWindow + KawaiiBar + DesktopKeyboard`，并补齐 `ViewTreeLifecycleOwner`；销毁必须显式调用 Overlay View 的释放路径，不能只依赖系统 IME Window 的 detach 回调。
4. 每个 `InputView` 携带可空的 `overlayRequestId`。`CommonKeyActionListener`、语音提交和剪贴板提交以该编号选择输出路由：拥有有效物理会话时走 `KBoardOverlaySession` Binder 回传，否则走当前普通 `InputConnection`。异步 Fcitx 任务执行前后都必须重新核对所有权，避免关闭或换屏后的旧任务写入新会话。
5. 冷启动先短暂 `startService` 创建主 IMS，再用同进程内部 binding 托管初始化并立即清除 started-state。Android 输入法管理器真正调用 `attachToken` 后，`TokenReadyInputMethodService.onSystemImeAttached()` 通知 Overlay 释放临时 binding，由系统标准 `android.view.InputMethod` binding 接管生命周期。这样既支持从未唤起过普通键盘的全冷启动，也避免旧 IMS 跨越系统解绑后在下一次 `initializeInternal` 被重复初始化。
6. Overlay 自己模拟底部左右按钮和手势条，并让 Window 不适配真实系统导航 Insets；附着和跨屏后隐藏目标屏真实 NavigationBar，避免系统 96px 栏与内部 96px 栏叠加。关闭 Overlay 后由底层 Activity 重新控制系统栏。
7. 桌面模式与浮动模式运行时互斥。桌面模式强制键盘和预编辑区 `MATCH_PARENT`、清零浮动位移并隐藏浮动控制点；用户的浮动偏好值不被清除，退出桌面后按原偏好恢复。

### 当日失败实验及停止使用原因

| 方案/候选 | 现场结果 | 根因 | 后续禁止事项 |
| --- | --- | --- | --- |
| 私有 VirtualDisplay + Relay Activity | token 留在 D0、键盘压缩，随后出现壁纸镜像、黑屏和 HDMI Activity 退出 | V900 厂商合成器把全高和短高 VD 都纳入镜像层；可信显示和 served-view 时序也不能保证物理屏本地 IME token | 不恢复 VD、trusted display policy、Relay Activity 或 52% 短画布路径 |
| 透明 Activity 直接放物理屏 | 会改变焦点/任务栈，反向模式和 HDMI 页面有被抢占风险 | Activity 天然参与焦点、生命周期和输入法 served-view 仲裁 | 物理 Overlay 冷启动和显示都不得依赖 Activity |
| Service 直接新造简化键盘 | 原布局、候选、数字/符号、KawaiiBar 和状态恢复缺失 | 绕过完整 `InputView` 组件树 | 不维护第二套桌面键盘或第二套高度公式 |
| 只 `startService` 冷启动 IMS | 首次可打开，但系统解绑/重绑后崩溃 `onInitialize can be called only once` | started-state 让旧 IMS 在系统解绑后继续存活 | started-state 必须立即清除，生命周期只由临时 binding→系统 binding 交接 |
| 内部 binding 保持到 Overlay 关闭 | 仍可能跨越系统 IME 解绑，重复初始化风险未消失 | 临时 binding 与系统 binding 没有明确交接点 | 以系统 `attachToken` 为唯一交接事件；不得覆盖 final `onBind()` 猜测绑定来源 |
| 只改导航区颜色 | D0 仍有双层 96px 导航区，键盘整体上移 | 真实 `NavigationBar0` 和 Overlay 内部栏同时占空间 | 颜色不能代替 Window frame/Insets 验证 |
| 浮动偏好直接参与桌面布局 | DesktopKeyboard 宽度变成约 807px 并位于屏幕中间 | `updateFloatingKeyboardLayout()` 在桌面模式仍写宽度、位移和 outline | 所有浮动几何都必须先排除 `desktopKeyboardMode` |
| 在原始 `ACTION_DOWN` 播放水滴声 | 误触、滑出取消和未分发手势也会有声音 | 声音早于动作接受结果 | 音效只挂在 `notifyAcceptedAction/onAcceptedActionFeedback` 后 |

### 可追踪回归清单

| ID | 触发步骤 | 必须满足 | 明确失败判据 | 主要源码 |
| --- | --- | --- | --- | --- |
| KB-OVL-001 冷启动 | 强停/重启后，不先打开普通输入框，直接从 KEMI 打开键盘 | 一次进入 ready，原 UI 完整，HDMI 帧继续 | `REASON_START_FAILED/READY_TIMEOUT`、黑屏、Activity 被切走 | `KBoardOverlayService.kt`、`FcitxInputMethodService.kt` |
| KB-OVL-002 系统接管 | Overlay 冷启动后在普通应用唤起、隐藏、解绑并再次唤起系统 IME | 只存在当前 IMS generation；普通键盘可输入 | `onInitialize can be called only once`、旧 PID/旧 View 继续响应 | `TokenReadyInputMethodService.kt`、`LifecycleInputMethodService.kt`、`KBoardOverlayService.kt` |
| KB-OVL-003 跨屏 | D0/D2 各打开，点击切屏，重复往返 | 每次只有一个 Overlay window；模式和布局名恢复；旧屏立即移除 | 双窗口、旧屏残影、输入仍发旧 session、HDMI 停帧 | `KBoardOverlayService.kt`、`KBoardOverlaySession.kt`、`KeyboardWindow.kt` |
| KB-OVL-004 输出隔离 | Overlay 与普通应用轮流输入中文、英文、退格、Enter、剪贴板、语音 final | Overlay 事件只回 KEMI；普通应用只收自己的 InputConnection | 串字、关闭后迟到提交、普通应用使 Overlay 输入失效 | `CommonKeyActionListener.kt`、`FcitxInputMethodService.kt`、`KawaiiBarComponent.kt` |
| KB-OVL-005 触摸 Scope | 打开 Overlay 后快速普通/桌面/符号切换、切屏、关闭，同时滑动键盘 | 无空 lifecycle scope，无 detached View 回调 | `CustomGestureView` scope 崩溃、WindowLeaked、关闭后仍响应 | `InputView.kt`、`BaseInputView.kt`、`CustomGestureView.kt` |
| KB-OVL-006 底栏 | D0/D2 普通与桌面首开及往返切换 | 始终只有一层底栏；左右按钮和手势条可见可点；背景随模式一致 | 白/灰空带、双导航栏、主体少 96px、关闭后系统栏不恢复 | `PhysicalOverlayWindowPolicy.kt`、`InputView.kt`、`KBoardOverlayService.kt` |
| KB-OVL-007 浮动互斥 | 先开启浮动键盘，再进入桌面；退出桌面 | 桌面全宽且 x=0；退出后恢复原浮动偏好 | 桌面约 807px 居中、残留 translation/outline/控制点 | `InputView.kt`、`DesktopKeyboardModeState.kt` |
| KB-OVL-008 功能键与音效 | 点击有效键、滑出取消、空白误触、长按修饰键、Enter、语音 | 只有真实接受动作发声；Enter/语音不重复发声；修饰键边沿成对 | 误触有声、一键双声、卡住 Ctrl/Alt/Cmd/鼠标键 | `InputFeedbacks.kt`、`BaseKeyboard.kt`、`DesktopKeyboard.kt`、`KeyAction.kt` |
| KB-OVL-009 桌面组合键 | 测 Ctrl/Alt/Command+字母/空格、Shift/Caps、F1-F12、方向、HOME/BACK | 主键与修饰状态完整回传；中文无修饰仍走 Fcitx | 只有修饰键没有主键、Space 被长按逻辑吞掉、Caps 大小写错 | `DesktopKeyPolicy.kt`、`DesktopKeyboard.kt`、`FcitxInputMethodService.kt` |
| KB-OVL-010 鼠标与视频焦点 | 移动、左右中键、切屏、关闭时按住按钮，同时观察 HDMI frameindex | 鼠标事件归当前 session；关闭补 UP；视频 Activity 一直 RESUMED 且帧增长 | 事件回灌、卡键、跨 session、portui pause/stop、帧停止 | `DesktopTouchpadView.kt`、`CommonKeyActionListener.kt`、`KBoardOverlaySession.kt` |
| KB-OVL-011 24Hz 资源 | 桌面水族与触控板持续运行，普通模式/隐藏后再采样 | 活动目标约 24Hz；隐藏后停止；静态键行缓存仅在 attach 期间存在 | 仍约 30/60Hz、隐藏后持续绘制、GPU layer 未释放、PSS 线性增长 | `DesktopAquariumView.kt`、`DesktopTouchpadView.kt`、`DesktopKeyboard.kt` |
| KB-OVL-012 关闭原因 | 隐藏、调用方死亡、Display 移除、普通输入接管、服务销毁 | 每次只回调一次正确 reason，释放窗口/按键/Context | 重复回调、遗留窗口、普通输入误关新会话 | `KBoardOverlayService.kt`、`OverlayRequestPolicy.kt` |

### 已验证与未验证边界

- 已验证：Overlay 定向 JVM 测试、Debug Kotlin 编译、Release/R8/lint 构建、`git diff --check`、APK v1/v2/v3 签名和固定证书；63 安装包 SHA 与 `56445eaf...` 一致，用户反馈初步可用；75 完成 system UID 安装链迁移，并完成 100 轮 Overlay 打开/关闭及 200 次跨屏切换，窗口与会话生命周期未见失败。
- 尚未完整验证：真实语音、剪贴板、全部中英文候选、所有组合键与鼠标按钮；系统 IME 多次解绑重绑；D0/D2 系统栏恢复；HDMI 长时间 frameindex；CPU/GPU/PSS 长期曲线。已有一次组合键自动化运行因 D0 上 KEMI 抢走接收器焦点，无法证明事件进入真实 `InputConnection`，该次结果作废且不得计为通过。
- 自动化工具已补齐：`tools/real-input-receiver` 是独立真实 `InputConnection` 接收器，只记录按键边沿、meta、repeat、时间、Display 和 accepted；commit/composing 只记录长度，不记录文本。`tools/real-input-injector` 是另一独立包的 `UiAutomation.injectInputEvent` instrumentation，按参数发送 DOWN→有序 POINTER_DOWN→逆序 POINTER_UP→UP，支持 display、pointer id/坐标、hold、pointer step 和 rounds，并输出机器 JSON。两包已在 ORICO 构建成功，源码和命令见 `tools/real-input-harness.md`。Android 12 首版因直接调用 `InputEvent.setDisplayId` 不兼容而失败，现已改为兼容查找并生成平台签名包；下一次必须先做单键正控、确认接收器保持 served view、Display 与坐标正确，再进入 100 轮组合门禁。
- 追随规则：发现问题时先记录回归 ID、候选 SHA、设备/Display、触发序列、窗口 frame、PID/IMS generation、HDMI Activity 状态和过滤日志，再修改源码；修复后重跑该 ID 与相邻的输出隔离、底栏、跨屏三组，不以截图正常替代输入和生命周期验证。

### 75 安装链差异与本次处理结论（后续部署前仍需核对）

- 75 处理前的 `com.newlink.kemi.kboard` 是普通应用 UID 10049，同时存在 `/system/app/KBoard` 系统底包和 `/data` 更新层；带 `android:sharedUserId="android.uid.system"` 的 `56445eaf...` 直接 `install -r` 会被 `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` 拒绝。这是安装身份不兼容，不是 APK 损坏或签名验证失败。本次已完成迁移并验证为 system UID 安装链，后续升级仍须保持同一 shared UID 和平台证书。
- 63 本轮没有可证明的“普通 UID 迁移到 UID 1000”操作。可证明事实只有：遗漏 sharedUserId 的首个候选被拒绝；补回 Release manifest 的 sharedUserId 后覆盖成功并保持 UID 1000。这说明 63 在本轮开始前已经处于 system UID 升级链。
- `.62` 历史上的 `adb remount`/overlayfs 删除 `/system/app/KBoard` 流程针对旧包 `org.fcitx.fcitx5.android`，最终安装的是 `/data/app` 普通 UID 版本；它不能作为 75 迁移到 UID 1000 的依据。63 后来的“卸载错误证书数据包再装平台签名包”记录也没有 shared UID 迁移证据。
- 本次 75 迁移已经完成，但迁移后只恢复主 IME 时遗漏了同包 `DisplaySwitchInputMethodService`：首次点击扩展键盘切屏出现 `Display-switch IME relay is not enabled`。补执行同包 relay 的 `ime enable` 后只能证明系统 IME token 短暂切到 D0；KEMI 没有收到扩展键盘切屏回调，也没有重建 host，Overlay `requestId=11` 仍为 `source=0,target=2,visible`，因此不得判定反向切屏成功。后续点击还可能穿透到 KEMI toolbar 并触发 `projection_stopped`。
- 静态复核确认回归根因：主仓成熟实现的 `toggleImeDisplay()` 仍会识别 `privateImeOptions == com.newlinksz.kemi.remote.EXPANDED_KEYBOARD`，向 KEMI 发送 `SWITCH_EXPANDED_KEYBOARD` 有序广播，等待 `DualScreenKeyboardSwitchReceiver` 迁移编辑器/host 后再切系统 IME token；生成 `56445eaf...` 的 Overlay 工作树遗漏了这整个分支，只保留 ROM relay 路径。修复必须最小合回成熟分支，不能整体覆盖并丢失 Overlay 生命周期修复；回归必须同时验证 KEMI 收到广播、host 目标屏改变、Overlay request 更新、截图和连续窗口状态，不能再只看 `mCurTokenDisplayId` 或瞬时 `shown=true`。
- 后续部署检查必须同时确认主 IME 和 relay 已启用，不能只检查默认输入法；该检查只能保证 relay 可调用，不能替代 KEMI 扩展模式协议验证。
- 后续更换设备或重做系统时仍必须先备份设置和 `/data` APK，并只读核对系统底包与更新层各自的包名、manifest sharedUserId、证书、版本、UID 和用户安装状态。禁止手工修改 `packages.xml`；不能把 75 的成功直接推定为其他设备也可无损迁移。

---

## 2026-09-18 - 改动记录专项 JVM 回归 100 遍

- 对照近期记录和当前未提交的 KEMI 扩展键盘切屏修复，按三组建立专项门禁：IME 生命周期（迟到光标、销毁后排队布局、显示门控、Android 12 框架兼容）、跨屏路由（扩展键盘策略、系统 relay 管理）、桌面输入（异常硬件键过滤、组合键策略）。
- 当前扩展模式判断原本直接耦合在 `FcitxInputMethodService`，现抽出 `ImeDisplaySwitchPolicy` 并新增 `ImeDisplaySwitchPolicyTest`。测试先因策略入口不存在而编译失败，再以最小实现转绿；扩展 KEMI 标记走宿主协调切屏，普通、空值和相似但不相等的标记保持系统切屏路径。
- 100 轮从 2026-09-18 18:22:58 至 18:29:32（Asia/Shanghai）连续运行。每轮强制重跑上述 8 个测试类，100/100 轮通过，合计 800 个测试类轮次；100 份 Gradle 日志均包含 `BUILD SUCCESSFUL`，没有 `BUILD FAILED` 或失败标记。
- 测试期间源码指纹始终为 `6b4b933e7be19e3362a76d0036fcb49bb28c679e5c1a23f0aaddae761c9f911f`。汇总、逐轮时间和日志保存在 `/Volumes/ORICO/kemi-build-cache/app-release-gate/kboard/20260918-change-regression-100/`。
- 本轮证明纯策略、生命周期防护和按键路由的 JVM 稳定性；当前没有在线 ADB 设备，因此不把它计作扩展键盘真实宿主迁移、双屏窗口、截图、HDMI 视频连续性、CPU/GPU/PSS 或 100 轮真机耐久通过。


## 2026-09-21 - 剪贴板候选发送后清除（候选，待实机闭环）

- 用户报告全局键盘发送剪贴板后候选持续存在。现有点击只清当前视图的 isClipboardFresh；进入全局模式从 Room 重载最新记录时，又将同一条设为新候选。
- ClipboardManager 以记录 id + 复制时间保存候选已消费状态，通知各键盘视图同步清除；保留系统剪贴板和完整历史。新复制事件时间变化，仍可重新显示同一段文本。
- KawaiiBarComponent 从实际显示的记录发送，避免 Room 恢复候选与进程 lastEntry 不一致；点击立即清除文字、超时任务和候选状态，重开时过滤已消费条目。未改输入路由、键盘布局、语音、导航和视频层。
- 待验收：可见候选发送并清除、隐藏重开不回填、跨屏后不回填、新复制可显示并发送、历史保留；未经真机验证不标记通过。
- 首次正式构建 Kotlin/R8 已执行，但 lint 在未修改的 KBoardOverlayService.kt 中遇到 Unexpected owner function 内部异常；保留日志 /private/tmp/kboard-clipboard-consumed-build.log，使用 --no-daemon 重跑相同门禁，不跳过静态检查。

### 本次候选实际结果

- 独立构建重试通过：BUILD SUCCESSFUL，287 tasks，含 lintVital；APK 1.4.1+182，SHA256 `15b6d693e55a6b5e85bf1f3078f60fe59e2182fed594175a67a65478a0713ced`，v1/v2签名与原平台证书一致。63 install -r 返回 Success。
- 63 主屏便签搜索框 + 全局键盘：发送可见候选后候选消失，收起键盘后搜索框确有接收文本；重开全局键盘后候选保持空白。各 1 次真实验证，不能冒充 10 次。
- 证据：/private/tmp/clipboard-installed-keyboard.png、clipboard-sent.png、clipboard-target-after.png、clipboard-reopened-settled.png。
- 后续新复制测试期间设备切换到远程桌面，场景不再是便签，此轮无效；新复制、跨屏反复与服务重启仍待验收，不计为通过。未更改或删除系统剪贴板及历史数据。


## 2026-09-21 两项目云端备份汇总（源码快照，非发布）

- 对应远程办公详细清单：rust-desk 的 kemi-docs/SOURCE-BACKUP-20260921.md。KBoard 继续使用既有 main，不创建备份分支，不强推。
- 本轮包含：跨屏后本地隐藏/HOME、鼠标 BACK 来源隔离、本地系统导航成对注入、全局剪贴板最新条目恢复/可见配色/已消费过滤、当前全局 Overlay 高度修正及对应策略测试。
- 全局高度修正目前出现在工作树中，不能据此前剪贴板候选的单次实测认定它通过。合并云端商场更新入口和版本变更后，重新冻结双 APK 并执行每适用变体十次验收。
- 上游 libime 与 Chinese addons 本地差异保存在 patches/source-backup-20260921/*.patch，子模块指针保持不变。应用前必须 git apply --check；未将 .DS_Store、prebuilt 缓存或旧 bin 文件删除纳入提交。
- 最新已实测剪贴板候选 1.4.1+182 SHA256 15b6d693e55a6b5e85bf1f3078f60fe59e2182fed594175a67a65478a0713ced；已证实本地发送清除与重开不回填各1次。十次矩阵未完成，整体仍 BLOCK。

### 本次备份文件清单

- `cl.md`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/data/clipboard/ClipboardManager.kt`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/data/clipboard/db/ClipboardDao.kt`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/CrossDisplayNavigationObserver.kt`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/DesktopNavigationHideBridge.kt`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/SystemMouseInjector.kt`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/bar/KawaiiBarComponent.kt`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/bar/ui/idle/ClipboardSuggestionUi.kt`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/overlay/KBoardOverlayService.kt`
- `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/overlay/PhysicalOverlayWindowPolicy.kt`
- `fcitx5-android/app/src/test/java/org/fcitx/fcitx5/android/input/overlay/PhysicalOverlayWindowPolicyTest.kt`

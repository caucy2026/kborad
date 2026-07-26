# 2026-07-26 变更记录（ASR + 图标）

## 1. 记录范围
- 仓库：fcitx5-android
- 记录时间：2026-07-26
- 记录类型：工作区未提交改动快照（用于备份与回溯）

## 2. 变更总览
- 变更文件：9
- 统计：247 insertions, 78 deletions
- 当前状态：全部为未提交修改（modified）

## 3. 文件清单
1. app/src/main/AndroidManifest.xml
2. app/src/main/java/org/fcitx/fcitx5/android/input/bar/KawaiiBarComponent.kt
3. app/src/main/java/org/fcitx/fcitx5/android/input/bar/ui/IdleUi.kt
4. app/src/main/java/org/fcitx/fcitx5/android/input/bar/ui/ToolButton.kt
5. app/src/main/res/drawable/ic_launcher_foreground.xml
6. app/src/main/res/drawable/ic_launcher_foreground_debug.xml
7. app/src/main/res/drawable/ic_launcher_foreground_monochrome.xml
8. app/src/main/res/drawable/ic_launcher_foreground_monochrome_debug.xml
9. app/src/main/res/values/strings.xml

## 4. 关键改动说明

### 4.1 Manifest 与权限链路
- 新增权限：RECORD_AUDIO、INTERNET、ACCESS_NETWORK_STATE
- application 增加 networkSecurityConfig 绑定：@xml/network_security_config
- 新增 VoicePermissionActivity 声明（exported=false）

### 4.2 ASR 交互与风控（KawaiiBarComponent）
- ASR 按键改为按住阈值触发（VOICE_PRESS_TO_START_MS）
- 未授权时增加中文提示并对权限页弹出做节流（VOICE_PERMISSION_REQUEST_COOLDOWN_MS）
- 无网预检查：未联网直接提示并不进入 ASR 录音流程
- Move/短按取消：避免误触发录音
- 错误中文化：网络策略拦截、无网、权限、参数缺失、麦克风异常
- 网络状态读取增加异常兜底，避免 SecurityException 直接崩溃

### 4.3 ASR 按钮视觉态（IdleUi + ToolButton）
- ToolButton 新增图标着色接口 setIconTintColor
- ASR active 时语音图标改为系统绿（0xFF34C759）
- 退出 active 后恢复默认图标色

### 4.4 文案本地化（strings）
- ASR 状态文案改为中文：连接中、听写中、收尾中
- 新增中文风控提示：
  - 按住说话提示
  - 权限提示
  - 网络安全策略拦截提示
  - 未联网提示
  - 网络权限异常提示
  - 参数缺失提示
  - 麦克风不可用提示

### 4.5 Launcher 图标重绘
- debug/release + monochrome 四套资源同步修改
- 从旧样式改为 MIC + 键盘结构
- 包含多轮位置微调（键盘与 MIC 纵向位置）

## 5. 已记录问题与修复
- 问题：按 ASR 崩溃（ConnectivityService: missing ACCESS_NETWORK_STATE）
- 原因：联网检查调用 activeNetwork 时缺少 ACCESS_NETWORK_STATE
- 修复：Manifest 补权限 + isNetworkAvailableForVoice 加异常保护

## 6. 备份/回滚建议
- 建议先将本文件与当前 diff 一起备份
- 若需快速回滚语音交互改动，优先回退：
  1. KawaiiBarComponent.kt
  2. AndroidManifest.xml
  3. strings.xml
- 若只回滚视觉，回退四个 ic_launcher_foreground*.xml 与 IdleUi/ToolButton 的语音按钮配色逻辑

## 7. 备注
- 系统绿色麦克风隐私指示器由 Android 系统控制，应用无法主动关闭；本次仅通过“按住阈值 + 取消策略”降低误触发概率。

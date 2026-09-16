# KBoard 旧 IME Session 修复：20 轮真机回归

- 日期：2026-09-16；设备：172.21.16.24:5555，H730 Android 12。
- 测试包：1.4.2 / 162，system UID，平台签名；上一轮覆盖安装并校验的 SHA256 为 B36B9330950B6A9B580E7A99ED3AA1E127614787AB115187A2D457496CA6D5C9。
- 执行：`python fcitx5-android/scripts/test-late-session-20.py`，退出码 0。

## 路径和结果

每轮打开便签搜索框，轮询确认 KBoard 显示，点击屏幕键盘 q 与退格，收起键盘；交替切换系统设置、KEMI 浏览器，再切换同包 DisplaySwitchInputMethodService 与主 FcitxInputMethodService，重新打开便签搜索框并确认键盘显示。

| 检查 | 结果 |
| --- | --- |
| 完整循环 | 20/20 |
| 切换前后键盘显示 | 40/40 |
| IME 服务释放日志 | 36 次 |
| KBoard PID | 始终为 10501，无变化 |
| KBoard FATAL | 0 |
| FcitxDaemon$DisconnectedException | 0 |
| Required value was null | 0 |
| KBoard ANR | 0 |
| 默认输入法 | 测试后恢复为原主输入法 |

第一轮和第二十轮截图均确认 q 实际进入搜索框；最终截图确认退格后搜索框清空、键盘仍显示。未新建或删除便签，没有卸载或清除应用数据。

## 结论与覆盖边界

本次 20 轮定向回归通过，输入、应用切换及输入法服务重建场景未复现客户两类崩溃。结合上一轮迟到回调 JVM 失败对照与修复后测试通过，可支持当前修复的有效性。

KEMI 远程在测试准备期间持续停在启动画面，重启后亦未进入设备号页面，因此本轮按客户报告的“任意应用输入、应用切换、输入法切换”使用便签执行，没有完成 KEMI 远程专属输入路径。未强制制造内存压力或进程回收，未执行五小时随机 Monkey；设备日志没有证明每轮均发生 onDestroy 后的迟到光标回调，不能把20轮通过等同于所有竞态彻底消除。

证据：`summary.json`、`logcat.txt`、40 份逐轮 IME dumpsys、`01-typed.png`、`20-typed.png`、`final-keyboard.png`。

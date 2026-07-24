# 讯飞 ASR 接口对接说明（可复用）

本文档描述本仓库**实际接入**的讯飞语音听写（ASR）链路，供其他 Android / 业务项目复用。

- **范围**：从系统级缓存读取讯飞参数 → HTTP 鉴权 → WebSocket 流式听写 → 两种业务用法（按住说话 / 自动聆听）
- **不含**：TTS（合成）、未使用的场景（粤语/英文 scene、Opus 编码等）
- **MAC / auth_id 细节**：见同目录 [`讯飞WiFi-MAC获取说明.md`](./讯飞WiFi-MAC获取说明.md)

---

## 0. 能力分层（先选模式）

对接前先明确业务交互，再选参数：

| 模式 | 典型 UI | 谁决定「一句话结束」 | `cloud_vad_eos`（本项目取值） |
|------|---------|----------------------|------------------------------|
| **按住说话** | 按下开麦 → 松手发送 | **客户端**：松手发 `--end--` | `60000`（拉长服务端 VAD，避免按住期间被断句关会话） |
| **自动聆听** | 开窗即开麦，说完自动提交 | **服务端云端 VAD** | `3000`（约 3 秒静音结束） |

两种模式共用同一套 HTTP 鉴权 + WebSocket 协议，差异主要在：

1. WebSocket `param.cloud_vad_eos`
2. 是否在松手时主动发送 `--end--`
3. 按住期间如何处理服务端的 `is_finish`（见第 5 节）

---

## 1. 总体调用顺序

每次听写会话建议按固定顺序：

```
① 从 Settings.Global 读取讯飞参数缓存（其中包含设备 WiFi MAC 等信息）
② 使用读取到的参数执行 HTTP POST 鉴权（license=token）
③ 用 app_id + api_key 签名，连接 WebSocket
④ 收到 action=started 后再开麦、推 PCM
⑤ 持续 send 二进制音频帧
⑥ 结束：发 "--end--" 或等待云端 VAD
⑦ 收 Final 文本 → 关连接
```

对外集成时可按以下职责拆分：

| 步骤 | 说明 |
|------|------|
| ① | 从 Settings.Global 读取讯飞参数缓存，其中应包含 WiFi MAC 等信息 |
| ② | 执行 HTTP 鉴权，拿到 `license` |
| ③～⑦ | 按协议建立 WebSocket、推送 PCM、处理结果流 |

其他项目可只复用协议与第 5、6 节的状态机，不必依赖任何特定源码结构。

---

## 2. 凭证来源：Settings.Global

在鉴权与 WebSocket 之前，需要 `token` / `app_id` / `api_key`。其他应用只需要从系统级全局设置读取，不需要自己从服务器申请。

### 2.1 读取位置

| 项 | 值 |
|----|-----|
| 读取方式 | `Settings.Global.getString(context.contentResolver, "iflytek_params")` |
| 数据格式 | JSON 字符串（完整参数，含 `token` / `app_id` / `api_key` 等） |

### 2.2 读取到的字段

读取到的 JSON 中应包含：

- `token` → 后续 HTTP 鉴权的 `license`
- `app_id` → WebSocket `appid`
- `api_key` → 用于计算 `checksum`（**不要**放到 WS URL 明文以外的用途）
- `auth_id` → 由 MAC 计算得到的鉴权 ID
- `wifi_mac` → 当前设备 WiFi MAC
- `sn` / `system_version` → 其余鉴权或上报信息

> 其他应用直接从 Settings.Global 读取参数并使用即可；如需配合设备身份，可参考 [`讯飞WiFi-MAC获取说明.md`](./讯飞WiFi-MAC获取说明.md) 里的 MAC 规则。 

---

## 3. 会话前鉴权（HTTP）

每个 ASR 会话开始前调用一次；成功后再连 WebSocket。

| 项 | 值 |
|----|-----|
| URL | `http://api.voice.gskiot.com/voice-api/voice/auth` |
| 方法 | `POST` |
| Content-Type | `application/json; charset=utf-8` |

### 请求体示例

```json
{
  "xiriSn": "1C:79:2D:02:F6:2D",
  "license": "<token>",
  "channel": "NEWLINK01",
  "devicewifiMac": "1C:79:2D:02:F6:2D",
  "deviceMac": "1C:79:2D:02:F6:2D",
  "sn": "QUALMETA-1C:79:2D:02:F6:2D",
  "clientVersion": "V1.0.1.2:2026-06-02:V1.4.4",
  "timestamp": "1780889790077"
}
```

### 字段说明

| 字段 | 说明 |
|------|------|
| `xiriSn` / `devicewifiMac` | WiFi MAC，**大写**、冒号分隔 |
| `license` | 申请接口返回的 `token` |
| `channel` | 本项目固定 `NEWLINK01`（其他项目按渠道约定） |
| `deviceMac` | 本项目与 WiFi MAC 相同；若有独立有线 MAC 可分开传 |
| `sn` | 本项目：`"QUALMETA-" + WIFI_MAC`（大写） |
| `clientVersion` | 客户端版本字符串 |
| `timestamp` | **毫秒**时间戳字符串 |

### 成功判定

```json
{
  "code": "00000",
  "data": { "status": 0, "msg": "成功" }
}
```

需同时满足：`code == "00000"` 且 `data.status == 0`。

---

## 4. 流式听写（WebSocket）

| 项 | 值 |
|----|-----|
| URL | `ws://wsapi.xfyun.cn/v1/aiui` |
| Origin | `http://wsapi.xfyun.cn` |
| 签名类型 | `signtype=sha256` |

### 4.1 握手 URL

```
ws://wsapi.xfyun.cn/v1/aiui?appid=<appid>&checksum=<checksum>&curtime=<curtime>&param=<paramBase64>&signtype=sha256
```

计算步骤：

1. `curtime` = 秒级时间戳：`System.currentTimeMillis() / 1000`
2. 构造 `param` JSON（见下），UTF-8 → Base64 → `paramBase64`
3. `checksum` = `SHA256(apiKey + curtime + paramBase64)` 的**小写十六进制**
4. 发起 WebSocket；Header 带 `Origin: http://wsapi.xfyun.cn`

### 4.2 `param`（本项目实际发送）

```json
{
  "result_level": "plain",
  "auth_id": "<MD5(wifiMac去冒号小写)>",
  "data_type": "audio",
  "aue": "raw",
  "scene": "main",
  "sample_rate": "16000",
  "dwa": "wpgs",
  "cloud_vad_eos": "60000"
}
```

| 字段 | 本项目取值 | 说明 |
|------|------------|------|
| `auth_id` | 由 MAC 计算 | **不要**写死示例串；算法见 MAC 说明文档 |
| `aue` | `raw` | 未压缩 PCM |
| `scene` | `main` | 普通话主场景 |
| `sample_rate` | `"16000"` | 与采集一致 |
| `dwa` | `wpgs` | 动态修正，便于流式 Partial |
| `cloud_vad_eos` | 见下表 | 云端静音判停（毫秒字符串） |

| 业务模式 | `cloud_vad_eos` | 含义 |
|----------|----------------|------|
| 按住说话 | `"60000"` | 按住期间几乎不靠 VAD 关会话，等客户端 `--end--` |
| 自动聆听 | `"3000"` | 约 3 秒静音由服务端结束一句 |

### 4.3 音频帧

| 项 | 本项目 |
|----|--------|
| 格式 | 16 kHz、单声道、16-bit PCM（小端） |
| 发送 | WebSocket **二进制帧**（非 JSON） |
| 分片 | 约 **3200 字节 / ~100 ms**（`16000×2×0.1`） |
| 开麦时机 | 收到服务端 `action: "started"` **之后**再 `AudioRecord.startRecording` |

结束会话时发送 ASCII：

```text
--end--
```

对应字节：`[45, 45, 101, 110, 100, 45, 45]`。

### 4.4 服务端下行（本项目实际使用的字段）

```json
{
  "action": "result",
  "code": 0,
  "data": {
    "text": "今天天气真好",
    "is_finish": false,
    "is_last": false
  }
}
```

| 字段 | 用法 |
|------|------|
| `code` | `≠0` → 会话错误 |
| `action` | `started`：可开麦；`result`：识别结果 |
| `data.text` | 当前识别文本（可能为空） |
| `data.is_finish` | 云端认为「一句/一段」结束 |
| `data.is_last` | 本连接最后一包；可关 WebSocket |

本项目**未依赖**：`sid`、`data.sub`、`result_id`、`json_args` 等（可记日志，非协议必需）。

### 4.5 结果语义建议

对接层建议抽象成三种事件（便于 UI 解耦）：

| 事件 | 条件（示意） |
|------|----------------|
| `Partial(text, segmentEnd)` | 有文本；`segmentEnd = is_finish`（按住模式多段拼接用） |
| `Final(text)` | `is_finish \|\| is_last`，且业务允许结束 |
| `Error(msg)` | `code ≠ 0` 或鉴权/网络失败 |

---

## 5. 按住说话（Hold-to-Speak）——推荐实现

目标：手指按下开始识别，**松手才提交**；按住过程中可显示实时字幕，中途 VAD 断句**不能**误触发「发送」。

### 5.1 状态建议

```
IDLE
  │ 按下
  ▼
HOLDING（开麦 + 显示「松手发送」）
  │ 松手且有效
  ▼
WAITING_FINAL（停麦 + 发 --end-- + 等 Final）
  │ 收到 Final / 超时
  ▼
IDLE（把文本交给业务：发 LLM、写入聊天等）
```

无效松手示例：按住不足 1 秒、上滑取消 → 不发 `--end--` 的「提交」，直接取消会话。

### 5.2 参数与开关

```
holdOpenUntilEnd = true
autoListenMode   = false
cloud_vad_eos    = "60000"
```

### 5.3 时序

```
用户按下
  → HTTP 鉴权 + WS 连接
  → 收到 started → AudioRecord 开麦
  → 循环 sendAudio(PCM chunk)
  → 服务端可能多次 is_finish=true（云端分段）
       ※ 按住未松手：只当 Partial(segmentEnd=true)，拼到本地缓冲区
       ※ 禁止当作 Final / 禁止关会话
用户松手（有效）
  → stop AudioRecord
  → send("--end--")
  → 等待 Final（建议超时 3～5 秒，超时可 Final("")）
  → close WebSocket
  → 业务使用最终文本
```

### 5.4 关键规则（易踩坑）

1. **按住期间丢弃过早的 Final**  
   即使服务端偶发终态包，只要还没松手、还没进入「等 Final」，就不要往业务层抛「可发送」的 Final。

2. **多段拼接**  
   当 `is_finish=true` 且仍在按住：把当前 `text` 视为一段结束，追加到「已确认文案」，下一段 Partial 再刷新「当前段」。  
   展示文案 = `已确认 + 当前段`。

3. **松手后才 sendEndFlag**  
   `--end--` 表示客户端主动结束本次听写；与 `cloud_vad_eos=60000` 搭配，避免「还在按住就被 VAD 关掉」。

4. **短按**  
   本项目：按住 &lt; 1s → 取消识别并提示「说话时间太短」，不进入 WAITING_FINAL。

5. **取消**  
   本项目：上滑超过阈值 → `cancel`（关麦、关 WS、不提交文本）。

### 5.5 伪代码

```text
onFingerDown:
  startSession(holdOpen=true, vadEos=60000)
  showListeningUI()

onPartial(text, segmentEnd):
  if segmentEnd: commitSegment(text) else refreshCurrent(text)
  updateSubtitle(committed + current)

onFingerUp(valid):
  if not valid: cancel(); return
  stopMic()
  sendEndFlag("--end--")
  wait Final (timeout 5s)
  submitToBusiness(finalText)

onFinal(text):  // 仅在已松手、waitingFinal 时处理
  submitToBusiness(text)
  teardownSession()
```

---

## 6. 自动聆听（可选）

目标：进入页面即开麦，用户说完一句自动提交，无需按住。

### 6.1 参数

```
holdOpenUntilEnd = false
autoListenMode   = true
cloud_vad_eos    = "3000"
```

### 6.2 行为差异

- **不**在客户端主动发 `--end--` 作为「用户松手」（本项目自动聆听路径依赖云端 VAD 出 `Final`）
- 收到 `Final` 且文本非空 → 交给业务；可选择是否立刻再开下一轮聆听  
  （本项目：开窗时触发一轮；空结果会有限次重试，**对话回合结束后默认不自动下一轮**）

### 6.3 与按住说话并存

若同一界面两种入口：自动聆听中用户按下「按住说话」，应先中断自动会话，再按第 5 节开手动会话。

---

## 7. 采集与权限（Android）

| 项 | 要求 |
|----|------|
| 权限 | `RECORD_AUDIO`；Android 9+ 后台采麦需符合前台服务类型（本项目浮窗使用麦克风 FGS） |
| `AudioRecord` | `MIC` / `16000` / `CHANNEL_IN_MONO` / `ENCODING_PCM_16BIT` |
| 开麦时机 | WebSocket `started` 之后，避免先灌无效音频 |

---

## 8. 其他项目最小接入清单

- [ ] 能稳定读到 WiFi MAC，并算出 `auth_id`
- [ ] 从 Settings.Global 读取 `iflytek_params`，并验证 `wifi_mac` 与当前设备一致
- [ ] 每次会话前 HTTP auth 成功
- [ ] WS 签名与 `param` 正确；`aue=raw` 与 PCM 一致
- [ ] 明确只用「按住」或「自动」之一，或按第 5/6 节切换
- [ ] 按住模式：`vad=60000` + 松手 `--end--` + 按住期不把 `is_finish` 当提交
- [ ] 自动模式：`vad=3000` + 以 `Final` 为提交点
- [ ] 错误码、鉴权失败、Final 超时有可恢复策略

---

## 9. 与旧版本文档的差异（避免照抄过时内容）

| 旧描述 | 本项目现状 |
|--------|------------|
| `auth_id` 写死示例 | 由设备 MAC 计算 MD5 |
| 默认 `cloud_vad_eos=1000` | 业务上只用 `60000` / `3000` |
| 建议 9600 字节 / 300ms 一帧 | 实现为约 3200 字节 / 100ms |
| 本文档含 TTS | TTS 已拆出，不在本文范围 |
| 文中附带某台设备真实 token | 仅作联调参考，**禁止**当通用密钥下发 |
| 需要自己申请讯飞参数 | 现在改为从 Settings.Global 读取，不再由其他应用自行申请 |

---

*文档版本：2026-07，按讯飞 ASR 协议与通用 Android 集成经验整理，面向跨项目复用。*

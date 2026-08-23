# KBoard 摸鱼水族键盘可复刻设计规范

> 文档状态：可交付 / 可复刻  
> 对应源码版本：`79a3f763`
> 目标设备基线：Android 12、arm64-v8a、Mali-G52、OpenGL ES 3.2、1920×1280  
> 目标效果：全局键盘下方是一整块沉浸式池塘；金鱼依靠尾鳍和胸鳍真实游动，触摸后手指下长出一簇跟手水草，松手立即消失；投喂时鱼群分为抢食、顺游、逆游和谨慎四种角色，松手轮换三种散场；空闲时会跟随、追逐、并游和交叉嬉戏；触点产生轻微非圆涟漪和一次真实水滴声；持续渲染稳定在 30Hz。

## 1. 怎样得到完全一致的效果

若项目也是 Android View + OpenGL ES，最可靠的复刻方法不是重新估算参数，而是复制下列源码与资源，再按第 3 节接入。本文后续章节解释每个参数为什么存在，便于移植到 Compose、Flutter Texture、Qt、Unity 原生插件或其他 GLES 容器。

| 文件 | 用途 | SHA-256（`79a3f763`） |
|---|---|---|
| `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/aquarium/DesktopAquariumView.kt` | EGL、30Hz 渲染线程、水面/水草/鱼体 Shader、个体速度、随机闪光、触摸角色、三种散场、按尺寸碰撞和水动力 | `40d9d8997ea849caa784f6dd71bb06f316e9778301b7a25f2ab81b76683dd3be` |
| `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/DesktopKeyboard.kt` | 水族层和原生按键层组合、触摸观察、底部水域、组合键提示和桌面功能键 | `5666323c4d2262b332d1345d123fa74745d94f2030b6c411ee0ac99467dbc48c` |
| `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyView.kt` | 半透明景深键帽、按压行程、无圆形 Ripple、稳定提示层 | `6e9a5bc1b48d04fe5539f702dfb381c5a45795fdadecf50dafc072ab4b10cd76` |
| `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyDefPreset.kt` | 全局修饰键的按住式定义 | `e6b8b09a074c47cab241dd6fef70a0f864e5268d438132786ab8c0c868a5751e` |
| `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/input/keyboard/KeyDrawable.kt` | 键帽分层渐变、描边和透明度 | 以同一提交为准 |
| `fcitx5-android/app/src/main/java/org/fcitx/fcitx5/android/data/InputFeedbacks.kt` | SoundPool 预加载、单次水滴播放和音量控制 | `eb3f55664b0ce829d131e946e82b4b59103b07c4141ae5260ad8c080a657d318` |
| `fcitx5-android/scripts/prepare-aquarium-water-touch.py` | 从原始 CC0 录音生成四个低延迟切片 | `50ee17f1488cb0f16ac853d45c908c22e5f65eb49d2f0e9a85d08d243091f47c` |
| `fcitx5-android/app/src/main/res/raw/aquarium_water_touch_1..4.wav` | 四个真实触水声音 | 见第 12 节 |

完全一致必须同时满足四点：

1. CPU 的推进、转向和 GPU 的尾鳍/胸鳍动作共用同一套相位及力度，不能让模型位移与鱼鳍动画各自运行。
2. 水族只观察触摸，原生按键仍独立命中和发送输入；渲染故障不能阻塞键盘。
3. 渲染固定 30Hz、内部最大宽度 1080；Android 字符和键帽仍按设备原生分辨率绘制。
4. `DOWN` 创建唯一手指水草、涟漪并播放一次声音；`MOVE` 让水草和鱼群目标跟手，`UP/CANCEL` 在下一帧完全移除水草并启动差异化散场。

## 2. 视觉和交互验收定义

空闲时 10 条鱼应有不同颜色、花纹、深度、体型和速度性格，覆盖键盘全高，包括最下面的功能键水域。不能形成等距队列，也不能全部同步摆尾。空闲期间每隔随机时间只有一条鱼出现短暂的头部、背部和尾膜流动闪光，不能所有鱼同时发光或常亮。

触摸瞬间必须同时出现：

- 触点处约 0.16–0.30 秒的浅凹陷、偏心高光和不规则接触带；首帧不能为零。
- 一次低音量真实触水声；释放时没有第二声。
- 所有鱼开始转向触点，但背对目标的鱼先卷尾、张胸鳍制动和快速转身，不能沿旧方向继续滑远。

持续按住或滑动时：

- 始终只有一簇水草在手指下，根部随 `MOVE` 跟手，滑动不另外播放声音或新建涟漪。
- 鱼群有抢食近槽、顺时针环游、逆时针环游、先外后内四类投喂角色；同一角色也保留个体速度与碰撞半径。
- 尾摆越快，单位时间完成的推进脉冲越多，位移才越大。
- 鱼身主体保持稳定，不跟尾巴一起左右摇；尾根先弯，尾尖延迟。
- 鱼永远头朝前，不倒游，不瞬移，不在边界直接反射或突然翻转。

松手的下一帧水草必须完全消失。鱼保留当前惯性，再在约 3.4 秒内轮换执行放射散开、双群反向分流或螺旋扩散，之后按个体状态恢复独立路线、同群跟随或双鱼绕游。

## 3. 分层架构

```text
Android IME / UI 主线程
  ├─ 原生 KeyView：命中、按键事件、无障碍、按压下沉
  ├─ 触摸观察器：DOWN / MOVE / UP -> 有界无锁队列
  ├─ SoundPool：DOWN 时轮转播放一个水滴样本
  └─ 语音键和底部操作条：镜像触摸但不改变原控件所有权

kboard-aquarium-gl 独立线程
  ├─ EGL 3 context + TextureView Surface
  ├─ 固定 30Hz 更新鱼群动力学
  ├─ Pass 1：全屏水面和最多 4 组涟漪
  ├─ Pass 2：5–10 条程序化金鱼
  └─ 每 5 秒统计 FPS 并调整鱼数
```

视图 Z 顺序必须是：水族 `TextureView` 最底层，半透明键帽在上，字符和功能图标最上。不要把鱼放在键帽上方，也不要把键帽做成完全不透明。

## 4. Android 容器与生命周期

`DesktopAquariumView` 使用 `TextureView.SurfaceTextureListener`：

- `activate()`：仅全局键盘进入时启动；若 Surface 已可用则创建线程。
- `deactivate()`：退出全局键盘立即停止线程并清空触摸队列。
- `onSurfaceTextureAvailable()`：启动 EGL 线程。
- `onSurfaceTextureSizeChanged()`：必须同时调用 `SurfaceTexture.setDefaultBufferSize()` 和更新 GLES viewport。
- `onSurfaceTextureDestroyed()`：停止线程并返回 `true`，由 TextureView 释放 SurfaceTexture。

内部缓冲宽度：

```text
bufferWidth  = min(viewWidth, 1080)
bufferHeight = viewHeight × bufferWidth / viewWidth
```

这一规则保证旋转后纵横比恢复。只更新 viewport、不更新默认缓冲尺寸，会出现鱼旋转后变小且无法恢复的问题。

EGL 配置固定为 GLES 3、window surface、RGBA 8 位、depth 16 位；开启 `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` 混合，关闭背面剔除。水面 pass 关闭 depth test。

## 5. 帧调度、队列和性能预算

目标帧间隔为 `33_333_334ns`。每帧执行顺序：

```text
读取并应用 resize
清空本帧触摸命令
dt = clamp((now - previous) / 1e9, 0.001, 0.05)
updateFish(time, dt)
drawWater(time)
drawFish()
eglSwapBuffers()
parkNanos(33.333ms - 已用时间)
```

触摸队列为 `ConcurrentLinkedQueue`，最多保留 12 条；超出时丢最旧事件，禁止连续滑动造成无界积压。输入主线程绝不等待渲染线程。

默认 10 条鱼。以 5 秒为性能窗口：

| 条件 | 动作 |
|---|---|
| FPS < 21 且鱼数 > 5 | 降到 5 条 |
| FPS < 26 且鱼数 > 7 | 降到 7 条 |
| FPS > 28.5 连续两个窗口 | 每次恢复 1 条，最多 10 条 |

`.63` 实测 1080×451、10 条鱼为 29.2–29.9 FPS。1440 内部宽度曾降到约 15 FPS，因此 V900/Mali-G52 不应使用 1440。

## 6. 坐标系

UI 触摸坐标先归一化为 `[0,1]`。引擎世界坐标为 NDC：

```text
worldX = touchX × 2 - 1
worldY = 1 - touchY × 2
```

水面 Shader 保持 UV `[0,1]`，只在距离计算时把 X 乘以 `width / height` 修正屏幕比例。外部覆盖按钮必须用屏幕坐标减去 aquarium 的屏幕原点，再除以 aquarium 宽高，不能直接把按钮局部坐标传入。

## 7. 单条鱼的数据模型与初始化

每条鱼至少保存：世界位置、朝向、非负前向速度、角速度、尾摆相位、胸鳍相位、尾鳍力度、左右胸鳍力度、转向力度、制动力、侧倾、深度、体型、巡游速度、尾摆节奏 `tailTempo`、冲程推力系数 `thrustScale`、个体最高速度、个体 seed、三色调色板、花纹类型、群组、个人路线和社交行为状态。

随机种子固定为 `0x4B4F49`，保证同一版本启动分布可复现。鱼数固定 10，分成两个松散群组 `schoolId=index%2`。

体型序列：

```text
0.045, 0.064, 0.053, 0.079, 0.042,
0.070, 0.057, 0.075, 0.048, 0.061
```

绘制时再乘 `0.75 + depth × 0.35`，alpha 为 `0.72 + depth × 0.24`，形成远近层次。

速度性格必须使用非单调、不重复的序列，避免按索引排成机械的由慢到快队列：

```text
0.72, 1.18, 0.88, 1.36, 0.79,
1.06, 1.43, 0.95, 1.27, 0.84
```

`cruiseSpeed=0.175×personality`，`tailTempo=0.82+personality×0.24`，`thrustScale=0.73+personality×0.31`，`maxForwardSpeed=0.72+personality×0.38`。初始速度为本鱼巡游速度的 72%..82%。快鱼不是被直接平移得更快，而是同时具有更高尾摆节奏、单次冲程推力和速度上限。三分之一鱼拥有底部路线：中心 Y 为 `-0.75..-0.65`，纵向半径 `0.12..0.22`；其余路线中心 Y 为 `-0.11..0.11`，纵向半径 `0.38..0.60`。所有鱼的横向路线半径 `0.48..0.70`。

十组三色调色板和四种花纹的精确 RGB 数组位于 `FISH_PALETTES`；复刻时应直接复制，不能只用一个鱼贴图随机改色。

## 8. 空闲池塘行为

三种行为按 `ROUTE -> FOLLOW -> PLAY` 循环，每次持续 `6.5..13s`，个体起始步不同。

### ROUTE

每条鱼沿个人不规则椭圆路线游动。路线相位只能按实际游过的距离推进：

```text
routeProgress += routeDirection × forwardSpeed × dt / meanRadius × 0.72
lookAhead = routeProgress + routeDirection × 0.48
targetX = centerX + cos(lookAhead) × radiusX
        + cos(2 × lookAhead + routeShape) × 0.065
targetY = centerY + sin(lookAhead) × radiusY
        + sin(3 × lookAhead - routeShape) × 0.045
```

鱼停下时路线也停，目标不能按时间拖着鱼走。

### FOLLOW

选择同群前一条鱼，目标位于其尾流后方：间距 `0.15 + scale×0.65`，再叠加最大 `0.035` 的慢速侧向摆动。目标速度为 `leaderSpeed×0.82 + ownCruise×0.42`，限制在本鱼巡游速度的 74% 到个体最高速度的 58%，不再用全群共享的 `0.36` 上限压平速度。

### PLAY

选择同群后一条鱼，每 `3.8s` 在三种游戏方式间轮换：

1. 相互绕游：半径不小于双方碰撞半径之和，并在伙伴前方增加 `0.068`；
2. 追尾嬉戏：在伙伴尾流后方保持安全距离，叠加 `sin(time×1.36+phase)×0.038` 的横向挑逗；
3. 并排交叉：在伙伴两侧以安全半径加 `0.018` 分开，同时用慢速正弦改变前后错位和侧向间隔。

目标速度下限为自身巡游速度的 90%，上限为个体最高速度的 58%。三种模式都只产生导航目标，不直接改变坐标或朝向。

四条底层鱼即使进入社交行为，非投喂/非散开时目标 Y 仍不高于 `-0.67`，因此最下排按键下方始终有鱼。

## 9. 局部群游与避边

每条鱼的碰撞半径按真实绘制尺寸计算：

```text
renderedScale = scale × (0.75 + depth×0.35)
collisionRadius = 0.025 + renderedScale×0.88
```

任意两鱼距离小于双方碰撞半径之和时产生分离；若另一条鱼位于前方且点积大于 `0.58`，同时产生拥挤制动。同群邻居在距离小于 `0.46` 且视野点积大于 `-0.52` 时参与聚合与方向协调。不能回退到固定 `0.155`，否则小鱼浪费空间、大鱼和长尾会穿模。

分离增益：投喂 `0.52`、散开 `1.05`、空闲 `1.55`。空闲时聚合增益 `0.42`，方向协调增益 `0.22`。

边缘预测距离为 `0.20 + forwardSpeed×0.58`。安全边界为 X `[-0.86,0.86]`、Y `[-0.95,0.86]`；预测穿越边界时增加向内意图并制动。最终数值安全夹取是 X `[-1.07,1.07]`、Y `[-1.02,0.94]`，越界只把速度乘 `0.12`，绝不能反射速度或翻转朝向。

## 10. 触摸、跟手与散开状态机

### DOWN

1. 在 4 个循环槽中写入新涟漪，Y 转成 `1-y`。
2. 覆盖唯一 `touchPlant`，根部 X 限制 `[-0.88,0.88]`、Y 限制 `[-0.93,0.70]`，并按当前种子生成叶片与气泡相位。
3. 设置手指世界坐标，吸引持续 4.6 秒；`touchHeld=true`。
4. 所有鱼清零本轮摆尾计数。
5. 每条鱼的尾膜相位被放到下一帧将穿过中线的位置，小偏移按索引为 `0.050..0.086rad`；这只消除等待空闲相位的迟滞，位移仍必须由下一次可见冲程产生。
6. 背对触点的鱼立即提高相应单侧胸鳍力度到 `0.72 + turnKick×0.28`，制动力最高到 `(1-forwardAlignment)×0.94`，尾鳍力度至少提高到约 0.88。
7. 投喂场景在 3 组变体间轮换，随机选一条幸运鱼；播放一个水滴样本。

### MOVE

更新吸引点、`touchPlant` 根部及 1 秒丢失 UP 的安全超时，不新建涟漪、不播放声音。渲染线程每帧读取最新目标，因此水草和鱼连续跟手。

### 投喂槽位

不能让 10 条鱼都追同一个坐标。基础角使用黄金角 `2.3999632rad`，角色为 `(index+interactionVariant) mod 4`：

```text
抢食：radius = 0.105 + (index mod 3)×0.022，speed = ownCruise×(5.35+个体扰动)
顺游：angle += age×(1.08..1.36)，radius = 0.155 + (index mod 3)×0.030
逆游：angle -= age×(0.88..1.12)，radius = 0.175 + (index mod 2)×0.035
谨慎：radius 在 0.90s 内由约 0.285 收缩到约 0.170
所有角色：offsetY *= 0.76，speed <= ownMaxForwardSpeed
```

触点靠边时把朝边外的 offset 反射回池塘，避免目标经 clamp 后重叠。角色速度仍以本鱼 `cruiseSpeed` 为基础且不超过 `maxForwardSpeed`；幸运鱼只在进入触点 0.34 范围后获得距离衰减的 `uSparkle`，不获得额外推力。

### UP / CANCEL

关闭吸引，把 `touchPlant.start` 立即置为 `-100`，清除幸运鱼触摸闪光，开启 3.4 秒散开。散场模式按触摸变体轮换：

1. 放射散开：以黄金角与 seed 分配 `0.44..0.86` 半径；
2. 双群分流：两个 `schoolId` 向相反方向分开，每条有单独角度与半径；
3. 螺旋扩散：角度随 index 额外增加 `0.28rad`，半径从 `0.34` 递增。

底层鱼的散开目标 Y 仍不高于 `-0.66`。每轮强制混合 ROUTE/FOLLOW/PLAY，散开结束后维持 4–10 秒。全程不改鱼的当前坐标、朝向或前向速度。

### 水草网格、气泡和鱼草槽位

水草必须使用独立小网格 Pass，绘制顺序为 `水面 -> 水草 -> 鱼 -> Android 键帽`，不得在全屏水面片元 Shader 中重复计算植物 SDF：

- 唯一手指草共 7 片叶，每片 5 段，每段 2 个三角形；另有 3 个气泡 quad，共 228 顶点；
- 首帧尺寸/透明度为 30%，`0.24s` 长满；按住时不计寿命，松手后下一帧不再进入水草 Draw Call；
- 叶片顶点以 `uTime + seed + bladeId` 产生不同水流相位，越靠叶尖摆幅越大；
- 气泡使用同一静态网格和 Draw Call 在顶点 Shader 循环上升，不创建 CPU 粒子或逐帧对象。

水草根部在按住时是柔性障碍：安全距离为 `fishCollisionRadius + 0.055`，进入后增加 0.78 倍分离力，若草根位于鱼正前方还要增加制动。`UP/CANCEL` 后该障碍与渲染同时失效。水草和角色槽位只能改变导航意图，不能直接位移鱼。

## 11. 从导航意图到真实游动

这是效果是否自然的核心。每帧只能沿以下单向链路更新：

```text
路线/同伴/触点/边缘
        ↓
期望方向与期望速度
        ↓
尾鳍、左胸鳍、右胸鳍、制动目标
        ↓
可见摆尾完成事件、推力、水阻、偏航转矩
        ↓
前向速度、角速度、位置
        ↓
同一相位和力度传入 Shader 形变鱼鳍
```

禁止路线或触点直接写 `x/y/heading`，也禁止把速度插值到目标速度后平移模型。

### 11.1 方向、到点制动与肌肉目标

```text
headingError    = wrap(desiredHeading - heading)
turnDemand      = clamp(headingError / 1.15, -1, 1)
forwardAlignment= clamp((cos(headingError)+1)/2, 0, 1)
arrivalRadius   = feeding ? 0.20 + speed×0.34 : 0.14
arrivalBrake    = targetDistance < radius ? 1-distance/radius : 0
feedingTurnBrake= smoothstep((abs(error)-0.52)/1.18) × 0.94
```

投喂时目标速度还要乘 `distance/arrivalRadius`（限制 `0.08..1`）和 `0.24 + forwardAlignment×0.76`。这保证背对触点时动作先加快但不会沿旧方向高速滑行。

尾鳍目标：

```text
tailTarget = clamp(
  0.16 + max(speedError,0)×1.72
  + abs(turnDemand)×0.12
  + feedingTailBoost
  - brakeDemand×0.20,
  0.12, 1.0)
```

胸鳍基础目标：

```text
finBase = clamp(0.17 + max(speedError,0)×0.42
  + abs(turnDemand)×0.14 + brake×0.60
  + (feeding ? 0.10 : 0), 0.12, 0.86)
left  = clamp(finBase + max(turnDemand,0)×0.76, 0.12, 1)
right = clamp(finBase + max(-turnDemand,0)×0.76, 0.12, 1)
```

肌肉响应率在投喂后前 0.46 秒为 `11.5/s`，后续投喂为 `7.2/s`，空闲为 `4.5/s`；转向力度响应率投喂 `32/s`、空闲 `9/s`。

### 11.2 摆频

`smoothstep01(x)=x²(3-2x)`：

```text
burst  = smoothstep01((drive-0.20)/0.80)
tailHz = (1.60 + burst×3.80) × tailTempo
finHz  = (0.70 + burst×1.55) × (0.88 + tailTempo×0.12)
```

尾鳍明显快于胸鳍，避免四片鱼鳍像蜻蜓翅膀同步拍动。投喂后前 0.46 秒再将两者节奏乘 1.22，因此快鱼的可见尾摆会先于位移明显爆发。

### 11.3 尾摆脉冲和水阻

CPU 与 Shader 共用 `swimPhase`。CPU 在连续尾幕约 72% 长度处采样，代表相位偏移为 `-0.96rad`。当该点跨过身体中线且差值大于 `0.015`，才算完成一个 power stroke。

```text
tailAmplitude = lerp(0.130, 0.400, tailDrive)
impulse = completedStroke
  ? tailAmplitude × (0.115 + tailHz×0.020) × thrustScale
  : 0
redirectedImpulse = impulse × clamp(1-brake×0.96, 0.02, 1)
speed = clamp(speed + redirectedImpulse, 0, maxForwardSpeed)
drag = speed×1.18 + speed²×0.82
pectoralBrake = brake×finExtension×(0.34 + speed×1.20)
speed = clamp(speed - (drag+pectoralBrake)×dt, 0, maxForwardSpeed)
```

两次摆尾之间只有水中惯性和阻力，不存在隐藏的连续目标速度推进。位置每帧只积分一次：

```text
x += cos(heading) × speed × dt
y += sin(heading) × speed × dt
```

### 11.4 快速 C 型转身

大角度投喂转向第一次出现时，锁存一次启动事件：旧方向速度乘 `0.42`，角速度增加：

```text
turnDemand × smoothstep((abs(turnDemand)-0.52)/0.38)
× (5.80 + tailDrive×3.40) × tailTempo
```

锁存防止逐帧重复。随后每个可见摆尾完成事件再增加一次同源偏航脉冲；投喂后前 0.46 秒角速度限制 `±14.50rad/s`，后续投喂 `±11.20rad/s`，空闲 `±3.20rad/s`。对准目标后额外 `6.8` 的角阻尼迅速停转。

鱼朝向只能由角速度积分，不能直接赋值。

## 12. 鱼体网格和材质

### 鱼身

鱼身是 32 段椭圆扇面：中心约 `(0.09,0)`，X 半径 `0.68`，Y 半径 `0.24`。鱼头沿本地 `+X`，尾部沿 `-X`。

### 连续尾幕

尾幕不是上下两片独立三角形，而是 6 列 × 5 行连续网格：

```text
columnsX = [-0.52,-0.72,-0.94,-1.16,-1.36,-1.48]
halfSpan = [0.075,0.145,0.275,0.425,0.490,0.405]
rows     = [-1,-0.5,0,0.5,1]
```

末列中央向前缩 `0.10×(1-|row|)`，形成浅尾缺口但不把尾膜拆开。尾根到尾尖相位逐步延迟 `1.34rad`；振幅从根部向尾尖增长。鱼身保持刚体，只有尾柄和尾幕参与行波。

大转向时连续尾幕按最大 `3.20..5.20rad` 卷成 C 型圆弧，卷曲越强膜宽越收至 58%，避免宽尾自交像两片翅膀。

### 胸鳍

左右胸鳍各用 7 点圆弧扇面，固定根为 `(-0.02, ±0.22)`。鳍根基本不动，位移和 Z 翻动随到鳍尖的 lever 增长；左右分别使用真实 `leftFinDrive/rightFinDrive`，制动时展开但频率始终低于尾鳍。

### 朝向矩阵

GLSL 是列主序，必须使用：

```glsl
mat2(cosH, sinH, -sinH, cosH) * local.xy
```

这样本地 `+X` 与 CPU 的 `(cos(heading),sin(heading))` 完全一致。矩阵列写反会出现斜向时尾朝前或倒游。

### 花纹和 3D 感

片元 Shader 用四类程序花纹：双频有机斑、条纹、交叉斑点、鞍形色块。每条鱼从 base/patch/accent 三色混合，并加入：

- 鱼身椭圆法线的漫反射。
- 随胸鳍动作变化的移动高光。
- 尾膜筋纹与半透明 alpha。
- 头部双眼和局部光泽。
- 实际角速度产生的侧倾 `bank`，但鱼身不随尾相位摇晃。

### 随机单鱼闪光

闪光不新增 CPU 粒子或第三个渲染 Pass，仍在鱼体片元 Shader 内完成：

- 任何时刻只有一条鱼的 `uSparkle` 非零；当前鱼结束后不立即重复选中同一条。
- 每次持续 `1.15..2.10s`，两次之间暂停 `1.40..4.00s`，选择仍使用固定引擎随机源。
- 在头部、背部、尾膜各生成一个与 `seed` 相关的星芒中心；三者用不同 7.1..9.4Hz 相位闪烁，叠加暖金和弱冷蓝高光。
- CPU 只传入时间和淡入/淡出强度；不修改鱼的坐标、速度、尾摆或深度，所以特效不影响物理。

要得到完全一致的形状和着色，请直接复用 `FISH_VERTEX_SHADER`、`FISH_FRAGMENT_SHADER`、`createFishGeometry()` 与 `FISH_PALETTES`，文档中的描述不替代可执行 Shader。

## 13. 非圆轻涟漪 Shader

水面是一个全屏四边形，最多 4 个 ripple uniform：`x,y,start,enabled`。

每个涟漪按以下顺序计算：

1. X 乘屏幕 aspect。
2. 依据触点生成水流方向，波心随 age 以 `0.006` 缓慢漂移。
3. 水流轴使用 `0.95/1.05` 非等比缩放。
4. 距离叠加 2、3、5 阶方向扰动，避免几何圆。
5. 波前速度 `0.19 UV/s`，主带宽 `0.074`，寿命 1.55–2.35 秒，指数衰减 `exp(-age×0.82)`。
6. `DOWN` 首帧额外加入 0.16–0.30 秒的接触凹陷、偏心 glint 和不规则接触带。不能只使用 `sin(age)`，否则 age=0 时看不到涟漪。
7. 波坡只造成 `0.0025/0.0034` 的轻微折射；不做规则高亮圆环或整屏镜头扭曲。

基础水色：deep `(0.012,0.075,0.14)`，shallow `(0.018,0.22,0.30)`；两组慢速正弦生成弱焦散，最后叠加法线高光、波峰、波谷和暗角。

完全一致应直接复制 `WATER_VERTEX_SHADER` 和 `WATER_FRAGMENT_SHADER`。Mali-G52 的 GLSL 编译器会拒绝某些保留标识符；本项目曾因变量名 `patch` 在真机失败，因此所有新增命名必须在目标 GPU 上编译验证。

## 14. 半透明键帽和输入隔离

键帽视觉参数：

- 外层暗影渐变：`0x4A020A12 -> 0x3D07121E`。
- 普通键面：`0x7329465C -> 0x5C102536`。
- 选中键面：`0x8F297895 -> 0x78123B55`。
- 普通描边 `0xFF426A84`，选中描边 `0xFF75DFFF`。
- 顶部高光：`0x24FFFFFF -> 0x00FFFFFF`。
- 键间距横/纵 2dp，圆角 8dp，按下 Y/Z 行程 3dp。
- 字色 `0xFFF4F8FC`。

水族按键禁用 Android `RippleDrawable`，因为规则圆形反馈会与水面 Shader 冲突。按键本身仍是原生 View；`dispatchTouchEvent()` 先把事件镜像给水族，再调用 `super.dispatchTouchEvent()`，不能消费或改写事件。

底部操作条和语音键不在键盘 View 内时，要用不消费事件的 listener 镜像触摸。水族 Surface 铺到最底边，但六行按键通过真实 44dp bottom inset 避开操作条，不能只设置 padding。

### 14.1 按住式组合键功能预览

全局键盘的 Ctrl、Alt、Cmd、Shift 必须按物理按住语义工作，不能做成点击一次锁定、再点一次解除：

1. 修饰键本身不绑定普通 `Click` 行为；其 `GestureType.Down` 把当前键加入 `heldModifierKeys`，`Up/Cancel` 移除。
2. 每次集合改变，都从仍被手指持有的键重新生成 `modifierStates`。两枚 Shift 同时按住时，松开其中一枚不能清除另一枚。
3. `BaseKeyboard` 保持 pointer-to-key 多指分发。一只手按住 Ctrl，另一只手释放 C 后，Ctrl 仍留在状态集合，因此可以继续按 V；只有控制键手指释放才取消提示。
4. 普通目标键动作复制当前 `modifierStates` 到真实 Fcitx 键事件。提示层不直接调用复制、保存等 Android API，前台 Windows/macOS 应用负责解释组合键。
5. Ctrl+Space 是本键盘明确接管的语言切换；其他组合键保持透明转发。

提示映射按“基础表 + 复合覆盖表”组织：

| 按住状态 | 代表性提示 |
|---|---|
| Ctrl | A 全选、C 复制、X 剪切、V 粘贴、Z 撤销、Y 重做、F 查找、H 替换、S 保存、N 新建文档、O 打开、P 打印、T 新标签、W 关闭、R 刷新 |
| Ctrl+Shift | 在 Ctrl 表基础上覆盖 S 另存为、V 纯文本粘贴、T 恢复标签、N 无痕窗口、Z 重做、Tab 上一标签 |
| Cmd | macOS 常用的全选、复制、剪切、粘贴、撤销、保存、打开、打印、退出、最小化、切换应用、系统搜索及行首/行尾导航 |
| Cmd+Shift | 在 Cmd 表基础上覆盖 3 全屏截图、4 区域截图、5 截图工具、S 另存为、N 新建文件夹、T 恢复标签 |
| Alt | Tab 切换窗口、F4 关闭窗口、Enter 属性、方向键后退/前进/上一级/展开菜单、Space 窗口菜单 |
| Shift | Tab 反向切换、Enter 换行、F10 右键菜单；字母大写和数字符号仍沿用键盘原有 Shift 变换 |

为防止第一次按修饰键造成 IME 或池塘画布跳动，每个 `TextKeyView` 在全局键盘挂载时就创建并测量第二行 `AutoScaleTextView`。状态切换只能：

- 用 `setLayoutStableText()` 改变绘制内容；
- 用 `alpha=0/1` 隐藏或显示；
- 把主字符向上平移固定 7dp，松开后回到 0。

第二行使用 8.5dp 白色 `#F4F8FC` 和 `Gravity.CENTER`，必须在当前键帽主字符下方水平居中。构造阶段绑定修饰键时直接遍历已经生成的 `allViews`；不要访问声明顺序位于 `init` 之后的 lazy 委托，否则其委托字段尚未初始化，会在第一次显示全局键盘时崩溃。

禁止在按压时创建/删除 View、切换 `visibility`、更改 LayoutParams 或调用普通 `setText()`。提示层只由 `DesktopKeyboard` 使用，所以普通键盘、数字键盘、候选栏和语音流程不会变化。

## 15. 真实水滴声音

声音来自 BigSoundBank `Drops of water #1` 的 CC0 现场录音，来源：<https://bigsoundbank.com/drops-of-water-1-s1384.html>。四个切片由 `prepare-aquarium-water-touch.py` 可重复生成。

| 样本 | SHA-256 | 播放速率 |
|---|---|---|
| `aquarium_water_touch_1.wav` | `c96bbd42ddf89db25465b9715c803a48d96f9b5752733ef2b27c116a39cf8302` | 0.98 |
| `aquarium_water_touch_2.wav` | `0d751efa5045b986e634def7077ca37b071144abf49e828392d815b00b2707b2` | 1.01 |
| `aquarium_water_touch_3.wav` | `34d1b8064ebea6ef1f0efa77e96370aa9be6e795554342615c577396b25e71f7` | 0.96 |
| `aquarium_water_touch_4.wav` | `abff27174a10d1741aab472feac6c5642a279e7610bed19f969847fc7bd04ef3` | 1.03 |

用 `SoundPool(maxStreams=2)` 在进入全局模式前异步预加载，usage/content type 均为 sonification。默认音量系数 0.30；每次 `DOWN` 停掉上一条未结束的尾音，再轮转播放下一样本。`MOVE` 和 `UP` 不播放，避免一次按键有按下/释放双音效。

## 16. 与语音、语言切换和页面布局共存

水族只在全局模式运行。普通键盘、数字键盘和其他页面不创建渲染线程，也不使用 44dp 水族底部占位。

语音按钮的 DOWN/MOVE/UP 同样镜像给鱼群，但录音生命周期仍由 ASR 手势处理器独占。全局语音识别过程中只更新永久测量的固定提示层；不能首次切换 View visibility，也不能持续向 `adjustPan` 目标编辑器写 composing 文本，否则客户端画面会移动。

全局键盘高度按 `orientation/screenWidthDp/screenHeightDp/densityDpi` 锁存；同一配置内候选、语音和中英切换不能重新计算画布。动态语言文字使用固定测量、只重绘方式：中文当前态显示“中/英”，英文当前态显示“英/中”，当前字符为 `#4285F4`。不要在切换时调用普通 `TextView.setText()` 触发整棵 IME `requestLayout()`。

## 17. 复刻步骤

1. 确认目标支持 GLES 3；建立一个只在目标模式激活的 Texture/Surface 容器。
2. 复制 EGL 生命周期、1080 内部缓冲和 30Hz 独立线程。
3. 复制鱼状态、固定 seed、初始化参数、路线和三种社交状态。
4. 完整复制 `updateFish()` 的“意图—肌肉—脉冲—水阻—积分”顺序，不能调换。
5. 复制鱼身、6×5 连续尾幕、胸鳍网格及四个 Shader。
6. 把主按键区、语音键、底部条的触摸以观察方式统一送入 DOWN/MOVE/UP 队列。
7. 复制半透明键帽参数，关闭原生圆形 Ripple。
8. 复制四个 WAV、SoundPool 预加载和单次播放策略。
9. 增加 5 秒 FPS 日志和 10/7/5 鱼降级。
10. 用第 18 节矩阵验收；只有构建通过不能算完成。

## 18. 验收矩阵

### 功能

- 按键输入、长按、连删、修饰键、空格、回车、语音不被水族拦截。
- DOWN 有一簇手指水草、一次涟漪和一次声音，MOVE 让水草跟手且无新声音/涟漪，UP 后下一帧水草必须完全消失且无第二声。
- 手指滑到语音键和底部空白水域，鱼也持续跟随。
- 连续三次按住可辨识抢食、顺/逆环游、谨慎靠近的角色差异；靠近时每次只有一条随机幸运鱼获得触摸闪光。
- 连续三次松手依次可观察放射、双群分流和螺旋三种散场；所有鱼保留惯性，不瞬移、不直接转向。

### 动作

- 不同鱼的巡游速度、尾摆节奏、冲程推力和最高速度均有稳定差异；快鱼和慢鱼不会被 FOLLOW/PLAY 的全局上限压成同速。
- 尾摆基础频率为 1.6–5.4Hz 再乘个体 `tailTempo`，投喂后前 0.46 秒再乘 1.22；胸鳍始终明显更慢。
- 鱼身不随尾相位整体晃动。
- 背对目标时下一帧即进入可见 C 型动力冲程，瞬时角速度可达 `14.5rad/s`，旧方向速度先下降。
- 无倒游、无瞬移、无边缘镜面反射、无所有鱼同步。
- 随机闪光期间只有一条鱼出现多点流动星芒，特效不改变该鱼的路线和速度。
- PLAY 可观察到相互绕游、追尾嬉戏和并排交叉三种阶段，3.8 秒轮换一次。
- 最大鱼与最小鱼具有不同碰撞半径；投喂、追逐和交叉时身体及长尾不穿模。

### 水面与声音

- 触摸首帧可见浅凹陷，不是延迟出现的圆圈。
- 波形轻微、非圆、约 2.35 秒衰减，不扭曲整块键盘。
- 声音自然、轻、每按一次只有一次。

### 性能与稳定性

- `KBoardAquarium` 每 5 秒输出 FPS、鱼数、surface。
- 目标 `.63` 应为 `surface=1080x451`、10 条鱼、29.2–29.9 FPS。
- 旋转往返后鱼体比例恢复。
- 退出全局模式后不再输出 FPS。
- 日志无 `FATAL EXCEPTION`、EGL/GL shader、`OutOfMemoryError` 或音频初始化错误。

### 投喂定量参考

一次 `.63` 实测中，平均触点距离从 `0.615` 降为：0.8 秒 `0.536`、1.57 秒 `0.356`、2.34 秒 `0.189`、3.11 秒 `0.115`；3.11 秒最远鱼为 `0.181`。若鱼更快到达但尾巴没明显加速，说明重新引入了直接平移；若尾巴很快但距离不降，说明推进脉冲或转向闭环断开。

## 19. 常见错误及判断

| 现象 | 根因 | 正确修法 |
|---|---|---|
| 鱼像图片平移 | 目标速度直接插值并更新位置 | 只让完成的可见尾摆产生推进脉冲 |
| 鱼僵住 | 推力和位置被相位二次门控 | 相位只控制加速事件，位置连续积分实际速度 |
| 尾巴像蜻蜓 | 尾叶拆成独立扇面、Z 轴反相拍动 | 连续 6×5 尾幕、根尖延迟、主要平面扫水 |
| 鱼倒着游 | CPU heading 与 GLSL 列主序矩阵不一致 | 使用本文第 12 节矩阵，速度保持非负 |
| 转身太慢 | 必须等待半个空闲尾摆才有转矩 | 大角度上升沿只锁存一次卷尾启动脉冲 |
| 十条鱼速度越游越一样 | FOLLOW/PLAY 使用全群共享上限 | 个体化 cruise/tailTempo/thrust/maxSpeed，社交速度也按本鱼上限计算 |
| 闪光像常亮贴图 | 全鱼共享发光材质 | 每次只给一条鱼传 `uSparkle`，三个 seed 星芒异步淡入淡出 |
| 水草导致 FPS 下降 | 在全屏水面片元 Shader 里计算每片叶 | 独立 228 顶点小网格，仅按住时 1 次 Draw Call |
| 大小鱼仍互相穿模 | 所有鱼沿用固定分离距离 | 按 renderedScale 计算双方碰撞半径并求和 |
| 十条鱼全叠在草根 | 所有鱼追同一植物坐标 | 抢食/顺游/逆游/谨慎四角色使用不同半径与角运动，叠加个体碰撞半径 |
| 松手后水草还在 | 水草使用独立秒数寿命 | 渲染和碰撞都由 `touchHeld` 门控，UP 同时使 `start=-100` |
| 两鱼一直机械绕圈 | PLAY 只有一个轨迹方程 | 每 3.8 秒轮换绕游、追尾和并排交叉 |
| 点击后鱼先滑远 | 尾推力仍沿旧朝向释放 | 胸鳍制动重定向推力，对准后再解锁冲刺 |
| 所有鱼叠在手指下 | 所有目标完全相同或边缘 clamp | 黄金角分槽，靠边时反射 offset |
| 涟漪第一帧没有 | 使用 `sin(age)`，age=0 为零 | 增加短寿命接触凹陷和偏心高光 |
| 涟漪像规则圆圈 | 只使用径向距离 | 水流轴非等比、漂移和多阶角度扰动 |
| 旋转后鱼变小 | 只改 viewport | 同时重配 SurfaceTexture buffer |
| 键盘切换状态时跳动 | 动态 TextView/visibility 请求布局 | 固定测量，只改 alpha 或自绘内容 |
| 声音变嘈杂 | 按下和释放都播、快速输入叠音 | 仅 DOWN，先停上一 stream，再播一个样本 |

## 20. 发布基线

当前最近一次已签名正式 APK 仍为：

```text
package     org.fcitx.fcitx5.android
versionName 2ecfa902
versionCode 112
ABI         arm64-v8a
APK         fcitx5-android/build/kboard.apk
SHA-256     1f4a5e489f5a24c6c87191a2006ec9f63b3c3a6401f02c21d67fba6dceac8fa0
签名        v1=true, v2=true
证书 SHA-256 c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
```

该 Release 已完成 Kotlin、Lint Vital、R8、arm64 原生组件和 v1/v2 签名构建验证。截至 2026-08-23，`192.168.3.62/.63` 均整机网络不可达，因此新增 Shader 的 Mali-G52 编译、30Hz 性能和真实触控尚未验收，不得将本地构建成功写成真机成功。复刻到其他项目时，应保留源码与音频来源许可，并在实际目标 GPU 上完成全部验收。

V1.40 水草互动源码为 `dd5d2700`。无签名环境下的 `:app:assembleRelease`、R8、Lint Vital、arm64 原生组件及 6 个 Shader 等价语法检查均通过，验证包 SHA-256 为 `574f0379eec1c1a08237b7989a66f589095f6c15e5de6cdf1d7ac10e578902d5`，文件名明确包含 `release-unsigned`。它不能安装、发布或替代上述正式 APK。最终复查时 `.62` 已恢复为 ADB `device`、`.63` 仍不在线，但没有新签名包，因此仍须恢复项目原签名环境并完成 Android 12/Mali-G52 真机验收后才能更新正式发布基线。

V1.42 触摸生命期与差异化互动源码为 `79a3f763`：多簇长寿命水草收口为唯一跟手水草，松手下一帧同时移除渲染和碰撞；新增四类投喂角色、单鱼抢食闪光和三种散场，触摸期间暂停空闲随机闪光以保证任意时刻最多一条鱼发光。所有新目标仍只进入肌肉/水动力链路，不直接改世界坐标或朝向。

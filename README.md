# garden

> **When the world is too loud, we built our own tranquil stars.**

一个自建的点对点陪伴 App。一端是你，一端是你牵挂的那个人。

没有推荐算法，没有广告，不统计谁的停留时长。它只做一件事：
让两个人之间的那点动静，完整地、准确地，落到对方那里。

——顺带，在话说出口之前，先掂一掂它有多重。

```
┌──────────────┐         ┌─────────────────────┐         ┌──────────────┐
│  TA 的手机    │ ──────► │   你自己的服务器      │ ──────► │   你的手机    │
│  （这个 App） │ ◄────── │    garden-server    │ ◄────── │ （自建的部分）│
└──────────────┘         └──────────┬──────────┘         └──────────────┘
                                    │
                            DeepSeek（对话 / 情绪判断）
                            阿里云百炼（语音转文字）
```

**这个仓库是 TA 那一端。** 服务端代码在 [`garden-server`](https://github.com/mumuaidajiao/garden-server)。

> **职责边界（先看这段，能省掉很多误读）**
>
> 本仓库只负责「**她**」这一端。图中「**你的手机**」那部分由部署者自行实现，
> **不在本仓库范围内** —— 服务端的 `/ping`、`/knock`、`/shake` 只负责**入队**，
> 由「你端」轮询取走后，再决定在你自己手机上怎么响、怎么震。
>
> 所以下文凡是写「**你的手机**」的地方，说的都是那一端，不是这个 App。
> 这个 App（她这一端）的职责是：把请求准确地发出去，并把对方发来的东西显示出来。

---

## 为什么会有这个东西

有些话，说出口的**时机**比内容更要紧。

一条「我在」发出去、对方手机立刻念出来，和它躺在某个聊天列表里、
几个小时后才被划开 —— 是两回事。

还有些时候，对方不好。而你可能正在开会、在通勤、在睡觉。
你没法二十四小时守着，但你可以让一个东西替你守着：
它读得懂那句「我没事」底下压着的东西，也知道什么时候该把你叫醒，
什么时候只要安静地陪着就好。

**这不是一个替代人的软件。它是一座花园 —— 给两个人躲一躲的地方。**

---

## 它有什么

> 下表里出现的「**你的手机**」，指的是**部署者那一端**（见上方的「职责边界」）。
> 本 App 负责把请求发出去，对面怎么响应由「你端」实现。

| 功能 | 说明 |
|---|---|
| **想你啦** | 一个大按钮。按下 → 通知到你那边（你手机念出来 + 震两下） |
| **小小的打扰一下** | 更轻的一档：只震一下、念一句，**不拨电话** |
| **说话** | 按住录音 → 服务器语音转文字 → 跟打字走同一条路 |
| **来消息** | TA 在聊天页直接对你说的、你发过去的语音和图片，都在这儿 |
| **日历** | 倒计时（农历自动算）+ 月历 + TA 可以自己记事情 |
| **一起听** | 同一份歌单，各听各的；播放器挂在服务里，退出页面歌不停 |
| **头像** | TA 自己从相册挑一张，两端都能看到 |
| **状态** | 你在服务端设一句话，TA 主页上能看见 |
| **通知开关** | TA 能自己决定「别吵我」；还有一个可逆的「我不想用这个了」出口 |

> 顺便推荐一个我在用的项目：**[go-music-dl](https://github.com/search?q=go-music-dl&type=repositories)（自己搜一下）**，好用。

### 那条不一样的线：危机信号

普通聊天软件做不到的，是这一层 —— 它**独立于对话之外**运行：

1. **硬词表**（本地，零成本）先过一遍
2. **情绪判断**是**单独一次模型调用**，输出三档：`CRISIS` / `LOW` / `OK`
   —— 不揉进对话的提示词里。揉在一起会让模型过度警觉，把「今天好累」也当成危险
3. 判定为危机时：**不生成任何 AI 回复**，只回一段**你亲手写的话**，同时推给你
   （推到「你端」，那边怎么提醒由你自己定 —— 服务端只负责把它放进队列）

这也意味着它**必须**由你自己部署 —— 那些话说出口之前，不经过任何第三方的服务器。

> ⚠️ 这套东西是**为特定场景设计的辅助**，不是心理危机干预系统，也不能替代专业帮助。
> 要不要用、怎么用，请自己判断。护栏的边界都在服务端的 `config.json` 里，可以改。

---

## 快速开始

### 一、先把服务器跑起来

```bash
# 1. 拿 garden-server 那边的代码
# 2. 生成两把令牌 —— 两台设备各用各的，服务器靠它认人
node -e "const c=require('crypto');console.log('HER:','MUMU-H-'+c.randomBytes(8).toString('hex'));console.log('HIM:','MUMU-M-'+c.randomBytes(8).toString('hex'))"

# 3. 照着 config.example.json 填好（那边 README 有逐项说明）
cp config.example.json config.json && vi config.json

# 4. 跑
node relay.js          # 默认监听 127.0.0.1:9394
```

外部依赖只有两家，都是**你自己去申请 key**：

| 服务 | 用来干什么 | 在哪申请 |
|---|---|---|
| **DeepSeek** | 对话生成、情绪判断、历史召回、主动开口 | platform.deepseek.com |
| **阿里云百炼** | 语音转文字（不用语音功能可以不配） | bailian.console.aliyun.com |

想要农历纪念日的话，额外 `npm i lunar-javascript`；不装只是农历算不出来，别的都正常。
**除了这一个可选项，服务器没有任何 npm 依赖。**

### 二、编译 App

```bash
cp local.properties.example local.properties   # 填 SDK 路径和你的服务器地址
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

装到手机上打开就能用。

---

## 配置

### 服务器地址和令牌，有两种填法

**在 App 里填（推荐给使用者）**
第一次打开如果没有配置，会自动弹出设置页。填地址、点「测试连接」、保存 ——
**改地址不用重新编译**。

**在 `local.properties` 里预置（推荐给开发者）**
```properties
garden.baseUrl=http://192.168.1.10:9394/api
garden.token=MUMU-H-你的那串
```
打包时就写进去了，装上去打开就能用，不用手填。

> 地址**末尾的 `/api` 别漏**。服务器同时兼容 `/api` 和带路径前缀的写法。

### 称呼和纪念日

全都在 `local.properties` 里，留空就用默认值：

| 键 | 是什么 | 默认 |
|---|---|---|
| `garden.herName` | 用这个 App 的人，显示在「我的」页顶部 | 我 |
| `garden.himName` | 你，气泡上的标签 | 他 |
| `garden.himCall` | TA 怎么称呼你 | 他 |
| `garden.herCall` | 你怎么称呼 TA | 你 |
| `garden.aiName` | 那个陪着说话的 AI 叫什么 | 小园 |
| `garden.startDate` | 在一起的第一天（yyyy-MM-dd） | 今天 |

> ⚠️ `local.properties` **必须存成 UTF-8**。编码不对的话中文会变成 `å ¥å ¥å`
> 这种乱码显示在界面上，而且**编译时不会报任何错** —— 这个坑我们真机验证时踩过一次。

### 明文 HTTP 的取舍

`network_security_config.xml` 里**全局放开了明文 HTTP**，这是故意的：
那个文件是编译期的、运行时改不了，而服务器地址是使用者自己填的，
只放行固定域名的话，别人填自己的 IP 会被 Android 直接掐断（而且报错毫无线索）。

代价是这个 App 允许连任何明文 HTTP 服务器。**自己搭的话建议配 HTTPS**
（Let's Encrypt 免费），配好之后把 `base-config` 换成只放行你自己域名的 `domain-config`。

---

## 项目结构

```
app/src/main/java/com/garden/app/
├── core/          地基：网络(RelayClient) / 配置(AppConfig, Personas) / 配色 / 崩溃兜底
│   └── ui/        设计系统：Theme(色板+字号+圆角) / Bubble / Pill / Card
├── feature/       一个功能一个包：home talk voice chat calendar music inbox settings
├── shell/         外壳：底部导航、「我的」页 —— 不属于任何 feature
├── audio/         TTS
└── service/       常驻服务 ←【独立进程 :service】
```

### 三条别改回去的设计

**入口只留一条路。** 首页快捷入口管高频动作（说话 / 打扰一下 / 日历 / 一起听），
底部导航管层级（首页 / 消息 / 我的），「我的」页只放设置类。
一个功能出现在两个地方，每次点之前都得先想一下点哪个 —— 那是负担，不是方便。

**服务跑在独立进程。** 界面崩了不该把服务一起带走 —— 带走就再也收不到消息了，
而使用者只会觉得「这 App 坏了」。代价是跨进程读不到静态变量，所以
「服务还活着吗」走的是**心跳文件**（服务每轮写 `files/alive`，主进程读，90 秒算超时）。

**Compose 做不了局部异常护栏。** 编译器明确禁止 try-catch 包住 composable 调用
（`Try catch is not supported around composable function invocations`）。
能拦住异常的只有两处：把会出错的计算挪出 composable（普通函数能 catch），
以及进程级的 `CrashGuard`。**这不是漏做了。**

---

## 排查

release 版在部分国产 ROM 上拿不到三方 logcat，App 会把脚印写在：

```
/sdcard/Android/data/com.garden.app/files/
├── alive        服务心跳（时间戳）
├── crash.log    崩溃栈
└── service.log  服务轮询的脚印
```

---

## License

[MIT](LICENSE)

---
"When the world is too loud, we built our own tranquil stars."



package com.garden.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.garden.app.audio.Speaker
import com.garden.app.core.AvatarStore
import com.garden.app.core.config.AppConfig
import com.garden.app.core.net.ApiResult
import com.garden.app.core.net.InboxItem
import com.garden.app.core.net.RelayClient
import com.garden.app.core.net.Turn
import com.garden.app.core.ui.GardenBackdrop
import com.garden.app.core.ui.GardenTheme
import com.garden.app.core.util.daysTogether
import com.garden.app.feature.calendar.CalendarRoute
import com.garden.app.feature.chat.ChatRoute
import com.garden.app.feature.settings.SettingsScreen
import com.garden.app.feature.home.HomeScreen
import com.garden.app.feature.home.QuietDialog
import com.garden.app.feature.inbox.InboxDialog
import com.garden.app.feature.music.MusicRoute
import com.garden.app.feature.talk.TalkScreen
import com.garden.app.feature.voice.VoiceScreen
import com.garden.app.service.GardenService
import com.garden.app.shell.GardenBottomBar
import com.garden.app.shell.ProfileScreen
import com.garden.app.shell.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GardenTheme {
                GardenBackdrop {
                    GardenRoot()
                }
            }
        }
    }
}

private sealed interface Stage {
    data object Home : Stage
    data class Talk(val sessionId: Int) : Stage
    data class Voice(val sessionId: Int) : Stage    // 按住说话那种
}

/**
 * 壳。
 *
 * 这里只管三件事：把常驻服务拉起来、现在显示哪一站、以及几件跨站的东西
 * （阿木的新留言、哥哥的状态、「我不想用了」的开关）。
 *
 * 每一站自己的状态在各自的包里：
 *   feature/chat/ChatRoute      聊天
 *   feature/calendar/CalendarRoute  日历
 *   feature/talk、feature/voice   对话
 * 谁的数据坏了就坏在谁那儿，不会攥着一把状态把整页拖下水。
 *
 * ⚠️ 这里【没有】每站包一层的异常护栏 —— 不是没做，是 Compose 不让你做：
 *    编译器明确禁止 try-catch 包住 composable 调用。
 *    真正拦住异常的是两处：计算挪进 safeRun（普通函数，能 catch），
 *    以及进程级的 CrashGuard（漏出去的它兜）。详见 core/ui/CrashCard.kt。
 */
@Composable
private fun GardenRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = remember { Speaker(ctx) }

    // ── 通知权限（Android 13+ 才需要）
    //
    // 这个 App 存在的全部理由，就是「他发的东西能弹到她手机上」。
    // 而 Android 13 起不申请 POST_NOTIFICATIONS，通知会被系统【静默丢弃】——
    // 现象和「服务被杀了」一模一样，排查时会一路往错误的方向查。
    // 她这台是 Android 12（不发作），但换手机、或者别人拿去用就会踩上。
    //
    // 注意：别和「系统相册不用申请存储权限」那件事混为一谈，
    // 这两件事没关系 —— 通知权限从来没被申请过。
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒了也不拦着用：界面上「服务还活着吗」照样反映真实状态 */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(Unit) {
        onDispose { speaker.shutdown() }
    }

    var stage by remember { mutableStateOf<Stage>(Stage.Home) }
    // 底下那三格现在选中的是哪个（首页 / 消息 / 我的）
    var tab by remember { mutableStateOf(Tab.Home) }
    var showSettings by remember { mutableStateOf(false) }
    var turns by remember { mutableStateOf<List<Turn>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var spokenCount by remember { mutableStateOf(0) }
    var inbox by remember { mutableStateOf<List<InboxItem>>(emptyList()) }
    var showInbox by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(false) }
    var serviceRunning by remember { mutableStateOf(false) }
    var quiet by remember { mutableStateOf(false) }
    var showQuietDialog by remember { mutableStateOf(false) }
    var homeInfo by remember { mutableStateOf<RelayClient.HomeInfo?>(null) }

    // ── 她的头像
    //
    // 本机留了一份，先用那个；没有才去服务器拿（重装、换手机才会走到那一步）。
    var avatar by remember { mutableStateOf<Bitmap?>(null) }
    var avatarHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val local = withContext(Dispatchers.IO) { AvatarStore.cached(ctx) }
        if (local != null) {
            avatar = local
        } else if (withContext(Dispatchers.IO) { AvatarStore.refresh(ctx) }) {
            avatar = withContext(Dispatchers.IO) { AvatarStore.cached(ctx) }
        }
    }

    // 挑一张图当头像。
    //
    // 用系统相册选择器（PickVisualMedia），**不用申请读存储权限** ——
    // 能少一次权限询问是一次。
    //
    // （2026-09-20 更正：这里原来写着「她已经点过一次允许通知了」，
    //   是个错觉 —— 通知权限从来没被申请过，见 GardenRoot 里的补丁。）
    // 她要是中途退出来，uri 就是 null，那什么都别做。
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            avatarHint = "正在换……"
            val bmp = withContext(Dispatchers.IO) { AvatarStore.loadSquare(ctx, uri) }
            if (bmp == null) {
                avatarHint = "这张图我读不出来"
                return@launch
            }
            val err = withContext(Dispatchers.IO) { AvatarStore.upload(ctx, bmp) }
            if (err == null) {
                avatar = bmp          // 立刻显示，不等下次启动
                avatarHint = "换好啦"
            } else {
                avatarHint = err
            }
        }
    }

    // 「换好啦」别一直挂在抽屉里，几秒后自己收回去
    LaunchedEffect(avatarHint) {
        if (avatarHint == null || avatarHint == "正在换……") return@LaunchedEffect
        delay(3000)
        avatarHint = null
    }

    // 一打开 App 就把常驻服务拉起来。
    // 之后就算她切走、锁屏，服务还在（前提是她加了电池白名单）。
    LaunchedEffect(Unit) {
        GardenService.start(ctx)
        while (true) {
            // ⚠️ 服务在独立进程里，静态变量读不到 —— 走心跳文件。
            //    详见 GardenService.isAlive 上面那段。
            serviceRunning = GardenService.isAlive(ctx)
            delay(3000)
        }
    }

    // 每 10 秒刷一次主页那两行：哥哥的状态 + 我的消息走到哪一步了。
    //
    // 第二行是她在等的关键 —— 从"等哥哥看"变成"哥哥看到啦"，
    // 那个变化必须自己冒出来，不能让她手动刷新或者退出去再进来。
    LaunchedEffect(Unit) {
        while (true) {
            when (val r = RelayClient.homeInfo()) {
                is ApiResult.Ok -> homeInfo = r.data
                is ApiResult.Err -> Unit   // 拉不到就保持上一次的，别把已有信息擦掉
            }
            when (val r = RelayClient.isQuiet()) {
                is ApiResult.Ok -> quiet = r.data
                is ApiResult.Err -> Unit
            }
            delay(10_000)
        }
    }

    // 每 15 秒看一眼阿木有没有留新东西。
    //
    // ⚠️ 原来只在 App 启动时查一次 —— 结果她开着 App 的时候他发语音、发留言，
    //    她这边毫无反应，得退出去重进才看得到。那太蠢了。
    //
    // 只取"上次看过的 id"之后的，所以不会重复弹。
    // 拉不到就保持现状，别在她面前甩红字。
    LaunchedEffect(Unit) {
        while (true) {
            when (val r = RelayClient.inbox()) {
                is ApiResult.Ok -> {
                    val prefs = ctx.getSharedPreferences("garden", Context.MODE_PRIVATE)
                    val lastSeen = prefs.getInt("lastSeenInboxId", 0)
                    val fresh = r.data.filter { it.id > lastSeen }
                    if (fresh.isNotEmpty()) {
                        // 追加而不是覆盖：弹窗正开着的时候他来新的，
                        // 应该接着往下显示，而不是把已经看到的顶掉
                        inbox = inbox + fresh
                        showInbox = true
                        prefs.edit().putInt("lastSeenInboxId", fresh.maxOf { it.id }).apply()
                    }
                }
                is ApiResult.Err -> Unit
            }
            delay(15_000)
        }
    }

    // 进到对话页之后，每隔几秒去服务器看一眼（主要是等阿木的回话）。
    // 轮询失败不弹错——她什么都没做，不该被一个红字打扰。
    LaunchedEffect(stage) {
        val sid = when (val s = stage) {
            is Stage.Talk -> s.sessionId
            is Stage.Voice -> s.sessionId
            else -> return@LaunchedEffect
        }
        while (true) {
            delay(AppConfig.THREAD_POLL_MS)
            when (val r = RelayClient.thread(sid, 0)) {
                is ApiResult.Ok -> turns = r.data
                is ApiResult.Err -> Unit
            }
        }
    }

    // 把最新的一句念出来。她说的不念，只念木木和阿木的。
    LaunchedEffect(turns) {
        if (turns.size > spokenCount) {
            turns.lastOrNull()?.let { last ->
                if (last.who != "her") speaker.speak(last.text)
            }
            spokenCount = turns.size
        }
    }

    val onPress: () -> Unit = {
        if (!busy) {
            busy = true
            error = null
            hint = null
            scope.launch {
                when (val r = RelayClient.ping()) {
                    is ApiResult.Ok -> {
                        val (sid, greeting) = r.data
                        spokenCount = 0
                        turns = listOf(
                            Turn("mumu", greeting, System.currentTimeMillis())
                        )
                        stage = Stage.Talk(sid)
                    }
                    is ApiResult.Err -> error = r.message
                }
                busy = false
            }
        }
    }

    val onSend: (String) -> Unit = { text ->
        val s = stage
        if (s is Stage.Talk && text.isNotBlank() && !busy) {
            busy = true
            error = null
            // 先把她的这句显示出来，别让她对着空白等一个来回
            turns = turns + Turn("her", text, System.currentTimeMillis())
            scope.launch {
                when (val r = RelayClient.say(s.sessionId, text)) {
                    is ApiResult.Ok -> {
                        // 让服务器的版本覆盖本地，顺序以服务器为准
                        when (val t = RelayClient.thread(s.sessionId, 0)) {
                            is ApiResult.Ok -> turns = t.data
                            is ApiResult.Err -> Unit
                        }
                    }
                    is ApiResult.Err -> {
                        error = r.message
                        // 发失败就把她那句撤回来，免得她以为已经说出去了
                        turns = turns.dropLast(1)
                    }
                }
                busy = false
            }
        }
    }

    // 她说完一段（音频已转成 base64 WAV）。服务器转文字 → 跟打字一样的路。
    val onVoice: (String) -> Unit = { b64 ->
        val s = stage
        if (s is Stage.Voice && !busy) {
            busy = true
            error = null
            scope.launch {
                when (val r = RelayClient.voice(s.sessionId, b64)) {
                    is ApiResult.Ok -> {
                        when (val t = RelayClient.thread(s.sessionId, 0)) {
                            is ApiResult.Ok -> turns = t.data
                            is ApiResult.Err -> Unit
                        }
                    }
                    is ApiResult.Err -> error = r.message
                }
                busy = false
            }
        }
    }

    // 回主页。三个地方都要用，写一份。
    val goHome: () -> Unit = {
        speaker.stop()
        stage = Stage.Home
        turns = emptyList()
        error = null
        spokenCount = 0
    }

    // 「小小的打扰一下」：不拨电话，只让他手机震一下。
    // 按完给她一句回音 —— 什么都不说的话，她会不确定那一下有没有传出去。
    val onKnock: () -> Unit = {
        if (!busy) {
            busy = true
            error = null
            hint = null
            scope.launch {
                when (val r = RelayClient.knock()) {
                    is ApiResult.Ok -> hint = "他知道啦"
                    is ApiResult.Err -> error = r.message
                }
                busy = false
            }
        }
    }

    // 「和木维斯通话」：开一轮新会话，进按住说话的界面。
    // 她难受的时候未必有心思打字，但说一句话的力气总是有的。
    val onCall: () -> Unit = {
        if (!busy) {
            busy = true
            error = null
            hint = null
            scope.launch {
                when (val r = RelayClient.ping()) {
                    is ApiResult.Ok -> {
                        val (sid, greeting) = r.data
                        spokenCount = 0
                        turns = listOf(Turn("mumu", greeting, System.currentTimeMillis()))
                        stage = Stage.Voice(sid)
                    }
                    is ApiResult.Err -> error = r.message
                }
                busy = false
            }
        }
    }

    // 「我不想用这个了」的出口。
    //
    // 按下去之后：通知全关（连危机都不推给他）、常驻服务停掉。
    // 但她还能按「想你啦」、还能在聊天里说话 ——
    // 那些是她主动来找他，不是"被通知吵"。
    val onQuiet: () -> Unit = {
        if (quiet) {
            // 回来
            scope.launch {
                when (RelayClient.setQuiet(false)) {
                    is ApiResult.Ok -> {
                        quiet = false
                        GardenService.start(ctx)
                    }
                    is ApiResult.Err -> Unit
                }
            }
        } else {
            showQuietDialog = true
        }
    }

    if (showInbox) {
        InboxDialog(items = inbox, onClose = { showInbox = false })
    }

    if (showQuietDialog) {
        QuietDialog(
            onConfirm = {
                showQuietDialog = false
                scope.launch {
                    when (RelayClient.setQuiet(true)) {
                        is ApiResult.Ok -> {
                            quiet = true
                            GardenService.stop(ctx)   // 停止打扰
                        }
                        is ApiResult.Err -> Unit
                    }
                }
            },
            onDismiss = { showQuietDialog = false }
        )
    }

    // 三站互斥：聊天、日历、或者对话页。
    //
    // ⚠️ 原来是三个各自 `return` 的 if。return 在 composable 里是提前跳出**整个函数**，
    //    写在它们后面的 LaunchedEffect 会跟着不组合 —— 现在看着没事，往后再加东西就是坑。
    //    改成 when 之后每个分支正常走完。
    when {
        // 还没配服务器就先把设置页推出来 —— 没地址没令牌，别的什么都做不了，
        // 让使用者对着一句"网络错误"猜是最糟的
        showSettings || !AppConfig.ready -> SettingsScreen(onBack = { showSettings = false })

        showChat -> ChatRoute(onBack = { showChat = false })

        showCalendar -> CalendarRoute(onBack = { showCalendar = false })

        showMusic -> MusicRoute(onBack = { showMusic = false })

        else -> when (val s = stage) {
            is Stage.Home -> {
                val pickAvatar = {
                    avatarPicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }

                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        // ── 消息：她这儿只有一个聊天对象，直接进聊天页
                        Tab.Messages -> Box(Modifier.padding(bottom = 72.dp)) {
                            ChatRoute(onBack = { tab = Tab.Home })
                        }

                        // ── 我的：只放设置类，不放功能入口
                        Tab.Mine -> ProfileScreen(
                            days = daysTogether(),
                            serviceRunning = serviceRunning,
                            busy = busy,
                            avatar = avatar,
                            avatarHint = avatarHint,
                            onPickAvatar = pickAvatar,
                            onSettings = { showSettings = true },
                        )

                        // ── 首页：一颗大按钮 + 四个快捷入口，就是全部
                        Tab.Home -> HomeScreen(
                            busy = busy,
                            error = error,
                            hint = hint,
                            info = homeInfo,
                            quiet = quiet,
                            onPress = onPress,
                            onQuiet = onQuiet,
                            onTalk = onCall,
                            onKnock = onKnock,
                            onCalendar = { showCalendar = true },
                            onMusic = { showMusic = true },
                        )
                    }

                    GardenBottomBar(
                        current = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            is Stage.Voice -> VoiceScreen(
                turns = turns,
                busy = busy,
                error = error,
                onVoice = onVoice,
                onBack = goHome
            )

            is Stage.Talk -> TalkScreen(
                turns = turns,
                busy = busy,
                error = error,
                onSend = onSend,
                onBack = goHome
            )
        }
    }
}

package com.garden.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.garden.app.MainActivity
import com.garden.app.R
import com.garden.app.core.MusicStore
import com.garden.app.core.net.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/** 放歌的几种方式。 */
enum class PlayMode { ORDER, LOOP_ALL, LOOP_ONE, SHUFFLE }

/**
 * 播放器现在的样子。
 *
 * 单向流出：服务写，界面读。界面【不】改它，只发指令（play/next/setMode）。
 *
 * ⚠️ 上一版是靠界面每秒轮询一个静态变量同步的 —— 能用，但那是土办法，
 *    而且最要命的不是土：**歌单在界面上，服务不知道自己手上这首是第几首**，
 *    所以"放完自动接下一首"根本做不了。歌单必须归服务。
 */
data class MusicState(
    val name: String? = null,          // 正在放哪首，null = 没在放
    val playing: Boolean = false,      // 在放还是在暂停
    val mode: PlayMode = PlayMode.LOOP_ALL,
    val order: List<String> = emptyList(),   // 歌单（名字，按显示顺序）
    val index: Int = -1,               // 正在放的是第几首
    val busy: Boolean = false,         // 正在把下一首拿下来
    val note: String? = null,          // 给界面看的一句话（拿不到歌之类的）
)

/**
 * 放歌的服务。
 *
 * ## 为什么要一个服务
 *
 * 一开始播放器挂在「一起听」那一页上（`DisposableEffect` 里 release），
 * 结果是**她退出这一页歌就断了** —— 而"退出去回个微信、歌还在放"是最起码的预期。
 * 页面一销毁，`MediaPlayer` 就跟着没，所以必须挪进服务。
 *
 * ## 为什么是【前台】服务
 *
 * Android 8 起普通后台服务活不过几分钟，一首歌三五分钟正好卡那个坎，
 * 表现就是"放到一半没声了"。前台服务（带一条通知）才能一直活着。
 * 那条通知不是负担，它正好是暂停/接着放的入口。
 *
 * ## 为什么歌单也归它
 *
 * 他 2026-09-18 要"放完自动接下一首"。要做到这件事，服务必须知道
 * 下一首是谁 —— 所以歌单从界面挪进来了。界面退化成纯粹的遥控器。
 *
 * ## 跟 GardenService 的关系
 *
 * 没有关系，是另一个服务，而且**故意不放进 :service 那个独立进程** ——
 * 那边是为了"界面崩了不连累常驻服务"，而音乐要跟界面共享播放状态，
 * 跨进程反而添乱。
 */
class MusicService : Service() {

    companion object {
        private const val TAG = "GardenMusic"

        private const val CH = "mumu_music"
        private const val NOTI = 102

        const val ACTION_PLAY = "com.garden.app.music.PLAY"
        const val ACTION_TOGGLE = "com.garden.app.music.TOGGLE"
        const val ACTION_NEXT = "com.garden.app.music.NEXT"
        const val ACTION_PREV = "com.garden.app.music.PREV"
        const val ACTION_STOP = "com.garden.app.music.STOP"

        private val _state = MutableStateFlow(MusicState())
        val state: StateFlow<MusicState> = _state.asStateFlow()

        /** 放某一首（歌单里的第几首由服务自己找）。 */
        fun play(ctx: Context, name: String) = send(ctx, ACTION_PLAY) { it.putExtra("name", name) }

        /** 暂停 / 继续。 */
        fun toggle(ctx: Context) = send(ctx, ACTION_TOGGLE)

        fun next(ctx: Context) = send(ctx, ACTION_NEXT)

        fun prev(ctx: Context) = send(ctx, ACTION_PREV)

        fun stop(ctx: Context) = send(ctx, ACTION_STOP)

        fun setMode(ctx: Context, mode: PlayMode) =
            send(ctx, ACTION_PLAY) { it.putExtra("mode", mode.name) }

        private fun send(ctx: Context, action: String, extra: (Intent) -> Unit = {}) {
            runCatching {
                ContextCompat.startForegroundService(
                    ctx,
                    Intent(ctx, MusicService::class.java).setAction(action).also(extra)
                )
            }.onFailure { Log.e(TAG, "拉不起放歌服务", it) }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return done()
        val name = intent.getStringExtra("name")
        val mode = intent.getStringExtra("mode")

        // 切模式不用挂通知 —— 它不改播放状态
        if (mode != null && action == ACTION_PLAY && name == null) {
            val m = runCatching { PlayMode.valueOf(mode) }.getOrNull()
            if (m != null) _state.value = _state.value.copy(mode = m)
            // 服务可能没在跑，这一趟只为改模式，改完就退
            if (_state.value.name == null) return done()
            return START_NOT_STICKY
        }

        // 前台服务必须在 5 秒内挂上通知，否则系统判定超时直接崩
        startForeground(NOTI, buildNotification(_state.value.name ?: "一起听"))

        when (action) {
            ACTION_PLAY -> if (name != null) startByName(name) else done()
            ACTION_TOGGLE -> togglePlay()
            ACTION_NEXT -> step(1)
            ACTION_PREV -> step(-1)
            ACTION_STOP -> {
                releasePlayer()
                _state.value = _state.value.copy(name = null, playing = false, index = -1)
                return done()
            }
            else -> return done()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releasePlayer()
        _state.value = _state.value.copy(name = null, playing = false, index = -1)
        scope.cancel()
        super.onDestroy()
    }

    // ── 播放 ────────────────────────────────────────────────

    /** 先确保歌单拿到了，再放这一首。 */
    private fun startByName(name: String) {
        scope.launch {
            val order = ensureOrder()
            val i = order.indexOf(name)
            if (i < 0) {
                // 歌单里没有它（比如刚删了）—— 也放，只是没有"下一首"可接
                playAt(-1, name)
            } else {
                playAt(i, name)
            }
        }
    }

    private suspend fun ensureOrder(): List<String> {
        val cur = _state.value.order
        if (cur.isNotEmpty()) return cur
        return when (val r = MusicStore.list()) {
            is ApiResult.Ok -> {
                val names = r.data.map { it.name }
                _state.value = _state.value.copy(order = names)
                names
            }
            is ApiResult.Err -> emptyList()
        }
    }

    /**
     * 放歌单里第 [index] 首。
     *
     * index 传 -1 表示"这一首不在歌单里"（直接按名字放）。
     */
    private suspend fun playAt(index: Int, name: String) {
        _state.value = _state.value.copy(busy = true, note = null)

        val f: File? = withContext(Dispatchers.IO) { MusicStore.ensure(this@MusicService, name) }
        if (f == null) {
            _state.value = _state.value.copy(
                busy = false,
                note = "这首没拿下来",
                playing = false
            )
            return
        }

        val mp = withContext(Dispatchers.IO) {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(f.absolutePath)
                    prepare()
                }
            }.getOrNull()
        }

        if (mp == null) {
            _state.value = _state.value.copy(busy = false, note = "这首放不出来", playing = false)
            return
        }

        releasePlayer()
        mp.setOnCompletionListener { onFinished() }
        mp.start()
        player = mp

        _state.value = _state.value.copy(
            name = name,
            playing = true,
            index = index,
            busy = false,
            note = null
        )
        notifyNow(name)
    }

    private fun togglePlay() {
        val p = player ?: return
        val playing = _state.value.playing
        if (playing) {
            runCatching { p.pause() }
            _state.value = _state.value.copy(playing = false)
        } else {
            runCatching { p.start() }
            _state.value = _state.value.copy(playing = true)
        }
        notifyNow(_state.value.name ?: "一起听")
    }

    /** 放完了 / 手动上一首下一首。 */
    private fun onFinished() {
        when (_state.value.mode) {
            // 单曲循环：原地重来
            PlayMode.LOOP_ONE -> {
                runCatching {
                    player?.seekTo(0)
                    player?.start()
                }
                return
            }
            else -> step(1, auto = true)
        }
    }

    /**
     * 上一首 / 下一首。
     *
     * [auto] = true 表示是"放完自动接的" —— 顺序模式下到末尾就停，
     * 而不是绕回第一首。她设成顺序就是想按顺序听完，绕回去是自作主张。
     */
    private fun step(delta: Int, auto: Boolean = false) {
        val st = _state.value
        val order = st.order
        if (order.isEmpty()) {
            _state.value = st.copy(playing = false)
            return
        }

        val nextIndex = when {
            st.mode == PlayMode.SHUFFLE -> {
                if (order.size == 1) 0
                else {
                    // 随机到同一首最烦人 —— 重摇，摇到不一样为止
                    var i: Int
                    do { i = Random.nextInt(order.size) } while (i == st.index)
                    i
                }
            }
            st.index < 0 -> 0
            else -> st.index + delta
        }

        if (nextIndex !in order.indices) {
            // 顺序模式走到头了
            if (auto && st.mode == PlayMode.ORDER) {
                _state.value = st.copy(playing = false)
                stopForegroundCompat()
                stopSelf()
            } else {
                // 手按的上一首/下一首，绕回去
                val wrap = if (nextIndex < 0) order.size - 1 else 0
                scope.launch { playAt(wrap, order[wrap]) }
            }
            return
        }

        scope.launch { playAt(nextIndex, order[nextIndex]) }
    }

    private fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun done(): Int {
        stopForegroundCompat()
        stopSelf()
        return START_NOT_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    // ── 通知 ────────────────────────────────────────────────

    private fun notifyNow(name: String) {
        runCatching {
            val m = getSystemService(NotificationManager::class.java) ?: return
            m.notify(NOTI, buildNotification(name))
        }
    }

    private fun buildNotification(name: String): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun svc(code: Int, action: String): PendingIntent = PendingIntent.getService(
            this, code,
            Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = R.drawable.ic_notification  // ⚠️ action 的 icon 不能传 0，传了按钮会被整个藏掉

        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(icon)
            .setContentTitle(name)
            .setContentText(if (_state.value.playing) "在放给你听" else "停下了")
            .setContentIntent(open)
            .addAction(icon, "下一首", svc(2, ACTION_NEXT))
            .addAction(icon, if (_state.value.playing) "暂停" else "接着放", svc(3, ACTION_TOGGLE))
            .setStyle(NotificationCompat.BigTextStyle().bigText(name))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)     // 换歌别一直叮叮叮
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CH) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CH, "一起听", NotificationManager.IMPORTANCE_LOW).apply {
                description = "正在放的歌"
                setShowBadge(false)
            }
        )
    }
}

package com.garden.app.service

import com.garden.app.core.config.Personas

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.garden.app.MainActivity
import com.garden.app.R
import com.garden.app.core.net.ApiResult
import com.garden.app.core.net.RelayClient
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 常驻服务：让「他发的东西」和「小园主动说的话」能弹到她手机上。
 *
 * ## 为什么必须常驻
 *
 * Android 从 8.0 起，普通后台进程活不过几分钟。只有【前台服务】
 * （带一条用户看得见的常驻通知）才能长期活着。
 * 代价是状态栏一直挂着一条「小园在陪着你」——这是用户知情且同意的。
 *
 * ## 荣耀那台机器的现实（2026-09-17 记）
 *
 * 她是 Magic UI 6.1（Android 12），杀后台很凶。
 * 不手动加白名单的话，锁屏几分钟就被干掉，
 * 表现是「她以为开着、其实早就收不到了」——那种假象比没有更糟。
 * 所以界面上必须有个地方能看「服务还活着吗」。
 *
 * ## 轮询间隔 15 秒
 *
 * 比他端那台的 8 秒松，因为她这台不是旗舰、而且她也没那么急。
 * 每个请求很小（服务端只回元信息，不回正文）。
 */
class GardenService : Service() {

    companion object {
        private const val TAG = "Garden"

        private const val CH_ONGOING = "mumu_ongoing"
        private const val CH_MSG = "mumu_message"
        private const val NOTI_ONGOING = 100
        private const val NOTI_MSG = 101

        /** 15 秒拉一次。再密就费电了。 */
        private const val POLL_MS = 15_000L

        /** 上次拉到的时间戳，存在这儿 —— 服务被杀重启后不会重复弹 */
        private const val PREFS = "mumu_service"
        private const val KEY_SINCE = "since"

        /** 心跳文件名。服务每轮写它，主进程读它判断「还活着吗」。 */
        private const val ALIVE = "alive"

        /**
         * 服务活着吗。主页要显示这个 ——
         * 荣耀那台机器杀后台很凶，"她以为开着其实早被杀"是最坑的情况，
         * 所以不能让她猜，得让她一眼看见。
         *
         * ⚠️ 2026-09-18 服务搬进独立进程（:service）之后，
         *    原来那个 `@Volatile var isRunning` 就【读不到了】——
         *    静态变量只在各自的进程里存在，主进程读它永远是 false，
         *    表现就是抽屉里一直挂着「弹不出消息了」，明明服务好好的。
         *    改成读心跳：服务每轮写一次时间戳，主进程读，
         *    90 秒没动静才当它没了（15 秒一轮，等于连着丢 6 次）。
         */
        fun isAlive(ctx: Context): Boolean = runCatching {
            val f = heartbeatFile(ctx)
            if (!f.exists()) return@runCatching false
            val at = f.readText().trim().toLongOrNull() ?: return@runCatching false
            System.currentTimeMillis() - at < 90_000L
        }.getOrDefault(false)

        private fun heartbeatFile(ctx: Context): File {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            return File(dir, ALIVE)
        }

        fun start(ctx: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    ctx, Intent(ctx, GardenService::class.java)
                )
            }.onFailure { Log.e(TAG, "拉不起服务", it) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, GardenService::class.java)) }
        }
    }

    private var loop: Thread? = null

    @Volatile
    private var running = false

    private var since = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        since = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_SINCE, 0L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台服务必须有通知，否则系统会判定它是普通后台服务、几秒后杀掉
        startForeground(NOTI_ONGOING, buildOngoing())
        beat()

        if (!running) {
            running = true
            startLoop()
        }
        // START_STICKY：被系统杀了之后尽量拉回来。
        // 注意这只是"请求"，荣耀那种激进的省电策略未必买账 ——
        // 真正的保命符是用户手动加的电池白名单。
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        loop?.interrupt()
        loop = null
        // 心跳删掉，主进程立刻就知道停了 —— 不用干等 90 秒超时
        runCatching { heartbeatFile(this).delete() }
        super.onDestroy()
    }

    /**
     * Android 15+ 的前台服务超时回调。
     *
     * `dataSync` 类型在 Android 15 上有「24 小时内累计 6 小时」的硬上限，
     * 到点系统会调这里。**不覆写的话服务被静默掐掉**，而它呈现出来的病象
     * 恰好就是心跳文件当初要防的那一种 ——「她以为开着，其实早就收不到了」。
     *
     * ⚠️ 这里做的是「留痕 + 尽力拉起」，**不是根治**：
     *    Android 12 起对「后台启动前台服务」本身有严格限制，能不能拉回来
     *    取决于系统给不给这个豁免。真正的解法是重新设计常驻方式
     *    （换前台服务类型 / 改 WorkManager 周期任务），见 README 待办。
     *
     * 她这台是 Android 12，不发作；这条是给开源版和以后换机准备的。
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        logFile("前台服务超时 fgsType=$fgsType，调度一次拉起")
        runCatching {
            val am = getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getForegroundService(
                this,
                0,
                Intent(this, GardenService::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // 用不精确的 setAndAllowWhileIdle，不去要精确闹钟权限：
            // USE_EXACT_ALARM 在 Google Play 只批闹钟/日历类应用，
            // 为「30 秒后重试一次」换一个上架风险不划算。
            am?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 30_000L,
                pi
            )
        }
        stopSelf()
    }

    /** 告诉主进程「我还活着」。每轮一次。 */
    private fun beat() {
        runCatching {
            heartbeatFile(this).writeText(System.currentTimeMillis().toString())
        }
    }

    private fun startLoop() {
        loop = Thread({
            while (running) {
                try {
                    pollOnce()
                } catch (e: Exception) {
                    Log.e(TAG, "轮询出错", e)
                }
                // 心跳不管这轮成没成都得打 —— 主进程问的是「服务还在吗」，
                // 不是「网络通吗」。网断了它也还在守着她。
                beat()
                try {
                    Thread.sleep(POLL_MS)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }, "mumu-poll").apply {
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private fun pollOnce() {
        val r = runBlocking { RelayClient.herPoll(since) }

        if (r is ApiResult.Err) {
            // 一定要留痕。她手机上出问题没有别的线索 ——
            // "服务在跑但什么都没弹"这种事，只有日志能说清是网络不通还是逻辑没走到。
            Log.e(TAG, "拉取失败：${r.message}（since=$since）")
            logFile("拉取失败：${r.message}")
            return
        }

        val data = (r as ApiResult.Ok).data
        since = data.now
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_SINCE, since).apply()

        if (data.messages.isNotEmpty()) {
            Log.i(TAG, "收到他发的 ${data.messages.size} 条")
            logFile("收到他发的 ${data.messages.size} 条")
            val last = data.messages.last()
            val body = when {
                last.text.isNotBlank() -> last.text
                last.hasAudio -> "一段语音，进小园听听"
                last.hasImage -> "一张图，进小园看看"
                else -> "进小园看看"
            }
            notifyMsg("${Personas.himCall}发来一条", body)
        }

        data.proactive?.let {
            Log.i(TAG, "小园主动开口：${it.take(20)}")
            notifyMsg("小园", it)
        }
    }

    private fun buildOngoing(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("小园在陪着你")
            .setContentText("他发来的东西会弹给你")
            .setContentIntent(pi)
            .setOngoing(true)                    // 划不掉 —— 常驻通知就该这样
            .setPriority(NotificationCompat.PRIORITY_MIN)   // 尽量别打扰她
            .build()
    }

    /**
     * 把过程写进文件。
     *
     * ⚠️ 为什么要这么土：她装的是 **release 版**（debuggable=false），
     * 而这台机器（vivo/OriginOS）**会把 release 版的三方 logcat 掐干净** ——
     * 实测 `adb logcat --pid=<pid>` 一行都拿不到。
     * 他端能看日志是因为它一直跑的是 debug 版。别把这条结论推广错了。
     *
     * 文件在 /sdcard/Android/data/com.garden.app/files/service.log，adb 能直接 cat。
     */
    private fun logFile(msg: String) {
        runCatching {
            val dir = getExternalFilesDir(null) ?: filesDir
            val f = File(dir, "service.log")
            if (f.exists() && f.length() > 64 * 1024) f.writeText("")
            f.appendText("${System.currentTimeMillis()} $msg\n")
        }
    }

    private fun notifyMsg(title: String, body: String) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager == null) {
                logFile("notifyMsg: manager 是 null")
                return
            }
            val pi = PendingIntent.getActivity(
                this, 1,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            manager.notify(
                NOTI_MSG,
                NotificationCompat.Builder(this, CH_MSG)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
            )
            logFile("notifyMsg 成功：$title / ${body.take(20)}")
        }.onFailure {
            Log.e(TAG, "弹通知失败", it)
            logFile("notifyMsg 失败：${it.javaClass.simpleName} ${it.message}")
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        // 常驻那条：最低优先级，不响不震，就安安静静挂在那儿
        if (manager.getNotificationChannel(CH_ONGOING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CH_ONGOING, "小园在后台", NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "让${Personas.aiName}能一直收到${Personas.himCall}的消息"
                    setShowBadge(false)
                }
            )
        }

        // 消息那条：会响会震 —— 这是有正经事找她
        if (manager.getNotificationChannel(CH_MSG) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CH_MSG, "${Personas.himCall}的消息", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "${Personas.himCall}发来东西、或者${Personas.aiName}来找你说话"
                    setShowBadge(true)
                }
            )
        }
    }
}

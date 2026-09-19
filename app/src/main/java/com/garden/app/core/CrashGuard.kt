package com.garden.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.garden.app.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 最后一道兜底。
 *
 * FeatureBoundary 兜住的是【组合期间】的异常。兜不住的 ——
 * 布局、绘制、事件回调、后台线程 —— 会一路冒到主线程，
 * Android 的默认反应是弹一句「应用已停止运行」然后把 App 关掉。
 *
 * 她那边看到的就是：App 突然没了。她不会截图给我们，她只会不用了。
 *
 * 所以这里做两件事：
 * ① 把崩溃栈写进 files/crash.log —— release 版 logcat 被 vivo/荣耀掐了，
 *    只剩这个文件是我们能拿到的唯一证据
 * ② 隔不到一秒自己把它拉回来。她看到的是屏幕闪了一下，App 还在
 *
 * ⚠️ 60 秒内崩够 2 次就不拉了 —— 那说明是必崩的死循环，
 *    硬拉会变成疯狂闪屏，比退出还吓人。这种情况老老实实交给系统。
 */
object CrashGuard {

    private const val PREF = "garden_crash"
    private const val WINDOW_MS = 60_000L
    private const val MAX_IN_WINDOW = 2

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            // 这里自己绝不能再抛 —— 抛了就真的什么都没有了
            runCatching {
                writeLog(app, e)
                if (mayRestart(app)) pullBackUp(app)
            }
            prev?.uncaughtException(t, e)
        }
    }

    private fun writeLog(ctx: Context, e: Throwable) {
        val dir = ctx.getExternalFilesDir(null) ?: return
        val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        File(dir, "crash.log").appendText(
            "\n===== $ts =====\n" +
                    "thread=${Thread.currentThread().name}\n" +
                    Log.getStackTraceString(e) + "\n"
        )
    }

    private fun mayRestart(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val recent = p.getString("times", "").orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { now - it < WINDOW_MS }

        p.edit().putString("times", (recent + now).joinToString(",")).apply()
        return recent.size < MAX_IN_WINDOW
    }

    /**
     * 拉回来。
     *
     * 不用 startActivity 直接调 —— 主线程已经废了，调了也未必画得出来。
     * 交给闹钟，一秒后由系统去拉，然后我们自己把进程结束掉，
     * 下一次启动就是干干净净的一份。
     */
    private fun pullBackUp(ctx: Context) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val at = System.currentTimeMillis() + 900

        // setExact 在 Android 12+ 要有精确闹钟权限。这项目里已经声明了
        // USE_EXACT_ALARM，但万一哪天被系统收回，不能让这里再抛一次 ——
        // 降级成不精确的 set，晚几秒起来总比起不来强。
        val exact = runCatching { am?.setExact(AlarmManager.RTC, at, pi) }.isSuccess
        if (!exact) runCatching { am?.set(AlarmManager.RTC, at, pi) }

        Process.killProcess(Process.myPid())
        kotlin.system.exitProcess(10)
    }
}

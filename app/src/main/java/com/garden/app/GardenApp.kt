package com.garden.app

import android.app.Application
import com.garden.app.core.CrashGuard
import com.garden.app.core.config.AppConfig

/**
 * Application 做两件事：读配置、把崩溃兜底装上。
 *
 * 都装在 Application 而不是 Activity —— 常驻服务跟界面是分开拉起来的，
 * 服务那边崩了同样要把证据留下来，也得同样能自己回来；
 * 配置同理，服务也要用，不能等界面起来才读。
 */
class GardenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 必须最先 —— 后面 CrashGuard、服务、界面都要读它
        AppConfig.init(this)
        CrashGuard.install(this)
    }
}

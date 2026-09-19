package com.garden.app.core.config

import android.content.Context
import android.content.SharedPreferences
import com.garden.app.BuildConfig

/**
 * 服务器地址与令牌 —— 全 App 唯一的一份。
 *
 * 两级来源，优先级从高到低：
 *   ① **运行时**：App 里「设置」填的，存 SharedPreferences。
 *      改服务器地址不用重新编译、不用重装 —— 这是开源版给别人用的主要方式。
 *   ② **编译时**：`local.properties` 里的 `garden.baseUrl` / `garden.token`
 *      注入成 BuildConfig 的默认值。开发者（和我们自己）预填一次，
 *      装到手机上打开就能用，不必手填。
 *
 * ⚠️ 这个 App 里【没有】任何模型 API Key —— DeepSeek 的调用全在服务器侧完成。
 *    APK 传出去也拿不到密钥；而且提示词和护栏改一次，不用让使用者重装。
 *
 * ⚠️ 明文 HTTP：原来这台服务器只能走 IP 直连（域名被备案拦截（备案拦的是 SNI，一握手就 RST），
 *    SNI 一握手就被 RST，证书也过期了），所以 network_security_config.xml
 *    里放开了明文。开源版是**全局**放开 —— 那个文件是编译期的、运行时改不了，
 *    不放开的话使用者填自己的地址会被 Android 直接掐断。
 *    生产环境请自行配 HTTPS。
 */
object AppConfig {

    private const val PREFS = "garden_config"
    private const val K_BASE_URL = "base_url"
    private const val K_TOKEN = "token"

    private var prefs: SharedPreferences? = null

    /** 在 Application.onCreate 里调一次（见 GardenApp）。 */
    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 服务器地址，形如 `http://192.168.1.10:9394/api`。 */
    val baseUrl: String
        get() = prefs?.getString(K_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_BASE_URL

    /** 这台设备的令牌。服务器靠它认出「这是谁」。 */
    val token: String
        get() = prefs?.getString(K_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_TOKEN

    /** 配置齐了吗 —— 没齐就该把设置页推到她面前。 */
    val ready: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    /**
     * 健康检查地址。
     *
     * baseUrl 是 `…/api`，而 `/health` 在上一级（不带 api 后缀），
     * 所以把尾巴上的 `/api` 去掉再拼。
     */
    val healthUrl: String
        get() = healthUrlOf(baseUrl)

    /** 设置页要拿"输入框里还没保存的地址"试连，所以单独开一个。 */
    fun healthUrlOf(base: String): String =
        base.trim().trimEnd('/').removeSuffix("/api").trimEnd('/') + "/health"

    fun save(baseUrl: String, token: String) {
        prefs?.edit()
            ?.putString(K_BASE_URL, baseUrl.trim())
            ?.putString(K_TOKEN, token.trim())
            ?.apply()
    }

    /** 清掉运行时配置，退回编译时的默认值。 */
    fun clear() {
        prefs?.edit()?.remove(K_BASE_URL)?.remove(K_TOKEN)?.apply()
    }

    /** 在对话页里，多久去服务器看一眼有没有回话（毫秒）。 */
    const val THREAD_POLL_MS = 5000L
}

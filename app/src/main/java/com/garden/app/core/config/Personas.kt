package com.garden.app.core.config

import com.garden.app.BuildConfig

/**
 * 称呼。
 *
 * 以前「他」「她」这些字直接写死在各个页面的 `Text("...")` 里，
 * 散在十来个文件，改一次要满项目找 —— 开源给别人用时根本没法改。
 * 现在统一从这里出，值来自 `local.properties`（编译期注入），
 * 不填就用括号里的默认值。
 *
 * ⚠️ 新增称呼一律加到这里，别在页面里写死。
 */
object Personas {

    /** AI 自称 / App 里那个陪着说话的人。默认「小园」。 */
    val aiName: String get() = BuildConfig.AI_NAME

    /** 他。气泡上的标签、文案里的第三人称用它。默认「他」。 */
    val himName: String get() = BuildConfig.HIM_NAME

    /** 她对他的称呼 —— 「想哥哥了就点下面那个」里的那个词。默认「哥哥」。 */
    val himCall: String get() = BuildConfig.HIM_CALL

    /** 他对她的称呼 —— 「马上来找老婆」里的那个词。默认「老婆」。 */
    val herCall: String get() = BuildConfig.HER_CALL

    /** 她本人。抽屉和「我的」页显示的名字。默认「她」。 */
    val herName: String get() = BuildConfig.HER_NAME
}

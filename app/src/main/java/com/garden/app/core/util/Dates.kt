package com.garden.app.core.util

import com.garden.app.BuildConfig
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 在一起多少天了。
 *
 * 基准 2026-06-01（确定关系那天），**当天算第 1 天**。
 *
 * 放在 core 而不是 home：主页和抽屉都要用它，
 * 让壳去依赖某一个功能模块，方向就反了。
 */
fun daysTogether(): Int {
    // 起算日在 local.properties 的 garden.startDate 里配（格式 yyyy-MM-dd）。
    // 解析不出来就退回原来那个日子，不至于让整页崩掉。
    val start = runCatching { LocalDate.parse(BuildConfig.START_DATE) }
        .getOrElse { LocalDate.now() }
    return ChronoUnit.DAYS.between(start, LocalDate.now()).toInt() + 1
}

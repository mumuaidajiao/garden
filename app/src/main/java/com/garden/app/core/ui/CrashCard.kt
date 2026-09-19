package com.garden.app.core.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「这儿卡了一下」。
 *
 * ⚠️ 为什么不做一个能自动捕获异常的 FeatureBoundary ——
 *
 *    Compose 编译器【明确禁止】把 composable 调用包进 try-catch：
 *        e: Try catch is not supported around composable function invocations.
 *    这不是写法问题，是组合模型本身不允许（组合可能被挂起、被取消、被重组，
 *    try-catch 的语义在那个世界里不成立）。
 *
 *    所以想在「某一站」这一层拦住异常是做不到的。真正能拦住异常的地方有两处：
 *      ① [safeRun] —— 把会出错的计算挪出 composable，那里是普通函数，能 catch
 *      ② CrashGuard —— 进程级兜底，管住所有漏出去的
 *
 *    这个组件剩下一个用途：feature 自己知道出事了（比如拿到的数据是坏的），
 *    主动拿出来显示，而不是甩一屏红字或者空白给她。
 */
@Composable
fun CrashCard(
    hint: String = "这儿卡了一下",
    detail: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = hint,
                fontSize = GardenText.title,
                color = TextMain,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (onRetry != null) "别的地方还能用" else "换个地方看看",
                fontSize = GardenText.small,
                color = TextDim,
                textAlign = TextAlign.Center
            )

            if (onRetry != null) {
                Spacer(Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PinkDeep)
                        .clickable { onRetry() }
                        .padding(horizontal = 26.dp, vertical = 9.dp)
                ) {
                    Text("点一下重来", fontSize = GardenText.body, color = Color.White)
                }
            }

            // 小到几乎看不见，但她在电话里念给我们听的时候用得上
            if (detail != null) {
                Spacer(Modifier.height(18.dp))
                Text(text = detail, fontSize = GardenText.tiny, color = TextDim.copy(alpha = 0.35f))
            }
        }
    }
}

/**
 * 把一段【普通计算】包起来，出错就退回 fallback。
 *
 * 它只能包非 composable 的东西：解析、日期换算、列表变换、字符串处理。
 * 这些恰恰是最常崩的地方 —— 而它们本来就不该写在 composable 里。
 *
 * 用法：
 * ```
 * val rows = safeRun("日历", emptyList()) { buildMonthGrid(year, month) }
 * ```
 * 崩了就是少显示几行，不是整页白掉。
 */
inline fun <T> safeRun(tag: String, fallback: T, block: () -> T): T =
    try {
        block()
    } catch (e: Throwable) {
        Log.e("garden", "「$tag」算出错了", e)
        fallback
    }

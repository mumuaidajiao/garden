package com.garden.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.garden.app.core.config.Personas

/**
 * 气泡归谁。
 *
 * - [Her] 她说的话：靠右，紫渐变，白字
 * - [Ai]  小园说的话：靠左，深色
 * - [Him] 他本人：靠左，暖金 + 金描边（跟他平时那个通道区分开）
 */
enum class From { Her, Ai, Him }

/**
 * 一条消息气泡。
 *
 * 2026-09-19 抽出来的 —— 在这之前 **TalkScreen / VoiceScreen / ChatScreen
 * 各写了一份**（前两份甚至是逐行一样的拷贝），改个圆角要动三个文件。
 *
 * 这里只管容器：对齐、底色、描边、圆角、内边距、可选的时间戳。
 * 里面装什么由调用方给（纯文字 / 图片 / 语音），所以聊天页那三种子形态
 * 也能用同一个壳。
 */
@Composable
fun Bubble(
    from: From,
    modifier: Modifier = Modifier,
    time: String? = null,
    maxWidth: Dp = 280.dp,
    radius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val mine = from == From.Her
    val shape = RoundedCornerShape(radius)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (time != null) {
            Text(time, fontSize = GardenText.tiny, color = TextDim.copy(alpha = 0.55f))
            Spacer(Modifier.height(3.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)   // 再窄点也不至于贴边
                .clip(shape)
                .background(bubbleBrush(from))
                .then(if (from == From.Him) Modifier.border(1.dp, HimGold, shape) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            content = content,
        )
    }
}

/** 气泡底色。 */
fun bubbleBrush(from: From): Brush = when (from) {
    From.Her -> Brush.linearGradient(listOf(PurpleLight, PurpleDeep))
    From.Him -> Brush.linearGradient(listOf(HimBubbleBottom, HimBubbleTop))
    From.Ai -> Brush.linearGradient(listOf(CardBgAlt, CardBgAlt))
}

/** 气泡里正文的颜色：她的是白字（紫底上），其余用主文字色。 */
fun bubbleTextColor(from: From): Color = if (from == From.Her) Color.White else TextMain

/**
 * 「他」小标签。对话页用它 —— 聊天页不用（那边靠左右和时间戳区分）。
 */
@Composable
fun HimTag() {
    Text(
        text = Personas.himName,
        fontSize = GardenText.caption,
        color = HimGold,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(4.dp))
}

/**
 * 把 `"her"` / `"him"` / 其他 转成 [From]。
 *
 * 服务器和两端的历史数据都用这个字符串存 who，别在页面里各写一遍 when。
 */
fun fromWho(who: String): From = when (who) {
    "her" -> From.Her
    "him" -> From.Him
    else -> From.Ai
}

/**
 * 一条纯文字气泡 —— 对话页（Talk / Voice）用它。
 *
 * him 的那条会自动带上名字标签，让"他的话"一眼跟小园说的话分开。
 * 聊天页结构更复杂（图 / 语音 / 时间戳），直接用 [Bubble] 自己拼。
 */
@Composable
fun TextBubble(who: String, text: String) {
    val from = fromWho(who)
    Bubble(from = from) {
        if (from == From.Him) HimTag()
        Text(text, fontSize = GardenText.title, color = bubbleTextColor(from))
    }
}

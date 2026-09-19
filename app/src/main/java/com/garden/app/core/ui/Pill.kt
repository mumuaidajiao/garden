package com.garden.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * 药丸按钮 / 标签。
 *
 * 在这之前全项目手写了十几遍这个模式：
 * ```
 * .clip(RoundedCornerShape(50)).background(PinkDeep).clickable{}.padding(14.dp, 7.dp)
 * ```
 * 圆角、内边距、字号的数值各页还不完全一样。现在统一从这里出。
 *
 * - 实心：默认（粉底白字）
 * - 描边：给 [outline] 一个颜色，底色就透明了（「摇他一下」那种）
 * - 发光：给 [glow] = true，深色底上这层柔光就是设计稿的质感来源
 */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bg: Color = PinkDeep,
    fg: Color = Color.White,
    outline: Color? = null,
    fontSize: TextUnit = GardenText.caption,
    glow: Boolean = false,
) {
    val shape = GardenShapes.chip

    Text(
        text = text,
        fontSize = fontSize,
        color = if (outline != null) outline else fg,
        modifier = modifier
            .then(if (glow) Modifier.glow(if (outline != null) outline else bg, 12.dp, shape) else Modifier)
            .clip(shape)
            .then(
                if (outline != null) Modifier.border(1.dp, outline, shape)
                else Modifier.background(bg)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

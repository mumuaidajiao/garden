package com.garden.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 卡片。
 *
 * ⚠️ **目前没有任何调用点。** 那些页面至今仍是内联写法：
 * HomeScreen / CalendarScreen / InboxDialog 里各写各的
 * `clip(RoundedCornerShape(16.dp)).background(CardBg)`。
 *
 * 「收编那 7 处」是当初的目标，**没有落地** —— 别让后来者（也包括 AI）
 * 以为这次重构已经完成了。要么把调用点迁过来，要么连同这个文件一起删掉。
 */
@Composable
fun GardenCard(
    modifier: Modifier = Modifier,
    color: Color = CardBg,
    radius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .padding(contentPadding),
        content = content,
    )
}

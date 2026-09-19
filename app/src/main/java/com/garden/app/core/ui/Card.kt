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
 * 收编原来散在 Home / Calendar / Inbox 里那 7 处
 * `clip(RoundedCornerShape(16.dp)).background(CardBg)`。
 * 底色改由 [CardBg] 统一给，换肤时只改 Theme.kt。
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

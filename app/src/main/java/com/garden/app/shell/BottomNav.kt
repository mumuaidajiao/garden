package com.garden.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garden.app.core.ui.BgTop
import com.garden.app.core.ui.GardenText
import com.garden.app.core.ui.PinkDeep
import com.garden.app.core.ui.PinkLight
import com.garden.app.core.ui.TextDim
import com.garden.app.core.ui.glow

/**
 * 底下那三格。
 *
 * 2026-09-19 加的（设计稿里的结构改动）。在这之前入口全在左上角的抽屉里 ——
 * 想看一眼他发来的东西，得先知道要往左划、再在五条里找。现在主要的三个地方
 * 摆在最底下，一眼就能看见。
 *
 * 抽屉**已经删了** —— `GardenDrawer` 这个类现在不存在，主页左上角也没有三条杠。
 * 2026-09-19 那次入口收敛之后，全部入口就是下面这三格 + 首页的快捷入口。
 */
enum class Tab(val label: String, val icon: String) {
    Home("首页", "🏠"),
    Messages("消息", "💬"),
    Mine("我的", "👤"),
}

@Composable
fun GardenBottomBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BgTop.copy(alpha = 0.94f))
            .navigationBarsPadding()   // 不避让会撞上系统手势条
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = tab.icon,
                    fontSize = GardenText.heading,
                    // 选中的那一格带一层柔光 —— 深色底上比换个颜色更明显
                    modifier = if (on) Modifier.glow(PinkDeep, 10.dp, RoundedCornerShape(50)) else Modifier,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    fontSize = GardenText.label,
                    color = if (on) PinkLight else TextDim.copy(alpha = 0.8f),
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

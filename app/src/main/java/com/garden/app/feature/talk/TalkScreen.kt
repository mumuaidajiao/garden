package com.garden.app.feature.talk
import com.garden.app.core.ui.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garden.app.core.config.Personas
import com.garden.app.core.net.Turn

/**
 * 对话页。
 *
 * 三种气泡：
 *   her  —— 她说的（右侧）
 *   mumu —— 小园说的（左侧）
 *   him  —— 他本人提前写好的原话（左侧，金色描边）
 *
 * him 这种必须一眼看出来不一样：那种时刻她要看到的是「他的话」，
 * 不能跟小园说的话混成一个样子。
 */
@Composable
fun TalkScreen(
    turns: List<Turn>,
    busy: Boolean,
    error: String?,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 有新消息就滚到底
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    // targetSdk 36 起系统强制 edge-to-edge，三种 insets 都得自己避让：
    //   statusBarsPadding  —— 不然顶栏被时间和电量压在底下
    //   imePadding         —— 不然键盘弹起来直接盖住输入框，打过的字看不见
    //                         （adjustResize 在 edge-to-edge 之后不再自动让位了）
    //   navigationBarsPadding（在下面输入区那一行）—— 不然撞手势条
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {

        // ── 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 给足 44dp 的触摸目标 —— 之前只有 padding，小得几乎点不中
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "‹", fontSize = GardenText.glyph, color = TextDim)
            }
            Spacer(Modifier.width(8.dp))
            Text(Personas.aiName, fontSize = GardenText.heading, color = TextMain, fontWeight = FontWeight.Medium)

            Spacer(Modifier.weight(1f))

            // 「我先走啦」。
            //
            // 跟左边的 ‹ 是两回事：那个是"返回"，这个是"告别"。
            // 她随时可以走，不用解释、不用道别得好看 —— 点一下就走，
            // 小园不会追问，也不会显示"你确定吗"。她要是想回来，随时还在。
            Text(
                text = "我先走啦",
                fontSize = GardenText.body,
                color = TextDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onBack() }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }

        // ── 消息区
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(turns) { t -> TextBubble(t.who, t.text) }
        }

        // ── 错误提示（断网时必须说清楚，不能假装送到了）
        if (error != null) {
            Text(
                text = error,
                fontSize = GardenText.body,
                color = PinkLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }

        // ── 输入区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(FieldBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (input.isEmpty()) {
                    Text("想说什么都行", fontSize = GardenText.title, color = TextDim)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = TextMain, fontSize = GardenText.title),
                    cursorBrush = SolidColor(PinkLight),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (busy || input.isBlank()) DisabledBg
                        else PinkDeep
                    )
                    .clickable(enabled = !busy && input.isNotBlank()) {
                        onSend(input.trim())
                        input = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("↑", fontSize = GardenText.button, color = Color.White)
            }
        }
    }
}


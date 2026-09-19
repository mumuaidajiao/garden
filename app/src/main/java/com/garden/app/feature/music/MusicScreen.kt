package com.garden.app.feature.music
import com.garden.app.core.ui.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garden.app.core.net.MusicTrack
import com.garden.app.service.MusicState
import com.garden.app.service.PlayMode

/**
 * 「伴奏 · 一起听」。
 *
 * 一张歌单，点一首就放。歌在服务器上，听过的存在手机里，下次再点不用重新下。
 *
 * 歌多了就得能找 —— 上面那个框是本机过滤，不打服务器。
 * 播放的活儿全在 [com.garden.app.service.MusicService] 里，这一页只是遥控器：
 * 它读 [MusicState]（服务流出来的），按键发指令。
 */
@Composable
fun MusicScreen(
    tracks: List<MusicTrack>,
    localNames: Set<String>,
    loading: Boolean,
    error: String?,
    hint: String?,
    state: MusicState,
    onPlay: (MusicTrack) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onCycleMode: () -> Unit,
    onUpload: () -> Unit,
    onBack: () -> Unit
) {
    // 搜索词是这一页自己的事，不用外面管
    var query by remember { mutableStateOf("") }
    val shown = remember(tracks, query) {
        val q = query.trim()
        if (q.isEmpty()) tracks
        else tracks.filter {
            it.name.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .statusBarsPadding()
    ) {
        // ── 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = GardenText.glyph, color = TextDim) }

            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("一起听", fontSize = GardenText.heading, color = TextMain, fontWeight = FontWeight.Medium)
                Text(
                    text = when {
                        state.busy -> "正在拿下一首……"
                        state.name != null && state.playing -> "正在放 · ${state.name}"
                        state.name != null -> "停下了 · ${state.name}"
                        else -> "挑一首，我陪你听"
                    },
                    fontSize = GardenText.label,
                    color = if (state.playing) PinkLight else TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 播放模式：点一下换下一个
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(FieldBg)
                    .clickable { onCycleMode() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(modeLabel(state.mode), fontSize = GardenText.caption, color = HimGold)
            }
        }

        // ── 找歌
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TrackRowBg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (query.isEmpty()) {
                Text("找歌……", fontSize = GardenText.body, color = TextDim.copy(alpha = 0.6f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextMain, fontSize = GardenText.body),
                    cursorBrush = SolidColor(PinkLight),
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    Text(
                        "✕",
                        fontSize = GardenText.small,
                        color = TextDim,
                        modifier = Modifier
                            .clickable { query = "" }
                            .padding(start = 8.dp)
                    )
                }
            }
        }

        val line = error ?: hint ?: state.note
        if (line != null) {
            Text(
                text = line,
                fontSize = GardenText.caption,
                color = if (error != null || state.note != null) PinkLight else TextDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // ── 歌单
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && tracks.isEmpty() -> Center("正在拿歌单……")
                tracks.isEmpty() -> Center("这儿还空着\n点下面那行，从手机里传一首上来")
                shown.isEmpty() -> Center("没找着「$query」")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(shown) { t ->
                        TrackRow(
                            track = t,
                            local = localNames.contains(t.name),
                            playing = state.name == t.name && state.playing,
                            current = state.name == t.name,
                            busy = state.busy && state.name == t.name,
                            onClick = { onPlay(t) }
                        )
                    }
                }
            }
        }

        // ── 播放控制（有歌在放才出现）
        if (state.name != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CtrlBtn("⏮") { onPrev() }
                Spacer(Modifier.width(18.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush2.pink)
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.playing) "❚❚" else "▶",
                        fontSize = GardenText.heading,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(18.dp))
                CtrlBtn("⏭") { onNext() }
            }
        }

        // ── 传歌
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBgAlt)
                    .clickable { onUpload() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("从手机里挑一首传上去", fontSize = GardenText.body, color = PinkLight)
            }
        }
    }
}

/** 播放模式的中文名。 */
private fun modeLabel(m: PlayMode): String = when (m) {
    PlayMode.ORDER -> "顺序播放"
    PlayMode.LOOP_ALL -> "列表循环"
    PlayMode.LOOP_ONE -> "单曲循环"
    PlayMode.SHUFFLE -> "随机播放"
}

/** 模式按钮点一下换下一个。顺序：列表循环 → 单曲循环 → 随机 → 顺序。 */
fun nextMode(m: PlayMode): PlayMode = when (m) {
    PlayMode.LOOP_ALL -> PlayMode.LOOP_ONE
    PlayMode.LOOP_ONE -> PlayMode.SHUFFLE
    PlayMode.SHUFFLE -> PlayMode.ORDER
    PlayMode.ORDER -> PlayMode.LOOP_ALL
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text, fontSize = GardenText.body, color = TextDim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CtrlBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CardBgAlt)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = GardenText.titleLg, color = TextMain)
    }
}

/** 播放按钮那个粉色圆。 */
private object Brush2 {
    val pink = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(PinkLight, PinkDeep)
    )
}

/**
 * 歌单里的一行。
 *
 * 左边那个圆是播放键 —— 正在放的时候变成 ■，再点一下就是停。
 */
@Composable
private fun TrackRow(
    track: MusicTrack,
    local: Boolean,
    playing: Boolean,
    current: Boolean,
    busy: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (current) TrackRowOn else TrackRowBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (current) PinkDeep else CardBgAlt),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (playing) "■" else "▶",
                fontSize = if (playing) 13.sp else 15.sp,
                color = if (current) Color.White else PinkLight
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                fontSize = GardenText.body,
                color = if (current) PinkLight else TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    busy -> "正在拿……"
                    playing -> "在放"
                    current -> "停下了"
                    local -> "${mb(track.size)} · 已经在你手机里了"
                    else -> "${mb(track.size)} · 点一下就拿过来"
                },
                fontSize = GardenText.label,
                color = TextDim.copy(alpha = 0.8f)
            )
        }
    }
}

private fun mb(bytes: Long): String =
    if (bytes <= 0) "" else "%.1f MB".format(bytes / 1024.0 / 1024.0)

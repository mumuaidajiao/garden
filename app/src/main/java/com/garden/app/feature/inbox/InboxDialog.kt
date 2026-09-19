package com.garden.app.feature.inbox

import com.garden.app.core.config.Personas
import com.garden.app.core.ui.*

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garden.app.core.net.InboxItem
import com.garden.app.core.util.decodeDataUrl
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「他留给你的」—— 有新东西的时候弹一次。
 *
 * 用暖金色，跟小园说的话区分开：这些是【他本人】留的，
 * 跟小园转述的不是一回事。
 *
 * 弹窗是一次性的（看过就不弹了），想看全部去 InboxScreen。
 */
@Composable
fun InboxDialog(items: List<InboxItem>, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "他留给你的",
                fontSize = GardenText.titleLg,
                color = HimGold,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(14.dp))

            items.forEach { item -> InboxEntry(item) }

            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(50))
                    .background(PinkDeep)
                    .clickable { onClose() }
                    .padding(horizontal = 22.dp, vertical = 9.dp)
            ) {
                Text(text = "好", fontSize = GardenText.bodyLg, color = Color.White)
            }
        }
    }
}

/**
 * 「看看哥哥都发来了什么」—— 全部记录，一条不落。
 *
 * 弹窗只看得到新来的那几条，看完就没了；
 * 这个页面是给她回头翻的：他哪天说过什么、录过什么，都在这儿。
 */
@Composable
fun InboxScreen(
    items: List<InboxItem>,
    loading: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "‹", fontSize = GardenText.glyph, color = TextDim)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${Personas.himCall}都发来了什么",
                fontSize = GardenText.titleLg,
                color = HimGold,
                fontWeight = FontWeight.Medium
            )
        }

        when {
            loading && items.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("正在找……", fontSize = GardenText.bodyLg, color = TextDim)
            }

            items.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("他还什么都没发过来", fontSize = GardenText.bodyLg, color = TextDim)
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 最近的排最上面
                items(items.reversed()) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBg)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = timeFmt.format(Date(item.at)),
                            fontSize = GardenText.caption,
                            color = TextDim
                        )
                        Spacer(Modifier.height(8.dp))
                        InboxEntry(item)
                    }
                }
            }
        }
    }
}

/** 一条留言的正文：文字 + 语音 + 图片。弹窗和记录页共用这一份。 */
@Composable
private fun InboxEntry(item: InboxItem) {
    if (item.text.isNotBlank()) {
        Text(text = item.text, fontSize = GardenText.title, color = TextMain)
        Spacer(Modifier.height(10.dp))
    }

    // 语音排在最前面 —— 听到他的声音，比看到什么都实在
    if (item.audio.isNotBlank()) {
        VoicePlayButton(item.audio)
        Spacer(Modifier.height(10.dp))
    }

    val bmp = remember(item.id) { decodeDataUrl(item.image) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(10.dp))
    }
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

/**
 * 「听他说的」—— 播放他录的那段语音。
 *
 * base64 先落到缓存目录再交给 MediaPlayer：
 * 直接从内存播需要 AudioTrack 那一套，费事得多，而这段音频总共也就几十秒。
 */
@Composable
private fun VoicePlayButton(base64Wav: String) {
    val ctx = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(HimGold.copy(alpha = 0.14f))
            .border(1.dp, HimGold.copy(alpha = 0.45f), shape)
            .clickable {
                if (playing) {
                    runCatching { player?.stop() }
                    runCatching { player?.release() }
                    player = null
                    playing = false
                    return@clickable
                }

                val mp = runCatching {
                    val bytes = Base64.decode(base64Wav, Base64.DEFAULT)
                    val f = File(ctx.cacheDir, "him_voice.wav")
                    f.writeBytes(bytes)
                    MediaPlayer().apply {
                        setDataSource(f.absolutePath)
                        setOnCompletionListener { playing = false }
                        prepare()
                    }
                }.getOrNull()

                if (mp == null) {
                    failed = true
                    return@clickable
                }
                player = mp
                mp.start()
                playing = true
            }
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(
            text = when {
                failed -> "这段没放出来"
                playing -> "▶ 正在放……"
                else -> "▶ 听他说的"
            },
            fontSize = GardenText.bodyLg,
            color = HimGold
        )
    }
}

// decodeDataUrl 搬到 core/util/Bitmaps.kt 去了 —— 聊天页也要用同一份。
// 而且那份必须两步解码（先 inJustDecodeBounds 读尺寸、算出采样率再真解码），
// 原来这里直接 decodeByteArray，一张大图就能把这个弹窗连同 App 一起 OOM 掉。

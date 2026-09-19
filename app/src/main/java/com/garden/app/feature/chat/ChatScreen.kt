package com.garden.app.feature.chat

import com.garden.app.core.config.Personas
import com.garden.app.core.ui.*

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.garden.app.core.net.ApiResult
import com.garden.app.core.net.ChatItem
import com.garden.app.core.util.decodeDataUrl
import com.garden.app.core.net.RelayClient
import com.garden.app.feature.voice.VoiceRecorder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 跟哥哥聊天。
 *
 * 跟「和他端通话」不是一回事：那个是跟小园说，这个是她直接跟他说。
 * 两边都走同一台服务器，但对话是分开的，不会混。
 */
@Composable
fun ChatScreen(
    items: List<ChatItem>,
    loading: Boolean,
    sending: Boolean,
    error: String?,
    shakeHint: String?,                 // 「摇他一下」的真实结果，由外面填
    onSend: (String, String) -> Unit,   // (文字, 语音base64)
    onShake: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(ctx) }
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var holding by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0.0) }
    var denied by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) denied = true }

    DisposableEffect(Unit) {
        onDispose { runCatching { recorder.stop() } }
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    LaunchedEffect(holding) {
        while (holding) {
            seconds = recorder.seconds
            delay(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .statusBarsPadding()
            .imePadding()
    ) {
        // ── 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = GardenText.glyph, color = TextDim) }

            Spacer(Modifier.width(6.dp))
            Text(Personas.himCall, fontSize = GardenText.heading, color = HimGold, fontWeight = FontWeight.Medium)

            Spacer(Modifier.weight(1f))

            // 摇他一下 —— 他手机震一分钟。
            // 十分钟只能摇一次，服务器会拦（拦了会告诉她等多久，不是干巴巴的失败）
            Pill(
                text = "摇他一下",
                onClick = onShake,
                outline = HimGold,
                fontSize = GardenText.small,
                glow = true,
            )
        }

        // 摇的结果 —— 成了、被拦了、还是网断了，三种说法不一样。
        // 以前这里写死一句「摇过啦」，成了没成看着都一样，等于没说。
        if (shakeHint != null) {
            Text(
                text = shakeHint,
                fontSize = GardenText.caption,
                color = if (shakeHint.contains("震")) HimGold else PinkLight,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
        }

        // ── 消息
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("正在打开……", fontSize = GardenText.bodyLg, color = TextDim)
                }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("你们还没说过话", fontSize = GardenText.bodyLg, color = TextDim)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items) { it -> ChatBubble(it) }
                }
            }
        }

        if (error != null) {
            Text(
                text = error,
                fontSize = GardenText.small,
                color = PinkLight,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }
        if (denied) {
            Text(
                text = "要用麦克风得先给「小园」开录音权限",
                fontSize = GardenText.caption,
                color = PinkLight,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }

        // ── 底部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 按住说语音
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (holding) PinkDeep else FieldBg)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (sending) return@detectTapGestures
                                val ok = ContextCompat.checkSelfPermission(
                                    ctx, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!ok) {
                                    permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@detectTapGestures
                                }
                                if (!recorder.start()) return@detectTapGestures

                                holding = true
                                tryAwaitRelease()
                                holding = false

                                val f = recorder.stop() ?: return@detectTapGestures
                                scope.launch {
                                    val b64 = withContext(Dispatchers.IO) {
                                        runCatching {
                                            Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                                        }.getOrNull()
                                    }
                                    if (b64 != null) onSend("", b64)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (holding) "%.0f".format(seconds) else "按住",
                    fontSize = if (holding) 11.sp else 11.sp,
                    color = if (holding) Color.White else TextDim
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(FieldBg)
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                if (input.isEmpty()) {
                    Text("跟${Personas.himCall}说点什么", fontSize = GardenText.bodyLg, color = TextDim)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = TextMain, fontSize = GardenText.bodyLg),
                    cursorBrush = SolidColor(PinkLight),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (sending || input.isBlank()) DisabledBg else PinkDeep
                    )
                    .clickable(enabled = !sending && input.isNotBlank()) {
                        val t = input.trim()
                        input = ""
                        onSend(t, "")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("发", fontSize = GardenText.body, color = Color.White)
            }
        }
    }
}

@Composable
private fun ChatBubble(item: ChatItem) {
    val from = fromWho(item.who)

    // 壳走公共组件（core/ui/Bubble.kt）—— 对齐、底色、描边、圆角、
    // 时间戳都在那边，跟对话页的气泡是同一套。
    Bubble(from = from, time = timeFmt.format(Date(item.at))) {
        when {
            // 语音：做成能点的 —— 光有个话筒图标等于没发
            item.hasAudio && item.text.isBlank() && !item.hasImage -> VoiceBubble(item)

            // 图（可能还带一段字）。
            //
            // ⚠️ 图必须优先于下面 text 那一支。图库那条线发过来的就是
            //    「一张图 + 一段字」，按老写法会走 text 分支，
            //    结果是字显示出来了、图永远看不见（以前这儿就写了个「🖼 图片」）。
            item.hasImage -> Column {
                ImageBubble(item)
                if (item.text.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.text, fontSize = GardenText.bodyLg,
                        color = bubbleTextColor(from)
                    )
                }
            }

            item.text.isNotBlank() -> Text(
                item.text, fontSize = GardenText.bodyLg,
                color = bubbleTextColor(from)
            )
            else -> Text(
                "…", fontSize = GardenText.bodyLg,
                color = bubbleTextColor(from)
            )
        }
    }
}

/**
 * 一张图。
 *
 * 聊天记录接口只回「有没有图」，图本身滚到这一条的时候才去服务器拿 ——
 * 一张几百 KB，每 5 秒拉一次记录都塞进去太费流量。
 *
 * LazyColumn 会把滚出去的回收掉，所以不会攒一堆 Bitmap 在内存里。
 */
@Composable
private fun ImageBubble(item: ChatItem) {
    var bmp by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        when (val r = RelayClient.imageOf(item.who, item.id)) {
            is ApiResult.Ok -> {
                // 解码是纯计算，大图要几十毫秒，别占着主线程
                val b = withContext(Dispatchers.IO) { decodeDataUrl(r.data) }
                if (b == null) failed = true else bmp = b
            }
            is ApiResult.Err -> failed = true
        }
    }

    val b = bmp
    when {
        // ⚠️ 必须卡高度。一张竖着拍的图按宽度铺满会有 600dp 高 ——
        //    整屏就只剩这一张图，聊天记录全被顶没了。
        //    Crop 会裁掉两头，但至少一眼能看出「这儿有张图」。
        b != null -> Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        failed -> Text("这张图没拿到", fontSize = GardenText.small, color = TextDim)
        else -> Text("正在拿……", fontSize = GardenText.small, color = TextDim)
    }
}

/**
 * 一条语音。点一下才去服务器拿音频、然后放。
 *
 * ⚠️ 以前这里就是个「🎤 语音」的文字 —— 因为聊天记录接口只回"有没有语音"、
 * 不回音频本身（那是为了省流量）。但"点开能听"那一步我一直没做，
 * 结果两边看到的都是一个放不了的话筒图标。2026-09-17 她发现的。
 */
@Composable
private fun VoiceBubble(item: ChatItem) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    Text(
        text = when {
            failed -> "放不出来，再点一下"
            loading -> "正在拿……"
            playing -> "▶ 在放"
            else -> "▶ 点开听"
        },
        fontSize = GardenText.bodyLg,
        color = Color.White,
        modifier = Modifier.clickable {
            if (playing) {
                runCatching { player?.stop() }
                runCatching { player?.release() }
                player = null
                playing = false
                return@clickable
            }
            if (loading) return@clickable

            loading = true
            failed = false
            scope.launch {
                val b64 = withContext(Dispatchers.IO) {
                    when (val r = RelayClient.audioOf(item.who, item.id)) {
                        is ApiResult.Ok -> r.data
                        is ApiResult.Err -> null
                    }
                }
                if (b64 == null) {
                    loading = false
                    failed = true
                    return@launch
                }
                val mp = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        val f = File(ctx.cacheDir, "v_${item.who}_${item.id}.wav")
                        f.writeBytes(bytes)
                        MediaPlayer().apply {
                            setDataSource(f.absolutePath)
                            setOnCompletionListener { playing = false }
                            prepare()
                        }
                    }.getOrNull()
                }
                loading = false
                if (mp == null) {
                    failed = true
                    return@launch
                }
                player = mp
                mp.start()
                playing = true
            }
        }
    )
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

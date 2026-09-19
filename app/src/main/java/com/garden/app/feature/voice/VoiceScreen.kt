package com.garden.app.feature.voice
import com.garden.app.core.ui.*

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.garden.app.core.config.Personas
import com.garden.app.core.net.Turn
import com.garden.app.feature.voice.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「和他端通话」。
 *
 * 她按住说话，松手就送出去 —— 不用打字。
 * 说出来的每个字走的都是跟打字【完全一样】的那条路：
 * 一样会进对话、一样会判危机、一样会原样传到他手机上。
 *
 * 这是这个 App 里对她最省力的一条路：她难受的时候未必有心思打字，
 * 但说一句话的力气总是有的。
 */
@Composable
fun VoiceScreen(
    turns: List<Turn>,
    busy: Boolean,
    error: String?,
    onVoice: (String) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(ctx) }
    val listState = rememberLazyListState()

    var holding by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(0.0) }
    var denied by remember { mutableStateOf(false) }

    // 松手之后先别急着发，留 3 秒给她反悔。
    //
    // 她说出去的每个字都会原样到哥哥手上 —— 说错了得有地方收回。
    // 之前是松手即发，她只能眼睁睁看着错话被送出去。
    var pending by remember { mutableStateOf<String?>(null) }   // base64 WAV
    var countdown by remember { mutableStateOf(0) }

    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        countdown = 3
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        val b64 = pending ?: return@LaunchedEffect
        pending = null
        onVoice(b64)
    }

    // 权限：按住那一刻才申请，不在进页面时就弹
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) denied = true
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { recorder.stop() } }
    }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    // 录音时每 0.1 秒刷一次秒数，让她知道真的在录
    LaunchedEffect(holding) {
        while (holding) {
            recorded = recorder.seconds
            delay(100)
        }
    }

    Column(
        modifier = Modifier
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

        // ── 对话
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

        if (error != null) {
            Text(
                text = error,
                fontSize = GardenText.body,
                color = PinkLight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }
        if (denied) {
            Text(
                text = "要用麦克风才能说话。去手机设置里给「小园」开一下录音权限",
                fontSize = GardenText.small,
                color = PinkLight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }

        // ── 反悔窗口：说完了，还没发出去
        if (pending != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$countdown 秒后发出去",
                    fontSize = GardenText.body,
                    color = TextDim
                )
                Spacer(Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PinkDeep)
                        .clickable { pending = null }
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text(text = "不发", fontSize = GardenText.body, color = Color.White)
                }
            }
        }

        // ── 按住说话
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    holding -> "%.1f 秒 · 松手就能改主意".format(recorded)
                    pending != null -> "说完了，还能收回"
                    busy -> "小园在想……"
                    else -> "按住说话"
                },
                fontSize = GardenText.small,
                color = TextDim
            )

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(if (holding) 96.dp else 84.dp)
                    .clip(CircleShape)
                    .background(
                        if (holding) Brush.linearGradient(listOf(PinkDeep, PinkLight))
                        else Brush.linearGradient(
                            listOf(DisabledBg, CardBgAlt)
                        )
                    )
                    .border(
                        1.dp,
                        if (holding) PinkLight else Color.White.copy(alpha = 0.12f),
                        CircleShape
                    )
                    // key 必须是 Unit：放会变的状态会让手势协程被取消重启，
                    // tryAwaitRelease 收不到抬手（他端那边栽过这个跟头）
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (busy) return@detectTapGestures

                                val ok = ContextCompat.checkSelfPermission(
                                    ctx, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!ok) {
                                    permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@detectTapGestures
                                }

                                if (!recorder.start()) {
                                    return@detectTapGestures
                                }
                                holding = true
                                tryAwaitRelease()
                                holding = false

                                val f = recorder.stop()
                                if (f == null) return@detectTapGestures

                                // 只转码，先不发 —— 交给上面的倒计时
                                scope.launch {
                                    val b64 = withContext(Dispatchers.IO) {
                                        runCatching {
                                            Base64.encodeToString(
                                                f.readBytes(), Base64.NO_WRAP
                                            )
                                        }.getOrNull()
                                    }
                                    if (b64 != null && pending == null) pending = b64
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (holding) "松手" else "按住",
                    fontSize = GardenText.titleLg,
                    color = if (holding) Color.White else TextDim
                )
            }
        }
    }
}

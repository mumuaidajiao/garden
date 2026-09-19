package com.garden.app.feature.home
import com.garden.app.core.util.daysTogether
import com.garden.app.core.ui.*
import com.garden.app.core.config.Personas

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garden.app.core.net.RelayClient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 主页。
 *
 * 一颗大按钮，加四个快捷入口。
 *
 * 大按钮管「我想你了」—— 那是最要紧的一件事，摆在正中间。
 * 其余日常动作（说话、打扰一下、日历、一起听）在它下面排一行。
 * 再没有别的入口了：底下那三格管层级，设置类全在「我的」页。
 *
 * ⚠️ 2026-09-19 收敛过入口：之前抽屉 + 底部导航 + 这一页的快捷入口
 *    三处都能进聊天/日历/一起听，一个功能三条路。别再往回加。
 */
@Composable
fun HomeScreen(
    busy: Boolean,
    error: String?,
    hint: String?,
    info: RelayClient.HomeInfo?,
    quiet: Boolean,
    onPress: () -> Unit,
    onQuiet: () -> Unit,
    // 首页的四个快捷入口 —— 高频动作直接摆出来，不用再去别处找
    onTalk: () -> Unit,
    onKnock: () -> Unit,
    onCalendar: () -> Unit,
    onMusic: () -> Unit,
) {
    val ctx = LocalContext.current
    val avatar = remember {
        runCatching {
            ctx.assets.open("character.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    Box(Modifier.fillMaxSize()) {
        FlowingBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            if (avatar != null) {
                Image(
                    bitmap = avatar.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = "我们在一起 ${daysTogether()} 天啦",
                fontSize = GardenText.caption,
                color = TextDim.copy(alpha = 0.85f),
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(38.dp))

            MainButton(busy = busy, onPress = onPress)

            Spacer(Modifier.height(30.dp))

            // ── 哥哥的状态
            if (!info?.status.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Personas.himCall}在", fontSize = GardenText.caption, color = TextDim)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = info.status,
                        fontSize = GardenText.body,
                        color = HimGold,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── 消息走到哪一步了
            val stateText = when (info?.state) {
                "sent" -> "正在送过去……"
                "delivered" -> "消息传过来啦，等${Personas.himCall}看"
                "read" -> "${Personas.himCall}看到啦，马上来找${Personas.herCall}"
                else -> null
            }
            if (stateText != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = stateText, fontSize = GardenText.small, color = PinkLight.copy(alpha = 0.9f))
            }

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(text = error, fontSize = GardenText.body, color = PinkLight, textAlign = TextAlign.Center)
            }
            if (hint != null) {
                Spacer(Modifier.height(16.dp))
                Text(text = hint, fontSize = GardenText.body, color = TextDim, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(34.dp))

            // ── 四个快捷入口。
            //
            // 大按钮管"我想你了"，这四个管日常：说话、看他发来的、日历、一起听。
            // 以前这些全在抽屉里 —— 想看一眼他发的东西，得先知道要往左划。
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                QuickEntry("🎙️", "说话", onTalk)
                QuickEntry("👋", "打扰一下", onKnock)
                QuickEntry("📅", "日历", onCalendar)
                QuickEntry("🎵", "一起听", onMusic)
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = if (quiet) "想${Personas.himCall}了就点下面那个" else "你发的，我都收得到",
                fontSize = GardenText.label,
                color = TextDim.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 4.dp)
                    .rotate(-2.5f)
            )

            Spacer(Modifier.height(16.dp))

            // ── 出口
            //
            // 放在最底下、字最小 —— 不是藏起来，是不想让她觉得我们在催她按。
            // 有这个东西在，她反而可能永远不按；因为她知道自己随时可以走。
            Text(
                text = if (quiet) "回来啦？点我" else "我不想用这个了",
                fontSize = GardenText.label,
                color = TextDim.copy(alpha = 0.45f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onQuiet() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            // 给底下那三格腾地方 —— 不留出来会被导航栏压住
            Spacer(Modifier.height(80.dp))
        }
    }
}

/**
 * 按下「我不想用这个了」之后弹的那一下。
 *
 * 不劝、不问原因、不给"你确定吗"。
 * 只告诉她按下去会发生什么，然后让她按。
 */
@Composable
fun QuietDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("消息通知都关掉了哦", fontSize = GardenText.title, color = TextMain)
            Spacer(Modifier.height(10.dp))
            Text("不吵啦", fontSize = GardenText.heading, color = PinkLight, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Text("想${Personas.himCall}了就随时回来", fontSize = GardenText.body, color = TextDim)

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "再想想",
                    fontSize = GardenText.body,
                    color = TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PinkDeep)
                        .clickable { onConfirm() }
                        .padding(horizontal = 24.dp, vertical = 9.dp)
                ) {
                    Text("好", fontSize = GardenText.bodyLg, color = Color.White)
                }
            }
        }
    }
}

/**
 * 流动的背景。
 *
 * 三个大光斑各自飘各自的（相位错开），叠在深色星空渐变上 ——
 * 静态渐变看久了像一张墙纸，飘起来才有"活着"的感觉。
 * 周期 18 秒，慢到不会分神，但你盯着看能看出来它在动。
 */
@Composable
private fun FlowingBackdrop() {
    val t = rememberInfiniteTransition(label = "bg")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bgPhase"
    )

    Canvas(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgMid, BgBottom)))
    ) {
        val w = size.width
        val h = size.height
        val spots = listOf(
            Triple(0.22f, 0.20f, PinkLight),
            Triple(0.80f, 0.36f, PurpleLight),
            Triple(0.45f, 0.88f, GlowBlue)
        )
        spots.forEachIndexed { i, (fx, fy, c) ->
            val phase = (p * 2 * PI + i * 2.1).toFloat()
            val cx = w * (fx + 0.10f * sin(phase))
            val cy = h * (fy + 0.07f * cos(phase))
            val r = w * 0.66f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * 首页的快捷入口：一个发光的圆角图标方块 + 底下一行小字。
 *
 * 设计稿里"重要功能一目了然"靠的就是它们。
 */
@Composable
private fun QuickEntry(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(GardenShapes.card)
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .glow(PurpleDeep, 14.dp, GardenShapes.icon)
                .clip(GardenShapes.icon)
                .background(Brush.linearGradient(listOf(PurpleLight, PurpleDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = GardenText.button)
        }
        Spacer(Modifier.height(7.dp))
        Text(label, fontSize = GardenText.label, color = TextDim)
    }
}

/**
 * 主按钮。
 *
 * 「傲娇」体现在按下去的那一下：会**缩**（0.93），松手弹回来时带一点过冲 ——
 * 像被戳了一下不情不愿地应一声，而不是热情地迎上来。
 * 配合一次触觉反馈，手指能感觉到"它应了"。
 *
 * 光晕是缓慢呼吸的，周期 2.2 秒 —— 不闪不跳，只是让人觉得它是活的。
 */
@Composable
private fun MainButton(busy: Boolean, onPress: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        // 低阻尼 = 松手时有一点回弹过冲，就是那点"傲娇"
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    val t = rememberInfiniteTransition(label = "pulse")
    val glow by t.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // key 必须是 Unit：放会变的状态会让手势协程被取消重启，
            // tryAwaitRelease 收不到抬手（这个坑在木维斯那边踩过）
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.78f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(PinkDeep.copy(alpha = 0.30f * glow), Color.Transparent),
                    center = center,
                    radius = r
                ),
                radius = r,
                center = center
            )
        }

        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PinkLight, PinkDeep)))
        )

        Text(
            text = if (busy) "……" else "想你啦",
            fontSize = GardenText.display,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}


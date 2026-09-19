package com.garden.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════
//  配色 —— 深紫夜空 + 粉紫
//
//  2026-09-19 按设计稿调过一次。基调【没变】（还是她熟悉的深色星空 +
//  粉紫，见下），变的是三件事：
//    ① 底色从「深蓝」拉向「深紫」，更暖一点
//    ② 新增卡片/输入框/禁用态等分层色，把散落在各页的字面色值收回来
//    ③ 新增字号阶梯、圆角规范、发光工具
//
//  ⚠️ 改这里的值 = 全局换肤。改完必须同步 res/values/colors.xml 的
//     garden_background（冷启动的 windowBackground 用它），否则会闪一下错色。
// ══════════════════════════════════════════════════════════════

// ── 背景三层渐变（上 → 下）──────────────────────────────────
val BgTop = Color(0xFF1A1030)
val BgMid = Color(0xFF241640)
val BgBottom = Color(0xFF3A1F5C)

// ── 主色：粉 ────────────────────────────────────────────────
val PinkLight = Color(0xFFF093FB)
val PinkDeep = Color(0xFFF5576C)

// ── 主色：紫 ────────────────────────────────────────────────
val PurpleLight = Color(0xFF667EEA)
val PurpleDeep = Color(0xFF764BA2)

// ── 文字三级 ────────────────────────────────────────────────
val TextMain = Color(0xFFEDEDF5)
val TextDim = Color(0xFF9A9AB0)

/** 最低一级：主页最底下那行出口小字，弱到"在但不吵"。 */
val TextFaint = Color(0x73FFFFFF)

// ── 面板分层 ────────────────────────────────────────────────
//
// 以前这些色值以字面量形式散在各页里（Color(0xFF20203A) 出现过 7 次），
// 换肤要满项目找。现在只有这里有定义。
val CardBg = Color(0xFF241A3E)        // 卡片 / 弹窗面板
val CardBgAlt = Color(0xFF2E2150)     // 次级卡片、小园气泡
val FieldBg = Color(0xFF2A1F48)       // 输入框底
val DisabledBg = Color(0xFF3E3057)    // 禁用态按钮
val DrawerBg = Color(0xFF1C1334)      // 抽屉底
val TrackRowBg = Color(0xFF221838)    // 歌单行
val TrackRowOn = Color(0xFF32214E)    // 歌单当前行高亮

// ── 他本人 ────────────────────────────────────────────────
//
// 他的话用暖金色单独标出来 —— 跟他平时说话的那个通道区分开。
val HimGold = Color(0xFFFFD180)
val HimBubbleTop = Color(0xFF4A3A22)
val HimBubbleBottom = Color(0xFF3A2F1E)

// ── 光晕 ────────────────────────────────────────────────────
//
// 设计稿的质感核心：深色底上，发光的边缘比描边高级得多。
// 用法见下面 Modifier.glow()。
val GlowPink = Color(0xFFF093FB)
val GlowPurple = Color(0xFF764BA2)
val GlowGold = Color(0xFFFFD180)
val GlowBlue = Color(0xFF2E6BA8)      // 背景上那颗冷色光斑

// ── 字号阶梯 ────────────────────────────────────────────────
//
// 原来是 10~30sp 的数字散在各页 Text(fontSize = ...) 里，现在收在这里。
// 新增字号先往这里加，别在页面里写裸数字。
object GardenText {
    val tiny = 10.sp          // 时间戳
    val label = 11.sp         // 出口小字、说明
    val caption = 12.sp       // 天数、副标题
    val small = 13.sp         // 图片占位、次要提示
    val body = 14.sp          // 正文
    val bodyLg = 15.sp        // 抽屉条目
    val title = 16.sp         // 气泡正文
    val titleLg = 17.sp       // 弹窗标题
    val heading = 18.sp       // 页面标题
    val button = 20.sp        // 图标
    val display = 28.sp       // 主按钮
    val glyph = 30.sp         // 返回箭头这类大字符
}

// ── 圆角规范 ────────────────────────────────────────────────
object GardenShapes {
    val chip = RoundedCornerShape(50)          // 药丸（按钮、标签）
    val row = RoundedCornerShape(12.dp)        // 列表行
    val icon = RoundedCornerShape(14.dp)       // 图标方块
    val card = RoundedCornerShape(16.dp)       // 卡片
    val bubble = RoundedCornerShape(18.dp)     // 气泡
    val panel = RoundedCornerShape(20.dp)      // 弹窗、抽屉边
}

/**
 * 发光。
 *
 * 深色底上画一圈同色的柔光 —— 比描边更像"亮着"，也是设计稿里
 * 主按钮、卡片、图标方块共同的那层质感。
 *
 * 实现走 `shadow(spotColor = ...)`：Compose 的彩色阴影要求 API 28+，
 * 低于这个版本会退化成普通灰影（不会崩，只是不好看）。本项目的
 * minSdk 是 26，只影响 26/27 两台老设备，可以接受。
 */
fun Modifier.glow(
    color: Color,
    elevation: Dp = 14.dp,
    shape: Shape = GardenShapes.chip,
): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = color,
    spotColor = color,
    clip = false,
)

private val GardenColorScheme = darkColorScheme(
    primary = PinkLight,
    onPrimary = Color.White,
    secondary = PurpleLight,
    background = BgTop,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
)

@Composable
fun GardenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GardenColorScheme, content = content)
}

/** 全屏星空渐变底。 */
@Composable
fun GardenBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgMid, BgBottom)))
    ) {
        content()
    }
}

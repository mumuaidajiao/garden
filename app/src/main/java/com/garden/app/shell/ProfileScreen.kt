package com.garden.app.shell

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garden.app.core.config.Personas
import com.garden.app.core.ui.*

/**
 * 「我的」那一页。
 *
 * ⚠️ 这里**只放设置类的东西**（头像、服务器、服务状态），不放功能入口。
 *
 * 2026-09-19 收敛过一次：在这之前抽屉、底部导航、首页快捷入口三处
 * 都能进聊天/日历/一起听 —— 一个功能三条路，每次点之前都得先想一下。
 * 现在分工是死的，别再往回加：
 *   **首页快捷入口** —— 高频动作（说话 / 打扰一下 / 日历 / 一起听）
 *   **底部导航**     —— 层级（首页 / 消息 / 我的）
 *   **这一页**       —— 只放"设置"，不放功能
 */
@Composable
fun ProfileScreen(
    days: Int,
    serviceRunning: Boolean,
    busy: Boolean,
    avatar: Bitmap?,
    avatarHint: String?,
    onPickAvatar: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        GardenBackdrop {}

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── 头像 + 名字 + 天数
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(PinkLight, PinkDeep)))
                    .glow(PinkDeep, 18.dp, CircleShape)
                    .clickable(enabled = !busy) { onPickAvatar() },
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    Image(
                        bitmap = avatar.asImageBitmap(),
                        contentDescription = "点一下换头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("＋", fontSize = GardenText.display, color = Color.White.copy(alpha = 0.9f))
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = Personas.herName,
                fontSize = GardenText.heading,
                color = TextMain,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                // 换头像的过程中这句话借用一下这行位置，几秒后自己变回来
                text = avatarHint ?: "在一起 $days 天 · 点头像能换",
                fontSize = GardenText.caption,
                color = if (avatarHint != null) PinkLight else TextDim,
            )

            Spacer(Modifier.height(34.dp))

            // ── 设置项
            SettingRow("⚙️", "设置", "服务器地址与令牌") { onSettings() }

            Spacer(Modifier.height(26.dp))

            // ── 服务状态
            //
            // 这一行不能省：服务被系统干掉时，界面不会闪退、也不会报错，
            // 她只会觉得"怎么收不到他发的东西了"。把状态摆出来，
            // 出问题时至少能一眼看见是这儿断了。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (serviceRunning) PinkLight else HimGold),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (serviceRunning) "${Personas.himCall}发的东西会弹给你"
                    else "弹不出消息了，重进一下",
                    fontSize = GardenText.caption,
                    color = if (serviceRunning) TextDim.copy(alpha = 0.75f) else HimGold,
                )
            }

            // 底部导航占位
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun SettingRow(icon: String, title: String, hint: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(GardenShapes.icon)
                .background(CardBgAlt),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = GardenText.title)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = GardenText.bodyLg, color = TextMain)
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = GardenText.label, color = TextDim.copy(alpha = 0.7f))
        }
    }
}

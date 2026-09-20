package com.garden.app.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garden.app.core.config.AppConfig
import com.garden.app.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 设置：服务器地址 + 令牌。
 *
 * 开源版给别人的主要入口 —— 不重新编译也能把 App 指到自己的服务器上
 * （编译时的默认值在 local.properties 里，那个是开发者用的）。
 *
 * 「测试连接」打的是 `/health`：填完先试一下再保存，
 * 免得存了个错的、回头只在主页上看到一句语焉不详的"网络错误"。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var url by remember { mutableStateOf(AppConfig.baseUrl) }
    var token by remember { mutableStateOf(AppConfig.token) }
    var testing by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var good by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        GardenBackdrop {}

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            // ── 顶栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", fontSize = GardenText.glyph, color = TextDim)
                }
                Spacer(Modifier.width(6.dp))
                Text("设置", fontSize = GardenText.heading, color = TextMain, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(24.dp))

            // ── 服务器
            Text("服务器", fontSize = GardenText.bodyLg, color = TextMain, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "你自己那台的地址，形如 http://192.168.1.10:9394/api\n（末尾的 /api 别漏）",
                fontSize = GardenText.caption,
                color = TextDim,
            )
            Spacer(Modifier.height(10.dp))
            FieldBox(url, "服务器地址") { url = it }

            Spacer(Modifier.height(22.dp))

            // ── 令牌
            Text("令牌", fontSize = GardenText.bodyLg, color = TextMain, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "服务器 config.json 里 tokens.her 那一串。\n这台设备用哪一串，服务器就认它是谁。",
                fontSize = GardenText.caption,
                color = TextDim,
            )
            Spacer(Modifier.height(10.dp))
            FieldBox(token, "令牌") { token = it }

            Spacer(Modifier.height(26.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill(
                    text = if (testing) "正在连……" else "测试连接",
                    onClick = {
                        if (testing) return@Pill
                        testing = true
                        msg = null
                        scope.launch {
                            val code = withContext(Dispatchers.IO) {
                                runCatching {
                                    val c = OkHttpClient.Builder()
                                        .connectTimeout(6, TimeUnit.SECONDS)
                                        .readTimeout(6, TimeUnit.SECONDS)
                                        .build()
                                    val req = Request.Builder()
                                        .url(AppConfig.healthUrlOf(url))
                                        .get()
                                        .build()
                                    c.newCall(req).execute().use { it.code }
                                }.getOrNull()
                            }
                            testing = false
                            good = code == 200
                            msg = when (code) {
                                null -> "连不上 —— 地址对不对？服务器起来了吗？"
                                200 -> "通了 ✓ 服务器活着"
                                // 这个请求打的是 /health，不带令牌，所以 401/403
                                // 在这里不可能出现 —— 别把使用者往「令牌不对」上引。
                                else -> "服务器回了 HTTP $code —— 地址可能不对，或者反代没配好"
                            }
                        }
                    },
                    outline = PinkLight,
                    fontSize = GardenText.body,
                    glow = true,
                )

                Pill(
                    text = "保存",
                    onClick = {
                        AppConfig.save(url, token)
                        onBack()
                    },
                    bg = PinkDeep,
                    fontSize = GardenText.body,
                    glow = true,
                )
            }

            msg?.let {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = it,
                    fontSize = GardenText.body,
                    color = if (good) PinkLight else HimGold,
                )
            }

            Spacer(Modifier.height(30.dp))

            // 编译时那层默认值不是给人填的，但出问题时要知道它有没有生效
            Text(
                text = "改完随时能再进来改，不用重装。\n编译时预置的值（local.properties）在没有运行时配置时才生效。",
                fontSize = GardenText.label,
                color = TextDim.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun FieldBox(value: String, hint: String, onChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FieldBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(hint, fontSize = GardenText.bodyLg, color = TextDim)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = TextMain, fontSize = GardenText.bodyLg),
            cursorBrush = SolidColor(PinkLight),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

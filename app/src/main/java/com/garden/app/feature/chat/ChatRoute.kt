package com.garden.app.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.garden.app.core.net.ApiResult
import com.garden.app.core.net.ChatItem
import com.garden.app.core.net.RelayClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「和哥哥聊天」这一站的全部家当。
 *
 * 状态从这里往下都归这个包管 —— 它崩了、它数据坏了，跟别站没关系。
 * 退出这一站时这些东西跟着一起销毁，下次进来重新拉，
 * 不会留半截旧数据在内存里。
 *
 * ⚠️ 轮询写在 LaunchedEffect(Unit) 里就够了：这一站只在显示的时候才组合，
 *    离开时组合被销毁、协程自动取消 —— 比外面拿一个 showChat 布尔量来开关干净。
 */
@Composable
fun ChatRoute(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var shakeHint by remember { mutableStateOf<String?>(null) }

    // 她进这一站多半是在等回复，所以刷得勤一点。
    // 拉不到就保持上一次的，别在她面前甩红字 —— 她什么都没做错。
    LaunchedEffect(Unit) {
        while (true) {
            when (val r = RelayClient.chat()) {
                is ApiResult.Ok -> items = r.data
                is ApiResult.Err -> Unit
            }
            loading = false
            delay(5000)
        }
    }

    ChatScreen(
        items = items,
        loading = loading,
        sending = sending,
        error = error,
        onSend = { text, audio ->
            sending = true
            error = null
            scope.launch {
                when (val r = RelayClient.sayToHim(text, audio)) {
                    is ApiResult.Ok -> when (val g = RelayClient.chat()) {
                        is ApiResult.Ok -> items = g.data
                        is ApiResult.Err -> Unit
                    }
                    is ApiResult.Err -> error = r.message
                }
                sending = false
            }
        },
        shakeHint = shakeHint,
        onShake = {
            shakeHint = "正在摇……"
            scope.launch {
                when (val r = RelayClient.shake()) {
                    is ApiResult.Ok -> shakeHint = "摇了，他手机在震"
                    // 服务器被拦时会说「刚摇过了，等3分钟再摇好不好」——
                    // 这句原话比任何我们编的话都有用
                    is ApiResult.Err -> shakeHint = r.message
                }
            }
        },
        onBack = onBack
    )
}

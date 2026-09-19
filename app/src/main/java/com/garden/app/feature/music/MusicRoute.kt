package com.garden.app.feature.music

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.garden.app.core.MusicStore
import com.garden.app.core.net.ApiResult
import com.garden.app.core.net.MusicTrack
import com.garden.app.service.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「一起听」这一站的全部家当。
 *
 * 跟别站一样：退出即销毁，不留状态。
 *
 * ⚠️ 但【播放状态不归它】—— 那个在 [MusicService] 里，因为歌要能跨页面继续放。
 *    这一页只做两件事：
 *      ① 订阅 `MusicService.state`（单向流出，不用轮询）
 *      ② 把她的动作翻译成对服务的指令
 *
 * 歌单倒是这儿拉一份 —— 服务那份是给"下一首"用的，这份是给她看的。
 */
@Composable
fun MusicRoute(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var localNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }

    // 服务那边流出来的，订阅就行
    val musicState by MusicService.state.collectAsState()

    // 本机已经存了哪些歌 —— 歌单里标"已经在你手机里了"
    fun scanLocal() {
        localNames = MusicStore.dir(ctx).listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun reloadList() {
        when (val r = MusicStore.list()) {
            is ApiResult.Ok -> {
                tracks = r.data
                error = null
            }
            is ApiResult.Err -> error = r.message
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { scanLocal() }
        reloadList()
        loading = false
    }

    // 传歌：系统文件选择器，不用申请读存储权限
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            hint = "正在传……"
            error = null
            val displayName = withContext(Dispatchers.IO) { queryName(ctx, uri) }
            val err = withContext(Dispatchers.IO) { MusicStore.upload(ctx, uri, displayName) }
            if (err == null) {
                hint = "传上去啦"
                withContext(Dispatchers.IO) { scanLocal() }
                reloadList()
            } else {
                hint = null
                error = err
            }
        }
    }

    LaunchedEffect(hint) {
        if (hint == null || hint == "正在传……") return@LaunchedEffect
        delay(3000)
        hint = null
    }

    // 下载完的歌补进"本机已有" —— 服务在后台下完的，这一页未必知道
    LaunchedEffect(musicState.name, musicState.busy) {
        if (!musicState.busy) withContext(Dispatchers.IO) { scanLocal() }
    }

    MusicScreen(
        tracks = tracks,
        localNames = localNames,
        loading = loading,
        error = error,
        hint = hint,
        state = musicState,

        onPlay = { track ->
            if (musicState.name == track.name && musicState.playing) {
                // 点正在放的那首 = 停下
                MusicService.stop(ctx)
            } else {
                MusicService.play(ctx, track.name)
            }
        },
        onToggle = { MusicService.toggle(ctx) },
        onNext = { MusicService.next(ctx) },
        onPrev = { MusicService.prev(ctx) },
        onCycleMode = { MusicService.setMode(ctx, nextMode(musicState.mode)) },
        onUpload = { picker.launch(arrayOf("audio/*")) },
        onBack = onBack
    )
}

/**
 * 从选中的那个 uri 里问出文件名。
 *
 * 文件管理器给的名字才是她认得的那首，`uri.lastPathSegment` 往往是一串数字。
 */
private fun queryName(ctx: Context, uri: Uri): String = runCatching {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "unknown.mp3"

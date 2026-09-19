package com.garden.app.core

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.garden.app.core.net.ApiResult
import com.garden.app.core.net.MusicTrack
import com.garden.app.core.net.RelayClient
import java.io.File

/**
 * 一起听的歌。
 *
 * 歌都在服务器上。她点一首就下到本机存着 —— 下次再听直接放本机那份，
 * 不重复走流量。她也能把手机里的歌传上去。
 *
 * 本机目录：`files/music/`
 */
object MusicStore {

    private val OK_EXT = listOf("mp3", "m4a", "aac", "wav", "ogg", "flac")

    /** 单首上限。跟服务器那头卡的一样，先在本机拦一道免得白传。 */
    private const val MAX_BYTES = 15 * 1024 * 1024L

    fun dir(ctx: Context): File = File(ctx.filesDir, "music").apply { mkdirs() }

    /** 本机存的那份。没下过就是 null。 */
    fun localFile(ctx: Context, name: String): File? {
        val f = File(dir(ctx), name)
        return if (f.exists() && f.length() > 0) f else null
    }

    suspend fun list(): ApiResult<List<MusicTrack>> = RelayClient.musicList()

    /**
     * 确保这首歌在本机。下过就直接用，不重复下。
     *
     * @return 本地文件；拿不到返回 null
     */
    suspend fun ensure(ctx: Context, name: String): File? {
        localFile(ctx, name)?.let { return it }

        val r = RelayClient.musicGet(name)
        if (r !is ApiResult.Ok) return null

        return runCatching {
            val bytes = Base64.decode(r.data, Base64.DEFAULT)
            val f = File(dir(ctx), name)
            // 先写 .tmp 再改名：中途断了不会留半个文件冒充"已下好"
            val tmp = File(dir(ctx), name + ".part")
            tmp.writeBytes(bytes)
            if (f.exists()) f.delete()
            tmp.renameTo(f)
            f
        }.getOrNull()
    }

    /**
     * 把手机里的一首歌传上去。
     *
     * @return null 表示成功；否则是能直接给她看的一句话
     */
    suspend fun upload(ctx: Context, uri: Uri, displayName: String): String? {
        val name = safeName(displayName)
            ?: return "这个格式不行，得是 mp3 或者 m4a"

        val bytes = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return "这首歌我读不出来"

        if (bytes.isEmpty()) return "这首歌是空的"
        if (bytes.size > MAX_BYTES) return "这首歌超过 15MB 了，换小一点的"

        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return when (val r = RelayClient.musicUpload(name, b64)) {
            is ApiResult.Ok -> {
                // 传上去了，本机顺手也留一份，省得下次自己再下一遍
                runCatching { File(dir(ctx), name).writeBytes(bytes) }
                null
            }
            is ApiResult.Err -> r.message
        }
    }

    /**
     * 从显示名里抠出一个安全的文件名。
     *
     * ⚠️ 只取最后一段、只留白名单扩展名 —— 文件管理器给的名字里
     *    可能带路径分隔符，直接拼进路径就是一个漏洞。
     *    （服务器那头还有一道 `path.basename` 兜着，两道都要有。）
     */
    fun safeName(raw: String): String? {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        if (base.isEmpty() || base.length > 100) return null
        if (!OK_EXT.any { base.endsWith(".$it", ignoreCase = true) }) return null
        return base
    }
}

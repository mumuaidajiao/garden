package com.garden.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.garden.app.core.net.ApiResult
import com.garden.app.core.net.RelayClient
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 她自己的头像。
 *
 * 她挑一张，传到服务器，两端都看得到。
 * 他 2026-09-18 提的：「她可以在抽屉里自己换头像」。
 *
 * 本机留一份在 `files/avatar.jpg` —— 换头像是偶尔一次的事，
 * 没必要每次开 App 都从服务器拉一张图。
 */
object AvatarStore {

    private const val FILE = "avatar.jpg"

    /** 头像边长。抽屉里那个圆才 42dp，512 绰绰有余。 */
    private const val SIDE = 512

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    /** 本机存的那张。没有就 null。 */
    fun cached(ctx: Context): Bitmap? = runCatching {
        val f = file(ctx)
        if (!f.exists()) return@runCatching null
        BitmapFactory.decodeFile(f.absolutePath)
    }.getOrNull()

    /** 从服务器拉一次存本地。返回"拉到了没有"。 */
    suspend fun refresh(ctx: Context): Boolean {
        val r = RelayClient.getAvatar()
        if (r !is ApiResult.Ok) return false
        if (r.data.isBlank()) return false          // 她还没设过，不是错
        val bmp = decodeDataUrl(r.data) ?: return false
        runCatching {
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                file(ctx).writeBytes(out.toByteArray())
            }
        }
        return true
    }

    /**
     * 换一张。
     *
     * @return null 表示成功；否则是能直接给她看的一句话
     */
    suspend fun upload(ctx: Context, bmp: Bitmap): String? {
        val dataUrl = encode(bmp)
        return when (val r = RelayClient.setAvatar(dataUrl)) {
            is ApiResult.Ok -> {
                runCatching {
                    val comma = dataUrl.indexOf(',')
                    file(ctx).writeBytes(Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT))
                }
                null
            }
            is ApiResult.Err -> r.message
        }
    }

    /**
     * 把相册里那张读进来，裁成正方形、缩到 512。
     *
     * ⚠️ 两步走：先 `inJustDecodeBounds` 只读尺寸、算出采样率，再真解码。
     *    直接解一张 4000×3000 要 48MB —— 她随手挑张自拍就能把这个 App 干掉。
     */
    fun loadSquare(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / sample > SIDE * 2 || bounds.outHeight / sample > SIDE * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return@runCatching null

        // 从正中间裁正方形 —— 头像显示出来是圆的，长方形直接缩会歪
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(
            src,
            (src.width - side) / 2,
            (src.height - side) / 2,
            side, side
        )
        Bitmap.createScaledBitmap(square, SIDE, SIDE, true)
    }.getOrNull()

    /** 压成 JPEG 的 data url。512×512 q88 大约 60KB。 */
    private fun encode(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return "data:image/jpeg;base64," +
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun decodeDataUrl(s: String): Bitmap? = runCatching {
        val comma = s.indexOf(',')
        if (comma < 0) return@runCatching null
        val bytes = Base64.decode(s.substring(comma + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

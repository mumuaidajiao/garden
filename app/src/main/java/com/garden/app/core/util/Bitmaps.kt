package com.garden.app.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * 解 `data:image/...;base64,xxx` 这种串。解不出来就当没有，别崩。
 *
 * ⚠️ **必须两步走**，这不是优化，是保命：
 *    直接 `decodeByteArray` 一张 1260×2800 的图要吃掉约 14MB 内存。
 *    聊天记录里连着三张就是 OOM —— 而她那边一 OOM，是**整个 App 没了**，
 *    不是"这张图没显示"。她的手机不是旗舰，别赌。
 *
 *    第一趟 `inJustDecodeBounds` 只读尺寸、不分配像素内存；
 *    算出 2 的幂次采样率；第二趟才真解码，但已经缩过了。
 *
 * @param maxPx 长边缩到多少像素以内。聊天里显示根本用不到原图那么大，
 *              1080 在手机屏上已经看不出差别了。
 */
fun decodeDataUrl(s: String, maxPx: Int = 1080): Bitmap? = runCatching {
    if (s.isBlank()) return@runCatching null
    val comma = s.indexOf(',')
    if (comma < 0) return@runCatching null
    val bytes = Base64.decode(s.substring(comma + 1), Base64.DEFAULT)
    if (bytes.isEmpty()) return@runCatching null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    // 采样率必须是 2 的幂，BitmapFactory 只认这个
    var sample = 1
    while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) {
        sample *= 2
    }

    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}.getOrNull()

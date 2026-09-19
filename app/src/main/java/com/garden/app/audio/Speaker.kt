package com.garden.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 让小园出声。
 *
 * 用系统自带的 TTS，不联网、不申请任何权限。
 * 初始化是异步的 —— init 之后要等 onInit 回调才能说话，
 * 所以这里用 [ready] 挡一下，没就绪就直接放弃（不静默排队，免得她等半天突然冒出一句）。
 */
class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.CHINA)
                ready = r != TextToSpeech.LANG_MISSING_DATA &&
                        r != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ready) Log.e(TAG, "系统没有中文 TTS，只能靠文字了")
            } else {
                Log.e(TAG, "TTS 初始化失败：$status")
            }
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        // QUEUE_FLUSH：新的一句直接打断上一句，别排队念
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "garden")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val TAG = "Garden"
    }
}

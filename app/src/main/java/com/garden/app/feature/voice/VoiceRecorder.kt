package com.garden.app.feature.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * 录音，产出 16kHz / 单声道 / 16bit 的 WAV。
 *
 * 参数是照百炼 ASR 的要求定的，别乱改。
 *
 * 从他端那边简化过来的：去掉了蓝牙麦、VAD 自动断句、音量回调这些，
 * 只留"按住录、松手停"。
 */
class VoiceRecorder(private val ctx: Context) {

    companion object {
        private const val TAG = "Garden"
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var outFile: File? = null

    @Volatile
    private var recording = false
    private var bytes = 0L

    /** 出错原因，给界面显示人话用 */
    var lastError: String? = null
        private set

    /** 已经录了多少秒 */
    val seconds: Double get() = bytes.toDouble() / (SAMPLE_RATE * BYTES_PER_SAMPLE)

    fun start(): Boolean {
        lastError = null

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            lastError = "麦克风拿不到"
            return false
        }

        val r = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, minBuf * 2)
        } catch (e: Exception) {
            Log.e(TAG, "建 AudioRecord 失败", e)
            lastError = "没有录音权限"
            return false
        }

        if (r.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { r.release() }
            lastError = "录音没准备好"
            return false
        }

        val f = File(ctx.cacheDir, "voice_in.wav")
        bytes = 0
        record = r
        outFile = f

        // ⚠️ 必须显式 startRecording()。只 new 出来不 start 的话，
        //    read() 永远返回 0，录出来的 WAV 只有 44 字节的头 ——
        //    现象特别有欺骗性：按钮有反应、界面显示"录完了"，就是没声音。
        try {
            r.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "startRecording 失败", e)
            runCatching { r.release() }
            record = null
            lastError = "录音启动失败"
            return false
        }

        recording = true
        thread = Thread({
            try {
                f.outputStream().buffered().use { out ->
                    out.write(ByteArray(44))          // 先占位，录完回填头
                    val buf = ByteArray(minBuf)
                    while (recording) {
                        val n = r.read(buf, 0, buf.size)
                        if (n > 0) {
                            out.write(buf, 0, n)
                            bytes += n
                        }
                    }
                    out.flush()
                }
                writeWavHeader(f, bytes)
            } catch (e: Exception) {
                Log.e(TAG, "写录音文件失败", e)
            }
        }, "garden-rec").apply { start() }

        return true
    }

    /** 停止并返回录好的文件；没录到东西返回 null。 */
    fun stop(): File? {
        if (!recording && record == null) return null
        recording = false

        runCatching { record?.stop() }
        runCatching { thread?.join(1500) }
        runCatching { record?.release() }
        record = null
        thread = null

        val f = outFile
        outFile = null
        return if (f != null && f.exists() && f.length() > 44) f else null
    }

    /** 补 WAV 头。录的时候先占 44 字节，现在把真实长度填回去。 */
    private fun writeWavHeader(file: File, pcmBytes: Long) {
        runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                val byteRate = SAMPLE_RATE * BYTES_PER_SAMPLE
                val header = ByteArray(44)
                var p = 0

                fun str(s: String) { for (c in s) header[p++] = c.code.toByte() }
                fun int(v: Int) {
                    header[p++] = (v and 0xff).toByte()
                    header[p++] = ((v shr 8) and 0xff).toByte()
                    header[p++] = ((v shr 16) and 0xff).toByte()
                    header[p++] = ((v shr 24) and 0xff).toByte()
                }
                fun short(v: Int) {
                    header[p++] = (v and 0xff).toByte()
                    header[p++] = ((v shr 8) and 0xff).toByte()
                }

                str("RIFF")
                int((36 + pcmBytes).toInt())
                str("WAVE")
                str("fmt ")
                int(16)                       // fmt 块长度
                short(1)                      // PCM
                short(1)                      // 单声道
                int(SAMPLE_RATE)
                int(byteRate)
                short(BYTES_PER_SAMPLE)       // 块对齐
                short(16)                     // 位深
                str("data")
                int(pcmBytes.toInt())

                raf.seek(0)
                raf.write(header)
            }
        }.onFailure { Log.e(TAG, "写 WAV 头失败", it) }
    }
}

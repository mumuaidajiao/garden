package com.garden.app.core.net

import com.garden.app.core.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * 服务器【明确告诉我们】的错误。
 *
 * 这种不要翻译，原样给她看 —— 服务器往往比我们编的话有用得多。
 * 实例：服务器说「刚摇过了，等3分钟再摇好不好」，
 * 以前被错误处理吞成了一句没头没尾的「没送出去」。
 */
class ApiError(message: String) : IOException(message)

/**
 * 一次网络调用的结果。
 *
 * 失败必须能说清楚是哪一种失败 —— 断网的时候绝不能假装"送到了"，
 * 那比报错还糟：她会以为你在路上。
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    data class Err(val message: String) : ApiResult<Nothing>
}

/**
 * 一句话。
 *
 * ⚠️ [crisis] 和 [fromHim] 目前**全项目无人读取** —— 解析出来了，但没人用。
 *
 * 危机时那段「他亲手写的话」仍然能正确显示，不过走的是另一条路：
 * 它经 `/thread` 回来时 `who == "him"`，由 TalkScreen / Bubble 对 him 的
 * 金色描边样式区分，跟这两个字段没有关系。
 *
 * 所以下面这句「界面上要用不同的样式显示」是**没兑现的承诺**。
 * 要么把这两个字段接上（做危机态的专属 UI），要么删掉。
 */
data class Reply(
    val text: String,
    val crisis: Boolean,
    val fromHim: Boolean
)

data class Turn(
    val who: String,   // "her" / "mumu" / "him"
    val text: String,
    val at: Long
)

/**
 * 他留给她的东西。
 *
 * image 是 `data:image/...;base64,...`；audio 是裸的 base64 WAV（不带前缀）——
 * 两个格式不一样是因为音频那边本来就要按字节写文件，套 data url 没意义。
 * 都可能为空串。
 */
data class InboxItem(
    val id: Int,
    val text: String,
    val image: String,
    val audio: String,
    val at: Long
)

/** 聊天里的一条。who 是 "him" 或 "her"。 */
data class ChatItem(
    val id: Int,
    val who: String,
    val text: String,
    val hasImage: Boolean,
    val hasAudio: Boolean,
    val at: Long
)

/** 常驻服务拉回来的：他发的新东西 + 小园要主动说的话。 */
data class HerMessage(val id: Int, val text: String, val hasImage: Boolean, val hasAudio: Boolean)

data class HerPoll(
    val now: Long,
    val messages: List<HerMessage>,
    val proactive: String?
)

/** 日历上的一个重要日子（倒计时用）。 */
data class CalDay(
    val title: String,
    val date: String,
    val daysLeft: Int,
    val isToday: Boolean,
    val lunar: Boolean
)

/** 日历上自己记的一件事。 */
data class CalItem(
    val id: Int,
    val date: String,
    val title: String,
    val by: String
)

data class CalendarData(
    val today: String,
    val days: List<CalDay>,
    val mine: List<CalItem>
)

/** 服务器上的一首歌。 */
data class MusicTrack(
    val name: String,
    val size: Long,
    val at: Long
)

object RelayClient {

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        // ⚠️ 超时别设太长。
        //
        // 原来 readTimeout 是 70 秒（为了迁就 DeepSeek），结果 2026-09-16 半夜
        // 网络断了的时候，她按下按钮就对着转圈干等一分多钟 —— 那比直接报错还难受，
        // 因为她不知道是在等还是在坏。
        // 关掉思考之后模型 1-2 秒就回，20 秒有余量了。
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * 语音单独用一个长超时的 client。
     *
     * ⚠️ 它比打字多两道手续：把整段音频传上去 + 服务器调 ASR 识别。
     * 2026-09-17 实测：她 30 多秒的语音卡在 20 秒读超时上，
     * 表现就是「没发出去」——她连着遇到两次。
     * 文字保持 20 秒（DeepSeek 一两秒就回），语音给到 2 分钟。
     */
    private val voiceClient = client.newBuilder()
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // ── 接口 ────────────────────────────────────────────────

    /** 她按下按钮。返回 (会话 id, 小园的第一句话)。 */
    suspend fun ping(): ApiResult<Pair<Int, String>> = call {
        val j = post("/ping", JSONObject().put("token", AppConfig.token))
        j.getInt("sessionId") to j.getString("greeting")
    }

    /**
     * 她按「小小的打扰一下」。
     *
     * 跟「想你啦」不是一回事：那个是"我现在不好，需要你"，
     * 这个是"我没那么严重，就是想你了"。所以它【不拨电话】，
     * 只让他手机震一下、听到一句提醒。
     */
    suspend fun knock(): ApiResult<Unit> = call {
        post("/knock", JSONObject().put("token", AppConfig.token))
        Unit
    }

    /** 拉他留给她的东西。 */
    suspend fun inbox(): ApiResult<List<InboxItem>> = call {
        val url = "${AppConfig.baseUrl}/inbox?token=${AppConfig.token}"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val arr = JSONObject(text).getJSONArray("items")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                InboxItem(
                    id = o.optInt("id"),
                    text = o.optString("text"),
                    image = o.optString("image"),
                    audio = o.optString("audio"),
                    at = o.optLong("at")
                )
            }
        }
    }

    /**
     * 她按住说了一段话。
     *
     * 音频传上去，服务器转成文字，然后跟打字走完全一样的那条路 ——
     * 所以她说「我不想活了」和打「我不想活了」的结果是一样的。
     *
     * @param wavBase64 16kHz 单声道 WAV 的 base64（不带 data url 前缀）
     */
    suspend fun voice(sessionId: Int, wavBase64: String): ApiResult<Reply> = call {
        val j = postWith(
            voiceClient,
            "/voice",
            JSONObject()
                .put("token", AppConfig.token)
                .put("sessionId", sessionId)
                .put("audio", wavBase64)
        )
        Reply(
            text = j.getString("reply"),
            crisis = j.optBoolean("crisis", false),
            fromHim = j.optString("replyFrom") == "him"
        )
    }

    /** 哥哥的状态 + 我那些消息走到哪一步了。主页那一小块用的。 */
    data class HomeInfo(
        val status: String,     // 他交代自己在干嘛，可能为空
        val state: String       // none / sent / delivered / read
    )

    suspend fun homeInfo(): ApiResult<HomeInfo> = call {
        val url = "${AppConfig.baseUrl}/mystate?token=${AppConfig.token}"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val j = JSONObject(text)
            HomeInfo(
                status = j.optString("status"),
                state = j.optString("state", "none")
            )
        }
    }

    /**
     * 常驻服务每 15 秒拉一次。
     *
     * 返回他新发的东西 + 小园要主动说的话（可能没有）。
     * 只给元信息不给正文 —— 正文她点通知进 App 自己拉，
     * 免得每 15 秒把图片和语音的 base64 重新传一遍。
     */
    suspend fun herPoll(since: Long): ApiResult<HerPoll> = call {
        val url = "${AppConfig.baseUrl}/herpoll?token=${AppConfig.token}&since=$since"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val j = JSONObject(text)

            val arr = j.optJSONArray("messages")
            val msgs = if (arr == null) emptyList()
            else (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HerMessage(
                    id = o.optInt("id"),
                    text = o.optString("text"),
                    hasImage = o.optBoolean("hasImage"),
                    hasAudio = o.optBoolean("hasAudio")
                )
            }

            val p = j.optJSONObject("proactive")
            HerPoll(
                now = j.optLong("now", System.currentTimeMillis()),
                messages = msgs,
                proactive = p?.optString("text")?.takeIf { it.isNotBlank() }
            )
        }
    }

    /** 读通知开关：true = 只有危机才告诉他。 */
    suspend fun notifyCrisisOnly(): ApiResult<Boolean> = call {
        val url = "${AppConfig.baseUrl}/notify?token=${AppConfig.token}"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            JSONObject(text).optBoolean("crisisOnly")
        }
    }

    /**
     * 设通知开关。
     *
     * crisisOnly = true：哥哥只收得到危机，别的都不打扰他。
     * 这是她的选择权 —— 被一直看着，比漏几条消息更让人难受。
     */
    suspend fun setNotify(crisisOnly: Boolean): ApiResult<Unit> = call {
        post(
            "/notify",
            JSONObject().put("token", AppConfig.token).put("crisisOnly", crisisOnly)
        )
        Unit
    }

    /** 拉整条聊天记录（他发的 + 她发的，服务器按时间合好了）。 */
    suspend fun chat(): ApiResult<List<ChatItem>> = call {
        val url = "${AppConfig.baseUrl}/chat?token=${AppConfig.token}"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val arr = JSONObject(text).getJSONArray("items")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatItem(
                    id = o.optInt("id"),
                    who = o.optString("who"),
                    text = o.optString("text"),
                    hasImage = o.optBoolean("hasImage"),
                    hasAudio = o.optBoolean("hasAudio"),
                    at = o.optLong("at")
                )
            }
        }
    }

    /** 读「不吵啦」的状态。 */
    suspend fun isQuiet(): ApiResult<Boolean> = call {
        val url = "${AppConfig.baseUrl}/quiet?token=${AppConfig.token}"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val t = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            JSONObject(t).optBoolean("quiet")
        }
    }

    /**
     * 按「不吵啦」，或者回来。
     *
     * quiet = true：通知全关，连危机都不推给哥哥。
     * 但她还能按「想你啦」、还能在聊天里说话 ——
     * 那些是她【主动来找他】，跟"被通知吵"是两回事。
     */
    suspend fun setQuiet(quiet: Boolean): ApiResult<Unit> = call {
        post("/quiet", JSONObject().put("token", AppConfig.token).put("quiet", quiet))
        Unit
    }

    /**
     * 取一条语音的音频（base64 WAV）。
     *
     * 聊天记录里只有"这条有语音"的标记，音频点的时候才来拿 ——
     * 一条 100KB 起步，每几秒拉一次记录都塞进去太费流量。
     *
     * （2026-09-20 更正：这段注释原来错位挂在 [isQuiet] 头上，
     *   现在挪回它该在的地方。）
     */
    suspend fun audioOf(kind: String, id: Int): ApiResult<String> = call {
        val url = "${AppConfig.baseUrl}/audio?token=${AppConfig.token}&kind=$kind&id=$id"
        val req = Request.Builder().url(url).get().build()
        voiceClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            JSONObject(text).getString("audio")
        }
    }

    /**
     * 取一条消息里的图（`data:image/...;base64,...`）。
     *
     * 跟 [audioOf] 一个道理：聊天记录接口只回「有没有图」，
     * 图本身进到那一条的时候才拿 —— 一张几百 KB，
     * 每 5 秒拉一次记录都把图塞进去太费流量。
     *
     * 走 voiceClient（120 秒）：图比语音还大，4MB 的图 base64 之后 5MB 出头，
     * 20 秒的普通超时在移动网络下未必够。
     */
    suspend fun imageOf(kind: String, id: Int): ApiResult<String> = call {
        val url = "${AppConfig.baseUrl}/image?token=${AppConfig.token}&kind=$kind&id=$id"
        val req = Request.Builder().url(url).get().build()
        voiceClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            JSONObject(text).getString("image")
        }
    }

    /** 拉她的头像（data url）。她还没设过就是空串，不是错。 */
    suspend fun getAvatar(): ApiResult<String> = call {
        val url = "${AppConfig.baseUrl}/avatar?token=${AppConfig.token}"
        val req = Request.Builder().url(url).get().build()
        voiceClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            JSONObject(text).getString("image")
        }
    }

    /** 换头像。image 是 `data:image/jpeg;base64,...`（她端传之前已经压到 512）。 */
    suspend fun setAvatar(image: String): ApiResult<Unit> = call {
        postWith(
            voiceClient,
            "/avatar",
            JSONObject().put("token", AppConfig.token).put("image", image)
        )
        Unit
    }

    // ── 一起听：歌 ──────────────────────────────────────────

    /** 服务器上有哪些歌。 */
    suspend fun musicList(): ApiResult<List<MusicTrack>> = call {
        val url = "${AppConfig.baseUrl}/music/list?token=${AppConfig.token}"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val arr = JSONObject(text).getJSONArray("items")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MusicTrack(
                    name = o.optString("name"),
                    size = o.optLong("size"),
                    at = o.optLong("at")
                )
            }
        }
    }

    /**
     * 下载一首歌（裸 base64，不带 data url 前缀）。
     *
     * 用 voiceClient（120 秒）：一首歌三四 MB，base64 之后还要涨三分之一，
     * 20 秒的普通超时在移动网络下不够。
     */
    suspend fun musicGet(name: String): ApiResult<String> = call {
        val url = "${AppConfig.baseUrl}/music/get?token=${AppConfig.token}" +
                "&name=${java.net.URLEncoder.encode(name, "UTF-8")}"
        val req = Request.Builder().url(url).get().build()
        voiceClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val j = JSONObject(text)
            if (!j.optBoolean("ok")) throw ApiError(j.optString("error", "这首歌拿不到"))
            j.getString("audio")
        }
    }

    /**
     * 往服务器传一首歌。
     *
     * 也走 voiceClient：传的这边同样是大 body。
     */
    suspend fun musicUpload(name: String, base64: String): ApiResult<Unit> = call {
        val j = postWith(
            voiceClient,
            "/music/upload",
            JSONObject()
                .put("token", AppConfig.token)
                .put("name", name)
                .put("audio", base64)
        )
        // 服务器拒绝时走的是 ok:false（比如重名），postWith 会抛 ApiError 把原话带出来
        if (!j.optBoolean("ok")) throw ApiError(j.optString("error", "没传上去"))
        Unit
    }

    /** 她在聊天里跟他说一句。 */
    suspend fun sayToHim(text: String, audioBase64: String = ""): ApiResult<Unit> = call {
        postWith(
            voiceClient,
            "/fromher",
            JSONObject()
                .put("token", AppConfig.token)
                .put("text", text)
                .put("audio", audioBase64)
        )
        Unit
    }

    /**
     * 摇他一下 —— 他手机会持续震一分钟。
     *
     * 服务器十分钟只让摇一次。狂按的后果不是"他快点回"，是"他关机"。
     */
    suspend fun shake(): ApiResult<Unit> = call {
        post("/shake", JSONObject().put("token", AppConfig.token))
        Unit
    }

    /** 拉日历：重要日子倒计时 + 自己记的那些。 */
    suspend fun calendar(): ApiResult<CalendarData> = call {
        val url = "${AppConfig.baseUrl}/calendar?token=${AppConfig.token}"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val j = JSONObject(text)

            val dArr = j.optJSONArray("days")
            val days = if (dArr == null) emptyList()
            else (0 until dArr.length()).map { i ->
                val o = dArr.getJSONObject(i)
                CalDay(
                    title = o.optString("title"),
                    date = o.optString("date"),
                    daysLeft = o.optInt("daysLeft"),
                    isToday = o.optBoolean("isToday"),
                    lunar = o.optBoolean("lunar")
                )
            }

            val mArr = j.optJSONArray("mine")
            val mine = if (mArr == null) emptyList()
            else (0 until mArr.length()).map { i ->
                val o = mArr.getJSONObject(i)
                CalItem(
                    id = o.optInt("id"),
                    date = o.optString("date"),
                    title = o.optString("title"),
                    by = o.optString("by")
                )
            }

            CalendarData(today = j.optString("today"), days = days, mine = mine)
        }
    }

    /** 在日历上记一件。 */
    suspend fun addCalItem(date: String, title: String): ApiResult<Unit> = call {
        post(
            "/calendar",
            JSONObject()
                .put("token", AppConfig.token)
                .put("date", date)
                .put("title", title)
        )
        Unit
    }

    /** 划掉日历上的一条。 */
    suspend fun delCalItem(id: Int): ApiResult<Unit> = call {
        post(
            "/calendar/delete",
            JSONObject().put("token", AppConfig.token).put("id", id)
        )
        Unit
    }

    /** 她说了一句，拿回小园（或他）的回应。 */
    suspend fun say(sessionId: Int, text: String): ApiResult<Reply> = call {
        val j = post(
            "/say",
            JSONObject()
                .put("token", AppConfig.token)
                .put("sessionId", sessionId)
                .put("text", text)
        )
        Reply(
            text = j.getString("reply"),
            crisis = j.optBoolean("crisis", false),
            fromHim = j.optString("replyFrom") == "him"
        )
    }

    /** 拉取这一轮里新出现的消息（主要用来看他有没有回话）。 */
    suspend fun thread(sessionId: Int, since: Int): ApiResult<List<Turn>> = call {
        val url = "${AppConfig.baseUrl}/thread" +
                "?token=${AppConfig.token}&sessionId=$sessionId&since=$since"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val arr = JSONObject(text).getJSONArray("turns")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Turn(
                    who = o.getString("who"),
                    text = o.getString("text"),
                    at = o.getLong("at")
                )
            }
        }
    }

    // ── 内部 ────────────────────────────────────────────────

    private fun post(path: String, json: JSONObject): JSONObject =
        postWith(client, path, json)

    private fun postWith(c: OkHttpClient, path: String, json: JSONObject): JSONObject {
        val req = Request.Builder()
            .url(AppConfig.baseUrl + path)
            .post(json.toString().toRequestBody(JSON_TYPE))
            .build()
        c.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 403) throw IOException("令牌不对")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            if (text.isBlank()) throw IOException("空响应")
            val j = JSONObject(text)
            if (!j.optBoolean("ok")) {
                // ApiError 而不是 IOException：下面的 friendly() 见到它就原样透传
                throw ApiError(j.optString("error", "服务器没接住"))
            }
            return j
        }
    }

    private suspend fun <T> call(block: () -> T): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            ApiResult.Ok(block())
        } catch (e: Exception) {
            ApiResult.Err(friendly(e))
        }
    }

    /**
     * 把异常翻译成她能看懂的一句话。
     *
     * 原则：**说清楚是"没送到"，不是"送到了但没人回"**。
     * 网络不通的时候含糊其辞最要命 —— 她会以为消息已经发出去了、
     * 只是在等回复，于是一直等下去。
     */
    private fun friendly(e: Exception): String = when (e) {
        // 服务器说的原话，一字不改 —— 它知道原因，我们不知道
        is ApiError -> e.message ?: "出了点问题"
        is SocketTimeoutException -> "网好像不太行，没送出去。等会儿再按一下好不好"
        is UnknownHostException -> "网好像断了，没送出去"
        is ConnectException -> "连不上，没送出去。换个网试试"
        is IOException -> "没送出去，等会儿再按一下好不好"
        else -> "没送出去，再试一次好不好"
    }
}

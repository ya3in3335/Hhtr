package com.ultron.chat.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Microsoft Edge (online neural) text-to-speech, called DIRECTLY from the phone.
 * Datacenter IPs (Cloudflare/AWS) get 403 from Microsoft, but a phone's own
 * network is not blocked — so this runs client-side with no server at all.
 *
 * Returns MP3 bytes, or null on any failure (caller then falls back to the
 * offline on-device voice).
 */
object EdgeTts {

    private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val WSS =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
    private const val GEC_VERSION = "1-130.0.2849.68"
    // The Origin the edge-tts clients use; Microsoft rejects other origins.
    private const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun voiceFor(lang: String): Pair<String, String> = when (lang) {
        "ar" -> "ar-SA-HamedNeural" to "ar-SA"
        "fr" -> "fr-FR-HenriNeural" to "fr-FR"
        else -> "en-US-ChristopherNeural" to "en-US"
    }

    private fun secMsGec(): String {
        val ticks = (System.currentTimeMillis() / 1000L + 11644473600L) * 10_000_000L
        val rounded = ticks - (ticks % 3_000_000_000L)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((rounded.toString() + TOKEN).toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun uuidHex() = UUID.randomUUID().toString().replace("-", "")

    private fun xmlEsc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    /**
     * Synthesizes [text] with a deep "monster" prosody. Blocking; call off the
     * main thread. [pitch]/[rate] are SSML prosody values (e.g. "-30%", "-8%").
     */
    fun synth(text: String, lang: String, pitch: String = "-30%", rate: String = "-8%"): ByteArray? {
        val clean = text.take(2000)
        if (clean.isBlank()) return null
        val (voice, xmlLang) = voiceFor(lang)
        return try {
            val url = "$WSS?TrustedClientToken=$TOKEN&Sec-MS-GEC=${secMsGec()}" +
                "&Sec-MS-GEC-Version=$GEC_VERSION&ConnectionId=${uuidHex()}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Origin", ORIGIN)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .build()

            val chunks = ArrayList<ByteArray>()
            val done = CountDownLatch(1)
            var ok = false

            val ws = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val config =
                        "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    val ssml =
                        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$xmlLang'>" +
                        "<voice name='$voice'><prosody pitch='$pitch' rate='$rate'>${xmlEsc(clean)}</prosody></voice></speak>"
                    val ssmlMsg =
                        "X-RequestId:${uuidHex()}\r\nContent-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${java.util.Date()}\r\nPath:ssml\r\n\r\n$ssml"
                    webSocket.send(config)
                    webSocket.send(ssmlMsg)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) { ok = true; done.countDown() }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val b = bytes.toByteArray()
                    if (b.size > 2) {
                        val headerLen = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
                        val start = 2 + headerLen
                        if (start < b.size) chunks.add(b.copyOfRange(start, b.size))
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    done.countDown()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    done.countDown()
                }
            })

            val finished = done.await(28, TimeUnit.SECONDS)
            ws.close(1000, null)
            if (!finished || !ok || chunks.isEmpty()) return null

            val total = chunks.sumOf { it.size }
            val out = ByteArray(total)
            var off = 0
            for (c in chunks) { c.copyInto(out, off); off += c.size }
            if (out.isEmpty()) null else out
        } catch (_: Throwable) {
            null
        }
    }
}

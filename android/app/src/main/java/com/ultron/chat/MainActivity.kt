package com.ultron.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import com.ultron.chat.net.EdgeTts
import com.ultron.chat.ui.App
import com.ultron.chat.ui.AvatarBus
import com.ultron.chat.ui.Host
import com.ultron.chat.ui.LocalHost
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : ComponentActivity(), Host {

    private val vm: ChatViewModel by viewModels()
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    @Volatile private var speakSeq = 0   // cancels stale Edge-TTS jobs

    /** Drives the 3D avatar's lips: speaking while an utterance plays, idle otherwise. */
    private val avatarSpeechListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) { runOnUiThread { AvatarBus.speaking = true } }
        override fun onDone(utteranceId: String?) { runOnUiThread { AvatarBus.speaking = false } }
        @Deprecated("deprecated in API 21")
        override fun onError(utteranceId: String?) { runOnUiThread { AvatarBus.speaking = false } }
        override fun onError(utteranceId: String?, errorCode: Int) { runOnUiThread { AvatarBus.speaking = false } }
        override fun onStop(utteranceId: String?, interrupted: Boolean) { runOnUiThread { AvatarBus.speaking = false } }
    }

    private var pendingWantMic = false
    private var pendingWantLoc = false
    private var pendingCb: ((Boolean, Boolean) -> Unit)? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingCb?.invoke(pendingWantMic && micGranted(), pendingWantLoc && locGranted())
        pendingCb = null
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = Prefs(newBase).lang
        super.attachBaseContext(LocaleHelper.wrap(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalHost provides this) {
                App(vm)
            }
        }
    }

    override fun onDestroy() {
        stopListening()
        try { player?.release() } catch (_: Exception) {}
        player = null
        tts?.shutdown()
        super.onDestroy()
    }

    /* ---------- Host ---------- */

    override fun micGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun locGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun requestPermissions(wantMic: Boolean, wantLoc: Boolean, done: (Boolean, Boolean) -> Unit) {
        val req = mutableListOf<String>()
        if (wantMic && !micGranted()) req += Manifest.permission.RECORD_AUDIO
        if (wantLoc && !locGranted()) {
            req += Manifest.permission.ACCESS_FINE_LOCATION
            req += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (req.isEmpty()) {
            done(wantMic && micGranted(), wantLoc && locGranted())
            return
        }
        pendingWantMic = wantMic
        pendingWantLoc = wantLoc
        pendingCb = done
        permLauncher.launch(req.toTypedArray())
    }

    override fun startListening(lang: String, onText: (String) -> Unit, onEnd: () -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { onEnd(); return }
        stopListening()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { onEnd() }
            override fun onResults(results: Bundle?) {
                val m = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                onText(m?.firstOrNull().orEmpty())
                onEnd()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val m = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!m.isNullOrEmpty()) onText(m[0])
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag(lang))
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        try { r.startListening(intent) } catch (_: Exception) { onEnd() }
    }

    override fun stopListening() {
        recognizer?.let {
            try { it.stopListening() } catch (_: Exception) {}
            try { it.destroy() } catch (_: Exception) {}
        }
        recognizer = null
    }

    override fun speak(text: String, lang: String) {
        val clean = text.replace(Regex("[*`#>_]"), "").trim()
        if (clean.isEmpty()) return
        stopSpeaking()
        val seq = ++speakSeq
        // Try Microsoft Edge neural TTS first (deep monster prosody), directly
        // from the phone. Fall back to the offline on-device voice on any failure.
        thread {
            val mp3 = EdgeTts.synth(clean, lang, pitch = "-30%", rate = "-8%")
            if (seq != speakSeq) return@thread   // superseded by a newer request
            runOnUiThread {
                if (seq != speakSeq) return@runOnUiThread
                if (mp3 != null && mp3.isNotEmpty()) playMp3(mp3)
                else speakOffline(clean, lang)
            }
        }
    }

    private fun playMp3(bytes: ByteArray) {
        try {
            val f = File(cacheDir, "ultron_tts.mp3")
            f.writeBytes(bytes)
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(f.absolutePath)
            mp.setOnPreparedListener { it.start(); AvatarBus.speaking = true }
            mp.setOnCompletionListener { AvatarBus.speaking = false; it.release(); if (player === it) player = null }
            mp.setOnErrorListener { p, _, _ -> AvatarBus.speaking = false; p.release(); if (player === p) player = null; true }
            player = mp
            mp.prepareAsync()
        } catch (_: Exception) {
            AvatarBus.speaking = false
        }
    }

    private fun speakOffline(clean: String, lang: String) {
        val locale = Locale(lang)
        fun play(engine: TextToSpeech) {
            engine.language = locale
            engine.setPitch(0.6f)              // deep monster pitch
            engine.setSpeechRate(0.92f)
            engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "ultron")
        }
        val t = tts
        if (t == null) {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) tts?.let {
                    it.setOnUtteranceProgressListener(avatarSpeechListener)
                    play(it)
                }
            }
        } else {
            play(t)
        }
    }

    override fun stopSpeaking() {
        speakSeq++   // invalidate any in-flight Edge-TTS job
        try { tts?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        AvatarBus.speaking = false
    }

    override fun lastLocation(cb: (Double?, Double?) -> Unit) {
        if (!locGranted()) { cb(null, null); return }
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            cb(loc?.latitude, loc?.longitude)
        } catch (_: SecurityException) {
            cb(null, null)
        }
    }

    override fun setLocale(lang: String) {
        Prefs(this).lang = lang
        recreate()
    }

    override fun currentLang(): String = Prefs(this).lang

    private fun localeTag(lang: String) = when (lang) {
        "ar" -> "ar-SA"
        "fr" -> "fr-FR"
        else -> "en-US"
    }
}

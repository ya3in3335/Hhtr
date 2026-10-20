package com.ultron.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Red = Color(0xFFE11D2A)
private val RedSoft = Color(0xFFFF4D5A)
private val Bg = Color(0xFF120212)
private val Surface = Color(0xFF1A0A1A)
private val TextC = Color(0xFFFFE9EA)
private val Muted = Color(0xFFD08A8F)

private val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private fun base(b: String) = b.trim().trimEnd('/')

    fun getPrompt(b: String, token: String): Result<String> = try {
        val req = Request.Builder().url("${base(b)}/admin/prompt").header("X-Admin-Token", token.trim()).build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) Result.failure(RuntimeException(errOf(t, r.code)))
            else Result.success(JSONObject(t).optString("prompt", ""))
        }
    } catch (e: Exception) { Result.failure(e) }

    fun savePrompt(b: String, token: String, prompt: String): Result<Unit> = post("${base(b)}/admin/prompt", token, JSONObject().put("prompt", prompt))
    fun changePw(b: String, token: String, np: String): Result<Unit> = post("${base(b)}/admin/password", token, JSONObject().put("password", np))

    private fun post(url: String, token: String, body: JSONObject): Result<Unit> = try {
        val req = Request.Builder().url(url)
            .header("X-Admin-Token", token.trim()).header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON)).build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) Result.failure(RuntimeException(errOf(t, r.code))) else Result.success(Unit)
        }
    } catch (e: Exception) { Result.failure(e) }

    fun meta(b: String): Result<JSONObject> = try {
        val req = Request.Builder().url("${base(b)}/api/meta").build()
        client.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) Result.failure(RuntimeException("HTTP ${r.code}")) else Result.success(JSONObject(t))
        }
    } catch (e: Exception) { Result.failure(e) }

    private fun errOf(body: String, code: Int): String = try { JSONObject(body).optString("error", "HTTP $code") } catch (e: Exception) { "HTTP $code" }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Red, background = Bg, surface = Surface, onSurface = TextC, onBackground = TextC)
            ) { AdminApp() }
        }
    }
}

@Composable
fun AdminApp() {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("ultron_admin", 0) }
    val font = Tajawal
    var base by remember { mutableStateOf(sp.getString("base", "https://ultron-proxy.yacincianai.workers.dev") ?: "") }
    var token by remember { mutableStateOf(sp.getString("token", "") ?: "") }
    var prompt by remember { mutableStateOf("") }
    var newpw by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var statusOk by remember { mutableStateOf(false) }
    var pwstatus by remember { mutableStateOf("") }
    var pwOk by remember { mutableStateOf(false) }
    var metaText by remember { mutableStateOf("—") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun persist() { sp.edit().putString("base", base.trim()).putString("token", token.trim()).apply() }
    fun refreshMeta() = scope.launch {
        val r = withContext(Dispatchers.IO) { Api.meta(base) }
        r.onSuccess { m ->
            metaText = (if (m.optBoolean("keyConfigured")) "المفتاح ✓" else "المفتاح ✗") + " · " +
                (if (m.optBoolean("promptConfigured")) "برومبت مضبوط" else "لا برومبت") + " · " +
                (if (m.optBoolean("kvBound")) "KV ✓" else "KV ✗")
        }.onFailure { metaText = "—" }
    }
    LaunchedEffect(Unit) { refreshMeta() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(42.dp))
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Ultron — لوحة الأدمن", color = TextC, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("البرومبت يُحفظ في Cloudflare ويُطبّق فورًا", color = Muted, fontFamily = font, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.size(18.dp))

        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("رمز دخول الأدمن", fontFamily = font) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(10.dp))
        Row {
            OutlinedButton(
                onClick = {
                    if (token.isBlank()) { status = "أدخل الرمز أولًا."; statusOk = false } else {
                        busy = true; status = "جارٍ التحميل…"; statusOk = false
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Api.getPrompt(base, token) }
                            busy = false
                            r.onSuccess { prompt = it; status = "تم التحميل ✓"; statusOk = true; persist(); refreshMeta() }
                                .onFailure { status = "فشل: ${it.message}"; statusOk = false }
                        }
                    }
                },
                enabled = !busy,
            ) { Text("تحميل", fontFamily = font) }
        }
        Spacer(Modifier.size(16.dp))

        Text("البرومبت الداخلي للنموذج", color = TextC, fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it },
            placeholder = { Text("اكتب البرومبت الداخلي هنا…", fontFamily = font, color = Muted) },
            minLines = 6, maxLines = 14,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (token.isBlank()) { status = "أدخل الرمز أولًا."; statusOk = false } else {
                        busy = true; status = "جارٍ الحفظ…"; statusOk = false
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Api.savePrompt(base, token, prompt) }
                            busy = false
                            r.onSuccess { status = "تم الحفظ في Cloudflare ✓"; statusOk = true; persist(); refreshMeta() }
                                .onFailure { status = "فشل الحفظ: ${it.message}"; statusOk = false }
                        }
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Red),
            ) { Text("حفظ في Cloudflare", fontFamily = font, color = Color.White) }
            Spacer(Modifier.size(12.dp))
            Text(metaText, color = Muted, fontFamily = font, fontSize = 11.sp)
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Text(status, color = if (statusOk) Color(0xFF7CFFA0) else RedSoft, fontFamily = font, fontSize = 13.sp)
        }

        Spacer(Modifier.size(26.dp))
        Text("تغيير كلمة مرور الأدمن", color = TextC, fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = newpw, onValueChange = { newpw = it },
            label = { Text("كلمة مرور جديدة", fontFamily = font) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(10.dp))
        OutlinedButton(
            onClick = {
                when {
                    token.isBlank() -> { pwstatus = "أدخل كلمة المرور الحالية بالأعلى."; pwOk = false }
                    newpw.trim().length < 4 -> { pwstatus = "كلمة المرور الجديدة قصيرة."; pwOk = false }
                    else -> {
                        busy = true; pwstatus = "جارٍ التغيير…"; pwOk = false
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Api.changePw(base, token, newpw.trim()) }
                            busy = false
                            r.onSuccess { token = newpw.trim(); newpw = ""; pwstatus = "تم تغيير كلمة المرور ✓"; pwOk = true; persist() }
                                .onFailure { pwstatus = "فشل: ${it.message}"; pwOk = false }
                        }
                    }
                }
            },
            enabled = !busy,
        ) { Text("تغيير", fontFamily = font) }
        if (pwstatus.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Text(pwstatus, color = if (pwOk) Color(0xFF7CFFA0) else RedSoft, fontFamily = font, fontSize = 13.sp)
        }
        Spacer(Modifier.size(30.dp))
    }
}

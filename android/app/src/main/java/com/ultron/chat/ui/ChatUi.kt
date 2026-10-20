package com.ultron.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultron.chat.ChatViewModel
import com.ultron.chat.Msg
import com.ultron.chat.Prefs
import com.ultron.chat.R
import com.ultron.chat.Status
import com.ultron.chat.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

@Composable
fun App(vm: ChatViewModel) {
    UltronTheme {
        var showSplash by remember { mutableStateOf(true) }
        if (showSplash) {
            SplashScreen { showSplash = false }
        } else {
            ChatScreen(vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val host = LocalHost.current
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()

    var apiBase by remember { mutableStateOf(prefs.apiBase) }
    var ghToken by remember { mutableStateOf(prefs.ghToken) }
    var micOn by remember { mutableStateOf(host.micGranted()) }
    var locOn by remember { mutableStateOf(host.locGranted()) }
    var listening by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    var showPerms by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val errText = stringResource(R.string.err_connect)
    val locLabel = stringResource(R.string.your_location)

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    fun fire(text: String) = vm.send(apiBase, text, errText) { reply -> if (micOn) host.speak(reply, prefs.lang) }

    fun doSend(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        input = ""
        // Consent-gated device context: attach location only when enabled AND explicitly asked.
        if (locOn && wantsLocation(t)) {
            host.lastLocation { lat, lon ->
                val note = if (lat != null && lon != null) "\n\n[$locLabel: $lat, $lon]" else ""
                fire(t + note)
            }
        } else {
            fire(t)
        }
    }

    fun toggleMic() {
        if (!micOn) { showPerms = true; return }
        if (listening) { host.stopListening(); listening = false; vm.setStatus(Status.READY); return }
        listening = true
        vm.setStatus(Status.LISTENING)
        host.startListening(
            prefs.lang,
            onText = { input = it },
            onEnd = {
                listening = false
                vm.setStatus(Status.READY)
                val t = input
                if (t.isNotBlank()) doSend(t)
            },
        )
    }

    val statusText = when (vm.status) {
        Status.READY -> stringResource(R.string.status_ready)
        Status.THINKING -> stringResource(R.string.status_thinking)
        Status.LISTENING -> stringResource(R.string.status_listening)
    }

    Scaffold(
        containerColor = UltronBg,
        topBar = {
            TopBar(
                status = statusText,
                permsActive = micOn || locOn,
                onPerms = { showPerms = true },
                onLang = { showLang = true },
                onSettings = { apiBase = prefs.apiBase; ghToken = prefs.ghToken; showSettings = true },
                onNew = { vm.clear() },
            )
        },
        bottomBar = {
            Composer(
                value = input,
                onValue = { input = it },
                onSend = { doSend(input) },
                onMic = { toggleMic() },
                listening = listening,
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.messages.isEmpty()) {
                EmptyState(micOn, locOn)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(vm.messages.size) { i -> MessageItem(vm.messages[i]) }
                    if (vm.busy) item { TypingRow() }
                }
            }
        }
    }

    if (showPerms) {
        ModalBottomSheet(onDismissRequest = { showPerms = false }, containerColor = UltronSurface2) {
            PermsSheet(
                micInit = micOn, locInit = locOn,
                onConfirm = { wantMic, wantLoc ->
                    host.requestPermissions(wantMic, wantLoc) { m, l ->
                        micOn = m; locOn = l; showPerms = false
                    }
                },
                onClose = { showPerms = false },
            )
        }
    }
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = UltronSurface2) {
            SettingsSheet(
                apiBase = apiBase, onApiBase = { apiBase = it },
                ghToken = ghToken, onGhToken = { ghToken = it },
                onConnect = { cb ->
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { GitHubApi.validate(ghToken.trim()) }
                        res.onSuccess { login -> prefs.ghToken = ghToken.trim(); cb("✓ $login") }
                            .onFailure { cb("✗ ${it.message}") }
                    }
                },
                onSave = {
                    prefs.apiBase = apiBase.trim()
                    prefs.ghToken = ghToken.trim()
                    showSettings = false
                },
                onLang = { showSettings = false; showLang = true },
            )
        }
    }
    if (showLang) {
        ModalBottomSheet(onDismissRequest = { showLang = false }, containerColor = UltronSurface2) {
            LangSheet(current = prefs.lang, onPick = { code ->
                showLang = false
                host.setLocale(code)
            })
        }
    }
}

@Composable
private fun TopBar(
    status: String,
    permsActive: Boolean,
    onPerms: () -> Unit,
    onLang: () -> Unit,
    onSettings: () -> Unit,
    onNew: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UltronSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.size(10.dp))
        Column {
            Text("Ultron", color = UltronText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(status, color = UltronMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onPerms) {
            Icon(Icons.Filled.Security, contentDescription = null, tint = if (permsActive) UltronRed else UltronText)
        }
        IconButton(onClick = onLang) { Icon(Icons.Filled.Translate, contentDescription = null, tint = UltronText) }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = null, tint = UltronText) }
        IconButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = null, tint = UltronText) }
    }
}

@Composable
private fun EmptyState(micOn: Boolean, locOn: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(84.dp))
        Spacer(Modifier.size(14.dp))
        Text(stringResource(R.string.empty_title), color = UltronText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.empty_desc), color = UltronMuted, fontSize = 13.sp)
        Spacer(Modifier.size(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (micOn) stringResource(R.string.mic_on) else stringResource(R.string.mic_off), micOn)
            Pill(if (locOn) stringResource(R.string.loc_on) else stringResource(R.string.loc_off), locOn)
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean) {
    Box(
        Modifier
            .border(1.dp, if (on) Color(0xFF2E6B3F) else UltronLine, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(text, color = if (on) Color(0xFF7CFFA0) else UltronMuted, fontSize = 11.sp) }
}

@Composable
private fun MessageItem(msg: Msg) {
    if (msg.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .background(UltronRed, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) { Text(msg.content, color = Color.White, fontSize = 15.sp) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(msg.content, color = UltronText, fontSize = 15.sp)
                if (!msg.reasoning.isNullOrBlank()) ReasoningBlock(msg.reasoning)
            }
        }
    }
}

@Composable
private fun ReasoningBlock(text: String) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(top = 8.dp)
            .border(1.dp, UltronLine, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧠 " + stringResource(R.string.think_show), color = UltronMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = UltronMuted, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(open) {
            Text(text, color = UltronMuted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun TypingRow() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.size(10.dp))
        Text("•••", color = UltronRedSoft, fontSize = 18.sp)
    }
}

@Composable
private fun Composer(
    value: String,
    onValue: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    listening: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UltronSurface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMic) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = if (listening) UltronRed else UltronText)
        }
        Box(
            Modifier
                .weight(1f)
                .background(Color(0xFF200607), RoundedCornerShape(22.dp))
                .border(1.dp, UltronLine, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.compose_hint), color = UltronMuted, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                textStyle = TextStyle(color = UltronText, fontFamily = Tajawal, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(UltronRed),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(8.dp))
        Box(
            Modifier
                .size(44.dp)
                .background(UltronRed, CircleShape)
                .clickable { onSend() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White) }
    }
}

/* ---------- Bottom sheets ---------- */

@Composable
private fun PermsSheet(
    micInit: Boolean,
    locInit: Boolean,
    onConfirm: (Boolean, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var mic by remember { mutableStateOf(micInit) }
    var loc by remember { mutableStateOf(locInit) }
    Column(Modifier.padding(20.dp)) {
        Text(stringResource(R.string.perms_title), color = UltronText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.size(4.dp))
        Text(stringResource(R.string.perms_sub), color = UltronMuted, fontSize = 13.sp)
        Spacer(Modifier.size(14.dp))
        PermRow(stringResource(R.string.perm_mic_t), stringResource(R.string.perm_mic_d), mic) { mic = it }
        Spacer(Modifier.size(10.dp))
        PermRow(stringResource(R.string.perm_loc_t), stringResource(R.string.perm_loc_d), loc) { loc = it }
        Spacer(Modifier.size(16.dp))
        Row {
            TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.close), color = UltronText) }
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier.weight(1f).background(UltronRed, RoundedCornerShape(12.dp)).clickable { onConfirm(mic, loc) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.confirm), color = Color.White, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun PermRow(title: String, desc: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, UltronLine, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = UltronText, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(desc, color = UltronMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheck)
    }
}

@Composable
private fun SettingsSheet(
    apiBase: String, onApiBase: (String) -> Unit,
    ghToken: String, onGhToken: (String) -> Unit,
    onConnect: ((String) -> Unit) -> Unit,
    onSave: () -> Unit,
    onLang: () -> Unit,
) {
    var ghStatus by remember { mutableStateOf("") }
    Column(Modifier.padding(20.dp)) {
        Text(stringResource(R.string.settings), color = UltronText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.size(14.dp))

        Field(stringResource(R.string.set_backend), apiBase, onApiBase)
        Spacer(Modifier.size(12.dp))
        Field(stringResource(R.string.set_gh), ghToken, onGhToken, password = true)
        if (ghStatus.isNotEmpty()) Text(ghStatus, color = UltronMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.gh_hint), color = UltronMuted, fontSize = 11.sp)
        Spacer(Modifier.size(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLang) {
                Icon(Icons.Filled.Translate, contentDescription = null, tint = UltronText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.language), color = UltronText)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onConnect { ghStatus = it } }) { Text(stringResource(R.string.gh_connect), color = UltronText) }
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier.background(UltronRed, RoundedCornerShape(12.dp)).clickable { onSave() }.padding(horizontal = 20.dp, vertical = 12.dp),
            ) { Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit, password: Boolean = false) {
    Column {
        Text(label, color = UltronMuted, fontSize = 13.sp)
        Spacer(Modifier.size(6.dp))
        Box(
            Modifier.fillMaxWidth().background(Color(0xFF200607), RoundedCornerShape(12.dp)).border(1.dp, UltronLine, RoundedCornerShape(12.dp)).padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = TextStyle(color = UltronText, fontFamily = Tajawal, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(UltronRed),
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LangSheet(current: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Text(stringResource(R.string.language), color = UltronText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.size(12.dp))
        LangRow("العربية", "ar", current == "ar", onPick)
        LangRow("English", "en", current == "en", onPick)
        LangRow("Français", "fr", current == "fr", onPick)
        Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun LangRow(label: String, code: String, selected: Boolean, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onPick(code) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) UltronRed else UltronText, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        if (selected) Text("✓", color = UltronRed, fontSize = 16.sp)
    }
}

private fun wantsLocation(text: String): Boolean {
    val t = text.lowercase()
    return Regex("أين أنا|اين انا|موقعي|الموقع الحالي|where am i|my location|où suis-?je|ma position").containsMatchIn(t)
}

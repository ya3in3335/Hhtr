package com.ultron.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

private val BgBrush = Brush.verticalGradient(listOf(UltronBgTop, UltronBgMid, UltronBgBottom))
private val UserBrush = Brush.linearGradient(listOf(UltronUserTop, UltronUserBottom))
private val SendBrush = Brush.linearGradient(listOf(UltronRedSoft, UltronRed))

@Composable
fun App(vm: ChatViewModel) {
    UltronTheme {
        var showSplash by remember { mutableStateOf(true) }
        if (showSplash) SplashScreen { showSplash = false } else ChatScreen(vm)
    }
}

/** Slow ambient red glow drifting behind everything. */
@Composable
private fun AmbientGlow() {
    val t = rememberInfiniteTransition(label = "amb")
    val y by t.animateFloat(0.12f, 0.30f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    val a by t.animateFloat(0.18f, 0.40f, infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(UltronGlow.copy(alpha = a), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * y),
                radius = size.width * 0.95f,
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(UltronGlow.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.92f),
                radius = size.width * 0.8f,
            )
        )
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
        if (locOn && wantsLocation(t)) {
            host.lastLocation { lat, lon ->
                val note = if (lat != null && lon != null) "\n\n[$locLabel: $lat, $lon]" else ""
                fire(t + note)
            }
        } else fire(t)
    }
    fun toggleMic() {
        if (!micOn) { showPerms = true; return }
        if (listening) { host.stopListening(); listening = false; vm.updateStatus(Status.READY); return }
        listening = true
        vm.updateStatus(Status.LISTENING)
        host.startListening(prefs.lang, onText = { input = it }, onEnd = {
            listening = false; vm.updateStatus(Status.READY)
            val t = input; if (t.isNotBlank()) doSend(t)
        })
    }

    val statusText = when (vm.status) {
        Status.READY -> stringResource(R.string.status_ready)
        Status.THINKING -> stringResource(R.string.status_thinking)
        Status.LISTENING -> stringResource(R.string.status_listening)
    }

    Box(Modifier.fillMaxSize().background(BgBrush)) {
        AmbientGlow()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(statusText, vm.status, micOn || locOn,
                    onPerms = { showPerms = true },
                    onLang = { showLang = true },
                    onSettings = { apiBase = prefs.apiBase; ghToken = prefs.ghToken; showSettings = true },
                    onNew = { vm.clear() })
            },
            bottomBar = {
                Composer(input, { input = it }, { doSend(input) }, { toggleMic() }, listening)
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (vm.messages.isEmpty()) {
                    EmptyState(micOn, locOn)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(vm.messages.size) { i -> MessageItem(vm.messages[i]) }
                        if (vm.busy) item { TypingRow() }
                    }
                }
            }
        }
    }

    val sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    if (showPerms) {
        ModalBottomSheet(onDismissRequest = { showPerms = false }, containerColor = UltronInk,
            sheetState = rememberModalBottomSheetState(), shape = sheetShape) {
            PermsSheet(micOn, locOn, onConfirm = { wm, wl ->
                host.requestPermissions(wm, wl) { m, l -> micOn = m; locOn = l; showPerms = false }
            }, onClose = { showPerms = false })
        }
    }
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = UltronInk, shape = sheetShape) {
            SettingsSheet(apiBase, { apiBase = it }, ghToken, { ghToken = it },
                onConnect = { cb ->
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { GitHubApi.validate(ghToken.trim()) }
                        res.onSuccess { l -> prefs.ghToken = ghToken.trim(); cb("✓ $l") }.onFailure { cb("✗ ${it.message}") }
                    }
                },
                onSave = { prefs.apiBase = apiBase.trim(); prefs.ghToken = ghToken.trim(); showSettings = false },
                onLang = { showSettings = false; showLang = true })
        }
    }
    if (showLang) {
        ModalBottomSheet(onDismissRequest = { showLang = false }, containerColor = UltronInk, shape = sheetShape) {
            LangSheet(prefs.lang) { code -> showLang = false; host.setLocale(code) }
        }
    }
}

@Composable
private fun GlassTopBar(
    status: String, statusEnum: Status, permsActive: Boolean,
    onPerms: () -> Unit, onLang: () -> Unit, onSettings: () -> Unit, onNew: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC1C0608), Color(0x001C0608))))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(42.dp).background(Brush.radialGradient(listOf(UltronGlow.copy(alpha = .5f), Color.Transparent))))
            Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.size(11.dp))
        Column {
            Text("ULTRON", color = UltronText, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(statusEnum)
                Spacer(Modifier.size(5.dp))
                Text(status, color = UltronMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        GlassIcon(Icons.Filled.Security, if (permsActive) UltronRedSoft else UltronText, onPerms)
        GlassIcon(Icons.Filled.Translate, UltronText, onLang)
        GlassIcon(Icons.Filled.Settings, UltronText, onSettings)
        GlassIcon(Icons.Filled.Add, UltronText, onNew)
    }
}

@Composable
private fun StatusDot(status: Status) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    val color = if (status == Status.READY) Color(0xFF6BE08A) else UltronRedSoft
    Box(Modifier.size(7.dp).graphicsLayer { alpha = a }.background(color, CircleShape))
}

@Composable
private fun GlassIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(UltronGlass)
            .border(1.dp, UltronGlassBorder, RoundedCornerShape(13.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun EmptyState(micOn: Boolean, locOn: Boolean) {
    val t = rememberInfiniteTransition(label = "empty")
    val scale by t.animateFloat(0.95f, 1.08f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    val glow by t.animateFloat(0.3f, 0.6f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g")
    Column(
        modifier = Modifier.fillMaxSize().padding(34.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp)) {
                drawCircle(Brush.radialGradient(listOf(UltronGlow.copy(alpha = glow), Color.Transparent)), radius = size.minDimension / 2f)
            }
            Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(96.dp).graphicsLayer { scaleX = scale; scaleY = scale })
        }
        Spacer(Modifier.size(18.dp))
        Text(stringResource(R.string.empty_title), color = UltronText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.empty_desc), color = UltronMuted, fontSize = 13.sp)
        Spacer(Modifier.size(16.dp))
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
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) Color(0x1A6BE08A) else UltronGlass)
            .border(1.dp, if (on) Color(0x556BE08A) else UltronGlassBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = if (on) Color(0xFF8CF0A6) else UltronMuted, fontSize = 11.sp) }
}

@Composable
private fun MessageItem(msg: Msg) {
    if (msg.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .shadow(14.dp, RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp), spotColor = UltronRed, ambientColor = UltronRed)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(UserBrush)
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            ) { Text(msg.content, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(38.dp)) { drawCircle(Brush.radialGradient(listOf(UltronGlow.copy(alpha = .45f), Color.Transparent)), radius = size.minDimension / 2f) }
                Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                        .background(UltronGlass)
                        .border(1.dp, UltronGlassBorder, RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                        .padding(horizontal = 15.dp, vertical = 11.dp)
                ) {
                    SelectionContainer { Text(msg.content, color = UltronText, fontSize = 15.sp, lineHeight = 23.sp) }
                }
                if (!msg.reasoning.isNullOrBlank()) ReasoningBlock(msg.reasoning)
            }
        }
    }
}

@Composable
private fun ReasoningBlock(text: String) {
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "rot")
    Column(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, UltronLine, RoundedCornerShape(14.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧠 " + stringResource(R.string.think_show), color = UltronMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = UltronMuted, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rot })
        }
        AnimatedVisibility(open) {
            Text(text, color = UltronMuted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(13.dp))
        }
    }
}

@Composable
private fun TypingRow() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.size(10.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                .background(UltronGlass)
                .border(1.dp, UltronGlassBorder, RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 0..2) {
                val a by t.animateFloat(
                    0.3f, 1f,
                    infiniteRepeatable(tween(700, delayMillis = i * 160, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "d$i",
                )
                Box(Modifier.size(8.dp).graphicsLayer { alpha = a; scaleX = a; scaleY = a }.background(UltronRedSoft, CircleShape))
            }
        }
    }
}

@Composable
private fun Composer(value: String, onValue: (String) -> Unit, onSend: () -> Unit, onMic: () -> Unit, listening: Boolean) {
    val hasText = value.isNotBlank()
    val sendScale by animateFloatAsState(if (hasText) 1f else 0.9f, label = "send")
    val micT = rememberInfiniteTransition(label = "mic")
    val micPulse by micT.animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "mp")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x001C0608), Color(0xDD140305))))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // mic
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer { if (listening) { alpha = micPulse } }
                .clip(CircleShape)
                .background(if (listening) UltronRed else UltronGlass)
                .border(1.dp, if (listening) UltronRed else UltronGlassBorder, CircleShape)
                .clickable { onMic() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Mic, contentDescription = null, tint = if (listening) Color.White else UltronText, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.size(9.dp))
        // text field
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(UltronGlassStrong)
                .border(1.dp, UltronGlassBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(stringResource(R.string.compose_hint), color = UltronMuted, fontSize = 15.sp)
            BasicTextField(
                value = value, onValueChange = onValue,
                textStyle = TextStyle(color = UltronText, fontFamily = Tajawal, fontSize = 15.sp),
                cursorBrush = SolidColor(UltronRedSoft),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(9.dp))
        // send
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                .shadow(if (hasText) 16.dp else 0.dp, CircleShape, spotColor = UltronRed, ambientColor = UltronRed)
                .clip(CircleShape)
                .background(SendBrush)
                .clickable { onSend() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp)) }
    }
}

/* ---------- Bottom sheets ---------- */

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.padding(bottom = 10.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(999.dp)).background(UltronLine))
    }
}

@Composable
private fun PermsSheet(micInit: Boolean, locInit: Boolean, onConfirm: (Boolean, Boolean) -> Unit, onClose: () -> Unit) {
    var mic by remember { mutableStateOf(micInit) }
    var loc by remember { mutableStateOf(locInit) }
    Column(Modifier.padding(20.dp)) {
        SheetHandle()
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
            GradientButton(stringResource(R.string.confirm), Modifier.weight(1f)) { onConfirm(mic, loc) }
        }
        Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun PermRow(title: String, desc: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(UltronGlass)
            .border(1.dp, UltronGlassBorder, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = UltronText, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(desc, color = UltronMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheck,
            colors = SwitchDefaults.colors(checkedTrackColor = UltronRed, checkedThumbColor = Color.White))
    }
}

@Composable
private fun SettingsSheet(
    apiBase: String, onApiBase: (String) -> Unit, ghToken: String, onGhToken: (String) -> Unit,
    onConnect: ((String) -> Unit) -> Unit, onSave: () -> Unit, onLang: () -> Unit,
) {
    var ghStatus by remember { mutableStateOf("") }
    Column(Modifier.padding(20.dp)) {
        SheetHandle()
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
                Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.language), color = UltronText)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onConnect { ghStatus = it } }) { Text(stringResource(R.string.gh_connect), color = UltronText) }
            Spacer(Modifier.size(8.dp))
            GradientButton(stringResource(R.string.save)) { onSave() }
        }
        Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun GradientButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .shadow(10.dp, RoundedCornerShape(14.dp), spotColor = UltronRed)
            .clip(RoundedCornerShape(14.dp))
            .background(SendBrush)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit, password: Boolean = false) {
    Column {
        Text(label, color = UltronMuted, fontSize = 13.sp)
        Spacer(Modifier.size(6.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(UltronGlassStrong)
                .border(1.dp, UltronGlassBorder, RoundedCornerShape(12.dp)).padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = true,
                textStyle = TextStyle(color = UltronText, fontFamily = Tajawal, fontSize = 15.sp),
                cursorBrush = SolidColor(UltronRedSoft),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LangSheet(current: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(20.dp)) {
        SheetHandle()
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x1FE11D2A) else Color.Transparent)
            .clickable { onPick(code) }.padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) UltronRedSoft else UltronText, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        if (selected) Text("✓", color = UltronRedSoft, fontSize = 16.sp)
    }
}

private fun wantsLocation(text: String): Boolean {
    val t = text.lowercase()
    return Regex("أين أنا|اين انا|موقعي|الموقع الحالي|where am i|my location|où suis-?je|ma position").containsMatchIn(t)
}

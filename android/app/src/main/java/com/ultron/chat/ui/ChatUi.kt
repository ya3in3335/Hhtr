package com.ultron.chat.ui

import android.content.Intent
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.ultron.chat.Conversation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultron.chat.ChatViewModel
import com.ultron.chat.Msg
import com.ultron.chat.Prefs
import com.ultron.chat.R
import com.ultron.chat.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WORKER_URL = "https://ultron-proxy.yacincianai.workers.dev"

private val BgBrush = Brush.verticalGradient(listOf(UltronBgTop, UltronBgMid, UltronBgBottom))
private val UserBrush = Brush.linearGradient(listOf(UltronUserTop, UltronUserBottom))
private val SendBrush = Brush.linearGradient(listOf(UltronRedSoft, UltronRed))

// Glassmorphism: a frosted translucent panel — base tint + top-left sheen + lit edge.
private val GlassSheen = Brush.linearGradient(listOf(Color(0x26FFFFFF), Color(0x0AFFFFFF), Color(0x00FFFFFF)))
private val GlassEdge = Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x12FFFFFF), Color(0x22FFFFFF)))

/** Applies the reusable frosted-glass look to any surface. */
private fun Modifier.glass(
    shape: Shape,
    tint: Color = Color(0xFF17171C),
    alpha: Float = 0.58f,
    border: Boolean = true,
): Modifier = this
    .clip(shape)
    .background(tint.copy(alpha = alpha), shape)
    .background(GlassSheen, shape)
    .then(if (border) Modifier.border(1.dp, GlassEdge, shape) else Modifier)

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
    val y by t.animateFloat(0.06f, 0.20f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    val a by t.animateFloat(0.04f, 0.10f, infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
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
                colors = listOf(UltronGlow.copy(alpha = 0.05f), Color.Transparent),
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

    var micOn by remember { mutableStateOf(host.micGranted()) }
    var locOn by remember { mutableStateOf(host.locGranted()) }
    var listening by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    var showPerms by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    var showAvatar by remember { mutableStateOf(false) }
    var showGh by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var actionIndex by remember { mutableStateOf(-1) }        // message long-pressed
    var renameId by remember { mutableStateOf<String?>(null) } // chat being renamed
    var autoSpeak by remember { mutableStateOf(prefs.autoSpeak) }

    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val errText = stringResource(R.string.err_connect)
    val locLabel = stringResource(R.string.your_location)
    val haptic = LocalHapticFeedback.current

    val lastLen = vm.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(vm.messages.size, lastLen) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.size - 1, Int.MAX_VALUE)
    }
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= (vm.messages.size - 1)
        }
    }

    fun share(text: String) {
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { ctx.startActivity(Intent.createChooser(send, null)) }
    }

    fun speakOut(text: String) = host.speak(text, prefs.lang)
    fun shouldSpeak() = micOn || autoSpeak
    fun fire(text: String) = vm.send(WORKER_URL, text, errText) { reply -> if (shouldSpeak()) host.speak(reply, prefs.lang) }
    fun regenerate() = vm.regenerate(WORKER_URL, errText) { reply -> if (shouldSpeak()) host.speak(reply, prefs.lang) }
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
        Status.TOOL -> stringResource(R.string.status_tool)
        Status.LISTENING -> stringResource(R.string.status_listening)
    }

    // Drives the voice orb: speaking overrides everything, else follow chat status.
    val orbState = when {
        AvatarBus.speaking -> "speaking"
        vm.status == Status.THINKING || vm.status == Status.TOOL -> "thinking"
        vm.status == Status.LISTENING -> "listening"
        else -> "idle"
    }

    if (showAvatar) {
        AvatarScreen(vm) { showAvatar = false }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                vm,
                onPicked = { scope.launch { drawerState.close() } },
                onFace = { scope.launch { drawerState.close() }; showAvatar = true },
                onPerms = { scope.launch { drawerState.close() }; showPerms = true },
                onLang = { scope.launch { drawerState.close() }; showLang = true },
                onGitHub = { scope.launch { drawerState.close() }; showGh = true },
                onSettings = { scope.launch { drawerState.close() }; showSettings = true },
                onRename = { id -> renameId = id },
            )
        },
    ) {
    Box(Modifier.fillMaxSize().background(BgBrush)) {
        AmbientGlow()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(statusText, vm.status, micOn || locOn,
                    onMenu = { scope.launch { drawerState.open() } },
                    onFace = { showAvatar = true },
                    onPerms = { showPerms = true },
                    onLang = { showLang = true })
            },
            bottomBar = {
                Composer(input, { input = it }, { doSend(input) }, { toggleMic() }, { vm.clear() },
                    listening, vm.busy, onStop = { vm.stop() })
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (vm.messages.isEmpty()) {
                    EmptyState(orbState, micOn, locOn, onSuggest = { doSend(it) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(vm.messages.size) { i ->
                            val m = vm.messages[i]
                            val isLastAssistant = i == vm.messages.size - 1 && m.role == "assistant" && !vm.busy && m.content.isNotBlank()
                            MessageItem(
                                m,
                                isLastAssistant = isLastAssistant,
                                onRegenerate = { regenerate() },
                                onSpeak = { speakOut(m.content) },
                                onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); actionIndex = i },
                            )
                        }
                    }
                    // Jump-to-latest button when scrolled up.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !atBottom && vm.messages.size > 2,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) {
                        FloatingActionButton(
                            onClick = { scope.launch { listState.scrollToItem(vm.messages.size - 1, Int.MAX_VALUE) } },
                            containerColor = UltronGlassStrong, contentColor = UltronText,
                            modifier = Modifier.size(44.dp),
                        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) }
                    }
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
    if (showGh) {
        ModalBottomSheet(onDismissRequest = { showGh = false }, containerColor = UltronInk,
            sheetState = rememberModalBottomSheetState(), shape = sheetShape) {
            GhSheet(prefs.ghToken, onSave = { prefs.ghToken = it; showGh = false }, onClose = { showGh = false })
        }
    }
    if (showLang) {
        ModalBottomSheet(onDismissRequest = { showLang = false }, containerColor = UltronInk, shape = sheetShape) {
            LangSheet(prefs.lang) { code -> showLang = false; host.setLocale(code) }
        }
    }
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = UltronInk,
            sheetState = rememberModalBottomSheetState(), shape = sheetShape) {
            SettingsSheet(
                autoSpeak = autoSpeak,
                onAutoSpeak = { autoSpeak = it; prefs.autoSpeak = it },
                haptics = prefs.haptics,
                onHaptics = { prefs.haptics = it },
                onClearAll = { vm.clearAllChats(); showSettings = false },
                onClose = { showSettings = false },
            )
        }
    }
    if (actionIndex in vm.messages.indices) {
        val m = vm.messages[actionIndex]
        val canRegen = actionIndex == vm.messages.size - 1 && m.role == "assistant" && !vm.busy && m.content.isNotBlank()
        ModalBottomSheet(onDismissRequest = { actionIndex = -1 }, containerColor = UltronInk,
            sheetState = rememberModalBottomSheetState(), shape = sheetShape) {
            MessageActionsSheet(
                isAssistant = m.role == "assistant",
                canRegenerate = canRegen,
                onCopy = { clipCopy(ctx, m.content); actionIndex = -1 },
                onShare = { share(m.content); actionIndex = -1 },
                onSpeak = { speakOut(m.content); actionIndex = -1 },
                onRegenerate = { regenerate(); actionIndex = -1 },
                onDelete = { vm.deleteMessage(actionIndex); actionIndex = -1 },
                onClose = { actionIndex = -1 },
            )
        }
    }
    renameId?.let { id ->
        val conv = vm.conversations.firstOrNull { it.id == id }
        RenameDialog(
            initial = conv?.title.orEmpty(),
            onConfirm = { vm.renameChat(id, it); renameId = null },
            onDismiss = { renameId = null },
        )
    }
}

/** Copies text to the clipboard without a Compose clipboard handle. */
private fun clipCopy(ctx: android.content.Context, text: String) {
    runCatching {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("ultron", text))
    }
}

@Composable
private fun GlassTopBar(
    status: String, statusEnum: Status, permsActive: Boolean,
    onMenu: () -> Unit, onFace: () -> Unit, onPerms: () -> Unit, onLang: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xE60F0F13), Color(0xB30F0F13))))
            .background(GlassSheen)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIcon(Icons.Filled.Menu, UltronText, onMenu)
        Spacer(Modifier.size(10.dp))
        Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(10.dp))
        Column {
            Text("Ultron", color = UltronText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(statusEnum)
                Spacer(Modifier.size(5.dp))
                Text(status, color = UltronMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        GlassIcon(Icons.Filled.Face, UltronRedSoft, onFace)
        GlassIcon(Icons.Filled.Security, if (permsActive) UltronRedSoft else UltronMuted, onPerms)
        GlassIcon(Icons.Filled.Translate, UltronMuted, onLang)
    }
        Box(Modifier.fillMaxWidth().height(1.dp).background(UltronGlassBorder))
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
            .glass(RoundedCornerShape(13.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun EmptyState(orbState: String, micOn: Boolean, locOn: Boolean, onSuggest: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        VoiceOrb(orbState, size = 172.dp)
        Spacer(Modifier.size(22.dp))
        Text(stringResource(R.string.empty_title), color = UltronText, fontWeight = FontWeight.Bold, fontSize = 27.sp)
        Spacer(Modifier.size(10.dp))
        Text(
            stringResource(R.string.empty_desc), color = UltronMuted, fontSize = 14.sp, lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(20.dp))
        val s1 = stringResource(R.string.suggest_code)
        val s2 = stringResource(R.string.suggest_explain)
        val s3 = stringResource(R.string.suggest_tool)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestChip(s1) { onSuggest(s1) }
            SuggestChip(s3) { onSuggest(s3) }
        }
        Spacer(Modifier.size(8.dp))
        SuggestChip(s2) { onSuggest(s2) }
        Spacer(Modifier.size(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (micOn) stringResource(R.string.mic_on) else stringResource(R.string.mic_off), micOn)
            Pill(if (locOn) stringResource(R.string.loc_on) else stringResource(R.string.loc_off), locOn)
        }
    }
}

@Composable
private fun SuggestChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .glass(RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, color = UltronText, fontSize = 13.sp) }
}

/**
 * Full-screen hands-free "face mode": nothing but the 3D Ultron head and an
 * exit button. It listens, sends what it hears to the model, speaks the reply
 * in the monster voice, and the avatar reacts (listening → thinking → speaking)
 * — then loops. Tap the head to (re)start a turn.
 */
@Composable
fun AvatarScreen(vm: ChatViewModel, onExit: () -> Unit) {
    val host = LocalHost.current
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val errText = stringResource(R.string.err_connect)

    var active by remember { mutableStateOf(true) }
    var micOk by remember { mutableStateOf(host.micGranted()) }
    var heard by remember { mutableStateOf("") }

    val avatarState = when {
        AvatarBus.speaking -> "speaking"
        vm.status == Status.THINKING || vm.status == Status.TOOL -> "thinking"
        vm.status == Status.LISTENING -> "listening"
        else -> "idle"
    }

    fun beginTurn() {
        if (!active || vm.busy || AvatarBus.speaking || !host.micGranted()) return
        heard = ""
        vm.updateStatus(Status.LISTENING)
        host.startListening(
            prefs.lang,
            onText = { heard = it },
            onEnd = {
                vm.updateStatus(Status.READY)
                val t = heard.trim()
                if (t.isNotEmpty()) {
                    vm.send(WORKER_URL, t, errText) { reply -> if (active) host.speak(reply, prefs.lang) }
                }
            },
        )
    }

    // Kick off the loop on entry, requesting the mic if it isn't granted yet.
    LaunchedEffect(Unit) {
        if (host.micGranted()) { micOk = true; beginTurn() }
        else host.requestPermissions(true, false) { m, _ -> micOk = m; if (m) beginTurn() }
    }

    // When a spoken reply finishes, listen again — a continuous conversation.
    // The READY guard keeps this from racing the initial start or a reply that
    // is just about to be spoken.
    LaunchedEffect(AvatarBus.speaking, vm.busy) {
        if (active && micOk && !AvatarBus.speaking && !vm.busy && vm.status == Status.READY) {
            delay(600)
            if (active && !AvatarBus.speaking && !vm.busy && vm.status == Status.READY) beginTurn()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            active = false
            host.stopListening()
            host.stopSpeaking()
            vm.updateStatus(Status.READY)
        }
    }

    val hint = when {
        !micOk -> stringResource(R.string.avatar_need_mic)
        AvatarBus.speaking -> "•••"
        vm.status == Status.THINKING -> stringResource(R.string.status_thinking)
        vm.status == Status.LISTENING -> stringResource(R.string.status_listening)
        else -> stringResource(R.string.avatar_idle)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgBrush)
            .clickable { beginTurn() },
    ) {
        VoiceOrb(state = avatarState, size = 300.dp, modifier = Modifier.align(Alignment.Center))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(46.dp)
                .glass(CircleShape)
                .clickable { onExit() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.avatar_exit), tint = UltronText)
        }
        Text(
            hint,
            color = UltronMuted,
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 54.dp),
        )
    }
}

@Composable
private fun Pill(text: String, on: Boolean) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .then(
                if (on) Modifier.clip(shape).background(Color(0x1A6BE08A)).border(1.dp, Color(0x556BE08A), shape)
                else Modifier.glass(shape)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = if (on) Color(0xFF8CF0A6) else UltronMuted, fontSize = 11.sp) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    msg: Msg,
    isLastAssistant: Boolean = false,
    onRegenerate: () -> Unit = {},
    onSpeak: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    if (msg.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp, 20.dp, 7.dp, 20.dp), spotColor = UltronRed.copy(alpha = .5f), ambientColor = Color.Transparent)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 7.dp, 20.dp))
                    .background(UserBrush)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            ) { Text(msg.content, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(UltronSurface2).border(1.dp, UltronGlassBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.size(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 2.dp)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress),
            ) {
                if (!msg.tools.isNullOrEmpty()) ToolsBlock(msg.tools)
                if (msg.content.isBlank() && msg.tools.isNullOrEmpty()) TypingDots()
                else if (msg.content.isNotBlank()) SelectionContainer { MarkdownText(msg.content, UltronText) }
                if (!msg.reasoning.isNullOrBlank()) ReasoningBlock(msg.reasoning)
                if (msg.content.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CopyRow(msg.content)
                        if (isLastAssistant) {
                            Spacer(Modifier.size(4.dp))
                            MsgAction(Icons.Filled.Refresh, stringResource(R.string.regenerate), onRegenerate)
                            MsgAction(Icons.Filled.VolumeUp, stringResource(R.string.speak), onSpeak)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MsgAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = UltronMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(5.dp))
        Text(label, color = UltronMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ReasoningBlock(text: String) {
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "rot")
    Column(
        Modifier
            .padding(top = 8.dp)
            .glass(RoundedCornerShape(14.dp), alpha = 0.40f)
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
private fun ToolsBlock(tools: List<com.ultron.chat.ToolStep>) {
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "trot")
    Column(
        Modifier
            .padding(bottom = 8.dp)
            .glass(RoundedCornerShape(14.dp), alpha = 0.42f),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔧 " + stringResource(R.string.tools_used, tools.size), color = UltronRedSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = UltronMuted, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rot })
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(horizontal = 13.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tools.forEachIndexed { i, t ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("#${i + 1} · ${t.tool}", color = UltronMuted, fontSize = 11.sp, letterSpacing = 1.sp)
                        Text(
                            t.code, color = UltronText, fontSize = 12.sp, lineHeight = 17.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x14000000)).padding(10.dp),
                        )
                        Text("→ ${t.result}", color = Color(0xFF8CF0A6), fontSize = 12.sp, lineHeight = 17.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 2.dp),
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

@Composable
private fun Composer(
    value: String, onValue: (String) -> Unit, onSend: () -> Unit, onMic: () -> Unit, onNew: () -> Unit,
    listening: Boolean, busy: Boolean, onStop: () -> Unit,
) {
    val hasText = value.isNotBlank()
    val micT = rememberInfiniteTransition(label = "mic")
    val micPulse by micT.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "mp")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(26.dp), spotColor = Color.Black, ambientColor = Color.Black)
                .glass(RoundedCornerShape(26.dp), tint = Color(0xFF151519), alpha = 0.66f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // text row
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (value.isEmpty()) Text(stringResource(R.string.compose_hint), color = UltronMuted, fontSize = 16.sp)
                BasicTextField(
                    value = value, onValueChange = onValue,
                    textStyle = TextStyle(color = UltronText, fontFamily = Tajawal, fontSize = 16.sp),
                    cursorBrush = SolidColor(UltronRedSoft),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.size(10.dp))
            // controls row
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundControl(Icons.Filled.Add, UltronText, onNew)
                Spacer(Modifier.size(8.dp))
                Box(
                    Modifier
                        .size(38.dp)
                        .graphicsLayer { if (listening) alpha = micPulse }
                        .then(
                            if (listening) Modifier.clip(CircleShape).background(UltronRed).border(1.dp, UltronRed, CircleShape)
                            else Modifier.glass(CircleShape)
                        )
                        .clickable { onMic() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Mic, contentDescription = null, tint = if (listening) Color.White else UltronText, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.weight(1f))
                // send / stop button
                Box(
                    Modifier
                        .size(42.dp)
                        .shadow(if (hasText || busy) 14.dp else 0.dp, CircleShape, spotColor = UltronRed, ambientColor = UltronRed)
                        .clip(CircleShape)
                        .background(if (hasText || busy) SendBrush else Brush.linearGradient(listOf(Color(0xFF3A3238), Color(0xFF2B262B))))
                        .clickable(enabled = hasText || busy) { if (busy) onStop() else onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (busy) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = if (hasText || busy) Color.White else UltronMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).glass(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp)) }
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
        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp),
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

@Composable
private fun CopyRow(text: String) {
    val clip = LocalClipboardManager.current
    val copyStr = stringResource(R.string.copy)
    val copiedStr = stringResource(R.string.copied)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1500); copied = false } }
    Row(
        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
            .clickable { clip.setText(AnnotatedString(text)); copied = true }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = if (copied) Color(0xFF8CF0A6) else UltronMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(5.dp))
        Text(if (copied) copiedStr else copyStr, color = if (copied) Color(0xFF8CF0A6) else UltronMuted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerContent(
    vm: ChatViewModel,
    onPicked: () -> Unit,
    onFace: () -> Unit,
    onPerms: () -> Unit,
    onLang: () -> Unit,
    onGitHub: () -> Unit,
    onSettings: () -> Unit,
    onRename: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalDrawerSheet(drawerContainerColor = Color.Transparent, drawerTonalElevation = 0.dp) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xF0151519), Color(0xF00B0B0F))))
                .background(GlassSheen),
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ultron), contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("ULTRON", color = UltronText, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp)
                        Text(stringResource(R.string.drawer_tagline), color = UltronMuted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.size(16.dp))
                GradientButton(stringResource(R.string.new_chat), Modifier.fillMaxWidth()) { vm.newChat(); onPicked() }
                Spacer(Modifier.size(14.dp))
                // Search chats
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = UltronMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(stringResource(R.string.search_chats), color = UltronMuted, fontSize = 14.sp)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = UltronText, fontFamily = Tajawal, fontSize = 14.sp),
                            cursorBrush = SolidColor(UltronRedSoft),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Text(stringResource(R.string.chats), color = UltronMuted, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.size(8.dp))
                val shown = vm.conversations.filter {
                    query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)
                }
                if (shown.isEmpty() || shown.all { it.title.isBlank() && it.messages.isEmpty() }) {
                    Text(stringResource(R.string.empty_chats), color = UltronMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown.size) { i ->
                        val c = shown[i]
                        ChatRow(c, c.id == vm.currentId,
                            onClick = { vm.selectChat(c.id); onPicked() },
                            onRename = { onRename(c.id) },
                            onDelete = { vm.deleteChat(c.id) })
                    }
                }
                Spacer(Modifier.size(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(UltronGlassBorder))
                Spacer(Modifier.size(8.dp))
                DrawerActionRow(Icons.Filled.Face, stringResource(R.string.avatar_mode), onFace)
                DrawerActionRow(Icons.Filled.Security, stringResource(R.string.permissions), onPerms)
                DrawerActionRow(Icons.Filled.Translate, stringResource(R.string.language), onLang)
                DrawerActionRow(Icons.Filled.Link, stringResource(R.string.gh_connect), onGitHub)
                DrawerActionRow(Icons.Filled.Settings, stringResource(R.string.settings), onSettings)
            }
        }
    }
}

@Composable
private fun DrawerActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).glass(RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = UltronText, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(label, color = UltronText, fontSize = 14.sp)
    }
}

@Composable
private fun GhSheet(initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var token by remember { mutableStateOf(initial) }
    Column(Modifier.padding(20.dp)) {
        SheetHandle()
        Text(stringResource(R.string.gh_connect), color = UltronText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.gh_hint), color = UltronMuted, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.size(14.dp))
        Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 14.dp)) {
            if (token.isEmpty()) Text(stringResource(R.string.set_gh), color = UltronMuted, fontSize = 14.sp)
            BasicTextField(
                value = token, onValueChange = { token = it }, singleLine = true,
                textStyle = TextStyle(color = UltronText, fontFamily = Tajawal, fontSize = 14.sp),
                cursorBrush = SolidColor(UltronRedSoft),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(16.dp))
        Row {
            TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.close), color = UltronText) }
            Spacer(Modifier.size(10.dp))
            GradientButton(stringResource(R.string.save), Modifier.weight(1f)) { onSave(token.trim()) }
        }
        Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun ChatRow(c: Conversation, selected: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0x1FE11D2A) else Color.Transparent)
            .clickable { onClick() }.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            c.title.ifBlank { stringResource(R.string.new_chat) },
            color = if (selected) UltronRedSoft else UltronText,
            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onRename() }.padding(6.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename), tint = UltronMuted, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDelete() }.padding(6.dp)) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = UltronMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsSheet(
    autoSpeak: Boolean, onAutoSpeak: (Boolean) -> Unit,
    haptics: Boolean, onHaptics: (Boolean) -> Unit,
    onClearAll: () -> Unit, onClose: () -> Unit,
) {
    Column(Modifier.padding(20.dp)) {
        SheetHandle()
        Text(stringResource(R.string.settings), color = UltronText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.size(14.dp))
        ToggleRow(stringResource(R.string.auto_speak_t), stringResource(R.string.auto_speak_d), autoSpeak, onAutoSpeak)
        Spacer(Modifier.size(10.dp))
        ToggleRow(stringResource(R.string.haptics_t), stringResource(R.string.haptics_d), haptics, onHaptics)
        Spacer(Modifier.size(16.dp))
        SheetActionRow(Icons.Filled.DeleteOutline, stringResource(R.string.clear_all), destructive = true, onClick = onClearAll)
        Spacer(Modifier.size(8.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close), color = UltronText) }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp),
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
private fun MessageActionsSheet(
    isAssistant: Boolean, canRegenerate: Boolean,
    onCopy: () -> Unit, onShare: () -> Unit, onSpeak: () -> Unit,
    onRegenerate: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        SheetHandle()
        Text(stringResource(R.string.msg_actions), color = UltronMuted, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.size(8.dp))
        SheetActionRow(Icons.Filled.ContentCopy, stringResource(R.string.copy), onClick = onCopy)
        SheetActionRow(Icons.Filled.Share, stringResource(R.string.share), onClick = onShare)
        if (isAssistant) SheetActionRow(Icons.Filled.VolumeUp, stringResource(R.string.speak), onClick = onSpeak)
        if (canRegenerate) SheetActionRow(Icons.Filled.Refresh, stringResource(R.string.regenerate), onClick = onRegenerate)
        SheetActionRow(Icons.Filled.DeleteOutline, stringResource(R.string.delete), destructive = true, onClick = onDelete)
        Spacer(Modifier.size(6.dp))
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    destructive: Boolean = false, onClick: () -> Unit,
) {
    val tint = if (destructive) UltronRedSoft else UltronText
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).glass(RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UltronInk,
        title = { Text(stringResource(R.string.rename), color = UltronText, fontWeight = FontWeight.Bold) },
        text = {
            Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (text.isEmpty()) Text(stringResource(R.string.new_chat), color = UltronMuted, fontSize = 14.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = TextStyle(color = UltronText, fontFamily = Tajawal, fontSize = 15.sp),
                    cursorBrush = SolidColor(UltronRedSoft),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save), color = UltronRedSoft) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = UltronMuted) } },
    )
}

private fun wantsLocation(text: String): Boolean {
    val t = text.lowercase()
    return Regex("أين أنا|اين انا|موقعي|الموقع الحالي|where am i|my location|où suis-?je|ma position").containsMatchIn(t)
}

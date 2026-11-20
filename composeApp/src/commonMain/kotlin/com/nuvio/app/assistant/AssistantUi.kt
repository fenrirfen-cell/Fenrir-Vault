package com.nuvio.app.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.PlatformBackHandler
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import com.nuvio.app.features.home.MetaPreview

private val PanelColor = Color(0xFF141414)
private val CardColor = Color(0xFF1F1F1F)
private val FieldColor = Color(0xFF242424)
private val UserBubbleColor = Color(0xFF2A2A2A)
private val AccentBg = Color(0xFF2A2140)
private val AccentFg = Color(0xFFE6E0FF)
private val MutedText = Color(0xFFAAAAAA)
private val BodyText = Color(0xFFDDDDDD)

/** The sparkle button next to the Watch / Read switch. Tap: open. Long-press: open and talk. */
@Composable
fun AssistantLauncherButton(modifier: Modifier = Modifier, hazeState: HazeState? = null) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(if (hazeState != null) Modifier.hazeEffect(state = hazeState) { blurRadius = 24.dp } else Modifier)
            .background(if (hazeState != null) Color(0xFF1C1C1E).copy(alpha = 0.55f) else AccentBg)
            .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .semantics {
                contentDescription = "Fenrir assistant"
                role = Role.Button
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { AssistantController.show() },
                    onLongPress = {
                        AssistantController.show()
                        AssistantController.startVoice()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = AccentFg, modifier = Modifier.size(20.dp))
    }
}

/** The assistant panel. Draws nothing while closed. */
@Composable
fun AssistantSheetHost() {
    val open by AssistantController.open.collectAsState()
    if (!open) return
    var showPrefs by remember { mutableStateOf(false) }
    PlatformBackHandler(enabled = true, onBack = {
        if (showPrefs) showPrefs = false else AssistantController.hide()
    })
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures(onTap = { AssistantController.hide() }) },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(PanelColor)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .heightIn(max = 560.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = AccentFg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (showPrefs) "Assistant preferences" else "Fenrir",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (showPrefs) {
                    PillButton(label = "Done", filled = true) { showPrefs = false }
                } else {
                    RoundIconButton(Icons.Rounded.Settings, "Preferences", background = Color.Transparent) { showPrefs = true }
                }
                Spacer(Modifier.width(4.dp))
                RoundIconButton(Icons.Rounded.Close, "Close", background = Color.Transparent) { AssistantController.hide() }
            }
            Spacer(Modifier.height(10.dp))
            if (showPrefs) PreferencesPanel() else ChatPanel()
        }
    }
}

@Composable
private fun ColumnScope.ChatPanel() {
    val messages by AssistantController.messages.collectAsState()
    val busy by AssistantController.busy.collectAsState()
    val countdown by AssistantController.countdown.collectAsState()
    val choices by AssistantController.choices.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy, countdown != null, choices.size) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (messages.isEmpty()) {
            item {
                Text(
                    text = "Try \"play the current episode of The Rookie in English\", \"next episode of Frieren dub\" or \"play Dune Part Two in 4K\".",
                    color = MutedText,
                    fontSize = 13.sp,
                )
            }
        }
        items(messages) { message -> MessageBubble(message) }
        if (busy) {
            item { Text("Looking\u2026", color = MutedText, fontSize = 13.sp) }
        }
        if (choices.isNotEmpty()) {
            items(choices) { item -> ChoiceRow(item) }
        }
        countdown?.let { current -> item { CountdownCard(current) } }
    }
    Spacer(Modifier.height(10.dp))
    InputRow()
}

@Composable
private fun MessageBubble(message: AssistantController.Message) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Text(
            text = message.text,
            color = if (message.fromUser) Color.White else BodyText,
            fontSize = 14.sp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .then(
                    if (message.fromUser) {
                        Modifier.clip(RoundedCornerShape(14.dp)).background(UserBubbleColor).padding(horizontal = 12.dp, vertical = 8.dp)
                    } else {
                        Modifier.padding(vertical = 2.dp)
                    },
                ),
        )
    }
}

@Composable
private fun ChoiceRow(item: MetaPreview) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardColor)
            .clickable { AssistantController.pick(item) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val sub = listOfNotNull(
                when (item.type.lowercase()) {
                    "movie" -> "Movie"
                    "series" -> "Series"
                    "anime" -> "Anime"
                    else -> item.type
                },
                item.releaseInfo?.takeIf { it.isNotBlank() },
            ).joinToString(" \u00b7 ")
            Text(sub, color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CountdownCard(countdown: AssistantController.Countdown) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardColor)
            .padding(12.dp),
    ) {
        Text(countdown.heading, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (countdown.detail.isNotBlank()) Text(countdown.detail, color = MutedText, fontSize = 12.sp)
        Text("Looking for: ${countdown.lookingFor}", color = MutedText, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Playing in ${countdown.secondsLeft}\u2026", color = AccentFg, fontSize = 13.sp, modifier = Modifier.weight(1f))
            PillButton(label = "Cancel", filled = false) { AssistantController.cancelCountdown() }
            Spacer(Modifier.width(8.dp))
            PillButton(label = "Play now", filled = true) { AssistantController.playNow() }
        }
    }
}

@Composable
private fun InputRow() {
    var text by remember { mutableStateOf("") }
    fun send() {
        val value = text
        text = ""
        AssistantController.submit(value)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask Fenrir\u2026") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = FieldColor,
                unfocusedContainerColor = FieldColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AccentFg,
                focusedPlaceholderColor = Color(0xFF777777),
                unfocusedPlaceholderColor = Color(0xFF777777),
            ),
        )
        Spacer(Modifier.width(8.dp))
        if (text.isBlank() && AssistantController.voiceAvailable) {
            RoundIconButton(Icons.Rounded.Mic, "Speak", background = Color.White, tint = Color.Black) {
                AssistantController.startVoice()
            }
        } else {
            RoundIconButton(Icons.AutoMirrored.Rounded.Send, "Send", background = Color.White, tint = Color.Black) { send() }
        }
    }
}

@Composable
private fun ColumnScope.PreferencesPanel() {
    val prefs by AssistantSettings.prefs.collectAsState()
    Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OptionRow(
            label = "Language",
            options = listOf("en", "any", "es", "fr", "de", "it", "pt", "ja", "ko", "hi")
                .map { it to AssistantStreamRanker.languageLabel(it).replace(" language", "") },
            selected = prefs.language,
        ) { value -> AssistantSettings.update { it.copy(language = value) } }
        OptionRow(
            label = "Anime audio",
            options = listOf(AssistantAudio.DUB to "Dub", AssistantAudio.SUB to "Sub", AssistantAudio.EITHER to "Either"),
            selected = prefs.animeAudio,
        ) { value -> AssistantSettings.update { it.copy(animeAudio = value) } }
        OptionRow(
            label = "Quality first",
            options = listOf("1080p" to "1080p", "2160p" to "4K", "720p" to "720p"),
            selected = prefs.qualityOrder.firstOrNull() ?: "1080p",
        ) { value ->
            AssistantSettings.update { it.copy(qualityOrder = listOf(value) + DEFAULT_QUALITY_ORDER.filter { q -> q != value }) }
        }
        ToggleRow("Only cached links", prefs.cachedOnly) { value -> AssistantSettings.update { it.copy(cachedOnly = value) } }
        OptionRow(
            label = "Max size",
            options = listOf<Double?>(null, 2.0, 4.0, 8.0, 15.0).map { it to (it?.let { gb -> "${gb.toInt()} GB" } ?: "No limit") },
            selected = prefs.maxSizeGb,
        ) { value -> AssistantSettings.update { it.copy(maxSizeGb = value) } }
        ToggleRow("Skip CAM / TS copies", prefs.skipLowQuality) { value -> AssistantSettings.update { it.copy(skipLowQuality = value) } }
        ToggleRow("Avoid HDR", prefs.avoidHdr) { value -> AssistantSettings.update { it.copy(avoidHdr = value) } }
        OptionRow(
            label = "Countdown before playing",
            options = listOf(0 to "Off", 3 to "3 sec", 5 to "5 sec"),
            selected = prefs.countdownSeconds,
        ) { value -> AssistantSettings.update { it.copy(countdownSeconds = value) } }
        Text(
            text = "These are your usual choices. Anything you say in a request, like \"in 4K\" or \"English dub\", overrides them for that request. If no stream matches, you get the normal stream list to pick yourself.",
            color = MutedText,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun <T> OptionRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, color = MutedText, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (value, text) ->
                PillButton(label = text, filled = value == selected) { onSelect(value) }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = BodyText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF2A2A2A),
            ),
        )
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(Color.White) else Modifier.border(0.5.dp, Color(0xFF555555), CircleShape),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (filled) Color.Black else BodyText, fontSize = 13.sp)
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color = BodyText,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(background)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

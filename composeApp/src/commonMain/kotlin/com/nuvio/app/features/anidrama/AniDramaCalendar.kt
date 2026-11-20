package com.nuvio.app.features.anidrama

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationPlatform
import kotlinx.coroutines.launch

private val PremiereColor = Color(0xFFD85A30)

/**
 * Month calendar (with a Week view) of episodes and premieres. Tap a day for its list; tap a
 * show's bell to set a reminder (optionally spoken out loud).
 */
@Composable
internal fun AniDramaCalendarView(
    key: String,
    loader: suspend (startIso: String, days: Int) -> List<AniDramaScheduleEntry>,
    filter: (AniDramaScheduleEntry) -> Boolean,
    showDubBadge: Boolean,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
) {
    val todayIso = remember { AniDramaTime.todayIso() }
    var year by rememberSaveable(key) { mutableIntStateOf(todayIso.take(4).toIntOrNull() ?: 2026) }
    var month by rememberSaveable(key) { mutableIntStateOf(todayIso.substring(5, 7).toIntOrNull() ?: 1) }
    var selectedIso by rememberSaveable(key) { mutableStateOf(todayIso) }
    var weekView by rememberSaveable(key) { mutableStateOf(false) }
    var myShowsOnly by rememberSaveable(key) { mutableStateOf(false) }
    var reminderFor by remember(key) { mutableStateOf<AniDramaScheduleEntry?>(null) }

    val library by LibraryRepository.uiState.collectAsStateWithLifecycle()
    val libraryIds = remember(library.items) { library.items.mapTo(HashSet()) { it.id } }
    val reminders by AniDramaReminders.reminders.collectAsStateWithLifecycle()

    val monthStart = AniDramaTime.firstOfMonth(year, month)
    val monthDays = AniDramaTime.daysInMonth(year, month)
    val weekStart = remember(selectedIso) {
        val day = AniDramaTime.epochDay(selectedIso) ?: 0L
        AniDramaTime.isoFromEpochDay(day - AniDramaTime.weekdayOfEpochDay(day))
    }
    val rangeStart = if (weekView) weekStart else monthStart
    val rangeDays = if (weekView) 7 else monthDays
    val (state, retry) = rememberAniDramaLoad("$key|$rangeStart|$rangeDays") { loader(rangeStart, rangeDays) }

    val entries = remember(state.value, myShowsOnly, libraryIds, filter) {
        state.value.orEmpty()
            .filter(filter)
            .filter { !myShowsOnly || it.item.matchIds.any { id -> id in libraryIds } }
            .distinctBy { it.item.key + "#" + it.episode + "#" + it.dateIso }
    }
    val byDay = remember(entries) { entries.groupBy { it.dateIso } }
    val dayEntries = byDay[selectedIso].orEmpty().sortedBy { it.sortKey }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = nuvioSafeBottomPadding(24.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!weekView) {
                        RoundIcon(Icons.Rounded.ChevronLeft, "Previous month") {
                            if (month == 1) { month = 12; year-- } else month--
                            selectedIso = AniDramaTime.firstOfMonth(year, month)
                        }
                        Text(
                            text = "${AniDramaTime.monthName(month)} $year",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        RoundIcon(Icons.Rounded.ChevronRight, "Next month") {
                            if (month == 12) { month = 1; year++ } else month++
                            selectedIso = AniDramaTime.firstOfMonth(year, month)
                        }
                    } else {
                        Text(
                            text = "Week of ${AniDramaTime.longDayLabel(weekStart).substringAfter(", ")}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    AniDramaSegmented(
                        options = listOf("All", "My shows"),
                        selectedIndex = if (myShowsOnly) 1 else 0,
                        onSelect = { myShowsOnly = it == 1 },
                        compact = true,
                    )
                }
            }
            item(key = "grid") {
                if (weekView) {
                    WeekStrip(weekStart, selectedIso, todayIso, byDay) { selectedIso = it }
                } else {
                    MonthGrid(monthStart, monthDays, selectedIso, todayIso, byDay) { selectedIso = it }
                }
            }
            item(key = "legend") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" New episodes   ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Dot(PremiereColor)
                    Text(" Premiere", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (weekView) "Month view" else "Week view",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(CircleShape).clickable { weekView = !weekView }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            item(key = "day-title") {
                Text(
                    text = AniDramaTime.longDayLabel(selectedIso),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            when {
                state.loading -> item(key = "loading") { AniDramaLoading(Modifier.fillMaxWidth().height(140.dp)) }
                state.failed -> item(key = "failed") {
                    AniDramaMessage("Couldn't load the calendar.", Modifier.padding(horizontal = 16.dp), onRetry = retry)
                }
                dayEntries.isEmpty() -> item(key = "empty") {
                    AniDramaMessage(
                        if (myShowsOnly) "None of your shows air on this day." else "Nothing airs on this day.",
                        Modifier.padding(horizontal = 16.dp),
                    )
                }
                else -> items(dayEntries, key = { it.item.key + "#" + it.episode + "#" + it.dateIso }) { entry ->
                    CalendarRow(
                        entry = entry,
                        showDubBadge = showDubBadge,
                        reminderSet = reminders.containsKey(entry.item.key),
                        onClick = { onItemClick(entry.item) },
                        onLongClick = { onItemLongClick(entry.item) },
                        onBell = { reminderFor = entry },
                    )
                }
            }
        }

        reminderFor?.let { entry ->
            ReminderSheet(
                entry = entry,
                upcoming = entries.filter { it.item.key == entry.item.key },
                existing = reminders[entry.item.key],
                onDismiss = { reminderFor = null },
            )
        }
    }
}

@Composable
private fun MonthGrid(
    monthStart: String,
    days: Int,
    selectedIso: String,
    todayIso: String,
    byDay: Map<String, List<AniDramaScheduleEntry>>,
    onSelect: (String) -> Unit,
) {
    val leading = AniDramaTime.weekdayOfIso(monthStart) ?: 0
    val cells = leading + days
    val rows = (cells + 6) / 7
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val index = row * 7 + column - leading
                    if (index in 0 until days) {
                        val iso = AniDramaTime.addDays(monthStart, index)
                        DayCell(
                            label = (index + 1).toString(),
                            iso = iso,
                            selected = iso == selectedIso,
                            today = iso == todayIso,
                            entries = byDay[iso].orEmpty(),
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).height(42.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    weekStart: String,
    selectedIso: String,
    todayIso: String,
    byDay: Map<String, List<AniDramaScheduleEntry>>,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            AniDramaTime.weekdayShort.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { offset ->
                val iso = AniDramaTime.addDays(weekStart, offset)
                DayCell(
                    label = iso.substring(8, 10).trimStart('0'),
                    iso = iso,
                    selected = iso == selectedIso,
                    today = iso == todayIso,
                    entries = byDay[iso].orEmpty(),
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    label: String,
    iso: String,
    selected: Boolean,
    today: Boolean,
    entries: List<AniDramaScheduleEntry>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val episodes = entries.count { !it.isPremiere }
    val premieres = entries.count { it.isPremiere }
    Column(
        modifier = modifier
            .height(42.dp)
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(colors.primary) else Modifier)
            .then(if (today && !selected) Modifier.border(1.dp, colors.primary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable { onSelect(iso) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp).padding(top = 2.dp)) {
            repeat(episodes.coerceAtMost(3)) { Dot(if (selected) colors.onPrimary else colors.onSurfaceVariant) }
            if (premieres > 0) Dot(PremiereColor)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(4.dp).clip(CircleShape).background(color))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarRow(
    entry: AniDramaScheduleEntry,
    showDubBadge: Boolean,
    reminderSet: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onBell: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            entry.item.poster?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.episodeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.isPremiere) PremiereColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDubBadge && entry.item.hasEnglishDub) {
            Text(
                text = "DUB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (AniDramaReminders.isAvailable) {
            Icon(
                imageVector = if (reminderSet) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone,
                contentDescription = if (reminderSet) "Reminder on" else "Remind me",
                tint = if (reminderSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onBell).padding(8.dp).size(22.dp),
            )
        }
    }
}

/** "Remind me about new episodes" sheet: notification only, or notification + spoken alert. */
@Composable
private fun ReminderSheet(
    entry: AniDramaScheduleEntry,
    upcoming: List<AniDramaScheduleEntry>,
    existing: AniDramaReminder?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val now = remember { AniDramaTime.nowEpochMs() }
    val next = upcoming
        .filter { (it.airEpochMs ?: 0L) >= now }
        .minByOrNull { it.airEpochMs ?: Long.MAX_VALUE }
        ?: entry.takeIf { (it.airEpochMs ?: 0L) >= now }
    var speak by remember { mutableStateOf(existing?.speak ?: true) }
    var timing by remember { mutableStateOf(existing?.timingValue ?: ReminderTiming.Release) }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(colors.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = nuvioSafeBottomPadding(16.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(CircleShape).background(colors.outlineVariant))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(38.dp).height(54.dp).clip(RoundedCornerShape(5.dp)).background(colors.surfaceVariant),
                ) {
                    entry.item.poster?.let {
                        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Remind me about new episodes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Text(
                        text = entry.item.title + (next?.let { " · Next: EP ${it.episode}, ${AniDramaTime.longDayLabel(it.dateIso)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ChoiceRow(
                selected = !speak,
                title = "Notification only",
                subtitle = "Quiet, like other apps",
                onClick = { speak = false },
            )
            ChoiceRow(
                selected = speak,
                title = "Notification + say it out loud",
                subtitle = "Stays quiet when the phone is on silent or Do Not Disturb",
                icon = true,
                onClick = { speak = true },
            )
            Text("When", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ReminderTiming.entries.toList(), key = { it.name }) { option ->
                    AniDramaChip(option.label, option == timing, onClick = { timing = option })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton(text = "Test voice", icon = true, primary = false, modifier = Modifier.weight(1f)) {
                    AniDramaReminders.testVoice("New episode: ${entry.item.title}, episode ${next?.episode ?: entry.episode}, is out now.")
                }
                SheetButton(text = "Save", icon = false, primary = true, modifier = Modifier.weight(1f)) {
                    val meta = entry.item.toMetaPreview()
                    val source = entry.item.source
                    val reminder = AniDramaReminder(
                        key = entry.item.key,
                        title = entry.item.title,
                        poster = entry.item.poster,
                        backdrop = entry.item.backdrop,
                        metaType = meta.type,
                        metaId = meta.id,
                        sourceKind = if (source is AniDramaSource.AniList) "anilist" else "tmdb",
                        sourceId = when (source) {
                            is AniDramaSource.AniList -> source.aniListId
                            is AniDramaSource.Tmdb -> source.tmdbId
                        },
                        speak = speak,
                        timing = timing.name,
                        network = entry.network,
                        nextEpisode = next?.episode,
                        nextAirEpochMs = next?.airEpochMs,
                    )
                    scope.launch {
                        runCatching { EpisodeReleaseNotificationPlatform.requestAuthorization() }
                        AniDramaReminders.set(reminder)
                        if (next == null) runCatching { AniDramaReminders.refreshAll() }
                    }
                    onDismiss()
                }
            }
            if (existing != null) {
                Text(
                    text = "Turn off reminder",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.error,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .clickable {
                            AniDramaReminders.remove(existing.key)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit, icon: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(if (selected) 5.dp else 1.5.dp, if (selected) colors.primary else colors.outline, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        if (icon) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SheetButton(text: String, icon: Boolean, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(CircleShape)
            .then(if (primary) Modifier.background(colors.primary) else Modifier.border(1.dp, colors.outline, CircleShape))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) colors.onPrimary else colors.onSurface)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(6.dp)
            .size(20.dp),
    )
}

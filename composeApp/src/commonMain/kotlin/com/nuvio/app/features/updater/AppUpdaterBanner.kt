package com.nuvio.app.features.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.appTheme
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.action_continue
import nuvio.composeapp.generated.resources.action_install
import nuvio.composeapp.generated.resources.action_later
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.action_update
import nuvio.composeapp.generated.resources.updates_debug_test_complete
import nuvio.composeapp.generated.resources.updates_downloading_progress
import nuvio.composeapp.generated.resources.updates_message_allow_installs
import nuvio.composeapp.generated.resources.updates_message_ready
import nuvio.composeapp.generated.resources.updates_no_release_notes
import nuvio.composeapp.generated.resources.updates_preparing_download
import nuvio.composeapp.generated.resources.updates_release_notes
import nuvio.composeapp.generated.resources.updates_title_allow_installs
import nuvio.composeapp.generated.resources.updates_title_available
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppUpdaterHost(
    controller: AppUpdaterController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!AppFeaturePolicy.inAppUpdaterEnabled || !AppUpdaterPlatform.isSupported) {
        content()
        return
    }

    val state by controller.uiState.collectAsStateWithLifecycle()
    var showReleaseNotes by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controller) {
        controller.ensureAutoCheckStarted()
    }
    LaunchedEffect(state.update?.tag) {
        showReleaseNotes = false
    }

    val update = state.update
    val showBanner = state.showDialog && update != null

    // Fenrir: a small glass pill in its own row at the top (pushes the screen down a little
    // instead of floating over the top bar's buttons).
    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = showBanner,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(durationMillis = 260)) +
                fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(durationMillis = 220)) +
                fadeOut(animationSpec = tween(durationMillis = 150)),
        ) {
            update?.let { availableUpdate ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppUpdateBanner(
                        state = state,
                        update = availableUpdate,
                        onDownload = controller::downloadUpdate,
                        onInstall = controller::installDownloadedUpdate,
                        onShowReleaseNotes = { showReleaseNotes = true },
                        onDismiss = controller::dismissDialog,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(if (showBanner) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
        ) {
            content()
        }
    }

    if (showReleaseNotes && update != null) {
        ReleaseNotesDialog(
            update = update,
            onDismiss = { showReleaseNotes = false },
        )
    }

    if (state.showUnknownSourcesDialog) {
        UnknownSourcesDialog(
            onContinue = controller::resumeInstallation,
            onDismiss = controller::dismissDialog,
        )
    }
}

@Composable
private fun AppUpdateBanner(
    state: AppUpdaterUiState,
    update: AppUpdate,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = androidx.compose.ui.graphics.Color(0xFFFF4D57)
    val progress by animateFloatAsState(
        targetValue = (if (state.isDownloading) state.downloadProgress ?: 0f else 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 180),
        label = "updatePillProgress",
    )
    val label = when {
        state.errorMessage != null -> "Update failed"
        state.isDownloading && state.downloadProgress != null -> "Downloading ${(state.downloadProgress * 100).toInt().coerceIn(0, 100)}%"
        state.isDownloading -> "Starting download…"
        state.downloadedApkPath != null -> "Update ready"
        else -> "Update available"
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 6.dp, bottom = 6.dp, start = 16.dp, end = 16.dp)
            .heightIn(min = 46.dp)
            .androidx_shadow(shape)
            .androidx_clip(shape)
            .drawBehind {
                drawRect(androidx.compose.ui.graphics.Color(0xF21C1C22))
                if (progress > 0f) {
                    drawRect(accent.copy(alpha = 0.35f), size = Size(size.width * progress, size.height))
                }
            }
            .androidx_border(shape)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier.androidx_clickable(update.notes.isNotBlank(), onShowReleaseNotes),
        ) {
            Text(
                text = label,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = state.errorMessage ?: update.tag,
                color = androidx.compose.ui.graphics.Color(0xFF9A9AA6),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!state.isDownloading) {
            Text(
                text = when {
                    state.downloadedApkPath != null -> stringResource(Res.string.action_install)
                    state.errorMessage != null -> stringResource(Res.string.action_retry)
                    else -> stringResource(Res.string.action_update)
                },
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .androidx_clip(androidx.compose.foundation.shape.CircleShape)
                    .background(accent)
                    .androidx_clickable(true, if (state.downloadedApkPath != null) onInstall else onDownload)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.action_close),
                    tint = androidx.compose.ui.graphics.Color(0xFFC9C9D2),
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(Modifier.size(10.dp))
        }
    }
}

private fun Modifier.androidx_shadow(shape: androidx.compose.ui.graphics.Shape) =
    this.then(Modifier.shadow(12.dp, shape))

private fun Modifier.androidx_clip(shape: androidx.compose.ui.graphics.Shape) =
    this.then(Modifier.clip(shape))

private fun Modifier.androidx_border(shape: androidx.compose.ui.graphics.Shape) =
    this.then(Modifier.border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f), shape))

private fun Modifier.androidx_clickable(enabled: Boolean, onClick: () -> Unit) =
    this.then(Modifier.clickable(enabled = enabled, onClick = onClick))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseNotesDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.updates_release_notes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = update.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = update.notes.ifBlank { stringResource(Res.string.updates_no_release_notes) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnknownSourcesDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.updates_title_allow_installs),
    ) {
        Text(
            text = stringResource(Res.string.updates_message_allow_installs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_later),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(Res.string.action_continue),
                onClick = onContinue,
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

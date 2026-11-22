package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.settingsRootContent(
    isTablet: Boolean,
    onPageClick: (SettingsPage) -> Unit,
    onSwitchProfileClick: (() -> Unit)? = null,
    onWatchPartyClick: (() -> Unit)? = null,
    category: SettingsCategory? = null,
) {
    // Fenrir: all sources in one place (Stremio add-ons here, manga and novel sources on the Read side).
    if (com.nuvio.app.readmode.FenrirSources.openReadExtensions != null &&
        (category == null || category == SettingsCategory.Watch)
    ) {
        item(key = "fenrir-sources") {
            FenrirSourcesGroup(isTablet = isTablet, onPageClick = onPageClick)
        }
    }
    SettingsCategory.entries.filter { category == null || it == category }.forEach { group ->
        item(key = group.name) {
            SettingsGroup(isTablet = isTablet) {
                when (group) {
                    SettingsCategory.You -> {
                        SettingsPageRow(
                            page = SettingsPage.Account,
                            description = Res.string.compose_settings_root_account_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        if (onSwitchProfileClick != null) {
                            SettingsGroupDivider(isTablet = isTablet)
                            SettingsNavigationRow(
                                title = stringResource(Res.string.compose_settings_root_switch_profile_title),
                                description = stringResource(Res.string.compose_settings_root_switch_profile_description),
                                icon = Icons.Default.People,
                                isTablet = isTablet,
                                onClick = onSwitchProfileClick,
                            )
                        }
                        if (onWatchPartyClick != null) {
                            SettingsGroupDivider(isTablet = isTablet)
                            SettingsNavigationRow(
                                title = stringResource(Res.string.party_title),
                                description = stringResource(Res.string.party_settings_description),
                                icon = Icons.Rounded.Groups,
                                isTablet = isTablet,
                                onClick = onWatchPartyClick,
                            )
                        }
                    }
                    SettingsCategory.Look -> {
                        if (com.nuvio.app.readmode.FenrirStyle.isAvailable) {
                            FenrirStyleRow(isTablet = isTablet)
                            SettingsGroupDivider(isTablet = isTablet)
                        }
                        SettingsPageRow(
                            page = SettingsPage.Appearance,
                            description = Res.string.settings_appearance_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.Layout,
                            description = Res.string.compose_settings_root_layout_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                    }
                    SettingsCategory.Watch -> {
                        SettingsPageRow(
                            page = SettingsPage.ContentDiscovery,
                            description = Res.string.compose_settings_root_content_discovery_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.Playback,
                            description = Res.string.settings_playback_subtitle,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                    }
                    SettingsCategory.Services -> {
                        SettingsPageRow(
                            page = SettingsPage.Integrations,
                            description = Res.string.compose_settings_root_integrations_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.TraktAuthentication,
                            description = Res.string.compose_settings_root_tracking_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                    }
                    SettingsCategory.System -> {
                        SettingsPageRow(
                            page = SettingsPage.Notifications,
                            description = Res.string.compose_settings_root_notifications_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.Advanced,
                            description = Res.string.compose_settings_root_advanced_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.About,
                            description = Res.string.compose_settings_root_about_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                    }
                }
            }
        }
    }
    settingsFooterContent(isTablet = isTablet)
}

@Composable
private fun SettingsPageRow(
    page: SettingsPage,
    description: StringResource,
    isTablet: Boolean,
    onPageClick: (SettingsPage) -> Unit,
) {
    SettingsNavigationRow(
        title = stringResource(page.titleRes),
        description = stringResource(description),
        icon = page.icon,
        isTablet = isTablet,
        onClick = { onPageClick(page) },
    )
}


@Composable
private fun FenrirStyleRow(isTablet: Boolean) {
    val enabled by com.nuvio.app.readmode.FenrirStyle.enabled.collectAsStateWithLifecycle()
    SettingsSwitchRow(
        title = "Fenrir style",
        description = "Big Home banner, red pill tab bar and white Play buttons. Pick colors in Appearance.",
        checked = enabled,
        isTablet = isTablet,
        onCheckedChange = { com.nuvio.app.readmode.FenrirStyle.setEnabled(it) },
    )
}

@Composable
private fun FenrirSourcesGroup(isTablet: Boolean, onPageClick: (SettingsPage) -> Unit) {
    val addons by com.nuvio.app.features.addons.AddonRepository.uiState.collectAsStateWithLifecycle()
    val sources = com.nuvio.app.readmode.FenrirSources
    val mangaCount = remember { runCatching { sources.mangaExtensionCount?.invoke() }.getOrNull() ?: 0 }
    val novelCount = remember { runCatching { sources.novelPluginCount?.invoke() }.getOrNull() ?: 0 }
    val pluginCount = com.nuvio.app.readmode.FenrirPlugins.all.sumOf { host ->
        val state by host.state.collectAsStateWithLifecycle()
        state.installed.size
    }
    val total = addons.addons.size + pluginCount + mangaCount + novelCount
    SettingsSection(title = "Sources", isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = "Sources",
                description = "Stremio · Aniyomi · CloudStream · Manga · Novel" + if (total > 0) " · $total installed" else "",
                icon = Icons.Default.Extension,
                isTablet = isTablet,
                onClick = { com.nuvio.app.readmode.FenrirSourcesHub.open() },
            )
        }
    }
}

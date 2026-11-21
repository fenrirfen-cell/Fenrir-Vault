package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.NuvioNavBarScrollState
import com.nuvio.app.core.ui.NuvioClassicNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.rememberNuvioNavBarScrollState
import com.nuvio.app.features.anidrama.AniDramaBridge
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.readmode.ReadModeBridge
import com.nuvio.app.readmode.ReadModeSwitch
import com.nuvio.app.readmode.FenrirMode
import com.nuvio.app.readmode.ModeBarBackground
import com.nuvio.app.readmode.ModeBarScroll
import com.nuvio.app.readmode.modeBarHeight
import com.nuvio.app.readmode.modeBarHiddenFraction
import com.nuvio.app.readmode.modeBarHideable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_profile
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_search
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MainTabsDestination(
    selectedTab: AppScreenTab,
    initialHomeReady: Boolean,
    rootRouteActive: Boolean,
    useTabletFloatingTabBar: Boolean,
    useNativeNavigation: Boolean,
    useNativeTabBar: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    requests: AppTabRequests,
    state: AppTabState,
    actions: (isTabletLayout: Boolean) -> AppTabActions,
    onBack: () -> Unit,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
) {
    PlatformBackHandler(enabled = rootRouteActive, onBack = onBack)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLayout = useTabletFloatingTabBar || maxWidth >= 768.dp
        val tabActions = remember(actions, isTabletLayout) { actions(isTabletLayout) }
        val useNativeBottomTabs = if (useNativeNavigation) {
            useNativeTabBar
        } else {
            liquidGlassNativeTabBarSupported && liquidGlassNativeTabBarEnabled && initialHomeReady
        }
        val tabsRouteActive = rootRouteActive
        val navBarScrollState = rememberNuvioNavBarScrollState()
        val navBarHazeState = rememberHazeState()
        val navBarStyleSetting by remember { ThemeSettingsRepository.navBarStyle }.collectAsStateWithLifecycle()
        val navBarGlowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
        val aniDramaAvailable by AniDramaBridge.isAvailable.collectAsStateWithLifecycle()
        val assistantOpen by com.nuvio.app.assistant.AssistantController.open.collectAsStateWithLifecycle()
        val fenrirStyleOn by com.nuvio.app.readmode.FenrirStyle.enabled.collectAsStateWithLifecycle()
        // Fenrir style replaces the phone bottom bars with its own pill bar.
        val fenrirBar = fenrirStyleOn && !isTabletLayout && !useNativeBottomTabs
        val fenrirNavItems = listOfNotNull(
            com.nuvio.app.readmode.FenrirNavItem(
                label = stringResource(Res.string.compose_nav_home),
                selected = selectedTab == AppScreenTab.Home || selectedTab == AppScreenTab.AniDrama,
                onClick = { onTabSelected(AppScreenTab.Home) },
                icon = Icons.Filled.Home,
            ),
            // Search moved to the round button at the top of Home (FenrirTopBar).
            if (ReadModeBridge.isAvailable) {
                com.nuvio.app.readmode.FenrirNavItem(
                    label = "Assistant",
                    selected = assistantOpen,
                    onClick = { com.nuvio.app.assistant.AssistantController.show() },
                    icon = Icons.Rounded.AutoAwesome,
                )
            } else null,
            com.nuvio.app.readmode.FenrirNavItem(
                label = "My list",
                selected = selectedTab == AppScreenTab.Library,
                onClick = { onTabSelected(AppScreenTab.Library) },
                painter = org.jetbrains.compose.resources.painterResource(Res.drawable.sidebar_library),
            ),
            com.nuvio.app.readmode.FenrirNavItem(
                label = "Settings",
                selected = selectedTab == AppScreenTab.Settings,
                onClick = { onTabSelected(AppScreenTab.Settings) },
                icon = Icons.Rounded.Settings,
            ),
        )
        val floatingNavigationItems = listOfNotNull(
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Home,
                onClick = { onTabSelected(AppScreenTab.Home) },
                icon = Icons.Filled.Home,
                label = stringResource(Res.string.compose_nav_home),
            ),
            // Fenrir assistant: opens the assistant panel (Ani-drama lives in the top switch).
            if (ReadModeBridge.isAvailable) {
                FloatingNavigationItem(
                    selected = assistantOpen,
                    onClick = { com.nuvio.app.assistant.AssistantController.show() },
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Assistant",
                )
            } else null,
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Search,
                onClick = { onTabSelected(AppScreenTab.Search) },
                drawable = Res.drawable.sidebar_search,
                label = stringResource(Res.string.compose_nav_search),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Library,
                onClick = { onTabSelected(AppScreenTab.Library) },
                drawable = Res.drawable.sidebar_library,
                label = stringResource(Res.string.compose_nav_library),
            ),
            FloatingNavigationItem(
                selected = selectedTab == AppScreenTab.Settings,
                onClick = { onTabSelected(AppScreenTab.Settings) },
                label = stringResource(Res.string.compose_nav_profile),
                content = { onClick ->
                    ProfileSwitcherTab(
                        selected = selectedTab == AppScreenTab.Settings,
                        onClick = onClick,
                        onProfileSelected = onProfileSelected,
                        onAddProfileRequested = onAddProfileRequested,
                        hazeState = navBarHazeState,
                        popupBelowAnchor = isTabletLayout,
                    )
                },
            ),
        )

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (initialHomeReady) 1f else 0f),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                // Fenrir's bar floats over the content instead (drawn below).
                if (!fenrirBar && !isTabletLayout && !useNativeBottomTabs && navBarStyleSetting == NavBarStyle.CLASSIC) {
                    NuvioClassicNavigationBar {
                        NavItem(
                            selected = selectedTab == AppScreenTab.Home,
                            onClick = { onTabSelected(AppScreenTab.Home) },
                            icon = Icons.Filled.Home,
                            contentDescription = stringResource(Res.string.compose_nav_home),
                        )
                        if (ReadModeBridge.isAvailable) {
                            NavItem(
                                selected = assistantOpen,
                                onClick = { com.nuvio.app.assistant.AssistantController.show() },
                                icon = Icons.Rounded.AutoAwesome,
                                contentDescription = "Assistant",
                            )
                        }
                        NavItem(
                            selected = selectedTab == AppScreenTab.Search,
                            onClick = { onTabSelected(AppScreenTab.Search) },
                            icon = Res.drawable.sidebar_search,
                            contentDescription = stringResource(Res.string.compose_nav_search),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Library,
                            onClick = { onTabSelected(AppScreenTab.Library) },
                            icon = Res.drawable.sidebar_library,
                            contentDescription = stringResource(Res.string.compose_nav_library),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Settings,
                            onClick = { onTabSelected(AppScreenTab.Settings) },
                        ) {
                            ProfileSwitcherTab(
                                selected = selectedTab == AppScreenTab.Settings,
                                onClick = { onTabSelected(AppScreenTab.Settings) },
                                onProfileSelected = onProfileSelected,
                                onAddProfileRequested = onAddProfileRequested,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            // Fenrir: the Watch / Ani-drama / Read bar sits on top of Home and Ani-drama.
            val showModeBar = ReadModeBridge.isAvailable &&
                (selectedTab == AppScreenTab.Home || selectedTab == AppScreenTab.AniDrama)
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNuvioBottomNavigationOverlayPadding provides if (useNativeBottomTabs) 49.dp else if (fenrirBar) 84.dp else if (!isTabletLayout && navBarStyleSetting != NavBarStyle.CLASSIC) 72.dp else 0.dp,
                    LocalNuvioNavBarScrollState provides navBarScrollState,
                ) {
                    AppTabHost(
                        selectedTab = selectedTab,
                        requests = requests,
                        state = state,
                        actions = tabActions,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isTabletLayout || navBarStyleSetting != NavBarStyle.CLASSIC || showModeBar) Modifier.hazeSource(state = navBarHazeState) else Modifier)
                            .then(if (navBarStyleSetting == NavBarStyle.ADAPTIVE) Modifier.nestedScroll(navBarScrollState.nestedScrollConnection) else Modifier)
                            .padding(innerPadding)
                            .then(if (selectedTab == AppScreenTab.Home && ReadModeBridge.isAvailable) Modifier.nestedScroll(ModeBarScroll.connection) else Modifier),
                    )
                }

                // Watch / Ani-drama / Read switch (Android app with Read mode only).
                if (showModeBar) {
                    androidx.compose.runtime.LaunchedEffect(selectedTab) { ModeBarScroll.reset() }
                    // Soft fade behind the switch; Home content scrolls underneath it.
                    ModeBarBackground()
                    if (fenrirStyleOn) {
                        com.nuvio.app.readmode.FenrirTopBar(
                            selected = if (selectedTab == AppScreenTab.AniDrama) FenrirMode.AniDrama else FenrirMode.Watch,
                            onWatch = { onTabSelected(AppScreenTab.Home) },
                            onAniDrama = if (aniDramaAvailable) {
                                { onTabSelected(AppScreenTab.AniDrama) }
                            } else null,
                            onSearch = { onTabSelected(AppScreenTab.Search) },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .modeBarHideable(modeBarHiddenFraction(), modeBarHeight())
                                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp),
                        )
                        // The picked CloudStream source, tucked under the FENRIR READ wordmark. Drawn
                        // separately so the top bar keeps its height and its buttons stay put.
                        if (selectedTab == AppScreenTab.Home) {
                            com.nuvio.app.readmode.FenrirSourcePillOverlay(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .modeBarHideable(modeBarHiddenFraction(), modeBarHeight())
                                    .padding(
                                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp + 42.dp,
                                        start = 16.dp,
                                        end = 140.dp,
                                    ),
                            )
                        }
                    } else ReadModeSwitch(
                        hazeState = navBarHazeState,
                        selected = if (selectedTab == AppScreenTab.AniDrama) FenrirMode.AniDrama else FenrirMode.Watch,
                        onWatch = { onTabSelected(AppScreenTab.Home) },
                        onAniDrama = if (aniDramaAvailable) {
                            { onTabSelected(AppScreenTab.AniDrama) }
                        } else null,
                        modifier = Modifier
                            .align(if (isTabletLayout) Alignment.TopStart else Alignment.TopCenter)
                            .modeBarHideable(modeBarHiddenFraction(), modeBarHeight())
                            .padding(
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                                start = if (isTabletLayout) 16.dp else 0.dp,
                            ),
                    )
                }

                if (isTabletLayout && !useNativeBottomTabs) {
                    val tabletNavBarScrollState = remember { NuvioNavBarScrollState().apply { collapse() } }
                    FloatingNavigationBar(
                        modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 416.dp),
                        scrollState = tabletNavBarScrollState,
                        hazeState = navBarHazeState,
                        contentPadding = PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                            bottom = 8.dp,
                        ),
                        compactSize = true,
                        items = floatingNavigationItems,
                        glowEnabled = navBarGlowEnabled,
                    )
                }

                if (!fenrirBar && !isTabletLayout && !useNativeBottomTabs && navBarStyleSetting != NavBarStyle.CLASSIC) {
                    when (navBarStyleSetting) {
                        NavBarStyle.EXPANDED -> navBarScrollState.expand()
                        NavBarStyle.COMPACT -> navBarScrollState.collapse()
                        else -> {}
                    }
                    FloatingNavigationBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        scrollState = navBarScrollState,
                        hazeState = navBarHazeState,
                        items = floatingNavigationItems,
                        glowEnabled = navBarGlowEnabled,
                    )
                }

                if (fenrirBar) {
                    com.nuvio.app.readmode.FenrirNavigationBar(
                        fenrirNavItems,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                // Fenrir assistant panel (drawn above everything else).
                com.nuvio.app.assistant.AssistantSheetHost()
            }
        }
    }
}

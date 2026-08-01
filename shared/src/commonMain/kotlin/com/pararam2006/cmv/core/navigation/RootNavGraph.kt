package com.pararam2006.cmv.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pararam2006.cmv.ui.Dimens
import com.pararam2006.cmv.ui.about.AboutScreen
import com.pararam2006.cmv.ui.changeMode.ChangeModeScreen
import com.pararam2006.cmv.ui.changeMode.ChangeModeScreenViewModel
import com.pararam2006.cmv.ui.listenerError.ListenerErrorScreen
import com.pararam2006.cmv.ui.main.MainScreen
import com.pararam2006.cmv.ui.main.MainViewModel
import com.pararam2006.cmv.ui.selectApps.SelectAppsScreen
import com.pararam2006.cmv.ui.selectApps.SelectAppsScreenViewModel
import com.pararam2006.cmv.ui.settings.SettingsScreen
import com.pararam2006.cmv.ui.settings.SettingsViewModel
import com.pararam2006.cmv.ui.theme.CustomMusicVolumeTheme
import com.pararam2006.cmv.ui.theme.disableServiceContainer
import com.pararam2006.cmv.ui.theme.enableServiceContainer
import custommusicvolume.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNavGraph(appVersion: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val mainViewModel = koinViewModel<MainViewModel>()
    val mainUiState by mainViewModel.mainScreenUiState.collectAsState()
    val listenerUiState by mainViewModel.listenerUiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) mainViewModel.refreshListenerUiState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(listenerUiState.connected, currentDestination) {
        if (listenerUiState.connected && currentDestination?.hasRoute<Route.ListenerError>() == true) {
            navController.navigate(Route.Main) {
                popUpTo(Route.ListenerError) { inclusive = true }
            }
        }
    }

    LaunchedEffect(listenerUiState.restartResult) {
        when (listenerUiState.restartResult) {
            false -> {
                if (currentDestination?.hasRoute<Route.ListenerError>() != true) {
                    navController.navigate(Route.ListenerError) {
                        popUpTo(Route.Main) { inclusive = true }
                    }
                }
                mainViewModel.clearRestartResult()
            }
            true -> mainViewModel.clearRestartResult()
            null -> Unit
        }
    }

    val searchFieldFocusRequester = remember { FocusRequester() }
    val searchFieldFocusManager = LocalFocusManager.current
    val isAddButtonEnabled =
        !listenerUiState.serviceSupported || !mainUiState.currentPlayingTrack.isNullOrEmpty()

    val title = when {
        currentDestination?.hasRoute<Route.Main>() == true -> stringResource(Res.string.main_screen_title)
        currentDestination?.hasRoute<Route.Settings>() == true -> stringResource(Res.string.settings_screen_title)
        currentDestination?.hasRoute<Route.ListenerError>() == true -> stringResource(Res.string.listener_error_screen_title)
        currentDestination?.hasRoute<Route.About>() == true -> stringResource(Res.string.about_screen_title)
        currentDestination?.hasRoute<Route.ChangeMode>() == true -> stringResource(Res.string.change_mode_screen_title)
        currentDestination?.hasRoute<Route.SelectApps>() == true -> stringResource(Res.string.select_apps_screen_title)
        else -> stringResource(Res.string.unknown_screen_title)
    }

    val isNavigationIconVisible = currentDestination?.hasRoute<Route.Main>() == false

    CustomMusicVolumeTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        AnimatedVisibility(
                            visible = mainUiState.isTitleVisible,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        ) {
                            Text(
                                text = title,
                                maxLines = 1,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = Dimens.paddingSmall)
                            )
                        }
                    },
                    navigationIcon = {
                        if (isNavigationIconVisible) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    painter = painterResource(Res.drawable.outline_arrow_back_24),
                                    contentDescription = stringResource(Res.string.main_screen_navigate_back_desc),
                                )
                            }
                        }
                    },
                    actions = {
                        MyTopAppBarActions(
                            isMainScreen = currentDestination?.hasRoute<Route.Main>() == true,
                            isTitleVisible = mainUiState.isTitleVisible,
                            isServiceSupported = listenerUiState.serviceSupported,
                            isOn = listenerUiState.isOn,
                            isStarting = listenerUiState.isStarting,
                            onToogleService = mainViewModel::toggleService,
                            onOpenAddDialog = mainViewModel::openAddDialog,
                            onOpenListenerErrorScreen = {
                                navController.navigate(Route.ListenerError) {
                                    popUpTo(Route.Main) { inclusive = true }
                                }
                            },
                            onStartSearching = mainViewModel::startSearch,
                            onCloseSearch = {
                                mainViewModel.closeSearch()
                                searchFieldFocusManager.clearFocus()
                            },
                            onSearchChange = mainViewModel::changeSearch,
                            searchText = mainUiState.searchQuery,
                            searchFieldFocusRequester = searchFieldFocusRequester,
                            keyboardActionOnDone = {
                                searchFieldFocusManager.clearFocus()
                            },
                            isAddButtonEnabled = isAddButtonEnabled
                        )
                    },
                )
            },
            bottomBar = {
                BottomAppBar {
                    BottomNavBar(
                        onNavigateToSettings = {
                            if (currentDestination?.hasRoute<Route.Settings>() == false) {
                                navController.navigate(Route.Settings)
                            }
                        },
                        currentDestination = currentDestination,
                        onNavigateToAbout = {
                            if (currentDestination?.hasRoute<Route.About>() == false) {
                                navController.navigate(Route.About)
                            }
                        },
                        onNavigateToMain = {
                            if (currentDestination?.hasRoute<Route.Main>() == false) {
                                navController.navigate(Route.Main)
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                NavHost(
                    navController = navController,
                    startDestination = Route.Main,
                    enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }) + fadeIn() },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }) + fadeOut() },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }) + fadeIn() },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }) + fadeOut() },
                    modifier = Modifier.weight(1f),
                ) {
                    composable<Route.Main> {
                        MainScreen(
                            uiState = mainUiState,
                            listenerUiState = listenerUiState,
                            onStartTrackDelete = mainViewModel::startTrackDelete,
                            onCancelTrackDelete = mainViewModel::cancelTrackDelete,
                            onSaveTrackVolume = mainViewModel::saveTrackVolume,
                            onTrackSearch = { query -> mainViewModel.searchTrackInBrowser(query) },
                            onCloseAddDialog = mainViewModel::closeAddDialog,
                            onStartEdit = mainViewModel::startEdit,
                            onStopEdit = mainViewModel::stopEdit,
                            onPermissionWarningRefresh = mainViewModel::refreshListenerUiState,
                            onOpenPermissionSettings = mainViewModel::openNotificationsAccessSettings,
                            onDismissHeadsetNotConnectedDialog = mainViewModel::closeHeadsetNotConnectedDialog,
                            onArtistChange = mainViewModel::changeEditArtist,
                            onTitleChange = mainViewModel::changeEditTitle,
                            onStartIncrementing = mainViewModel::startIncrementing,
                            onStopIncrementing = mainViewModel::stopIncrementing,
                            onStartDecrementing = mainViewModel::startDecrementing,
                            onStopDecrementing = mainViewModel::stopDecrementing,
                            onOffsetChange = mainViewModel::changeEditOffset,
                            manualTrackEntryEnabled = !listenerUiState.serviceSupported,
                        )
                    }
                    composable<Route.Settings> {
                        val settingsScreenViewModel = koinViewModel<SettingsViewModel>()
                        val uiState by settingsScreenViewModel.uiState.collectAsState()

                        SettingsScreen(
                            uiState = uiState,
                            onSetShowSystemVolumeUi = settingsScreenViewModel::setShowSystemVolumeUi,
                            onSliderPositionChange = settingsScreenViewModel::changeSliderPositionState,
                            onNavigateToApps = { navController.navigate(Route.SelectApps) },
                            onNavigateToChangeMode = { navController.navigate(Route.ChangeMode) }
                        )
                    }

                    composable<Route.ListenerError> {
                        ListenerErrorScreen(
                            onOpenNotificationListenerSettings = mainViewModel::openNotificationsAccessSettings,
                            onNavigateToMain = {
                                navController.navigate(Route.Main) {
                                    popUpTo(Route.ListenerError) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable<Route.About> {
                        AboutScreen(version = appVersion)
                    }

                    composable<Route.ChangeMode> {
                        val vm = koinViewModel<ChangeModeScreenViewModel>()
                        val uiState by vm.uiState.collectAsState()

                        ChangeModeScreen(
                            mode = uiState.mode,
                            onModeChange = vm::setAppMode
                        )
                    }

                    composable<Route.SelectApps> {
                        val vm = koinViewModel<SelectAppsScreenViewModel>()
                        val uiState by vm.uiState.collectAsState()

                        SelectAppsScreen(
                            apps = uiState.apps,
                            isLoading = uiState.isLoading,
                            loadFailed = uiState.loadFailed,
                            onRetry = vm::retry,
                            onToogle = vm::toogleApp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MyTopAppBarActions(
    isMainScreen: Boolean,
    isTitleVisible: Boolean,
    isServiceSupported: Boolean,
    isOn: Boolean,
    isStarting: Boolean,
    onToogleService: () -> Boolean,
    onOpenAddDialog: () -> Unit,
    onOpenListenerErrorScreen: () -> Unit,
    onStartSearching: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    searchText: String,
    searchFieldFocusRequester: FocusRequester,
    keyboardActionOnDone: () -> Unit,
    modifier: Modifier = Modifier,
    isAddButtonEnabled: Boolean,
) {
    Row(
        modifier = modifier.padding(horizontal = Dimens.paddingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        if (isMainScreen) {
            Box(modifier = Modifier.animateContentSize()) {
                this@Row.AnimatedVisibility(
                    visible = isTitleVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(onClick = onStartSearching) {
                        Icon(
                            painter = painterResource(Res.drawable.outline_search_24),
                            contentDescription = stringResource(Res.string.main_screen_search_desc)
                        )
                    }
                }

                this@Row.AnimatedVisibility(
                    visible = !isTitleVisible,
                    enter = slideInHorizontally { it } + expandHorizontally(expandFrom = Alignment.End) + fadeIn(
                        initialAlpha = 0.3f
                    ),
                    exit = slideOutHorizontally { it } + shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
                ) {
                    TextField(
                        value = searchText,
                        onValueChange = onSearchChange,
                        placeholder = { Text(text = stringResource(Res.string.main_screen_search_placeholder)) },
                        trailingIcon = {
                            IconButton(onClick = onCloseSearch) {
                                Icon(
                                    painter = painterResource(Res.drawable.outline_close_24),
                                    contentDescription = stringResource(Res.string.main_screen_close_search_desc)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(searchFieldFocusRequester),
                        shape = RoundedCornerShape(Dimens.paddingMedium),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardActionOnDone() })
                    )
                    LaunchedEffect(Unit) { searchFieldFocusRequester.requestFocus() }
                }
            }

            IconButton(onClick = onOpenAddDialog, enabled = isAddButtonEnabled) {
                Icon(
                    painter = painterResource(Res.drawable.outline_add_24),
                    contentDescription = stringResource(Res.string.main_screen_create_volume_by_self_desc)
                )
            }

            if (!isServiceSupported) return@Row

            val containerColor = if (isOn) disableServiceContainer else enableServiceContainer
            val contentColor =
                if (isOn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer

            FilledIconButton(
                onClick = { if (!onToogleService()) onOpenListenerErrorScreen() },
                enabled = !isStarting,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                    .padding(start = 5.dp)
            ) {
                if (isStarting) {
                    CircularProgressIndicator(color = contentColor)
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.outline_refresh_24),
                        contentDescription = stringResource(if (isOn) Res.string.main_screen_disable_service_desc else Res.string.main_screen_enable_service_desc)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    onNavigateToSettings: () -> Unit,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
    onNavigateToAbout: () -> Unit,
    onNavigateToMain: () -> Unit,
) {
    NavigationBar(modifier = modifier.padding(horizontal = Dimens.paddingMedium).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavigationBarItem(
                selected = currentDestination?.hasRoute<Route.Main>() ?: false,
                onClick = onNavigateToMain,
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.outline_list_24),
                        contentDescription = stringResource(Res.string.bottom_navbar_main_icon_desc)
                    )
                },
                label = { Text(text = stringResource(Res.string.bottom_navbar_main)) }
            )
            NavigationBarItem(
                selected = currentDestination?.hasRoute<Route.Settings>() ?: false,
                onClick = onNavigateToSettings,
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.outline_settings_24),
                        contentDescription = stringResource(Res.string.main_screen_settings_button_desc)
                    )
                },
                label = { Text(text = stringResource(Res.string.settings_screen_title)) }
            )
            NavigationBarItem(
                selected = currentDestination?.hasRoute<Route.About>() ?: false,
                onClick = onNavigateToAbout,
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.outline_info_24),
                        contentDescription = stringResource(Res.string.bottom_navbar_about_icon_desc)
                    )
                },
                label = { Text(text = stringResource(Res.string.bottom_navbar_about)) }
            )
        }
    }
}

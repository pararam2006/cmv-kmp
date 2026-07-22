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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pararam2006.cmv.BuildConfig
import com.pararam2006.cmv.R
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
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val isMainScreen = currentRoute?.contains("Main") == true || currentRoute == null
    val mainViewModel: MainViewModel = koinViewModel()
    val mainScreenUiState =
        mainViewModel.mainScreenUiState.collectAsStateWithLifecycle().value
    val lifecycleOwner = LocalLifecycleOwner.current
    val listenerUiState by mainViewModel.listenerUiState.collectAsState()
    val appVersion = BuildConfig.VERSION_NAME
    val searchFieldFocusRequester = remember { FocusRequester() }
    val searchFieldFocusManager = LocalFocusManager.current
    val isAddButtonEnabled = !mainScreenUiState.currentPlayingTrack.isNullOrEmpty()

    var title: String
    val isNavigationIconVisible: Boolean = currentDestination?.hasRoute<Route.Main>() == false

    when {
        currentDestination?.hasRoute<Route.Main>() == true -> {
            title = stringResource(R.string.main_screen_title)
        }

        currentDestination?.hasRoute<Route.Settings>() == true -> {
            title = stringResource(R.string.settings_screen_title)
        }

        currentDestination?.hasRoute<Route.ListenerError>() == true -> {
            title = stringResource(R.string.listener_error_screen_title)
        }

        currentDestination?.hasRoute<Route.About>() == true -> {
            title = stringResource(R.string.about_screen_title)
        }

        currentDestination?.hasRoute<Route.ChangeMode>() == true -> {
            title = stringResource(R.string.change_mode_screen_title)
        }

        currentDestination?.hasRoute<Route.SelectApps>() == true -> {
            title = stringResource(R.string.select_apps_screen_title)
        }

        else -> {
            title = stringResource(R.string.unknown_screen_title)
        }
    }

    // Re-check permission & request rebind when returning to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainViewModel.refreshListenerUiState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(listenerUiState.connected, currentDestination) {
        // Отсутствие запуска сервиса при запуске приложения решает проблему bind'а с системой
        if (listenerUiState.connected && currentDestination?.hasRoute<Route.ListenerError>() == true) {
            navController.navigate(Route.Main) {
                popUpTo(Route.ListenerError) { inclusive = true }
            }
        }
    }

    LaunchedEffect(listenerUiState.restartResult) {
        if (listenerUiState.restartResult == false) {
            navController.navigate(Route.ListenerError) {
                popUpTo(Route.Main) { inclusive = true }
            }
        }
        if (listenerUiState.restartResult != null) {
            mainViewModel.clearRestartResult()
        }
    }

    CustomMusicVolumeTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        AnimatedVisibility(
                            visible = mainScreenUiState.isTitleVisible,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        ) {
                            Text(
                                text = title,
                                maxLines = 1,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
                            )
                        }
                    },
                    navigationIcon = {
                        if (isNavigationIconVisible) {
                            IconButton(onClick = {
                                if (navBackStackEntry != null) {
                                    navController.popBackStack()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.main_screen_navigate_back_desc),
                                )
                            }
                        }
                    },
                    actions = {
                        MyTopAppBarActions(
                            isMainScreen = isMainScreen,
                            isTitleVisible = mainScreenUiState.isTitleVisible,
                            isOn = listenerUiState.isOn,
                            isStarting = listenerUiState.isStarting,
                            onToogleService = { mainViewModel.toggleService() == true },
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
                            searchText = mainScreenUiState.searchQuery,
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
                    // Анимация при входе на любой экран
                    enterTransition = {
                        slideInHorizontally(initialOffsetX = { 1000 }) + fadeIn()
                    },
                    // Анимация при уходе с экрана
                    exitTransition = {
                        slideOutHorizontally(targetOffsetX = { -1000 }) + fadeOut()
                    },
                    // Анимация при возврате назад (движение слева направо)
                    popEnterTransition = {
                        slideInHorizontally(initialOffsetX = { -1000 }) + fadeIn()
                    },
                    // Анимация исчезновения текущего экрана при нажатии "Назад"
                    popExitTransition = {
                        slideOutHorizontally(targetOffsetX = { 1000 }) + fadeOut()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    composable<Route.Main> {
                        MainScreen(
                            uiState = mainScreenUiState,
                            listenerUiState = listenerUiState,
                            onTrackDelete = mainViewModel::deleteTrackVolume,
                            onSaveTrackVolume = mainViewModel::saveTrackVolume,
                            onTrackSearch = { query ->
                                mainViewModel.searchTrackInBrowser(
                                    query
                                )
                            },
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
                        )
                    }
                    composable<Route.Settings> {
                        val settingsScreenViewModel: SettingsViewModel = koinViewModel()
                        val uiState =
                            settingsScreenViewModel.uiState.collectAsStateWithLifecycle().value

                        SettingsScreen(
                            uiState = uiState,
                            onSetShowSystemVolumeUi = settingsScreenViewModel::setShowSystemVolumeUi,
                            onSliderPositionChange = settingsScreenViewModel::changeSliderPositionState,
                            onNavigateToApps = {
                                navController.navigate(Route.SelectApps)
                            },
                            onNavigateToChangeMode = {
                                navController.navigate(Route.ChangeMode)
                            }
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
                        AboutScreen(
                            version = appVersion,
                        )
                    }

                    composable<Route.ChangeMode> {
                        val vm: ChangeModeScreenViewModel = koinViewModel()
                        val uiState = vm.uiState.collectAsStateWithLifecycle().value

                        ChangeModeScreen(
                            mode = uiState.mode,
                            onModeChange = vm::setAppMode
                        )
                    }

                    composable<Route.SelectApps> {
                        val vm: SelectAppsScreenViewModel = koinViewModel()
                        val uiState = vm.uiState.collectAsStateWithLifecycle().value

                        SelectAppsScreen(
                            apps = uiState.apps,
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
        modifier = modifier
            .padding(
                start = dimensionResource(R.dimen.padding_medium),
                end = dimensionResource(R.dimen.padding_medium)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        if (isMainScreen) {
            Box(
                modifier = Modifier.animateContentSize()
            ) {
                this@Row.AnimatedVisibility(
                    visible = isTitleVisible,
                    enter = fadeIn(),
                    //                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                    exit = fadeOut(),
                ) {
                    IconButton(onClick = onStartSearching) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.main_screen_search_desc)
                        )
                    }
                }

                this@Row.AnimatedVisibility(
                    visible = !isTitleVisible,
                    enter = slideInHorizontally { fullWidth -> fullWidth } +
                            expandHorizontally(expandFrom = Alignment.End) +
                            fadeIn(initialAlpha = 0.3f),
                    exit = slideOutHorizontally { fullWidth -> fullWidth } +
                            shrinkHorizontally(shrinkTowards = Alignment.End) +
                            fadeOut(),
                ) {
                    TextField(
                        value = searchText,
                        onValueChange = onSearchChange,
                        placeholder = {
                            Text(text = stringResource(R.string.main_screen_search_placeholder))
                        },
                        trailingIcon = {
                            IconButton(onClick = onCloseSearch) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.main_screen_close_search_desc),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFieldFocusRequester),
                        shape = RoundedCornerShape(dimensionResource(R.dimen.padding_medium)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardActionOnDone() })
                    )

                    LaunchedEffect(Unit) {
                        searchFieldFocusRequester.requestFocus()
                    }
                }
            }

            IconButton(
                onClick = onOpenAddDialog,
                enabled = isAddButtonEnabled
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.main_screen_create_volume_by_self_desc),
                )
            }

            val containerColor = if (isOn) {
                disableServiceContainer
            } else {
                enableServiceContainer
            }
            val contentColor = if (isOn) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }

            FilledIconButton(
                onClick = {
                    val isSuccess = onToogleService()
                    if (!isSuccess) {
                        onOpenListenerErrorScreen()
                    }
                },
                enabled = !isStarting,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
                modifier = Modifier
                    .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                    .padding(start = 5.dp)
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        color = contentColor,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (isOn) {
                            stringResource(R.string.main_screen_disable_service_desc)
                        } else {
                            stringResource(R.string.main_screen_enable_service_desc)
                        },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ActionsPreview() {
    CustomMusicVolumeTheme {
        MyTopAppBarActions(
            isMainScreen = true,
            isTitleVisible = true,
            isOn = false,
            isStarting = false,
            onToogleService = { false },
            onOpenAddDialog = {},
            onOpenListenerErrorScreen = {},
            onStartSearching = {},
            onCloseSearch = {},
            onSearchChange = {},
            searchText = "penis bolshoy",
            searchFieldFocusRequester = remember { FocusRequester() },
            keyboardActionOnDone = {},
            isAddButtonEnabled = true
        )
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
    NavigationBar(
        modifier = modifier
            .padding(
                start = dimensionResource(R.dimen.padding_medium),
                end = dimensionResource(R.dimen.padding_medium)
            )
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavigationBarItem(
                selected = currentDestination?.hasRoute<Route.Main>() ?: false,
                onClick = onNavigateToMain,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.bottom_navbar_main_icon_desc),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.bottom_navbar_main)
                    )
                }
            )

            NavigationBarItem(
                selected = currentDestination?.hasRoute<Route.Settings>() ?: false,
                onClick = onNavigateToSettings,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.main_screen_settings_button_desc),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.settings_screen_title)
                    )
                }
            )

            NavigationBarItem(
                selected = currentDestination?.hasRoute<Route.About>() ?: false,
                onClick = onNavigateToAbout,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.bottom_navbar_about_icon_desc),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.bottom_navbar_about)
                    )
                }
            )

        }
    }
}

@Preview
@Composable
private fun BottomNavBarPreview() {
    CustomMusicVolumeTheme {
        BottomNavBar(
            onNavigateToSettings = {},
            currentDestination = null,
            onNavigateToAbout = {},
        ) { }
    }
}
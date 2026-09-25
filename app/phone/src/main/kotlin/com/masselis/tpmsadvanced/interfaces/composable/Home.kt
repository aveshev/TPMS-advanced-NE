package com.masselis.tpmsadvanced.interfaces.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.masselis.tpmsadvanced.R
import com.masselis.tpmsadvanced.core.ui.LocalHomeNavController
import com.masselis.tpmsadvanced.core.ui.Spotlight
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.MonitoringButton
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.PersistentScanningHost
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.CurrentVehicleDropdown
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.LocalVehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QrCodeScan
import com.masselis.tpmsadvanced.feature.unlocated.interfaces.ui.UnlocatedSensorList
import com.masselis.tpmsadvanced.interfaces.composable.HomeTags.backButton
import com.masselis.tpmsadvanced.interfaces.composable.HomeTags.carListDropdownMenu
import com.masselis.tpmsadvanced.interfaces.viewmodel.HomeViewModel
import com.masselis.tpmsadvanced.interfaces.viewmodel.VehicleHomeViewModel
import com.masselis.tpmsadvanced.interfaces.viewmodel.VehicleHomeViewModel.Event
import com.masselis.tpmsadvanced.ioc.Bindings.Companion.HomeViewModel
import com.masselis.tpmsadvanced.ioc.Bindings.Companion.VehicleHomeViewModel
import java.util.UUID

@Composable
internal fun Home(
    expectedVehicle: UUID?,
    viewModel: HomeViewModel = viewModel(key = "HomeViewModel_$expectedVehicle") {
        HomeViewModel(expectedVehicle = expectedVehicle)
    }
) {
    val vehicleComponent by viewModel.vehicleComponentStateFlow.collectAsState()
    PersistentScanningHost {
        VehicleHome(
            vehicleComponent = vehicleComponent
        )
    }
}

@Suppress("LongMethod")
@Composable
internal fun VehicleHome(
    vehicleComponent: VehicleComponent,
    viewModel: VehicleHomeViewModel = viewModel { VehicleHomeViewModel() }
) {
    val navController = rememberNavController()
    CompositionLocalProvider(
        LocalVehicleComponent provides vehicleComponent,
        LocalHomeNavController provides navController
    ) {
        var offsetToFocus by remember { mutableStateOf<Offset?>(null) }
        var showManualMonitoringSpotlight by remember { mutableStateOf(false) }
        var showAndroidAutoSupportAlert by remember { mutableStateOf(false) }
        var showWicarlinkSupportAlert by remember { mutableStateOf(false) }
        // A vehicle just added becomes the current one, its settings open once its home is shown
        var openSettingsOf by rememberSaveable { mutableStateOf<UUID?>(null) }
        val snackbarHostState = remember { SnackbarHostState() }
        Scaffold(
            topBar = {
                TopAppBar(
                    onVehicleAdded = { openSettingsOf = it },
                    manualBackgroundButtonModifier = Modifier.onGloballyPositioned { coordinates ->
                        if (offsetToFocus != null) return@onGloballyPositioned
                        coordinates.positionInRoot()
                            .takeIf { it != Offset.Unspecified }
                            ?.let { topLeft ->
                                Offset(
                                    topLeft.x + coordinates.size.width.div(2),
                                    topLeft.y + coordinates.size.height.div(2)
                                )
                            }
                            ?.also { offsetToFocus = it }
                    },
                )
            },
            content = { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = "${Path.Home(vehicleComponent.vehicle.uuid)}"
                ) {
                    val modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                    composable(route = "${Path.Home(vehicleComponent.vehicle.uuid)}") {
                        CurrentVehicle(
                            snackbarHostState = snackbarHostState,
                            modifier = modifier
                        )
                        // Inside the graph so the settings route of this vehicle surely exists
                        LaunchedEffect(openSettingsOf) {
                            openSettingsOf
                                ?.takeIf { it == vehicleComponent.vehicle.uuid }
                                ?.also {
                                    openSettingsOf = null
                                    navController.navigate("${Path.Settings(it)}")
                                }
                        }
                    }
                    composable("${Path.Settings(vehicleComponent.vehicle.uuid)}") {
                        Settings(
                            openPressure = {
                                navController.navigate("${Path.PressureSettings(vehicleComponent.vehicle.uuid)}")
                            },
                            openTemperature = {
                                navController.navigate("${Path.TemperatureSettings(vehicleComponent.vehicle.uuid)}")
                            },
                            openBindingMethod = {
                                navController.navigate("${Path.BindingMethod(vehicleComponent.vehicle.uuid)}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.PressureSettings(vehicleComponent.vehicle.uuid)}") {
                        PressureSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.TemperatureSettings(vehicleComponent.vehicle.uuid)}") {
                        TemperatureSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.AppSettings}") {
                        AppSettings(
                            openTimeSinceUpdate = {
                                navController.navigate("${Path.TimeSinceUpdate}")
                            },
                            openPersistentScanning = {
                                navController.navigate("${Path.PersistentScanning}")
                            },
                            openActivateScanConditions = {
                                navController.navigate("${Path.ActivateScanConditions}")
                            },
                            openSuspendScanConditions = {
                                navController.navigate("${Path.SuspendScanConditions}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.TimeSinceUpdate}") {
                        TimeSinceUpdateSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.PersistentScanning}") {
                        PersistentScanningDetailsSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.ActivateScanConditions}") {
                        ActivateScanConditionsSettings(
                            openStayActiveDuration = {
                                navController.navigate("${Path.StayActiveDuration}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.StayActiveDuration}") {
                        StayActiveDurationSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.SuspendScanConditions}") {
                        SuspendScanConditionsSettings(
                            openExceptedWifis = {
                                navController.navigate("${Path.ExceptedWifis}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.ExceptedWifis}") {
                        ExceptedWifisSettings(
                            modifier = modifier
                        )
                    }
                    composable("${Path.BindingMethod(vehicleComponent.vehicle.uuid)}") {
                        ChooseBindingMethod(
                            scanQrCode = {
                                navController.navigate("${Path.QrCode(vehicleComponent.vehicle.uuid)}")
                            },
                            searchUnlocatedSensors = {
                                navController.navigate("${Path.Unlocated(vehicleComponent.vehicle.uuid)}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.QrCode(vehicleComponent.vehicle.uuid)}") {
                        QrCodeScan(
                            snackbarHostState = snackbarHostState,
                            openUnlocatedSensorBinding = {
                                navController.navigate("${Path.Unlocated(vehicleComponent.vehicle.uuid)}")
                            },
                            modifier = modifier
                        )
                    }
                    composable("${Path.Unlocated(vehicleComponent.vehicle.uuid)}") {
                        UnlocatedSensorList(
                            vehicleUuid = vehicleComponent.vehicle.uuid,
                            bindingFinished = {
                                navController.popBackStack(
                                    "${Path.Home(vehicleComponent.vehicle.uuid)}",
                                    false
                                )
                            },
                            modifier = modifier
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        )
        if (offsetToFocus != null)
            AnimatedVisibility(
                visible = showManualMonitoringSpotlight,
                enter = fadeIn(spring(0f)),
                exit = fadeOut()
            ) {
                with(LocalDensity.current) {
                    @Suppress("MaxLineLength")
                    Spotlight(
                        center = offsetToFocus!!,
                        radius = 50.dp.toPx(),
                        text = AnnotatedString("Something new is waiting for you,\nTap this button to monitor your vehicle while the app is in background"),
                        textPadding = 8.dp.toPx(),
                        textStyle = TextStyle.Default.copy(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        onSpotlight = { showManualMonitoringSpotlight = false }
                    )
                }
            }
        if (showWicarlinkSupportAlert) {
            WicarlinkSupportAlert(
                onDismissRequest = {
                    showWicarlinkSupportAlert = false
                    showAndroidAutoSupportAlert = true
                }
            )
        }
        if (showAndroidAutoSupportAlert) {
            AndroidAutoSupportAlert(onDismissRequest = { showAndroidAutoSupportAlert = false })
        }
        LaunchedEffect(viewModel) {
            for (event in viewModel.eventChannel) {
                when (event) {
                    Event.ManualMonitorDropdown ->
                        showManualMonitoringSpotlight = true

                    Event.WicarlinkSupportAndAndroidAuto ->
                        showWicarlinkSupportAlert = true
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun TopAppBar(
    onVehicleAdded: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    manualBackgroundButtonModifier: Modifier = Modifier
) {
    val navController = LocalHomeNavController.current
    val currentPath = navController.currentBackStackEntryAsState()
        .value
        ?.destination
        ?.route
        ?.let { Path.from(it) }
    CenterAlignedTopAppBar(
        title = {
            when (currentPath) {
                is Path.Home -> CurrentVehicleDropdown(
                    modifier = Modifier.testTag(carListDropdownMenu),
                    onVehicleAdded = onVehicleAdded,
                )

                is Path.Settings -> Text(
                    text = LocalVehicleComponent.current.vehicleStateFlow.collectAsState().value.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                is Path.PressureSettings -> Text(text = "Pressure")
                is Path.TemperatureSettings -> Text(text = "Temperature")
                is Path.AppSettings -> Text(text = "App settings")
                is Path.TimeSinceUpdate -> Text(text = "Time since last update")
                is Path.PersistentScanning -> Text(text = "Persistent scanning")
                is Path.ActivateScanConditions -> Text(text = "Activate scan conditions")
                is Path.StayActiveDuration -> Text(text = "Stay active")
                is Path.SuspendScanConditions -> Text(text = "Suspend scan conditions")
                is Path.ExceptedWifis -> Text(text = "Excepted WiFis")
                is Path.BindingMethod -> Text(text = "Binding method")
                is Path.Unlocated -> Text(text = "Binding")
                is Path.QrCode, null -> {}
            }
        },
        navigationIcon = {
            when (currentPath) {
                is Path.Settings,
                is Path.PressureSettings,
                is Path.TemperatureSettings,
                is Path.AppSettings,
                is Path.TimeSinceUpdate,
                is Path.PersistentScanning,
                is Path.ActivateScanConditions,
                is Path.StayActiveDuration,
                is Path.SuspendScanConditions,
                is Path.ExceptedWifis,
                is Path.BindingMethod,
                is Path.QrCode,
                is Path.Unlocated -> {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        content = {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.arrow_back_24px),
                                contentDescription = "Go back"
                            )
                        },
                        modifier = Modifier.testTag(backButton)
                    )
                }

                is Path.Home, null -> {}
            }
        },
        actions = {
            var showMenu by remember { mutableStateOf(false) }
            when (currentPath) {
                is Path.Home -> {
                    MonitoringButton(
                        modifier = manualBackgroundButtonModifier
                            .testTag(HomeTags.Actions.manualBackground)
                    )
                    IconButton(
                        onClick = { showMenu = true },
                        Modifier.testTag(HomeTags.Actions.overflow)
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.more_vert_24px),
                            contentDescription = "Show more options",
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.testTag(HomeTags.Overflow.root)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Vehicle settings") },
                            onClick = {
                                showMenu = false
                                navController.navigate("${Path.Settings(currentPath.vehicleUUID)}")
                            },
                            modifier = Modifier.testTag(HomeTags.Overflow.settings)
                        )
                        DropdownMenuItem(
                            text = { Text("App settings") },
                            onClick = {
                                showMenu = false
                                navController.navigate("${Path.AppSettings}")
                            },
                            modifier = Modifier.testTag(HomeTags.Overflow.appSettings)
                        )
                    }
                }

                is Path.Settings,
                is Path.PressureSettings,
                is Path.TemperatureSettings,
                is Path.AppSettings,
                is Path.TimeSinceUpdate,
                is Path.PersistentScanning,
                is Path.ActivateScanConditions,
                is Path.StayActiveDuration,
                is Path.SuspendScanConditions,
                is Path.ExceptedWifis,
                is Path.BindingMethod,
                is Path.QrCode,
                is Path.Unlocated,
                null -> {}
            }
        },
        modifier = modifier
    )
}

@Composable
private fun WicarlinkSupportAlert(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("New sensor support!") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Image(
                        bitmap = ImageBitmap.imageResource(id = R.drawable.lytpms_icon),
                        contentDescription = "LTPMS Icon app",
                    )
                }

                Spacer(Modifier.width(8.dp))
                Text(
                    "Sensors manufactured by \"Wicarlink\" shown within their app \"LYTPMS\" are now " +
                            "supported by TPMS Advanced",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("I want more !")
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun AndroidAutoSupportAlert(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Great news !") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Image(
                        bitmap = ImageBitmap.imageResource(id = R.drawable.android_auto_icon),
                        contentDescription = "Android Auto",
                    )
                }

                Spacer(Modifier.width(8.dp))
                Text(
                    "Android auto is now fully supported! Connect this device to your car radio and open TPMS Advanced",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("OK")
            }
        },
        modifier = modifier,
    )
}

@Suppress("ConstPropertyName")
internal object HomeTags {
    const val backButton = "HomeTags_backButton"
    const val carListDropdownMenu = "HomeTags_carListDropdownMenu"

    object Actions {
        const val manualBackground = "HomeTags_Actions_manualBackground"
        const val overflow = "HomeTags_Actions_overflow"

    }

    object Overflow {
        const val root = "HomeTags_Overflow_root"
        const val settings = "HomeTags_Overflow_settings"
        const val appSettings = "HomeTags_Overflow_appSettings"
    }
}

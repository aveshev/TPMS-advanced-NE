package com.masselis.tpmsadvanced.feature.background.ioc

import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BackgroundViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BeaconsViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.DetectedActivitiesViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.KeepAliveInstructionsViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.ScanStatusAnnouncementsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.AndroidAutoUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BluetoothDevicesUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.ChargingStateUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.DeviceIdleModeUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.KeepAliveInstructionsUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.ScanStatusAnnouncer
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus

@Suppress("unused", "FunctionNaming")
@ContributesTo(AppScope::class)
public interface Bindings {

    @Provides
    @SingleIn(AppScope::class)
    private fun monitoringController(): MonitoringController = MonitoringController()

    @Provides
    @SingleIn(AppScope::class)
    private fun unexpectedStopUseCase(controller: MonitoringController): UnexpectedStopUseCase =
        UnexpectedStopUseCase(controller.isRunning)

    @Provides
    private fun keepAliveInstructionsViewModel(): KeepAliveInstructionsViewModel =
        KeepAliveInstructionsViewModel(KeepAliveInstructionsUseCase())

    @Provides
    private fun backgroundViewModel(controller: MonitoringController): BackgroundViewModel =
        BackgroundViewModel(controller)

    @Provides
    @SingleIn(AppScope::class)
    private fun deviceIdleModeUseCase(): DeviceIdleModeUseCase = DeviceIdleModeUseCase()

    @Provides
    @SingleIn(AppScope::class)
    private fun wifiConnectionUseCase(): WifiConnectionUseCase = WifiConnectionUseCase()

    @OptIn(DelicateCoroutinesApi::class)
    @Provides
    @SingleIn(AppScope::class)
    private fun bluetoothDevicesUseCase(): BluetoothDevicesUseCase =
        BluetoothDevicesUseCase(GlobalScope + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    private fun beaconPresenceUseCase(
        scanner: BluetoothLeScanner,
        bluetoothDevicesUseCase: BluetoothDevicesUseCase,
    ): BeaconPresenceUseCase = BeaconPresenceUseCase(
        scanner,
        // The paired devices are only unknown while Bluetooth is off
        bluetoothDevicesUseCase.paired.map { it != null },
    )

    @Provides
    private fun beaconDiscoveryUseCase(
        scanner: BluetoothLeScanner,
        bluetoothDevicesUseCase: BluetoothDevicesUseCase,
    ): BeaconDiscoveryUseCase = BeaconDiscoveryUseCase(
        scanner,
        bluetoothDevicesUseCase.paired.map { it != null },
    )

    @Provides
    private fun beaconsViewModel(
        appPreferences: AppPreferences,
        beaconPresenceUseCase: BeaconPresenceUseCase,
        beaconDiscoveryUseCase: BeaconDiscoveryUseCase,
    ): BeaconsViewModel = BeaconsViewModel(appPreferences, beaconPresenceUseCase, beaconDiscoveryUseCase)

    @Provides
    @SingleIn(AppScope::class)
    private fun scanSuspensionUseCase(
        appPreferences: AppPreferences,
        deviceIdleModeUseCase: DeviceIdleModeUseCase,
        wifiConnectionUseCase: WifiConnectionUseCase,
        bluetoothDevicesUseCase: BluetoothDevicesUseCase,
    ): ScanSuspensionUseCase = ScanSuspensionUseCase(
        appPreferences,
        deviceIdleModeUseCase,
        wifiConnectionUseCase,
        bluetoothDevicesUseCase,
    )

    @Provides
    @SingleIn(AppScope::class)
    private fun chargingStateUseCase(): ChargingStateUseCase = ChargingStateUseCase()

    @Provides
    @SingleIn(AppScope::class)
    private fun androidAutoUseCase(): AndroidAutoUseCase = AndroidAutoUseCase()

    @OptIn(DelicateCoroutinesApi::class)
    @Provides
    @SingleIn(AppScope::class)
    private fun activityRecognitionUseCase(): ActivityRecognitionUseCase =
        ActivityRecognitionUseCase(GlobalScope + Dispatchers.Default)

    @OptIn(DelicateCoroutinesApi::class)
    @Provides
    @SingleIn(AppScope::class)
    private fun scanPolicyUseCase(
        appPreferences: AppPreferences,
        scanSuspensionUseCase: ScanSuspensionUseCase,
        chargingStateUseCase: ChargingStateUseCase,
        androidAutoUseCase: AndroidAutoUseCase,
        bluetoothDevicesUseCase: BluetoothDevicesUseCase,
        beaconPresenceUseCase: BeaconPresenceUseCase,
    ): ScanPolicyUseCase = ScanPolicyUseCase(
        appPreferences,
        scanSuspensionUseCase,
        chargingStateUseCase,
        androidAutoUseCase,
        bluetoothDevicesUseCase,
        beaconPresenceUseCase,
        GlobalScope + Dispatchers.Default,
    )

    @OptIn(DelicateCoroutinesApi::class)
    @Provides
    @SingleIn(AppScope::class)
    private fun scanStatusAnnouncer(
        appPreferences: AppPreferences,
        scanPolicyUseCase: ScanPolicyUseCase,
    ): ScanStatusAnnouncer = ScanStatusAnnouncer(
        appPreferences,
        scanPolicyUseCase,
        // Text-to-speech and the process lifecycle are used from the main thread
        GlobalScope + Dispatchers.Main,
    )

    @Provides
    private fun persistentScanningViewModel(
        appPreferences: AppPreferences,
        scanPolicyUseCase: ScanPolicyUseCase,
        wifiConnectionUseCase: WifiConnectionUseCase,
        unexpectedStopUseCase: UnexpectedStopUseCase,
        controller: MonitoringController,
    ): PersistentScanningViewModel = PersistentScanningViewModel(
        appPreferences,
        scanPolicyUseCase,
        wifiConnectionUseCase,
        unexpectedStopUseCase,
        controller,
    )

    @Provides
    private fun persistentScanningSettingsViewModel(
        appPreferences: AppPreferences,
        scanPolicyUseCase: ScanPolicyUseCase,
        wifiConnectionUseCase: WifiConnectionUseCase,
        bluetoothDevicesUseCase: BluetoothDevicesUseCase,
        controller: MonitoringController,
    ): PersistentScanningSettingsViewModel = PersistentScanningSettingsViewModel(
        appPreferences,
        scanPolicyUseCase,
        wifiConnectionUseCase,
        bluetoothDevicesUseCase,
        controller,
    )

    @Provides
    private fun detectedActivitiesViewModel(
        appPreferences: AppPreferences,
        activityRecognitionUseCase: ActivityRecognitionUseCase,
    ): DetectedActivitiesViewModel = DetectedActivitiesViewModel(appPreferences, activityRecognitionUseCase)

    @Provides
    private fun scanStatusAnnouncementsViewModel(
        appPreferences: AppPreferences,
    ): ScanStatusAnnouncementsViewModel = ScanStatusAnnouncementsViewModel(appPreferences)

    public val featureBackgroundInternal: Internal

    @Suppress("LongParameterList")
    @Inject
    public class Internal internal constructor(
        internal val appPreferences: AppPreferences,
        internal val controller: MonitoringController,
        internal val unexpectedStopUseCase: UnexpectedStopUseCase,
        internal val scanStatusAnnouncer: () -> ScanStatusAnnouncer,
        internal val backgroundViewModel: () -> BackgroundViewModel,
        internal val persistentScanningViewModel: () -> PersistentScanningViewModel,
        internal val persistentScanningSettingsViewModel: () -> PersistentScanningSettingsViewModel,
        internal val detectedActivitiesViewModel: () -> DetectedActivitiesViewModel,
        internal val keepAliveInstructionsViewModel: () -> KeepAliveInstructionsViewModel,
        internal val scanStatusAnnouncementsViewModel: () -> ScanStatusAnnouncementsViewModel,
        internal val beaconsViewModel: () -> BeaconsViewModel,
    )

    public companion object : Bindings by appGraph as Bindings {
        internal fun PersistentScanningSettingsViewModel() =
            featureBackgroundInternal.persistentScanningSettingsViewModel()

        internal fun BeaconsViewModel() = featureBackgroundInternal.beaconsViewModel()
    }
}

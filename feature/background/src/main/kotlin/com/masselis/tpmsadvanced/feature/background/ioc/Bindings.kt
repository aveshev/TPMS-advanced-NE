package com.masselis.tpmsadvanced.feature.background.ioc

import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringController
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BackgroundViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PersistentScanningViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.ChargingStateUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.DeviceIdleModeUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.ScanPolicyUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.plus

@Suppress("unused", "FunctionNaming")
@ContributesTo(AppScope::class)
public interface Bindings {

    @Provides
    @SingleIn(AppScope::class)
    private fun monitoringController(): MonitoringController = MonitoringController()

    @Provides
    private fun backgroundViewModel(controller: MonitoringController): BackgroundViewModel =
        BackgroundViewModel(controller)

    @Provides
    @SingleIn(AppScope::class)
    private fun deviceIdleModeUseCase(): DeviceIdleModeUseCase = DeviceIdleModeUseCase()

    @Provides
    @SingleIn(AppScope::class)
    private fun wifiConnectionUseCase(): WifiConnectionUseCase = WifiConnectionUseCase()

    @Provides
    @SingleIn(AppScope::class)
    private fun scanSuspensionUseCase(
        appPreferences: AppPreferences,
        deviceIdleModeUseCase: DeviceIdleModeUseCase,
        wifiConnectionUseCase: WifiConnectionUseCase,
    ): ScanSuspensionUseCase = ScanSuspensionUseCase(
        appPreferences,
        deviceIdleModeUseCase,
        wifiConnectionUseCase,
    )

    @Provides
    @SingleIn(AppScope::class)
    private fun chargingStateUseCase(): ChargingStateUseCase = ChargingStateUseCase()

    @OptIn(DelicateCoroutinesApi::class)
    @Provides
    @SingleIn(AppScope::class)
    private fun scanPolicyUseCase(
        appPreferences: AppPreferences,
        scanSuspensionUseCase: ScanSuspensionUseCase,
        chargingStateUseCase: ChargingStateUseCase,
    ): ScanPolicyUseCase = ScanPolicyUseCase(
        appPreferences,
        scanSuspensionUseCase,
        chargingStateUseCase,
        GlobalScope + Dispatchers.Default,
    )

    @Provides
    private fun persistentScanningViewModel(
        appPreferences: AppPreferences,
        scanPolicyUseCase: ScanPolicyUseCase,
        controller: MonitoringController,
    ): PersistentScanningViewModel = PersistentScanningViewModel(
        appPreferences,
        scanPolicyUseCase,
        controller,
    )

    @Provides
    private fun persistentScanningSettingsViewModel(
        appPreferences: AppPreferences,
        wifiConnectionUseCase: WifiConnectionUseCase,
        controller: MonitoringController,
    ): PersistentScanningSettingsViewModel = PersistentScanningSettingsViewModel(
        appPreferences,
        wifiConnectionUseCase,
        controller,
    )

    public val featureBackgroundInternal: Internal

    @Inject
    public class Internal internal constructor(
        internal val appPreferences: AppPreferences,
        internal val backgroundViewModel: () -> BackgroundViewModel,
        internal val persistentScanningViewModel: () -> PersistentScanningViewModel,
        internal val persistentScanningSettingsViewModel: () -> PersistentScanningSettingsViewModel,
    )

    public companion object : Bindings by appGraph as Bindings {
        internal fun PersistentScanningSettingsViewModel() =
            featureBackgroundInternal.persistentScanningSettingsViewModel()
    }
}

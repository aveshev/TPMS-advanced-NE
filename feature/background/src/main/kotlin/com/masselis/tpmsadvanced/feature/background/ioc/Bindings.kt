package com.masselis.tpmsadvanced.feature.background.ioc

import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BackgroundViewModel
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.ScanSuspensionSettingsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.DeviceIdleModeUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.WifiConnectionUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@Suppress("unused", "FunctionNaming")
@ContributesTo(AppScope::class)
public interface Bindings {

    @Provides
    private fun backgroundViewModel(): BackgroundViewModel = BackgroundViewModel()

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
    private fun scanSuspensionSettingsViewModel(
        appPreferences: AppPreferences,
        wifiConnectionUseCase: WifiConnectionUseCase,
    ): ScanSuspensionSettingsViewModel = ScanSuspensionSettingsViewModel(
        appPreferences,
        wifiConnectionUseCase,
    )

    public val featureBackgroundInternal: Internal

    @Inject
    public class Internal internal constructor(
        internal val backgroundViewModel: () -> BackgroundViewModel,
        internal val scanSuspensionSettingsViewModel: () -> ScanSuspensionSettingsViewModel,
    )

    public companion object : Bindings by appGraph as Bindings {
        internal fun ScanSuspensionSettingsViewModel() =
            featureBackgroundInternal.scanSuspensionSettingsViewModel()
    }
}

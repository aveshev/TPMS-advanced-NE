package com.masselis.tpmsadvanced.data.app.interfaces

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.core.content.edit
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.observableStateFlow
import kotlinx.coroutines.flow.MutableStateFlow

public class AppPreferences internal constructor(
    context: Context
) {
    private val sharedPreferences = context.getSharedPreferences(
        "APP",
        Context.MODE_PRIVATE
    )

    public val showSensorId: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_SENSOR_ID", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_SENSOR_ID", newValue) }
    }

    public val showTimeSinceUpdate: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_TIME_SINCE_UPDATE", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_TIME_SINCE_UPDATE", newValue) }
    }

    public val persistentScanning: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("PERSISTENT_SCANNING", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("PERSISTENT_SCANNING", newValue) }
    }

    public val activateOnCableCharging: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_ON_CABLE_CHARGING", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_ON_CABLE_CHARGING", newValue) }
    }

    public val activateOnWirelessCharging: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_ON_WIRELESS_CHARGING", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_ON_WIRELESS_CHARGING", newValue) }
    }

    public val activateOnAndroidAuto: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_ON_ANDROID_AUTO", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_ON_ANDROID_AUTO", newValue) }
    }

    /** Keeps scanning for [stayActiveMinutes] once every activate condition has ended */
    public val stayActive: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("STAY_ACTIVE", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("STAY_ACTIVE", newValue) }
    }

    public val stayActiveMinutes: MutableStateFlow<Int> = observableStateFlow(
        sharedPreferences.getInt("STAY_ACTIVE_MINUTES", DEFAULT_STAY_ACTIVE_MINUTES)
    ) { _, newValue ->
        sharedPreferences.edit { putInt("STAY_ACTIVE_MINUTES", newValue) }
    }

    public val justScan: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("JUST_SCAN", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("JUST_SCAN", newValue) }
    }

    public val suspendScanningInDoze: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SUSPEND_SCANNING_IN_DOZE", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SUSPEND_SCANNING_IN_DOZE", newValue) }
    }

    public val suspendScanningOnWifi: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SUSPEND_SCANNING_ON_WIFI", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SUSPEND_SCANNING_ON_WIFI", newValue) }
    }

    public val wifiExceptionEnabled: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("WIFI_EXCEPTION_ENABLED", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("WIFI_EXCEPTION_ENABLED", newValue) }
    }

    public val exceptedWifiSsids: MutableStateFlow<Set<String>> = observableStateFlow(
        sharedPreferences.getStringSet("EXCEPTED_WIFI_SSIDS", emptySet())!!.toSet()
    ) { _, newValue ->
        sharedPreferences.edit { putStringSet("EXCEPTED_WIFI_SSIDS", newValue) }
    }

    private val packageInfo
        get() = appContext
            .packageManager
            .run {
                if (SDK_INT >= TIRAMISU) {
                    getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    getPackageInfo(appContext.packageName, 0)
                }
            }!!

    public val previousVersionCode: Long? = sharedPreferences
        .getLong("VC", Long.MIN_VALUE)
        .takeIf { it != Long.MIN_VALUE }

    public val currentVersionCode: Long = packageInfo.longVersionCode

    public val isFreshInstallation: Boolean =
        packageInfo.let { it.firstInstallTime == it.lastUpdateTime }

    init {
        if (currentVersionCode != previousVersionCode)
            sharedPreferences.edit { putLong("VC", currentVersionCode) }
    }

    private companion object {
        const val DEFAULT_STAY_ACTIVE_MINUTES = 10
    }
}

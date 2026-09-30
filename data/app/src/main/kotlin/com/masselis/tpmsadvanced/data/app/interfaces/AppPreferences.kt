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

    /**
     * The switch of the debug options: while off, none of them is shown, whatever is selected.
     * On by default when the sensor ID, the only one which predates it, was shown.
     */
    public val debugOptions: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("DEBUG_OPTIONS", sharedPreferences.getBoolean("SHOW_SENSOR_ID", false))
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("DEBUG_OPTIONS", newValue) }
    }

    /** Shows the status byte of the last packet under each tyre, bit by bit */
    public val showSensorFlags: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_SENSOR_FLAGS", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_SENSOR_FLAGS", newValue) }
    }

    /** Shows the activities the phone detects, with their confidence, on the home screen */
    public val showDetectedActivities: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_DETECTED_ACTIVITIES", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_DETECTED_ACTIVITIES", newValue) }
    }

    public val showTimeSinceUpdate: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_TIME_SINCE_UPDATE", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_TIME_SINCE_UPDATE", newValue) }
    }

    /** A voltage getting low or alarming is shown whatever this is set to */
    public val showBatteryVoltage: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SHOW_BATTERY_VOLTAGE", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SHOW_BATTERY_VOLTAGE", newValue) }
    }

    /**
     * Warns about a tyre losing at least [pressureLossMinDrop] kPa while riding. Experimental, so
     * off by default.
     */
    public val pressureLoss: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("PRESSURE_LOSS", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("PRESSURE_LOSS", newValue) }
    }

    /** In kPa */
    public val pressureLossMinDrop: MutableStateFlow<Float> = observableStateFlow(
        sharedPreferences.getFloat("PRESSURE_LOSS_MIN_DROP_KPA", DEFAULT_PRESSURE_LOSS_MIN_DROP)
    ) { _, newValue ->
        sharedPreferences.edit { putFloat("PRESSURE_LOSS_MIN_DROP_KPA", newValue) }
    }

    /** Also shows the loss of a tyre losing too little to be warned about, zero included */
    public val alwaysShowPressureLoss: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ALWAYS_SHOW_PRESSURE_LOSS", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ALWAYS_SHOW_PRESSURE_LOSS", newValue) }
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

    /** Scans while one of [activateBluetoothDevices] is connected */
    public val activateOnBluetooth: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_ON_BLUETOOTH", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_ON_BLUETOOTH", newValue) }
    }

    /**
     * Addresses of the Bluetooth devices picked for [activateOnBluetooth]. A device unpaired since
     * then stays in the set, so that it comes back selected once paired again.
     */
    public val activateBluetoothDevices: MutableStateFlow<Set<String>> = observableStateFlow(
        sharedPreferences.getStringSet("ACTIVATE_BLUETOOTH_DEVICES", emptySet())!!.toSet()
    ) { _, newValue ->
        sharedPreferences.edit { putStringSet("ACTIVATE_BLUETOOTH_DEVICES", newValue) }
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

    /** When off, persistent scanning ignores the activate conditions and always scans */
    public val activateConditions: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("ACTIVATE_CONDITIONS", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("ACTIVATE_CONDITIONS", newValue) }
    }

    /** When off, none of the suspend conditions applies, whatever each one is set to */
    public val suspendConditions: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SUSPEND_CONDITIONS", true)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SUSPEND_CONDITIONS", newValue) }
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

    /** Suspends scanning while one of [suspendBluetoothDevices] is connected */
    public val suspendScanningOnBluetooth: MutableStateFlow<Boolean> = observableStateFlow(
        sharedPreferences.getBoolean("SUSPEND_SCANNING_ON_BLUETOOTH", false)
    ) { _, newValue ->
        sharedPreferences.edit { putBoolean("SUSPEND_SCANNING_ON_BLUETOOTH", newValue) }
    }

    /** Same as [activateBluetoothDevices], for [suspendScanningOnBluetooth] */
    public val suspendBluetoothDevices: MutableStateFlow<Set<String>> = observableStateFlow(
        sharedPreferences.getStringSet("SUSPEND_BLUETOOTH_DEVICES", emptySet())!!.toSet()
    ) { _, newValue ->
        sharedPreferences.edit { putStringSet("SUSPEND_BLUETOOTH_DEVICES", newValue) }
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
        const val DEFAULT_PRESSURE_LOSS_MIN_DROP = 7f
    }
}

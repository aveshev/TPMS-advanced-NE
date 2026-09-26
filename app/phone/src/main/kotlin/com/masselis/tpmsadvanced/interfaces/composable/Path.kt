package com.masselis.tpmsadvanced.interfaces.composable

import java.util.UUID

@Suppress("MemberVisibilityCanBePrivate")
internal sealed interface Path {

    @JvmInline
    value class Home(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/home"
    }

    @JvmInline
    value class Settings(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/settings"
    }

    @JvmInline
    value class PressureSettings(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/settings_pressure"
    }

    @JvmInline
    value class CalibrationSettings(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/settings_calibration"
    }

    @JvmInline
    value class TemperatureSettings(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/settings_temperature"
    }

    data object AppSettings : Path {
        override fun toString(): String = "app_settings"
    }

    data object TimeSinceUpdate : Path {
        override fun toString(): String = "app_settings/time_since_last_update"
    }

    data object PersistentScanning : Path {
        override fun toString(): String = "app_settings/persistent_scanning"
    }

    data object ActivateScanConditions : Path {
        override fun toString(): String = "app_settings/activate_scan_conditions"
    }

    data object StayActiveDuration : Path {
        override fun toString(): String = "app_settings/activate_scan_conditions/stay_active"
    }

    data object ExceptedWifis : Path {
        override fun toString(): String = "app_settings/suspend_scan_conditions/excepted_wifis"
    }

    data object SuspendScanConditions : Path {
        override fun toString(): String = "app_settings/suspend_scan_conditions"
    }

    @JvmInline
    value class BindingMethod(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/binding_method"
    }

    @JvmInline
    value class QrCode(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/qrcode"
    }

    @JvmInline
    value class Unlocated(val vehicleUUID: UUID) : Path {
        override fun toString(): String = "vehicle/$vehicleUUID/unlocated"
    }

    companion object {
        /** Pages that don't depend on a vehicle, their route is fixed */
        private val appPages
            get() = listOf(
                AppSettings,
                TimeSinceUpdate,
                PersistentScanning,
                ActivateScanConditions,
                StayActiveDuration,
                SuspendScanConditions,
                ExceptedWifis,
            )

        @Suppress("NAME_SHADOWING")
        fun from(route: String): Path = when (val page = appPages.firstOrNull { "$it" == route }) {
            null -> route
                .split('/')
                .let { (host, uuid, screen) ->
                    assert(host == "vehicle")
                    val uuid = UUID.fromString(uuid)
                    when (screen) {
                        "home" -> Home(uuid)
                        "settings" -> Settings(uuid)
                        "settings_pressure" -> PressureSettings(uuid)
                        "settings_temperature" -> TemperatureSettings(uuid)
                        "settings_calibration" -> CalibrationSettings(uuid)
                        "binding_method" -> BindingMethod(uuid)
                        "qrcode" -> QrCode(uuid)
                        "unlocated" -> Unlocated(uuid)
                        else -> error("Unrecognized route: \"$route\"")
                    }
                }

            else -> page
        }
    }
}

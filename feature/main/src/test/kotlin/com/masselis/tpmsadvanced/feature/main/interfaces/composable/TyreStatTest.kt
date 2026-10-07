package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

internal class TyreStatTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(locale = "fr-rFR"),
        theme = "android:Theme.Material3.DayNight.NoActionBar",
    )

    @Before
    fun setup() {
        Locale.setDefault(Locale.FRANCE)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
    }

    // Shows the blinking values in their first phase, as previews do, instead of the current one
    private fun snapshot(content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true, content)
    }

    @Test
    fun notDetected() {
        snapshot {
            TyreStatNotDetectedPreview()
        }
    }

    @Test
    fun normal() {
        snapshot {
            TyreStatNormalPreview()
        }
    }

    @Test
    fun calibrated() {
        snapshot {
            TyreStatCalibratedPreview()
        }
    }

    @Test
    fun alerting() {
        snapshot {
            TyreStatAlertingPreview()
        }
    }

    @Test
    fun pressureAlerting() {
        snapshot {
            TyreStatPressureAlertingPreview()
        }
    }

    @Test
    fun temperatureAlerting() {
        snapshot {
            TyreStatTemperatureAlertingPreview()
        }
    }

    @Test
    fun pressureLoss() {
        snapshot {
            TyreStatPressureLossPreview()
        }
    }
}

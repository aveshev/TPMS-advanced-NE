package com.masselis.tpmsadvanced.feature.unit.interfaces

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

@Preview
@Composable
internal fun UnitsPreview() {
    SettingsGroup {
        UnitsSettingsItems(
            viewModel = mockk {
                every { pressure } returns MutableStateFlow(BAR)
                every { temperature } returns MutableStateFlow(CELSIUS)
            }
        )
    }
}

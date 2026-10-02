package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BeaconsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.BeaconsViewModel

/** The signal from which a beacon counts as nearby, to tune how far from the vehicle it triggers */
@Composable
public fun BeaconMinRssiSettingsItem(modifier: Modifier = Modifier): Unit =
    BeaconMinRssiSettingsItem(modifier, viewModel { BeaconsViewModel() })

@Composable
internal fun BeaconMinRssiSettingsItem(
    modifier: Modifier = Modifier,
    viewModel: BeaconsViewModel = viewModel { BeaconsViewModel() },
) {
    val minRssi by viewModel.minRssi.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        TextSettingsItem(
            headline = "Beacon signal threshold",
            supporting = "$minRssi dBm, from which a beacon counts as nearby",
            onClick = { expanded = true },
            modifier = Modifier.testTag(BeaconMinRssiSettingsItemTags.minRssi),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text("$option dBm") },
                    onClick = {
                        viewModel.minRssi.value = option
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == minRssi) Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                            contentDescription = "Selected",
                        )
                    },
                    modifier = Modifier.testTag("${BeaconMinRssiSettingsItemTags.option}_$option"),
                )
            }
        }
    }
}

// From right next to the phone to a few meters away
@Suppress("MagicNumber")
private val OPTIONS = (-60 downTo -100 step 5).toList()

@Suppress("ConstPropertyName")
internal object BeaconMinRssiSettingsItemTags {
    const val minRssi = "BeaconMinRssiSettingsItemTags_minRssi"
    const val option = "BeaconMinRssiSettingsItemTags_option"
}

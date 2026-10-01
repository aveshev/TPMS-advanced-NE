package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.ActionSettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsItem
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.core.ui.rememberBluetoothState
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.SettingsOnScreenEffect
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.BeaconsViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings.Companion.BeaconsViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase.Device
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The page opened from the "Add a beacon" item of [Beacons]: every device advertising around, to
 * add as a beacon. Scans for as long as the page is seen.
 */
@Composable
public fun BeaconScan(modifier: Modifier = Modifier): Unit =
    BeaconScan(modifier, viewModel { BeaconsViewModel() })

@Composable
internal fun BeaconScan(
    modifier: Modifier = Modifier,
    viewModel: BeaconsViewModel = viewModel { BeaconsViewModel() },
) {
    SettingsOnScreenEffect()
    // Stops scanning once the page isn't seen any more, the screen being off included
    val devices by viewModel.discovered.collectAsStateWithLifecycle(initialValue = emptyList())
    val beacons by viewModel.beacons.collectAsState()
    val bluetoothState = rememberBluetoothState()
    BeaconScan(
        devices = devices.takeIf { bluetoothState.isEnabled },
        added = beacons.map { it.address }.toSet(),
        onAdd = viewModel::add,
        onTurnOnBluetooth = bluetoothState::askEnable,
        modifier = modifier,
    )
}

@Suppress("MaxLineLength")
@Composable
private fun BeaconScan(
    // Null while Bluetooth is off
    devices: List<Device>?,
    added: Set<String>,
    onAdd: (Device) -> Unit,
    onTurnOnBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(
        "Hold the phone next to the beacon: the devices with the strongest signal come first. A beacon must advertise all the time, every second or more often, and keep its address."
    )
    SettingsSectionHeader("Devices around")
    SettingsGroup {
        when {
            devices == null -> TextSettingsItem(
                headline = "Bluetooth is off",
                supporting = "Turn it on to look for beacons",
                onClick = onTurnOnBluetooth,
                modifier = Modifier.testTag(BeaconScanTags.bluetoothOff),
            )

            devices.isEmpty() -> SettingsItem { Text("Searching…") }

            else -> devices.forEach { device ->
                val isAdded = device.address in added
                ActionSettingsItem(
                    headline = device.name ?: "Unnamed",
                    supporting = listOfNotNull(
                        device.address,
                        "${device.rssi} dBm",
                        device.period?.let { "every ${it.asSeconds()}" },
                        "Tyre sensor".takeIf { device.isTyreSensor },
                        "Address may change".takeIf { device.mayChangeAddress },
                        "Added".takeIf { isAdded },
                    ).joinToString(" · "),
                    modifier = Modifier.testTag("${BeaconScanTags.device}_${device.address}"),
                ) {
                    if (isAdded) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                            contentDescription = "Added",
                            // Where the button would be
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        IconButton(onClick = { onAdd(device) }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.add_24px),
                                contentDescription = "Add ${device.name ?: device.address}",
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "0.30 s" */
@Suppress("MagicNumber")
private fun Duration.asSeconds() = String.format(Locale.ROOT, "%.2f s", inWholeMilliseconds / 1000.0)

@Suppress("ConstPropertyName")
internal object BeaconScanTags {
    const val device = "BeaconScanTags_device"
    const val bluetoothOff = "BeaconScanTags_bluetoothOff"
}

@Suppress("MagicNumber")
private val previewDevices = listOf(
    Device("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", -62, 303.milliseconds, isTyreSensor = false),
    Device("C4:CD:82:63:55:15", "RE6603100142", -82, 104.milliseconds, isTyreSensor = false),
    Device("4A:1B:2C:3D:4E:5F", null, -88, 52.milliseconds, isTyreSensor = false),
    Device("80:EA:CA:10:20:30", null, -90, null, isTyreSensor = true),
)

@Preview
@Composable
internal fun BeaconScanListPreview() = BeaconScan(
    devices = previewDevices,
    added = setOf("EE:64:A3:12:38:1A"),
    onAdd = {},
    onTurnOnBluetooth = {},
)

@Preview
@Composable
internal fun BeaconScanSearchingPreview() = BeaconScan(
    devices = emptyList(),
    added = emptySet(),
    onAdd = {},
    onTurnOnBluetooth = {},
)

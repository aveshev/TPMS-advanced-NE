package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
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
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase.Device
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase.Page
import java.util.Locale
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The page opened from the "Add a beacon" item of [Beacons]: the named devices advertising around,
 * to add as a beacon. Searches for a few seconds first, then lists them in an order that only
 * changes when reloading. Scans for as long as the page is seen.
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
    // Pauses scanning once the page isn't seen any more, the screen being off included
    LifecycleStartEffect(viewModel) {
        viewModel.scanPageVisible.value = true
        onStopOrDispose { viewModel.scanPageVisible.value = false }
    }
    val page by viewModel.scanPage.collectAsState()
    val beacons by viewModel.beacons.collectAsState()
    val bluetoothState = rememberBluetoothState()
    BeaconScan(
        page = page.takeIf { bluetoothState.isEnabled },
        added = beacons.map { it.address }.toSet(),
        onAdd = viewModel::add,
        onReload = viewModel::reload,
        onTurnOnBluetooth = bluetoothState::askEnable,
        modifier = modifier,
    )
}

@Suppress("MaxLineLength", "LongMethod")
@Composable
private fun BeaconScan(
    // Null while Bluetooth is off
    page: Page?,
    added: Set<String>,
    onAdd: (Device) -> Unit,
    onReload: () -> Unit,
    onTurnOnBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SettingsIntro(
        "Hold the phone next to the beacon: the devices with the strongest signal come first. A beacon must advertise all the time, every second or more often, keep its address, and send a name."
    )
    SettingsSectionHeader("Devices around")
    SettingsGroup {
        when (page) {
            null -> TextSettingsItem(
                headline = "Bluetooth is off",
                supporting = "Turn it on to look for beacons",
                onClick = onTurnOnBluetooth,
                modifier = Modifier.testTag(BeaconScanTags.bluetoothOff),
            )

            is Page.Searching -> SettingsItem(Modifier.testTag(BeaconScanTags.searching)) {
                Column {
                    Text(
                        "Searching… ${ceil(page.remaining.inWholeMilliseconds / 1000.0).toInt()} s, ${page.found} found",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    LinearProgressIndicator(
                        progress = { 1f - (page.remaining / BeaconDiscoveryUseCase.SEARCH).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
            }

            is Page.Listed -> {
                SettingsItem(Modifier.testTag(BeaconScanTags.notShown)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${page.notShown.devices} not shown",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (page.notShown > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onReload, modifier = Modifier.testTag(BeaconScanTags.reload)) {
                            Text("Reload")
                        }
                    }
                }
                if (page.devices.isEmpty()) SettingsItem { Text("No named device nearby") }
                page.devices.forEach { device ->
                    val isAdded = device.address in added
                    ActionSettingsItem(
                        headline = device.name,
                        supporting = listOfNotNull(device.address, "Added".takeIf { isAdded })
                            .joinToString(" · ") + "\n" + listOfNotNull(
                            "${device.rssi} dBm",
                            device.period?.let { "every ${it.asSeconds()}" },
                            "Not heard lately".takeIf { device.isQuiet },
                            "Weak signal".takeIf { device.isQuiet.not() && device.isStrong.not() },
                        ).joinToString(" · "),
                        // Too weak or gone for now, may come back
                        enabled = device.isCandidate,
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
                                    contentDescription = "Add ${device.name}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "1 new device", "3 new devices" */
private val Int.devices
    get() = if (this == 1) "1 new device" else "$this new devices"

/** "0.3 s" */
@Suppress("MagicNumber")
private fun Duration.asSeconds() = String.format(Locale.ROOT, "%.1f s", inWholeMilliseconds / 1000.0)

@Suppress("ConstPropertyName")
internal object BeaconScanTags {
    const val device = "BeaconScanTags_device"
    const val bluetoothOff = "BeaconScanTags_bluetoothOff"
    const val searching = "BeaconScanTags_searching"
    const val notShown = "BeaconScanTags_notShown"
    const val reload = "BeaconScanTags_reload"
}

@Suppress("MagicNumber")
private val previewDevices = listOf(
    Device("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", -62, 303.milliseconds, isStrong = true, isQuiet = false),
    Device("C4:CD:82:63:55:15", "RE6603100142", -78, 104.milliseconds, isStrong = true, isQuiet = false),
    Device("4A:1B:2C:3D:4E:5F", "Galaxy Watch", -91, 52.milliseconds, isStrong = false, isQuiet = false),
    Device("F3:34:67:CA:7B:EB", "CFMOTOR_f33467ca7beb", -84, null, isStrong = true, isQuiet = true),
)

@Suppress("MagicNumber")
@Preview
@Composable
internal fun BeaconScanListPreview() = BeaconScan(
    page = Page.Listed(previewDevices, notShown = 2),
    added = setOf("EE:64:A3:12:38:1A"),
    onAdd = {},
    onReload = {},
    onTurnOnBluetooth = {},
)

@Suppress("MagicNumber")
@Preview
@Composable
internal fun BeaconScanSearchingPreview() = BeaconScan(
    page = Page.Searching(3.seconds, found = 4),
    added = emptySet(),
    onAdd = {},
    onReload = {},
    onTurnOnBluetooth = {},
)

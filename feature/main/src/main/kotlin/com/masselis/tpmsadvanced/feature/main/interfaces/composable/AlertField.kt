package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.OutlinedTextFieldHorizontalPadding
import com.masselis.tpmsadvanced.core.ui.textWidth
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.CELSIUS
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.FAHRENHEIT
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.toPressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.toTemperature
import com.masselis.tpmsadvanced.feature.main.R

/** Material3's default IconButton touch target size. */
private val IconButtonWidth = 48.dp

/** Blurs the field once the keyboard is dismissed, instead of staying active until another field is tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClearFocusOnImeDismiss(isFocused: Boolean) {
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible.not() && isFocused) focusManager.clearFocus()
    }
}

@Composable
internal fun PressureRangeField(
    minMaxRange: ClosedFloatingPointRange<Pressure>,
    values: ClosedFloatingPointRange<Pressure>,
    onValue: (ClosedFloatingPointRange<Pressure>) -> Unit,
    openInfo: () -> Unit,
    unit: PressureUnit,
    modifier: Modifier = Modifier,
    title: String = "Expected pressure range:",
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = openInfo,
                content = {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.info_24px),
                        contentDescription = "More information about this alert"
                    )
                }
            )
        }
        PressureMinMaxFields(
            minMaxRange = minMaxRange,
            values = values,
            onValue = onValue,
            unit = unit,
        )
    }
}

@Composable
private fun PressureMinMaxFields(
    minMaxRange: ClosedFloatingPointRange<Pressure>,
    values: ClosedFloatingPointRange<Pressure>,
    onValue: (ClosedFloatingPointRange<Pressure>) -> Unit,
    unit: PressureUnit,
) {
    val fieldWidth = textWidth("149.9") + OutlinedTextFieldHorizontalPadding + 4.dp + textWidth(unit.string())
    val groupWidth = maxOf(textWidth("Min"), textWidth("Max")) + 8.dp + fieldWidth
    val gapBetweenGroups = textWidth("mm")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val halfWidth = maxWidth / 2
        // Max always starts at halfWidth, so Min's group must end well before it, not just fit somewhere overall.
        if (groupWidth + gapBetweenGroups <= halfWidth) {
            Box(Modifier.fillMaxWidth()) {
                PressureField(
                    label = "Min",
                    minMaxRange = minMaxRange,
                    value = values.start,
                    onValue = { onValue(it..values.endInclusive) },
                    unit = unit,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                PressureField(
                    label = "Max",
                    minMaxRange = minMaxRange,
                    value = values.endInclusive,
                    onValue = { onValue(values.start..it) },
                    unit = unit,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = halfWidth)
                )
            }
        } else {
            Column {
                PressureField(
                    label = "Min",
                    minMaxRange = minMaxRange,
                    value = values.start,
                    onValue = { onValue(it..values.endInclusive) },
                    unit = unit,
                )
                PressureField(
                    label = "Max",
                    minMaxRange = minMaxRange,
                    value = values.endInclusive,
                    onValue = { onValue(values.start..it) },
                    unit = unit,
                )
            }
        }
    }
}

@Composable
private fun PressureField(
    label: String,
    minMaxRange: ClosedFloatingPointRange<Pressure>,
    value: Pressure,
    onValue: (Pressure) -> Unit,
    unit: PressureUnit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(label, modifier = Modifier.widthIn(min = maxOf(textWidth("Min"), textWidth("Max"))))
        Spacer(Modifier.width(8.dp))
        PressureTextField(
            minMaxRange = minMaxRange,
            value = value,
            onValue = onValue,
            unit = unit,
        )
    }
}

@Composable
private fun PressureTextField(
    minMaxRange: ClosedFloatingPointRange<Pressure>,
    value: Pressure,
    onValue: (Pressure) -> Unit,
    unit: PressureUnit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        var isFocused by remember { mutableStateOf(false) }
        var text by remember { mutableStateOf(value.numberString(unit)) }
        if (isFocused.not()) text = value.numberString(unit)
        ClearFocusOnImeDismiss(isFocused)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .width(textWidth("149.9") + OutlinedTextFieldHorizontalPadding)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused.not()) {
                        text.toFloatOrNull()
                            ?.toPressure(unit)
                            ?.coerceIn(minMaxRange)
                            ?.also(onValue)
                            ?.also { text = it.numberString(unit) }
                            ?: run { text = value.numberString(unit) }
                    }
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.width(4.dp))
        Text(unit.string())
    }
}

@Preview
@Composable
internal fun PressureRangeFieldPreview() {
    PressureRangeField(
        values = 1.5f.bar..2.5f.bar,
        minMaxRange = 0.5f.bar..5f.bar,
        onValue = {},
        openInfo = {},
        unit = PressureUnit.BAR,
    )
}

@Composable
internal fun TemperatureField(
    label: String,
    minMaxRange: ClosedFloatingPointRange<Temperature>,
    value: Temperature,
    onValue: (Temperature) -> Unit,
    openInfo: () -> Unit,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val labelWidth = textWidth("Normal")
    val boxWidth = textWidth("302") + OutlinedTextFieldHorizontalPadding
    val idealGap = textWidth("mmm")
    val fixedContentWidth = labelWidth + boxWidth + 4.dp + textWidth(unit.symbol()) + IconButtonWidth
    BoxWithConstraints(modifier) {
        val gap = if (fixedContentWidth + idealGap <= maxWidth)
            idealGap
        else
            (maxWidth - fixedContentWidth).coerceAtLeast(0.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.widthIn(min = labelWidth), maxLines = 1)
            Spacer(Modifier.width(gap))
            var isFocused by remember { mutableStateOf(false) }
            var text by remember { mutableStateOf(value.numberString(unit)) }
            if (isFocused.not()) text = value.numberString(unit)
            ClearFocusOnImeDismiss(isFocused)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .width(boxWidth)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused.not()) {
                            text.toFloatOrNull()
                                ?.toTemperature(unit)
                                ?.coerceIn(minMaxRange)
                                ?.also(onValue)
                                ?.also { text = it.numberString(unit) }
                                ?: run { text = value.numberString(unit) }
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.width(4.dp))
            Text(unit.symbol())
            IconButton(
                onClick = openInfo,
                content = {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.info_24px),
                        contentDescription = "More information about this alert",
                    )
                }
            )
        }
    }
}

private fun TemperatureUnit.symbol(): String = when (this) {
    CELSIUS -> "°C"
    FAHRENHEIT -> "°F"
}

@Preview
@Composable
internal fun TemperatureFieldPreview() {
    TemperatureField(
        label = "Normal",
        minMaxRange = 5f.celsius..80f.celsius,
        value = 20f.celsius,
        onValue = {},
        openInfo = { },
        unit = TemperatureUnit.CELSIUS
    )
}

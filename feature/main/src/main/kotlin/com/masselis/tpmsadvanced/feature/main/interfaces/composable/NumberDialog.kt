package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.feature.main.R
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Edits a number expressed in the user's [unit], typed in or nudged by [step] with the −/+ buttons.
 * Confirming is only possible once the text is a number within [range], the range being shown
 * below the field instead of silently clamping what the user typed.
 */
@Suppress("LongMethod", "MaxLineLength")
@Composable
internal fun NumberDialog(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    format: (Float) -> String,
    onConfirm: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Starts fully selected so typing replaces the value
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(format(value).let { TextFieldValue(it, TextRange(0, it.length)) })
    }
    // format() follows the locale's decimal separator, both are accepted back
    val parsed = text.text.replace(',', '.').toFloatOrNull()
    // The bounds are displayed rounded, typing them as displayed must still be accepted
    val tolerance = step / 100
    val isValid = parsed != null && parsed >= range.start - tolerance && parsed <= range.endInclusive + tolerance
    val current = parsed ?: value
    val confirm = { parsed?.takeIf { isValid }?.coerceIn(range)?.also(onConfirm) }
    val nudge = { next: Float ->
        next
            .times(step)
            .coerceIn(range)
            .let(format)
            .also { text = TextFieldValue(it, TextRange(it.length)) }
    }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        // The small offset keeps a value sitting on a step, despite float errors, on it
                        onClick = { nudge(ceil(current / step - STEP_EPSILON) - 1) },
                        enabled = current > range.start + tolerance,
                    ) {
                        Icon(ImageVector.vectorResource(R.drawable.remove_24px), "Decrease")
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        isError = isValid.not(),
                        suffix = { Text(unit) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )
                    IconButton(
                        onClick = { nudge(floor(current / step + STEP_EPSILON) + 1) },
                        enabled = current < range.endInclusive - tolerance,
                    ) {
                        Icon(ImageVector.vectorResource(R.drawable.add_24px), "Increase")
                    }
                }
                Text(
                    text = "From ${format(range.start)} to ${format(range.endInclusive)} $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    // Aligned with the field's text, after the decrease button
                    modifier = Modifier.padding(start = 64.dp, top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = isValid) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        },
        modifier = modifier,
    )
    LaunchedEffect(Unit) {
        // Same delay as the add vehicle dialog: the dialog's window must be shown to take the focus
        delay(200)
        focusRequester.requestFocus()
    }
}

private const val STEP_EPSILON = 0.01f

@Preview
@Composable
internal fun NumberDialogPreview() {
    NumberDialog(
        title = "Minimum pressure",
        value = 2.2f,
        range = 0f..2.6f,
        step = 0.1f,
        unit = "bar",
        format = { "%.1f".format(it) },
        onConfirm = {},
        onDismissRequest = {},
    )
}

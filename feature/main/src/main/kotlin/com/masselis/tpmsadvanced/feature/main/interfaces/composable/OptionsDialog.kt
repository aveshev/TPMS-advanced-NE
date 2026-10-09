package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.masselis.tpmsadvanced.core.ui.isWideWindow

private val PADDING = 24.dp

/**
 * An [AlertDialog] whose [text] scrolls when it doesn't fit. In a wide window, see
 * [isWideWindow], the height is short: the title and buttons go to the left, the text takes the
 * whole height on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionsDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    if (isWideWindow().not()) AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { text() } },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        modifier = modifier,
    )
    else BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        // Wider than a dialog usually is, for both sides
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier
            .padding(horizontal = 48.dp, vertical = PADDING)
            .widthIn(max = 720.dp),
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PADDING),
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(PADDING),
            ) {
                Column(
                    Modifier
                        .weight(2f)
                        .fillMaxHeight()
                ) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
                Column(
                    Modifier
                        .weight(3f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                    }
                }
            }
        }
    }
}

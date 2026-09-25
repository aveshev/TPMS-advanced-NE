package com.masselis.tpmsadvanced.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** The title above a [SettingsGroup] */
@Composable
public fun SettingsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
): Unit = Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
)

/**
 * Settings items drawn like the Android settings app: one card per item, separated by a thin gap,
 * the group's outer corners being rounder than the inner ones.
 */
@Composable
public fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
): Unit = Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp)),
    content = content,
)

/** A [SettingsGroup] item with a switch, the whole item toggles it */
@Composable
public fun SwitchSettingsItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
        .padding(start = 16.dp),
) {
    ItemTexts(headline, listOfNotNull(supporting?.let(::AnnotatedString)), Modifier.enabledAlpha(enabled))
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/**
 * A [SettingsGroup] item that both toggles a feature and, once it is on, opens the page holding
 * the feature's details: the text and the chevron open the page, the switch toggles. With
 * [openableWhenOff], the page opens whatever the switch is.
 *
 * [supporting] holds alternatives, from the most detailed to the shortest: the first one that fits
 * a single line is shown, the last one being cut if none does.
 */
@Composable
public fun SwitchNavigationSettingsItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: List<AnnotatedString> = emptyList(),
    enabled: Boolean = true,
    openableWhenOff: Boolean = false,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.settingsItem(MaterialTheme.colorScheme.settingsItem),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .weight(1f)
            .clickable(enabled = enabled && (checked || openableWhenOff), onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
    ) {
        ItemTexts(headline, supporting, Modifier.enabledAlpha(enabled), singleLineSupporting = true)
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.chevron_right_24px),
            contentDescription = "Open $headline",
            modifier = Modifier
                .size(32.dp)
                .enabledAlpha(enabled && (checked || openableWhenOff)),
        )
    }
    VerticalDivider(Modifier.height(40.dp))
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/**
 * A [SettingsGroup] item with a button at its end, such as one removing the item. [struckOut]
 * greys out and strikes through the headline alone, without changing the item's height, the
 * [action] staying usable (e.g. to undo a removal).
 */
@Composable
public fun ActionSettingsItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    struckOut: Boolean = false,
    action: @Composable () -> Unit,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .padding(start = 16.dp, end = 4.dp),
) {
    ItemTexts(
        headline = headline,
        supporting = listOfNotNull(supporting?.let(::AnnotatedString)),
        headlineModifier = Modifier.enabledAlpha(struckOut.not()),
        headlineDecoration = TextDecoration.LineThrough.takeIf { struckOut },
    )
    action()
}

/** A [SettingsGroup] item of a single choice list, the whole item selects it */
@Composable
public fun RadioSettingsItem(
    headline: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(end = 16.dp),
) {
    RadioButton(
        selected = selected,
        onClick = null,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ItemTexts(headline, emptyList())
}

/** The explanation at the top of a settings page, above its first [SettingsSectionHeader] */
@Composable
public fun SettingsIntro(
    text: String,
    modifier: Modifier = Modifier,
): Unit = Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
)

/** Headline and supporting text of an item, taking the row's remaining width */
@Composable
private fun RowScope.ItemTexts(
    headline: String,
    supporting: List<AnnotatedString>,
    modifier: Modifier = Modifier,
    singleLineSupporting: Boolean = false,
    headlineModifier: Modifier = Modifier,
    headlineDecoration: TextDecoration? = null,
) = Column(
    modifier = modifier
        .weight(1f)
        .padding(vertical = 16.dp),
) {
    Text(
        text = headline,
        style = MaterialTheme.typography.bodyLarge,
        textDecoration = headlineDecoration,
        modifier = headlineModifier,
    )
    if (supporting.isEmpty()) return@Column
    // Same breathing room as the system settings between the two texts
    Spacer(Modifier.height(4.dp))
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (singleLineSupporting.not()) {
        supporting.forEach { Text(it, style = style, color = color) }
        return@Column
    }
    BoxWithConstraints {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        Text(
            text = supporting.firstOrNull { candidate ->
                measurer
                    .measure(candidate, style, maxLines = 1, constraints = Constraints(maxWidth = width))
                    .hasVisualOverflow
                    .not()
            } ?: supporting.last(),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A [SettingsGroup] item holding anything else than a switch */
@Composable
public fun SettingsItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
): Unit = Box(
    modifier = modifier
        .settingsItem(MaterialTheme.colorScheme.settingsItem)
        .padding(horizontal = 16.dp, vertical = 12.dp),
) {
    content()
}

private val ColorScheme.settingsItem: Color
    get() = surfaceColorAtElevation(3.dp)

private fun Modifier.settingsItem(color: Color): Modifier = this
    .fillMaxWidth()
    .clip(RoundedCornerShape(4.dp))
    .background(color)

private fun Modifier.enabledAlpha(enabled: Boolean): Modifier = alpha(if (enabled) 1f else DISABLED_ALPHA)

private const val DISABLED_ALPHA = 0.38f

@Preview
@Composable
internal fun SettingsGroupPreview() {
    Column {
        SettingsSectionHeader("Section")
        SettingsGroup {
            SwitchSettingsItem(headline = "Switch", checked = true, onCheckedChange = {})
            SwitchSettingsItem(
                headline = "Disabled switch",
                supporting = "With a supporting text",
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
            SwitchNavigationSettingsItem(
                headline = "Feature with details",
                supporting = listOf("A long summary of every detail of the feature", "A short summary")
                    .map(::AnnotatedString),
                checked = true,
                onCheckedChange = {},
                onClick = {},
            )
            RadioSettingsItem(headline = "Choice", selected = true, onClick = {})
            SettingsItem { Text("Anything else") }
        }
    }
}

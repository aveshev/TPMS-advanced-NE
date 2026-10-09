package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable.ExitToken
import com.masselis.tpmsadvanced.core.androidtest.onEnterAndOnExit

@OptIn(ExperimentalTestApi::class)
public class ManageSensorDialog private constructor(
    composeTestRule: ComposeTestRule
) :
    ComposeTestRule by composeTestRule,
    EnterExitComposable<ManageSensorDialog> by onEnterAndOnExit(
        { composeTestRule.waitUntilExactlyOneExists(hasTestTag(TyreTapAreaTags.manageDialog)) },
        { composeTestRule.waitUntilDoesNotExist(hasTestTag(TyreTapAreaTags.manageDialog)) },
    ) {

    /** Taps delete, then confirms it, or cancels it when not [confirm] */
    public fun delete(confirm: Boolean = true): ExitToken<ManageSensorDialog> {
        onNodeWithTag(TyreTapAreaTags.delete).performClick()
        waitUntilExactlyOneExists(hasTestTag(TyreTapAreaTags.deleteDialog))
        onNodeWithTag(if (confirm) TyreTapAreaTags.confirmDelete else TyreTapAreaTags.cancelDelete).performClick()
        waitUntilDoesNotExist(hasTestTag(TyreTapAreaTags.deleteDialog))
        return exitToken
    }

    /** Starts moving the sensor, see `ManageSensors` */
    public fun move(): ExitToken<ManageSensorDialog> {
        onNodeWithTag(TyreTapAreaTags.move).performClick()
        return exitToken
    }

    public fun cancel(): ExitToken<ManageSensorDialog> {
        onNodeWithTag(TyreTapAreaTags.manageCancel).performClick()
        return exitToken
    }

    public companion object {
        context(rule: ComposeTestRule)
        public operator fun invoke(): ManageSensorDialog = ManageSensorDialog(rule)
    }
}

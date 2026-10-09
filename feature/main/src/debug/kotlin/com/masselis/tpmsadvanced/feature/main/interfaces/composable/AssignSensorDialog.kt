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
public class AssignSensorDialog private constructor(
    composeTestRule: ComposeTestRule
) :
    ComposeTestRule by composeTestRule,
    EnterExitComposable<AssignSensorDialog> by onEnterAndOnExit(
        { composeTestRule.waitUntilExactlyOneExists(hasTestTag(AssignSensorDialogTags.root)) },
        { composeTestRule.waitUntilDoesNotExist(hasTestTag(AssignSensorDialogTags.root)) },
    ) {

    public fun assertDetectedIsOffered() {
        waitUntilExactlyOneExists(hasTestTag(AssignSensorDialogTags.detected))
    }

    public fun assignDetected(): ExitToken<AssignSensorDialog> {
        onNodeWithTag(AssignSensorDialogTags.detected).performClick()
        return exitToken
    }

    /** Leaves for the scan of the sensors around, see `BluetoothAssign` */
    public fun scanBluetooth(): ExitToken<AssignSensorDialog> {
        onNodeWithTag(AssignSensorDialogTags.bluetooth).performClick()
        return exitToken
    }

    public fun cancel(): ExitToken<AssignSensorDialog> {
        onNodeWithTag(AssignSensorDialogTags.cancel).performClick()
        return exitToken
    }

    public companion object {
        context(rule: ComposeTestRule)
        public operator fun invoke(): AssignSensorDialog = AssignSensorDialog(rule)
    }
}

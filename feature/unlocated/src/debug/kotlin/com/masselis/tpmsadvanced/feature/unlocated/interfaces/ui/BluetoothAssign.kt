package com.masselis.tpmsadvanced.feature.unlocated.interfaces.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable.ExitToken
import com.masselis.tpmsadvanced.core.androidtest.onEnterAndOnExit

/** The steps of assigning a sensor by Bluetooth, left by the top bar's [backButtonTag] */
@OptIn(ExperimentalTestApi::class)
public class BluetoothAssign private constructor(
    private val backButtonTag: String,
    composeTestRule: ComposeTestRule,
) :
    ComposeTestRule by composeTestRule,
    EnterExitComposable<BluetoothAssign> by onEnterAndOnExit(
        { composeTestRule.waitUntilExactlyOneExists(hasTestTag(BluetoothAssignTags.root)) },
        { composeTestRule.waitUntilDoesNotExist(hasTestTag(BluetoothAssignTags.root)) },
    ) {

    public fun inHand() {
        onNodeWithTag(BluetoothAssignTags.inHand).performClick()
    }

    public fun onWheel() {
        onNodeWithTag(BluetoothAssignTags.onWheel).performClick()
    }

    /** Waits for [count] sensors heard on a wheel, see AssignStep */
    public fun waitOnWheelFound(count: Int) {
        waitUntilExactlyOneExists(hasText(if (count == 1) "1 on-wheel sensor found" else "$count on-wheel sensors found"))
    }

    public fun next() {
        waitUntilExactlyOneExists(hasTestTag(BluetoothAssignTags.next) and isEnabled())
        onNodeWithTag(BluetoothAssignTags.next).performClick()
    }

    /** Several sensors found: on to taking the sensor off the wheel to tell which one */
    public fun checkAgain() {
        waitUntilExactlyOneExists(hasTestTag(BluetoothAssignTags.checkAgain))
        onNodeWithTag(BluetoothAssignTags.checkAgain).performClick()
    }

    public fun waitTakeOff() {
        waitUntilExactlyOneExists(hasText("Listening for off-wheel sensors…"))
    }

    public fun leave(): ExitToken<BluetoothAssign> {
        onNodeWithTag(backButtonTag).performClick()
        return exitToken
    }

    public companion object {
        context(rule: ComposeTestRule)
        public operator fun invoke(backButtonTag: String): BluetoothAssign = BluetoothAssign(backButtonTag, rule)
    }
}

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The rules of a [NumberDialog], kept apart from the composable to be unit tested: a number typed
 * with either decimal separator is accepted within [range], and the −/+ buttons move it to the
 * previous or next multiple of [step].
 */
internal class NumberInput(
    private val range: ClosedFloatingPointRange<Float>,
    private val step: Float,
) {
    // The bounds are displayed rounded, typing them as displayed must still be accepted
    private val tolerance = step * TOLERANCE

    /** Both decimal separators are read, the dialog formats values with the locale's one */
    fun parse(text: String): Float? = text
        .trim()
        .replace(',', '.')
        .toFloatOrNull()

    /** The value to confirm, null while [text] isn't a number within the range */
    fun accepted(text: String): Float? = parse(text)
        ?.takeIf { it >= range.start - tolerance && it <= range.endInclusive + tolerance }
        ?.coerceIn(range)

    fun canDecrease(current: Float): Boolean = current > range.start + tolerance

    fun canIncrease(current: Float): Boolean = current < range.endInclusive - tolerance

    // The small offsets keep a value sitting on a step, despite float errors, on it: it then moves
    // by a whole step instead of snapping to itself
    fun decreased(current: Float): Float = ceil(current / step - STEP_EPSILON)
        .minus(1)
        .times(step)
        .coerceIn(range)

    fun increased(current: Float): Float = floor(current / step + STEP_EPSILON)
        .plus(1)
        .times(step)
        .coerceIn(range)

    private companion object {
        const val STEP_EPSILON = 0.01f

        /** A hundredth of a step, finer than any rounding the dialog displays */
        const val TOLERANCE = 0.01f
    }
}

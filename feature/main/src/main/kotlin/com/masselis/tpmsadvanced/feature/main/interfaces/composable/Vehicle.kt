@file:Suppress("LongMethod")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstrainScope
import androidx.constraintlayout.compose.ConstrainedLayoutReference
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintLayoutBaseScope.HorizontalAnchor
import androidx.constraintlayout.compose.ConstraintLayoutScope
import androidx.constraintlayout.compose.Dimension
import com.masselis.tpmsadvanced.core.ui.KeepScreenOn
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.KILO_PASCAL
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Every vehicle image is drawn on a 208:462 canvas, centered and taking `imageHeight` (0..1) of the
 * available height. Tyres are placed and sized as fractions of that image so they stay on the
 * drawn wheels whatever the screen size or orientation. Texts and buttons keep a fixed size to
 * remain readable.
 */
private const val IMAGE_RATIO = 208f / 462f

/*
 * The image is as tall as possible, up to [MAX_IMAGE_HEIGHT] of the height, while leaving room on
 * each side for the widest readout text, its [READOUT_GAP] and a [SCREEN_MARGIN], so readouts are
 * never cut by the screen edges. On narrow screens (portrait) the width decides, down to
 * [MIN_IMAGE_HEIGHT].
 */
private const val MIN_IMAGE_HEIGHT = .45f
private const val MAX_IMAGE_HEIGHT = .9f

/*
 * The image never covers more than this share of the available area. Vehicles with readouts on
 * both sides are limited by the width first on phones; this keeps a vehicle whose readouts are all
 * on one side (more width left for the image) from growing much bigger than the others.
 */
private const val MAX_IMAGE_AREA = .5f
private val READOUT_GAP = 8.dp
private val SCREEN_MARGIN = 4.dp

/**
 * Widest pressure line of a readout in this unit, up to the 150 psi settings limit, see [TyreStat]
 * and [com.masselis.tpmsadvanced.data.vehicle.model.Pressure.string]
 */
private val PressureUnit.widestReadout: String
    get() = when (this) {
        KILO_PASCAL -> "1034 kpa*"
        BAR -> "8.88 bar*"
        PSI -> "88.8 psi*"
    }

/** Widest plausible detail lines of a readout, see [TyreStat] */
private val WIDEST_DETAILS = listOf("188°F", "188°C", "88 hours", "99+ days")

/** Height of a tyre as a fraction of the image height, its width follows the tyre 15:40 ratio */
private const val TYRE_HEIGHT = .165f

/**
 * The current vehicle. Tapping a location without a sensor assigns it one by [scanQrCode],
 * [scanBluetooth] or the sensor detected there.
 */
@Composable
public fun CurrentVehicle(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    center: @Composable (Modifier) -> Unit = {},
    scanQrCode: () -> Unit = {},
    scanBluetooth: () -> Unit = {},
) {
    Vehicle(
        component = LocalVehicleComponent.current,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        center = center,
        taps = TyreTaps(isManaging = false, scanQrCode = scanQrCode, scanBluetooth = scanBluetooth),
    )
}

@Suppress("MaxLineLength")
@Composable
public fun Vehicle(
    component: VehicleComponent,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    /** Placed over the vehicle, between its axles or in the middle of a single axle one */
    center: @Composable (Modifier) -> Unit = {},
    taps: TyreTaps = TyreTaps(),
) {
    KeepScreenOn()
    val pressureUnit by component
        .viewModel(component.key()) { it.VehicleSettingsViewModel() }
        .pressureUnit
        .collectAsState()
    // Every readout as wide, so the outlines around them are too
    val readoutSlotWidth = rememberWidestReadoutWidth(pressureUnit) + OUTLINE_PADDING
    // The outline reaches past the readout, it must stay off the screen's edge too
    val readoutWidth = readoutSlotWidth + READOUT_GAP + OUTLINE_OUTSET
    val basicReadoutHeight = rememberBasicReadoutHeight()
    val readoutSides = component.vehicle.kind.locations.map { it.readoutSide }.toSet()
    BoxWithConstraints(modifier) {
        val imageHeight = maxWidth
            .minus(readoutWidth * readoutSides.size)
            .minus(SCREEN_MARGIN * 2)
            .div(maxHeight * IMAGE_RATIO)
            // (imageHeight * maxHeight)² * IMAGE_RATIO <= MAX_IMAGE_AREA * maxWidth * maxHeight
            .coerceAtMost(sqrt(MAX_IMAGE_AREA * maxWidth.value / (IMAGE_RATIO * maxHeight.value)))
            .coerceIn(MIN_IMAGE_HEIGHT, MAX_IMAGE_HEIGHT)
        // Centers the image and its readouts together when readouts are only on one side
        val fill = Modifier
            .fillMaxSize()
            .offset(
                x = listOfNotNull(
                    readoutWidth.takeIf { LEFT in readoutSides },
                    readoutWidth.takeIf { RIGHT in readoutSides }?.unaryMinus(),
                ).fold(0.dp, Dp::plus) / 2
            )
        when (component.vehicle.kind) {
            Kind.CAR -> 
                Car(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.SINGLE_AXLE_TRAILER -> 
                SingleAxleTrailer(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.MOTORCYCLE -> 
                Motorcycle(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.TADPOLE_THREE_WHEELER -> 
                TadpoleThreadWheeler(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.DELTA_THREE_WHEELER -> 
                DeltaThreeWheeler(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
        }
    }
}

/**
 * What tapping the vehicle's locations does. While [isManaging], see [ManageSensors], every
 * location is outlined and its readout keeps to its pressure, temperature and time since update.
 */
public class TyreTaps(
    internal val isManaging: Boolean = false,
    internal val scanQrCode: () -> Unit = {},
    internal val scanBluetooth: () -> Unit = {},
)

/**
 * Spans [tyre] and its [readout], whichever side of the tyre the readout of [location] is on, and
 * the readout's height: the whole image, see [outlineCenteredOn]
 */
private fun ConstrainScope.around(
    tyre: ConstrainedLayoutReference,
    readout: ConstrainedLayoutReference,
    location: Location,
) {
    top.linkTo(readout.top)
    bottom.linkTo(readout.bottom)
    when (location.readoutSide) {
        LEFT -> {
            start.linkTo(readout.start)
            end.linkTo(tyre.end)
        }

        RIGHT -> {
            start.linkTo(tyre.start)
            end.linkTo(readout.end)
        }
    }
    width = Dimension.fillToConstraints
    height = Dimension.fillToConstraints
}

/**
 * Given the image's height, as tall as a tyre or [minHeight] for a basic readout, and placed like
 * [verticallyCenteredOn] places the readout: centered on [y], pushed back inside the image when it
 * would overflow, so it stays around the readout wherever it's moved
 */
private fun Modifier.outlineCenteredOn(y: Float, minHeight: Dp) = layout { measurable, constraints ->
    val height = (constraints.maxHeight * TYRE_HEIGHT)
        .roundToInt()
        .coerceAtLeast(minHeight.roundToPx())
        .coerceAtMost(constraints.maxHeight)
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, constraints.maxHeight) {
        placeable.place(
            x = 0,
            y = (constraints.maxHeight * y - height / 2f)
                .roundToInt()
                .coerceIn(0, constraints.maxHeight - height)
        )
    }
}

/** Side of the image the readout of this location sits on, see the layouts below */
internal val Location.readoutSide: SensorLocation.Side
    get() = when (this) {
        is Location.Axle -> RIGHT
        is Location.Wheel -> location.side
        is Location.Side -> side
    }

/** Height of a [TyreStat] showing only its pressure, temperature and time since update */
@Composable
private fun rememberBasicReadoutHeight(): Dp {
    val measurer = rememberTextMeasurer()
    val pressureStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    return remember(measurer, pressureStyle, density) {
        measurer.measure("0", pressureStyle).size.height
            .plus(measurer.measure("0", pressureStyle.copy(fontSize = 16.sp)).size.height * 2)
            .let { with(density) { it.toDp() } }
    }
}

/** Width of the widest line a [TyreReadout] can show, with the current font and font scale */
@Composable
private fun rememberWidestReadoutWidth(pressureUnit: PressureUnit): Dp {
    val measurer = rememberTextMeasurer()
    val pressureStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    return remember(measurer, pressureStyle, density, pressureUnit) {
        listOf(measurer.measure(pressureUnit.widestReadout, pressureStyle))
            .plus(WIDEST_DETAILS.map { measurer.measure(it, pressureStyle.copy(fontSize = 16.sp)) })
            // What a location without a sensor shows instead, see TyreReadout
            .plus(measurer.measure("Tap to", pressureStyle.copy(fontSize = 20.sp)))
            .plus(measurer.measure("(detected)", pressureStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal)))
            .maxOf { it.size.width }
            .let { with(density) { it.toDp() } }
    }
}

@Composable
private fun ConstraintLayoutScope.VehicleImage(
    ref: ConstrainedLayoutReference,
    @DrawableRes id: Int,
    contentDescription: String,
    imageHeight: Float,
) {
    Image(
        bitmap = ImageBitmap.imageResource(id = id),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
        contentDescription = contentDescription,
        modifier = Modifier
            .aspectRatio(IMAGE_RATIO)
            .constrainAs(ref) {
                centerTo(parent)
                height = Dimension.percent(imageHeight)
            }
    )
}

/**
 * Invisible box centered like the image and spanning [span] of its width, its edges anchor what
 * sits at `(1 - span) / 2` and `(1 + span) / 2` of the image width: wheel centers or the outline.
 */
@Composable
private fun ConstraintLayoutScope.ImageSpan(
    ref: ConstrainedLayoutReference,
    span: Float,
    imageHeight: Float,
) {
    Box(
        Modifier
            .aspectRatio(span * IMAGE_RATIO)
            .constrainAs(ref) {
                centerTo(parent)
                height = Dimension.percent(imageHeight)
            }
    )
}

/** Anchor at [y] (0..1) of the image height */
private fun ConstraintLayoutScope.imageGuideline(y: Float, imageHeight: Float): HorizontalAnchor =
    createGuidelineFromTop((1f - imageHeight) / 2f + imageHeight * y)

/**
 * Takes the whole height it's given and places its content centered on [y] (0..1) of that height,
 * pushed back inside when it would overflow. Given the image's height, it keeps a readout next to
 * its tyre without ever going above or below the image.
 */
private fun Modifier.verticallyCenteredOn(y: Float) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    layout(placeable.width, constraints.maxHeight) {
        placeable.place(
            x = 0,
            y = (constraints.maxHeight * y - placeable.height / 2f)
                .roundToInt()
                .coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0))
        )
    }
}

/**
 * Moves its content to the vertical middle of the window rather than of the space it's given,
 * which the top bar pushes down
 */
@Composable
private fun Modifier.windowCenteredVertically(): Modifier {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    var shift by remember { mutableIntStateOf(0) }
    return this
        // Before the offset, where it would be without it
        .onGloballyPositioned {
            shift = (windowHeight / 2f - it.positionInWindow().y - it.size.height / 2f).roundToInt()
        }
        .offset { IntOffset(0, shift) }
}

private fun ConstrainScope.tyreSize(imageHeight: Float) {
    height = Dimension.percent(imageHeight * TYRE_HEIGHT)
    width = Dimension.ratio("15:40")
}

@Composable
private fun Car(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            track,
            frontLeft,
            frontLeftStats,
            frontLeftTap,
            frontRight,
            frontRightStats,
            frontRightTap,
            rearLeft,
            rearLeftStats,
            rearLeftTap,
            rearRight,
            rearRightStats,
            rearRightTap
        ) = createRefs()
        VehicleImage(vehicleImage, R.drawable.schema_car_top_view, "Image of your car", imageHeight)
        ImageSpan(track, .74f, imageHeight)
        val frontY = .217f
        val frontAxle = imageGuideline(frontY, imageHeight)
        val rearY = .783f
        val rearAxle = imageGuideline(rearY, imageHeight)
        with(Location.Wheel(FRONT_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontLeft) {
                    centerAround(track.start)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(frontLeft.start, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(frontLeftTap) { around(frontLeft, frontLeftStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(FRONT_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontRight) {
                    centerAround(track.end)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(frontRight.end, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(frontRightTap) { around(frontRight, frontRightStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(REAR_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearLeft) {
                    centerAround(track.start)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(rearLeft.start, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(rearLeftTap) { around(rearLeft, rearLeftStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(REAR_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearRight) {
                    centerAround(track.end)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(rearRight.end, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(rearRightTap) { around(rearRight, rearRightStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            val middle = imageGuideline((frontY + rearY) / 2, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(middle)
                }
            )
        }
    }
}

@Composable
private fun SingleAxleTrailer(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            track,
            tyreLeft,
            leftStats,
            leftTap,
            tyreRight,
            rightStats,
            rightTap,
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_single_axle_trailer_top_view,
            "Image of your trailer",
            imageHeight
        )
        ImageSpan(track, .86f, imageHeight)
        val axleY = .686f
        val axle = imageGuideline(axleY, imageHeight)
        with(Location.Side(LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreLeft) {
                    centerAround(track.start)
                    centerAround(axle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(leftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(tyreLeft.start, 8.dp)
                }.verticallyCenteredOn(axleY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(leftTap) { around(tyreLeft, leftStats, this@with) }
                    .outlineCenteredOn(axleY, basicReadoutHeight),
            )
        }
        with(Location.Side(RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreRight) {
                    centerAround(track.end)
                    centerAround(axle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(tyreRight.end, 8.dp)
                }.verticallyCenteredOn(axleY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(rightTap) { around(tyreRight, rightStats, this@with) }
                    .outlineCenteredOn(axleY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            center(
                Modifier
                    .constrainAs(ref) {
                        centerHorizontallyTo(vehicleImage)
                        centerVerticallyTo(vehicleImage)
                    }
                    .windowCenteredVertically()
            )
        }
    }
}

@Composable
private fun Motorcycle(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            tyreFront,
            frontStats,
            frontTap,
            tyreRear,
            rearStats,
            rearTap,
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_motorcycle_top_view,
            "Image of your motorcycle",
            imageHeight
        )
        val frontY = .0865f
        val frontAxle = imageGuideline(frontY, imageHeight)
        val rearY = .805f
        val rearAxle = imageGuideline(rearY, imageHeight)
        with(Location.Axle(FRONT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreFront) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(vehicleImage.end, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(frontTap) { around(tyreFront, frontStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Axle(REAR)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreRear) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(vehicleImage.end, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(rearTap) { around(tyreRear, rearStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            val middle = imageGuideline((frontY + rearY) / 2, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(middle)
                }
            )
        }
    }
}

@Composable
private fun TadpoleThreadWheeler(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            frontTrack,
            rearOutline,
            frontLeft,
            frontLeftStats,
            frontLeftTap,
            frontRight,
            frontRightStats,
            frontRightTap,
            tyreRear,
            rearStats,
            rearTap,
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_tadpole_three_wheeler_top_view,
            "Image of your three-wheeler",
            imageHeight
        )
        ImageSpan(frontTrack, .58f, imageHeight)
        // Outline around the rear wheel (the exhaust on the right), the rear readout sits next to it
        ImageSpan(rearOutline, .65f, imageHeight)
        val frontY = .1005f
        val frontAxle = imageGuideline(frontY, imageHeight)
        val rearY = .845f
        val rearAxle = imageGuideline(rearY, imageHeight)
        with(Location.Wheel(FRONT_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontLeft) {
                    centerAround(frontTrack.start)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(frontLeft.start, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(frontLeftTap) { around(frontLeft, frontLeftStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(FRONT_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontRight) {
                    centerAround(frontTrack.end)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(frontRight.end, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(frontRightTap) { around(frontRight, frontRightStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Axle(REAR)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreRear) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(rearOutline.end, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(rearTap) { around(tyreRear, rearStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            val middle = imageGuideline((frontY + rearY) / 2, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(middle)
                }
            )
        }
    }
}

@Composable
private fun DeltaThreeWheeler(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            rearTrack,
            frontOutline,
            tyreFront,
            frontStats,
            frontTap,
            rearLeft,
            rearLeftStats,
            rearLeftTap,
            rearRight,
            rearRightStats,
            rearRightTap
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_delta_three_wheeler_top_view,
            "Image of your three-wheeler",
            imageHeight
        )
        ImageSpan(rearTrack, .713f, imageHeight)
        // Outline around the front wheel, the front readout sits next to it
        ImageSpan(frontOutline, .22f, imageHeight)
        val frontY = .0865f
        val frontAxle = imageGuideline(frontY, imageHeight)
        val rearY = .835f
        val rearAxle = imageGuideline(rearY, imageHeight)
        with(Location.Axle(FRONT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreFront) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(frontOutline.end, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(frontTap) { around(tyreFront, frontStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(REAR_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearLeft) {
                    centerAround(rearTrack.start)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(vehicleImage.start, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(rearLeftTap) { around(rearLeft, rearLeftStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(REAR_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearRight) {
                    centerAround(rearTrack.end)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(vehicleImage.end, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                modifier = Modifier
                    .constrainAs(rearRightTap) { around(rearRight, rearRightStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            val middle = imageGuideline((frontY + rearY) / 2, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(middle)
                }
            )
        }
    }
}

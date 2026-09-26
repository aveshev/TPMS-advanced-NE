@file:Suppress("LongMethod")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstrainScope
import androidx.constraintlayout.compose.ConstrainedLayoutReference
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintLayoutBaseScope.HorizontalAnchor
import androidx.constraintlayout.compose.ConstraintLayoutScope
import androidx.constraintlayout.compose.Dimension
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.core.ui.KeepScreenOn
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

/*
 * Every vehicle image is drawn on a 208:462 canvas, centered and taking [IMAGE_HEIGHT] of the
 * available height. Tyres are placed and sized as fractions of that image so they stay on the
 * drawn wheels whatever the screen size or orientation. Texts and buttons keep a fixed size to
 * remain readable.
 */
private const val IMAGE_RATIO = 208f / 462f
private const val IMAGE_HEIGHT = .7f

/** Height of a tyre as a fraction of the image height, its width follows the tyre 15:40 ratio */
private const val TYRE_HEIGHT = .165f

@Composable
public fun CurrentVehicle(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Vehicle(
        component = LocalVehicleComponent.current,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@Composable
public fun Vehicle(
    component: VehicleComponent,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    when (component.vehicle.kind) {
        Kind.CAR -> Car(snackbarHostState, modifier)
        Kind.SINGLE_AXLE_TRAILER -> SingleAxleTrailer(snackbarHostState, modifier)
        Kind.MOTORCYCLE -> Motorcycle(snackbarHostState, modifier)
        Kind.TADPOLE_THREE_WHEELER -> TadpoleThreadWheeler(snackbarHostState, modifier)
        Kind.DELTA_THREE_WHEELER -> DeltaThreeWheeler(snackbarHostState, modifier)
    }
}

@Composable
private fun ConstraintLayoutScope.VehicleImage(
    ref: ConstrainedLayoutReference,
    @DrawableRes id: Int,
    contentDescription: String,
) {
    Image(
        bitmap = ImageBitmap.imageResource(id = id),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
        contentDescription = contentDescription,
        modifier = Modifier
            .aspectRatio(IMAGE_RATIO)
            .constrainAs(ref) {
                centerTo(parent)
                height = Dimension.percent(IMAGE_HEIGHT)
            }
    )
}

/**
 * Invisible box centered like the image and spanning [span] of its width, tyres are centered on
 * its edges to sit on wheels drawn at `(1 - span) / 2` and `(1 + span) / 2` of the image width.
 */
@Composable
private fun ConstraintLayoutScope.WheelTrack(ref: ConstrainedLayoutReference, span: Float) {
    Box(
        Modifier
            .aspectRatio(span * IMAGE_RATIO)
            .constrainAs(ref) {
                centerTo(parent)
                height = Dimension.percent(IMAGE_HEIGHT)
            }
    )
}

/** Anchor at [y] (0..1) of the image height */
private fun ConstraintLayoutScope.imageGuideline(y: Float): HorizontalAnchor =
    createGuidelineFromTop((1f - IMAGE_HEIGHT) / 2f + IMAGE_HEIGHT * y)

private fun ConstrainScope.tyreSize() {
    height = Dimension.percent(IMAGE_HEIGHT * TYRE_HEIGHT)
    width = Dimension.ratio("15:40")
}

@Composable
private fun Car(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            track,
            frontLeft,
            frontLeftStats,
            frontLeftBinding,
            frontRight,
            frontRightStats,
            frontRightBinding,
            rearLeft,
            rearLeftStats,
            rearLeftBinding,
            rearRight,
            rearRightStats,
            rearRightBinding
        ) = createRefs()
        VehicleImage(vehicleImage, R.drawable.schema_car_top_view, "Image of your car")
        WheelTrack(track, .765f)
        val frontAxle = imageGuideline(.217f)
        val rearAxle = imageGuideline(.783f)
        with(Location.Wheel(FRONT_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontLeft) {
                    centerAround(track.start)
                    centerAround(frontAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontLeftStats) {
                    top.linkTo(frontLeft.top)
                    end.linkTo(frontLeft.start, 8.dp)
                    // If not, the word "bar" for "1,50 bar" is not displayed 🤷
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontLeftBinding) {
                    top.linkTo(frontLeft.top)
                    start.linkTo(frontLeft.end)
                }
            )
        }
        with(Location.Wheel(FRONT_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontRight) {
                    centerAround(track.end)
                    centerAround(frontAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontRightStats) {
                    top.linkTo(frontRight.top)
                    start.linkTo(frontRight.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontRightBinding) {
                    top.linkTo(frontRight.top)
                    end.linkTo(frontRight.start)
                }
            )
        }
        with(Location.Wheel(REAR_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearLeft) {
                    centerAround(track.start)
                    centerAround(rearAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearLeftStats) {
                    bottom.linkTo(rearLeft.bottom)
                    end.linkTo(rearLeft.start, 8.dp)
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearLeftBinding) {
                    bottom.linkTo(rearLeft.bottom)
                    start.linkTo(rearLeft.end)
                }
            )
        }
        with(Location.Wheel(REAR_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearRight) {
                    centerAround(track.end)
                    centerAround(rearAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearRightStats) {
                    bottom.linkTo(rearRight.bottom)
                    start.linkTo(rearRight.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearRightBinding) {
                    bottom.linkTo(rearRight.bottom)
                    end.linkTo(rearRight.start)
                }
            )
        }
    }
}

@Composable
private fun SingleAxleTrailer(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            track,
            tyreLeft,
            leftStats,
            leftBinding,
            tyreRight,
            rightStats,
            rightBinding,
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_single_axle_trailer_top_view,
            "Image of your trailer"
        )
        WheelTrack(track, .884f)
        val axle = imageGuideline(.686f)
        with(Location.Side(LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreLeft) {
                    centerAround(track.start)
                    centerAround(axle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(leftStats) {
                    top.linkTo(tyreLeft.top)
                    bottom.linkTo(tyreLeft.bottom)
                    end.linkTo(tyreLeft.start, 8.dp)
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(leftBinding) {
                    top.linkTo(tyreLeft.top)
                    bottom.linkTo(tyreLeft.bottom)
                    start.linkTo(tyreLeft.end)
                }
            )
        }
        with(Location.Side(RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreRight) {
                    centerAround(track.end)
                    centerAround(axle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rightStats) {
                    top.linkTo(tyreRight.top)
                    bottom.linkTo(tyreRight.bottom)
                    start.linkTo(tyreRight.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rightBinding) {
                    top.linkTo(tyreRight.top)
                    bottom.linkTo(tyreRight.bottom)
                    end.linkTo(tyreRight.start)
                }
            )
        }
    }
}

@Composable
private fun Motorcycle(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            tyreFront,
            frontStats,
            frontBinding,
            tyreRear,
            rearStats,
            rearBinding,
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_motorcycle_top_view,
            "Image of your motorcycle"
        )
        val frontAxle = imageGuideline(.0865f)
        val rearAxle = imageGuideline(.805f)
        with(Location.Axle(FRONT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreFront) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(frontAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontStats) {
                    top.linkTo(tyreFront.top)
                    start.linkTo(vehicleImage.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontBinding) {
                    top.linkTo(tyreFront.top)
                    bottom.linkTo(tyreFront.bottom)
                    end.linkTo(tyreFront.start)
                }
            )
        }
        with(Location.Axle(REAR)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreRear) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(rearAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearStats) {
                    bottom.linkTo(tyreRear.bottom)
                    start.linkTo(vehicleImage.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearBinding) {
                    top.linkTo(tyreRear.top)
                    bottom.linkTo(tyreRear.bottom)
                    end.linkTo(tyreRear.start)
                }
            )
        }
    }
}

@Composable
private fun TadpoleThreadWheeler(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            frontTrack,
            frontLeft,
            frontLeftStats,
            frontLeftBinding,
            frontRight,
            frontRightStats,
            frontRightBinding,
            tyreRear,
            rearStats,
            rearBinding,
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_tadpole_three_wheeler_top_view,
            "Image of your three-wheeler"
        )
        WheelTrack(frontTrack, .68f)
        val frontAxle = imageGuideline(.1005f)
        val rearAxle = imageGuideline(.83f)
        with(Location.Wheel(FRONT_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontLeft) {
                    centerAround(frontTrack.start)
                    centerAround(frontAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontLeftStats) {
                    top.linkTo(frontLeft.top)
                    end.linkTo(frontLeft.start, 8.dp)
                    // If not, the word "bar" for "1,50 bar" is not displayed 🤷
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontLeftBinding) {
                    top.linkTo(frontLeft.bottom)
                    centerHorizontallyTo(frontLeft)
                }
            )
        }
        with(Location.Wheel(FRONT_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(frontRight) {
                    centerAround(frontTrack.end)
                    centerAround(frontAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontRightStats) {
                    top.linkTo(frontRight.top)
                    start.linkTo(frontRight.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontRightBinding) {
                    top.linkTo(frontRight.bottom)
                    centerHorizontallyTo(frontRight)
                }
            )
        }
        with(Location.Axle(REAR)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreRear) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(rearAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearStats) {
                    bottom.linkTo(tyreRear.bottom)
                    start.linkTo(tyreRear.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearBinding) {
                    top.linkTo(tyreRear.top)
                    bottom.linkTo(tyreRear.bottom)
                    end.linkTo(tyreRear.start)
                }
            )
        }
    }
}

@Composable
private fun DeltaThreeWheeler(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            rearTrack,
            tyreFront,
            frontStats,
            frontBinding,
            rearLeft,
            rearLeftStats,
            rearLeftBinding,
            rearRight,
            rearRightStats,
            rearRightBinding
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_delta_three_wheeler_top_view,
            "Image of your three-wheeler"
        )
        WheelTrack(rearTrack, .737f)
        val frontAxle = imageGuideline(.0865f)
        val rearAxle = imageGuideline(.835f)
        with(Location.Axle(FRONT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(tyreFront) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(frontAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(frontStats) {
                    top.linkTo(tyreFront.top)
                    start.linkTo(vehicleImage.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(frontBinding) {
                    top.linkTo(tyreFront.top)
                    bottom.linkTo(tyreFront.bottom)
                    end.linkTo(tyreFront.start)
                }
            )
        }
        with(Location.Wheel(REAR_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearLeft) {
                    centerAround(rearTrack.start)
                    centerAround(rearAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearLeftStats) {
                    bottom.linkTo(rearLeft.bottom)
                    end.linkTo(vehicleImage.start, 8.dp)
                    width = Dimension.value(100.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearLeftBinding) {
                    bottom.linkTo(rearLeft.top)
                    centerHorizontallyTo(rearLeft)
                }
            )
        }
        with(Location.Wheel(REAR_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.constrainAs(rearRight) {
                    centerAround(rearTrack.end)
                    centerAround(rearAxle)
                    tyreSize()
                }
            )
            TyreStat(
                location = this,
                modifier = Modifier.constrainAs(rearRightStats) {
                    bottom.linkTo(rearRight.bottom)
                    start.linkTo(vehicleImage.end, 8.dp)
                }
            )
            BindSensorButton(
                location = this,
                modifier = Modifier.constrainAs(rearRightBinding) {
                    bottom.linkTo(rearRight.top)
                    centerHorizontallyTo(rearRight)
                }
            )
        }
    }
}

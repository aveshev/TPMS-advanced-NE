import re,sys
F,T=sys.argv[1],sys.argv[2]
src=open(F).read()
src=src.replace('package com.masselis.tpmsadvanced.feature.main.interfaces.composable','package com.masselis.tpmsadvanced.feature.main.interfaces.composable.scratch')
split=src.index('/*\n * Every vehicle'); consts=src[split:src.index('@Composable\npublic fun CurrentVehicle')]; head=src[:split].replace('import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent\n','')
body=src[src.index('/** Width of the widest line'):]
body=re.sub(r'\bTyre\(\n(\s*)location = this,\n\s*snackbarHostState = snackbarHostState,', r'FakeTyre(\n\1location = this,', body)
body=re.sub(r'\bTyreStat\(', 'FakeStat(', body); body=re.sub(r'\bBindSensorButton\(', 'FakeBind(', body)
body=re.sub(r'private fun (Car|SingleAxleTrailer|Motorcycle|TadpoleThreadWheeler|DeltaThreeWheeler)\(', r'internal fun \1(', body)
extra='''import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Tyre
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow

@Composable internal fun FakeTyre(location: Location, modifier: Modifier = Modifier) {
    Tyre(TyreIconStateFlow.State.NotDetected, SnackbarHostState(), modifier.background(Color(0x55FF00FF)))
}
@Composable internal fun FakeStat(location: Location, modifier: Modifier = Modifier) {
    // Same texts, styles and per-side alignment as TyreStat
    val alignment = when (location) {
        is Location.Axle -> androidx.compose.ui.Alignment.Start
        is Location.Wheel -> if (location.location.side == com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT) androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start
        is Location.Side -> if (location.side == com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT) androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start
    }
    androidx.compose.foundation.layout.Column(modifier.background(Color(0x33FF0000))) {
        androidx.compose.material3.Text("2.00 bar", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, modifier = Modifier.align(alignment).background(Color(0x66FF0000)))
        androidx.compose.material3.Text("30°C", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1, modifier = Modifier.align(alignment).background(Color(0x66FF0000)))
        androidx.compose.material3.Text("47 hours", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1, modifier = Modifier.align(alignment).background(Color(0x66FF0000)))
    }
}
@Composable internal fun FakeBind(location: Location, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).background(Color(0x440000FF)))
}

'''
consts=consts.replace('\nprivate ','\ninternal '); body=body.replace('\nprivate ','\ninternal ')
open(T+'/ScratchVehicle.kt','w').write(head+extra+consts+body)

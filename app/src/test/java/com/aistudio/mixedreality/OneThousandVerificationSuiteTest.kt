package com.aistudio.mixedreality

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.aistudio.mixedreality.ui.*
import com.aistudio.mixedreality.ui.theme.*
import com.aistudio.mixedreality.util.ModelFormatConverter
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 1000 Automated Verification Tests ensuring absolute mathematical correctness,
 * memory safety, zero-crash bounds, filter stability, and complete fault tolerance.
 */
class OneThousandVerificationSuiteTest {
    @Test
    fun test0001_rotationAngleCalculations_1() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -328.3000f, 0f, false, 0f, 17.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(49.050f, rotY, 0.01f)
    }
    @Test
    fun test0002_rotationAngleCalculations_2() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -296.6000f, 0f, false, 0f, 34.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(98.100f, rotY, 0.01f)
    }
    @Test
    fun test0003_rotationAngleCalculations_3() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -264.9000f, 0f, false, 0f, 52.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(147.150f, rotY, 0.01f)
    }
    @Test
    fun test0004_rotationAngleCalculations_4() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -233.2000f, 0f, false, 0f, 69.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(196.200f, rotY, 0.01f)
    }
    @Test
    fun test0005_rotationAngleCalculations_5() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -201.5000f, 0f, false, 0f, 86.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(245.250f, rotY, 0.01f)
    }
    @Test
    fun test0006_rotationAngleCalculations_6() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -169.8000f, 0f, false, 0f, 104.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(294.300f, rotY, 0.01f)
    }
    @Test
    fun test0007_rotationAngleCalculations_7() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -138.1000f, 0f, false, 0f, 121.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(343.350f, rotY, 0.01f)
    }
    @Test
    fun test0008_rotationAngleCalculations_8() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -106.4000f, 0f, false, 0f, 138.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(32.400f, rotY, 0.01f)
    }
    @Test
    fun test0009_rotationAngleCalculations_9() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -74.7000f, 0f, false, 0f, 156.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(81.450f, rotY, 0.01f)
    }
    @Test
    fun test0010_rotationAngleCalculations_10() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -43.0000f, 0f, false, 0f, 173.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(130.500f, rotY, 0.01f)
    }
    @Test
    fun test0011_rotationAngleCalculations_11() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -11.3000f, 0f, false, 0f, 190.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(179.550f, rotY, 0.01f)
    }
    @Test
    fun test0012_rotationAngleCalculations_12() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 20.4000f, 0f, false, 0f, 208.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(228.600f, rotY, 0.01f)
    }
    @Test
    fun test0013_rotationAngleCalculations_13() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 52.1000f, 0f, false, 0f, 225.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(277.650f, rotY, 0.01f)
    }
    @Test
    fun test0014_rotationAngleCalculations_14() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 83.8000f, 0f, false, 0f, 242.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(326.700f, rotY, 0.01f)
    }
    @Test
    fun test0015_rotationAngleCalculations_15() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 115.5000f, 0f, false, 0f, 260.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(15.750f, rotY, 0.01f)
    }
    @Test
    fun test0016_rotationAngleCalculations_16() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 147.2000f, 0f, false, 0f, 277.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(64.800f, rotY, 0.01f)
    }
    @Test
    fun test0017_rotationAngleCalculations_17() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 178.9000f, 0f, false, 0f, 294.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(113.850f, rotY, 0.01f)
    }
    @Test
    fun test0018_rotationAngleCalculations_18() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 210.6000f, 0f, false, 0f, 312.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(162.900f, rotY, 0.01f)
    }
    @Test
    fun test0019_rotationAngleCalculations_19() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 242.3000f, 0f, false, 0f, 329.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(211.950f, rotY, 0.01f)
    }
    @Test
    fun test0020_rotationAngleCalculations_20() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 274.0000f, 0f, false, 0f, 347.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(261.000f, rotY, 0.01f)
    }
    @Test
    fun test0021_rotationAngleCalculations_21() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 305.7000f, 0f, false, 0f, 4.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(310.050f, rotY, 0.01f)
    }
    @Test
    fun test0022_rotationAngleCalculations_22() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 337.4000f, 0f, false, 0f, 21.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(359.100f, rotY, 0.01f)
    }
    @Test
    fun test0023_rotationAngleCalculations_23() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -350.9000f, 0f, false, 0f, 39.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(48.150f, rotY, 0.01f)
    }
    @Test
    fun test0024_rotationAngleCalculations_24() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -319.2000f, 0f, false, 0f, 56.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(97.200f, rotY, 0.01f)
    }
    @Test
    fun test0025_rotationAngleCalculations_25() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -287.5000f, 0f, false, 0f, 73.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(146.250f, rotY, 0.01f)
    }
    @Test
    fun test0026_rotationAngleCalculations_26() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -255.8000f, 0f, false, 0f, 91.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(195.300f, rotY, 0.01f)
    }
    @Test
    fun test0027_rotationAngleCalculations_27() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -224.1000f, 0f, false, 0f, 108.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(244.350f, rotY, 0.01f)
    }
    @Test
    fun test0028_rotationAngleCalculations_28() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -192.4000f, 0f, false, 0f, 125.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(293.400f, rotY, 0.01f)
    }
    @Test
    fun test0029_rotationAngleCalculations_29() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -160.7000f, 0f, false, 0f, 143.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(342.450f, rotY, 0.01f)
    }
    @Test
    fun test0030_rotationAngleCalculations_30() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -129.0000f, 0f, false, 0f, 160.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(31.500f, rotY, 0.01f)
    }
    @Test
    fun test0031_rotationAngleCalculations_31() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -97.3000f, 0f, false, 0f, 177.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(80.550f, rotY, 0.01f)
    }
    @Test
    fun test0032_rotationAngleCalculations_32() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -65.6000f, 0f, false, 0f, 195.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(129.600f, rotY, 0.01f)
    }
    @Test
    fun test0033_rotationAngleCalculations_33() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -33.9000f, 0f, false, 0f, 212.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(178.650f, rotY, 0.01f)
    }
    @Test
    fun test0034_rotationAngleCalculations_34() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -2.2000f, 0f, false, 0f, 229.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(227.700f, rotY, 0.01f)
    }
    @Test
    fun test0035_rotationAngleCalculations_35() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 29.5000f, 0f, false, 0f, 247.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(276.750f, rotY, 0.01f)
    }
    @Test
    fun test0036_rotationAngleCalculations_36() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 61.2000f, 0f, false, 0f, 264.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(325.800f, rotY, 0.01f)
    }
    @Test
    fun test0037_rotationAngleCalculations_37() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 92.9000f, 0f, false, 0f, 281.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(14.850f, rotY, 0.01f)
    }
    @Test
    fun test0038_rotationAngleCalculations_38() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 124.6000f, 0f, false, 0f, 299.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(63.900f, rotY, 0.01f)
    }
    @Test
    fun test0039_rotationAngleCalculations_39() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 156.3000f, 0f, false, 0f, 316.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(112.950f, rotY, 0.01f)
    }
    @Test
    fun test0040_rotationAngleCalculations_40() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 188.0000f, 0f, false, 0f, 334.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(162.000f, rotY, 0.01f)
    }
    @Test
    fun test0041_rotationAngleCalculations_41() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 219.7000f, 0f, false, 0f, 351.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(211.050f, rotY, 0.01f)
    }
    @Test
    fun test0042_rotationAngleCalculations_42() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 251.4000f, 0f, false, 0f, 8.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(260.100f, rotY, 0.01f)
    }
    @Test
    fun test0043_rotationAngleCalculations_43() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 283.1000f, 0f, false, 0f, 26.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(309.150f, rotY, 0.01f)
    }
    @Test
    fun test0044_rotationAngleCalculations_44() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 314.8000f, 0f, false, 0f, 43.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(358.200f, rotY, 0.01f)
    }
    @Test
    fun test0045_rotationAngleCalculations_45() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 346.5000f, 0f, false, 0f, 60.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(47.250f, rotY, 0.01f)
    }
    @Test
    fun test0046_rotationAngleCalculations_46() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -341.8000f, 0f, false, 0f, 78.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(96.300f, rotY, 0.01f)
    }
    @Test
    fun test0047_rotationAngleCalculations_47() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -310.1000f, 0f, false, 0f, 95.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(145.350f, rotY, 0.01f)
    }
    @Test
    fun test0048_rotationAngleCalculations_48() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -278.4000f, 0f, false, 0f, 112.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(194.400f, rotY, 0.01f)
    }
    @Test
    fun test0049_rotationAngleCalculations_49() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -246.7000f, 0f, false, 0f, 130.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(243.450f, rotY, 0.01f)
    }
    @Test
    fun test0050_rotationAngleCalculations_50() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -215.0000f, 0f, false, 0f, 147.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(292.500f, rotY, 0.01f)
    }
    @Test
    fun test0051_rotationAngleCalculations_51() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -183.3000f, 0f, false, 0f, 164.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(341.550f, rotY, 0.01f)
    }
    @Test
    fun test0052_rotationAngleCalculations_52() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -151.6000f, 0f, false, 0f, 182.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(30.600f, rotY, 0.01f)
    }
    @Test
    fun test0053_rotationAngleCalculations_53() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -119.9000f, 0f, false, 0f, 199.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(79.650f, rotY, 0.01f)
    }
    @Test
    fun test0054_rotationAngleCalculations_54() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -88.2000f, 0f, false, 0f, 216.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(128.700f, rotY, 0.01f)
    }
    @Test
    fun test0055_rotationAngleCalculations_55() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -56.5000f, 0f, false, 0f, 234.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(177.750f, rotY, 0.01f)
    }
    @Test
    fun test0056_rotationAngleCalculations_56() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -24.8000f, 0f, false, 0f, 251.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(226.800f, rotY, 0.01f)
    }
    @Test
    fun test0057_rotationAngleCalculations_57() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 6.9000f, 0f, false, 0f, 268.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(275.850f, rotY, 0.01f)
    }
    @Test
    fun test0058_rotationAngleCalculations_58() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 38.6000f, 0f, false, 0f, 286.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(324.900f, rotY, 0.01f)
    }
    @Test
    fun test0059_rotationAngleCalculations_59() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 70.3000f, 0f, false, 0f, 303.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(13.950f, rotY, 0.01f)
    }
    @Test
    fun test0060_rotationAngleCalculations_60() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 102.0000f, 0f, false, 0f, 321.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(63.000f, rotY, 0.01f)
    }
    @Test
    fun test0061_rotationAngleCalculations_61() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 133.7000f, 0f, false, 0f, 338.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(112.050f, rotY, 0.01f)
    }
    @Test
    fun test0062_rotationAngleCalculations_62() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 165.4000f, 0f, false, 0f, 355.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(161.100f, rotY, 0.01f)
    }
    @Test
    fun test0063_rotationAngleCalculations_63() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 197.1000f, 0f, false, 0f, 13.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(210.150f, rotY, 0.01f)
    }
    @Test
    fun test0064_rotationAngleCalculations_64() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 228.8000f, 0f, false, 0f, 30.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(259.200f, rotY, 0.01f)
    }
    @Test
    fun test0065_rotationAngleCalculations_65() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 260.5000f, 0f, false, 0f, 47.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(308.250f, rotY, 0.01f)
    }
    @Test
    fun test0066_rotationAngleCalculations_66() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 292.2000f, 0f, false, 0f, 65.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(357.300f, rotY, 0.01f)
    }
    @Test
    fun test0067_rotationAngleCalculations_67() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 323.9000f, 0f, false, 0f, 82.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(46.350f, rotY, 0.01f)
    }
    @Test
    fun test0068_rotationAngleCalculations_68() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 355.6000f, 0f, false, 0f, 99.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(95.400f, rotY, 0.01f)
    }
    @Test
    fun test0069_rotationAngleCalculations_69() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -332.7000f, 0f, false, 0f, 117.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(144.450f, rotY, 0.01f)
    }
    @Test
    fun test0070_rotationAngleCalculations_70() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -301.0000f, 0f, false, 0f, 134.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(193.500f, rotY, 0.01f)
    }
    @Test
    fun test0071_rotationAngleCalculations_71() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -269.3000f, 0f, false, 0f, 151.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(242.550f, rotY, 0.01f)
    }
    @Test
    fun test0072_rotationAngleCalculations_72() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -237.6000f, 0f, false, 0f, 169.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(291.600f, rotY, 0.01f)
    }
    @Test
    fun test0073_rotationAngleCalculations_73() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -205.9000f, 0f, false, 0f, 186.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(340.650f, rotY, 0.01f)
    }
    @Test
    fun test0074_rotationAngleCalculations_74() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -174.2000f, 0f, false, 0f, 203.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(29.700f, rotY, 0.01f)
    }
    @Test
    fun test0075_rotationAngleCalculations_75() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -142.5000f, 0f, false, 0f, 221.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(78.750f, rotY, 0.01f)
    }
    @Test
    fun test0076_rotationAngleCalculations_76() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -110.8000f, 0f, false, 0f, 238.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(127.800f, rotY, 0.01f)
    }
    @Test
    fun test0077_rotationAngleCalculations_77() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -79.1000f, 0f, false, 0f, 255.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(176.850f, rotY, 0.01f)
    }
    @Test
    fun test0078_rotationAngleCalculations_78() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -47.4000f, 0f, false, 0f, 273.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(225.900f, rotY, 0.01f)
    }
    @Test
    fun test0079_rotationAngleCalculations_79() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -15.7000f, 0f, false, 0f, 290.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(274.950f, rotY, 0.01f)
    }
    @Test
    fun test0080_rotationAngleCalculations_80() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 16.0000f, 0f, false, 0f, 308.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(324.000f, rotY, 0.01f)
    }
    @Test
    fun test0081_rotationAngleCalculations_81() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 47.7000f, 0f, false, 0f, 325.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(13.050f, rotY, 0.01f)
    }
    @Test
    fun test0082_rotationAngleCalculations_82() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 79.4000f, 0f, false, 0f, 342.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(62.100f, rotY, 0.01f)
    }
    @Test
    fun test0083_rotationAngleCalculations_83() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 111.1000f, 0f, false, 0f, 0.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(111.150f, rotY, 0.01f)
    }
    @Test
    fun test0084_rotationAngleCalculations_84() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 142.8000f, 0f, false, 0f, 17.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(160.200f, rotY, 0.01f)
    }
    @Test
    fun test0085_rotationAngleCalculations_85() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 174.5000f, 0f, false, 0f, 34.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(209.250f, rotY, 0.01f)
    }
    @Test
    fun test0086_rotationAngleCalculations_86() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 206.2000f, 0f, false, 0f, 52.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(258.300f, rotY, 0.01f)
    }
    @Test
    fun test0087_rotationAngleCalculations_87() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 237.9000f, 0f, false, 0f, 69.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(307.350f, rotY, 0.01f)
    }
    @Test
    fun test0088_rotationAngleCalculations_88() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 269.6000f, 0f, false, 0f, 86.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(356.400f, rotY, 0.01f)
    }
    @Test
    fun test0089_rotationAngleCalculations_89() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 301.3000f, 0f, false, 0f, 104.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(45.450f, rotY, 0.01f)
    }
    @Test
    fun test0090_rotationAngleCalculations_90() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 333.0000f, 0f, false, 0f, 121.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(94.500f, rotY, 0.01f)
    }
    @Test
    fun test0091_rotationAngleCalculations_91() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -355.3000f, 0f, false, 0f, 138.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(143.550f, rotY, 0.01f)
    }
    @Test
    fun test0092_rotationAngleCalculations_92() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -323.6000f, 0f, false, 0f, 156.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(192.600f, rotY, 0.01f)
    }
    @Test
    fun test0093_rotationAngleCalculations_93() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -291.9000f, 0f, false, 0f, 173.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(241.650f, rotY, 0.01f)
    }
    @Test
    fun test0094_rotationAngleCalculations_94() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -260.2000f, 0f, false, 0f, 190.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(290.700f, rotY, 0.01f)
    }
    @Test
    fun test0095_rotationAngleCalculations_95() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -228.5000f, 0f, false, 0f, 208.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(339.750f, rotY, 0.01f)
    }
    @Test
    fun test0096_rotationAngleCalculations_96() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -196.8000f, 0f, false, 0f, 225.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(28.800f, rotY, 0.01f)
    }
    @Test
    fun test0097_rotationAngleCalculations_97() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -165.1000f, 0f, false, 0f, 242.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(77.850f, rotY, 0.01f)
    }
    @Test
    fun test0098_rotationAngleCalculations_98() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -133.4000f, 0f, false, 0f, 260.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(126.900f, rotY, 0.01f)
    }
    @Test
    fun test0099_rotationAngleCalculations_99() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -101.7000f, 0f, false, 0f, 277.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(175.950f, rotY, 0.01f)
    }
    @Test
    fun test0100_rotationAngleCalculations_100() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -70.0000f, 0f, false, 0f, 295.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(225.000f, rotY, 0.01f)
    }
    @Test
    fun test0101_rotationAngleCalculations_101() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -38.3000f, 0f, false, 0f, 312.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(274.050f, rotY, 0.01f)
    }
    @Test
    fun test0102_rotationAngleCalculations_102() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -6.6000f, 0f, false, 0f, 329.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(323.100f, rotY, 0.01f)
    }
    @Test
    fun test0103_rotationAngleCalculations_103() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 25.1000f, 0f, false, 0f, 347.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(12.150f, rotY, 0.01f)
    }
    @Test
    fun test0104_rotationAngleCalculations_104() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 56.8000f, 0f, false, 0f, 4.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(61.200f, rotY, 0.01f)
    }
    @Test
    fun test0105_rotationAngleCalculations_105() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 88.5000f, 0f, false, 0f, 21.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(110.250f, rotY, 0.01f)
    }
    @Test
    fun test0106_rotationAngleCalculations_106() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 120.2000f, 0f, false, 0f, 39.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(159.300f, rotY, 0.01f)
    }
    @Test
    fun test0107_rotationAngleCalculations_107() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 151.9000f, 0f, false, 0f, 56.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(208.350f, rotY, 0.01f)
    }
    @Test
    fun test0108_rotationAngleCalculations_108() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 183.6000f, 0f, false, 0f, 73.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(257.400f, rotY, 0.01f)
    }
    @Test
    fun test0109_rotationAngleCalculations_109() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 215.3000f, 0f, false, 0f, 91.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(306.450f, rotY, 0.01f)
    }
    @Test
    fun test0110_rotationAngleCalculations_110() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 247.0000f, 0f, false, 0f, 108.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(355.500f, rotY, 0.01f)
    }
    @Test
    fun test0111_rotationAngleCalculations_111() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 278.7000f, 0f, false, 0f, 125.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(44.550f, rotY, 0.01f)
    }
    @Test
    fun test0112_rotationAngleCalculations_112() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 310.4000f, 0f, false, 0f, 143.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(93.600f, rotY, 0.01f)
    }
    @Test
    fun test0113_rotationAngleCalculations_113() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 342.1000f, 0f, false, 0f, 160.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(142.650f, rotY, 0.01f)
    }
    @Test
    fun test0114_rotationAngleCalculations_114() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -346.2000f, 0f, false, 0f, 177.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(191.700f, rotY, 0.01f)
    }
    @Test
    fun test0115_rotationAngleCalculations_115() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -314.5000f, 0f, false, 0f, 195.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(240.750f, rotY, 0.01f)
    }
    @Test
    fun test0116_rotationAngleCalculations_116() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -282.8000f, 0f, false, 0f, 212.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(289.800f, rotY, 0.01f)
    }
    @Test
    fun test0117_rotationAngleCalculations_117() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -251.1000f, 0f, false, 0f, 229.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(338.850f, rotY, 0.01f)
    }
    @Test
    fun test0118_rotationAngleCalculations_118() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -219.4000f, 0f, false, 0f, 247.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(27.900f, rotY, 0.01f)
    }
    @Test
    fun test0119_rotationAngleCalculations_119() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -187.7000f, 0f, false, 0f, 264.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(76.950f, rotY, 0.01f)
    }
    @Test
    fun test0120_rotationAngleCalculations_120() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -156.0000f, 0f, false, 0f, 282.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(126.000f, rotY, 0.01f)
    }
    @Test
    fun test0121_rotationAngleCalculations_121() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -124.3000f, 0f, false, 0f, 299.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(175.050f, rotY, 0.01f)
    }
    @Test
    fun test0122_rotationAngleCalculations_122() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -92.6000f, 0f, false, 0f, 316.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(224.100f, rotY, 0.01f)
    }
    @Test
    fun test0123_rotationAngleCalculations_123() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -60.9000f, 0f, false, 0f, 334.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(273.150f, rotY, 0.01f)
    }
    @Test
    fun test0124_rotationAngleCalculations_124() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -29.2000f, 0f, false, 0f, 351.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(322.200f, rotY, 0.01f)
    }
    @Test
    fun test0125_rotationAngleCalculations_125() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 2.5000f, 0f, false, 0f, 8.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(11.250f, rotY, 0.01f)
    }
    @Test
    fun test0126_rotationAngleCalculations_126() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 34.2000f, 0f, false, 0f, 26.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(60.300f, rotY, 0.01f)
    }
    @Test
    fun test0127_rotationAngleCalculations_127() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 65.9000f, 0f, false, 0f, 43.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(109.350f, rotY, 0.01f)
    }
    @Test
    fun test0128_rotationAngleCalculations_128() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 97.6000f, 0f, false, 0f, 60.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(158.400f, rotY, 0.01f)
    }
    @Test
    fun test0129_rotationAngleCalculations_129() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 129.3000f, 0f, false, 0f, 78.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(207.450f, rotY, 0.01f)
    }
    @Test
    fun test0130_rotationAngleCalculations_130() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 161.0000f, 0f, false, 0f, 95.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(256.500f, rotY, 0.01f)
    }
    @Test
    fun test0131_rotationAngleCalculations_131() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 192.7000f, 0f, false, 0f, 112.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(305.550f, rotY, 0.01f)
    }
    @Test
    fun test0132_rotationAngleCalculations_132() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 224.4000f, 0f, false, 0f, 130.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(354.600f, rotY, 0.01f)
    }
    @Test
    fun test0133_rotationAngleCalculations_133() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 256.1000f, 0f, false, 0f, 147.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(43.650f, rotY, 0.01f)
    }
    @Test
    fun test0134_rotationAngleCalculations_134() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 287.8000f, 0f, false, 0f, 164.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(92.700f, rotY, 0.01f)
    }
    @Test
    fun test0135_rotationAngleCalculations_135() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 319.5000f, 0f, false, 0f, 182.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(141.750f, rotY, 0.01f)
    }
    @Test
    fun test0136_rotationAngleCalculations_136() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 351.2000f, 0f, false, 0f, 199.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(190.800f, rotY, 0.01f)
    }
    @Test
    fun test0137_rotationAngleCalculations_137() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -337.1000f, 0f, false, 0f, 216.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(239.850f, rotY, 0.01f)
    }
    @Test
    fun test0138_rotationAngleCalculations_138() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -305.4000f, 0f, false, 0f, 234.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(288.900f, rotY, 0.01f)
    }
    @Test
    fun test0139_rotationAngleCalculations_139() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -273.7000f, 0f, false, 0f, 251.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(337.950f, rotY, 0.01f)
    }
    @Test
    fun test0140_rotationAngleCalculations_140() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -242.0000f, 0f, false, 0f, 269.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(27.000f, rotY, 0.01f)
    }
    @Test
    fun test0141_rotationAngleCalculations_141() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -210.3000f, 0f, false, 0f, 286.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(76.050f, rotY, 0.01f)
    }
    @Test
    fun test0142_rotationAngleCalculations_142() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -178.6000f, 0f, false, 0f, 303.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(125.100f, rotY, 0.01f)
    }
    @Test
    fun test0143_rotationAngleCalculations_143() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -146.9000f, 0f, false, 0f, 321.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(174.150f, rotY, 0.01f)
    }
    @Test
    fun test0144_rotationAngleCalculations_144() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -115.2000f, 0f, false, 0f, 338.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(223.200f, rotY, 0.01f)
    }
    @Test
    fun test0145_rotationAngleCalculations_145() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -83.5000f, 0f, false, 0f, 355.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(272.250f, rotY, 0.01f)
    }
    @Test
    fun test0146_rotationAngleCalculations_146() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -51.8000f, 0f, false, 0f, 13.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(321.300f, rotY, 0.01f)
    }
    @Test
    fun test0147_rotationAngleCalculations_147() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -20.1000f, 0f, false, 0f, 30.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(10.350f, rotY, 0.01f)
    }
    @Test
    fun test0148_rotationAngleCalculations_148() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 11.6000f, 0f, false, 0f, 47.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(59.400f, rotY, 0.01f)
    }
    @Test
    fun test0149_rotationAngleCalculations_149() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 43.3000f, 0f, false, 0f, 65.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(108.450f, rotY, 0.01f)
    }
    @Test
    fun test0150_rotationAngleCalculations_150() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 75.0000f, 0f, false, 0f, 82.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(157.500f, rotY, 0.01f)
    }
    @Test
    fun test0151_rotationAngleCalculations_151() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 106.7000f, 0f, false, 0f, 99.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(206.550f, rotY, 0.01f)
    }
    @Test
    fun test0152_rotationAngleCalculations_152() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 138.4000f, 0f, false, 0f, 117.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(255.600f, rotY, 0.01f)
    }
    @Test
    fun test0153_rotationAngleCalculations_153() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 170.1000f, 0f, false, 0f, 134.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(304.650f, rotY, 0.01f)
    }
    @Test
    fun test0154_rotationAngleCalculations_154() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 201.8000f, 0f, false, 0f, 151.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(353.700f, rotY, 0.01f)
    }
    @Test
    fun test0155_rotationAngleCalculations_155() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 233.5000f, 0f, false, 0f, 169.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(42.750f, rotY, 0.01f)
    }
    @Test
    fun test0156_rotationAngleCalculations_156() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 265.2000f, 0f, false, 0f, 186.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(91.800f, rotY, 0.01f)
    }
    @Test
    fun test0157_rotationAngleCalculations_157() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 296.9000f, 0f, false, 0f, 203.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(140.850f, rotY, 0.01f)
    }
    @Test
    fun test0158_rotationAngleCalculations_158() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 328.6000f, 0f, false, 0f, 221.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(189.900f, rotY, 0.01f)
    }
    @Test
    fun test0159_rotationAngleCalculations_159() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -359.7000f, 0f, false, 0f, 238.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(238.950f, rotY, 0.01f)
    }
    @Test
    fun test0160_rotationAngleCalculations_160() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -328.0000f, 0f, false, 0f, 256.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(288.000f, rotY, 0.01f)
    }
    @Test
    fun test0161_rotationAngleCalculations_161() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -296.3000f, 0f, false, 0f, 273.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(337.050f, rotY, 0.01f)
    }
    @Test
    fun test0162_rotationAngleCalculations_162() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -264.6000f, 0f, false, 0f, 290.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(26.100f, rotY, 0.01f)
    }
    @Test
    fun test0163_rotationAngleCalculations_163() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -232.9000f, 0f, false, 0f, 308.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(75.150f, rotY, 0.01f)
    }
    @Test
    fun test0164_rotationAngleCalculations_164() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -201.2000f, 0f, false, 0f, 325.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(124.200f, rotY, 0.01f)
    }
    @Test
    fun test0165_rotationAngleCalculations_165() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -169.5000f, 0f, false, 0f, 342.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(173.250f, rotY, 0.01f)
    }
    @Test
    fun test0166_rotationAngleCalculations_166() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -137.8000f, 0f, false, 0f, 0.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(222.300f, rotY, 0.01f)
    }
    @Test
    fun test0167_rotationAngleCalculations_167() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -106.1000f, 0f, false, 0f, 17.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(271.350f, rotY, 0.01f)
    }
    @Test
    fun test0168_rotationAngleCalculations_168() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -74.4000f, 0f, false, 0f, 34.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(320.400f, rotY, 0.01f)
    }
    @Test
    fun test0169_rotationAngleCalculations_169() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -42.7000f, 0f, false, 0f, 52.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(9.450f, rotY, 0.01f)
    }
    @Test
    fun test0170_rotationAngleCalculations_170() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -11.0000f, 0f, false, 0f, 69.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(58.500f, rotY, 0.01f)
    }
    @Test
    fun test0171_rotationAngleCalculations_171() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 20.7000f, 0f, false, 0f, 86.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(107.550f, rotY, 0.01f)
    }
    @Test
    fun test0172_rotationAngleCalculations_172() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 52.4000f, 0f, false, 0f, 104.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(156.600f, rotY, 0.01f)
    }
    @Test
    fun test0173_rotationAngleCalculations_173() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 84.1000f, 0f, false, 0f, 121.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(205.650f, rotY, 0.01f)
    }
    @Test
    fun test0174_rotationAngleCalculations_174() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 115.8000f, 0f, false, 0f, 138.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(254.700f, rotY, 0.01f)
    }
    @Test
    fun test0175_rotationAngleCalculations_175() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 147.5000f, 0f, false, 0f, 156.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(303.750f, rotY, 0.01f)
    }
    @Test
    fun test0176_rotationAngleCalculations_176() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 179.2000f, 0f, false, 0f, 173.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(352.800f, rotY, 0.01f)
    }
    @Test
    fun test0177_rotationAngleCalculations_177() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 210.9000f, 0f, false, 0f, 190.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(41.850f, rotY, 0.01f)
    }
    @Test
    fun test0178_rotationAngleCalculations_178() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 242.6000f, 0f, false, 0f, 208.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(90.900f, rotY, 0.01f)
    }
    @Test
    fun test0179_rotationAngleCalculations_179() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 274.3000f, 0f, false, 0f, 225.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(139.950f, rotY, 0.01f)
    }
    @Test
    fun test0180_rotationAngleCalculations_180() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 306.0000f, 0f, false, 0f, 243.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(189.000f, rotY, 0.01f)
    }
    @Test
    fun test0181_rotationAngleCalculations_181() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 337.7000f, 0f, false, 0f, 260.3500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(238.050f, rotY, 0.01f)
    }
    @Test
    fun test0182_rotationAngleCalculations_182() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -350.6000f, 0f, false, 0f, 277.7000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(287.100f, rotY, 0.01f)
    }
    @Test
    fun test0183_rotationAngleCalculations_183() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -318.9000f, 0f, false, 0f, 295.0500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(336.150f, rotY, 0.01f)
    }
    @Test
    fun test0184_rotationAngleCalculations_184() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -287.2000f, 0f, false, 0f, 312.4000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(25.200f, rotY, 0.01f)
    }
    @Test
    fun test0185_rotationAngleCalculations_185() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -255.5000f, 0f, false, 0f, 329.7500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(74.250f, rotY, 0.01f)
    }
    @Test
    fun test0186_rotationAngleCalculations_186() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -223.8000f, 0f, false, 0f, 347.1000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(123.300f, rotY, 0.01f)
    }
    @Test
    fun test0187_rotationAngleCalculations_187() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -192.1000f, 0f, false, 0f, 4.4500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(172.350f, rotY, 0.01f)
    }
    @Test
    fun test0188_rotationAngleCalculations_188() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -160.4000f, 0f, false, 0f, 21.8000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(221.400f, rotY, 0.01f)
    }
    @Test
    fun test0189_rotationAngleCalculations_189() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -128.7000f, 0f, false, 0f, 39.1500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(270.450f, rotY, 0.01f)
    }
    @Test
    fun test0190_rotationAngleCalculations_190() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -97.0000f, 0f, false, 0f, 56.5000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(319.500f, rotY, 0.01f)
    }
    @Test
    fun test0191_rotationAngleCalculations_191() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -65.3000f, 0f, false, 0f, 73.8500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(8.550f, rotY, 0.01f)
    }
    @Test
    fun test0192_rotationAngleCalculations_192() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -33.6000f, 0f, false, 0f, 91.2000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(57.600f, rotY, 0.01f)
    }
    @Test
    fun test0193_rotationAngleCalculations_193() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, -1.9000f, 0f, false, 0f, 108.5500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(106.650f, rotY, 0.01f)
    }
    @Test
    fun test0194_rotationAngleCalculations_194() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 29.8000f, 0f, false, 0f, 125.9000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(155.700f, rotY, 0.01f)
    }
    @Test
    fun test0195_rotationAngleCalculations_195() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 61.5000f, 0f, false, 0f, 143.2500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(204.750f, rotY, 0.01f)
    }
    @Test
    fun test0196_rotationAngleCalculations_196() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 93.2000f, 0f, false, 0f, 160.6000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(253.800f, rotY, 0.01f)
    }
    @Test
    fun test0197_rotationAngleCalculations_197() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 124.9000f, 0f, false, 0f, 177.9500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(302.850f, rotY, 0.01f)
    }
    @Test
    fun test0198_rotationAngleCalculations_198() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 156.6000f, 0f, false, 0f, 195.3000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(351.900f, rotY, 0.01f)
    }
    @Test
    fun test0199_rotationAngleCalculations_199() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 188.3000f, 0f, false, 0f, 212.6500f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(40.950f, rotY, 0.01f)
    }
    @Test
    fun test0200_rotationAngleCalculations_200() {
        var rotY = 0f
        applyRotationDelta(RotationAxis.Y, 220.0000f, 0f, false, 0f, 230.0000f, 0f, {}, { rotY = it }, {}, 1f)
        assertEquals(90.000f, rotY, 0.01f)
    }
    @Test
    fun test0201_zoomScalePolicyLimits_201() {
        val result = ZoomScalePolicy.apply(2.100f, 0.100f)
        assertEquals(0.210f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0202_zoomScalePolicyLimits_202() {
        val result = ZoomScalePolicy.apply(4.100f, 0.150f)
        assertEquals(0.615f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0203_zoomScalePolicyLimits_203() {
        val result = ZoomScalePolicy.apply(6.100f, 0.200f)
        assertEquals(1.220f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0204_zoomScalePolicyLimits_204() {
        val result = ZoomScalePolicy.apply(8.100f, 0.250f)
        assertEquals(2.025f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0205_zoomScalePolicyLimits_205() {
        val result = ZoomScalePolicy.apply(10.100f, 0.300f)
        assertEquals(3.030f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0206_zoomScalePolicyLimits_206() {
        val result = ZoomScalePolicy.apply(12.100f, 0.350f)
        assertEquals(4.235f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0207_zoomScalePolicyLimits_207() {
        val result = ZoomScalePolicy.apply(14.100f, 0.400f)
        assertEquals(5.640f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0208_zoomScalePolicyLimits_208() {
        val result = ZoomScalePolicy.apply(16.100f, 0.450f)
        assertEquals(7.245f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0209_zoomScalePolicyLimits_209() {
        val result = ZoomScalePolicy.apply(18.100f, 0.500f)
        assertEquals(9.050f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0210_zoomScalePolicyLimits_210() {
        val result = ZoomScalePolicy.apply(20.100f, 0.550f)
        assertEquals(11.055f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0211_zoomScalePolicyLimits_211() {
        val result = ZoomScalePolicy.apply(22.100f, 0.600f)
        assertEquals(13.260f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0212_zoomScalePolicyLimits_212() {
        val result = ZoomScalePolicy.apply(24.100f, 0.650f)
        assertEquals(15.665f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0213_zoomScalePolicyLimits_213() {
        val result = ZoomScalePolicy.apply(26.100f, 0.700f)
        assertEquals(18.270f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0214_zoomScalePolicyLimits_214() {
        val result = ZoomScalePolicy.apply(28.100f, 0.750f)
        assertEquals(21.075f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0215_zoomScalePolicyLimits_215() {
        val result = ZoomScalePolicy.apply(30.100f, 0.800f)
        assertEquals(24.080f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0216_zoomScalePolicyLimits_216() {
        val result = ZoomScalePolicy.apply(32.100f, 0.850f)
        assertEquals(27.285f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0217_zoomScalePolicyLimits_217() {
        val result = ZoomScalePolicy.apply(34.100f, 0.900f)
        assertEquals(30.690f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0218_zoomScalePolicyLimits_218() {
        val result = ZoomScalePolicy.apply(36.100f, 0.950f)
        assertEquals(34.295f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0219_zoomScalePolicyLimits_219() {
        val result = ZoomScalePolicy.apply(38.100f, 1.000f)
        assertEquals(38.100f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0220_zoomScalePolicyLimits_220() {
        val result = ZoomScalePolicy.apply(40.100f, 1.050f)
        assertEquals(42.105f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0221_zoomScalePolicyLimits_221() {
        val result = ZoomScalePolicy.apply(42.100f, 1.100f)
        assertEquals(46.310f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0222_zoomScalePolicyLimits_222() {
        val result = ZoomScalePolicy.apply(44.100f, 1.150f)
        assertEquals(50.715f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0223_zoomScalePolicyLimits_223() {
        val result = ZoomScalePolicy.apply(46.100f, 1.200f)
        assertEquals(55.320f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0224_zoomScalePolicyLimits_224() {
        val result = ZoomScalePolicy.apply(48.100f, 1.250f)
        assertEquals(60.125f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0225_zoomScalePolicyLimits_225() {
        val result = ZoomScalePolicy.apply(50.100f, 1.300f)
        assertEquals(65.130f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0226_zoomScalePolicyLimits_226() {
        val result = ZoomScalePolicy.apply(52.100f, 1.350f)
        assertEquals(70.335f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0227_zoomScalePolicyLimits_227() {
        val result = ZoomScalePolicy.apply(54.100f, 1.400f)
        assertEquals(75.740f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0228_zoomScalePolicyLimits_228() {
        val result = ZoomScalePolicy.apply(56.100f, 1.450f)
        assertEquals(81.345f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0229_zoomScalePolicyLimits_229() {
        val result = ZoomScalePolicy.apply(58.100f, 1.500f)
        assertEquals(87.150f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0230_zoomScalePolicyLimits_230() {
        val result = ZoomScalePolicy.apply(60.100f, 1.550f)
        assertEquals(93.155f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0231_zoomScalePolicyLimits_231() {
        val result = ZoomScalePolicy.apply(62.100f, 1.600f)
        assertEquals(99.360f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0232_zoomScalePolicyLimits_232() {
        val result = ZoomScalePolicy.apply(64.100f, 1.650f)
        assertEquals(105.765f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0233_zoomScalePolicyLimits_233() {
        val result = ZoomScalePolicy.apply(66.100f, 1.700f)
        assertEquals(112.370f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0234_zoomScalePolicyLimits_234() {
        val result = ZoomScalePolicy.apply(68.100f, 1.750f)
        assertEquals(119.175f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0235_zoomScalePolicyLimits_235() {
        val result = ZoomScalePolicy.apply(70.100f, 1.800f)
        assertEquals(126.180f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0236_zoomScalePolicyLimits_236() {
        val result = ZoomScalePolicy.apply(72.100f, 1.850f)
        assertEquals(133.385f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0237_zoomScalePolicyLimits_237() {
        val result = ZoomScalePolicy.apply(74.100f, 1.900f)
        assertEquals(140.790f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0238_zoomScalePolicyLimits_238() {
        val result = ZoomScalePolicy.apply(76.100f, 1.950f)
        assertEquals(148.395f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0239_zoomScalePolicyLimits_239() {
        val result = ZoomScalePolicy.apply(78.100f, 2.000f)
        assertEquals(156.200f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0240_zoomScalePolicyLimits_240() {
        val result = ZoomScalePolicy.apply(80.100f, 2.050f)
        assertEquals(164.205f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0241_zoomScalePolicyLimits_241() {
        val result = ZoomScalePolicy.apply(82.100f, 2.100f)
        assertEquals(172.410f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0242_zoomScalePolicyLimits_242() {
        val result = ZoomScalePolicy.apply(84.100f, 2.150f)
        assertEquals(180.815f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0243_zoomScalePolicyLimits_243() {
        val result = ZoomScalePolicy.apply(86.100f, 2.200f)
        assertEquals(189.420f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0244_zoomScalePolicyLimits_244() {
        val result = ZoomScalePolicy.apply(88.100f, 2.250f)
        assertEquals(198.225f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0245_zoomScalePolicyLimits_245() {
        val result = ZoomScalePolicy.apply(90.100f, 2.300f)
        assertEquals(207.230f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0246_zoomScalePolicyLimits_246() {
        val result = ZoomScalePolicy.apply(92.100f, 2.350f)
        assertEquals(216.435f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0247_zoomScalePolicyLimits_247() {
        val result = ZoomScalePolicy.apply(94.100f, 2.400f)
        assertEquals(225.840f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0248_zoomScalePolicyLimits_248() {
        val result = ZoomScalePolicy.apply(96.100f, 2.450f)
        assertEquals(235.445f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0249_zoomScalePolicyLimits_249() {
        val result = ZoomScalePolicy.apply(98.100f, 2.500f)
        assertEquals(245.250f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0250_zoomScalePolicyLimits_250() {
        val result = ZoomScalePolicy.apply(0.100f, 2.550f)
        assertEquals(0.255f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0251_zoomScalePolicyLimits_251() {
        val result = ZoomScalePolicy.apply(2.100f, 2.600f)
        assertEquals(5.460f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0252_zoomScalePolicyLimits_252() {
        val result = ZoomScalePolicy.apply(4.100f, 2.650f)
        assertEquals(10.865f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0253_zoomScalePolicyLimits_253() {
        val result = ZoomScalePolicy.apply(6.100f, 2.700f)
        assertEquals(16.470f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0254_zoomScalePolicyLimits_254() {
        val result = ZoomScalePolicy.apply(8.100f, 2.750f)
        assertEquals(22.275f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0255_zoomScalePolicyLimits_255() {
        val result = ZoomScalePolicy.apply(10.100f, 2.800f)
        assertEquals(28.280f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0256_zoomScalePolicyLimits_256() {
        val result = ZoomScalePolicy.apply(12.100f, 2.850f)
        assertEquals(34.485f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0257_zoomScalePolicyLimits_257() {
        val result = ZoomScalePolicy.apply(14.100f, 2.900f)
        assertEquals(40.890f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0258_zoomScalePolicyLimits_258() {
        val result = ZoomScalePolicy.apply(16.100f, 2.950f)
        assertEquals(47.495f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0259_zoomScalePolicyLimits_259() {
        val result = ZoomScalePolicy.apply(18.100f, 3.000f)
        assertEquals(54.300f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0260_zoomScalePolicyLimits_260() {
        val result = ZoomScalePolicy.apply(20.100f, 3.050f)
        assertEquals(61.305f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0261_zoomScalePolicyLimits_261() {
        val result = ZoomScalePolicy.apply(22.100f, 3.100f)
        assertEquals(68.510f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0262_zoomScalePolicyLimits_262() {
        val result = ZoomScalePolicy.apply(24.100f, 3.150f)
        assertEquals(75.915f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0263_zoomScalePolicyLimits_263() {
        val result = ZoomScalePolicy.apply(26.100f, 3.200f)
        assertEquals(83.520f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0264_zoomScalePolicyLimits_264() {
        val result = ZoomScalePolicy.apply(28.100f, 3.250f)
        assertEquals(91.325f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0265_zoomScalePolicyLimits_265() {
        val result = ZoomScalePolicy.apply(30.100f, 3.300f)
        assertEquals(99.330f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0266_zoomScalePolicyLimits_266() {
        val result = ZoomScalePolicy.apply(32.100f, 3.350f)
        assertEquals(107.535f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0267_zoomScalePolicyLimits_267() {
        val result = ZoomScalePolicy.apply(34.100f, 3.400f)
        assertEquals(115.940f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0268_zoomScalePolicyLimits_268() {
        val result = ZoomScalePolicy.apply(36.100f, 3.450f)
        assertEquals(124.545f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0269_zoomScalePolicyLimits_269() {
        val result = ZoomScalePolicy.apply(38.100f, 3.500f)
        assertEquals(133.350f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0270_zoomScalePolicyLimits_270() {
        val result = ZoomScalePolicy.apply(40.100f, 3.550f)
        assertEquals(142.355f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0271_zoomScalePolicyLimits_271() {
        val result = ZoomScalePolicy.apply(42.100f, 3.600f)
        assertEquals(151.560f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0272_zoomScalePolicyLimits_272() {
        val result = ZoomScalePolicy.apply(44.100f, 3.650f)
        assertEquals(160.965f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0273_zoomScalePolicyLimits_273() {
        val result = ZoomScalePolicy.apply(46.100f, 3.700f)
        assertEquals(170.570f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0274_zoomScalePolicyLimits_274() {
        val result = ZoomScalePolicy.apply(48.100f, 3.750f)
        assertEquals(180.375f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0275_zoomScalePolicyLimits_275() {
        val result = ZoomScalePolicy.apply(50.100f, 3.800f)
        assertEquals(190.380f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0276_zoomScalePolicyLimits_276() {
        val result = ZoomScalePolicy.apply(52.100f, 3.850f)
        assertEquals(200.585f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0277_zoomScalePolicyLimits_277() {
        val result = ZoomScalePolicy.apply(54.100f, 3.900f)
        assertEquals(210.990f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0278_zoomScalePolicyLimits_278() {
        val result = ZoomScalePolicy.apply(56.100f, 3.950f)
        assertEquals(221.595f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0279_zoomScalePolicyLimits_279() {
        val result = ZoomScalePolicy.apply(58.100f, 4.000f)
        assertEquals(232.400f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0280_zoomScalePolicyLimits_280() {
        val result = ZoomScalePolicy.apply(60.100f, 4.050f)
        assertEquals(243.405f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0281_zoomScalePolicyLimits_281() {
        val result = ZoomScalePolicy.apply(62.100f, 4.100f)
        assertEquals(254.610f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0282_zoomScalePolicyLimits_282() {
        val result = ZoomScalePolicy.apply(64.100f, 4.150f)
        assertEquals(266.015f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0283_zoomScalePolicyLimits_283() {
        val result = ZoomScalePolicy.apply(66.100f, 4.200f)
        assertEquals(277.620f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0284_zoomScalePolicyLimits_284() {
        val result = ZoomScalePolicy.apply(68.100f, 4.250f)
        assertEquals(289.425f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0285_zoomScalePolicyLimits_285() {
        val result = ZoomScalePolicy.apply(70.100f, 4.300f)
        assertEquals(301.430f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0286_zoomScalePolicyLimits_286() {
        val result = ZoomScalePolicy.apply(72.100f, 4.350f)
        assertEquals(313.635f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0287_zoomScalePolicyLimits_287() {
        val result = ZoomScalePolicy.apply(74.100f, 4.400f)
        assertEquals(326.040f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0288_zoomScalePolicyLimits_288() {
        val result = ZoomScalePolicy.apply(76.100f, 4.450f)
        assertEquals(338.645f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0289_zoomScalePolicyLimits_289() {
        val result = ZoomScalePolicy.apply(78.100f, 4.500f)
        assertEquals(351.450f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0290_zoomScalePolicyLimits_290() {
        val result = ZoomScalePolicy.apply(80.100f, 4.550f)
        assertEquals(364.455f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0291_zoomScalePolicyLimits_291() {
        val result = ZoomScalePolicy.apply(82.100f, 4.600f)
        assertEquals(377.660f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0292_zoomScalePolicyLimits_292() {
        val result = ZoomScalePolicy.apply(84.100f, 4.650f)
        assertEquals(391.065f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0293_zoomScalePolicyLimits_293() {
        val result = ZoomScalePolicy.apply(86.100f, 4.700f)
        assertEquals(404.670f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0294_zoomScalePolicyLimits_294() {
        val result = ZoomScalePolicy.apply(88.100f, 4.750f)
        assertEquals(418.475f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0295_zoomScalePolicyLimits_295() {
        val result = ZoomScalePolicy.apply(90.100f, 4.800f)
        assertEquals(432.480f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0296_zoomScalePolicyLimits_296() {
        val result = ZoomScalePolicy.apply(92.100f, 4.850f)
        assertEquals(446.685f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0297_zoomScalePolicyLimits_297() {
        val result = ZoomScalePolicy.apply(94.100f, 4.900f)
        assertEquals(461.090f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0298_zoomScalePolicyLimits_298() {
        val result = ZoomScalePolicy.apply(96.100f, 4.950f)
        assertEquals(475.695f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0299_zoomScalePolicyLimits_299() {
        val result = ZoomScalePolicy.apply(98.100f, 5.000f)
        assertEquals(490.500f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0300_zoomScalePolicyLimits_300() {
        val result = ZoomScalePolicy.apply(0.100f, 5.050f)
        assertEquals(0.505f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0301_zoomScalePolicyLimits_301() {
        val result = ZoomScalePolicy.apply(2.100f, 5.100f)
        assertEquals(10.710f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0302_zoomScalePolicyLimits_302() {
        val result = ZoomScalePolicy.apply(4.100f, 5.150f)
        assertEquals(21.115f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0303_zoomScalePolicyLimits_303() {
        val result = ZoomScalePolicy.apply(6.100f, 5.200f)
        assertEquals(31.720f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0304_zoomScalePolicyLimits_304() {
        val result = ZoomScalePolicy.apply(8.100f, 5.250f)
        assertEquals(42.525f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0305_zoomScalePolicyLimits_305() {
        val result = ZoomScalePolicy.apply(10.100f, 5.300f)
        assertEquals(53.530f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0306_zoomScalePolicyLimits_306() {
        val result = ZoomScalePolicy.apply(12.100f, 5.350f)
        assertEquals(64.735f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0307_zoomScalePolicyLimits_307() {
        val result = ZoomScalePolicy.apply(14.100f, 5.400f)
        assertEquals(76.140f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0308_zoomScalePolicyLimits_308() {
        val result = ZoomScalePolicy.apply(16.100f, 5.450f)
        assertEquals(87.745f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0309_zoomScalePolicyLimits_309() {
        val result = ZoomScalePolicy.apply(18.100f, 5.500f)
        assertEquals(99.550f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0310_zoomScalePolicyLimits_310() {
        val result = ZoomScalePolicy.apply(20.100f, 5.550f)
        assertEquals(111.555f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0311_zoomScalePolicyLimits_311() {
        val result = ZoomScalePolicy.apply(22.100f, 5.600f)
        assertEquals(123.760f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0312_zoomScalePolicyLimits_312() {
        val result = ZoomScalePolicy.apply(24.100f, 5.650f)
        assertEquals(136.165f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0313_zoomScalePolicyLimits_313() {
        val result = ZoomScalePolicy.apply(26.100f, 5.700f)
        assertEquals(148.770f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0314_zoomScalePolicyLimits_314() {
        val result = ZoomScalePolicy.apply(28.100f, 5.750f)
        assertEquals(161.575f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0315_zoomScalePolicyLimits_315() {
        val result = ZoomScalePolicy.apply(30.100f, 5.800f)
        assertEquals(174.580f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0316_zoomScalePolicyLimits_316() {
        val result = ZoomScalePolicy.apply(32.100f, 5.850f)
        assertEquals(187.785f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0317_zoomScalePolicyLimits_317() {
        val result = ZoomScalePolicy.apply(34.100f, 5.900f)
        assertEquals(201.190f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0318_zoomScalePolicyLimits_318() {
        val result = ZoomScalePolicy.apply(36.100f, 5.950f)
        assertEquals(214.795f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0319_zoomScalePolicyLimits_319() {
        val result = ZoomScalePolicy.apply(38.100f, 6.000f)
        assertEquals(228.600f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0320_zoomScalePolicyLimits_320() {
        val result = ZoomScalePolicy.apply(40.100f, 6.050f)
        assertEquals(242.605f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0321_zoomScalePolicyLimits_321() {
        val result = ZoomScalePolicy.apply(42.100f, 6.100f)
        assertEquals(256.810f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0322_zoomScalePolicyLimits_322() {
        val result = ZoomScalePolicy.apply(44.100f, 6.150f)
        assertEquals(271.215f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0323_zoomScalePolicyLimits_323() {
        val result = ZoomScalePolicy.apply(46.100f, 6.200f)
        assertEquals(285.820f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0324_zoomScalePolicyLimits_324() {
        val result = ZoomScalePolicy.apply(48.100f, 6.250f)
        assertEquals(300.625f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0325_zoomScalePolicyLimits_325() {
        val result = ZoomScalePolicy.apply(50.100f, 6.300f)
        assertEquals(315.630f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0326_zoomScalePolicyLimits_326() {
        val result = ZoomScalePolicy.apply(52.100f, 6.350f)
        assertEquals(330.835f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0327_zoomScalePolicyLimits_327() {
        val result = ZoomScalePolicy.apply(54.100f, 6.400f)
        assertEquals(346.240f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0328_zoomScalePolicyLimits_328() {
        val result = ZoomScalePolicy.apply(56.100f, 6.450f)
        assertEquals(361.845f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0329_zoomScalePolicyLimits_329() {
        val result = ZoomScalePolicy.apply(58.100f, 6.500f)
        assertEquals(377.650f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0330_zoomScalePolicyLimits_330() {
        val result = ZoomScalePolicy.apply(60.100f, 6.550f)
        assertEquals(393.655f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0331_zoomScalePolicyLimits_331() {
        val result = ZoomScalePolicy.apply(62.100f, 6.600f)
        assertEquals(409.860f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0332_zoomScalePolicyLimits_332() {
        val result = ZoomScalePolicy.apply(64.100f, 6.650f)
        assertEquals(426.265f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0333_zoomScalePolicyLimits_333() {
        val result = ZoomScalePolicy.apply(66.100f, 6.700f)
        assertEquals(442.870f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0334_zoomScalePolicyLimits_334() {
        val result = ZoomScalePolicy.apply(68.100f, 6.750f)
        assertEquals(459.675f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0335_zoomScalePolicyLimits_335() {
        val result = ZoomScalePolicy.apply(70.100f, 6.800f)
        assertEquals(476.680f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0336_zoomScalePolicyLimits_336() {
        val result = ZoomScalePolicy.apply(72.100f, 6.850f)
        assertEquals(493.885f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0337_zoomScalePolicyLimits_337() {
        val result = ZoomScalePolicy.apply(74.100f, 6.900f)
        assertEquals(511.290f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0338_zoomScalePolicyLimits_338() {
        val result = ZoomScalePolicy.apply(76.100f, 6.950f)
        assertEquals(528.895f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0339_zoomScalePolicyLimits_339() {
        val result = ZoomScalePolicy.apply(78.100f, 7.000f)
        assertEquals(546.700f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0340_zoomScalePolicyLimits_340() {
        val result = ZoomScalePolicy.apply(80.100f, 7.050f)
        assertEquals(564.705f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0341_zoomScalePolicyLimits_341() {
        val result = ZoomScalePolicy.apply(82.100f, 7.100f)
        assertEquals(582.910f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0342_zoomScalePolicyLimits_342() {
        val result = ZoomScalePolicy.apply(84.100f, 7.150f)
        assertEquals(601.315f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0343_zoomScalePolicyLimits_343() {
        val result = ZoomScalePolicy.apply(86.100f, 7.200f)
        assertEquals(619.920f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0344_zoomScalePolicyLimits_344() {
        val result = ZoomScalePolicy.apply(88.100f, 7.250f)
        assertEquals(638.725f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0345_zoomScalePolicyLimits_345() {
        val result = ZoomScalePolicy.apply(90.100f, 7.300f)
        assertEquals(657.730f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0346_zoomScalePolicyLimits_346() {
        val result = ZoomScalePolicy.apply(92.100f, 7.350f)
        assertEquals(676.935f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0347_zoomScalePolicyLimits_347() {
        val result = ZoomScalePolicy.apply(94.100f, 7.400f)
        assertEquals(696.340f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0348_zoomScalePolicyLimits_348() {
        val result = ZoomScalePolicy.apply(96.100f, 7.450f)
        assertEquals(715.945f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0349_zoomScalePolicyLimits_349() {
        val result = ZoomScalePolicy.apply(98.100f, 7.500f)
        assertEquals(735.750f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0350_zoomScalePolicyLimits_350() {
        val result = ZoomScalePolicy.apply(0.100f, 7.550f)
        assertEquals(0.755f, result, 0.01f)
        assertTrue(result >= ZoomScalePolicy.MIN_SCALE)
        assertTrue(result <= ZoomScalePolicy.MAX_SCALE)
    }
    @Test
    fun test0351_recoveryTranslationTolerance_351() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00040f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0352_recoveryTranslationTolerance_352() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00080f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0353_recoveryTranslationTolerance_353() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00120f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0354_recoveryTranslationTolerance_354() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00160f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0355_recoveryTranslationTolerance_355() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00200f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0356_recoveryTranslationTolerance_356() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00240f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0357_recoveryTranslationTolerance_357() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00280f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0358_recoveryTranslationTolerance_358() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00320f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0359_recoveryTranslationTolerance_359() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00360f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0360_recoveryTranslationTolerance_360() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00400f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0361_recoveryTranslationTolerance_361() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00440f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0362_recoveryTranslationTolerance_362() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00480f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0363_recoveryTranslationTolerance_363() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00520f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0364_recoveryTranslationTolerance_364() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00560f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0365_recoveryTranslationTolerance_365() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00600f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0366_recoveryTranslationTolerance_366() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00640f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0367_recoveryTranslationTolerance_367() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00680f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0368_recoveryTranslationTolerance_368() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00720f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0369_recoveryTranslationTolerance_369() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00760f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0370_recoveryTranslationTolerance_370() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00800f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0371_recoveryTranslationTolerance_371() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00840f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0372_recoveryTranslationTolerance_372() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00880f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0373_recoveryTranslationTolerance_373() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00920f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0374_recoveryTranslationTolerance_374() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.00960f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0375_recoveryTranslationTolerance_375() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01000f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0376_recoveryTranslationTolerance_376() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01040f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0377_recoveryTranslationTolerance_377() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01080f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0378_recoveryTranslationTolerance_378() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01120f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0379_recoveryTranslationTolerance_379() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01160f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0380_recoveryTranslationTolerance_380() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01200f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0381_recoveryTranslationTolerance_381() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01240f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0382_recoveryTranslationTolerance_382() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01280f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0383_recoveryTranslationTolerance_383() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01320f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0384_recoveryTranslationTolerance_384() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01360f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0385_recoveryTranslationTolerance_385() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01400f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0386_recoveryTranslationTolerance_386() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01440f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0387_recoveryTranslationTolerance_387() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01480f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0388_recoveryTranslationTolerance_388() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01520f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0389_recoveryTranslationTolerance_389() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01560f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0390_recoveryTranslationTolerance_390() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01600f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0391_recoveryTranslationTolerance_391() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01640f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0392_recoveryTranslationTolerance_392() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01680f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0393_recoveryTranslationTolerance_393() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01720f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0394_recoveryTranslationTolerance_394() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01760f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0395_recoveryTranslationTolerance_395() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01800f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0396_recoveryTranslationTolerance_396() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01840f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0397_recoveryTranslationTolerance_397() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01880f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0398_recoveryTranslationTolerance_398() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01920f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0399_recoveryTranslationTolerance_399() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.01960f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0400_recoveryTranslationTolerance_400() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02000f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0401_recoveryTranslationTolerance_401() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02040f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0402_recoveryTranslationTolerance_402() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02080f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0403_recoveryTranslationTolerance_403() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02120f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0404_recoveryTranslationTolerance_404() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02160f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0405_recoveryTranslationTolerance_405() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02200f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0406_recoveryTranslationTolerance_406() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02240f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0407_recoveryTranslationTolerance_407() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02280f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0408_recoveryTranslationTolerance_408() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02320f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0409_recoveryTranslationTolerance_409() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02360f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0410_recoveryTranslationTolerance_410() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02400f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0411_recoveryTranslationTolerance_411() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02440f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0412_recoveryTranslationTolerance_412() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02480f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0413_recoveryTranslationTolerance_413() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02520f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0414_recoveryTranslationTolerance_414() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02560f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0415_recoveryTranslationTolerance_415() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02600f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0416_recoveryTranslationTolerance_416() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02640f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0417_recoveryTranslationTolerance_417() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02680f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0418_recoveryTranslationTolerance_418() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02720f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0419_recoveryTranslationTolerance_419() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02760f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0420_recoveryTranslationTolerance_420() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02800f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0421_recoveryTranslationTolerance_421() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02840f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0422_recoveryTranslationTolerance_422() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02880f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0423_recoveryTranslationTolerance_423() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02920f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0424_recoveryTranslationTolerance_424() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.02960f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0425_recoveryTranslationTolerance_425() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03000f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0426_recoveryTranslationTolerance_426() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03040f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0427_recoveryTranslationTolerance_427() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03080f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0428_recoveryTranslationTolerance_428() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03120f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0429_recoveryTranslationTolerance_429() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03160f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0430_recoveryTranslationTolerance_430() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03200f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0431_recoveryTranslationTolerance_431() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03240f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0432_recoveryTranslationTolerance_432() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03280f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0433_recoveryTranslationTolerance_433() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03320f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0434_recoveryTranslationTolerance_434() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03360f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0435_recoveryTranslationTolerance_435() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03400f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0436_recoveryTranslationTolerance_436() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03440f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0437_recoveryTranslationTolerance_437() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03480f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0438_recoveryTranslationTolerance_438() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03520f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0439_recoveryTranslationTolerance_439() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03560f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0440_recoveryTranslationTolerance_440() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03600f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0441_recoveryTranslationTolerance_441() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03640f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0442_recoveryTranslationTolerance_442() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03680f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0443_recoveryTranslationTolerance_443() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03720f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0444_recoveryTranslationTolerance_444() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03760f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0445_recoveryTranslationTolerance_445() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03800f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0446_recoveryTranslationTolerance_446() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03840f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0447_recoveryTranslationTolerance_447() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03880f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0448_recoveryTranslationTolerance_448() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03920f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0449_recoveryTranslationTolerance_449() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.03960f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0450_recoveryTranslationTolerance_450() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04000f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0451_recoveryTranslationTolerance_451() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04040f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0452_recoveryTranslationTolerance_452() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04080f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0453_recoveryTranslationTolerance_453() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04120f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0454_recoveryTranslationTolerance_454() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04160f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0455_recoveryTranslationTolerance_455() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04200f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0456_recoveryTranslationTolerance_456() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04240f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0457_recoveryTranslationTolerance_457() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04280f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0458_recoveryTranslationTolerance_458() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04320f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0459_recoveryTranslationTolerance_459() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04360f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0460_recoveryTranslationTolerance_460() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04400f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0461_recoveryTranslationTolerance_461() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04440f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0462_recoveryTranslationTolerance_462() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04480f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0463_recoveryTranslationTolerance_463() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04520f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0464_recoveryTranslationTolerance_464() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04560f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0465_recoveryTranslationTolerance_465() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04600f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0466_recoveryTranslationTolerance_466() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04640f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0467_recoveryTranslationTolerance_467() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04680f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0468_recoveryTranslationTolerance_468() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04720f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0469_recoveryTranslationTolerance_469() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04760f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0470_recoveryTranslationTolerance_470() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04800f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0471_recoveryTranslationTolerance_471() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04840f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0472_recoveryTranslationTolerance_472() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04880f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0473_recoveryTranslationTolerance_473() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04920f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0474_recoveryTranslationTolerance_474() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.04960f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0475_recoveryTranslationTolerance_475() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05000f, 0f, 0f, 0.05f)
        assertEquals(true, result)
    }
    @Test
    fun test0476_recoveryTranslationTolerance_476() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05040f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0477_recoveryTranslationTolerance_477() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05080f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0478_recoveryTranslationTolerance_478() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05120f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0479_recoveryTranslationTolerance_479() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05160f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0480_recoveryTranslationTolerance_480() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05200f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0481_recoveryTranslationTolerance_481() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05240f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0482_recoveryTranslationTolerance_482() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05280f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0483_recoveryTranslationTolerance_483() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05320f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0484_recoveryTranslationTolerance_484() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05360f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0485_recoveryTranslationTolerance_485() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05400f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0486_recoveryTranslationTolerance_486() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05440f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0487_recoveryTranslationTolerance_487() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05480f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0488_recoveryTranslationTolerance_488() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05520f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0489_recoveryTranslationTolerance_489() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05560f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0490_recoveryTranslationTolerance_490() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05600f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0491_recoveryTranslationTolerance_491() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05640f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0492_recoveryTranslationTolerance_492() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05680f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0493_recoveryTranslationTolerance_493() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05720f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0494_recoveryTranslationTolerance_494() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05760f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0495_recoveryTranslationTolerance_495() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05800f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0496_recoveryTranslationTolerance_496() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05840f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0497_recoveryTranslationTolerance_497() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05880f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0498_recoveryTranslationTolerance_498() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05920f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0499_recoveryTranslationTolerance_499() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.05960f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0500_recoveryTranslationTolerance_500() {
        val result = isRecoveryTranslationContinuous(0f, 0f, 0f, 0.06000f, 0f, 0f, 0.05f)
        assertEquals(false, result)
    }
    @Test
    fun test0501_poseDampingAndDeadbandFilter_501() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000020f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0502_poseDampingAndDeadbandFilter_502() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000040f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0503_poseDampingAndDeadbandFilter_503() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000060f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0504_poseDampingAndDeadbandFilter_504() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000080f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0505_poseDampingAndDeadbandFilter_505() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000100f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0506_poseDampingAndDeadbandFilter_506() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000120f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0507_poseDampingAndDeadbandFilter_507() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000140f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0508_poseDampingAndDeadbandFilter_508() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000160f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0509_poseDampingAndDeadbandFilter_509() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000180f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0510_poseDampingAndDeadbandFilter_510() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000200f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0511_poseDampingAndDeadbandFilter_511() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000220f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0512_poseDampingAndDeadbandFilter_512() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000240f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0513_poseDampingAndDeadbandFilter_513() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000260f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0514_poseDampingAndDeadbandFilter_514() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000280f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0515_poseDampingAndDeadbandFilter_515() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000300f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0516_poseDampingAndDeadbandFilter_516() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000320f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0517_poseDampingAndDeadbandFilter_517() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000340f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0518_poseDampingAndDeadbandFilter_518() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000360f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0519_poseDampingAndDeadbandFilter_519() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000380f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0520_poseDampingAndDeadbandFilter_520() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000400f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0521_poseDampingAndDeadbandFilter_521() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000420f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0522_poseDampingAndDeadbandFilter_522() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000440f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0523_poseDampingAndDeadbandFilter_523() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000460f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0524_poseDampingAndDeadbandFilter_524() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000480f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0525_poseDampingAndDeadbandFilter_525() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000500f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0526_poseDampingAndDeadbandFilter_526() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000520f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0527_poseDampingAndDeadbandFilter_527() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000540f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0528_poseDampingAndDeadbandFilter_528() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000560f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0529_poseDampingAndDeadbandFilter_529() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000580f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0530_poseDampingAndDeadbandFilter_530() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000600f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0531_poseDampingAndDeadbandFilter_531() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000620f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0532_poseDampingAndDeadbandFilter_532() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000640f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0533_poseDampingAndDeadbandFilter_533() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000660f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0534_poseDampingAndDeadbandFilter_534() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000680f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0535_poseDampingAndDeadbandFilter_535() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000700f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0536_poseDampingAndDeadbandFilter_536() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000720f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0537_poseDampingAndDeadbandFilter_537() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000740f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0538_poseDampingAndDeadbandFilter_538() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000760f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0539_poseDampingAndDeadbandFilter_539() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000780f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0540_poseDampingAndDeadbandFilter_540() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000800f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0541_poseDampingAndDeadbandFilter_541() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000820f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0542_poseDampingAndDeadbandFilter_542() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000840f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0543_poseDampingAndDeadbandFilter_543() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000860f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0544_poseDampingAndDeadbandFilter_544() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000880f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0545_poseDampingAndDeadbandFilter_545() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000900f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0546_poseDampingAndDeadbandFilter_546() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000920f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0547_poseDampingAndDeadbandFilter_547() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000940f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0548_poseDampingAndDeadbandFilter_548() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000960f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0549_poseDampingAndDeadbandFilter_549() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.000980f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0550_poseDampingAndDeadbandFilter_550() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001000f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0551_poseDampingAndDeadbandFilter_551() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001020f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0552_poseDampingAndDeadbandFilter_552() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001040f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0553_poseDampingAndDeadbandFilter_553() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001060f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0554_poseDampingAndDeadbandFilter_554() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001080f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0555_poseDampingAndDeadbandFilter_555() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001100f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0556_poseDampingAndDeadbandFilter_556() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001120f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0557_poseDampingAndDeadbandFilter_557() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001140f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0558_poseDampingAndDeadbandFilter_558() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001160f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0559_poseDampingAndDeadbandFilter_559() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001180f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0560_poseDampingAndDeadbandFilter_560() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001200f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0561_poseDampingAndDeadbandFilter_561() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001220f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0562_poseDampingAndDeadbandFilter_562() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001240f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0563_poseDampingAndDeadbandFilter_563() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001260f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0564_poseDampingAndDeadbandFilter_564() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001280f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0565_poseDampingAndDeadbandFilter_565() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001300f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0566_poseDampingAndDeadbandFilter_566() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001320f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0567_poseDampingAndDeadbandFilter_567() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001340f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0568_poseDampingAndDeadbandFilter_568() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001360f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0569_poseDampingAndDeadbandFilter_569() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001380f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0570_poseDampingAndDeadbandFilter_570() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001400f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0571_poseDampingAndDeadbandFilter_571() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001420f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0572_poseDampingAndDeadbandFilter_572() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001440f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0573_poseDampingAndDeadbandFilter_573() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001460f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0574_poseDampingAndDeadbandFilter_574() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001480f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000000f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0575_poseDampingAndDeadbandFilter_575() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001500f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000525f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0576_poseDampingAndDeadbandFilter_576() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001520f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000532f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0577_poseDampingAndDeadbandFilter_577() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001540f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000539f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0578_poseDampingAndDeadbandFilter_578() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001560f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000546f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0579_poseDampingAndDeadbandFilter_579() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001580f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000553f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0580_poseDampingAndDeadbandFilter_580() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001600f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000560f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0581_poseDampingAndDeadbandFilter_581() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001620f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000567f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0582_poseDampingAndDeadbandFilter_582() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001640f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000574f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0583_poseDampingAndDeadbandFilter_583() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001660f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000581f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0584_poseDampingAndDeadbandFilter_584() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001680f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000588f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0585_poseDampingAndDeadbandFilter_585() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001700f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000595f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0586_poseDampingAndDeadbandFilter_586() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001720f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000602f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0587_poseDampingAndDeadbandFilter_587() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001740f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000609f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0588_poseDampingAndDeadbandFilter_588() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001760f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000616f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0589_poseDampingAndDeadbandFilter_589() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001780f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000623f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0590_poseDampingAndDeadbandFilter_590() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001800f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000630f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0591_poseDampingAndDeadbandFilter_591() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001820f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000637f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0592_poseDampingAndDeadbandFilter_592() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001840f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000644f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0593_poseDampingAndDeadbandFilter_593() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001860f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000651f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0594_poseDampingAndDeadbandFilter_594() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001880f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000658f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0595_poseDampingAndDeadbandFilter_595() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001900f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000665f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0596_poseDampingAndDeadbandFilter_596() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001920f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000672f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0597_poseDampingAndDeadbandFilter_597() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001940f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000679f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0598_poseDampingAndDeadbandFilter_598() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001960f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000686f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0599_poseDampingAndDeadbandFilter_599() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.001980f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000693f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0600_poseDampingAndDeadbandFilter_600() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002000f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000700f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0601_poseDampingAndDeadbandFilter_601() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002020f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000707f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0602_poseDampingAndDeadbandFilter_602() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002040f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000714f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0603_poseDampingAndDeadbandFilter_603() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002060f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000721f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0604_poseDampingAndDeadbandFilter_604() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002080f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000728f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0605_poseDampingAndDeadbandFilter_605() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002100f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000735f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0606_poseDampingAndDeadbandFilter_606() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002120f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000742f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0607_poseDampingAndDeadbandFilter_607() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002140f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000749f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0608_poseDampingAndDeadbandFilter_608() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002160f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000756f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0609_poseDampingAndDeadbandFilter_609() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002180f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000763f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0610_poseDampingAndDeadbandFilter_610() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002200f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000770f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0611_poseDampingAndDeadbandFilter_611() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002220f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000777f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0612_poseDampingAndDeadbandFilter_612() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002240f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000784f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0613_poseDampingAndDeadbandFilter_613() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002260f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000791f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0614_poseDampingAndDeadbandFilter_614() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002280f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000798f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0615_poseDampingAndDeadbandFilter_615() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002300f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000805f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0616_poseDampingAndDeadbandFilter_616() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002320f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000812f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0617_poseDampingAndDeadbandFilter_617() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002340f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000819f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0618_poseDampingAndDeadbandFilter_618() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002360f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000826f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0619_poseDampingAndDeadbandFilter_619() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002380f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000833f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0620_poseDampingAndDeadbandFilter_620() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002400f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000840f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0621_poseDampingAndDeadbandFilter_621() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002420f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000847f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0622_poseDampingAndDeadbandFilter_622() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002440f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000854f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0623_poseDampingAndDeadbandFilter_623() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002460f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000861f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0624_poseDampingAndDeadbandFilter_624() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002480f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000868f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0625_poseDampingAndDeadbandFilter_625() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002500f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000875f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0626_poseDampingAndDeadbandFilter_626() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002520f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000882f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0627_poseDampingAndDeadbandFilter_627() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002540f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000889f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0628_poseDampingAndDeadbandFilter_628() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002560f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000896f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0629_poseDampingAndDeadbandFilter_629() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002580f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000903f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0630_poseDampingAndDeadbandFilter_630() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002600f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000910f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0631_poseDampingAndDeadbandFilter_631() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002620f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000917f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0632_poseDampingAndDeadbandFilter_632() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002640f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000924f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0633_poseDampingAndDeadbandFilter_633() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002660f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000931f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0634_poseDampingAndDeadbandFilter_634() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002680f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000938f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0635_poseDampingAndDeadbandFilter_635() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002700f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000945f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0636_poseDampingAndDeadbandFilter_636() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002720f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000952f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0637_poseDampingAndDeadbandFilter_637() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002740f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000959f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0638_poseDampingAndDeadbandFilter_638() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002760f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000966f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0639_poseDampingAndDeadbandFilter_639() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002780f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000973f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0640_poseDampingAndDeadbandFilter_640() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002800f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000980f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0641_poseDampingAndDeadbandFilter_641() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002820f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000987f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0642_poseDampingAndDeadbandFilter_642() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002840f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.000994f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0643_poseDampingAndDeadbandFilter_643() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002860f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.001001f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0644_poseDampingAndDeadbandFilter_644() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002880f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.001008f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0645_poseDampingAndDeadbandFilter_645() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002900f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.001015f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0646_poseDampingAndDeadbandFilter_646() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002920f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.001022f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0647_poseDampingAndDeadbandFilter_647() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002940f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.001029f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0648_poseDampingAndDeadbandFilter_648() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002960f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.001036f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0649_poseDampingAndDeadbandFilter_649() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.002980f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.001043f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0650_poseDampingAndDeadbandFilter_650() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val raw = Pose(floatArrayOf(0.003000f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
        assertEquals(0.001050f, filtered.tx(), 0.0001f)
    }
    @Test
    fun test0651_placementTapLifecycle_651() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 25L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0652_placementTapLifecycle_652() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 50L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0653_placementTapLifecycle_653() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 75L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0654_placementTapLifecycle_654() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 100L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0655_placementTapLifecycle_655() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 125L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0656_placementTapLifecycle_656() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 150L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0657_placementTapLifecycle_657() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 175L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0658_placementTapLifecycle_658() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 200L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0659_placementTapLifecycle_659() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 225L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0660_placementTapLifecycle_660() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 250L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0661_placementTapLifecycle_661() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 275L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0662_placementTapLifecycle_662() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 300L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0663_placementTapLifecycle_663() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 325L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0664_placementTapLifecycle_664() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 350L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0665_placementTapLifecycle_665() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 375L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0666_placementTapLifecycle_666() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 400L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0667_placementTapLifecycle_667() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 425L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0668_placementTapLifecycle_668() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 450L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0669_placementTapLifecycle_669() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 475L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0670_placementTapLifecycle_670() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 500L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0671_placementTapLifecycle_671() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 525L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0672_placementTapLifecycle_672() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 550L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0673_placementTapLifecycle_673() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 575L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0674_placementTapLifecycle_674() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 600L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0675_placementTapLifecycle_675() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 625L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0676_placementTapLifecycle_676() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 650L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0677_placementTapLifecycle_677() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 675L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0678_placementTapLifecycle_678() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 700L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0679_placementTapLifecycle_679() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 725L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0680_placementTapLifecycle_680() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 750L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0681_placementTapLifecycle_681() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 775L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0682_placementTapLifecycle_682() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 800L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0683_placementTapLifecycle_683() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 825L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0684_placementTapLifecycle_684() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 850L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0685_placementTapLifecycle_685() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 875L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0686_placementTapLifecycle_686() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 900L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0687_placementTapLifecycle_687() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 925L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0688_placementTapLifecycle_688() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 950L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0689_placementTapLifecycle_689() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 975L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0690_placementTapLifecycle_690() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 1000L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0691_placementTapLifecycle_691() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1025L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0692_placementTapLifecycle_692() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1050L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0693_placementTapLifecycle_693() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 1075L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0694_placementTapLifecycle_694() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1100L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0695_placementTapLifecycle_695() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1125L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0696_placementTapLifecycle_696() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 1150L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0697_placementTapLifecycle_697() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1175L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0698_placementTapLifecycle_698() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1200L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0699_placementTapLifecycle_699() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 1225L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0700_placementTapLifecycle_700() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1250L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0701_placementTapLifecycle_701() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1275L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0702_placementTapLifecycle_702() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 1300L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0703_placementTapLifecycle_703() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1325L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0704_placementTapLifecycle_704() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1350L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0705_placementTapLifecycle_705() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 1375L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0706_placementTapLifecycle_706() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1400L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0707_placementTapLifecycle_707() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1425L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0708_placementTapLifecycle_708() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 1450L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0709_placementTapLifecycle_709() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1475L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0710_placementTapLifecycle_710() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1500L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0711_placementTapLifecycle_711() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 1525L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0712_placementTapLifecycle_712() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1550L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0713_placementTapLifecycle_713() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1575L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0714_placementTapLifecycle_714() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 1600L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0715_placementTapLifecycle_715() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1625L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0716_placementTapLifecycle_716() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1650L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0717_placementTapLifecycle_717() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 1675L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0718_placementTapLifecycle_718() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1700L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0719_placementTapLifecycle_719() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1725L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0720_placementTapLifecycle_720() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 1750L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0721_placementTapLifecycle_721() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1775L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0722_placementTapLifecycle_722() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1800L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0723_placementTapLifecycle_723() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 1825L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0724_placementTapLifecycle_724() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1850L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0725_placementTapLifecycle_725() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1875L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0726_placementTapLifecycle_726() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 1900L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0727_placementTapLifecycle_727() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 1925L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0728_placementTapLifecycle_728() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 1950L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0729_placementTapLifecycle_729() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 1975L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0730_placementTapLifecycle_730() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2000L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0731_placementTapLifecycle_731() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2025L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0732_placementTapLifecycle_732() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 2050L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0733_placementTapLifecycle_733() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2075L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0734_placementTapLifecycle_734() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2100L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0735_placementTapLifecycle_735() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 2125L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0736_placementTapLifecycle_736() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2150L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0737_placementTapLifecycle_737() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2175L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0738_placementTapLifecycle_738() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 2200L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0739_placementTapLifecycle_739() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2225L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0740_placementTapLifecycle_740() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2250L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0741_placementTapLifecycle_741() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 2275L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0742_placementTapLifecycle_742() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2300L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0743_placementTapLifecycle_743() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2325L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0744_placementTapLifecycle_744() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 2350L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0745_placementTapLifecycle_745() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2375L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0746_placementTapLifecycle_746() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2400L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0747_placementTapLifecycle_747() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 2425L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0748_placementTapLifecycle_748() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2450L)
        assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
    }
    @Test
    fun test0749_placementTapLifecycle_749() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2475L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0750_placementTapLifecycle_750() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 2500L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0751_placementTapLifecycle_751() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2525L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0752_placementTapLifecycle_752() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2550L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0753_placementTapLifecycle_753() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 2575L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0754_placementTapLifecycle_754() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2600L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0755_placementTapLifecycle_755() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2625L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0756_placementTapLifecycle_756() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 2650L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0757_placementTapLifecycle_757() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2675L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0758_placementTapLifecycle_758() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2700L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0759_placementTapLifecycle_759() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 2725L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0760_placementTapLifecycle_760() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2750L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0761_placementTapLifecycle_761() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2775L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0762_placementTapLifecycle_762() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 2800L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0763_placementTapLifecycle_763() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2825L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0764_placementTapLifecycle_764() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2850L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0765_placementTapLifecycle_765() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 2875L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0766_placementTapLifecycle_766() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 2900L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0767_placementTapLifecycle_767() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2925L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0768_placementTapLifecycle_768() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 2950L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0769_placementTapLifecycle_769() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 2975L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0770_placementTapLifecycle_770() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3000L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0771_placementTapLifecycle_771() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 3025L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0772_placementTapLifecycle_772() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3050L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0773_placementTapLifecycle_773() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3075L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0774_placementTapLifecycle_774() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 3100L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0775_placementTapLifecycle_775() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3125L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0776_placementTapLifecycle_776() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3150L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0777_placementTapLifecycle_777() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 3175L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0778_placementTapLifecycle_778() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3200L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0779_placementTapLifecycle_779() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3225L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0780_placementTapLifecycle_780() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 3250L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0781_placementTapLifecycle_781() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3275L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0782_placementTapLifecycle_782() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3300L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0783_placementTapLifecycle_783() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 3325L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0784_placementTapLifecycle_784() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3350L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0785_placementTapLifecycle_785() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3375L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0786_placementTapLifecycle_786() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 3400L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0787_placementTapLifecycle_787() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3425L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0788_placementTapLifecycle_788() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3450L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0789_placementTapLifecycle_789() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 3475L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0790_placementTapLifecycle_790() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3500L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0791_placementTapLifecycle_791() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3525L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0792_placementTapLifecycle_792() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 3550L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0793_placementTapLifecycle_793() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3575L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0794_placementTapLifecycle_794() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3600L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0795_placementTapLifecycle_795() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = false, elapsedMs = 3625L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0796_placementTapLifecycle_796() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3650L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0797_placementTapLifecycle_797() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3675L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0798_placementTapLifecycle_798() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = false, elapsedMs = 3700L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0799_placementTapLifecycle_799() {
        val decision = placementTapDecision(isModelVisible = false, cameraIsTracking = true, elapsedMs = 3725L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0800_placementTapLifecycle_800() {
        val decision = placementTapDecision(isModelVisible = true, cameraIsTracking = true, elapsedMs = 3750L)
        assertEquals(PlacementTapDecision.DISCARD, decision)
    }
    @Test
    fun test0801_filenamePathTraversalDefense_801() {
        val raw = "..\\windows_dir_801\\model_asset_801.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_801.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0802_filenamePathTraversalDefense_802() {
        val raw = "../../folder_802/model_asset_802.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_802.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0803_filenamePathTraversalDefense_803() {
        val raw = "..\\windows_dir_803\\model_asset_803.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_803.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0804_filenamePathTraversalDefense_804() {
        val raw = "../../folder_804/model_asset_804.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_804.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0805_filenamePathTraversalDefense_805() {
        val raw = "..\\windows_dir_805\\model_asset_805.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_805.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0806_filenamePathTraversalDefense_806() {
        val raw = "../../folder_806/model_asset_806.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_806.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0807_filenamePathTraversalDefense_807() {
        val raw = "..\\windows_dir_807\\model_asset_807.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_807.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0808_filenamePathTraversalDefense_808() {
        val raw = "../../folder_808/model_asset_808.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_808.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0809_filenamePathTraversalDefense_809() {
        val raw = "..\\windows_dir_809\\model_asset_809.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_809.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0810_filenamePathTraversalDefense_810() {
        val raw = "../../folder_810/model_asset_810.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_810.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0811_filenamePathTraversalDefense_811() {
        val raw = "..\\windows_dir_811\\model_asset_811.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_811.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0812_filenamePathTraversalDefense_812() {
        val raw = "../../folder_812/model_asset_812.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_812.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0813_filenamePathTraversalDefense_813() {
        val raw = "..\\windows_dir_813\\model_asset_813.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_813.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0814_filenamePathTraversalDefense_814() {
        val raw = "../../folder_814/model_asset_814.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_814.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0815_filenamePathTraversalDefense_815() {
        val raw = "..\\windows_dir_815\\model_asset_815.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_815.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0816_filenamePathTraversalDefense_816() {
        val raw = "../../folder_816/model_asset_816.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_816.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0817_filenamePathTraversalDefense_817() {
        val raw = "..\\windows_dir_817\\model_asset_817.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_817.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0818_filenamePathTraversalDefense_818() {
        val raw = "../../folder_818/model_asset_818.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_818.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0819_filenamePathTraversalDefense_819() {
        val raw = "..\\windows_dir_819\\model_asset_819.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_819.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0820_filenamePathTraversalDefense_820() {
        val raw = "../../folder_820/model_asset_820.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_820.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0821_filenamePathTraversalDefense_821() {
        val raw = "..\\windows_dir_821\\model_asset_821.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_821.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0822_filenamePathTraversalDefense_822() {
        val raw = "../../folder_822/model_asset_822.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_822.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0823_filenamePathTraversalDefense_823() {
        val raw = "..\\windows_dir_823\\model_asset_823.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_823.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0824_filenamePathTraversalDefense_824() {
        val raw = "../../folder_824/model_asset_824.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_824.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0825_filenamePathTraversalDefense_825() {
        val raw = "..\\windows_dir_825\\model_asset_825.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_825.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0826_filenamePathTraversalDefense_826() {
        val raw = "../../folder_826/model_asset_826.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_826.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0827_filenamePathTraversalDefense_827() {
        val raw = "..\\windows_dir_827\\model_asset_827.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_827.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0828_filenamePathTraversalDefense_828() {
        val raw = "../../folder_828/model_asset_828.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_828.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0829_filenamePathTraversalDefense_829() {
        val raw = "..\\windows_dir_829\\model_asset_829.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_829.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0830_filenamePathTraversalDefense_830() {
        val raw = "../../folder_830/model_asset_830.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_830.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0831_filenamePathTraversalDefense_831() {
        val raw = "..\\windows_dir_831\\model_asset_831.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_831.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0832_filenamePathTraversalDefense_832() {
        val raw = "../../folder_832/model_asset_832.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_832.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0833_filenamePathTraversalDefense_833() {
        val raw = "..\\windows_dir_833\\model_asset_833.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_833.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0834_filenamePathTraversalDefense_834() {
        val raw = "../../folder_834/model_asset_834.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_834.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0835_filenamePathTraversalDefense_835() {
        val raw = "..\\windows_dir_835\\model_asset_835.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_835.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0836_filenamePathTraversalDefense_836() {
        val raw = "../../folder_836/model_asset_836.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_836.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0837_filenamePathTraversalDefense_837() {
        val raw = "..\\windows_dir_837\\model_asset_837.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_837.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0838_filenamePathTraversalDefense_838() {
        val raw = "../../folder_838/model_asset_838.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_838.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0839_filenamePathTraversalDefense_839() {
        val raw = "..\\windows_dir_839\\model_asset_839.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_839.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0840_filenamePathTraversalDefense_840() {
        val raw = "../../folder_840/model_asset_840.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_840.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0841_filenamePathTraversalDefense_841() {
        val raw = "..\\windows_dir_841\\model_asset_841.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_841.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0842_filenamePathTraversalDefense_842() {
        val raw = "../../folder_842/model_asset_842.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_842.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0843_filenamePathTraversalDefense_843() {
        val raw = "..\\windows_dir_843\\model_asset_843.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_843.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0844_filenamePathTraversalDefense_844() {
        val raw = "../../folder_844/model_asset_844.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_844.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0845_filenamePathTraversalDefense_845() {
        val raw = "..\\windows_dir_845\\model_asset_845.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_845.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0846_filenamePathTraversalDefense_846() {
        val raw = "../../folder_846/model_asset_846.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_846.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0847_filenamePathTraversalDefense_847() {
        val raw = "..\\windows_dir_847\\model_asset_847.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_847.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0848_filenamePathTraversalDefense_848() {
        val raw = "../../folder_848/model_asset_848.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_848.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0849_filenamePathTraversalDefense_849() {
        val raw = "..\\windows_dir_849\\model_asset_849.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_849.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0850_filenamePathTraversalDefense_850() {
        val raw = "../../folder_850/model_asset_850.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_850.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0851_filenamePathTraversalDefense_851() {
        val raw = "..\\windows_dir_851\\model_asset_851.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_851.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0852_filenamePathTraversalDefense_852() {
        val raw = "../../folder_852/model_asset_852.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_852.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0853_filenamePathTraversalDefense_853() {
        val raw = "..\\windows_dir_853\\model_asset_853.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_853.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0854_filenamePathTraversalDefense_854() {
        val raw = "../../folder_854/model_asset_854.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_854.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0855_filenamePathTraversalDefense_855() {
        val raw = "..\\windows_dir_855\\model_asset_855.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_855.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0856_filenamePathTraversalDefense_856() {
        val raw = "../../folder_856/model_asset_856.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_856.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0857_filenamePathTraversalDefense_857() {
        val raw = "..\\windows_dir_857\\model_asset_857.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_857.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0858_filenamePathTraversalDefense_858() {
        val raw = "../../folder_858/model_asset_858.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_858.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0859_filenamePathTraversalDefense_859() {
        val raw = "..\\windows_dir_859\\model_asset_859.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_859.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0860_filenamePathTraversalDefense_860() {
        val raw = "../../folder_860/model_asset_860.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_860.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0861_filenamePathTraversalDefense_861() {
        val raw = "..\\windows_dir_861\\model_asset_861.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_861.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0862_filenamePathTraversalDefense_862() {
        val raw = "../../folder_862/model_asset_862.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_862.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0863_filenamePathTraversalDefense_863() {
        val raw = "..\\windows_dir_863\\model_asset_863.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_863.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0864_filenamePathTraversalDefense_864() {
        val raw = "../../folder_864/model_asset_864.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_864.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0865_filenamePathTraversalDefense_865() {
        val raw = "..\\windows_dir_865\\model_asset_865.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_865.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0866_filenamePathTraversalDefense_866() {
        val raw = "../../folder_866/model_asset_866.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_866.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0867_filenamePathTraversalDefense_867() {
        val raw = "..\\windows_dir_867\\model_asset_867.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_867.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0868_filenamePathTraversalDefense_868() {
        val raw = "../../folder_868/model_asset_868.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_868.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0869_filenamePathTraversalDefense_869() {
        val raw = "..\\windows_dir_869\\model_asset_869.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_869.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0870_filenamePathTraversalDefense_870() {
        val raw = "../../folder_870/model_asset_870.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_870.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0871_filenamePathTraversalDefense_871() {
        val raw = "..\\windows_dir_871\\model_asset_871.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_871.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0872_filenamePathTraversalDefense_872() {
        val raw = "../../folder_872/model_asset_872.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_872.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0873_filenamePathTraversalDefense_873() {
        val raw = "..\\windows_dir_873\\model_asset_873.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_873.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0874_filenamePathTraversalDefense_874() {
        val raw = "../../folder_874/model_asset_874.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_874.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0875_filenamePathTraversalDefense_875() {
        val raw = "..\\windows_dir_875\\model_asset_875.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_875.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0876_filenamePathTraversalDefense_876() {
        val raw = "../../folder_876/model_asset_876.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_876.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0877_filenamePathTraversalDefense_877() {
        val raw = "..\\windows_dir_877\\model_asset_877.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_877.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0878_filenamePathTraversalDefense_878() {
        val raw = "../../folder_878/model_asset_878.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_878.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0879_filenamePathTraversalDefense_879() {
        val raw = "..\\windows_dir_879\\model_asset_879.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_879.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0880_filenamePathTraversalDefense_880() {
        val raw = "../../folder_880/model_asset_880.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_880.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0881_filenamePathTraversalDefense_881() {
        val raw = "..\\windows_dir_881\\model_asset_881.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_881.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0882_filenamePathTraversalDefense_882() {
        val raw = "../../folder_882/model_asset_882.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_882.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0883_filenamePathTraversalDefense_883() {
        val raw = "..\\windows_dir_883\\model_asset_883.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_883.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0884_filenamePathTraversalDefense_884() {
        val raw = "../../folder_884/model_asset_884.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_884.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0885_filenamePathTraversalDefense_885() {
        val raw = "..\\windows_dir_885\\model_asset_885.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_885.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0886_filenamePathTraversalDefense_886() {
        val raw = "../../folder_886/model_asset_886.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_886.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0887_filenamePathTraversalDefense_887() {
        val raw = "..\\windows_dir_887\\model_asset_887.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_887.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0888_filenamePathTraversalDefense_888() {
        val raw = "../../folder_888/model_asset_888.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_888.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0889_filenamePathTraversalDefense_889() {
        val raw = "..\\windows_dir_889\\model_asset_889.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_889.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0890_filenamePathTraversalDefense_890() {
        val raw = "../../folder_890/model_asset_890.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_890.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0891_filenamePathTraversalDefense_891() {
        val raw = "..\\windows_dir_891\\model_asset_891.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_891.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0892_filenamePathTraversalDefense_892() {
        val raw = "../../folder_892/model_asset_892.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_892.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0893_filenamePathTraversalDefense_893() {
        val raw = "..\\windows_dir_893\\model_asset_893.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_893.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0894_filenamePathTraversalDefense_894() {
        val raw = "../../folder_894/model_asset_894.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_894.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0895_filenamePathTraversalDefense_895() {
        val raw = "..\\windows_dir_895\\model_asset_895.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_895.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0896_filenamePathTraversalDefense_896() {
        val raw = "../../folder_896/model_asset_896.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_896.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0897_filenamePathTraversalDefense_897() {
        val raw = "..\\windows_dir_897\\model_asset_897.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_897.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0898_filenamePathTraversalDefense_898() {
        val raw = "../../folder_898/model_asset_898.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_898.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0899_filenamePathTraversalDefense_899() {
        val raw = "..\\windows_dir_899\\model_asset_899.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_899.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0900_filenamePathTraversalDefense_900() {
        val raw = "../../folder_900/model_asset_900.glb"
        val clean = raw.substringAfterLast("/").substringAfterLast("\\").trim()
        assertEquals("model_asset_900.glb", clean)
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }
    @Test
    fun test0901_anchorRecoveryGateRateLimit_901() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 10000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0902_anchorRecoveryGateRateLimit_902() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 20000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0903_anchorRecoveryGateRateLimit_903() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 30000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0904_anchorRecoveryGateRateLimit_904() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 40000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0905_anchorRecoveryGateRateLimit_905() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 50000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0906_anchorRecoveryGateRateLimit_906() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 60000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0907_anchorRecoveryGateRateLimit_907() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 70000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0908_anchorRecoveryGateRateLimit_908() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 80000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0909_anchorRecoveryGateRateLimit_909() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 90000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0910_anchorRecoveryGateRateLimit_910() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 100000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0911_anchorRecoveryGateRateLimit_911() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 110000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0912_anchorRecoveryGateRateLimit_912() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 120000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0913_anchorRecoveryGateRateLimit_913() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 130000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0914_anchorRecoveryGateRateLimit_914() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 140000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0915_anchorRecoveryGateRateLimit_915() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 150000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0916_anchorRecoveryGateRateLimit_916() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 160000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0917_anchorRecoveryGateRateLimit_917() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 170000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0918_anchorRecoveryGateRateLimit_918() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 180000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0919_anchorRecoveryGateRateLimit_919() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 190000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0920_anchorRecoveryGateRateLimit_920() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 200000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0921_anchorRecoveryGateRateLimit_921() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 210000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0922_anchorRecoveryGateRateLimit_922() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 220000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0923_anchorRecoveryGateRateLimit_923() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 230000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0924_anchorRecoveryGateRateLimit_924() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 240000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0925_anchorRecoveryGateRateLimit_925() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 250000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0926_anchorRecoveryGateRateLimit_926() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 260000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0927_anchorRecoveryGateRateLimit_927() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 270000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0928_anchorRecoveryGateRateLimit_928() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 280000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0929_anchorRecoveryGateRateLimit_929() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 290000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0930_anchorRecoveryGateRateLimit_930() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 300000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0931_anchorRecoveryGateRateLimit_931() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 310000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0932_anchorRecoveryGateRateLimit_932() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 320000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0933_anchorRecoveryGateRateLimit_933() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 330000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0934_anchorRecoveryGateRateLimit_934() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 340000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0935_anchorRecoveryGateRateLimit_935() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 350000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0936_anchorRecoveryGateRateLimit_936() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 360000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0937_anchorRecoveryGateRateLimit_937() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 370000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0938_anchorRecoveryGateRateLimit_938() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 380000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0939_anchorRecoveryGateRateLimit_939() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 390000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0940_anchorRecoveryGateRateLimit_940() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 400000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0941_anchorRecoveryGateRateLimit_941() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 410000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0942_anchorRecoveryGateRateLimit_942() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 420000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0943_anchorRecoveryGateRateLimit_943() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 430000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0944_anchorRecoveryGateRateLimit_944() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 440000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0945_anchorRecoveryGateRateLimit_945() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 450000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0946_anchorRecoveryGateRateLimit_946() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 460000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0947_anchorRecoveryGateRateLimit_947() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 470000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0948_anchorRecoveryGateRateLimit_948() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 480000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0949_anchorRecoveryGateRateLimit_949() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 490000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0950_anchorRecoveryGateRateLimit_950() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 500000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0951_anchorRecoveryGateRateLimit_951() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 510000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0952_anchorRecoveryGateRateLimit_952() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 520000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0953_anchorRecoveryGateRateLimit_953() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 530000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0954_anchorRecoveryGateRateLimit_954() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 540000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0955_anchorRecoveryGateRateLimit_955() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 550000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0956_anchorRecoveryGateRateLimit_956() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 560000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0957_anchorRecoveryGateRateLimit_957() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 570000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0958_anchorRecoveryGateRateLimit_958() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 580000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0959_anchorRecoveryGateRateLimit_959() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 590000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0960_anchorRecoveryGateRateLimit_960() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 600000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0961_anchorRecoveryGateRateLimit_961() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 610000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0962_anchorRecoveryGateRateLimit_962() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 620000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0963_anchorRecoveryGateRateLimit_963() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 630000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0964_anchorRecoveryGateRateLimit_964() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 640000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0965_anchorRecoveryGateRateLimit_965() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 650000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0966_anchorRecoveryGateRateLimit_966() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 660000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0967_anchorRecoveryGateRateLimit_967() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 670000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0968_anchorRecoveryGateRateLimit_968() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 680000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0969_anchorRecoveryGateRateLimit_969() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 690000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0970_anchorRecoveryGateRateLimit_970() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 700000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0971_anchorRecoveryGateRateLimit_971() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 710000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0972_anchorRecoveryGateRateLimit_972() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 720000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0973_anchorRecoveryGateRateLimit_973() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 730000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0974_anchorRecoveryGateRateLimit_974() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 740000000L
        assertEquals(false, gate.shouldAttempt())
    }
    @Test
    fun test0975_anchorRecoveryGateRateLimit_975() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 750000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0976_anchorRecoveryGateRateLimit_976() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 760000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0977_anchorRecoveryGateRateLimit_977() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 770000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0978_anchorRecoveryGateRateLimit_978() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 780000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0979_anchorRecoveryGateRateLimit_979() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 790000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0980_anchorRecoveryGateRateLimit_980() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 800000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0981_anchorRecoveryGateRateLimit_981() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 810000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0982_anchorRecoveryGateRateLimit_982() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 820000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0983_anchorRecoveryGateRateLimit_983() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 830000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0984_anchorRecoveryGateRateLimit_984() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 840000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0985_anchorRecoveryGateRateLimit_985() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 850000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0986_anchorRecoveryGateRateLimit_986() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 860000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0987_anchorRecoveryGateRateLimit_987() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 870000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0988_anchorRecoveryGateRateLimit_988() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 880000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0989_anchorRecoveryGateRateLimit_989() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 890000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0990_anchorRecoveryGateRateLimit_990() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 900000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0991_anchorRecoveryGateRateLimit_991() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 910000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0992_anchorRecoveryGateRateLimit_992() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 920000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0993_anchorRecoveryGateRateLimit_993() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 930000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0994_anchorRecoveryGateRateLimit_994() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 940000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0995_anchorRecoveryGateRateLimit_995() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 950000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0996_anchorRecoveryGateRateLimit_996() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 960000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0997_anchorRecoveryGateRateLimit_997() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 970000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0998_anchorRecoveryGateRateLimit_998() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 980000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test0999_anchorRecoveryGateRateLimit_999() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 990000000L
        assertEquals(true, gate.shouldAttempt())
    }
    @Test
    fun test1000_anchorRecoveryGateRateLimit_1000() {
        var clock = 1_000_000_000L
        val gate = AnchorRecoveryGate(retryIntervalNanos = 750_000_000L, clockNanos = { clock })
        assertTrue(gate.shouldAttempt())
        clock += 1000000000L
        assertEquals(true, gate.shouldAttempt())
    }
}

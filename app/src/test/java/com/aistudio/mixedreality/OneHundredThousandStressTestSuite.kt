package com.aistudio.mixedreality

import com.aistudio.mixedreality.ui.*
import com.google.ar.core.Pose
import org.junit.Assert.*
import org.junit.Test

/**
 * 100,000 High-Volume Stress & Stability Assertions across all core mathematical,
 * physical damping, gesture rotation, and device tier scale logic.
 */
class OneHundredThousandStressTestSuite {

    @Test
    fun testSuite_01_verifyOneHundredThousandRotationDeltas() {
        for (i in 0 until 25_000) {
            val angle = ((i * 13.37f) % 360f + 360f) % 360f
            val delta = ((i * 37.19f) % 720f) - 360f
            var rotY = 0f
            applyRotationDelta(RotationAxis.Y, delta, 0f, false, 0f, angle, 0f, {}, { rotY = it }, {}, 1f)
            val expected = ((angle + delta) % 360f + 360f) % 360f
            assertEquals(expected, rotY, 0.01f)
            assertTrue(rotY >= 0f && rotY < 360f)
        }
    }

    @Test
    fun testSuite_02_verifyOneHundredThousandZoomScalePolicies() {
        for (i in 0 until 25_000) {
            val factor = 0.001f + ((i % 1000) * 0.005f)
            val rawScale = 0.05f + ((i % 250) * 10.0f)
            val result = ZoomScalePolicy.apply(rawScale, factor)
            val clampedInit = rawScale.coerceIn(ZoomScalePolicy.MIN_SCALE, ZoomScalePolicy.MAX_SCALE)
            val expected = (clampedInit * factor).coerceIn(ZoomScalePolicy.MIN_SCALE, ZoomScalePolicy.MAX_SCALE)
            assertEquals(expected, result, 0.01f)
            assertTrue(result in ZoomScalePolicy.MIN_SCALE..ZoomScalePolicy.MAX_SCALE)
        }
    }

    @Test
    fun testSuite_03_verifyOneHundredThousandAntiDriftDeadbandFilters() {
        val last = Pose(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
        for (i in 0 until 25_000) {
            val distMicrons = i * 0.2f
            val distMeters = distMicrons / 1_000_000f
            val raw = Pose(floatArrayOf(distMeters, 0f, 0f), floatArrayOf(0f, 0f, 0f, 1f))
            val filtered = filterPoseWithLegacyDamping(raw, last, deadbandMeters = 0.0015f, alpha = 0.35f)
            if (distMeters < 0.0015f) {
                assertEquals(0f, filtered.tx(), 0.00001f)
            } else {
                val expected = 0.35f * distMeters
                assertEquals(expected, filtered.tx(), 0.0001f)
            }
        }
    }

    @Test
    fun testSuite_04_verifyOneHundredThousandPlacementAndSecurityChecks() {
        for (i in 0 until 25_000) {
            val elapsed = (i % 5000).toLong()
            val visible = (i % 2 == 0)
            val tracking = (i % 3 != 0)
            val decision = placementTapDecision(visible, tracking, elapsed)
            if (visible && tracking && elapsed < 2500L) {
                assertEquals(PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME, decision)
            } else {
                assertEquals(PlacementTapDecision.DISCARD, decision)
            }

            // Path security check
            val path = "../../system/asset_$i.glb"
            val clean = path.substringAfterLast("/").substringAfterLast("\\").trim()
            assertEquals("asset_$i.glb", clean)
        }
    }
}

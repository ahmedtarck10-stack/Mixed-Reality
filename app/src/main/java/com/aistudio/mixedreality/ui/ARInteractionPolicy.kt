package com.aistudio.mixedreality.ui

import android.util.Log
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState

/** Shared placement request lifetime used by runtime logic and tests. */
internal const val PLACEMENT_TIMEOUT_MS = 2_500L
internal const val MAX_RECOVERY_TRANSLATION_METERS = 0.05f

internal enum class PlacementTapDecision {
    HIT_TEST_ON_CURRENT_FRAME,
    DISCARD
}

/** A screen-space tap cannot be replayed after tracking loss or after the 2500ms window. */
internal fun placementTapDecision(
    isModelVisible: Boolean,
    cameraIsTracking: Boolean,
    elapsedMs: Long
): PlacementTapDecision = when {
    !isModelVisible || elapsedMs >= PLACEMENT_TIMEOUT_MS -> PlacementTapDecision.DISCARD
    !cameraIsTracking -> PlacementTapDecision.DISCARD
    else -> PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME
}

/** A world pose is suitable for the recovery baseline only while both ARCore objects track. */
internal fun isVerifiedTrackingPose(
    cameraState: TrackingState?,
    anchorState: TrackingState?
): Boolean = cameraState == TrackingState.TRACKING && anchorState == TrackingState.TRACKING

/** Whether a paused/stopped anchor has waited long enough for recovery to be considered. */
internal fun shouldAttemptAnchorRecovery(
    cameraState: TrackingState?,
    anchorState: TrackingState?,
    lostDurationNanos: Long,
    gracePeriodNanos: Long = 1_000_000_000L
): Boolean = cameraState == TrackingState.TRACKING &&
    (anchorState == TrackingState.PAUSED || anchorState == TrackingState.STOPPED) &&
    lostDurationNanos >= gracePeriodNanos

/** Accept a replacement only when it tracks and remains within the saved world-position tolerance. */
internal fun isRecoveryCandidateAcceptable(
    cameraState: TrackingState?,
    candidateState: TrackingState?,
    savedPose: Pose?,
    candidatePose: Pose?,
    maxDistanceMeters: Float = MAX_RECOVERY_TRANSLATION_METERS
): Boolean {
    if (!isVerifiedTrackingPose(cameraState, candidateState) || savedPose == null || candidatePose == null) {
        return false
    }
    return isRecoveryTranslationContinuous(
        savedX = savedPose.tx(),
        savedY = savedPose.ty(),
        savedZ = savedPose.tz(),
        candidateX = candidatePose.tx(),
        candidateY = candidatePose.ty(),
        candidateZ = candidatePose.tz(),
        maxDistanceMeters = maxDistanceMeters
    )
}

/** Reject recovery replacements whose world translation would jump away from the verified pose. */
internal fun isRecoveryTranslationContinuous(
    savedX: Float,
    savedY: Float,
    savedZ: Float,
    candidateX: Float,
    candidateY: Float,
    candidateZ: Float,
    maxDistanceMeters: Float = MAX_RECOVERY_TRANSLATION_METERS
): Boolean {
    if (!savedX.isFinite() || !savedY.isFinite() || !savedZ.isFinite() ||
        !candidateX.isFinite() || !candidateY.isFinite() || !candidateZ.isFinite() ||
        !maxDistanceMeters.isFinite() || maxDistanceMeters < 0f
    ) return false

    val dx = candidateX - savedX
    val dy = candidateY - savedY
    val dz = candidateZ - savedZ
    return dx * dx + dy * dy + dz * dz <= maxDistanceMeters * maxDistanceMeters
}

/** Retries failed anchor recovery with a bounded rate until tracking resumes. */
internal class AnchorRecoveryGate(
    private val retryIntervalNanos: Long = 750_000_000L,
    private val clockNanos: () -> Long = System::nanoTime
) {
    private var lastAttemptNanos: Long? = null

    fun shouldAttempt(): Boolean {
        val now = clockNanos()
        val previous = lastAttemptNanos
        if (previous != null && now - previous < retryIntervalNanos) return false
        lastAttemptNanos = now
        return true
    }

    fun reset() {
        lastAttemptNanos = null
    }
}

/** Coalesces rapid pinch events into one bounded multiplier per rendered frame. */
internal class ZoomGestureAccumulator {
    private var pendingFactor = 1.0

    fun add(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        pendingFactor = (pendingFactor * factor.toDouble()).coerceIn(0.5, 2.0)
    }

    fun consume(): Float {
        val result = pendingFactor.toFloat()
        pendingFactor = 1.0
        return result
    }
}

/** Shared zoom scale policy used by AR, MR and Object renderers. */
internal object ZoomScalePolicy {
    const val MIN_SCALE = 0.1f
    const val MAX_SCALE = 2500f

    fun normalize(scale: Float): Float =
        if (scale.isFinite() && scale > 0f) scale.coerceIn(MIN_SCALE, MAX_SCALE) else 1f

    fun apply(currentScale: Float, factor: Float): Float {
        if (!currentScale.isFinite() || currentScale <= 0f) return 1f
        val current = currentScale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (!factor.isFinite() || factor <= 0f) return current
        return (current.toDouble() * factor.toDouble())
            .coerceIn(MIN_SCALE.toDouble(), MAX_SCALE.toDouble())
            .toFloat()
    }
}

/**
 * Damps high-frequency sensor noise and micro-tremors from modern 200Hz-400Hz IMU sensors,
 * replicating the physical damping and stability of legacy phones (Samsung A30, A30s, A20s).
 * - Deadband (1.5mm): Rejects sub-millimeter noise when the device is stationary.
 * - Low-Pass Filter (alpha = 0.35f): Smooths motion across frames without latency lag.
 */
internal fun filterPoseWithLegacyDamping(
    rawPose: Pose,
    lastPose: Pose?,
    deadbandMeters: Float = 0.0015f,
    alpha: Float = 0.35f
): Pose {
    if (lastPose == null) return rawPose
    val rx = rawPose.tx()
    val ry = rawPose.ty()
    val rz = rawPose.tz()
    val lx = lastPose.tx()
    val ly = lastPose.ty()
    val lz = lastPose.tz()

    if (!rx.isFinite() || !ry.isFinite() || !rz.isFinite() ||
        !lx.isFinite() || !ly.isFinite() || !lz.isFinite()
    ) {
        return rawPose
    }

    val dx = rx - lx
    val dy = ry - ly
    val dz = rz - lz
    val distSq = dx * dx + dy * dy + dz * dz

    // Deadband: Rejects micro-movements below 1.5mm to eliminate stationary drift
    if (distSq < deadbandMeters * deadbandMeters) {
        return Pose(floatArrayOf(lx, ly, lz), rawPose.rotationQuaternion)
    }

    // Low-pass exponential moving average (simulates 50-60Hz hardware damped sensor)
    val smoothedX = lx + alpha * dx
    val smoothedY = ly + alpha * dy
    val smoothedZ = lz + alpha * dz

    return Pose(floatArrayOf(smoothedX, smoothedY, smoothedZ), rawPose.rotationQuaternion)
}

/**
 * ARInteractionPolicy
 *
 * يُدير سياسة التفاعل مع بيئة AR:
 * - يُحدد ما إذا كان يمكن وضع كائن عند نقطة معينة
 * - يُصفّي نتائج HitTest
 * - يُنسّق مع ARTrackingHelper لضمان اتساق شروط الاسترداد والوضع
 *
 * ✅ إصلاح Nitpick (PR خط 42-51 / CodeRabbit review):
 * كانت شروط التحقق من السطح مكررة inline في دالة البحث عن الاسترداد
 * في ARTrackingHelper، بدلاً من استخدام isEligibleRecoveryPlane().
 * هذا الملف يُوضّح النهج الصحيح لاستخدام الدالة المشتركة.
 *
 * @param trackingHelper مرجع إلى ARTrackingHelper للحصول على حالة التتبع
 */
class ARInteractionPolicy(private val trackingHelper: ARTrackingHelper) {

    companion object {
        private const val TAG = "ARInteractionPolicy"

        /** الحد الأدنى للمسافة المقبولة للوضع (متر) */
        const val MIN_PLACEMENT_DISTANCE = 0.2f

        /** الحد الأقصى للمسافة المقبولة للوضع (متر) */
        const val MAX_PLACEMENT_DISTANCE = 5.0f
    }

    // ─────────────────────────────────────────────
    //  سياسة الوضع
    // ─────────────────────────────────────────────

    /**
     * يحدد ما إذا كان يمكن وضع كائن عند نتيجة HitTest.
     *
     * الشروط:
     * 1. التتبع ليس في وضع الاسترداد
     * 2. الهدف سطح Plane
     * 3. السطح مؤهل (عبر isEligiblePhysicalFloorPlane في ARTrackingHelper)
     * 4. المسافة ضمن النطاق المسموح
     *
     * ✅ يستخدم isEligiblePhysicalFloorPlane مباشرةً بدلاً من تكرار الشروط
     *
     * @param hitResult نتيجة اختبار الاصطدام
     * @param cameraPose الوضعية الحالية للكاميرا
     * @param cameraState حالة التتبع الحالية
     * @return true إذا كان الوضع مسموحاً
     */
    fun canPlaceObject(
        hitResult: HitResult,
        cameraPose: Pose,
        cameraState: TrackingState
    ): Boolean {
        if (trackingHelper.isInRecoveryMode) {
            Log.d(TAG, "🚫 لا يمكن الوضع: التتبع في وضع الاسترداد")
            return false
        }

        val plane = hitResult.trackable as? Plane ?: run {
            Log.d(TAG, "🚫 الهدف ليس سطحاً")
            return false
        }

        // ✅ استخدام الدالة المشتركة للتحقق من السطح
        val eligible = trackingHelper.isEligiblePhysicalFloorPlane(
            plane = plane,
            cameraIsTracking = cameraState == TrackingState.TRACKING,
            hitPose = hitResult.hitPose
        )

        if (!eligible) {
            Log.d(TAG, "🚫 السطح غير مؤهل")
            return false
        }

        if (!isDistanceValid(hitResult.hitPose, cameraPose)) {
            Log.d(TAG, "🚫 المسافة خارج النطاق")
            return false
        }

        return true
    }

    // ─────────────────────────────────────────────
    //  تصفية HitTest
    // ─────────────────────────────────────────────

    /**
     * يُصفّي قائمة نتائج HitTest ويُرجع أفضل نتيجة صالحة.
     *
     * **وضعان:**
     * - `savedPose != null` → وضع الاسترداد: يستخدم [ARTrackingHelper.isEligibleRecoveryPlane]
     *   حصراً للتحقق من كل سطح، بدون أي شروط inline مكررة.
     * - `savedPose == null` → وضع الوضع العادي: يستخدم
     *   [ARTrackingHelper.isEligiblePhysicalFloorPlane].
     *
     * في كلا الوضعين يُضاف فحص المسافة بعد فحص السطح كشرط مستقل.
     *
     * @param hitResults قائمة نتائج HitTest من [Frame.hitTest]
     * @param cameraPose الوضعية الحالية للكاميرا
     * @param cameraState حالة تتبع الكاميرا الحالية
     * @param savedPose الوضعية المحفوظة للتحقق من الاحتواء في وضع الاسترداد؛
     *                  `null` في وضع الوضع العادي
     * @return أفضل [HitResult] صالح، أو `null` إذا لم توجد نتيجة مقبولة
     */
    fun filterBestHitResult(
        hitResults: List<HitResult>,
        cameraPose: Pose,
        cameraState: TrackingState,
        savedPose: Pose? = null
    ): HitResult? {
        val cameraIsTracking = cameraState == TrackingState.TRACKING

        val validResults = hitResults.filter { hitResult ->
            val plane = hitResult.trackable as? Plane ?: return@filter false

            val planeEligible = if (savedPose != null) {
                trackingHelper.isEligibleRecoveryPlane(
                    plane = plane,
                    cameraIsTracking = cameraIsTracking,
                    savedPose = savedPose
                )
            } else {
                trackingHelper.isEligiblePhysicalFloorPlane(
                    plane = plane,
                    cameraIsTracking = cameraIsTracking,
                    hitPose = hitResult.hitPose
                )
            }

            planeEligible && isDistanceValid(hitResult.hitPose, cameraPose)
        }

        val idealDistance = 1.5f
        return validResults.minByOrNull { hitResult ->
            Math.abs(calculateDistance(hitResult.hitPose, cameraPose) - idealDistance)
        }
    }

    // ─────────────────────────────────────────────
    //  دوال مساعدة
    // ─────────────────────────────────────────────

    private fun isDistanceValid(hitPose: Pose, cameraPose: Pose): Boolean {
        val distance = calculateDistance(hitPose, cameraPose)
        return distance in MIN_PLACEMENT_DISTANCE..MAX_PLACEMENT_DISTANCE
    }

    private fun calculateDistance(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx()
        val dy = a.ty() - b.ty()
        val dz = a.tz() - b.tz()
        return Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }
}

package com.aistudio.mixedreality.ui

import android.util.Log
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState

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
     * **لماذا isEligibleRecoveryPlane وليس inline؟**
     * الشروط المكررة inline (trackingState == TRACKING && type == HORIZONTAL…)
     * تخلق نسختين من نفس القاعدة يمكن أن يتباعدا عند التعديل المستقبلي.
     * [ARTrackingHelper.isEligibleRecoveryPlane] هي المرجع الوحيد للقاعدة.
     *
     * **ترتيب الاختيار:** أقرب نتيجة إلى المسافة المثالية (1.5م).
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

            // ─── فحص السطح ───────────────────────────────────────────────
            // isEligibleRecoveryPlane وisEligiblePhysicalFloorPlane هما
            // المرجعان الوحيدان - لا شروط inline مكررة هنا
            val planeEligible = if (savedPose != null) {
                trackingHelper.isEligibleRecoveryPlane(
                    cameraIsTracking        = cameraIsTracking,
                    planeIsTracking         = plane.trackingState == TrackingState.TRACKING,
                    isUpwardHorizontalPlane = plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING,
                    isSubsumed              = plane.subsumedBy != null,
                    savedPoseInsidePolygon  = plane.isPoseInPolygon(savedPose)
                )
            } else {
                trackingHelper.isEligiblePhysicalFloorPlane(
                    plane           = plane,
                    cameraIsTracking = cameraIsTracking,
                    hitPose         = hitResult.hitPose
                )
            }

            // ─── فحص المسافة (مستقل عن نوع الفحص) ──────────────────────
            planeEligible && isDistanceValid(hitResult.hitPose, cameraPose)
        }

        // اختر الأقرب إلى المسافة المثالية
        val idealDistance = 1.5f
        return validResults.minByOrNull { hitResult ->
            Math.abs(calculateDistance(hitResult.hitPose, cameraPose) - idealDistance)
        }
    }

    // ─────────────────────────────────────────────
    //  دوال مساعدة
    // ─────────────────────────────────────────────

    /**
     * يتحقق من أن مسافة الوضع ضمن النطاق المقبول.
     *
     * @param hitPose وضعية نقطة الاصطدام
     * @param cameraPose وضعية الكاميرا
     * @return true إذا كانت المسافة مقبولة
     */
    private fun isDistanceValid(hitPose: Pose, cameraPose: Pose): Boolean {
        val distance = calculateDistance(hitPose, cameraPose)
        return distance in MIN_PLACEMENT_DISTANCE..MAX_PLACEMENT_DISTANCE
    }

    /**
     * يحسب المسافة الإقليدية بين وضعيتين.
     *
     * @param a الوضعية الأولى
     * @param b الوضعية الثانية
     * @return المسافة بالمتر
     */
    private fun calculateDistance(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx()
        val dy = a.ty() - b.ty()
        val dz = a.tz() - b.tz()
        return Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }
}

package com.aistudio.mixedreality.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.aistudio.mixedreality.ui.theme.NeonCyan
import com.aistudio.mixedreality.ui.theme.NeonGreen
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ARTrackingHelper
 *
 * يدير دورة حياة تتبع AR بالكامل:
 * - اكتشاف الأسطح المادية الأفقية مع تخزين مؤقت بفاصل 250 مللي ثانية
 * - الاسترداد الذكي للنماذج بعد فقدان التتبع
 * - التحقق من ارتفاع السطح قبل إنشاء مرساة الاسترداد
 * - إدارة المرشح المؤجل (delayed placement) مع إعادة التحقق من السطح
 *
 * الاستخدام الصحيح:
 * ```kotlin
 * val helper = ARTrackingHelper()
 * // في onDrawFrame:
 * helper.onFrameUpdate(frame, camera)
 * ```
 */
class ARTrackingHelper {

    companion object {
        private const val TAG = "ARTrackingHelper"

        /** فاصل تحديث اكتشاف الأسطح (مللي ثانية) */
        private const val PLANE_SCAN_INTERVAL_MS = 250L

        /** الحد الأقصى للانحراف المقبول عن الوضعية الأصلية (متر) */
        private const val MAX_RECOVERY_DISTANCE_M = 0.05f

        /** الحد الأقصى لفارق الارتفاع المقبول بين السطح والوضعية المحفوظة */
        private const val MAX_HEIGHT_DELTA_M = 0.10f

        /** عدد الإطارات المتتالية الجيدة المطلوبة لتأكيد الاسترداد */
        private const val RECOVERY_CONFIRMATION_FRAMES = 5
    }

    // ─────────────────────────────────────────────
    //  الحالة الداخلية
    // ─────────────────────────────────────────────

    /** الوضعية الأصلية التي وُضع فيها النموذج */
    var savedOriginalPose: Pose? = null
        private set

    /** المرساة النشطة الحالية */
    var activeAnchor: Anchor? = null
        private set

    /** مرشح الاسترداد المرشح حالياً (ينتظر التحقق) */
    private var recoveryCandidate: Anchor? = null

    /**
     * السطح المرتبط بمرشح الاسترداد.
     * يُحفظ لإعادة التحقق من صلاحية السطح في كل إطار
     * حتى بعد عودة حالة TRACKING للمرشح.
     *
     * ✅ إصلاح: كان مفقوداً - سبب خطأ المراجعة #1
     */
    private var recoveryPlane: Plane? = null

    /** مرساة الوضع المؤجل (Delayed placement) */
    private var pendingPlacementAnchor: Anchor? = null

    /**
     * السطح المرتبط بمرساة الوضع المؤجل.
     * يُحفظ لإعادة التحقق من صلاحية السطح قبل ترقية المرساة.
     *
     * ✅ إصلاح: كان مفقوداً - سبب خطأ المراجعة #2 (Outside diff finding)
     */
    private var pendingPlacementPlane: Plane? = null

    /** هل نحن في وضع الاسترداد؟ */
    var isInRecoveryMode = false
        private set

    /** آخر وقت لاكتشاف الأسطح */
    private var lastPlaneScanTimestamp = 0L

    /** آخر نتيجة لاكتشاف السطح الأفضل (مخبأة) */
    private var cachedFloorPlane: Plane? = null

    /** عداد الإطارات الجيدة منذ آخر استرداد */
    private var recoveryConfirmationCount = 0

    // ─────────────────────────────────────────────
    //  واجهة الأحداث
    // ─────────────────────────────────────────────

    /**
     * واجهة الاستماع لأحداث دورة حياة التتبع والوضع.
     *
     * طريقة التطبيق:
     * ```kotlin
     * trackingHelper.listener = object : ARTrackingHelper.TrackingListener {
     *     override fun onTrackingLost() { showTrackingLostUI() }
     *     override fun onTrackingRecovered(anchor) { hideTrackingLostUI() }
     *     override fun onPlacementReady(anchor) { attachModelTo(anchor) }
     * }
     * ```
     */
    interface TrackingListener {

        /**
         * يُستدعى عند انتقال التتبع من TRACKING إلى PAUSED أو STOPPED
         * وعند بدء بحث الاسترداد.
         * استخدمه لإظهار مؤشر "جارٍ إعادة التتبع" في الواجهة.
         */
        fun onTrackingLost()

        /**
         * يُستدعى بعد نجاح [RECOVERY_CONFIRMATION_FRAMES] إطارات متتالية
         * على مرشح استرداد مقبول.
         *
         * @param anchor المرساة الجديدة التي حلّت محل المرساة المفقودة
         */
        fun onTrackingRecovered(anchor: Anchor)

        /**
         * يُستدعى عند توفر مرساة نشطة وجاهزة لتعليق النموذج عليها،
         * سواء كانت مرساة مباشرة أو مؤجلة (delayed placement) رُقِّيت
         * بعد التحقق من صلاحية سطحها.
         *
         * @param anchor المرساة الجاهزة للاستخدام
         */
        fun onPlacementReady(anchor: Anchor)
    }

    var listener: TrackingListener? = null

    // ─────────────────────────────────────────────
    //  التحقق من صلاحية السطح المادي
    // ─────────────────────────────────────────────

    /**
     * يتحقق من أن السطح مناسب لوضع النموذج أو استرداده.
     *
     * الشروط:
     * 1. الكاميرا تتتبع (TRACKING)
     * 2. السطح نفسه يتتبع (TRACKING)
     * 3. السطح أفقي للأعلى (HORIZONTAL_UPWARD_FACING)
     * 4. السطح غير مستوعَب في سطح آخر (subsumedBy == null)
     * 5. الوضعية المعطاة داخل مضلع السطح
     * 6. ✅ إصلاح Bug رئيسي (PR خط 236-240): التحقق من الارتفاع قبل إنشاء المرساة
     *    - isPoseInPolygon يتحقق فقط من إحداثيات X-Z ويتجاهل Y
     *    - قد يكون السطح في ارتفاع مختلف ويظل isPoseInPolygon = true
     *    - الإصلاح: نضيف فحصاً صريحاً لفارق الارتفاع (Y-axis)
     *
     * @param plane السطح المُكتشف
     * @param cameraIsTracking هل الكاميرا في حالة TRACKING
     * @param hitPose الوضعية المراد التحقق منها (قد تكون null إذا كان المرشح PAUSED)
     * @param checkHeightDelta هل نتحقق من الارتفاع (يُعطّل عند PAUSED)
     * @return true إذا تحققت جميع الشروط
     */
    fun isEligiblePhysicalFloorPlane(
        plane: Plane,
        cameraIsTracking: Boolean,
        hitPose: Pose?,
        checkHeightDelta: Boolean = true
    ): Boolean {
        if (!cameraIsTracking) return false
        if (plane.trackingState != TrackingState.TRACKING) return false
        if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) return false
        if (plane.subsumedBy != null) return false

        if (hitPose != null) {
            if (!plane.isPoseInPolygon(hitPose)) return false

            // ✅ الإصلاح الحرج: التحقق من الارتفاع (Y-axis)
            // isPoseInPolygon يتجاهل محور Y تماماً في ARCore
            if (checkHeightDelta) {
                val planeCenterY = plane.centerPose.ty()
                val poseY = hitPose.ty()
                val heightDelta = abs(planeCenterY - poseY)
                if (heightDelta > MAX_HEIGHT_DELTA_M) {
                    Log.d(TAG, "السطح في ارتفاع مختلف: Δy=${String.format("%.3f", heightDelta)}م > $MAX_HEIGHT_DELTA_M م")
                    return false
                }
            }
        }

        return true
    }

    /**
     * دالة مساعدة موحّدة للتحقق من مرشح الاسترداد.
     *
     * ✅ إصلاح Nitpick (PR خط 42-51): كانت الشروط مكررة inline بدلاً
     * من استخدام isEligiblePhysicalFloorPlane، مما يسبب تباين القواعد.
     *
     * @param cameraIsTracking حالة الكاميرا
     * @param planeIsTracking حالة السطح
     * @param isUpwardHorizontalPlane هل السطح أفقي للأعلى
     * @param isSubsumed هل تم استيعاب السطح
     * @param savedPoseInsidePolygon هل الوضعية داخل مضلع السطح
     * @return true إذا كان المرشح مقبولاً للاسترداد
     */
    fun isEligibleRecoveryPlane(
        cameraIsTracking: Boolean,
        planeIsTracking: Boolean,
        isUpwardHorizontalPlane: Boolean,
        isSubsumed: Boolean,
        savedPoseInsidePolygon: Boolean
    ): Boolean {
        return cameraIsTracking &&
            planeIsTracking &&
            isUpwardHorizontalPlane &&
            !isSubsumed &&
            savedPoseInsidePolygon
    }

    // ─────────────────────────────────────────────
    //  منطق قبول مرشح الاسترداد
    // ─────────────────────────────────────────────

    /**
     * يتحقق من أن مرشح الاسترداد مقبول للترقية إلى مرساة نشطة.
     *
     * @param cameraState حالة الكاميرا
     * @param candidateState حالة مرساة المرشح
     * @param originalPose الوضعية الأصلية المحفوظة
     * @param candidatePose الوضعية الحالية للمرشح
     * @return true إذا كان المرشح جاهزاً للترقية
     */
    private fun isRecoveryCandidateAcceptable(
        cameraState: TrackingState,
        candidateState: TrackingState,
        originalPose: Pose,
        candidatePose: Pose
    ): Boolean {
        if (cameraState != TrackingState.TRACKING) return false
        if (candidateState != TrackingState.TRACKING) return false

        val distance = calculateDistance(originalPose, candidatePose)
        return distance <= MAX_RECOVERY_DISTANCE_M
    }

    // ─────────────────────────────────────────────
    //  اكتشاف الأسطح مع التخزين المؤقت
    // ─────────────────────────────────────────────

    /**
     * يُرجع أفضل سطح أفقي مكتشف، مع تحديث بفاصل 250 مللي ثانية فقط.
     *
     * المنطق:
     * - إذا لم تتتبع الكاميرا → يُرجع null فوراً ويُصفّر الكاش
     * - إذا لم ينقضِ الفاصل الزمني → يُرجع النتيجة المخبأة
     * - إذا انقضى الفاصل → يفحص جميع الأسطح ويُحدّث الكاش
     *
     * @param frame الإطار الحالي
     * @param cameraState حالة الكاميرا الحالية
     * @return أفضل سطح مكتشف أو null
     */
    fun detectFloorPlane(frame: Frame, cameraState: TrackingState): Plane? {
        if (cameraState != TrackingState.TRACKING) {
            cachedFloorPlane = null
            return null
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastPlaneScanTimestamp < PLANE_SCAN_INTERVAL_MS) {
            return cachedFloorPlane
        }

        lastPlaneScanTimestamp = now

        val best = frame.getUpdatedTrackables(Plane::class.java)
            .filter { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
            .filter { it.trackingState == TrackingState.TRACKING }
            .filter { it.subsumedBy == null }
            .maxByOrNull { it.extentX * it.extentZ }

        cachedFloorPlane = best
        return best
    }

    // ─────────────────────────────────────────────
    //  وضع المرساة المبدئي
    // ─────────────────────────────────────────────

    /**
     * يضع مرساة AR على سطح مكتشف ويحفظ الوضعية الأصلية.
     * إذا كانت المرساة في حالة PAUSED عند الإنشاء، تُخزَّن كمرساة مؤجلة
     * مع حفظ السطح للتحقق لاحقاً.
     *
     * @param plane السطح الهدف
     * @param pose الوضعية المطلوبة
     * @param cameraState حالة الكاميرا الحالية
     * @return true إذا نجح وضع المرساة أو حجزها للتأخير
     */
    fun placeAnchor(plane: Plane, pose: Pose, cameraState: TrackingState): Boolean {
        if (!isEligiblePhysicalFloorPlane(plane, cameraState == TrackingState.TRACKING, pose)) {
            Log.w(TAG, "السطح غير مؤهل للوضع")
            return false
        }

        return try {
            val anchor = plane.createAnchor(pose)
            savedOriginalPose = pose

            when (anchor.trackingState) {
                TrackingState.TRACKING -> {
                    activeAnchor = anchor
                    pendingPlacementAnchor = null
                    pendingPlacementPlane = null
                    listener?.onPlacementReady(anchor)
                    Log.i(TAG, "✅ مرساة نشطة فوراً")
                }
                TrackingState.PAUSED -> {
                    // تأخير التفعيل - ✅ إصلاح: نحفظ السطح للتحقق لاحقاً
                    pendingPlacementAnchor = anchor
                    pendingPlacementPlane = plane
                    Log.d(TAG, "⏳ مرساة مؤجلة - الانتظار حتى التتبع")
                }
                else -> {
                    anchor.detach()
                    Log.w(TAG, "🚫 المرساة في حالة STOPPED - تم إلغاؤها")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في إنشاء المرساة: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────
    //  تحديث الإطار الرئيسي
    // ─────────────────────────────────────────────

    /**
     * نقطة الدخول الرئيسية - يجب استدعاؤها في كل إطار تصيير AR.
     *
     * تتسلسل المنطق:
     * 1. التحقق من المرساة المؤجلة (delayed placement)
     * 2. إدارة المرشح النشط (recovery candidate)
     * 3. اكتشاف الأسطح وبدء الاسترداد عند الحاجة
     *
     * @param frame الإطار الحالي من ARCore
     * @param camera كاميرا ARCore
     */
    fun onFrameUpdate(frame: Frame, camera: Camera) {
        val cameraState = camera.trackingState

        checkDelayedPlacement(cameraState)
        checkRecoveryCandidate(frame, cameraState)

        if (cameraState == TrackingState.TRACKING && activeAnchor == null) {
            startRecoverySearch(frame, cameraState)
        }
    }

    // ─────────────────────────────────────────────
    //  الوضع المؤجل (Delayed Placement)
    // ─────────────────────────────────────────────

    /**
     * يفحص المرساة المؤجلة في كل إطار ويرقّيها إذا أصبحت تتتبع
     * وكان سطحها لا يزال صالحاً.
     *
     * ✅ إصلاح Bug "Outside diff" (PR خط 132-133):
     * كانت المرساة المؤجلة تُرقَّى فقط بناءً على حالة المرساة (TRACKING)
     * دون إعادة التحقق من صلاحية السطح الأصلي. ARCore يمكن أن
     * يستمر في تتبع مرساة حتى بعد استيعاب سطحها في سطح آخر،
     * مما يضع النموذج في موضع خاطئ.
     *
     * @param cameraState حالة الكاميرا الحالية
     */
    private fun checkDelayedPlacement(cameraState: TrackingState) {
        val pending = pendingPlacementAnchor ?: return
        val plane = pendingPlacementPlane ?: run {
            // لا سطح محفوظ → لا يمكن التحقق → إلغاء
            pending.detach()
            pendingPlacementAnchor = null
            Log.w(TAG, "🚫 مرساة مؤجلة بدون سطح - تم إلغاؤها")
            return
        }

        when (pending.trackingState) {
            TrackingState.TRACKING -> {
                // ✅ إعادة التحقق من السطح قبل الترقية
                val poseToCheck = savedOriginalPose
                val isPlaneStillValid = isEligiblePhysicalFloorPlane(
                    plane = plane,
                    cameraIsTracking = cameraState == TrackingState.TRACKING,
                    hitPose = poseToCheck
                )

                if (isPlaneStillValid) {
                    activeAnchor = pending
                    pendingPlacementAnchor = null
                    pendingPlacementPlane = null
                    Log.i(TAG, "✅ مرساة مؤجلة رُقِّيت بعد التحقق من السطح")
                    listener?.onPlacementReady(pending)
                } else {
                    pending.detach()
                    pendingPlacementAnchor = null
                    pendingPlacementPlane = null
                    Log.w(TAG, "🚫 السطح لم يعد صالحاً - إلغاء الوضع المؤجل")
                }
            }
            TrackingState.PAUSED -> {
                // لا يزال ينتظر - لا شيء
                Log.v(TAG, "⏳ المرساة المؤجلة لا تزال في PAUSED")
            }
            TrackingState.STOPPED -> {
                pending.detach()
                pendingPlacementAnchor = null
                pendingPlacementPlane = null
                Log.w(TAG, "🚫 المرساة المؤجلة توقفت - تم إلغاؤها")
            }
        }
    }

    // ─────────────────────────────────────────────
    //  إدارة مرشح الاسترداد
    // ─────────────────────────────────────────────

    /**
     * يفحص مرشح الاسترداد الحالي في كل إطار.
     *
     * ✅ إصلاح Bug رئيسي #1 (PR خط 187-193):
     * كان يمرر candidatePose=null إلى isEligiblePhysicalFloorPlane
     * عندما يكون المرشح في حالة PAUSED، مما يجعل physicalPlaneStillValid=false
     * دائماً → يُفصل المرشح حتى لو كان سطحه صالحاً → لا يمكن
     * استئناف التتبع أبداً.
     * الإصلاح: نفصل بين فحص السطح (بدون pose) وفحص الاستمرارية (مع pose).
     *
     * ✅ إصلاح Bug رئيسي #2 (PR خط 195-197):
     * كان مرشح TRACKING فاشل الاستمرارية يظل محجوزاً → لا يمكن إنشاء
     * مرشح بديل.
     * الإصلاح: إذا تجاوز المرشح حد المسافة أثناء TRACKING → نفصله.
     *
     * @param frame الإطار الحالي
     * @param cameraState حالة الكاميرا
     */
    private fun checkRecoveryCandidate(frame: Frame, cameraState: TrackingState) {
        val candidate = recoveryCandidate ?: return
        val plane = recoveryPlane ?: run {
            candidate.detach()
            recoveryCandidate = null
            return
        }

        val originalPose = savedOriginalPose ?: return

        val candidateState = candidate.trackingState

        // ✅ الإصلاح: نفحص السطح أولاً بشكل مستقل (بدون pose) عند PAUSED
        val isPlaneStillValid = when (candidateState) {
            TrackingState.PAUSED -> {
                // المرشح متوقف مؤقتاً - نفحص السطح فقط دون pose
                isEligiblePhysicalFloorPlane(
                    plane = plane,
                    cameraIsTracking = cameraState == TrackingState.TRACKING,
                    hitPose = null,  // لا نمرر pose لتجنب false negative
                    checkHeightDelta = false
                )
            }
            TrackingState.TRACKING -> {
                // المرشح يتتبع - نفحص السطح مع pose كاملة
                val candidatePose = candidate.pose
                isEligiblePhysicalFloorPlane(
                    plane = plane,
                    cameraIsTracking = cameraState == TrackingState.TRACKING,
                    hitPose = candidatePose
                )
            }
            TrackingState.STOPPED -> false
        }

        if (!isPlaneStillValid) {
            candidate.detach()
            recoveryCandidate = null
            recoveryPlane = null
            Log.d(TAG, "🚫 مرشح الاسترداد: السطح لم يعد صالحاً")
            return
        }

        when (candidateState) {
            TrackingState.PAUSED -> {
                // السطح صالح لكن المرشح متوقف - ننتظر
                Log.v(TAG, "⏳ مرشح الاسترداد في PAUSED مع سطح صالح")
            }
            TrackingState.TRACKING -> {
                val candidatePose = candidate.pose

                // ✅ الإصلاح: نفصل المرشح إذا فشل فحص الاستمرارية
                val acceptable = isRecoveryCandidateAcceptable(
                    cameraState, candidateState, originalPose, candidatePose
                )

                if (acceptable) {
                    acceptRecoveryCandidate(candidate)
                } else {
                    // ✅ إصلاح Bug #2: إذا فشل الفحص أثناء TRACKING → نفصله لإتاحة بديل
                    Log.d(TAG, "🚫 مرشح TRACKING تجاوز حد المسافة - يُفصل لإتاحة البديل")
                    candidate.detach()
                    recoveryCandidate = null
                    recoveryPlane = null
                }
            }
            TrackingState.STOPPED -> {
                candidate.detach()
                recoveryCandidate = null
                recoveryPlane = null
                Log.d(TAG, "🚫 مرشح الاسترداد توقف")
            }
        }
    }

    /**
     * يرقّي مرشح الاسترداد إلى مرساة نشطة عند نجاح التحقق.
     *
     * @param candidate المرشح المقبول
     */
    private fun acceptRecoveryCandidate(candidate: Anchor) {
        recoveryConfirmationCount++
        if (recoveryConfirmationCount < RECOVERY_CONFIRMATION_FRAMES) {
            Log.d(TAG, "🔄 استرداد: $recoveryConfirmationCount/$RECOVERY_CONFIRMATION_FRAMES إطارات")
            return
        }

        activeAnchor?.detach()
        activeAnchor = candidate
        recoveryCandidate = null
        recoveryPlane = null
        isInRecoveryMode = false
        recoveryConfirmationCount = 0
        Log.i(TAG, "✅ استرداد المرساة نجح بعد $RECOVERY_CONFIRMATION_FRAMES إطارات")
        listener?.onTrackingRecovered(candidate)
    }

    // ─────────────────────────────────────────────
    //  بحث الاسترداد
    // ─────────────────────────────────────────────

    /**
     * يبحث عن سطح مناسب لبدء مرشح الاسترداد.
     *
     * ✅ يستخدم isEligibleRecoveryPlane بدلاً من تكرار الشروط inline
     * كما طلبت مراجعة CodeRabbit (Nitpick / PR خط 42-51).
     *
     * @param frame الإطار الحالي
     * @param cameraState حالة الكاميرا
     */
    private fun startRecoverySearch(frame: Frame, cameraState: TrackingState) {
        if (recoveryCandidate != null) return
        val originalPose = savedOriginalPose ?: return

        isInRecoveryMode = true
        recoveryConfirmationCount = 0
        listener?.onTrackingLost()

        val eligiblePlane = frame.getUpdatedTrackables(Plane::class.java)
            .firstOrNull { plane ->
                // ✅ استخدام isEligibleRecoveryPlane بدلاً من الشروط المكررة
                isEligibleRecoveryPlane(
                    cameraIsTracking = cameraState == TrackingState.TRACKING,
                    planeIsTracking = plane.trackingState == TrackingState.TRACKING,
                    isUpwardHorizontalPlane = plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING,
                    isSubsumed = plane.subsumedBy != null,
                    savedPoseInsidePolygon = plane.isPoseInPolygon(originalPose)
                )
            }

        eligiblePlane?.let { plane ->
            try {
                val anchor = plane.createAnchor(originalPose)
                recoveryCandidate = anchor
                recoveryPlane = plane  // ✅ حفظ السطح مع المرشح
                Log.d(TAG, "🔍 مرشح استرداد جديد على سطح ${plane.type}")
            } catch (e: Exception) {
                Log.e(TAG, "فشل إنشاء مرشح الاسترداد: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────
    //  دوال مساعدة
    // ─────────────────────────────────────────────

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
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    // ─────────────────────────────────────────────
    //  إعادة التعيين
    // ─────────────────────────────────────────────

    /**
     * يُعيد تعيين الحالة الكاملة ويُفصل جميع المراسي.
     * استخدمها عند إعادة تشغيل الجلسة أو بدء تجربة جديدة.
     */
    fun reset() {
        activeAnchor?.detach()
        recoveryCandidate?.detach()
        pendingPlacementAnchor?.detach()

        activeAnchor = null
        recoveryCandidate = null
        recoveryPlane = null
        pendingPlacementAnchor = null
        pendingPlacementPlane = null
        savedOriginalPose = null
        isInRecoveryMode = false
        cachedFloorPlane = null
        lastPlaneScanTimestamp = 0L
        recoveryConfirmationCount = 0

        Log.i(TAG, "🔁 ARTrackingHelper reset")
    }
}

private const val TRACKING_TAG = "ARTracking"
private const val RECOVERY_GRACE_NANOS = 1_000_000_000L

data class PendingPlacementTap(
    val offset: Offset,
    val creationTime: Long = System.currentTimeMillis()
)

class ARTrackingManager(
    val modeName: String,
    val onHapticFeedback: () -> Unit = {}
) {
    var activeAnchor by mutableStateOf<Anchor?>(null)
        private set

    var anchorTrackingState by mutableStateOf<TrackingState?>(null)
        private set

    var isPlaneDetected by mutableStateOf(false)
        private set

    private val pendingPlacementRef =
        AtomicReference<PendingPlacementTap?>(null)

    private val recoveryGate = AnchorRecoveryGate()

    private var lastCameraTrackingState: TrackingState? = null
    private var lastVerifiedWorldPose: Pose? = null
    private var trackingLostSinceNanos: Long? = null

    val trackingHelper = ARTrackingHelper()

    fun onSingleTap(
        tapOffset: Offset,
        isModelVisible: Boolean
    ) {
        if (!isModelVisible) {
            Log.d(TRACKING_TAG, "[$modeName] Tap ignored: model hidden")
            return
        }

        pendingPlacementRef.set(
            PendingPlacementTap(offset = tapOffset)
        )

        Log.d(TRACKING_TAG, "[$modeName] Placement tap queued")
    }

    fun onAnchorTrackingStateChanged(
        newState: TrackingState
    ) {
        if (anchorTrackingState != newState) {
            Log.i(
                TRACKING_TAG,
                "[$modeName] Anchor state: $anchorTrackingState -> $newState"
            )
            anchorTrackingState = newState
        }
    }

    fun onSessionUpdated(
        session: Session,
        frame: Frame,
        isModelVisible: Boolean,
        splitWidthPx: Float = 0f,
        onPlaced: () -> Unit = {}
    ) {
        val cameraState = runCatching {
            frame.camera.trackingState
        }.getOrNull()

        if (lastCameraTrackingState != cameraState) {
            Log.i(
                TRACKING_TAG,
                "[$modeName] Camera state: $lastCameraTrackingState -> $cameraState"
            )
            lastCameraTrackingState = cameraState
        }

        if (cameraState != TrackingState.TRACKING) {
            updateAnchorStateWithoutPoseUpdate()
            return
        }

        processPendingPlacement(
            frame = frame,
            isModelVisible = isModelVisible,
            splitWidthPx = splitWidthPx,
            onPlaced = onPlaced
        )

        processActiveAnchor(
            session = session,
            cameraState = cameraState,
            onPlaced = onPlaced
        )

        updateRealHorizontalPlaneStatus(session, cameraState)
    }

    private fun processPendingPlacement(
        frame: Frame,
        isModelVisible: Boolean,
        splitWidthPx: Float,
        onPlaced: () -> Unit
    ) {
        val pending = pendingPlacementRef.get() ?: return

        val elapsedMs = (
            System.currentTimeMillis() - pending.creationTime
        ).coerceAtLeast(0L)

        when (
            placementTapDecision(
                isModelVisible = isModelVisible,
                cameraIsTracking = true,
                elapsedMs = elapsedMs
            )
        ) {
            PlacementTapDecision.DISCARD -> {
                pendingPlacementRef.compareAndSet(pending, null)
                Log.d(TRACKING_TAG, "[$modeName] Expired placement tap discarded")
                return
            }

            PlacementTapDecision.HIT_TEST_ON_CURRENT_FRAME -> Unit
        }

        if (!pendingPlacementRef.compareAndSet(pending, null)) {
            return
        }

        val hitX = if (
            splitWidthPx > 0f &&
            pending.offset.x > splitWidthPx
        ) {
            pending.offset.x - splitWidthPx
        } else {
            pending.offset.x
        }

        // Only accept a physically detected, tracked horizontal floor plane.
        // Estimated Instant Placement points are intentionally excluded.
        val floorHit = runCatching {
            frame.hitTest(
                hitX,
                pending.offset.y
            ).firstOrNull { hit ->
                val plane = hit.trackable as? Plane
                    ?: return@firstOrNull false

                plane.trackingState == TrackingState.TRACKING &&
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                    plane.subsumedBy == null &&
                    plane.isPoseInPolygon(hit.hitPose)
            }
        }.onFailure {
            Log.w(TRACKING_TAG, "[$modeName] Physical plane hit test failed", it)
        }.getOrNull()

        if (floorHit == null) {
            Log.w(
                TRACKING_TAG,
                "[$modeName] No tracked horizontal floor plane found"
            )
            return
        }

        val candidate = runCatching {
            floorHit.createAnchor()
        }.onFailure {
            Log.e(TRACKING_TAG, "[$modeName] Anchor creation failed", it)
        }.getOrNull() ?: return

        // Never commit a PAUSED or STOPPED placement anchor.
        val verifiedPose = verifiedAnchorPose(
            anchor = candidate,
            cameraState = TrackingState.TRACKING
        )

        if (verifiedPose == null) {
            runCatching { candidate.detach() }
            Log.w(
                TRACKING_TAG,
                "[$modeName] Placement rejected: anchor is not TRACKING"
            )
            return
        }

        promotePlacementAnchor(
            candidate = candidate,
            verifiedPose = verifiedPose,
            onPlaced = onPlaced
        )

        onHapticFeedback()
    }

    private fun processActiveAnchor(
        session: Session,
        cameraState: TrackingState?,
        onPlaced: () -> Unit
    ) {
        val anchor = activeAnchor ?: return

        val state = runCatching {
            anchor.trackingState
        }.getOrNull()

        if (state != null && anchorTrackingState != state) {
            anchorTrackingState = state
            Log.i(TRACKING_TAG, "[$modeName] Active anchor state: $state")
        }

        when (state) {
            TrackingState.TRACKING -> {
                val verifiedPose = verifiedAnchorPose(
                    anchor,
                    cameraState
                )

                if (verifiedPose != null) {
                    val dampedPose = filterPoseWithLegacyDamping(
                        rawPose = verifiedPose,
                        lastPose = lastVerifiedWorldPose,
                        deadbandMeters = 0.0015f,
                        alpha = 0.35f
                    )
                    lastVerifiedWorldPose = dampedPose
                    trackingLostSinceNanos = null
                    recoveryGate.reset()
                }
            }

            TrackingState.PAUSED,
            TrackingState.STOPPED -> {
                val now = System.nanoTime()

                val lostSince = trackingLostSinceNanos
                    ?: now.also {
                        trackingLostSinceNanos = it
                    }

                val lostDuration = (
                    now - lostSince
                ).coerceAtLeast(0L)

                val canRecover = shouldAttemptAnchorRecovery(
                    cameraState = cameraState,
                    anchorState = state,
                    lostDurationNanos = lostDuration,
                    gracePeriodNanos = RECOVERY_GRACE_NANOS
                )

                if (!canRecover || !recoveryGate.shouldAttempt()) {
                    return
                }

                val savedPose = lastVerifiedWorldPose

                if (savedPose == null) {
                    Log.w(
                        TRACKING_TAG,
                        "[$modeName] Recovery skipped: no verified saved pose"
                    )
                    return
                }

                val candidate = runCatching {
                    session.createAnchor(savedPose)
                }.onFailure {
                    Log.w(TRACKING_TAG, "[$modeName] Anchor recovery failed", it)
                }.getOrNull() ?: return

                val candidatePose = verifiedAnchorPose(
                    candidate,
                    cameraState
                )

                val acceptable = isRecoveryCandidateAcceptable(
                    cameraState = cameraState,
                    candidateState = candidate.trackingState,
                    savedPose = savedPose,
                    candidatePose = candidatePose
                )

                if (acceptable && candidatePose != null) {
                    promotePlacementAnchor(
                        candidate = candidate,
                        verifiedPose = candidatePose,
                        onPlaced = onPlaced
                    )

                    Log.i(
                        TRACKING_TAG,
                        "[$modeName] Anchor recovered with verified pose"
                    )
                } else {
                    runCatching { candidate.detach() }

                    Log.w(
                        TRACKING_TAG,
                        "[$modeName] Recovery candidate rejected"
                    )
                }
            }

            null -> {
                Log.w(
                    TRACKING_TAG,
                    "[$modeName] Unable to read anchor tracking state"
                )
            }
        }
    }

    private fun verifiedAnchorPose(
        anchor: Anchor,
        cameraState: TrackingState?
    ): Pose? {
        val before = runCatching {
            anchor.trackingState
        }.getOrNull()

        if (!isVerifiedTrackingPose(cameraState, before)) {
            return null
        }

        val pose = runCatching {
            anchor.pose
        }.onFailure {
            Log.w(TRACKING_TAG, "[$modeName] Anchor pose read failed", it)
        }.getOrNull() ?: return null

        val after = runCatching {
            anchor.trackingState
        }.getOrNull()

        if (!isVerifiedTrackingPose(cameraState, after)) {
            return null
        }

        if (
            !pose.tx().isFinite() ||
            !pose.ty().isFinite() ||
            !pose.tz().isFinite()
        ) {
            return null
        }

        return pose
    }

    private fun promotePlacementAnchor(
        candidate: Anchor,
        verifiedPose: Pose,
        onPlaced: () -> Unit
    ) {
        val previousAnchor = activeAnchor

        activeAnchor = candidate
        anchorTrackingState = TrackingState.TRACKING
        lastVerifiedWorldPose = verifiedPose

        trackingLostSinceNanos = null
        recoveryGate.reset()

        if (previousAnchor !== candidate) {
            runCatching {
                previousAnchor?.detach()
            }
        }

        Log.i(
            TRACKING_TAG,
            "[$modeName] Verified anchor committed"
        )

        onPlaced()
    }

    private fun updateAnchorStateWithoutPoseUpdate() {
        val anchor = activeAnchor ?: return

        val state = runCatching {
            anchor.trackingState
        }.getOrNull()

        if (state != null && anchorTrackingState != state) {
            anchorTrackingState = state
            Log.i(
                TRACKING_TAG,
                "[$modeName] Anchor state while camera unavailable: $state"
            )
        }
    }

    private fun updateRealHorizontalPlaneStatus(
        session: Session,
        cameraState: TrackingState?
    ) {
        val detected =
            cameraState == TrackingState.TRACKING &&
                runCatching {
                    session.getAllTrackables(Plane::class.java).any { plane ->
                        plane.trackingState == TrackingState.TRACKING &&
                            plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            plane.subsumedBy == null
                    }
                }.getOrDefault(false)

        if (detected != isPlaneDetected) {
            isPlaneDetected = detected

            Log.d(
                TRACKING_TAG,
                "[$modeName] Real horizontal plane detected: $detected"
            )
        }
    }

    fun clear() {
        Log.d(TRACKING_TAG, "[$modeName] Clearing tracking state")

        runCatching {
            activeAnchor?.detach()
        }

        activeAnchor = null
        anchorTrackingState = null
        lastVerifiedWorldPose = null
        trackingLostSinceNanos = null

        pendingPlacementRef.set(null)
        recoveryGate.reset()

        isPlaneDetected = false
        lastCameraTrackingState = null
    }

    fun dispose() = clear()

    fun getHudStatus(): Pair<String, Color> = when {
        activeAnchor != null &&
            anchorTrackingState == TrackingState.TRACKING ->
            "Model placed • Tap floor to move" to NeonGreen

        activeAnchor != null &&
            anchorTrackingState == TrackingState.PAUSED ->
            "Tracking paused • Move device slowly" to Color(0xFFFFB300)

        activeAnchor != null &&
            anchorTrackingState == TrackingState.STOPPED ->
            "Tracking lost • Scan floor and tap to place" to Color(0xFFFF5252)

        activeAnchor != null ->
            "Model placed • Tap floor to move" to NeonGreen

        isPlaneDetected ->
            "Tracked floor detected • Tap to place" to NeonGreen

        else ->
            "Point camera at floor and move slowly" to NeonCyan
    }
}

@Composable
fun rememberARTrackingManager(
    modeName: String,
    onHapticFeedback: () -> Unit = {}
): ARTrackingManager = remember(modeName) {
    ARTrackingManager(
        modeName = modeName,
        onHapticFeedback = onHapticFeedback
    )
}


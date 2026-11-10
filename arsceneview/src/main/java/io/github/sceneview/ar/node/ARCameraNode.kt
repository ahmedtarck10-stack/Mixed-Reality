package io.github.sceneview.ar.node

import com.google.android.filament.Engine
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.arcore.getProjectionTransform
import io.github.sceneview.ar.arcore.position
import io.github.sceneview.ar.arcore.quaternion
import io.github.sceneview.ViewportPadding
import io.github.sceneview.math.Transform
import io.github.sceneview.node.CameraNode
import io.github.sceneview.utils.setCustomProjection

/**
 * Represents a virtual camera, which determines the perspective through which the scene is viewed.
 *
 * If the camera is part of an AR scene, then the camera automatically tracks the
 * camera pose from ARCore.
 *
 * The following methods will throw [ ] when called:
 * - [parent] - CameraNode's parent cannot be changed, it is always the scene.
 * - [position] - CameraNode's position cannot be changed, it is controlled by the ARCore camera
 * pose.
 * - [rotation] - CameraNode's rotation cannot be changed, it is controlled by the ARCore camera
 * pose.
 *
 * The **projection** is owned by ARCore too: it is re-derived from the physical camera's intrinsics
 * on every tracked frame, so [focalLength] has no effect here and [updateProjection] restores
 * ARCore's matrix rather than building one from a lens (#2950). Set [near] / [far] to move the clip
 * planes — those *are* honoured, and ARCore rebuilds its projection around them.
 *
 * All other functionality in Node is supported. You can access the position and rotation of the
 * camera, assign a collision shape to the camera, or add children to the camera. Disabling the
 * camera turns off rendering.
 */
open class ARCameraNode(engine: Engine) : CameraNode(engine) {

    /**
     * The virtual camera pose in world space for rendering AR content onto the latest frame.
     *
     * This is an OpenGL camera pose with +X pointing right, +Y pointing up, and -Z pointing in the
     * direction the camera is looking, with "right" and "up" being relative to current logical
     * display orientation.
     *
     * Note: This pose is only useful when [trackingState] returns [TrackingState.TRACKING] and
     * otherwise should not be used.
     */
    open var pose: Pose? = null
        protected set(value) {
            if (field != value) {
                field = value
                value?.let {
                    worldPosition = it.position
                    worldQuaternion = it.quaternion
                }
            }
        }

    open var trackingState = TrackingState.STOPPED
        protected set(value) {
            if (field != value) {
                field = value
                onTrackingStateChanged(value)
            }
        }

    var session: Session? = null
    var frame: Frame? = null

    private var cachedProjectionTransform: Transform? = null
    private var cachedProjectionNear: Float = Float.NaN
    private var cachedProjectionFar: Float = Float.NaN
    private var displayGeometryChanged: Boolean = true
    private var projectionInvalidated: Boolean = false

    override var contentPadding: ViewportPadding
        get() = ViewportPadding.Zero
        set(@Suppress("UNUSED_PARAMETER") value) = Unit

    open fun update(session: Session, frame: Frame) {
        this.session = session
        this.frame = frame
        displayGeometryChanged = frame.hasDisplayGeometryChanged()
        onCameraUpdated(frame.camera)
    }

    open fun onCameraUpdated(camera: Camera) {
        trackingState = camera.trackingState
        pose = camera.displayOrientedPose

        val near = near
        val far = far

        if (shouldRecomputeProjection(
                cached = cachedProjectionTransform != null,
                displayGeometryChanged = displayGeometryChanged,
                projectionInvalidated = projectionInvalidated,
                near = near,
                far = far,
                cachedNear = cachedProjectionNear,
                cachedFar = cachedProjectionFar,
            )
        ) {
            applyARProjection(camera, near, far)
        }

        displayGeometryChanged = true
    }

    override fun updateProjection(
        focalLength: Double,
        near: Float,
        far: Float,
        aspect: Double
    ) {
        val arCamera = frame?.camera
        if (arCamera != null) {
            applyARProjection(arCamera, near, far)
        } else {
            super.updateProjection(focalLength, near, far, aspect)
            projectionInvalidated = true
        }
    }

    private fun applyARProjection(arCamera: Camera, near: Float, far: Float) {
        val transform = arCamera.getProjectionTransform(near, far)
        cachedProjectionTransform = transform
        cachedProjectionNear = near
        cachedProjectionFar = far
        projectionInvalidated = false
        camera.setCustomProjection(transform, near.toDouble(), far.toDouble())
    }

    open fun onTrackingStateChanged(trackingState: TrackingState) {
    }
}

internal fun shouldRecomputeProjection(
    cached: Boolean,
    displayGeometryChanged: Boolean,
    projectionInvalidated: Boolean,
    near: Float,
    far: Float,
    cachedNear: Float,
    cachedFar: Float,
): Boolean = !cached ||
    projectionInvalidated ||
    displayGeometryChanged ||
    near != cachedNear ||
    far != cachedFar
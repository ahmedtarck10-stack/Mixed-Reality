package io.github.sceneview.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay

enum class ARMotionSensorHealth {
    DELIVERING,
    SILENT,
    UNKNOWN,
}

data class ARMotionSensorSample(
    val gyroscopeEvents: Int,
    val accelerometerEvents: Int,
    val windowMillis: Long,
) {
    val health: ARMotionSensorHealth
        get() = arMotionSensorHealth(
            gyroscopeEvents = gyroscopeEvents,
            accelerometerEvents = accelerometerEvents,
            windowMillis = windowMillis
        )
}

internal const val AR_MOTION_SENSOR_MIN_WINDOW_MS = 500L
internal const val AR_MOTION_SENSOR_MIN_REFERENCE_EVENTS = 10
internal const val AR_MOTION_SENSOR_PROBE_WINDOW_MS = 1_000L
internal const val AR_MOTION_SENSOR_NOT_LISTENED = -1

internal fun arMotionSensorHealth(
    gyroscopeEvents: Int,
    accelerometerEvents: Int,
    windowMillis: Long,
): ARMotionSensorHealth = when {
    gyroscopeEvents < 0 || accelerometerEvents < 0 ->
        ARMotionSensorHealth.UNKNOWN

    windowMillis < AR_MOTION_SENSOR_MIN_WINDOW_MS ->
        ARMotionSensorHealth.UNKNOWN

    gyroscopeEvents >= AR_MOTION_SENSOR_MIN_REFERENCE_EVENTS &&
        accelerometerEvents >= AR_MOTION_SENSOR_MIN_REFERENCE_EVENTS ->
        ARMotionSensorHealth.DELIVERING

    gyroscopeEvents > 0 || accelerometerEvents > 0 ->
        ARMotionSensorHealth.UNKNOWN

    else -> ARMotionSensorHealth.SILENT
}

suspend fun probeARMotionSensors(
    context: Context,
    windowMillis: Long = AR_MOTION_SENSOR_PROBE_WINDOW_MS,
): ARMotionSensorSample? {
    val manager = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ?: return null

    return probeARMotionSensors(manager, windowMillis) { listener, sensor ->
        manager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_GAME
        )
    }
}

internal suspend fun probeARMotionSensors(
    manager: SensorManager,
    windowMillis: Long,
    register: (SensorEventListener, Sensor) -> Boolean,
): ARMotionSensorSample? {
    val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        ?: manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        ?: return null

    val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)
        ?: return null

    val gyroscopeEvents = AtomicInteger(0)
    val accelerometerEvents = AtomicInteger(0)

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> {
                    gyroscopeEvents.incrementAndGet()
                }

                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> {
                    accelerometerEvents.incrementAndGet()
                }
            }
        }

        override fun onAccuracyChanged(
            sensor: Sensor,
            accuracy: Int,
        ) = Unit
    }

    val startTime = System.nanoTime()

    try {
        val accelerometerListened = register(listener, accelerometer)

        if (!accelerometerListened) {
            return ARMotionSensorSample(
                gyroscopeEvents = AR_MOTION_SENSOR_NOT_LISTENED,
                accelerometerEvents = AR_MOTION_SENSOR_NOT_LISTENED,
                windowMillis = 0L
            )
        }

        val gyroscopeListened = register(listener, gyroscope)

        if (!gyroscopeListened) {
            return ARMotionSensorSample(
                gyroscopeEvents = AR_MOTION_SENSOR_NOT_LISTENED,
                accelerometerEvents = 0,
                windowMillis = 0L
            )
        }

        delay(windowMillis.coerceAtLeast(0L))
    } finally {
        manager.unregisterListener(listener)
    }

    val elapsedMillis =
        (System.nanoTime() - startTime) / 1_000_000L

    return ARMotionSensorSample(
        gyroscopeEvents = gyroscopeEvents.get(),
        accelerometerEvents = accelerometerEvents.get(),
        windowMillis = elapsedMillis
    )
}

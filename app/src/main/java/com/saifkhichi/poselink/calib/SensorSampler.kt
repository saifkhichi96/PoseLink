// calib/SensorSampler.kt
package com.saifkhichi.poselink.calib

import android.content.Context
import android.hardware.*
import android.os.Handler
import android.os.HandlerThread
import kotlin.math.sqrt

class SensorSampler(ctx: Context) : SensorEventListener {

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val thread = HandlerThread("CalibSensors").apply { start() }
    private val handler = Handler(thread.looper)

    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro  = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val mag   = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // Latest values
    @Volatile var lastAcc: FloatArray? = null
    @Volatile var lastGyro: FloatArray? = null
    @Volatile var lastMag: FloatArray? = null

    fun start() {
        accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        gyro ?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        mag  ?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
    }

    fun stop() {
        sm.unregisterListener(this)
        thread.quitSafely()
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> lastAcc = e.values.clone()
            Sensor.TYPE_GYROSCOPE     -> lastGyro = e.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD-> lastMag  = e.values.clone()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        fun norm3(v: FloatArray?): Double {
            if (v == null) return 0.0
            return sqrt((v[0]*v[0] + v[1]*v[1] + v[2]*v[2]).toDouble())
        }
    }
}
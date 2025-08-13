package com.saifkhichi.poselink.core.sensors

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.preference.PreferenceManager
import com.saifkhichi.poselink.streaming.SensorJsonProvider
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.util.ArrayDeque
import java.util.Deque
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class IMUManager(activity: Activity) : SensorEventListener, SensorJsonProvider {

    // ---------- config ----------
    private val G = 9.80665f
    private val STILL_GYRO_MAX = 0.03f       // rad/s
    private val STILL_ACC_DEV_MAX = 0.12f    // | |acc|-g | (m/s^2)
    private val STILL_MIN_SAMPLES = 10       // consecutive samples
    private val MAX_BUF = 512                // ring buffer cap
    private val INTERP_TOL_NS = 2_000_000L   // 2 ms tolerance for accel-gyro interpolation

    private class SensorPacket(
        var timestamp: Long,   // nanoseconds (SensorEvent.timestamp)
        var unixTime: Long,    // milliseconds (System.currentTimeMillis)
        var values: FloatArray // clone of event.values
    ) {
        override fun toString(): String {
            val delimiter = ","
            val sb = StringBuilder()
            sb.append(timestamp)
            for (value in values) sb.append(delimiter).append(value)
            // append unix time in ns for logs
            sb.append(delimiter).append(unixTime).append("000000")
            return sb.toString()
        }
    }

    private val sensorManager: SensorManager =
        activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Raw / fused sensors (guard by null)
    private val accel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val mag: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotVec: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroUnc: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
    private val accelUnc: Sensor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)
        else null

    @Volatile
    private var recording = false
    private var dataWriter: BufferedWriter? = null
    private var sensorThread: HandlerThread? = null

    // Buffers for sync / logging
    private val gyroData: Deque<SensorPacket> = ArrayDeque()
    private val accelData: Deque<SensorPacket> = ArrayDeque()
    private val magData: Deque<SensorPacket> = ArrayDeque()

    // Latest snapshots for /sensors.json
    @Volatile
    private var lastAccel: SensorPacket? = null

    @Volatile
    private var lastGyro: SensorPacket? = null

    @Volatile
    private var lastMag: SensorPacket? = null

    @Volatile
    private var lastRot: SensorPacket? = null

    @Volatile
    private var lastGyroUnc: SensorPacket? = null

    @Volatile
    private var lastAccelUnc: SensorPacket? = null

    // Stationarity detector
    @Volatile
    private var stationary = false
    private var stillCount = 0

    init {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    // ---------- recording to file (optional) ----------
    fun startRecording(captureResultFile: String) {
        try {
            dataWriter = BufferedWriter(FileWriter(captureResultFile, false))
            if (gyro == null || accel == null) {
                val warning = buildString {
                    appendLine("The device may not have a gyroscope or an accelerometer!")
                    appendLine("No IMU data will be logged.")
                    appendLine("Has Gyroscope? ${if (gyro == null) "No" else "Yes"}")
                    appendLine("Has Accelerometer? ${if (accel == null) "No" else "Yes"}")
                }
                dataWriter!!.write(warning)
            } else {
                dataWriter!!.write(ImuHeader)
            }
            recording = true
        } catch (err: IOException) {
            Timber.e(err, "IOException opening inertial data writer at %s", captureResultFile)
        }
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        try {
            dataWriter?.flush()
            dataWriter?.close()
        } catch (err: IOException) {
            Timber.e(err, "IOException closing inertial data writer")
        } finally {
            dataWriter = null
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------- small helpers ----------
    private fun trim(d: Deque<*>) {
        while (d.size > MAX_BUF) {
            d.removeFirst()
        }
    }

    private fun norm3(v: FloatArray): Float {
        val x = v[0]
        val y = v[1]
        val z = v[2]
        return sqrt(x * x + y * y + z * z)
    }

    private fun updateStationary() {
        val g = lastGyro?.values
        val a = lastAccel?.values
        if (g == null || a == null) return

        val gyroNorm = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        val accDev = abs(norm3(a) - G)
        val isStillNow = (gyroNorm < STILL_GYRO_MAX) && (accDev < STILL_ACC_DEV_MAX)
        stillCount = if (isStillNow) stillCount + 1 else max(0, stillCount - 1)
        stationary = (stillCount >= STILL_MIN_SAMPLES)
    }

    // Interpolate accel to the current oldest gyro timestamp for logging
    private fun syncInertialDataForLog(): SensorPacket? {
        if (gyroData.isEmpty() || accelData.size < 2) return null
        val oldestGyro = gyroData.peekFirst() ?: return null
        val oldestAccel = accelData.peekFirst() ?: return null

        if (oldestGyro.timestamp < oldestAccel.timestamp) {
            // gyro is older than earliest accel -> drop one gyro
            gyroData.removeFirst()
            return null
        }
        val latestAccel = accelData.peekLast() ?: return null
        if (oldestGyro.timestamp > latestAccel.timestamp) {
            // gyro is newer than latest accel -> keep only the most recent accel to wait
            Timber.w("throwing #accel data %d", accelData.size - 1)
            accelData.clear()
            accelData.add(latestAccel)
            return null
        }

        // find bracketing accel samples
        var leftAccel: SensorPacket? = null
        var rightAccel: SensorPacket? = null
        for (packet in accelData) {
            if (packet.timestamp <= oldestGyro.timestamp) leftAccel = packet
            else {
                rightAccel = packet; break
            }
        }
        if (leftAccel == null) return null

        val gyroAccel = FloatArray(6)
        gyroAccel[0] = oldestGyro.values[0]
        gyroAccel[1] = oldestGyro.values[1]
        gyroAccel[2] = oldestGyro.values[2]

        val sp = SensorPacket(oldestGyro.timestamp, oldestGyro.unixTime, gyroAccel)

        when {
            oldestGyro.timestamp - leftAccel.timestamp <= INTERP_TOL_NS -> {
                // use left accel
                val a = leftAccel.values
                gyroAccel[3] = a[0]; gyroAccel[4] = a[1]; gyroAccel[5] = a[2]
            }

            rightAccel != null && rightAccel.timestamp - oldestGyro.timestamp <= INTERP_TOL_NS -> {
                // use right accel
                val a = rightAccel.values
                gyroAccel[3] = a[0]; gyroAccel[4] = a[1]; gyroAccel[5] = a[2]
            }

            else -> {
                // linear interpolate
                val la = leftAccel
                val ra = rightAccel ?: return null
                val dt = (ra.timestamp - la.timestamp).toFloat()
                if (dt <= 0f) return null
                val u = (oldestGyro.timestamp - la.timestamp).toFloat() / dt
                gyroAccel[3] = la.values[0] + (ra.values[0] - la.values[0]) * u
                gyroAccel[4] = la.values[1] + (ra.values[1] - la.values[1]) * u
                gyroAccel[5] = la.values[2] + (ra.values[2] - la.values[2]) * u
            }
        }

        gyroData.removeFirst()
        // drop old accel
        val it = accelData.iterator()
        while (it.hasNext()) {
            val packet = it.next()
            if (packet.timestamp < leftAccel.timestamp) it.remove()
            else break
        }
        return sp
    }

    override fun onSensorChanged(event: SensorEvent) {
        val unixTime = System.currentTimeMillis()
        val sp = SensorPacket(event.timestamp, unixTime, event.values.clone())

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelData.add(sp); trim(accelData)
                lastAccel = sp
                updateStationary()
            }

            Sensor.TYPE_GYROSCOPE -> {
                gyroData.add(sp); trim(gyroData)
                lastGyro = sp
                updateStationary()
                // optional log row (gyro + accel aligned to gyro time)
                val synced = syncInertialDataForLog()
                if (synced != null && recording) {
                    try {
                        dataWriter?.write(synced.toString() + "\n")
                    } catch (ioe: IOException) {
                        Timber.e(ioe)
                    }
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                magData.add(sp); trim(magData)
                lastMag = sp
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                // Build quaternion xyzw; event.values may be length 3 or 4 (or 5 with heading accuracy)
                val v = sp.values
                val x = v[0]
                val y = v[1]
                val z = v[2]
                val w = if (v.size >= 4) v[3] else {
                    val s = max(0f, 1f - x * x - y * y - z * z)
                    sqrt(s)
                }
                sp.values = floatArrayOf(x, y, z, w)
                lastRot = sp
            }

            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> {
                lastGyroUnc = sp // values: [wx,wy,wz, biasx,biasy,biasz]
            }
            // Available from API 26
            35 /*Sensor.TYPE_ACCELEROMETER_UNCALIBRATED*/ -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) lastAccelUnc =
                    sp // [ax,ay,az, biasx,biasy,biasz]
            }
        }
    }

    // ---------- registration ----------
    fun register() {
        sensorThread = HandlerThread(
            "SensorThread", Process.THREAD_PRIORITY_MORE_FAVORABLE
        ).also { it.start() }
        val handler = Handler(sensorThread!!.looper)

        val pref = sharedPreferences.getString("prefImuFreq", "GAME") ?: "GAME"
        val samplingUs = resolveSamplingPeriodUs(pref)

        accel?.let { sensorManager.registerListener(this, it, samplingUs, 0, handler) }
        gyro?.let { sensorManager.registerListener(this, it, samplingUs, 0, handler) }
        mag?.let { sensorManager.registerListener(this, it, samplingUs, 0, handler) }
        rotVec?.let { sensorManager.registerListener(this, it, samplingUs, 0, handler) }
        gyroUnc?.let { sensorManager.registerListener(this, it, samplingUs, 0, handler) }
        accelUnc?.let { sensorManager.registerListener(this, it, samplingUs, 0, handler) }
    }

    fun unregister() {
        sensorManager.unregisterListener(this)
        sensorThread?.quitSafely()
        stopRecording()
    }

    private fun resolveSamplingPeriodUs(pref: String): Int {
        // Accept symbolic names or explicit microseconds; keep backward compat for "0,1,2,3"
        return when (pref.uppercase()) {
            "FASTEST", "0" -> SensorManager.SENSOR_DELAY_FASTEST  // requires HIGH_SAMPLING_RATE_SENSORS permission
            "GAME", "1" -> SensorManager.SENSOR_DELAY_GAME
            "UI", "2" -> SensorManager.SENSOR_DELAY_UI
            "NORMAL", "3" -> SensorManager.SENSOR_DELAY_NORMAL
            else -> pref.toIntOrNull() ?: SensorManager.SENSOR_DELAY_GAME
        }
    }

    // ---------- HTTP export ----------
    override fun snapshotJson(): String {
        val json = JSONObject()
        // clock: single monotonic base for the snapshot
        json.put("clock", JSONObject().put("t_ns", System.nanoTime()))

        lastAccel?.let {
            json.put(
                "acc",
                JSONObject().put("t_ns", it.timestamp).put("t_unix_ms", it.unixTime)
                    .put("xyz", listOf(it.values[0], it.values[1], it.values[2]))
            )
        }
        lastGyro?.let {
            json.put(
                "gyro",
                JSONObject().put("t_ns", it.timestamp).put("t_unix_ms", it.unixTime)
                    .put("xyz", listOf(it.values[0], it.values[1], it.values[2]))
            )
        }
        lastMag?.let {
            json.put(
                "mag",
                JSONObject().put("t_ns", it.timestamp).put("t_unix_ms", it.unixTime)
                    .put("xyz", listOf(it.values[0], it.values[1], it.values[2]))
            )
        }
        lastRot?.let {
            json.put(
                "rot",
                JSONObject().put("t_ns", it.timestamp).put("t_unix_ms", it.unixTime)
                    .put("xyzw", listOf(it.values[0], it.values[1], it.values[2], it.values[3]))
            )
        }
        lastGyroUnc?.let {
            val v = it.values
            if (v.size >= 6) {
                json.put(
                    "gyro_uncal",
                    JSONObject().put("t_ns", it.timestamp).put("t_unix_ms", it.unixTime)
                        .put("xyz", listOf(v[0], v[1], v[2])).put("bias", listOf(v[3], v[4], v[5]))
                )
            }
        }
        lastAccelUnc?.let {
            val v = it.values
            if (v.size >= 6) {
                json.put(
                    "acc_uncal",
                    JSONObject().put("t_ns", it.timestamp).put("t_unix_ms", it.unixTime)
                        .put("xyz", listOf(v[0], v[1], v[2])).put("bias", listOf(v[3], v[4], v[5]))
                )
            }
        }

        json.put(
            "stationary",
            JSONObject().put("value", stationary).put("samples", stillCount)
                .put("gyro_thr_rad_s", STILL_GYRO_MAX).put("acc_dev_thr_m_s2", STILL_ACC_DEV_MAX)
        )

        return json.toString()
    }

    companion object {
        var ImuHeader: String =
            "Timestamp[nanosec],gx[rad/s],gy[rad/s],gz[rad/s]," + "ax[m/s^2],ay[m/s^2],az[m/s^2],Unix time[nanosec]\n"

        private lateinit var sharedPreferences: SharedPreferences
    }
}
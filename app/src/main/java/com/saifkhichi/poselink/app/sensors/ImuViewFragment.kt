package com.saifkhichi.poselink.app.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.saifkhichi.poselink.app.ui.sensors.ImuKind
import com.saifkhichi.poselink.app.ui.sensors.ImuSensorAdapter
import com.saifkhichi.poselink.app.ui.sensors.ImuSensorItem
import com.saifkhichi.poselink.databinding.ImuListFragmentBinding
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class ImuViewFragment : Fragment(), SensorEventListener {

    private lateinit var binding: ImuListFragmentBinding
    private lateinit var sensorManager: SensorManager
    private lateinit var sensorThread: HandlerThread
    private lateinit var adapter: ImuSensorAdapter

    private var acc: Sensor? = null
    private var gyro: Sensor? = null
    private var mag: Sensor? = null
    private var rotVec: Sensor? = null
    private var gyroUnc: Sensor? = null
    private var accUnc: Sensor? = null

    // Stationarity detector (mirror IMUManager thresholds)
    private val G = 9.80665f
    private val STILL_GYRO_MAX = 0.03f
    private val STILL_ACC_DEV_MAX = 0.12f
    private val STILL_MIN_SAMPLES = 10
    private var stillCount = 0
    private var lastAcc: FloatArray? = null
    private var lastGyro: FloatArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        rotVec = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        gyroUnc = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        accUnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED) else null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ImuListFragmentBinding.inflate(inflater, container, false)

        val items = mutableListOf(
            buildItem(ImuKind.ACC, acc, "m/s²"),
            buildItem(ImuKind.GYRO, gyro, "rad/s"),
            buildItem(ImuKind.MAG, mag, "µT"),
            buildItem(ImuKind.GYRO_UNC, gyroUnc, "rad/s"),
            buildItem(ImuKind.MAG, mag, "µT"),
            buildItem(ImuKind.ROT, rotVec, "quat"),
            // Synthetic item (no underlying Sensor)
            ImuSensorItem(
                kind = ImuKind.STAT,
                available = acc != null && gyro != null,
                unit = "-",
                vendor = "Derived",
                name = "Stillness estimator"
            )
        )
        adapter = ImuSensorAdapter(items)

        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.list.itemAnimator = null
        return binding.root
    }

    private fun buildItem(kind: ImuKind, s: Sensor?, unit: String): ImuSensorItem {
        return ImuSensorItem(
            kind = kind,
            available = s != null,
            unit = unit,
            vendor = s?.vendor,
            name = s?.name,
            resolution = s?.resolution,
            maxRange = s?.maximumRange
        )
    }

    override fun onResume() {
        super.onResume()
        registerImu()
    }

    override fun onPause() {
        super.onPause()
        unregisterImu()
    }

    private fun registerImu() {
        sensorThread = HandlerThread("SensorThread", Process.THREAD_PRIORITY_MORE_FAVORABLE)
        sensorThread.start()
        val handler = Handler(sensorThread.looper)

        val rate = SensorManager.SENSOR_DELAY_UI
        acc?.let { sensorManager.registerListener(this, it, rate, handler) }
        gyro?.let { sensorManager.registerListener(this, it, rate, handler) }
        mag?.let { sensorManager.registerListener(this, it, rate, handler) }
        rotVec?.let { sensorManager.registerListener(this, it, rate, 0, handler) }
        gyroUnc?.let { sensorManager.registerListener(this, it, rate, 0, handler) }
        accUnc?.let { sensorManager.registerListener(this, it, rate, 0, handler) }
    }

    private fun unregisterImu() {
        sensorManager.unregisterListener(this)
        if (::sensorThread.isInitialized) sensorThread.quitSafely()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAcc = e.values.clone()
                postUpdate(ImuKind.ACC, e.values, e.accuracy, e.timestamp)
                postStationarity(e.timestamp)
            }

            Sensor.TYPE_GYROSCOPE -> {
                lastGyro = e.values.clone()
                postUpdate(ImuKind.GYRO, e.values, e.accuracy, e.timestamp)
                postStationarity(e.timestamp)
            }

            Sensor.TYPE_MAGNETIC_FIELD ->
                postUpdate(ImuKind.MAG, e.values, e.accuracy, e.timestamp)

            Sensor.TYPE_ROTATION_VECTOR -> {
                // Ensure quaternion xyzw (compute w if missing)
                val v = e.values
                val x = v.getOrNull(0) ?: 0f
                val y = v.getOrNull(1) ?: 0f
                val z = v.getOrNull(2) ?: 0f
                val w = v.getOrNull(3) ?: run {
                    val s = max(0f, 1f - x * x - y * y - z * z); sqrt(s)
                }
                postUpdate(ImuKind.ROT, floatArrayOf(x, y, z, w), e.accuracy, e.timestamp)
            }

            Sensor.TYPE_GYROSCOPE_UNCALIBRATED ->
                postUpdate(ImuKind.GYRO_UNC, pad6(e.values), e.accuracy, e.timestamp)

            // TYPE_ACCELEROMETER_UNCALIBRATED (35) from API 26
            35 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                postUpdate(ImuKind.ACC_UNC, pad6(e.values), e.accuracy, e.timestamp)
            }
        }
    }

    private fun postUpdate(kind: ImuKind, values: FloatArray, accuracy: Int, tNs: Long) {
        // UI update on main thread; adapter does per-row notify
        if (!isAdded) return
        requireActivity().runOnUiThread {
            adapter.update(kind, values, accuracy, tNs)
        }
    }

    private fun pad6(src: FloatArray): FloatArray {
        val out = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
        val n = minOf(6, src.size)
        for (i in 0 until n) out[i] = src[i]
        return out
    }

    private fun postStationarity(tNs: Long) {
        val g = lastGyro ?: return
        val a = lastAcc ?: return
        val gyroNorm = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        val accNorm = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        val accDev = abs(accNorm - G)
        val isStillNow = (gyroNorm < STILL_GYRO_MAX) && (accDev < STILL_ACC_DEV_MAX)
        stillCount = if (isStillNow) stillCount + 1 else max(0, stillCount - 1)
        val stationary = stillCount >= STILL_MIN_SAMPLES
        val values = floatArrayOf(gyroNorm, accDev, if (stationary) 1f else 0f, 0f, 0f, 0f)
        // reuse "accuracy" field to show samples in the UI
        postUpdate(ImuKind.STAT, values, stillCount, tNs)
    }
}
package com.saifkhichi.poselink.app.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

class ImuViewFragment : Fragment(), SensorEventListener {

    private lateinit var binding: ImuListFragmentBinding
    private lateinit var sensorManager: SensorManager
    private lateinit var sensorThread: HandlerThread
    private lateinit var adapter: ImuSensorAdapter

    private var acc: Sensor? = null
    private var gyro: Sensor? = null
    private var mag: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
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
            buildItem(ImuKind.MAG, mag, "µT")
        )
        adapter = ImuSensorAdapter(items)

        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
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
    }

    private fun unregisterImu() {
        sensorManager.unregisterListener(this)
        if (::sensorThread.isInitialized) sensorThread.quitSafely()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER ->
                postUpdate(ImuKind.ACC, e.values, e.accuracy, e.timestamp)

            Sensor.TYPE_GYROSCOPE ->
                postUpdate(ImuKind.GYRO, e.values, e.accuracy, e.timestamp)

            Sensor.TYPE_MAGNETIC_FIELD ->
                postUpdate(ImuKind.MAG, e.values, e.accuracy, e.timestamp)
        }
    }

    private fun postUpdate(kind: ImuKind, values: FloatArray, accuracy: Int, tNs: Long) {
        // UI update on main thread; adapter does per-row notify
        if (!isAdded) return
        requireActivity().runOnUiThread {
            adapter.update(kind, values, accuracy, tNs)
        }
    }

    interface OnListFragmentInteractionListener {
        fun onListFragmentInteraction(kind: ImuKind) { /* optional */
        }
    }
}
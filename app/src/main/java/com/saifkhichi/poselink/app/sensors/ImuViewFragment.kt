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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.saifkhichi.poselink.core.sensors.ImuViewContent
import com.saifkhichi.poselink.core.sensors.ImuViewContent.SingleAxis
import com.saifkhichi.poselink.databinding.ImuListFragmentBinding
import io.a3dv.VIRec.core.sensors.ImuRecyclerViewAdapter

class ImuViewFragment : Fragment(), SensorEventListener {

    private var columnCount: Int = 1
    private var listener: OnListFragmentInteractionListener? = null

    private lateinit var adapter: ImuRecyclerViewAdapter
    private lateinit var sensorManager: SensorManager
    private lateinit var sensorThread: HandlerThread

    private val preferredSensors = listOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD
    )
    private val sensorList = mutableListOf<Sensor>()

    private lateinit var binding: ImuListFragmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Safe args with default
        columnCount = arguments?.getInt(ARG_COLUMN_COUNT) ?: 1

        sensorManager = requireActivity()
            .getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Build the list of available sensors in the preferred order
        preferredSensors.forEach { type ->
            sensorManager.getDefaultSensor(type)?.let { sensorList.add(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ImuListFragmentBinding.inflate(inflater, container, false)

        val rv = binding.list
        rv.layoutManager = if (columnCount <= 1)
            LinearLayoutManager(requireContext())
        else
            GridLayoutManager(requireContext(), columnCount)

        // NOTE: ensure ImuViewContent.ITEMS size matches what adapter expects (often 9 items)
        adapter = ImuRecyclerViewAdapter(ImuViewContent.ITEMS, listener)
        rv.adapter = adapter

        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // ✅ Optional listener instead of hard crash
        listener = if (context is OnListFragmentInteractionListener) context else null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onResume() {
        super.onResume()
        registerImu()
    }

    override fun onPause() {
        super.onPause()
        unregisterImu()
    }

    interface OnListFragmentInteractionListener {
        fun onListFragmentInteraction(item: SingleAxis?)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        // Map into fixed positions: [0..2]=acc, [3..5]=gyro, [6..8]=mag
        val baseIndex = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> 0
            Sensor.TYPE_GYROSCOPE -> 3
            Sensor.TYPE_MAGNETIC_FIELD -> 6
            else -> return
        }

        // Guard: some devices may give fewer than 3 values or adapter shorter list
        val count = minOf(3, event.values.size, ImuViewContent.ITEMS.size - baseIndex)

        for (i in 0 until count) {
            adapter.updateListItem(baseIndex + i, event.values[i])
        }

        // ✅ Single UI thread hop per event
        if (isAdded) {
            requireActivity().runOnUiThread {
                // If your adapter always has 9 items, update that range; otherwise use size
                val notifyCount = minOf(9, ImuViewContent.ITEMS.size)
                adapter.notifyItemRangeChanged(0, notifyCount)
            }
        }
    }

    /** Register all IMU listeners on a background thread */
    private fun registerImu() {
        sensorThread = HandlerThread("SensorThread", Process.THREAD_PRIORITY_MORE_FAVORABLE)
        sensorThread.start()
        val handler = Handler(sensorThread.looper)

        val rate = SensorManager.SENSOR_DELAY_UI
        for (sensor in sensorList) {
            sensorManager.registerListener(this, sensor, rate, handler)
        }
    }

    /** Unregister all IMU listeners */
    private fun unregisterImu() {
        sensorManager.unregisterListener(this)
        if (::sensorThread.isInitialized) {
            sensorThread.quitSafely()
        }
    }

    companion object {
        private const val ARG_COLUMN_COUNT = "column-count"

        fun newInstance(columnCount: Int = 1): ImuViewFragment =
            ImuViewFragment().apply {
                arguments = Bundle().apply { putInt(ARG_COLUMN_COUNT, columnCount) }
            }
    }
}
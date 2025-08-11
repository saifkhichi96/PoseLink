package io.a3dv.VIRec

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
import androidx.recyclerview.widget.RecyclerView
import io.a3dv.VIRec.ImuViewContent.SingleAxis

class ImuViewFragment : Fragment(), SensorEventListener {
    private var mColumnCount = 1
    private var mListener: OnListFragmentInteractionListener? = null
    var mAdapter: ImuRecyclerViewAdapter? = null
    private var mSensorManager: SensorManager? = null
    private var mAccel: Sensor? = null
    private var mGyro: Sensor? = null
    private var mMag: Sensor? = null

    private class SensorPacket(var timestamp: Long, var values: FloatArray)

    private var mSensorThread: HandlerThread? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (arguments != null) {
            mColumnCount = arguments!!.getInt(ARG_COLUMN_COUNT)
        }

        mSensorManager = activity!!.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccel =
            mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) // warn: mAccel can be null.
        mGyro = mSensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE) // warn: mGyro can be null.
        mMag = mSensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.imu_list_fragment, container, false)
        if (view is RecyclerView) {
            val context = view.context
            val recyclerView = view
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(LinearLayoutManager(context))
            } else {
                recyclerView.setLayoutManager(GridLayoutManager(context, mColumnCount))
            }
            mAdapter = ImuRecyclerViewAdapter(ImuViewContent.ITEMS, mListener)
            recyclerView.setAdapter(mAdapter)
        }
        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnListFragmentInteractionListener) {
            mListener = context as OnListFragmentInteractionListener
        } else {
            throw RuntimeException(
                context.toString()
                        + " must implement OnListFragmentInteractionListener"
            )
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val sp = SensorPacket(event.timestamp, event.values)
            for (i in 0..2) {
                mAdapter!!.updateListItem(i, sp.values[i])
            }
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val sp = SensorPacket(event.timestamp, event.values)
            for (i in 0..2) {
                mAdapter!!.updateListItem(i + 3, sp.values[i])
            }
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val sp = SensorPacket(event.timestamp, event.values)
            for (i in 0..2) {
                mAdapter!!.updateListItem(i + 6, sp.values[i])
            }
        }

        activity!!.runOnUiThread(Runnable { mAdapter!!.notifyDataSetChanged() })
    }

    /**
     * This will register all IMU listeners
     */
    fun registerImu() {
        mSensorThread = HandlerThread(
            "Sensor thread",
            Process.THREAD_PRIORITY_MORE_FAVORABLE
        )
        mSensorThread!!.start()
        val sensorHandler = Handler(mSensorThread!!.looper)

        val mSensorRate = SensorManager.SENSOR_DELAY_UI

        mSensorManager!!.registerListener(this, mAccel, mSensorRate, sensorHandler)
        mSensorManager!!.registerListener(this, mGyro, mSensorRate, sensorHandler)
        mSensorManager!!.registerListener(this, mMag, mSensorRate, sensorHandler)
    }

    /**
     * This will unregister all IMU listeners
     */
    fun unregisterImu() {
        mSensorManager!!.unregisterListener(this, mAccel)
        mSensorManager!!.unregisterListener(this, mGyro)
        mSensorManager!!.unregisterListener(this, mMag)
        mSensorManager!!.unregisterListener(this)
        mSensorThread!!.quitSafely()
    }

    companion object {
        private const val ARG_COLUMN_COUNT = "column-count"

        @Suppress("unused")
        fun newInstance(columnCount: Int): ImuViewFragment {
            val fragment = ImuViewFragment()
            val args = Bundle()
            args.putInt(ARG_COLUMN_COUNT, columnCount)
            fragment.setArguments(args)
            return fragment
        }
    }
}

package io.a3dv.VIRec

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.preference.PreferenceManager
import timber.log.Timber
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.util.ArrayDeque
import java.util.Deque
import kotlin.concurrent.Volatile

class IMUManager(activity: Activity) : SensorEventListener {
    private class SensorPacket(// nanoseconds
        var timestamp: Long, // milliseconds
        var unixTime: Long, var values: FloatArray
    ) {
        override fun toString(): String {
            val delimiter = ","
            val sb = StringBuilder()
            sb.append(timestamp)
            for (value in values) {
                sb.append(delimiter).append(value)
            }
            sb.append(delimiter).append(unixTime).append("000000")
            return sb.toString()
        }
    }

    // Sensor listeners
    private val mSensorManager: SensorManager
    private val mAccel: Sensor?
    private val mGyro: Sensor?

    @Volatile
    private var mRecordingInertialData = false
    private var mDataWriter: BufferedWriter? = null
    private var mSensorThread: HandlerThread? = null

    private val mGyroData: Deque<SensorPacket?> = ArrayDeque<SensorPacket?>()
    private val mAccelData: Deque<SensorPacket> = ArrayDeque<SensorPacket>()

    init {
        mSensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    fun startRecording(captureResultFile: String) {
        try {
            mDataWriter = BufferedWriter(
                FileWriter(captureResultFile, false)
            )
            if (mGyro == null || mAccel == null) {
                val warning = ("The device may not have a gyroscope or an accelerometer!\n" +
                        "No IMU data will be logged.\n" +
                        "Has Gyroscope? " + (if (mGyro == null) "No" else "Yes") + "\n"
                        + "Has Accelerometer? " + (if (mAccel == null) "No" else "Yes") + "\n")
                mDataWriter!!.write(warning)
            } else {
                mDataWriter!!.write(ImuHeader)
            }
            mRecordingInertialData = true
        } catch (err: IOException) {
            Timber.e(
                err, "IOException in opening inertial data writer at %s",
                captureResultFile
            )
        }
    }

    fun stopRecording() {
        if (mRecordingInertialData) {
            mRecordingInertialData = false
            try {
                mDataWriter!!.flush()
                mDataWriter!!.close()
            } catch (err: IOException) {
                Timber.e(err, "IOException in closing inertial data writer")
            }
            mDataWriter = null
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    // sync inertial data by interpolating linear acceleration for each gyro data
    // Because the sensor events are delivered to the handler thread in order,
    // no need for synchronization here
    private fun syncInertialData(): SensorPacket? {
        if (mGyroData.size >= 1 && mAccelData.size >= 2) {
            val oldestGyro = mGyroData.peekFirst()
            val oldestAccel = mAccelData.peekFirst()
            val latestAccel = mAccelData.peekLast()

            checkNotNull(oldestGyro)
            checkNotNull(oldestAccel)
            if (oldestGyro.timestamp < oldestAccel.timestamp) {
                Timber.w("throwing one gyro data")
                mGyroData.removeFirst()
            } else {
                checkNotNull(latestAccel)
                if (oldestGyro.timestamp > latestAccel.timestamp) {
                    Timber.w("throwing #accel data %d", mAccelData.size - 1)
                    mAccelData.clear()
                    mAccelData.add(latestAccel)
                } else { // linearly interpolate the accel data at the gyro timestamp
                    val gyro_accel = FloatArray(6)
                    val sp = SensorPacket(oldestGyro.timestamp, oldestGyro.unixTime, gyro_accel)
                    gyro_accel[0] = oldestGyro.values[0]
                    gyro_accel[1] = oldestGyro.values[1]
                    gyro_accel[2] = oldestGyro.values[2]

                    var leftAccel: SensorPacket? = null
                    var rightAccel: SensorPacket? = null
                    for (packet in mAccelData) {
                        if (packet.timestamp <= oldestGyro.timestamp) {
                            leftAccel = packet
                        } else {
                            rightAccel = packet
                            break
                        }
                    }

                    // if the accelerometer data has a timestamp within the
                    // [t-x, t+x] of the gyro data at t, then the original acceleration data
                    // is used instead of linear interpolation
                    // nanoseconds
                    val mInterpolationTimeResolution: Long = 500
                    checkNotNull(leftAccel)
                    if (oldestGyro.timestamp - leftAccel.timestamp <=
                        mInterpolationTimeResolution
                    ) {
                        gyro_accel[3] = leftAccel.values[0]
                        gyro_accel[4] = leftAccel.values[1]
                        gyro_accel[5] = leftAccel.values[2]
                    } else {
                        checkNotNull(rightAccel)
                        if (rightAccel.timestamp - oldestGyro.timestamp <=
                            mInterpolationTimeResolution
                        ) {
                            gyro_accel[3] = rightAccel.values[0]
                            gyro_accel[4] = rightAccel.values[1]
                            gyro_accel[5] = rightAccel.values[2]
                        } else {
                            val tmp1 = (oldestGyro.timestamp - leftAccel.timestamp).toFloat()
                            val tmp2 = (rightAccel.timestamp - leftAccel.timestamp).toFloat()
                            val ratio = tmp1 / tmp2
                            gyro_accel[3] = leftAccel.values[0] +
                                    (rightAccel.values[0] - leftAccel.values[0]) * ratio
                            gyro_accel[4] = leftAccel.values[1] +
                                    (rightAccel.values[1] - leftAccel.values[1]) * ratio
                            gyro_accel[5] = leftAccel.values[2] +
                                    (rightAccel.values[2] - leftAccel.values[2]) * ratio
                        }
                    }

                    mGyroData.removeFirst()
                    val iterator = mAccelData.iterator()
                    while (iterator.hasNext()) {
                        val packet = iterator.next()
                        if (packet.timestamp < leftAccel.timestamp) {
                            // Remove the current element from the iterator and the list.
                            iterator.remove()
                        } else {
                            break
                        }
                    }
                    return sp
                }
            }
        }
        return null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val unixTime = System.currentTimeMillis()
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val sp = SensorPacket(event.timestamp, unixTime, event.values)
            mAccelData.add(sp)
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val sp = SensorPacket(event.timestamp, unixTime, event.values)
            mGyroData.add(sp)
            val syncedData = syncInertialData()
            if (syncedData != null && mRecordingInertialData) {
                try {
                    mDataWriter!!.write(syncedData.toString() + "\n")
                } catch (ioe: IOException) {
                    Timber.e(ioe)
                }
            }
        }
    }

    /**
     * This will register all IMU listeners
     */
    fun register() {
        mSensorThread = HandlerThread(
            "Sensor thread",
            Process.THREAD_PRIORITY_MORE_FAVORABLE
        )
        mSensorThread!!.start()
        val imuFreq: String = mSharedPreferences.getString("prefImuFreq", "1")!!
        val mSensorRate = imuFreq.toInt()
        // Blocks until looper is prepared, which is fairly quick
        val sensorHandler = Handler(mSensorThread!!.looper)
        mSensorManager.registerListener(this, mAccel, mSensorRate, sensorHandler)
        mSensorManager.registerListener(this, mGyro, mSensorRate, sensorHandler)
    }

    /**
     * This will unregister all IMU listeners
     */
    fun unregister() {
        mSensorManager.unregisterListener(this, mAccel)
        mSensorManager.unregisterListener(this, mGyro)
        mSensorManager.unregisterListener(this)
        mSensorThread!!.quitSafely()
        stopRecording()
    }

    companion object {
        var ImuHeader: String = "Timestamp[nanosec],gx[rad/s],gy[rad/s],gz[rad/s]," +
                "ax[m/s^2],ay[m/s^2],az[m/s^2],Unix time[nanosec]\n"

        private lateinit var mSharedPreferences: SharedPreferences
    }
}

package com.saifkhichi.poselink.core.sensors

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
import com.saifkhichi.poselink.streaming.SensorJsonProvider
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.util.ArrayDeque
import java.util.Deque
import kotlin.concurrent.Volatile

class IMUManager(activity: Activity) : SensorEventListener, SensorJsonProvider {

    private class SensorPacket(
        var timestamp: Long, // nanoseconds (event.timestamp)
        var unixTime: Long,  // milliseconds (System.currentTimeMillis)
        var values: FloatArray
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

    private val mSensorManager: SensorManager =
        activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mAccel: Sensor? = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val mGyro: Sensor? = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val mMag: Sensor? = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    @Volatile
    private var mRecordingInertialData = false
    private var mDataWriter: BufferedWriter? = null
    private var mSensorThread: HandlerThread? = null

    // Buffers for sync interpolation
    private val mGyroData: Deque<SensorPacket?> = ArrayDeque()
    private val mAccelData: Deque<SensorPacket> = ArrayDeque()
    private val mMagData: Deque<SensorPacket> = ArrayDeque()

    // Latest snapshots for quick JSON export
    @Volatile private var mLastAccel: SensorPacket? = null
    @Volatile private var mLastGyro: SensorPacket? = null
    @Volatile private var mLastMag: SensorPacket? = null

    init {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    fun startRecording(captureResultFile: String) {
        try {
            mDataWriter = BufferedWriter(FileWriter(captureResultFile, false))
            if (mGyro == null || mAccel == null) {
                val warning = ("The device may not have a gyroscope or an accelerometer!\n" +
                        "No IMU data will be logged.\n" +
                        "Has Gyroscope? " + (if (mGyro == null) "No" else "Yes") + "\n" +
                        "Has Accelerometer? " + (if (mAccel == null) "No" else "Yes") + "\n")
                mDataWriter!!.write(warning)
            } else {
                mDataWriter!!.write(ImuHeader)
            }
            mRecordingInertialData = true
        } catch (err: IOException) {
            Timber.e(err, "IOException in opening inertial data writer at %s", captureResultFile)
        }
    }

    fun stopRecording() {
        if (mRecordingInertialData) {
            mRecordingInertialData = false
            try {
                mDataWriter?.flush()
                mDataWriter?.close()
            } catch (err: IOException) {
                Timber.e(err, "IOException in closing inertial data writer")
            }
            mDataWriter = null
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun syncInertialData(): SensorPacket? {
        if (mGyroData.isNotEmpty() && mAccelData.size >= 2) {
            val oldestGyro = mGyroData.peekFirst() ?: return null
            val oldestAccel = mAccelData.peekFirst() ?: return null
            if (oldestGyro.timestamp < oldestAccel.timestamp) {
                Timber.w("throwing one gyro data")
                mGyroData.removeFirst()
            } else {
                val latestAccel = mAccelData.peekLast() ?: return null
                if (oldestGyro.timestamp > latestAccel.timestamp) {
                    Timber.w("throwing #accel data %d", mAccelData.size - 1)
                    mAccelData.clear()
                    mAccelData.add(latestAccel)
                } else {
                    val gyro_accel = FloatArray(6)
                    val sp = SensorPacket(oldestGyro.timestamp, oldestGyro.unixTime, gyro_accel)
                    gyro_accel[0] = oldestGyro.values[0]
                    gyro_accel[1] = oldestGyro.values[1]
                    gyro_accel[2] = oldestGyro.values[2]

                    var leftAccel: SensorPacket? = null
                    var rightAccel: SensorPacket? = null
                    for (packet in mAccelData) {
                        if (packet.timestamp <= oldestGyro.timestamp) leftAccel = packet
                        else {
                            rightAccel = packet
                            break
                        }
                    }

                    val mInterpolationTimeResolution: Long = 500
                    checkNotNull(leftAccel)
                    if (oldestGyro.timestamp - leftAccel.timestamp <= mInterpolationTimeResolution) {
                        gyro_accel[3] = leftAccel.values[0]
                        gyro_accel[4] = leftAccel.values[1]
                        gyro_accel[5] = leftAccel.values[2]
                    } else {
                        checkNotNull(rightAccel)
                        if (rightAccel.timestamp - oldestGyro.timestamp <= mInterpolationTimeResolution) {
                            gyro_accel[3] = rightAccel.values[0]
                            gyro_accel[4] = rightAccel.values[1]
                            gyro_accel[5] = rightAccel.values[2]
                        } else {
                            val tmp1 = (oldestGyro.timestamp - leftAccel.timestamp).toFloat()
                            val tmp2 = (rightAccel.timestamp - leftAccel.timestamp).toFloat()
                            val ratio = tmp1 / tmp2
                            gyro_accel[3] = leftAccel.values[0] + (rightAccel.values[0] - leftAccel.values[0]) * ratio
                            gyro_accel[4] = leftAccel.values[1] + (rightAccel.values[1] - leftAccel.values[1]) * ratio
                            gyro_accel[5] = leftAccel.values[2] + (rightAccel.values[2] - leftAccel.values[2]) * ratio
                        }
                    }

                    mGyroData.removeFirst()
                    val iterator = mAccelData.iterator()
                    while (iterator.hasNext()) {
                        val packet = iterator.next()
                        if (packet.timestamp < leftAccel.timestamp) iterator.remove()
                        else break
                    }
                    return sp
                }
            }
        }
        return null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val unixTime = System.currentTimeMillis()
        val sp = SensorPacket(event.timestamp, unixTime, event.values.clone())

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                mAccelData.add(sp)
                mLastAccel = sp
            }
            Sensor.TYPE_GYROSCOPE -> {
                mGyroData.add(sp)
                mLastGyro = sp
                val syncedData = syncInertialData()
                if (syncedData != null && mRecordingInertialData) {
                    try {
                        mDataWriter?.write(syncedData.toString() + "\n")
                    } catch (ioe: IOException) {
                        Timber.e(ioe)
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                mMagData.add(sp)
                mLastMag = sp
            }
        }
    }

    fun register() {
        mSensorThread = HandlerThread("Sensor thread", Process.THREAD_PRIORITY_MORE_FAVORABLE)
        mSensorThread!!.start()
        val imuFreq = mSharedPreferences.getString("prefImuFreq", "1")!!.toInt()
        val sensorHandler = Handler(mSensorThread!!.looper)
        mAccel?.let { mSensorManager.registerListener(this, it, imuFreq, sensorHandler) }
        mGyro?.let { mSensorManager.registerListener(this, it, imuFreq, sensorHandler) }
        mMag?.let { mSensorManager.registerListener(this, it, imuFreq, sensorHandler) }
    }

    fun unregister() {
        mSensorManager.unregisterListener(this)
        mSensorThread?.quitSafely()
        stopRecording()
    }

    override fun snapshotJson(): String {
        val json = JSONObject()
        mLastAccel?.let {
            json.put("acc", JSONObject()
                .put("t_ns", it.timestamp)
                .put("t_unix_ms", it.unixTime)
                .put("xyz", listOf(it.values[0], it.values[1], it.values[2]))
            )
        }
        mLastGyro?.let {
            json.put("gyro", JSONObject()
                .put("t_ns", it.timestamp)
                .put("t_unix_ms", it.unixTime)
                .put("xyz", listOf(it.values[0], it.values[1], it.values[2]))
            )
        }
        mLastMag?.let {
            json.put("mag", JSONObject()
                .put("t_ns", it.timestamp)
                .put("t_unix_ms", it.unixTime)
                .put("xyz", listOf(it.values[0], it.values[1], it.values[2]))
            )
        }
        return json.toString()
    }

    companion object {
        var ImuHeader: String = "Timestamp[nanosec],gx[rad/s],gy[rad/s],gz[rad/s]," +
                "ax[m/s^2],ay[m/s^2],az[m/s^2],Unix time[nanosec]\n"

        private lateinit var mSharedPreferences: SharedPreferences
    }
}
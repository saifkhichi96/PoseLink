package com.saifkhichi.poselink.sync

import android.hardware.camera2.CameraCharacteristics
import android.os.SystemClock
import com.saifkhichi.poselink.storage.FileHelper
import timber.log.Timber
import java.io.BufferedWriter
import java.io.IOException

class TimeBaseManager {
    var mTimeBaseHint: String? = null
    private var mDataWriter: BufferedWriter? = null

    /** Monotonic phone time in ns (same base used in /clock and headers). */
    fun clockNowNs(): Long = System.nanoTime()

    fun startRecording(captureResultFile: String, timeSourceValue: Int) {
        mDataWriter = FileHelper.createBufferedWriter(captureResultFile)
        val sysElapsedNs = SystemClock.elapsedRealtimeNanos()
        val sysNs = System.nanoTime()
        val diff = sysElapsedNs - sysNs
        setCameraTimestampSource(timeSourceValue)
        try {
            mDataWriter!!.write(mTimeBaseHint + "\n")
            mDataWriter!!.write("#IMU data clock\tSENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN camera clock\tDifference\n")
            mDataWriter!!.write("#elapsedRealtimeNanos()\tnanoTime()\tDifference\n")
            mDataWriter!!.write("$sysElapsedNs\t$sysNs\t$diff\n")
        } catch (ioe: IOException) {
            Timber.Forest.e(ioe)
        }
    }

    fun stopRecording() {
        val sysElapsedNs = SystemClock.elapsedRealtimeNanos()
        val sysNs = System.nanoTime()
        val diff = sysElapsedNs - sysNs
        try {
            mDataWriter!!.write("$sysElapsedNs\t$sysNs\t$diff\n")
        } catch (ioe: IOException) {
            Timber.Forest.e(ioe)
        }
        mDataWriter?.let { FileHelper.closeBufferedWriter(it) }
        mDataWriter = null
    }

    private fun createHeader(timestampSource: String?) {
        mTimeBaseHint =
            "#Camera frame timestamp source according to CameraCharacteristics.SENSOR_INFO_" +
                    "TIMESTAMP_SOURCE is $timestampSource.\n#" +
                    "If SENSOR_INFO_TIMESTAMP_SOURCE is SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME, then camera " +
                    "frame timestamps (CaptureResult.SENSOR_TIMESTAMP) and IMU SensorEvent.timestamp share CLOCK_BOOTTIME " +
                    "(elapsedRealtimeNanos()). No offline sync is necessary.\n#" +
                    "Otherwise, camera frames typically use CLOCK_MONOTONIC (nanoTime()); offline sync may be needed " +
                    "unless the delta is negligible.\n#" +
                    "Start/end snapshots of both clocks are recorded below."
    }

    private fun setCameraTimestampSource(timestampSource: Int?) {
        var srcType = "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN"
        if (timestampSource != null) {
            srcType = when (timestampSource) {
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME ->
                    "SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME"

                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> {
                    Timber.Forest.d(
                        "Camera timestamp source unreliable for IMU sync: %s",
                        "UNKNOWN"
                    )
                    "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN"
                }

                else -> {
                    Timber.Forest.d(
                        "Camera timestamp source unreliable for IMU sync: %s",
                        timestampSource
                    )
                    "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN ($timestampSource)"
                }
            }
        }
        createHeader(srcType)
    }
}
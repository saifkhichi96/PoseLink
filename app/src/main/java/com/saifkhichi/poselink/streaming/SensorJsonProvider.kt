package com.saifkhichi.poselink.streaming

interface SensorJsonProvider {

    /** Sensor calibration data as a JSON string. */
    fun calibrationJson(): String

    /** Latest raw sensor state as a JSON string. */
    fun snapshotJson(): String

    /** Latest calibrated sensor state as a JSON string. */
    fun snapshotJsonCalibrated(): String

}
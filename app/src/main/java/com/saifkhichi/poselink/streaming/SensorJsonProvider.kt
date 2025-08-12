package com.saifkhichi.poselink.streaming

interface SensorJsonProvider {
    /** A compact JSON string of latest sensor state (thread-safe). */
    fun snapshotJson(): String
}
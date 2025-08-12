package com.saifkhichi.poselink.core.camera

object DesiredCameraSetting {
    const val mDesiredFrameWidth: Int = 1280
    const val mDesiredFrameHeight: Int = 720
    const val mDesiredExposureTime: Long = 5000000L // nanoseconds
    val mDesiredFrameSize: String = mDesiredFrameWidth.toString() + "x" + mDesiredFrameHeight
}
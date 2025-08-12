package com.saifkhichi.poselink.core.camera

class ManualFocusConfig(
    val mEventX: Float,
    val mEventY: Float,
    val mViewWidth: Int,
    val mViewHeight: Int
) {
    override fun toString(): String {
        return "ManualFocusConfig: " + mViewWidth + "x" + mViewHeight + " @ " + mEventX +
                "," + mEventY
    }
}
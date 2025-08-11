package io.a3dv.VIRec

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

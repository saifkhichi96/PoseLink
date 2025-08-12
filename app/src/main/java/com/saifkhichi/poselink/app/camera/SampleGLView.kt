package com.saifkhichi.poselink.app.camera

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener

class SampleGLView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) :
    GLSurfaceView(context, attrs), OnTouchListener {
    private var touchListener: ((MotionEvent?, Int, Int) -> Unit)? = null

    init {
        setOnTouchListener(this)
    }

    fun setTouchListener(listener: (MotionEvent?, Int, Int) -> Unit) {
        this.touchListener = listener
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val actionMasked = event.actionMasked
        if (actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }

        touchListener?.invoke(event, width, height)
        return true
    }
}

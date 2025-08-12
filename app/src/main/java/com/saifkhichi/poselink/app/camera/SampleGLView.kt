package com.saifkhichi.poselink.app.camera

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SampleGLView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) :
    GLSurfaceView(context, attrs), OnTouchListener {
    private var touchListener: ((MotionEvent?, Int, Int) -> Unit)? = null

    interface Renderer : GLSurfaceView.Renderer {
        override fun onDrawFrame(gl: GL10?)

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int)

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?)
    }

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

    companion object {
        const val RENDERMODE_WHEN_DIRTY = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }
}

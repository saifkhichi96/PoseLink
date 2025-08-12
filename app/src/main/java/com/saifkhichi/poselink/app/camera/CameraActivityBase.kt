package com.saifkhichi.poselink.app.camera

import android.app.Activity
import android.graphics.SurfaceTexture
import android.util.Size
import android.view.WindowManager
import android.widget.TextView
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.core.camera.Camera2Proxy
import com.saifkhichi.poselink.core.camera.TextureMovieEncoder
import timber.log.Timber
import java.util.Locale

open class CameraActivityBase : Activity(), SurfaceTexture.OnFrameAvailableListener {
    protected var mCameraPreviewWidth: Int = 0
    protected var mCameraPreviewHeight: Int = 0
    protected var mOutputDirText: TextView? = null

    // Primary camera properties
    protected var mPrimaryCameraProxy: Camera2Proxy? = null
    protected var mPrimaryGLView: SampleGLView? = null
    protected var mPrimaryVideoEncoder: TextureMovieEncoder = TextureMovieEncoder()
    protected var mPrimaryFrameWidth: Int = 0
    protected var mPrimaryFrameHeight: Int = 0
    protected var mPrimaryCameraParamsView: TextView? = null
    protected var mPrimaryCaptureResultView: TextView? = null

    // Secondary camera properties
    protected var mSecondaryCameraProxy: Camera2Proxy? = null
    protected var mSecondaryGLView: SampleGLView? = null
    protected var mSecondaryVideoEncoder: TextureMovieEncoder = TextureMovieEncoder()
    protected var mSecondaryFrameWidth: Int = 0
    protected var mSecondaryFrameHeight: Int = 0
    protected var mSecondaryCameraParamsView: TextView? = null
    protected var mSecondaryCaptureResultView: TextView? = null

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    fun handleSetSurfaceTexture(st: SurfaceTexture) {
        st.setOnFrameAvailableListener(this)

        if (mPrimaryCameraProxy != null) {
            mPrimaryCameraProxy!!.setPreviewSurfaceTexture(st)
            mPrimaryCameraProxy!!.openCamera()
        } else {
            throw RuntimeException(
                "Try to set surface texture while camera2proxy is null"
            )
        }
    }

    fun handleSetSurfaceTexture2(st: SurfaceTexture) {
        st.setOnFrameAvailableListener { surfaceTexture: SurfaceTexture? ->
            if (VERBOSE) Timber.Forest.d("ST onFrameAvailable")
            mSecondaryGLView!!.requestRender()

            val sFps = String.Companion.format(
                Locale.getDefault(), "%.1f FPS",
                mPrimaryVideoEncoder.mFrameRate
            )
            val previewFacts =
                "[2] " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + "@" + sFps
            mSecondaryCameraParamsView!!.text = previewFacts
        }

        if (mSecondaryCameraProxy != null) {
            mSecondaryCameraProxy!!.setPreviewSurfaceTexture(st)
            mSecondaryCameraProxy!!.openCamera()
        } else {
            throw RuntimeException(
                "Try to set surface texture while camera2proxy is null"
            )
        }
    }

    fun getCameraProxy(): Camera2Proxy {
        if (mPrimaryCameraProxy == null) {
            throw RuntimeException("Primary camera proxy is null")
        }
        return mPrimaryCameraProxy!!
    }

    // updates mCameraPreviewWidth/Height
    protected fun setLayoutAspectRatio(cameraPreviewSize: Size) {
        val layout = findViewById<AspectFrameLayout>(R.id.cameraPreview_afl)
        val display = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
        mCameraPreviewWidth = cameraPreviewSize.width
        mCameraPreviewHeight = cameraPreviewSize.height
        when (display.rotation) {
            display.rotation -> {
                layout.setAspectRatio(mCameraPreviewHeight.toDouble() / mCameraPreviewWidth)
            }

            display.rotation -> {
                layout.setAspectRatio(mCameraPreviewHeight.toDouble() / mCameraPreviewWidth)
            }

            else -> {
                layout.setAspectRatio(mCameraPreviewWidth.toDouble() / mCameraPreviewHeight)
            }
        }
    }

    fun updateCaptureResultPanel(
        fl: Float,
        exposureTimeNs: Long?, afMode: Int, secondCamera: Boolean
    ) {
        val sfl = String.Companion.format(Locale.getDefault(), "%.3f", fl)
        val sExpoTime =
            if (exposureTimeNs == null) "null ms" else String.Companion.format(
                Locale.getDefault(), "%.2f ms",
                exposureTimeNs / 1000000.0
            )

        val saf = "AF Mode: $afMode"

        if (secondCamera) {
            runOnUiThread {
                mSecondaryCaptureResultView!!.text = "$sfl $sExpoTime $saf"
            }
        } else {
            runOnUiThread {
                mPrimaryCaptureResultView!!.text = "$sfl $sExpoTime $saf"
            }
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        // The SurfaceTexture uses this to signal the availability of a new frame.  The
        // thread that "owns" the external texture associated with the SurfaceTexture (which,
        // by virtue of the context being shared, *should* be either one) needs to call
        // updateTexImage() to latch the buffer.
        //
        // Once the buffer is latched, the GLSurfaceView thread can signal the encoder thread.
        // This feels backward -- we want recording to be prioritized over rendering -- but
        // since recording is only enabled some of the time it's easier to do it this way.
        //
        // Since GLSurfaceView doesn't establish a Looper, this will *probably* execute on
        // the main UI thread.  Fortunately, requestRender() can be called from any thread,
        // so it doesn't really matter.
        if (VERBOSE) Timber.Forest.d("ST onFrameAvailable")
        mPrimaryGLView!!.requestRender()

        val sFps = String.Companion.format(
            Locale.getDefault(), "%.1f FPS",
            mPrimaryVideoEncoder.mFrameRate
        )
        val previewFacts = "[1] " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + "@" + sFps

        mPrimaryCameraParamsView!!.text = previewFacts
    }

    companion object {
        protected const val VERBOSE: Boolean = false

        // Camera filters; must match up with cameraFilterNames in strings.xml
        const val FILTER_NONE: Int = 0
        const val FILTER_BLACK_WHITE: Int = 1
        const val FILTER_BLUR: Int = 2
        const val FILTER_SHARPEN: Int = 3
        const val FILTER_EDGE_DETECT: Int = 4
        const val FILTER_EMBOSS: Int = 5
    }
}
package io.a3dv.VIRec

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.util.Size
import android.view.Gravity
import android.view.MenuItem
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.RequiresApi
import io.a3dv.VIRec.TextureMovieEncoder.EncoderConfig
import io.a3dv.VIRec.gles.FullFrameRect
import io.a3dv.VIRec.gles.Texture2dProgram
import io.a3dv.VIRec.gles.Texture2dProgram.ProgramType
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal object DesiredCameraSetting {
    const val mDesiredFrameWidth: Int = 1280
    const val mDesiredFrameHeight: Int = 720
    const val mDesiredExposureTime: Long = 5000000L // nanoseconds
    val mDesiredFrameSize: String = mDesiredFrameWidth.toString() + "x" + mDesiredFrameHeight
}

open class CameraActivityBase : Activity(), OnFrameAvailableListener {
    protected var mKeyCameraParamsText: TextView? = null
    protected var mKeyCameraParamsText2: TextView? = null
    protected var mCaptureResultText: TextView? = null
    protected var mCaptureResultText2: TextView? = null

    protected var mCameraPreviewWidth: Int = 0
    protected var mCameraPreviewHeight: Int = 0
    protected var mVideoFrameWidth: Int = 0
    protected var mVideoFrameHeight: Int = 0
    protected var mVideoFrameWidth2: Int = 0
    protected var mVideoFrameHeight2: Int = 0
    protected var mCamera2Proxy: Camera2Proxy? = null
    protected var mCamera2Proxy2: Camera2Proxy? = null

    protected var mGLView: SampleGLView? = null
    protected var mGLView2: SampleGLView? = null
    protected var sVideoEncoder: TextureMovieEncoder = TextureMovieEncoder()
    protected var sVideoEncoder2: TextureMovieEncoder = TextureMovieEncoder()

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    fun handleSetSurfaceTexture(st: SurfaceTexture) {
        st.setOnFrameAvailableListener(this)

        if (mCamera2Proxy != null) {
            mCamera2Proxy!!.setPreviewSurfaceTexture(st)
            mCamera2Proxy!!.openCamera()
        } else {
            throw RuntimeException(
                "Try to set surface texture while camera2proxy is null"
            )
        }
    }

    fun handleSetSurfaceTexture2(st: SurfaceTexture) {
        st.setOnFrameAvailableListener { surfaceTexture: SurfaceTexture? ->
            if (VERBOSE) Timber.d("ST onFrameAvailable")
            mGLView2!!.requestRender()

            val sFps = String.format(
                Locale.getDefault(), "%.1f FPS",
                sVideoEncoder.mFrameRate
            )
            val previewFacts =
                "[2] " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + "@" + sFps
            mKeyCameraParamsText2!!.text = previewFacts
        }

        if (mCamera2Proxy2 != null) {
            mCamera2Proxy2!!.setPreviewSurfaceTexture(st)
            mCamera2Proxy2!!.openCamera()
        } else {
            throw RuntimeException(
                "Try to set surface texture while camera2proxy is null"
            )
        }
    }

    fun getmCamera2Proxy(): Camera2Proxy {
        if (mCamera2Proxy == null) {
            throw RuntimeException("Get a null Camera2Proxy")
        }
        return mCamera2Proxy!!
    }

    protected fun renewOutputDir(): String {
        val dateFormat = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
        val folderName = dateFormat.format(Date())

        val dataDir = getExternalFilesDir(
            Environment.getDataDirectory().absolutePath
        )!!.absolutePath
        val outputDir = buildString {
            append(dataDir)
            append(File.separator)
            append(folderName)
        }

        (File(outputDir)).mkdirs()
        return outputDir
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
        val sfl = String.format(Locale.getDefault(), "%.3f", fl)
        val sExpoTime =
            if (exposureTimeNs == null) "null ms" else String.format(
                Locale.getDefault(), "%.2f ms",
                exposureTimeNs / 1000000.0
            )

        val saf = "AF Mode: $afMode"

        if (secondCamera) {
            runOnUiThread {
                mCaptureResultText2!!.text = "$sfl $sExpoTime $saf"
            }
        } else {
            runOnUiThread {
                mCaptureResultText!!.text = "$sfl $sExpoTime $saf"
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
        if (VERBOSE) Timber.d("ST onFrameAvailable")
        mGLView!!.requestRender()

        val sFps = String.format(
            Locale.getDefault(), "%.1f FPS",
            sVideoEncoder.mFrameRate
        )
        val previewFacts = "[1] " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + "@" + sFps

        mKeyCameraParamsText!!.text = previewFacts
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

class CameraActivity : CameraActivityBase(), PopupMenu.OnMenuItemClickListener {
    private var mRenderer: CameraSurfaceRenderer? = null
    private var mRenderer2: CameraSurfaceRenderer? = null
    private lateinit var mOutputDirText: TextView

    private var mCameraHandler: CameraHandler? = null
    private var mCameraHandler2: CameraHandler? = null
    private var mRecordingEnabled = false // controls button state

    private var mImuManager: IMUManager? = null
    private var mGpsManager: GPSManager? = null
    private var mTimeBaseManager: TimeBaseManager? = null

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        setContentView(R.layout.camera_activity)
        val spinner = findViewById<Spinner>(R.id.cameraFilter_spinner)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.cameraFilterNames, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Apply the adapter to the spinner.
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val spinner = parent as Spinner
                val filterNum = spinner.selectedItemPosition
                val textView = parent.getChildAt(0) as TextView
                textView.setTextColor(-0x1)
                textView.gravity = Gravity.CENTER

                mGLView!!.queueEvent {
                    // notify the renderer that we want to change the encoder's state
                    mRenderer!!.changeFilterMode(filterNum)
                }

                mGLView2!!.queueEvent {
                    // notify the renderer that we want to change the encoder's state
                    mRenderer2!!.changeFilterMode(filterNum)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mCamera2Proxy = Camera2Proxy(this, false)
        mCamera2Proxy2 = Camera2Proxy(this, true)
        val previewSize = mCamera2Proxy!!.configureCamera()
        mCamera2Proxy2!!.configureCamera()
        setLayoutAspectRatio(previewSize) // updates mCameraPreviewWidth/Height
        val videoSize = mCamera2Proxy!!.getmVideoSize()
        mVideoFrameWidth = videoSize.width
        mVideoFrameHeight = videoSize.height

        val videoSize2 = mCamera2Proxy2!!.getmVideoSize()
        mVideoFrameWidth2 = videoSize2.width
        mVideoFrameHeight2 = videoSize2.height
        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = CameraHandler(this)
        mCameraHandler2 = CameraHandler(this)

        mRecordingEnabled = sVideoEncoder.isRecording

        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        mGLView = findViewById(R.id.cameraPreview_surfaceView)
        mGLView2 = findViewById(R.id.cameraPreview_surfaceView2)

        if (mRenderer == null) {
            mRenderer = CameraSurfaceRenderer(mCameraHandler!!, sVideoEncoder, 0)
            mGLView!!.setEGLContextClientVersion(2) // select GLES 2.0
            mGLView!!.setRenderer(mRenderer)
            mGLView!!.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        if (mRenderer2 == null) {
            mRenderer2 = CameraSurfaceRenderer(mCameraHandler2!!, sVideoEncoder2, 1)
            mGLView2!!.setEGLContextClientVersion(2) // select GLES 2.0
            mGLView2!!.setRenderer(mRenderer2)
            mGLView2!!.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        mGLView!!.setTouchListener({ event: MotionEvent?, width: Int, height: Int ->
            val focusConfig =
                ManualFocusConfig(event!!.x, event.y, width, height)
            Timber.d(focusConfig.toString())
            mCameraHandler!!.sendMessage(
                mCameraHandler!!.obtainMessage(
                    CameraHandler.Companion.MSG_MANUAL_FOCUS,
                    focusConfig
                )
            )
        })

        if (mImuManager == null) {
            mImuManager = IMUManager(this)
            mTimeBaseManager = TimeBaseManager()
        }

        if (mGpsManager == null) {
            mGpsManager = GPSManager(this)
            mTimeBaseManager = TimeBaseManager()
        }

        mKeyCameraParamsText = findViewById(R.id.cameraParams_text)
        mKeyCameraParamsText2 = findViewById(R.id.cameraParams_text2)
        mCaptureResultText = findViewById(R.id.captureResult_text)
        mCaptureResultText2 = findViewById(R.id.captureResult_text2)
        mOutputDirText = findViewById(R.id.cameraOutputDir_text)
    }

    override fun onResume() {
        Timber.d("onResume -- acquiring camera")
        super.onResume()
        Timber.d("Keeping screen on for previewing recording.")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateControls()

        if (mCamera2Proxy == null) {
            mCamera2Proxy = Camera2Proxy(this, false)
            val previewSize = mCamera2Proxy!!.configureCamera()
            setLayoutAspectRatio(previewSize)
            val videoSize = mCamera2Proxy!!.getmVideoSize()
            mVideoFrameWidth = videoSize.width
            mVideoFrameHeight = videoSize.height
        }

        if (mCamera2Proxy2 == null) {
            mCamera2Proxy2 = Camera2Proxy(this, true)
            mCamera2Proxy2!!.configureCamera()
            val videoSize = mCamera2Proxy2!!.getmVideoSize()
            mVideoFrameWidth2 = videoSize.width
            mVideoFrameHeight2 = videoSize.height
        }

        mGLView!!.onResume()
        mGLView!!.queueEvent {
            mRenderer!!.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight)
            mRenderer!!.setVideoFrameSize(mVideoFrameWidth, mVideoFrameHeight)
        }

        mGLView2!!.onResume()
        mGLView2!!.queueEvent {
            mRenderer2!!.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight)
            mRenderer2!!.setVideoFrameSize(mVideoFrameWidth2, mVideoFrameHeight2)
        }

        mImuManager!!.register()
        mGpsManager!!.register()
    }

    override fun onPause() {
        Timber.d("onPause -- releasing camera")
        super.onPause()
        // no more frame metadata will be saved during pause
        if (mCamera2Proxy != null) {
            mCamera2Proxy!!.releaseCamera()
            mCamera2Proxy = null
        }

        if (mCamera2Proxy2 != null) {
            mCamera2Proxy2!!.releaseCamera()
            mCamera2Proxy2 = null
        }

        mGLView!!.queueEvent {
            // Tell the renderer that it's about to be paused so it can clean up.
            mRenderer!!.notifyPausing()
        }
        mGLView!!.onPause()

        mGLView2!!.queueEvent {
            // Tell the renderer that it's about to be paused so it can clean up.
            mRenderer2!!.notifyPausing()
        }
        mGLView2!!.onPause()

        mImuManager!!.unregister()
        mGpsManager!!.unregister()
        Timber.d("onPause complete")
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        super.onDestroy()
        mCameraHandler!!.invalidateHandler()
        mCameraHandler2!!.invalidateHandler()
    }


    fun clickToggleRecording(@Suppress("unused") unused: View?) {
        mRecordingEnabled = !mRecordingEnabled
        if (mRecordingEnabled) {
            val outputDir = renewOutputDir()
            val outputFile = outputDir + File.separator + "movie.mp4"
            val outputFile2 = outputDir + File.separator + "movie2.mp4"
            val metaFile = outputDir + File.separator + "frame_timestamps.txt"
            val metaFile2 = outputDir + File.separator + "frame_timestamps2.txt"

            val basename = outputDir.substring(outputDir.lastIndexOf("/") + 1)
            mOutputDirText.text = basename
            mRenderer!!.resetOutputFiles(outputFile, metaFile) // this will not cause sync issues
            mRenderer2!!.resetOutputFiles(outputFile2, metaFile2)

            val inertialFile = outputDir + File.separator + "gyro_accel.csv"
            val locationFile = outputDir + File.separator + "location.csv"
            val edgeEpochFile = outputDir + File.separator + "edge_epochs.txt"

            mCamera2Proxy?.getmTimeSourceValue()?.let {
                mTimeBaseManager?.startRecording(edgeEpochFile, it)
            }
            mImuManager!!.startRecording(inertialFile)
            mGpsManager!!.startRecording(locationFile)
            mCamera2Proxy!!.startRecordingCaptureResult(
                outputDir + File.separator + "movie_metadata.csv"
            )
            mCamera2Proxy2!!.startRecordingCaptureResult(
                outputDir + File.separator + "movie_metadata2.csv"
            )
        } else {
            mCamera2Proxy!!.stopRecordingCaptureResult()
            mCamera2Proxy2!!.stopRecordingCaptureResult()
            mImuManager!!.stopRecording()
            mGpsManager!!.stopRecording()
            mTimeBaseManager!!.stopRecording()
        }

        mGLView!!.queueEvent {
            // notify the renderer that we want to change the encoder's state
            mRenderer!!.changeRecordingState(mRecordingEnabled)
        }

        mGLView2!!.queueEvent {
            // notify the renderer that we want to change the encoder's state
            mRenderer2!!.changeRecordingState(mRecordingEnabled)
        }

        updateControls()
    }

    private fun updateControls() {
        val toggleRecordingButton = findViewById<ImageButton>(R.id.toggleRecordingButton)
        toggleRecordingButton.contentDescription = if (mRecordingEnabled)
            getString(R.string.stop)
        else
            getString(R.string.record)
        toggleRecordingButton.setImageResource(
            if (mRecordingEnabled)
                R.drawable.ic_baseline_stop_24
            else
                R.drawable.ic_baseline_fiber_manual_record_24
        )

        val filterSpinner = findViewById<Spinner>(R.id.cameraFilter_spinner)
        filterSpinner.visibility = if (mRecordingEnabled) View.INVISIBLE else View.VISIBLE
    }

    fun clickShowPopupMenu(v: View?) {
        val popup = PopupMenu(applicationContext, v)
        popup.setOnMenuItemClickListener(this)
        popup.inflate(R.menu.popup_menu)
        popup.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_settings) {
            val toSettings = Intent(this, SettingsActivity::class.java)
            startActivity(toSettings)
        } else if (item.itemId == R.id.menu_imu) {
            val toImuViewer = Intent(this, ImuViewerActivity::class.java)
            startActivity(toImuViewer)
        } else if (item.itemId == R.id.menu_about) {
            val toAbout = Intent(this, AboutActivity::class.java)
            startActivity(toAbout)
        }

        return false
    }
}


internal class CameraHandler(activity: Activity?) : Handler() {
    // Weak reference to the Activity; only access this from the UI thread.
    private val mWeakActivity: WeakReference<Activity?> = WeakReference<Activity?>(activity)

    /**
     * Drop the reference to the activity. Useful as a paranoid measure to ensure that
     * attempts to access a stale Activity through a handler are caught.
     */
    fun invalidateHandler() {
        mWeakActivity.clear()
    }


    // runs on UI thread
    override fun handleMessage(inputMessage: Message) {
        val what = inputMessage.what
        val obj = inputMessage.obj

        Timber.d("CameraHandler [%s]: what=%d", this.toString(), what)

        val activity = mWeakActivity.get()
        if (activity == null) {
            Timber.w("CameraHandler.handleMessage: activity is null")
            return
        }

        when (what) {
            MSG_SET_SURFACE_TEXTURE -> (activity as CameraActivityBase).handleSetSurfaceTexture(
                (inputMessage.obj as SurfaceTexture?)!!
            )

            MSG_SET_SURFACE_TEXTURE2 -> (activity as CameraActivityBase).handleSetSurfaceTexture2(
                (inputMessage.obj as SurfaceTexture?)!!
            )

            MSG_MANUAL_FOCUS -> {
                val camera2proxy = (activity as CameraActivityBase).getmCamera2Proxy()
                (obj as ManualFocusConfig?)?.let { camera2proxy.changeManualFocusPoint(it) }
            }

            else -> throw RuntimeException("unknown msg " + what)
        }
    }

    companion object {
        const val MSG_SET_SURFACE_TEXTURE: Int = 0
        const val MSG_SET_SURFACE_TEXTURE2: Int = 2
        const val MSG_MANUAL_FOCUS: Int = 1
    }
}

internal class CameraSurfaceRenderer(
    private val mCameraHandler: CameraHandler,
    private val mVideoEncoder: TextureMovieEncoder, cameraId: Int
) : GLSurfaceView.Renderer {
    private var mOutputFile: String? = null
    private var mMetadataFile: String? = null

    private var mFullScreen: FullFrameRect? = null

    private val mSTMatrix = FloatArray(16)
    private var mTextureId: Int

    private var mSurfaceTexture: SurfaceTexture? = null
    private var mRecordingEnabled = false
    private var mRecordingStatus: Int
    private var mFrameCount: Int

    // width/height of the incoming camera preview frames
    private var mIncomingSizeUpdated = false
    private var mIncomingWidth: Int
    private var mIncomingHeight: Int

    private var mVideoFrameWidth: Int
    private var mVideoFrameHeight: Int

    private var mCurrentFilter: Int
    private var mNewFilter: Int

    private val mCameraId: Int

    init {
        mTextureId = -1

        mRecordingStatus = -1
        mFrameCount = -1

        mIncomingHeight = -1
        mIncomingWidth = mIncomingHeight
        mVideoFrameHeight = -1
        mVideoFrameWidth = mVideoFrameHeight

        mCurrentFilter = -1
        mNewFilter = CameraActivityBase.Companion.FILTER_NONE

        mCameraId = cameraId
    }

    fun resetOutputFiles(outputFile: String, metaFile: String?) {
        mOutputFile = outputFile
        mMetadataFile = metaFile
    }

    /**
     * Notifies the renderer thread that the activity is pausing.
     *
     *
     * For best results, call this *after* disabling Camera preview.
     */
    fun notifyPausing() {
        if (mSurfaceTexture != null) {
            Timber.d("renderer pausing -- releasing SurfaceTexture")
            mSurfaceTexture!!.release()
            mSurfaceTexture = null
        }
        if (mFullScreen != null) {
            mFullScreen!!.release(false) // assume the GLSurfaceView EGL context is about
            mFullScreen = null //  to be destroyed
        }
        mIncomingHeight = -1
        mIncomingWidth = mIncomingHeight
        mVideoFrameHeight = -1
        mVideoFrameWidth = mVideoFrameHeight
    }

    /**
     * Notifies the renderer that we want to stop or start recording.
     */
    fun changeRecordingState(isRecording: Boolean) {
        Timber.d("changeRecordingState: was %b now %b", mRecordingEnabled, isRecording)
        mRecordingEnabled = isRecording
    }

    /**
     * Changes the filter that we're applying to the camera preview.
     */
    fun changeFilterMode(filter: Int) {
        mNewFilter = filter
    }

    /**
     * Updates the filter program.
     */
    fun updateFilter() {
        val programType: ProgramType?
        var kernel: FloatArray? = null
        var colorAdj = 0.0f

        Timber.d("Updating filter to %d", mNewFilter)
        when (mNewFilter) {
            CameraActivityBase.Companion.FILTER_NONE -> programType = ProgramType.TEXTURE_EXT
            CameraActivityBase.Companion.FILTER_BLACK_WHITE -> programType =
                ProgramType.TEXTURE_EXT_BW

            CameraActivityBase.Companion.FILTER_BLUR -> {
                programType = ProgramType.TEXTURE_EXT_FILT_VIEW
                kernel = floatArrayOf(
                    1f / 16f, 2f / 16f, 1f / 16f,
                    2f / 16f, 4f / 16f, 2f / 16f,
                    1f / 16f, 2f / 16f, 1f / 16f
                )
            }

            CameraActivityBase.Companion.FILTER_SHARPEN -> {
                programType = ProgramType.TEXTURE_EXT_FILT_VIEW
                kernel = floatArrayOf(
                    0f, -1f, 0f,
                    -1f, 5f, -1f,
                    0f, -1f, 0f
                )
            }

            CameraActivityBase.Companion.FILTER_EDGE_DETECT -> {
                programType = ProgramType.TEXTURE_EXT_FILT_VIEW
                kernel = floatArrayOf(
                    -1f, -1f, -1f,
                    -1f, 8f, -1f,
                    -1f, -1f, -1f
                )
            }

            CameraActivityBase.Companion.FILTER_EMBOSS -> {
                programType = ProgramType.TEXTURE_EXT_FILT_VIEW
                kernel = floatArrayOf(
                    2f, 0f, 0f,
                    0f, -1f, 0f,
                    0f, 0f, -1f
                )
                colorAdj = 0.5f
            }

            else -> throw RuntimeException("Unknown filter mode $mNewFilter")
        }

        // Do we need a whole new program?  (We want to avoid doing this if we don't have
        // too -- compiling a program could be expensive.)
        if (programType != mFullScreen?.program?.programType) {
            mFullScreen?.changeProgram(Texture2dProgram(programType))
            // If we created a new program, we need to initialize the texture width/height.
            mIncomingSizeUpdated = true
        }

        // Update the filter kernel (if any).
        if (kernel != null) {
            mFullScreen?.program?.setKernel(kernel, colorAdj)
        }

        mCurrentFilter = mNewFilter
    }

    /**
     * Records the size of the incoming camera preview frames.
     *
     *
     * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
     * so we assume it could go either way.  (Fortunately they both run on the same thread,
     * so we at least know that they won't execute concurrently.)
     */
    fun setCameraPreviewSize(width: Int, height: Int) {
        Timber.d("setCameraPreviewSize")
        mIncomingWidth = width
        mIncomingHeight = height
        mIncomingSizeUpdated = true
    }

    fun setVideoFrameSize(width: Int, height: Int) {
        mVideoFrameWidth = width
        mVideoFrameHeight = height
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        Timber.d("onSurfaceCreated")

        // We're starting up or coming back. Either way we've got a new EGLContext that will
        // need to be shared with the video encoder, so figure out if a recording is already
        // in progress.
        mRecordingEnabled = mVideoEncoder.isRecording
        mRecordingStatus = if (mRecordingEnabled) {
            RECORDING_RESUMED
        } else {
            RECORDING_OFF
        }

        // Set up the texture glitter that will be used for on-screen display. This
        // is *not* applied to the recording, because that uses a separate shader.
        mFullScreen = FullFrameRect(
            Texture2dProgram(ProgramType.TEXTURE_EXT)
        )

        mTextureId = mFullScreen?.createTextureObject()!!

        // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
        // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
        // available messages will arrive on the main thread.
        mSurfaceTexture = SurfaceTexture(mTextureId)

        // Tell the UI thread to enable the camera preview.
        if (mCameraId == 0) {
            mCameraHandler.sendMessage(
                mCameraHandler.obtainMessage(
                    CameraHandler.Companion.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture
                )
            )
        } else {
            mCameraHandler.sendMessage(
                mCameraHandler.obtainMessage(
                    CameraHandler.Companion.MSG_SET_SURFACE_TEXTURE2, mSurfaceTexture
                )
            )
        }
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        Timber.d("onSurfaceChanged %dx%d", width, height)
    }

    override fun onDrawFrame(unused: GL10?) {
        if (VERBOSE) Timber.d("onDrawFrame tex=%d", mTextureId)

        // Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
        // was there before.
        mSurfaceTexture!!.updateTexImage()

        // If the recording state is changing, take care of it here.  Ideally we wouldn't
        // be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
        // makes it hard to do elsewhere.
        if (mRecordingEnabled) {
            when (mRecordingStatus) {
                RECORDING_OFF -> {
                    if (mVideoFrameWidth > 0 && mVideoFrameHeight > 0) {
                        Timber.d("Start recording outputFile: %s", mOutputFile)
                        // The output video has a size e.g., 720x1280. Video of the same size is recorded in
                        // the portrait mode of the complex CameraRecorder-android at
                        // https://github.com/MasayukiSuda/CameraRecorder-android.
                        mVideoEncoder.startRecording(
                            mFullScreen?.program?.let {
                                EncoderConfig(
                                    mOutputFile, mVideoFrameHeight, mVideoFrameWidth,
                                    CameraUtils.calcBitRate(
                                        mVideoFrameWidth, mVideoFrameHeight,
                                        VideoEncoderCore.Companion.FRAME_RATE
                                    ),
                                    EGL14.eglGetCurrentContext(),
                                    it, mMetadataFile
                                )
                            }
                        )
                        mRecordingStatus = RECORDING_ON
                    } else {
                        Timber.i("Start recording before setting video frame size; skipping")
                    }
                }

                RECORDING_RESUMED -> {
                    Timber.d("Resume recording")
                    mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext())
                    mRecordingStatus = RECORDING_ON
                }

                RECORDING_ON -> {}
                else -> throw RuntimeException("unknown status $mRecordingStatus")
            }
        } else {
            when (mRecordingStatus) {
                RECORDING_ON, RECORDING_RESUMED -> {
                    Timber.d("Stop recording")
                    mVideoEncoder.stopRecording()
                    mRecordingStatus = RECORDING_OFF
                }

                RECORDING_OFF -> {}
                else -> throw RuntimeException("unknown status $mRecordingStatus")
            }
        }

        // Set the video encoder's texture name.  We only need to do this once, but in the
        // current implementation it has to happen after the video encoder is started, so
        // we just do it here.
        mVideoEncoder.setTextureId(mTextureId)

        // Tell the video encoder thread that a new frame is available.
        // This will be ignored if we're not actually recording.
        mSurfaceTexture?.let { mVideoEncoder.frameAvailable(it) }

        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
            // Texture size isn't set yet.  This is only used for the filters, but to be
            // safe we can just skip drawing while we wait for the various races to resolve.
            // (This seems to happen if you toggle the screen off/on with power button.)
            Timber.i("Drawing before incoming texture size set; skipping")
            return
        }

        // Update the filter, if necessary.
        if (mCurrentFilter != mNewFilter) {
            updateFilter()
        }

        if (mIncomingSizeUpdated) {
            mFullScreen?.program?.setTexSize(mIncomingWidth, mIncomingHeight)
            mIncomingSizeUpdated = false
        }

        // Draw the video frame.
        mSurfaceTexture!!.getTransformMatrix(mSTMatrix)
        mFullScreen!!.drawFrame(mTextureId, mSTMatrix)

        // Draw a flashing box if we're recording. This only appears on screen.
        val showBox: Boolean = (mRecordingStatus == RECORDING_ON)
        if (showBox && (++mFrameCount and 0x04) == 0) {
            drawBox()
        }
    }

    /**
     * Draws a red box in the corner.
     */
    private fun drawBox() {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(0, 0, 50, 50)
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    companion object {
        private const val VERBOSE = false

        private const val RECORDING_OFF = 0
        private const val RECORDING_ON = 1
        private const val RECORDING_RESUMED = 2
    }
}

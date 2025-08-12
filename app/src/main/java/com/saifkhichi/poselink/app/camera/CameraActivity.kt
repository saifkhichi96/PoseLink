package com.saifkhichi.poselink.app.camera

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.app.recordings.RecordingsActivity
import com.saifkhichi.poselink.app.sensors.ImuViewerActivity
import com.saifkhichi.poselink.app.settings.AboutActivity
import com.saifkhichi.poselink.app.settings.SettingsActivity
import com.saifkhichi.poselink.core.camera.Camera2Proxy
import com.saifkhichi.poselink.core.camera.ManualFocusConfig
import com.saifkhichi.poselink.core.sensors.IMUManager
import com.saifkhichi.poselink.databinding.CameraActivityBinding
import com.saifkhichi.poselink.streaming.HttpStreamingServer
import com.saifkhichi.poselink.sync.TimeBaseManager
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface

class CameraActivity : CameraActivityBase(), PopupMenu.OnMenuItemClickListener {
    private lateinit var binding: CameraActivityBinding

    // Primary camera properties
    private var mPrimaryRenderer: CameraSurfaceRenderer? = null
    private lateinit var mPrimaryCameraHandler: CameraHandler

    // Secondary camera properties
    private var mSecondaryRenderer: CameraSurfaceRenderer? = null
    private lateinit var mSecondaryCameraHandler: CameraHandler

    // Session manager, inertial manager, and time base manager
    private lateinit var mSessionManager: SessionManager
    private lateinit var mImuManager: IMUManager
    private lateinit var mTimeBaseManager: TimeBaseManager
    private lateinit var mStreamingServer: HttpStreamingServer

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        binding = CameraActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFilterSpinner(binding.cameraFilterSpinner)
    }

    override fun onStart() {
        super.onStart()
        setupCameras()
        setupGLViews()
        setupSensors()
        setupStreaming()
    }

    override fun onResume() {
        super.onResume()
        resumeCameras()
        resumeGLViews()
        mImuManager.register()
    }

    override fun onPause() {
        super.onPause()
        releaseCameras()
        pauseGLViews()
        mImuManager.unregister()
        mStreamingServer.stopServer()
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        super.onDestroy()
        mPrimaryCameraHandler.invalidateHandler()
        mSecondaryCameraHandler.invalidateHandler()
    }

    private fun setupCameras() {
        // Set up the primary camera proxy
        mPrimaryCameraProxy = Camera2Proxy(this, false)
        val previewSize = mPrimaryCameraProxy!!.configureCamera()
        val primaryVideoSize = mPrimaryCameraProxy!!.getVideoSize()
        mPrimaryFrameWidth = primaryVideoSize.width
        mPrimaryFrameHeight = primaryVideoSize.height

        // Set up the secondary camera proxy
        mSecondaryCameraProxy = Camera2Proxy(this, true)
        mSecondaryCameraProxy!!.configureCamera()
        val secondaryVideoSize = mSecondaryCameraProxy!!.getVideoSize()
        mSecondaryFrameWidth = secondaryVideoSize.width
        mSecondaryFrameHeight = secondaryVideoSize.height

        // Update layout aspect ratio based on the primary camera preview size
        setLayoutAspectRatio(previewSize) // updates mCameraPreviewWidth/Height

        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mPrimaryCameraHandler = CameraHandler(this)
        mSecondaryCameraHandler = CameraHandler(this)

        mPrimaryCameraParamsView = findViewById(R.id.cameraParams_text)
        mSecondaryCameraParamsView = findViewById(R.id.cameraParams_text2)
        mPrimaryCaptureResultView = findViewById(R.id.captureResult_text)
        mSecondaryCaptureResultView = findViewById(R.id.captureResult_text2)
        mOutputDirText = findViewById(R.id.cameraOutputDir_text)
    }

    private fun resumeCameras() {
        Timber.d("Keeping screen on for previewing recording.")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateControls()

        if (mPrimaryCameraProxy == null) {
            mPrimaryCameraProxy = Camera2Proxy(this, false)
            val previewSize = mPrimaryCameraProxy!!.configureCamera()
            setLayoutAspectRatio(previewSize)
            val videoSize = mPrimaryCameraProxy!!.getVideoSize()
            mPrimaryFrameWidth = videoSize.width
            mPrimaryFrameHeight = videoSize.height
        }

        if (mSecondaryCameraProxy == null) {
            mSecondaryCameraProxy = Camera2Proxy(this, true)
            mSecondaryCameraProxy!!.configureCamera()
            val videoSize = mSecondaryCameraProxy!!.getVideoSize()
            mSecondaryFrameWidth = videoSize.width
            mSecondaryFrameHeight = videoSize.height
        }
    }

    private fun releaseCameras() {
        if (mPrimaryCameraProxy != null) {
            mPrimaryCameraProxy?.releaseCamera()
            mPrimaryCameraProxy = null
        }

        if (mSecondaryCameraProxy != null) {
            mSecondaryCameraProxy?.releaseCamera()
            mSecondaryCameraProxy = null
        }
    }

    private fun setupGLViews() {
        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        mPrimaryGLView = findViewById(R.id.cameraPreview_surfaceView)
        mSecondaryGLView = findViewById(R.id.cameraPreview_surfaceView2)

        if (mPrimaryRenderer == null) {
            mPrimaryRenderer =
                CameraSurfaceRenderer(mPrimaryCameraHandler!!, mPrimaryVideoEncoder, 0)
            mPrimaryGLView?.setEGLContextClientVersion(2) // select GLES 2.0
            mPrimaryGLView?.setRenderer(mPrimaryRenderer)
            mPrimaryGLView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        if (mSecondaryRenderer == null) {
            mSecondaryRenderer =
                CameraSurfaceRenderer(mSecondaryCameraHandler!!, mSecondaryVideoEncoder, 1)
            mSecondaryGLView?.setEGLContextClientVersion(2) // select GLES 2.0
            mSecondaryGLView?.setRenderer(mSecondaryRenderer)
            mSecondaryGLView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        mPrimaryGLView?.setTouchListener({ event: MotionEvent?, width: Int, height: Int ->
            val focusConfig = ManualFocusConfig(
                event!!.x,
                event.y,
                width,
                height
            )
            Timber.d(focusConfig.toString())
            mPrimaryCameraHandler!!.sendMessage(
                mPrimaryCameraHandler!!.obtainMessage(
                    CameraHandler.Companion.MSG_MANUAL_FOCUS,
                    focusConfig
                )
            )
        })
    }

    private fun resumeGLViews() {
        mPrimaryGLView!!.onResume()
        mPrimaryGLView!!.queueEvent {
            mPrimaryRenderer!!.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight)
            mPrimaryRenderer!!.setVideoFrameSize(mPrimaryFrameWidth, mPrimaryFrameHeight)
        }

        mSecondaryGLView!!.onResume()
        mSecondaryGLView!!.queueEvent {
            mSecondaryRenderer!!.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight)
            mSecondaryRenderer!!.setVideoFrameSize(mSecondaryFrameWidth, mSecondaryFrameHeight)
        }
    }

    private fun pauseGLViews() {
        mPrimaryGLView?.queueEvent { mPrimaryRenderer?.notifyPausing() }
        mPrimaryGLView?.onPause()

        mSecondaryGLView?.queueEvent { mSecondaryRenderer?.notifyPausing() }
        mSecondaryGLView?.onPause()
    }

    private fun setupSensors() {
        if (!::mSessionManager.isInitialized) {
            mImuManager = IMUManager(this)
            mTimeBaseManager = TimeBaseManager()
            mSessionManager = SessionManager(this)

            mSessionManager.onSessionFilesReady = ::startRecording
            mSessionManager.onSessionEnded = ::stopRecording
        }
        mSessionManager.setActive(mSecondaryVideoEncoder.isRecording)
    }

    private fun setupStreaming() {
        // Create the streaming server
        mStreamingServer = HttpStreamingServer(
            port = 8080,
            frames = mPrimaryVideoEncoder,
            sensors = mImuManager,
            maxFps = 30
        )
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (i in interfaces) {
                if (!i.isUp || i.isLoopback) continue
                for (address in i.inetAddresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    fun clickToggleRecording(@Suppress("unused") unused: View?) {
        mSessionManager.toggleEnabled()
        val state = mSessionManager.isActive()
        mPrimaryGLView?.queueEvent { mPrimaryRenderer?.changeRecordingState(state) }
        mSecondaryGLView?.queueEvent { mSecondaryRenderer?.changeRecordingState(state) }
        updateControls()
    }

    private fun startRecording(paths: SessionManager.SessionPaths) {
        Timber.d("Starting recording session with paths: $paths")

        // Show output directory name in the UI
        val outputDir = paths.outputDir
        val basename = outputDir.substring(outputDir.lastIndexOf("/") + 1)
        mOutputDirText?.text = basename

        // Set the new output files in the renderers
        mPrimaryRenderer?.resetOutputFiles(
            paths.videoOutputFile1,
            paths.videoTimestampFile1
        )
        mSecondaryRenderer?.resetOutputFiles(
            paths.videoOutputFile2,
            paths.videoTimestampFile2
        )

        // Start recording the IMU data
        mImuManager.startRecording(paths.inertialFile)

        // Start recording the camera capture results
        mPrimaryCameraProxy?.startRecordingCaptureResult(paths.videoMetaFile1)
        mSecondaryCameraProxy?.startRecordingCaptureResult(paths.videoMetaFile2)

        // Start the time base manager
        mPrimaryCameraProxy?.getTimeSourceValue()?.let {
            mTimeBaseManager.startRecording(
                paths.edgeEpochFile,
                it
            )
        }

        // Start HTTP streaming here
        mStreamingServer.startServer()
        getLocalIpAddress()?.let { ip ->
            runOnUiThread {
                findViewById<TextView>(R.id.serverAddressView)?.text =
                    getString(
                        R.string.streaming_status_on,
                        ip,
                        mStreamingServer.listeningPort
                    )
            }
        }
    }

    private fun stopRecording() {
        Timber.d("Stopping recording session")

        // Stop recording IMU data
        mImuManager.stopRecording()

        // Stop recording the camera capture results
        mPrimaryCameraProxy?.stopRecordingCaptureResult()
        mSecondaryCameraProxy?.stopRecordingCaptureResult()

        // Stop the time base manager
        mTimeBaseManager.stopRecording()

        // Stop HTTP streaming here
        mStreamingServer.stopServer()
        runOnUiThread {
            findViewById<TextView>(R.id.serverAddressView)?.text =
                getString(R.string.streaming_status_off)
        }
    }

    private fun setupFilterSpinner(spinner: Spinner) {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.cameraFilterNames, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val filterNum = pos
                (parent?.getChildAt(0) as? TextView)?.apply {
                    setTextColor(-0x1)
                    gravity = Gravity.CENTER
                }
                mPrimaryGLView?.queueEvent { mPrimaryRenderer?.changeFilterMode(filterNum) }
                mSecondaryGLView?.queueEvent { mSecondaryRenderer?.changeFilterMode(filterNum) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateControls() {
        val state = mSessionManager.isActive()
        val toggleRecordingButton = binding.toggleRecordingButton
        val filterSpinner = binding.cameraFilterSpinner

        toggleRecordingButton.contentDescription = when {
            state -> getString(R.string.stop)
            else -> getString(R.string.record)
        }
        toggleRecordingButton.setImageResource(
            when {
                state -> R.drawable.ic_baseline_stop_24
                else -> R.drawable.ic_baseline_fiber_manual_record_24
            }
        )
        filterSpinner.visibility = when {
            state -> View.INVISIBLE
            else -> View.VISIBLE
        }
    }

    fun clickShowPopupMenu(v: View?) {
        val popup = PopupMenu(applicationContext, v)
        popup.setOnMenuItemClickListener(this)
        popup.inflate(R.menu.popup_menu)
        popup.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_imu -> Intent(this, ImuViewerActivity::class.java)
            R.id.menu_recordings -> Intent(this, RecordingsActivity::class.java)
            R.id.menu_settings -> Intent(this, SettingsActivity::class.java)
            R.id.menu_about -> Intent(this, AboutActivity::class.java)
            else -> null
        }?.let {
            startActivity(it)
            return true
        }
        return false
    }
}

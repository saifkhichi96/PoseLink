package com.saifkhichi.poselink.core.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.preference.PreferenceManager
import com.saifkhichi.poselink.app.camera.CameraActivityBase
import com.saifkhichi.poselink.app.camera.FocalLengthHelper
import timber.log.Timber
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import kotlin.concurrent.Volatile
import kotlin.math.max

class Camera2Proxy(private val mActivity: Activity, private val mSecondCamera: Boolean) {
    private var mCameraIdStr = ""
    private lateinit var mPreviewSize: Size
    private var mVideoSize: Size? = null
    private val mCameraManager: CameraManager
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var sensorArraySize: Rect? = null
    private var mTimeSourceValue: Int? = null

    private var mPreviewRequest: CaptureRequest? = null
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null
    private var mImageReader: ImageReader? = null
    private var mPreviewSurface: Surface? = null
    private var mPreviewSurfaceTexture: SurfaceTexture? = null

    /**
     * The current state of camera state for taking pictures.
     *
     * @see .mFocusCaptureCallback
     */
    private var mState: Int = STATE_PREVIEW

    private var mFrameMetadataWriter: BufferedWriter? = null

    @Volatile
    private var mRecordingMetadata = false

    private val mFocalLengthHelper = FocalLengthHelper()

    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Timber.d("onOpened")
            mCameraDevice = camera
            initPreviewRequest()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Timber.d("onDisconnected")
            releaseCamera()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Timber.w("Camera Open failed with error %d", error)
            releaseCamera()
        }
    }

    fun getTimeSourceValue(): Int? {
        return mTimeSourceValue
    }

    fun getVideoSize(): Size {
        return mVideoSize!!
    }

    fun startRecordingCaptureResult(captureResultFile: String) {
        try {
            if (mFrameMetadataWriter != null) {
                try {
                    mFrameMetadataWriter!!.flush()
                    mFrameMetadataWriter!!.close()
                    Timber.d("Flushing results!")
                } catch (err: IOException) {
                    Timber.e(err, "IOException in closing an earlier frameMetadataWriter.")
                }
            }
            mFrameMetadataWriter = BufferedWriter(
                FileWriter(captureResultFile, true)
            )
            val header = "Timestamp[nanosec],fx[px],fy[px],Frame No.," +
                    "Exposure time[nanosec],Sensor frame duration[nanosec]," +
                    "Frame readout time[nanosec]," +
                    "ISO,Focal length,Focus distance,AF mode,Unix time[nanosec]"

            mFrameMetadataWriter!!.write(header + "\n")
            mRecordingMetadata = true
        } catch (err: IOException) {
            Timber.e(
                err, "IOException in opening frameMetadataWriter at %s",
                captureResultFile
            )
        }
    }

    fun stopRecordingCaptureResult() {
        if (mRecordingMetadata) {
            mRecordingMetadata = false
        }
        if (mFrameMetadataWriter != null) {
            try {
                mFrameMetadataWriter!!.flush()
                mFrameMetadataWriter!!.close()
            } catch (err: IOException) {
                Timber.e(err, "IOException in closing frameMetadataWriter.")
            }
            mFrameMetadataWriter = null
        }
    }

    fun configureCamera(): Size {
        try {
            mCameraIdStr = if (mSecondCamera) {
                mSharedPreferences.getString("prefCamera2", "1")!!
            } else {
                mSharedPreferences.getString("prefCamera", "0")!!
            }

            val mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraIdStr)

            val imageSize: String = mSharedPreferences.getString(
                "prefSizeRaw",
                DesiredCameraSetting.mDesiredFrameSize
            )!!
            val width = imageSize.substring(0, imageSize.lastIndexOf("x")).toInt()
            val height = imageSize.substring(imageSize.lastIndexOf("x") + 1).toInt()

            sensorArraySize = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
            )
            mTimeSourceValue = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE
            )

            val map = mCameraCharacteristics.get(
                CameraCharacteristics
                    .SCALER_STREAM_CONFIGURATION_MAP
            )

            val videoSizeChoices = map!!.getOutputSizes(MediaRecorder::class.java)
            mVideoSize = CameraUtils.chooseVideoSize(videoSizeChoices, width, height, width)

            mFocalLengthHelper.setLensParams(mCameraCharacteristics)
            mVideoSize?.let { mFocalLengthHelper.setmImageSize(it) }

            mPreviewSize = mVideoSize?.let {
                CameraUtils.chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture::class.java),
                    width, height, it
                )
            }!!
            Timber.d(
                "Video size %s preview size %s.",
                mVideoSize.toString(), mPreviewSize.toString()
            )
        } catch (e: CameraAccessException) {
            Timber.e(e)
        }
        return mPreviewSize
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        Timber.v("openCamera")
        startBackgroundThread()
        if (mCameraIdStr.isEmpty()) {
            configureCamera()
        }
        try {
            mCameraManager.openCamera(mCameraIdStr, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            Timber.e(e)
        }
    }

    fun releaseCamera() {
        Timber.v("releaseCamera")
        if (null != mCaptureSession) {
            mCaptureSession!!.close()
            mCaptureSession = null
        }
        if (mCameraDevice != null) {
            mCameraDevice!!.close()
            mCameraDevice = null
        }
        if (mImageReader != null) {
            mImageReader!!.close()
            mImageReader = null
        }
        mPreviewSurfaceTexture = null
        mCameraIdStr = ""
        stopRecordingCaptureResult()
        stopBackgroundThread()
    }

    fun setPreviewSurfaceTexture(surfaceTexture: SurfaceTexture?) {
        mPreviewSurfaceTexture = surfaceTexture
    }

    private class NumExpoIso(var mNumber: Long?, var mExposureNanos: Long?, var mIso: Int?)

    private val kMaxExpoSamples = 10
    private val expoStats: ArrayList<NumExpoIso?> = ArrayList<NumExpoIso?>(kMaxExpoSamples)

    private fun setExposureAndIso() {
        var exposureNanos = DesiredCameraSetting.mDesiredExposureTime
        var desiredIsoL = 30L * 30000000L / exposureNanos
        var desiredIso: Int? = desiredIsoL.toInt()
        if (!expoStats.isEmpty()) {
            val index = expoStats.size / 2
            val actualExpo = expoStats[index]!!.mExposureNanos
            val actualIso = expoStats[index]!!.mIso
            if (actualExpo != null && actualIso != null) {
                if (actualExpo <= exposureNanos) {
                    exposureNanos = actualExpo
                    desiredIso = actualIso
                } else {
                    desiredIsoL = actualIso * actualExpo / exposureNanos
                    desiredIso = desiredIsoL.toInt()
                }
            } // else may occur on an emulated device.
        }

        val manualControl: Boolean = mSharedPreferences.getBoolean("switchManualControl", false)
        if (manualControl) {
            val exposureTimeMs = exposureNanos.toFloat() / 1e6f
            val exposureTimeMsStr: String = mSharedPreferences.getString(
                "prefExposureTime", exposureTimeMs.toString()
            )!!
            exposureNanos = (exposureTimeMsStr.toFloat() * 1e6f).toLong()
            val desiredIsoStr: String =
                mSharedPreferences.getString("prefISO", desiredIso.toString())!!
            desiredIso = desiredIsoStr.toInt()
        }

        // fix exposure
        mPreviewRequestBuilder!!.set(
            CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF
        )

        mPreviewRequestBuilder!!.set(
            CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNanos
        )
        Timber.d("Exposure time set to %d", exposureNanos)

        // fix ISO
        mPreviewRequestBuilder!!.set(CaptureRequest.SENSOR_SENSITIVITY, desiredIso)
        Timber.d("ISO set to %d", desiredIso)
    }

    private fun initPreviewRequest() {
        try {
            mPreviewRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)

            // Set control elements, we want auto white balance
            mPreviewRequestBuilder!!.set(
                CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO
            )
            mPreviewRequestBuilder!!.set(
                CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO
            )

            val surfaces: MutableList<Surface?> = ArrayList()

            if (mPreviewSurfaceTexture != null && mPreviewSurface == null) { // use texture view
                mPreviewSurfaceTexture!!.setDefaultBufferSize(
                    mPreviewSize.width,
                    mPreviewSize.height
                )
                mPreviewSurface = Surface(mPreviewSurfaceTexture)
            }
            surfaces.add(mPreviewSurface)
            mPreviewRequestBuilder!!.addTarget(mPreviewSurface!!)

            mCameraDevice!!.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        mCaptureSession = session
                        mPreviewRequest = mPreviewRequestBuilder!!.build()
                        startPreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Timber.w("ConfigureFailed. session: mCaptureSession")
                    }
                }, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            Timber.e(e)
        }
    }

    fun startPreview() {
        Timber.v("startPreview")
        if (mCaptureSession == null || mPreviewRequestBuilder == null) {
            Timber.w("startPreview: mCaptureSession or mPreviewRequestBuilder is null")
            return
        }
        try {
            mCaptureSession!!.setRepeatingRequest(
                mPreviewRequest!!, mFocusCaptureCallback, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            Timber.e(e)
        }
    }

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to tap to focus.
     * https://stackoverflow.com/questions/42127464/how-to-lock-focus-in-camera2-api-android
     */
    private val mFocusCaptureCallback
            : CaptureCallback = object : CaptureCallback() {
        private fun process(result: CaptureResult) {
            when (mState) {
                STATE_PREVIEW -> {}
                STATE_WAITING_AUTO -> {
                    val afMode = result.get(CaptureResult.CONTROL_AF_MODE)
                    if (afMode != null && afMode == CaptureResult.CONTROL_AF_MODE_AUTO) {
                        mState = STATE_TRIGGER_AUTO

                        mPreviewRequestBuilder!!.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_AUTO
                        )
                        mPreviewRequestBuilder!!.set(
                            CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_START
                        )
                        try {
                            mCaptureSession!!.capture(
                                mPreviewRequestBuilder!!.build(),
                                mFocusCaptureCallback, mBackgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            Timber.e(e)
                        }
                    }
                }

                STATE_TRIGGER_AUTO -> {
                    mState = STATE_WAITING_LOCK

                    setExposureAndIso()

                    mPreviewRequestBuilder!!.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_AUTO
                    )
                    mPreviewRequestBuilder!!.set(
                        CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_IDLE
                    )
                    try {
                        mCaptureSession!!.setRepeatingRequest(
                            mPreviewRequestBuilder!!.build(),
                            mFocusCaptureCallback, mBackgroundHandler
                        )
                    } catch (e: CameraAccessException) {
                        Timber.e(e)
                    }
                    Timber.d("Focus trigger auto")
                }

                STATE_WAITING_LOCK -> {
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (afState == null) {
                        mState = STATE_FOCUS_LOCKED
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState
                    ) {
                        mState = STATE_FOCUS_LOCKED
                        Timber.d("Focus locked after waiting lock")
                    }
                }
            }
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val unixTime = System.currentTimeMillis()
            process(result)

            val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
            val number = result.frameNumber
            val exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)

            val frmDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION)
            val frmReadoutNs = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            if (expoStats.size > kMaxExpoSamples) {
                expoStats.subList(0, kMaxExpoSamples / 2).clear()
            }
            expoStats.add(NumExpoIso(number, exposureTimeNs, iso))

            val fl = result.get(CaptureResult.LENS_FOCAL_LENGTH)

            val fd = result.get(CaptureResult.LENS_FOCUS_DISTANCE)

            val afMode = result.get(CaptureResult.CONTROL_AF_MODE)

            val rect = result.get(CaptureResult.SCALER_CROP_REGION)
            mFocalLengthHelper.setmFocalLength(fl)
            mFocalLengthHelper.setmFocusDistance(fd)
            rect?.let { mFocalLengthHelper.setmCropRegion(it) }
            val szFocalLength = mFocalLengthHelper.focalLengthPixel
            val delimiter = ","
            val frameInfo = timestamp.toString() +
                    delimiter + szFocalLength.width +
                    delimiter + szFocalLength.height +
                    delimiter + number +
                    delimiter + exposureTimeNs +
                    delimiter + frmDurationNs +
                    delimiter + frmReadoutNs +
                    delimiter + iso +
                    delimiter + fl +
                    delimiter + fd +
                    delimiter + afMode +
                    delimiter + unixTime + "000000"
            if (mRecordingMetadata) {
                try {
                    mFrameMetadataWriter!!.write(frameInfo + "\n")
                } catch (err: IOException) {
                    Timber.e(err, "Error writing captureResult")
                }
            }
            afMode?.let {
                (mActivity as CameraActivityBase).updateCaptureResultPanel(
                    szFocalLength.width, exposureTimeNs, it, mSecondCamera
                )
            }
        }
    }


    init {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity)
        mCameraManager = mActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // If it's the second camera
    }

    fun changeManualFocusPoint(focusConfig: ManualFocusConfig) {
        val eventX = focusConfig.mEventX
        val eventY = focusConfig.mEventY
        val viewWidth = focusConfig.mViewWidth
        val viewHeight = focusConfig.mViewHeight

        val y = ((eventX / viewWidth.toFloat()) * sensorArraySize!!.height().toFloat()).toInt()
        val x = ((eventY / viewHeight.toFloat()) * sensorArraySize!!.width().toFloat()).toInt()
        val halfTouchWidth = 400
        val halfTouchHeight = 400
        val focusAreaTouch = MeteringRectangle(
            max(x - halfTouchWidth, 0),
            max(y - halfTouchHeight, 0),
            halfTouchWidth * 2,
            halfTouchHeight * 2,
            MeteringRectangle.METERING_WEIGHT_MAX - 1
        )
        mPreviewRequestBuilder!!.set(
            CaptureRequest.CONTROL_AF_MODE,
            CameraMetadata.CONTROL_AF_MODE_AUTO
        )
        mPreviewRequestBuilder!!.set(
            CaptureRequest.CONTROL_AF_REGIONS,
            arrayOf(focusAreaTouch) as Array<MeteringRectangle?>?
        )
        try {
            mState = STATE_WAITING_AUTO
            mCaptureSession!!.setRepeatingRequest(
                mPreviewRequestBuilder!!.build(), mFocusCaptureCallback, null
            )
        } catch (e: CameraAccessException) {
            Timber.e(e)
        }
    }

    private fun startBackgroundThread() {
        if (mBackgroundThread == null || mBackgroundHandler == null) {
            Timber.v("startBackgroundThread")
            mBackgroundThread = HandlerThread("CameraBackground")
            mBackgroundThread!!.start()
            mBackgroundHandler = Handler(mBackgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        Timber.v("stopBackgroundThread")
        try {
            if (mBackgroundThread != null) {
                mBackgroundThread!!.quitSafely()
                mBackgroundThread!!.join()
            }
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            Timber.e(e)
        }
    }

    companion object {
        private lateinit var mSharedPreferences: SharedPreferences

        /**
         * Camera state: Showing camera preview.
         */
        private const val STATE_PREVIEW = 0

        /**
         * Wait until the CONTROL_AF_MODE is in auto.
         */
        private const val STATE_WAITING_AUTO = 1

        /**
         * Trigger auto focus algorithm.
         */
        private const val STATE_TRIGGER_AUTO = 2

        /**
         * Camera state: Waiting for the focus to be locked.
         */
        private const val STATE_WAITING_LOCK = 3

        /**
         * Camera state: Focus distance is locked.
         */
        private const val STATE_FOCUS_LOCKED = 4
    }
}

package com.saifkhichi.poselink.app.camera

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.saifkhichi.poselink.core.camera.CameraUtils
import com.saifkhichi.poselink.core.camera.TextureMovieEncoder
import com.saifkhichi.poselink.core.camera.VideoEncoderCore
import com.saifkhichi.poselink.gles.FullFrameRect
import com.saifkhichi.poselink.gles.Texture2dProgram
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

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
            Timber.Forest.d("renderer pausing -- releasing SurfaceTexture")
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
        Timber.Forest.d("changeRecordingState: was %b now %b", mRecordingEnabled, isRecording)
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
        val programType: Texture2dProgram.ProgramType?
        var kernel: FloatArray? = null
        var colorAdj = 0.0f

        Timber.Forest.d("Updating filter to %d", mNewFilter)
        when (mNewFilter) {
            CameraActivityBase.Companion.FILTER_NONE -> programType =
                Texture2dProgram.ProgramType.TEXTURE_EXT

            CameraActivityBase.Companion.FILTER_BLACK_WHITE -> programType =
                Texture2dProgram.ProgramType.TEXTURE_EXT_BW

            CameraActivityBase.Companion.FILTER_BLUR -> {
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT_VIEW
                kernel = floatArrayOf(
                    1f / 16f, 2f / 16f, 1f / 16f,
                    2f / 16f, 4f / 16f, 2f / 16f,
                    1f / 16f, 2f / 16f, 1f / 16f
                )
            }

            CameraActivityBase.Companion.FILTER_SHARPEN -> {
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT_VIEW
                kernel = floatArrayOf(
                    0f, -1f, 0f,
                    -1f, 5f, -1f,
                    0f, -1f, 0f
                )
            }

            CameraActivityBase.Companion.FILTER_EDGE_DETECT -> {
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT_VIEW
                kernel = floatArrayOf(
                    -1f, -1f, -1f,
                    -1f, 8f, -1f,
                    -1f, -1f, -1f
                )
            }

            CameraActivityBase.Companion.FILTER_EMBOSS -> {
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT_VIEW
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
        Timber.Forest.d("setCameraPreviewSize")
        mIncomingWidth = width
        mIncomingHeight = height
        mIncomingSizeUpdated = true
    }

    fun setVideoFrameSize(width: Int, height: Int) {
        mVideoFrameWidth = width
        mVideoFrameHeight = height
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        Timber.Forest.d("onSurfaceCreated")

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
            Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT)
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
        Timber.Forest.d("onSurfaceChanged %dx%d", width, height)
    }

    override fun onDrawFrame(unused: GL10?) {
        if (VERBOSE) Timber.Forest.d("onDrawFrame tex=%d", mTextureId)

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
                        Timber.Forest.d("Start recording outputFile: %s", mOutputFile)
                        // The output video has a size e.g., 720x1280. Video of the same size is recorded in
                        // the portrait mode of the complex CameraRecorder-android at
                        // https://github.com/MasayukiSuda/CameraRecorder-android.
                        mVideoEncoder.startRecording(
                            mFullScreen?.program?.let {
                                TextureMovieEncoder.EncoderConfig(
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
                        Timber.Forest.i("Start recording before setting video frame size; skipping")
                    }
                }

                RECORDING_RESUMED -> {
                    Timber.Forest.d("Resume recording")
                    mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext())
                    mRecordingStatus = RECORDING_ON
                }

                RECORDING_ON -> {}
                else -> throw RuntimeException("unknown status $mRecordingStatus")
            }
        } else {
            when (mRecordingStatus) {
                RECORDING_ON, RECORDING_RESUMED -> {
                    Timber.Forest.d("Stop recording")
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
            Timber.Forest.i("Drawing before incoming texture size set; skipping")
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
package com.saifkhichi.poselink.core.camera

import android.graphics.SurfaceTexture
import android.opengl.EGLContext
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.saifkhichi.poselink.gles.EglCore
import com.saifkhichi.poselink.gles.FullFrameRect
import com.saifkhichi.poselink.gles.Texture2dProgram
import com.saifkhichi.poselink.gles.WindowSurface
import timber.log.Timber
import java.io.IOException
import java.lang.ref.WeakReference
import kotlin.concurrent.Volatile

/**
 * Encode a movie from frames rendered from an external texture image.
 *
 *
 * The object wraps an encoder running on a dedicated thread.  The various control messages
 * may be sent from arbitrary threads (typically the app UI thread).  The encoder thread
 * manages both sides of the encoder (feeding and draining); the only external input is
 * the GL texture.
 *
 *
 * The design is complicated slightly by the need to create an EGL context that shares state
 * with a view that gets restarted if (say) the device orientation changes.  When the view
 * in question is a GLSurfaceView, we don't have full control over the EGL context creation
 * on that side, so we have to bend a bit backwards here.
 *
 *
 * To use:
 *
 *  * create TextureMovieEncoder object
 *  * create an EncoderConfig
 *  * call TextureMovieEncoder#startRecording() with the config
 *  * call TextureMovieEncoder#setTextureId() with the texture object that receives frames
 *  * for each frame, after latching it with SurfaceTexture#updateTexImage(),
 * call TextureMovieEncoder#frameAvailable().
 */
class TextureMovieEncoder : Runnable {
    // ----- accessed exclusively by encoder thread -----
    private var mInputWindowSurface: WindowSurface? = null
    private var mEglCore: EglCore? = null
    private var mFullScreen: FullFrameRect? = null
    private var mTextureId = 0
    private var mFrameNum = 0
    private var mVideoEncoder: VideoEncoderCore? = null

    // ----- accessed by multiple threads -----
    @Volatile
    private var mHandler: EncoderHandler? = null

    private val mReadyFence = Any() // guards ready/running
    private var mReady = false
    private var mRunning = false
    private var mLastFrameTimeNs: Long? = null
    var mFrameRate: Float = 15f
    private val STMatrix = FloatArray(16)

    /**
     * Encoder configuration.
     *
     *
     * Object is immutable, which means we can safely pass it between threads without
     * explicit synchronization (and don't need to worry about it getting tweaked out from
     * under us).
     *
     *
     * with reasonable defaults for those and bit rate.
     */
    class EncoderConfig(
        val mOutputFile: String?, val mWidth: Int, val mHeight: Int, val mBitRate: Int,
        val mEglContext: EGLContext?, val mProgram: Texture2dProgram, val mMetadataFile: String?
    ) {
        override fun toString(): String {
            return "EncoderConfig: " + mWidth + "x" + mHeight + " @" + mBitRate +
                    " to '" + mOutputFile + "' ctxt=" + mEglContext
        }
    }

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     *
     *
     * Creates a new thread, which will create an encoder using the provided configuration.
     *
     *
     * Returns after the recorder thread has started and is ready to accept Messages.  The
     * encoder may not yet be fully configured.
     */
    fun startRecording(config: EncoderConfig?) {
        Timber.Forest.d("Encoder: startRecording()")
        synchronized(mReadyFence) {
            if (mRunning) {
                Timber.Forest.w("Encoder thread already running")
                return
            }
            mRunning = true

            Thread(this, "TextureMovieEncoder").start()
            while (!mReady) {
                try {
                    (mReadyFence as Object).wait()
                } catch (ie: InterruptedException) {
                    // ignore
                }
            }
        }

        mHandler!!.sendMessage(mHandler!!.obtainMessage(MSG_START_RECORDING, config))
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     *
     *
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     *
     *
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    fun stopRecording() {
        mHandler!!.sendMessage(mHandler!!.obtainMessage(MSG_STOP_RECORDING))
        mHandler!!.sendMessage(mHandler!!.obtainMessage(MSG_QUIT))
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    val isRecording: Boolean
        /**
         * Returns true if recording has been started.
         */
        get() {
            synchronized(mReadyFence) {
                return mRunning
            }
        }

    /**
     * Tells the video recorder to refresh its EGL surface.  (Call from non-encoder thread.)
     */
    fun updateSharedContext(sharedContext: EGLContext?) {
        mHandler!!.sendMessage(mHandler!!.obtainMessage(MSG_UPDATE_SHARED_CONTEXT, sharedContext))
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     *
     *
     * This function sends a message and returns immediately.  This isn't sufficient -- we
     * don't want the caller to latch a new frame until we're done with this one -- but we
     * can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     *
     *
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     */
    fun frameAvailable(st: SurfaceTexture) {
        synchronized(mReadyFence) {
            if (!mReady) {
                return
            }
        }

        st.getTransformMatrix(STMatrix)
        val timestamp = st.timestamp
        if (timestamp == 0L) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            //
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Timber.Forest.w("HEY: got SurfaceTexture with timestamp of zero")
            return
        }

        mHandler!!.sendMessage(
            mHandler!!.obtainMessage(
                MSG_FRAME_AVAILABLE,
                (timestamp shr 32).toInt(), timestamp.toInt(), STMatrix
            )
        )
    }

    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     *
     *
     */
    fun setTextureId(id: Int) {
        synchronized(mReadyFence) {
            if (!mReady) {
                return
            }
        }
        mHandler!!.sendMessage(mHandler!!.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null))
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     *
     *
     *
     * @see Thread.run
     */
    override fun run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare()
        synchronized(mReadyFence) {
            mHandler = EncoderHandler(this)
            mReady = true
            (mReadyFence as Object).notify()
        }
        Looper.loop()

        Timber.Forest.d("Encoder thread exiting")
        synchronized(mReadyFence) {
            mRunning = false
            mReady = mRunning
            mHandler = null
        }
    }


    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private class EncoderHandler(encoder: TextureMovieEncoder?) : Handler() {
        private val mWeakEncoder = WeakReference<TextureMovieEncoder?>(encoder)

        // runs on encoder thread
        override fun handleMessage(inputMessage: Message) {
            val what = inputMessage.what
            val obj = inputMessage.obj

            val encoder = mWeakEncoder.get()
            if (encoder == null) {
                Timber.Forest.w("EncoderHandler.handleMessage: encoder is null")
                return
            }

            when (what) {
                MSG_START_RECORDING -> encoder.handleStartRecording((obj as EncoderConfig?)!!)
                MSG_STOP_RECORDING -> encoder.handleStopRecording()
                MSG_FRAME_AVAILABLE -> {
                    val timestamp = ((inputMessage.arg1.toLong()) shl 32) or
                            ((inputMessage.arg2.toLong()) and 0xffffffffL)
                    encoder.handleFrameAvailable((obj as FloatArray?)!!, timestamp)
                }

                MSG_SET_TEXTURE_ID -> encoder.handleSetTexture(inputMessage.arg1)
                MSG_UPDATE_SHARED_CONTEXT -> encoder.handleUpdateSharedContext((inputMessage.obj as EGLContext?)!!)
                MSG_QUIT -> Looper.myLooper()!!.quit()
                else -> throw RuntimeException("Unhandled msg what=$what")
            }
        }
    }

    /**
     * Starts recording.
     */
    private fun handleStartRecording(config: EncoderConfig) {
        Timber.Forest.d("handleStartRecording %s", config.toString())
        mFrameNum = 0
        prepareEncoder(
            config.mEglContext, config.mWidth, config.mHeight, config.mBitRate,
            config.mOutputFile, config.mProgram, config.mMetadataFile
        )
    }

    /**
     * Handles notification of an available frame.
     *
     *
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     *
     *
     *
     * @param transform      The texture transform, from SurfaceTexture.
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    private fun handleFrameAvailable(transform: FloatArray, timestampNanos: Long) {
        if (VERBOSE) Timber.Forest.d("handleFrameAvailable tr=%f", transform[0])
        mVideoEncoder!!.drainEncoder(false)
        mFullScreen!!.drawFrame(mTextureId, transform)

        mInputWindowSurface!!.setPresentationTime(timestampNanos)
        mInputWindowSurface!!.swapBuffers()

        if (mLastFrameTimeNs != null) {
            val gapNs = timestampNanos - mLastFrameTimeNs!!
            mFrameRate = mFrameRate * 0.3f + (1000000000.0 / gapNs * 0.7).toFloat()
        }
        mLastFrameTimeNs = timestampNanos
    }

    /**
     * Handles a request to stop encoding.
     */
    private fun handleStopRecording() {
        Timber.Forest.d("handleStopRecording")
        mVideoEncoder!!.drainEncoder(true)
        releaseEncoder()
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    private fun handleSetTexture(id: Int) {
        mTextureId = id
    }

    /**
     * Tears down the EGL surface and context we've been using to feed the MediaCodec input
     * surface, and replaces it with a new one that shares with the new context.
     *
     *
     * This is useful if the old context we were sharing with went away (maybe a GLSurfaceView
     * that got torn down) and we need to hook up with the new one.
     */
    private fun handleUpdateSharedContext(newSharedContext: EGLContext) {
        Timber.Forest.d("handleUpdatedSharedContext %s", newSharedContext.toString())

        // Release the EGLSurface and EGLContext.
        mInputWindowSurface!!.releaseEglSurface()
        mFullScreen!!.release(false)
        mEglCore!!.release()

        // Create a new EGLContext and recreate the window surface.
        mEglCore = EglCore(newSharedContext, EglCore.Companion.FLAG_RECORDABLE)
        mEglCore?.let {
            mInputWindowSurface?.recreate(it)
            mInputWindowSurface?.makeCurrent()
        }

        // Create new programs and such for the new context.
        mFullScreen = FullFrameRect(
            Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT)
        )
    }

    private fun prepareEncoder(
        sharedContext: EGLContext?, width: Int, height: Int, bitRate: Int,
        outputFile: String?, program: Texture2dProgram, metaFile: String?
    ) {
        try {
            mVideoEncoder = outputFile?.let {
                VideoEncoderCore(
                    width, height, bitRate, it, metaFile
                )
            }
        } catch (ioe: IOException) {
            throw RuntimeException(ioe)
        }
        mEglCore = EglCore(sharedContext, EglCore.Companion.FLAG_RECORDABLE)
        mInputWindowSurface = mEglCore?.let {
            WindowSurface(it, mVideoEncoder!!.inputSurface, true)
        }
        mInputWindowSurface?.makeCurrent()

        if (program.programType == Texture2dProgram.ProgramType.TEXTURE_EXT_FILT_VIEW) {
            val newProgram = Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_FILT)
            newProgram.setKernel(program.kernel, program.colorAdjust)
            mFullScreen = FullFrameRect(newProgram)
        } else {
            mFullScreen = FullFrameRect(program)
        }
    }

    private fun releaseEncoder() {
        mVideoEncoder!!.release()
        if (mInputWindowSurface != null) {
            mInputWindowSurface!!.release()
            mInputWindowSurface = null
        }
        if (mFullScreen != null) {
            mFullScreen!!.release(false)
            mFullScreen = null
        }
        if (mEglCore != null) {
            mEglCore!!.release()
            mEglCore = null
        }
    }

    companion object {
        private const val VERBOSE = false

        private const val MSG_START_RECORDING = 0
        private const val MSG_STOP_RECORDING = 1
        private const val MSG_FRAME_AVAILABLE = 2
        private const val MSG_SET_TEXTURE_ID = 3
        private const val MSG_UPDATE_SHARED_CONTEXT = 4
        private const val MSG_QUIT = 5
    }
}
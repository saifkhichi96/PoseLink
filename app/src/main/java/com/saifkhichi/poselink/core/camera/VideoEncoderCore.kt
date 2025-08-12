package com.saifkhichi.poselink.core.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import timber.log.Timber
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException

/**
 * This class wraps up the core components used for surface-input video encoding.
 *
 *
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 *
 *
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
class VideoEncoderCore(
    width: Int, height: Int, bitRate: Int,
    outputFile: String, metaFile: String?
) {
    /**
     * Returns the encoder's input surface.
     */
    val inputSurface: Surface
    private var mMuxer: MediaMuxer?
    private var mEncoder: MediaCodec?
    private var mEncoderInExecutingState = false
    private val mBufferInfo: MediaCodec.BufferInfo
    private var mTrackIndex: Int
    private var mMuxerStarted: Boolean
    private var mFrameMetadataWriter: BufferedWriter? = null

    internal class TimePair(var sensorTimeMicros: Long?, var unixTimeMillis: Long) {
        override fun toString(): String {
            val delimiter = ","
            return sensorTimeMicros.toString() + "000" + delimiter + unixTimeMillis + "000000"
        }
    }

    private val mTimeArray: ArrayList<TimePair>
    val TIMEOUT_USEC: Int = 10000

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    init {
        mBufferInfo = MediaCodec.BufferInfo()

        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        if (VERBOSE) Timber.Forest.d("format: %s", format.toString())

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        mEncoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        this.inputSurface = mEncoder!!.createInputSurface()
        mEncoder!!.start()

        try {
            mEncoder!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC.toLong())
            mEncoderInExecutingState = true
        } catch (ise: IllegalStateException) {
            // This exception occurs with certain devices e.g., Nexus 9 API 22.
            Timber.Forest.e(ise)
            mEncoderInExecutingState = false
        }

        // Create a MediaMuxer. We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        mMuxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        mTrackIndex = -1
        mMuxerStarted = false

        try {
            mFrameMetadataWriter = BufferedWriter(FileWriter(metaFile, false))
        } catch (err: IOException) {
            Timber.Forest.e(err, "IOException in opening frameMetadataWriter.")
        }
        mTimeArray = ArrayList<TimePair>()
    }

    /**
     * Releases encoder resources.
     */
    fun release() {
        if (VERBOSE) Timber.Forest.d("releasing encoder objects")
        if (mEncoder != null) {
            mEncoder!!.stop()
            mEncoder!!.release()
            mEncoder = null
        }
        if (mMuxer != null) {
            mMuxer!!.stop()
            mMuxer!!.release()
            mMuxer = null
        }
        if (mFrameMetadataWriter != null) {
            try {
                val frameTimeHeader = "Frame timestamp[nanosec],Unix time[nanosec]\n"
                mFrameMetadataWriter!!.write(frameTimeHeader)
                for (value in mTimeArray) {
                    mFrameMetadataWriter!!.write(value.toString() + "\n")
                }
                mFrameMetadataWriter!!.flush()
                mFrameMetadataWriter!!.close()
            } catch (err: IOException) {
                Timber.Forest.e(err, "IOException in closing frameMetadataWriter.")
            }
            mFrameMetadataWriter = null
        }
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     *
     *
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     *
     *
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    fun drainEncoder(endOfStream: Boolean) {
        if (VERBOSE) Timber.Forest.d("drainEncoder(%b)", endOfStream)

        if (endOfStream) {
            if (VERBOSE) Timber.Forest.d("sending EOS to encoder")
            mEncoder!!.signalEndOfInputStream()
        }

        while (mEncoderInExecutingState) {
            val encoderStatus = mEncoder!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break // out of while
                } else {
                    if (VERBOSE) Timber.Forest.d("no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat = mEncoder!!.outputFormat
                Timber.Forest.d("encoder output format changed: %s", newFormat.toString())

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer!!.addTrack(newFormat)
                mMuxer!!.start()
                mMuxerStarted = true
            } else if (encoderStatus < 0) {
                Timber.Forest.w(
                    "unexpected result from encoder.dequeueOutputBuffer: %d",
                    encoderStatus
                )
                // let's ignore it
            } else {
                val encodedData = mEncoder!!.getOutputBuffer(encoderStatus)
                //                MediaFormat bufferFormat = mEncoder.getOutputFormat(encoderStatus);
                // bufferFormat is identical to newFormat
                if (encodedData == null) {
                    throw RuntimeException("encoderOutputBuffer " + encoderStatus + " was null")
                }

                if ((mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Timber.Forest.d("ignoring BUFFER_FLAG_CODEC_CONFIG")
                    mBufferInfo.size = 0
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw RuntimeException("muxer hasn't started")
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset)
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size)
                    mTimeArray.add(
                        TimePair(
                            mBufferInfo.presentationTimeUs,
                            System.currentTimeMillis()
                        )
                    )
                    mMuxer!!.writeSampleData(mTrackIndex, encodedData, mBufferInfo)
                    if (VERBOSE) {
                        Timber.Forest.d(
                            "sent %d bytes to muxer, ts=%d",
                            mBufferInfo.size, mBufferInfo.presentationTimeUs
                        )
                    }
                }

                mEncoder!!.releaseOutputBuffer(encoderStatus, false)

                if ((mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Timber.Forest.w("reached end of stream unexpectedly")
                    } else {
                        if (VERBOSE) Timber.Forest.d("end of stream reached")
                    }
                    break // out of while
                }
            }
        }
    }

    companion object {
        private const val VERBOSE = false

        private const val MIME_TYPE = "video/avc" // H.264 Advanced Video Coding
        const val FRAME_RATE: Int = 30 // 30fps
        private const val IFRAME_INTERVAL = 1 // seconds between I-frames
    }
}
package com.saifkhichi.poselink.streaming

interface JpegFrameProvider {
    /** Latest JPEG frame, or null if none yet. Thread-safe. */
    fun latestJpeg(): ByteArray?
}


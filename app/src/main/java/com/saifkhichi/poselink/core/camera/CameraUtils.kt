package com.saifkhichi.poselink.core.camera

import android.util.Size
import timber.log.Timber
import java.lang.Long
import java.util.Collections
import kotlin.Array
import kotlin.Comparator
import kotlin.Int

/**
 * Camera-related utility functions.
 */
object CameraUtils {
    private const val BPP = 0.25f

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    fun chooseVideoSize(
        choices: Array<Size>, wScale: Int, hScale: Int, maxWidth: Int
    ): Size? {
        for (size in choices) {
            if (size.width == size.height * wScale / hScale &&
                size.width <= maxWidth
            ) {
                return size
            }
        }
        Timber.e("Couldn't find any suitable video size")
        return choices[choices.size - 1]
    }

    /**
     * Given `choices` of `Size`s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int, aspectRatio: Size): Size? {
        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough: MutableList<Size?> = ArrayList()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.height == option.width * h / w && option.width >= width && option.height >= height) {
                bigEnough.add(option)
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.isNotEmpty()) {
            return Collections.min<Size?>(bigEnough, CompareSizesByArea())
        } else {
            Timber.e("Couldn't find any suitable preview size")
            return choices[0]
        }
    }

    fun calcBitRate(width: Int, height: Int, frameRate: Int): Int {
        val bitrate = (BPP * frameRate * width * height).toInt()
        Timber.i("bitrate=%d", bitrate)
        return bitrate
    }

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(p0: Size, p1: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum(
                p0.width.toLong() * p0.height -
                        p1.width.toLong() * p1.height
            )
        }
    }
}

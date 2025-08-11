package io.a3dv.VIRec

import android.annotation.TargetApi
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Size
import android.util.SizeF
import timber.log.Timber

// estimate focal length, i.e., imaging distance in pixels, using all sorts of info

class FocalLengthHelper {
    private var mIntrinsic: FloatArray? = null
    private var mFocalLength: Float? = null
    private var mFocusDistance: Float? = null
    private var mPhysicalSize: SizeF? = null
    private var mPixelArraySize: Size? = null
    private var mPreCorrectionSize: Rect? =
        null // This rectangle is defined relative to full pixel array; (0,0) is the top-left of the full pixel array
    private var mActiveSize: Rect? =
        null // This rectangle is defined relative to the full pixel array; (0,0) is the top-left of the full pixel array,
    private var mCropRegion: Rect? =
        null // Its The coordinate system is defined relative to the active array rectangle given in this field, with (0, 0) being the top-left of this rectangle.
    private var mImageSize: Size? = null

    fun setLensParams(result: CameraCharacteristics) {
        setLensParams21(result)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setLensParams23(result)
        }
    }

    fun setmCropRegion(mCropRegion: Rect) {
        this.mCropRegion = mCropRegion
    }

    fun setmFocalLength(mFocalLength: Float?) {
        this.mFocalLength = mFocalLength
    }

    fun setmFocusDistance(mFocusDistance: Float?) {
        this.mFocusDistance = mFocusDistance
    }

    fun setmImageSize(mImageSize: Size) {
        this.mImageSize = mImageSize
    }

    val focalLengthPixel: SizeF
        /**
         * compute the focal length in pixels.
         * First it tries to use values read from LENS_INTRINSIC_CALIBRATION, if not available,
         * it will compute focal length based on an empirical model.
         *
         * focus distance is the inverse of the distance between the lens and the subject,
         * assuming LENS_INFO_FOCUS_DISTANCE_CALIBRATION is APPROXIMATE or CALIBRATED.
         * see https://stackoverflow.com/questions/60394282/unit-of-camera2-lens-focus-distance
         * i is the distance between the imaging sensor and the lens.
         * Recall 1/focal_length = focus_distance + 1/i.
         * Because focal_length is very small say 3 mm,
         * focus_distance is often comparatively small, say 5 1/meter,
         * i is often very close to the physical focal length, say 3 mm.
         *
         * see: https://source.android.com/devices/camera/camera3_crop_reprocess.html
         * https://stackoverflow.com/questions/39965408/what-is-the-android-camera2-api-equivalent-of-camera-parameters-gethorizontalvie
         *
         * @return (focal length along x, focal length along y) in pixels
         */
        get() {
            if (mIntrinsic != null && mIntrinsic!![0] > 1.0) {
                Timber.d("Focal length set as (%f, %f)", mIntrinsic!![0], mIntrinsic!![1])
                return SizeF(mIntrinsic!![0], mIntrinsic!![1])
            }

            if (mFocalLength != null) {
                val imageDistance: Float // mm
                if (mFocusDistance == null || mFocusDistance == 0f) {
                    imageDistance = mFocalLength!!
                } else {
                    imageDistance = 1000f / (1000f / mFocalLength!! - mFocusDistance!!)
                }
                // ignore the effect of distortion on the active array coordinates
                val crop_aspect = mCropRegion!!.width().toFloat() /
                        (mCropRegion!!.height().toFloat())
                val image_aspect = mImageSize!!.width.toFloat() /
                        (mImageSize!!.height.toFloat())
                val f_image_pixel: Float
                if (image_aspect >= crop_aspect) {
                    val scale =
                        mImageSize!!.width.toFloat() / (mCropRegion!!.width().toFloat())
                    f_image_pixel = scale * imageDistance * mPixelArraySize!!.width /
                            mPhysicalSize!!.width
                } else {
                    val scale =
                        mImageSize!!.height.toFloat() / (mCropRegion!!.height().toFloat())
                    f_image_pixel = scale * imageDistance * mPixelArraySize!!.height /
                            mPhysicalSize!!.height
                }
                return SizeF(f_image_pixel, f_image_pixel)
            }
            return SizeF(1.0f, 1.0f)
        }

    @TargetApi(23)
    private fun setLensParams23(result: CameraCharacteristics) {
        mIntrinsic = result.get<FloatArray?>(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
        if (mIntrinsic != null) Timber.d(
            "char lens intrinsics fx %f fy %f cx %f cy %f s %f",
            mIntrinsic!![0], mIntrinsic!![1], mIntrinsic!![2], mIntrinsic!![3], mIntrinsic!![4]
        )
        mPreCorrectionSize =
            result.get<Rect?>(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
        if (mPreCorrectionSize != null) Timber.d(
            "Precorrection rect %s",
            mPreCorrectionSize.toString()
        )
    }

    private fun setLensParams21(result: CameraCharacteristics) {
        mPhysicalSize = result.get<SizeF?>(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (mPhysicalSize != null) Timber.d("Physical size %s", mPhysicalSize.toString())
        mPixelArraySize = result.get<Size?>(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        if (mPixelArraySize != null) Timber.d("Pixel array size %s", mPixelArraySize.toString())
        mActiveSize = result.get<Rect?>(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (mActiveSize != null) Timber.d("Active rect %s", mActiveSize.toString())
    }

    companion object {
        private const val TAG = "FocalLengthHelper"
    }
}

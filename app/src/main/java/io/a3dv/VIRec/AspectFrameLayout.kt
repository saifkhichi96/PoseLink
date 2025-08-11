package io.a3dv.VIRec

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import timber.log.Timber
import kotlin.math.abs

/**
 * Layout that adjusts to maintain a specific aspect ratio.
 */
class AspectFrameLayout : FrameLayout {
    private var mTargetAspect = -1.0 // initially use default window size

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    /**
     * Sets the desired aspect ratio. The value is `width / height`.
     */
    fun setAspectRatio(aspectRatio: Double) {
        require(!(aspectRatio < 0))
        Timber.d("Setting aspect ratio to %f (was %f)", aspectRatio, mTargetAspect)
        if (mTargetAspect != aspectRatio) {
            mTargetAspect = aspectRatio
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Target aspect ratio will be < 0 if it hasn't been set yet.  In that case,
        // we just use whatever we've been handed.
        var widthMeasureSpec = widthMeasureSpec
        var heightMeasureSpec = heightMeasureSpec
        if (mTargetAspect > 0) {
            var initialWidth = MeasureSpec.getSize(widthMeasureSpec)
            var initialHeight = MeasureSpec.getSize(heightMeasureSpec)

            // factor the padding out
            val horizPadding = paddingLeft + paddingRight
            val vertPadding = paddingTop + paddingBottom
            initialWidth -= horizPadding
            initialHeight -= vertPadding

            val viewAspectRatio = initialWidth.toDouble() / initialHeight
            val aspectDiff = mTargetAspect / viewAspectRatio - 1

            if (abs(aspectDiff) >= 0.01) {
                if (aspectDiff > 0) {
                    // limited by narrow width; restrict height
                    initialHeight = (initialWidth / mTargetAspect).toInt()
                } else {
                    // limited by short height; restrict width
                    initialWidth = (initialHeight * mTargetAspect).toInt()
                }
                initialWidth += horizPadding
                initialHeight += vertPadding
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(initialWidth, MeasureSpec.EXACTLY)
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(initialHeight, MeasureSpec.EXACTLY)
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

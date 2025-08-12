package com.saifkhichi.poselink.app.camera

import android.app.Activity
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Message
import com.saifkhichi.poselink.core.camera.ManualFocusConfig
import timber.log.Timber
import java.lang.ref.WeakReference


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

        Timber.Forest.d("CameraHandler [%s]: what=%d", this.toString(), what)

        val activity = mWeakActivity.get()
        if (activity == null) {
            Timber.Forest.w("CameraHandler.handleMessage: activity is null")
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
                val camera2proxy = (activity as CameraActivityBase).getCameraProxy()
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

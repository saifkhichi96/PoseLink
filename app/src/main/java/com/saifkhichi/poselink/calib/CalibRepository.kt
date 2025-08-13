// calib/CalibRepository.kt
package com.saifkhichi.poselink.calib

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import org.json.JSONObject

class CalibRepository(private val ctx: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)

    fun saveCalibration(json: JSONObject) {
        prefs.edit().putString(KEY_CALIB_JSON, json.toString()).apply()
    }

    fun loadCalibrationRaw(): String? = prefs.getString(KEY_CALIB_JSON, null)

    fun metaJson(): JSONObject = JSONObject().apply {
        put("device", Build.MODEL ?: "unknown")
        put("manufacturer", Build.MANUFACTURER ?: "unknown")
        put("android_sdk", Build.VERSION.SDK_INT)
        put("timestamp_ms", System.currentTimeMillis())
    }

    companion object {
        const val KEY_CALIB_JSON = "poselink.calibration.json"
    }
}
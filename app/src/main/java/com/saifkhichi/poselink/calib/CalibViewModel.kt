// calib/CalibViewModel.kt
package com.saifkhichi.poselink.calib

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.*

import com.saifkhichi.poselink.R
import org.json.JSONObject

class CalibViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CalibRepository(app)

    // Wizard UI state
    private val _stepTitle = MutableLiveData("Welcome")
    val stepTitle: LiveData<String> = _stepTitle

    private val _enableNext = MutableLiveData(false)
    val enableNext: LiveData<Boolean> = _enableNext

    private val _setNextText = MutableLiveData<Int>(R.string.next)
    val setNextText: LiveData<Int> = _setNextText

    private val _setBackVisible = MutableLiveData<Boolean>(false)
    val setBackVisible: LiveData<Boolean> = _setBackVisible

    fun ui(stepTitle: String, enableNext: Boolean, @StringRes nextText: Int, backVisible: Boolean) {
        _stepTitle.value = stepTitle
        _enableNext.value = enableNext
        _setNextText.value = nextText
        _setBackVisible.value = backVisible
    }

    // Calibration accumulation
    var accCal: AccelCal? = null
    var gyroCal: GyroCal? = null
    var magCal: MagCal? = null

    fun save() {
        val js = JSONObject().apply {
            put("acc", accCal?.toJson() ?: JSONObject.NULL)
            put("gyro", gyroCal?.toJson() ?: JSONObject.NULL)
            put("mag", magCal?.toJson() ?: JSONObject.NULL)
            put("meta", repo.metaJson())
        }
        repo.saveCalibration(js)
    }

    fun lastSavedJson(): String? = repo.loadCalibrationRaw()
}

// Simple data holders
data class AccelCal(val A: Array<DoubleArray>, val b: DoubleArray, val sigma: DoubleArray) {
    fun toJson() = JSONObject().apply {
        put("A", A.map { it.toList() })
        put("b", b.toList())
        put("sigma", sigma.toList())
    }
}

data class GyroCal(
    val bias0: DoubleArray,
    val sigma: DoubleArray,
    val ARW: DoubleArray? = null,
    val BI: DoubleArray? = null
) {
    fun toJson() = JSONObject().apply {
        put("bias0", bias0.toList())
        put("sigma", sigma.toList())
        if (ARW != null) put("ARW", ARW.toList())
        if (BI  != null) put("BI",  BI.toList())
    }
}

data class MagCal(val S: Array<DoubleArray>, val h: DoubleArray) {
    fun toJson() = JSONObject().apply {
        put("S", S.map { it.toList() })
        put("h", h.toList())
    }
}
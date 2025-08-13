// calib/ui/AccelFragment.kt
package com.saifkhichi.poselink.calib.ui

import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.calib.*
import com.saifkhichi.poselink.calib.math.AccelSixFace
import com.saifkhichi.poselink.calib.math.SixFaceSamples
import kotlin.math.abs

class AccelFragment : Fragment() {
    private val vm: CalibViewModel by activityViewModels()
    private lateinit var sampler: SensorSampler

    private lateinit var txtStep: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnCapture: MaterialButton

    private val faces = arrayOf("+Z up", "-Z up", "+X up", "-X up", "+Y up", "-Y up")
    private var faceIndex = 0
    private val samples = SixFaceSamples()

    private var timer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_accel, c, false)
        txtStep = v.findViewById(R.id.txtStep)
        progress = v.findViewById(R.id.progress)
        btnCapture = v.findViewById(R.id.btnCapture)
        sampler = SensorSampler(requireContext())

        btnCapture.setOnClickListener { captureFace() }
        return v
    }

    override fun onResume() {
        super.onResume()
        vm.ui(getString(R.string.calib_accel_title), enableNext = false, nextText = R.string.next, backVisible = true)
        sampler.start()
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        sampler.stop()
        timer?.cancel()
    }

    private fun updateUi() {
        txtStep.text = getString(R.string.accel_place_face, faces[faceIndex])
        progress.max = faces.size
        progress.progress = faceIndex
        btnCapture.isEnabled = true
    }

    private fun captureFace() {
        btnCapture.isEnabled = false
        txtStep.text = getString(R.string.hold_still_collecting)

        val collected = mutableListOf<FloatArray>()
        timer?.cancel()
        timer = object : CountDownTimer(2500, 20) {
            override fun onTick(ms: Long) {
                sampler.lastAcc?.let { a ->
                    // reject if |‖a‖-g| large (not still)
                    val n = SensorSampler.norm3(a)
                    if (abs(n - AccelSixFace.G) < 0.2) collected += a.clone()
                }
            }
            override fun onFinish() {
                if (collected.size < 40) {
                    txtStep.text = getString(R.string.too_noisy_try_again)
                    btnCapture.isEnabled = true
                    return
                }
                when (faceIndex) {
                    0 -> samples.zp.addAll(collected)
                    1 -> samples.zn.addAll(collected)
                    2 -> samples.xp.addAll(collected)
                    3 -> samples.xn.addAll(collected)
                    4 -> samples.yp.addAll(collected)
                    5 -> samples.yn.addAll(collected)
                }
                faceIndex++
                if (faceIndex >= faces.size) {
                    finalizeAccel()
                } else {
                    txtStep.text = getString(R.string.face_done_next, faces[faceIndex])
                    btnCapture.isEnabled = true
                    updateUi()
                }
            }
        }.start()
    }

    private fun finalizeAccel() {
        val (b, s) = AccelSixFace.solveDiagonal(samples)
        val sigma = doubleArrayOf(0.03, 0.03, 0.03)
        val A = arrayOf(doubleArrayOf(s[0],0.0,0.0), doubleArrayOf(0.0,s[1],0.0), doubleArrayOf(0.0,0.0,s[2]))
        vm.accCal = AccelCal(A = A, b = b, sigma = sigma)
        txtStep.text = getString(R.string.accel_done)
        progress.progress = progress.max
        vm.ui(getString(R.string.calib_accel_title), enableNext = true, nextText = R.string.next, backVisible = true)
    }
}
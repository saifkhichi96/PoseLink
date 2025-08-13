// calib/ui/GyroFragment.kt
package com.saifkhichi.poselink.calib.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.calib.*

class GyroFragment : Fragment() {
    private val vm: CalibViewModel by activityViewModels()
    private lateinit var sampler: SensorSampler
    private lateinit var txt: TextView
    private lateinit var progress: ProgressBar
    private var timer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        inflater.inflate(R.layout.fragment_gyro, c, false).apply {
            txt = findViewById(R.id.txt)
            progress = findViewById(R.id.progress)
            sampler = SensorSampler(requireContext())
        }

    override fun onResume() {
        super.onResume()
        vm.ui(getString(R.string.calib_gyro_title), enableNext = false, nextText = R.string.next, backVisible = true)
        sampler.start()
        runHold()
    }

    override fun onPause() {
        super.onPause(); sampler.stop(); timer?.cancel()
    }

    private fun runHold() {
        txt.text = getString(R.string.hold_still_collecting)
        val ws = mutableListOf<FloatArray>()
        progress.max = 3000; progress.progress = 0
        timer?.cancel()
        timer = object : CountDownTimer(3000, 10) {
            override fun onTick(ms: Long) {
                sampler.lastGyro?.let { ws += it.clone() }
                progress.progress = progress.progress + 10
            }
            override fun onFinish() {
                if (ws.isEmpty()) { txt.text = getString(R.string.too_noisy_try_again); return }
                val b = DoubleArray(3)
                val s2 = DoubleArray(3)
                for (i in 0..2) {
                    b[i] = ws.map { it[i].toDouble() }.average()
                    s2[i] = ws.map { val d = it[i] - b[i]; d*d }.average()
                }
                val sigma = DoubleArray(3) { kotlin.math.sqrt(s2[it]) }
                vm.gyroCal = GyroCal(bias0 = b, sigma = sigma)
                txt.text = getString(R.string.gyro_done)
                vm.ui(getString(R.string.calib_gyro_title), enableNext = true, nextText = R.string.next, backVisible = true)
            }
        }.start()
    }
}
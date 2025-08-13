// calib/ui/MagFragment.kt
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
import com.saifkhichi.poselink.calib.math.MagCal

class MagFragment : Fragment() {
    private val vm: CalibViewModel by activityViewModels()
    private lateinit var sampler: SensorSampler
    private lateinit var txt: TextView
    private lateinit var progress: ProgressBar
    private var timer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        inflater.inflate(R.layout.fragment_mag, c, false).apply {
            txt = findViewById(R.id.txt)
            progress = findViewById(R.id.progress)
            sampler = SensorSampler(requireContext())
        }

    override fun onResume() {
        super.onResume()
        vm.ui(getString(R.string.calib_mag_title), enableNext = false, nextText = R.string.next, backVisible = true)
        sampler.start()
        tumble()
    }

    override fun onPause() { super.onPause(); sampler.stop(); timer?.cancel() }

    private fun tumble() {
        txt.text = getString(R.string.mag_tumble_collecting)
        val acc = MagCal.Accum()
        progress.max = 60000; progress.progress = 0
        timer?.cancel()
        timer = object : CountDownTimer(60000, 20) {
            override fun onTick(ms: Long) {
                sampler.lastMag?.let { MagCal.accumulate(acc, it) }
                progress.progress = (60000 - ms).toInt()
            }
            override fun onFinish() {
                val (h, S) = MagCal.solve(acc)
                vm.magCal = MagCal(S = S, h = h)
                txt.text = getString(R.string.mag_done)
                vm.ui(getString(R.string.calib_mag_title), enableNext = true, nextText = R.string.next, backVisible = true)
            }
        }.start()
    }
}
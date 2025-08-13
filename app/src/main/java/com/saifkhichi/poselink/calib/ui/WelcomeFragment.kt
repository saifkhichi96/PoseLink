// calib/ui/WelcomeFragment.kt
package com.saifkhichi.poselink.calib.ui

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.calib.CalibViewModel

class WelcomeFragment : Fragment() {
    private val vm: CalibViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        inflater.inflate(R.layout.fragment_welcome, c, false)

    override fun onResume() {
        super.onResume()
        vm.ui(
            stepTitle = getString(R.string.calib_welcome),
            enableNext = true,
            nextText = R.string.start,
            backVisible = false
        )
    }
}
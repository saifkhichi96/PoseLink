// calib/ui/SummaryFragment.kt
package com.saifkhichi.poselink.calib.ui

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.calib.CalibViewModel

class SummaryFragment : Fragment() {
    private val vm: CalibViewModel by activityViewModels()
    private lateinit var txt: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnRecal: MaterialButton

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        inflater.inflate(R.layout.fragment_summary, c, false).apply {
            txt = findViewById(R.id.txtJson)
            btnSave = findViewById(R.id.btnSave)
            btnRecal = findViewById(R.id.btnRecal)
            btnSave.setOnClickListener {
                vm.save()
                txt.append("\n\nSaved.")
            }
            btnRecal.setOnClickListener {
                requireActivity().finish()
                startActivity(requireActivity().intent) // restart wizard
            }
        }

    override fun onResume() {
        super.onResume()
        vm.ui(getString(R.string.calib_summary_title), enableNext = true, nextText = R.string.finish, backVisible = true)
        val json = vm.lastSavedJson()
        txt.text = buildString {
            appendLine("Preview (will save on 'Save'):")
            appendLine("-------------------------------")
            appendLine(
                """{
  "acc": ${vm.accCal?.toJson()?.toString(2) ?: "null"},
  "gyro": ${vm.gyroCal?.toJson()?.toString(2) ?: "null"},
  "mag": ${vm.magCal?.toJson()?.toString(2) ?: "null"}
}"""
            )
            if (json != null) {
                appendLine("\nExisting saved calibration:")
                appendLine(json)
            }
        }
    }
}
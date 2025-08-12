package com.saifkhichi.poselink.app.sensors

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.core.sensors.ImuViewContent.SingleAxis
import com.saifkhichi.poselink.databinding.MenuIntentActivityBinding

class ImuViewerActivity : AppCompatActivity(), ImuViewFragment.OnListFragmentInteractionListener {
    private lateinit var binding: MenuIntentActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MenuIntentActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.menu_intent, ImuViewFragment())
                .commit()
        }
    }

    override fun onListFragmentInteraction(item: SingleAxis?) {
    }
}
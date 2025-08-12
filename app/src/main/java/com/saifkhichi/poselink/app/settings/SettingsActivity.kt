package com.saifkhichi.poselink.app.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.databinding.MenuIntentActivityBinding

class SettingsActivity : AppCompatActivity(), SettingsFragment.OnFragmentInteractionListener {
    private lateinit var binding: MenuIntentActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MenuIntentActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.menu_intent, SettingsFragment())
                .commit()
        }
    }
}
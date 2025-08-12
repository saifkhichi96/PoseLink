package com.saifkhichi.poselink.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.app.SettingsFragment.OnFragmentInteractionListener
import com.saifkhichi.poselink.databinding.MenuIntentActivityBinding

class SettingsActivity : AppCompatActivity(), OnFragmentInteractionListener {
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
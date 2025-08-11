package io.a3dv.VIRec

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.a3dv.VIRec.SettingsFragment.OnFragmentInteractionListener

class SettingsActivity : AppCompatActivity(), OnFragmentInteractionListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.menu_intent_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.menu_intent, SettingsFragment())
                .commit()
        }
    }
}
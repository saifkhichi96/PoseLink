package io.a3dv.VIRec

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.a3dv.VIRec.ImuViewContent.SingleAxis
import io.a3dv.VIRec.ImuViewFragment.OnListFragmentInteractionListener

class ImuViewerActivity : AppCompatActivity(), OnListFragmentInteractionListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.menu_intent_activity)
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
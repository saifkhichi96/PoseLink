package com.saifkhichi.poselink.app

import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.databinding.AboutActivityBinding

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = AboutActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val hyperlink = binding.linkTextView
        val linkText = getResources().getString(R.string.link_foreword)
        val text = com.saifkhichi.poselink.storage.FileHelper.fromHtml(
            linkText + " " +
                    "<a href='https://github.com/A3DV/VIRec'>GitHub</a>."
        )
        hyperlink.movementMethod = LinkMovementMethod.getInstance()
        hyperlink.text = text

        // val versionName = findViewById<TextView>(R.id.versionText)
        // versionName.setText(getString(R.string.versionName, BuildConfig.VERSION_NAME))
    }
}

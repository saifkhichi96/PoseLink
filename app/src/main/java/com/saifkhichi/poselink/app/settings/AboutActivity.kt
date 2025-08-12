package com.saifkhichi.poselink.app.settings

import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import com.saifkhichi.poselink.R
import com.saifkhichi.poselink.databinding.AboutActivityBinding
import com.saifkhichi.poselink.storage.FileHelper

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = AboutActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val hyperlink = binding.linkTextView
        val linkText = getResources().getString(R.string.link_foreword)
        val text = FileHelper.fromHtml(
            linkText + " " +
                    "<a href='https://github.com/saifkhichi96/PoseLink'>GitHub</a>."
        )
        hyperlink.movementMethod = LinkMovementMethod.getInstance()
        hyperlink.text = text

        val versionName = binding.versionText
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        versionName.text = getString(
            R.string.versionName,
            packageInfo.versionName
        )
    }
}

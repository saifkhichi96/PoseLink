package io.a3dv.VIRec

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about_activity)

        val hyperlink = findViewById<TextView>(R.id.linkTextView)
        val linkText = getResources().getString(R.string.link_foreword)
        val text = FileHelper.fromHtml(
            linkText + " " +
                    "<a href='https://github.com/A3DV/VIRec'>GitHub</a>."
        )
        hyperlink.movementMethod = LinkMovementMethod.getInstance()
        hyperlink.text = text

        // val versionName = findViewById<TextView>(R.id.versionText)
        // versionName.setText(getString(R.string.versionName, BuildConfig.VERSION_NAME))
    }
}

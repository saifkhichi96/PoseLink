package com.saifkhichi.poselink.storage

import android.os.Build
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

object FileHelper {
    fun createBufferedWriter(filename: String): BufferedWriter? {
        val dest = File(filename)
        try {
            if (!dest.exists()) dest.createNewFile()
            return BufferedWriter(FileWriter(dest, true))
        } catch (ioe: IOException) {
            Timber.Forest.e(ioe)
        }
        return null
    }

    fun closeBufferedWriter(writer: BufferedWriter) {
        try {
            writer.flush()
            writer.close()
        } catch (ioe: IOException) {
            Timber.Forest.e(ioe)
        }
    }

    // https://stackoverflow.com/questions/37904739/html-fromhtml-deprecated-in-android-n
    @Suppress("deprecation")
    fun fromHtml(html: String?): Spanned? {
        if (html == null) {
            // return an empty spannable if the html is null
            return SpannableString("")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // FROM_HTML_MODE_LEGACY is the behaviour that was used for versions below android N
            // we are using this flag to give a consistent behaviour
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            return Html.fromHtml(html)
        }
    }
}
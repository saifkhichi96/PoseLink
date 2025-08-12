package com.saifkhichi.poselink.app.camera

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionManager(
    private val context: Context,
) {

    data class SessionPaths(val outputDir: String) {
        val videoOutputFile1 = outputDir + File.separator + "movie1.mp4"
        val videoOutputFile2 = outputDir + File.separator + "movie2.mp4"
        val videoTimestampFile1 = outputDir + File.separator + "frame_timestamps1.txt"
        val videoTimestampFile2 = outputDir + File.separator + "frame_timestamps2.txt"
        val videoMetaFile1 = outputDir + File.separator + "movie_metadata1.csv"
        val videoMetaFile2 = outputDir + File.separator + "movie_metadata2.csv"
        val inertialFile = outputDir + File.separator + "gyro_accel.csv"
        val edgeEpochFile = outputDir + File.separator + "edge_epochs.txt"
    }

    private var active = false

    fun isActive(): Boolean {
        return active
    }

    fun setActive(active: Boolean) {
        this.active = active
    }

    fun toggleEnabled() {
        active = !active
        if (active) {
            val paths = SessionPaths(createSessionDir())
            onSessionFilesReady?.invoke(paths)
        } else {
            onSessionEnded?.invoke()
        }
    }

    var onSessionFilesReady: ((SessionPaths) -> Unit)? = null

    var onSessionEnded: (() -> Unit)? = null

    private fun createSessionDir(): String {
        val dateFormat = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
        val folderName = dateFormat.format(Date())

        val dataDir = context.getExternalFilesDir(
            Environment.getDataDirectory().absolutePath
        )!!.absolutePath
        val outputDir = buildString {
            append(dataDir)
            append(File.separator)
            append(folderName)
        }

        (File(outputDir)).mkdirs()
        return outputDir
    }

}
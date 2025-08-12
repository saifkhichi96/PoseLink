package com.saifkhichi.poselink.app.recordings

import java.io.File

data class RecordingSession(
    val name: String,
    val path: File,
    val files: List<File>
)
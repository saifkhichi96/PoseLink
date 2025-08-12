package com.saifkhichi.poselink.app.recordings

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.saifkhichi.poselink.R
import java.io.File
import java.net.URLConnection

class FileAdapter(
    private val ctx: Context,
    private val files: List<File>
) : RecyclerView.Adapter<FileAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: CardView = v.findViewById(R.id.file_card)
        val name: MaterialTextView = v.findViewById(R.id.file_name)
        val size: MaterialTextView = v.findViewById(R.id.file_size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return VH(v)
    }

    private fun getSizeString(size: Long): String {
        return if (size < 1024) {
            "$size B"
        } else if (size < 1024 * 1024) {
            "${size / 1024} KB"
        } else if (size < 1024 * 1024 * 1024) {
            "${size / (1024 * 1024)} MB"
        } else {
            "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val f = files[pos]
        h.name.text = f.name
        h.size.text = getSizeString(f.length())
        h.card.setOnClickListener {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(f))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Open with"))
        }
    }

    override fun getItemCount() = files.size

    private fun getMimeType(file: File): String {
        return URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
    }
}
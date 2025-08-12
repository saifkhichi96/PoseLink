package com.saifkhichi.poselink.app.recordings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.saifkhichi.poselink.R

class SessionAdapter(
    private val ctx: Context,
    private val sessions: List<RecordingSession>
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: CardView = v.findViewById(R.id.session_card)
        val title: MaterialTextView = v.findViewById(R.id.session_title)
        val fileList: RecyclerView = v.findViewById(R.id.file_list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val session = sessions[pos]
        h.title.text = session.name
        h.fileList.layoutManager = LinearLayoutManager(ctx)
        h.fileList.adapter = FileAdapter(ctx, session.files)
    }

    override fun getItemCount() = sessions.size
}
package com.saifkhichi.poselink.app.recordings

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.saifkhichi.poselink.R

class RecordingBrowserFragment : Fragment() {

    private lateinit var adapter: SessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_recording_browser, container, false)
        val recycler = view.findViewById<RecyclerView>(R.id.recording_list)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = SessionAdapter(requireContext(), scanSessions())
        recycler.adapter = adapter
        return view
    }

    private fun scanSessions(): List<RecordingSession> {
        val baseDir =
            requireContext().getExternalFilesDir(Environment.getDataDirectory().absolutePath)
                ?: return emptyList()
        return baseDir.listFiles { file -> file.isDirectory }?.map { dir ->
            RecordingSession(
                name = dir.name,
                path = dir,
                files = dir.listFiles()?.toList() ?: emptyList()
            )
        }?.sortedByDescending { it.name } ?: emptyList()
    }
}
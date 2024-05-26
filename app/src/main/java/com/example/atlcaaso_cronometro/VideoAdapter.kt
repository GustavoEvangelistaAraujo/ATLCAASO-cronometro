package com.example.atlcaaso_cronometro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class VideoAdapter(private val videoDirectory: File) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val videoFiles: Array<File> = videoDirectory.listFiles() ?: arrayOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val videoFile = videoFiles[position]
        holder.videoTitle.text = videoFile.name

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", videoFile)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setDataAndType(uri, "video/mp4")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return videoFiles.size
    }

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val videoTitle: TextView = itemView.findViewById(R.id.videoTitle)
    }
}

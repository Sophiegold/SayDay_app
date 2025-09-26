package com.sophiegold.app_sayday

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class TapeLogoAdapter(
    private val logos: List<Int>,
    private val onLogoClick: (Int) -> Unit
) : RecyclerView.Adapter<TapeLogoAdapter.LogoViewHolder>() {

    inner class LogoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val logoImage: ImageView = itemView.findViewById(R.id.logoImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tape_logo, parent, false)
        return LogoViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogoViewHolder, position: Int) {
        val logoRes = logos[position]
        holder.logoImage.setImageResource(logoRes)
        holder.logoImage.setOnClickListener {
            onLogoClick(logoRes)
        }
    }

    override fun getItemCount(): Int = logos.size
}
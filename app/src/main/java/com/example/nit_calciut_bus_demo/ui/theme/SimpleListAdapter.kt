package com.example.nit_calciut_bus_demo.ui.theme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SimpleListAdapter(
    private val items: List<Pair<String, String>>, // title, subtitle
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<SimpleListAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val subtitle: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (title, subtitle) = items[position]
        holder.title.text = title
        holder.subtitle.text = subtitle
        holder.itemView.setOnClickListener { onClick(position) }
    }

    override fun getItemCount(): Int = items.size
}

package com.example.nit_calciut_bus_demo.ui.theme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nit_calciut_bus_demo.R

class StepsAdapter : RecyclerView.Adapter<StepsAdapter.StepViewHolder>() {

    private var items: List<String> = emptyList()

    class StepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stepText: TextView = view.findViewById(R.id.stepText)
    }

    fun submitList(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_step, parent, false)
        return StepViewHolder(v)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.stepText.text = "${position + 1}. ${items[position]}"
    }

    override fun getItemCount(): Int = items.size
}

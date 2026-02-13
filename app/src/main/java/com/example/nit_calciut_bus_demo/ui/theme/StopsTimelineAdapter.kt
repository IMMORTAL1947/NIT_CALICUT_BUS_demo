package com.example.nit_calciut_bus_demo.ui.theme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nit_calciut_bus_demo.R

class StopsTimelineAdapter(
    private val onStopClick: ((StopState) -> Unit)? = null
) : ListAdapter<StopState, StopsTimelineAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<StopState>() {
            override fun areItemsTheSame(oldItem: StopState, newItem: StopState): Boolean =
                oldItem.index == newItem.index

            override fun areContentsTheSame(oldItem: StopState, newItem: StopState): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stop_timeline, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onStopClick?.invoke(item)
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stopName: TextView = itemView.findViewById(R.id.stopNameText)
        private val stopTime: TextView = itemView.findViewById(R.id.stopTimeText)
        private val statusDot: View = itemView.findViewById(R.id.statusDot)
        private val connector: View = itemView.findViewById(R.id.timelineLine)
        private val busIndicator: ImageView = itemView.findViewById(R.id.busIndicator)

        fun bind(state: StopState) {
            stopName.text = state.name
            stopTime.text = state.scheduledTime ?: ""
            stopTime.visibility = if (state.scheduledTime.isNullOrEmpty()) View.GONE else View.VISIBLE

            val ctx = itemView.context
            when (state.status) {
                StopStatus.COMPLETED -> {
                    statusDot.setBackgroundResource(R.drawable.circle_completed)
                    connector.setBackgroundColor(0xFFB0BEC5.toInt())
                }
                StopStatus.CURRENT -> {
                    statusDot.setBackgroundResource(R.drawable.circle_current)
                    connector.setBackgroundColor(0xFF64B5F6.toInt())
                }
                StopStatus.UPCOMING -> {
                    statusDot.setBackgroundResource(R.drawable.circle_upcoming)
                    connector.setBackgroundColor(0xFFE0E0E0.toInt())
                }
            }

            busIndicator.visibility = if (state.showBusHere) View.VISIBLE else View.INVISIBLE
        }
    }
}

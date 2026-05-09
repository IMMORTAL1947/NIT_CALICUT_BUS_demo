package com.example.nit_calciut_bus_demo.ui.theme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nit_calciut_bus_demo.R

class RequestListAdapter(private val onClick: (SpecialRequest) -> Unit)
    : ListAdapter<SpecialRequest, RequestListAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SpecialRequest>() {
            override fun areItemsTheSame(oldItem: SpecialRequest, newItem: SpecialRequest) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SpecialRequest, newItem: SpecialRequest) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_request_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val req = getItem(position)
        holder.bind(req)
        holder.itemView.setOnClickListener { onClick( req) }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val routeText: TextView = itemView.findViewById(R.id.routeText)
        private val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        private val datetimeText: TextView = itemView.findViewById(R.id.datetimeText)
        private val passengersText: TextView = itemView.findViewById(R.id.passengersText)
        private val purposeText: TextView = itemView.findViewById(R.id.purposeText)

        fun bind(it: SpecialRequest) {
            routeText.text = it.route
            datetimeText.text = it.dateTime
            passengersText.text = "${it.passengers} passengers"
            purposeText.text = it.purpose
            statusBadge.text = when (it.status) {
                RequestStatus.APPROVED -> "Approved"
                RequestStatus.REJECTED -> "Rejected"
                else -> "Pending"
            }
        }
    }
}

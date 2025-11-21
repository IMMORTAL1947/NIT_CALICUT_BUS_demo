package com.example.nit_calciut_bus_demo.ui.theme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nit_calciut_bus_demo.R

class RouteAdapter(private val routes: List<RouteInfo>) : 
    RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(routes[position])
    }

    override fun getItemCount() = routes.size

    class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val routeNameText: TextView = itemView.findViewById(R.id.routeNameText)
        private val routeColorIndicator: View = itemView.findViewById(R.id.routeColorIndicator)
        private val stopsContainer: ViewGroup = itemView.findViewById(R.id.stopsContainer)

        fun bind(routeInfo: RouteInfo) {
            routeNameText.text = routeInfo.routeName
            routeColorIndicator.setBackgroundColor(routeInfo.routeColor)
            
            // Clear previous stops
            stopsContainer.removeAllViews()
            
            // Add stops to the timeline
            routeInfo.stops.forEachIndexed { index, stop ->
                val stopView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.item_stop_timeline, stopsContainer, false)
                
                val stopNameText = stopView.findViewById<TextView>(R.id.stopNameText)
                val stopNumber = stopView.findViewById<TextView>(R.id.stopNumber)
                val timelineLine = stopView.findViewById<View>(R.id.timelineLine)
                
                stopNameText.text = stop
                stopNumber.text = (index + 1).toString()
                
                // Hide line for the last stop
                if (index == routeInfo.stops.size - 1) {
                    timelineLine.visibility = View.INVISIBLE
                }
                
                stopsContainer.addView(stopView)
            }
        }
    }
}

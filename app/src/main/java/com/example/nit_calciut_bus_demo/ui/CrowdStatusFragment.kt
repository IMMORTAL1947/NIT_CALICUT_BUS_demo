package com.example.nit_calciut_bus_demo.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nit_calciut_bus_demo.R
import com.example.nit_calciut_bus_demo.ui.theme.RoutesViewModel

/**
 * Optional standalone fragment for displaying crowd monitoring data.
 * (Primary crowd display is integrated directly into RoutesFragment)
 */
class CrowdStatusFragment : Fragment() {
    private lateinit var viewModel: RoutesViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_crowd_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity()).get(RoutesViewModel::class.java)
        
        val crowdContainer = view.findViewById<View>(R.id.crowdContainer)
        val crowdLevelText = view.findViewById<TextView>(R.id.crowdLevelText)
        val crowdPeopleText = view.findViewById<TextView>(R.id.crowdPeopleText)
        val crowdProgressBar = view.findViewById<ProgressBar>(R.id.crowdProgressBar)
        val crowdPercentText = view.findViewById<TextView>(R.id.crowdPercentText)
        
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (state == null) {
                crowdContainer.visibility = View.GONE
                return@observe
            }
            
            crowdContainer.visibility = View.VISIBLE
            
            // Update crowd level indicator
            val level = state.crowdLevel ?: "UNAVAILABLE"
            val icon = when (level) {
                "LOW" -> "🟢"
                "MEDIUM" -> "🟡"
                "HIGH" -> "🔴"
                "FULL" -> "🔴"
                else -> "⚪"
            }
            
            crowdLevelText.text = "$icon $level Rush"
            crowdPeopleText.text = "${state.crowdPeopleCount} passengers"
            
            // Update progress bar
            crowdProgressBar.progress = state.crowdPercent
            crowdPercentText.text = "${state.crowdPercent}% Full"
            
            // Update color
            try {
                val color = Color.parseColor(state.crowdColor ?: "#CCCCCC")
                crowdProgressBar.progressDrawable.setColorFilter(
                    color,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                crowdLevelText.setTextColor(color)
            } catch (e: Exception) {
                crowdLevelText.setTextColor(Color.GRAY)
            }
        }
    }
}

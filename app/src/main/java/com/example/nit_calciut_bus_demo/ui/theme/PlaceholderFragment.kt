package com.example.nit_calciut_bus_demo.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.nit_calciut_bus_demo.R

class PlaceholderFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_placeholder, container, false)
        val titleView = view.findViewById<TextView>(R.id.placeholderTitle)
        val messageView = view.findViewById<TextView>(R.id.placeholderMessage)
        titleView.text = arguments?.getString(ARG_TITLE) ?: ""
        messageView.text = arguments?.getString(ARG_MESSAGE) ?: ""
        return view
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(title: String, message: String): PlaceholderFragment {
            val fragment = PlaceholderFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_MESSAGE, message)
            }
            return fragment
        }
    }
}

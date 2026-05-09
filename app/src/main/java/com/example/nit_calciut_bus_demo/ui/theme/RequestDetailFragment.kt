package com.example.nit_calciut_bus_demo.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nit_calciut_bus_demo.R

class RequestDetailFragment : Fragment() {
    companion object {
        private const val ARG_ID = "req_id"
        fun newInstance(id: String) = RequestDetailFragment().apply { arguments = Bundle().apply { putString(ARG_ID, id) } }
    }

    private lateinit var vm: RequestViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_request_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(this)[RequestViewModel::class.java]
        val id = arguments?.getString(ARG_ID) ?: return

        val timeline = view.findViewById<LinearLayout>(R.id.timelineContainer)
        val route = view.findViewById<TextView>(R.id.detailRoute)
        val dt = view.findViewById<TextView>(R.id.detailDateTime)
        val purpose = view.findViewById<TextView>(R.id.detailPurpose)

        vm.requests.observe(viewLifecycleOwner) { list ->
            val req = list.find { it.id == id } ?: return@observe
            route.text = req.route
            dt.text = req.dateTime
            purpose.text = "Purpose: ${req.purpose}"

            timeline.removeAllViews()
            addTimelineItem(timeline, "Request Submitted", req.lastUpdated, req.status == RequestStatus.PENDING || req.status == RequestStatus.APPROVED || req.status == RequestStatus.REJECTED)
            addTimelineItem(timeline, "Under Review", req.lastUpdated + 1000, req.status == RequestStatus.APPROVED || req.status == RequestStatus.REJECTED)
            addTimelineItem(timeline, "Approved / Scheduled", req.lastUpdated + 2000, req.status == RequestStatus.APPROVED)
        }

        vm.loadRequests(requireContext())
    }

    private fun addTimelineItem(container: LinearLayout, title: String, ts: Long, done: Boolean) {
        val v = layoutInflater.inflate(android.R.layout.simple_list_item_1, container, false) as TextView
        v.text = (if (done) "✔ " else "⏳ ") + title
        container.addView(v)
    }
}

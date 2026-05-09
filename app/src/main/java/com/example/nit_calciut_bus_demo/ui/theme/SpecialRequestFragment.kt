package com.example.nit_calciut_bus_demo.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nit_calciut_bus_demo.R

class SpecialRequestFragment : Fragment() {
    private lateinit var vm: RequestViewModel
    private lateinit var adapter: RequestListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_special_request, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(this)[RequestViewModel::class.java]

        val recycler = view.findViewById<RecyclerView>(R.id.requestsRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = RequestListAdapter { req ->
            // Open detail fragment
            val f = RequestDetailFragment.newInstance(req.id)
            parentFragmentManager.beginTransaction().replace((view.parent as ViewGroup).id, f).addToBackStack(null).commit()
        }
        recycler.adapter = adapter

        val empty = view.findViewById<TextView>(R.id.srEmpty)

        val fab = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabNewRequest)
        fab.setOnClickListener {
            parentFragmentManager.beginTransaction().replace((view.parent as ViewGroup).id, RequestFormFragment()).addToBackStack(null).commit()
        }

        vm.requests.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        vm.loadRequests(requireContext())
    }
}

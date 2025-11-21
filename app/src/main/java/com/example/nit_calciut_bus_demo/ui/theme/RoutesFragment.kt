package com.example.nit_calciut_bus_demo.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.nit_calciut_bus_demo.R

class RoutesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_routes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.routesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        loadRoutesFromBackend { routes ->
            recyclerView.adapter = RouteAdapter(routes)
        }
    }
}

data class RouteInfo(
    val routeName: String,
    val routeColor: Int,
    val stops: List<String>
)

private fun RoutesFragment.loadRoutesFromBackend(onReady: (List<RouteInfo>) -> Unit) {
    val code = AppPrefs.getCollegeCode(requireContext())
    val baseUrl = AppPrefs.getServerUrl(requireContext())
    if (code.isNullOrEmpty()) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Set College in Settings to load routes", Toast.LENGTH_SHORT).show()
        }
        onReady(emptyList())
        return
    }
    Thread {
        try {
            val url = java.net.URL("${baseUrl}/api/colleges/${code}/config")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(response)
                val stopsArr = json.getJSONArray("stops")
                val routesArr = json.getJSONArray("routes")
                val stopNameById = mutableMapOf<String, String>()
                for (i in 0 until stopsArr.length()) {
                    val s = stopsArr.getJSONObject(i)
                    stopNameById[s.getString("id")] = s.getString("name")
                }
                val list = mutableListOf<RouteInfo>()
                for (i in 0 until routesArr.length()) {
                    val r = routesArr.getJSONObject(i)
                    val name = r.getString("name")
                    val colorHex = r.optString("color", "#2196f3")
                    val colorInt = android.graphics.Color.parseColor(colorHex)
                    val stopIds = r.getJSONArray("stopIds")
                    val stops = mutableListOf<String>()
                    for (j in 0 until stopIds.length()) {
                        val id = stopIds.getString(j)
                        stops.add(stopNameById[id] ?: id)
                    }
                    list.add(RouteInfo(name, colorInt, stops))
                }
                requireActivity().runOnUiThread {
                    if (list.isEmpty()) {
                        Toast.makeText(requireContext(), "No routes found for $code", Toast.LENGTH_SHORT).show()
                    }
                    onReady(list)
                }
            } else {
                Log.e("Routes", "Config fetch failed: ${conn.responseCode}")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to fetch routes (${conn.responseCode})", Toast.LENGTH_SHORT).show()
                    onReady(emptyList())
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("Routes", "Error fetching config", e)
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Failed to fetch data from backend", Toast.LENGTH_SHORT).show()
                onReady(emptyList())
            }
        }
    }.start()
}

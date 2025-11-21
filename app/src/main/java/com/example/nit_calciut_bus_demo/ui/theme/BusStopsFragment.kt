package com.example.nit_calciut_bus_demo.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.nit_calciut_bus_demo.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

class BusStopsFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    companion object {
        // Bus stops coordinates
        private val southCampus = LatLng(11.314111, 75.932000)
        private val somsHostel = LatLng(11.314833, 75.932417)
        private val lhHostel = LatLng(11.318333, 75.931028)
        private val eclhc = LatLng(11.323056, 75.937278)
        private val architecture = LatLng(11.322944, 75.936444)
        private val deptBuilding = LatLng(11.321639, 75.935028)
        private val centerCircle = LatLng(11.321500, 75.934167)
        private val megaHostel = LatLng(11.317167, 75.937667)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bus_stops, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        
        // Configure map settings
        mMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isMapToolbarEnabled = true
            isCompassEnabled = true
        }

        // Set up marker click listener
        mMap.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            true
        }

    // Try to load from backend using college code; if it fails, show an error toast
    loadAndRenderStops()

        // Center the map
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centerCircle, 15f))
    }

    private fun addBusStopMarkers(stops: List<Pair<LatLng, String>>) {
        val busIcon = getScaledBusIcon(28)

        stops.forEach { (position, title) ->
            mMap.addMarker(MarkerOptions()
                .position(position)
                .title(title)
                .icon(busIcon)
                .anchor(0.5f, 0.5f))
        }
    }

    private fun loadAndRenderStops() {
        val code = AppPrefs.getCollegeCode(requireContext())
        val baseUrl = AppPrefs.getServerUrl(requireContext())
        if (code.isNullOrEmpty()) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Set College in Settings to load stops", Toast.LENGTH_SHORT).show()
            }
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
                    val stopsJson = json.getJSONArray("stops")
                    val stopsList = mutableListOf<Pair<LatLng, String>>()
                    for (i in 0 until stopsJson.length()) {
                        val s = stopsJson.getJSONObject(i)
                        val lat = s.getDouble("lat")
                        val lng = s.getDouble("lng")
                        val name = s.getString("name")
                        stopsList.add(Pair(LatLng(lat, lng), name))
                    }
                    requireActivity().runOnUiThread {
                        if (stopsList.isNotEmpty()) {
                            addBusStopMarkers(stopsList)
                        } else {
                            Log.w("BusStops", "No stops in config")
                            Toast.makeText(requireContext(), "No stops found for $code", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("BusStops", "Config fetch failed: ${conn.responseCode}")
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to fetch stops (${conn.responseCode})", Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("BusStops", "Error fetching config", e)
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to fetch data from backend", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun getScaledBusIcon(dpSize: Int = 28): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val px = (dpSize * density).toInt().coerceAtLeast(24)
        val original = BitmapFactory.decodeResource(resources, R.drawable.bus)
        val scaled = Bitmap.createScaledBitmap(original, px, px, true)
        return BitmapDescriptorFactory.fromBitmap(scaled)
    }
}

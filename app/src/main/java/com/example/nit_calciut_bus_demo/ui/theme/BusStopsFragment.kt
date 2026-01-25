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
    private val liveMarkers: MutableMap<String, Marker> = mutableMapOf()
    private var livePollingThread: Thread? = null

    companion object {}

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
    startLivePolling()

        // Camera will be centered to fetched stops when available
    }

    private fun addBusStopMarkers(stops: List<Pair<LatLng, String>>) {
        val busIcon = getScaledBusIcon(28)

        val boundsBuilder = LatLngBounds.Builder()
        stops.forEach { (position, title) ->
            mMap.addMarker(MarkerOptions()
                .position(position)
                .title(title)
                .icon(busIcon)
                .anchor(0.5f, 0.5f))
            boundsBuilder.include(position)
        }
        if (stops.isNotEmpty()) {
            val bounds = boundsBuilder.build()
            val padding = 100
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
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

    private fun startLivePolling() {
        val code = AppPrefs.getCollegeCode(requireContext()) ?: return
        val baseUrl = AppPrefs.getServerUrl(requireContext()) ?: return
        livePollingThread?.interrupt()
        livePollingThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val url = java.net.URL("${baseUrl}/api/colleges/${code}/live")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(response)
                        val arr = json.getJSONArray("buses")
                        val positions = mutableListOf<Triple<String, Double, Double>>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            positions.add(Triple(o.getString("busId"), o.getDouble("lat"), o.getDouble("lng")))
                        }
                        requireActivity().runOnUiThread {
                            updateLiveMarkers(positions)
                        }
                    }
                    conn.disconnect()
                    Thread.sleep(5000)
                }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                Log.e("LivePolling", "error", e)
            }
        }
        livePollingThread!!.start()
    }

    private fun updateLiveMarkers(positions: List<Triple<String, Double, Double>>) {
        val icon = getScaledBusIcon(32)
        for ((busId, lat, lng) in positions) {
            val pos = LatLng(lat, lng)
            val existing = liveMarkers[busId]
            if (existing != null) {
                existing.position = pos
            } else {
                val marker = mMap.addMarker(MarkerOptions().position(pos).icon(icon).title("Bus: $busId"))
                if (marker != null) liveMarkers[busId] = marker
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        livePollingThread?.interrupt()
        livePollingThread = null
    }

    private fun getScaledBusIcon(dpSize: Int = 28): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val px = (dpSize * density).toInt().coerceAtLeast(24)
        val original = BitmapFactory.decodeResource(resources, R.drawable.bus)
        val scaled = Bitmap.createScaledBitmap(original, px, px, true)
        return BitmapDescriptorFactory.fromBitmap(scaled)
    }
}

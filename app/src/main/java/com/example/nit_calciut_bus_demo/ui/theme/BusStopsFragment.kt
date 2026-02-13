package com.example.nit_calciut_bus_demo.ui.theme

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.nit_calciut_bus_demo.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

class BusStopsFragment : Fragment() {

    private lateinit var stopsMap: GoogleMap
    private lateinit var liveMap: GoogleMap
    private val liveMarkers: MutableMap<String, Marker> = mutableMapOf()
    private var livePollingThread: Thread? = null
    private var userLocation: Location? = null

    private val stopItems: MutableList<Pair<String, LatLng>> = mutableListOf()
    private val busMeta: MutableMap<String, Pair<String, String>> = mutableMapOf() // busId -> (busName, routeId)
    private val busLastTs: MutableMap<String, Long> = mutableMapOf()

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
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                userLocation = loc
            }
        }

        val stopsFrag = childFragmentManager.findFragmentById(R.id.stopsMap) as SupportMapFragment
        stopsFrag.getMapAsync { map ->
            stopsMap = map
            configureMapUi(stopsMap)
            loadAndRenderStops()
        }

        val liveFrag = childFragmentManager.findFragmentById(R.id.liveMap) as SupportMapFragment
        liveFrag.getMapAsync { map ->
            liveMap = map
            configureMapUi(liveMap)
            startLivePolling()
        }
    }

    private fun configureMapUi(map: GoogleMap) {
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isMapToolbarEnabled = true
            isCompassEnabled = true
        }
        map.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            true
        }
    }

    private fun addBusStopMarkers(stops: List<Pair<LatLng, String>>) {
        val stopIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)

        val boundsBuilder = LatLngBounds.Builder()
        stops.forEach { (position, title) ->
            stopsMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(title)
                    .icon(stopIcon)
                    .anchor(0.5f, 0.5f)
            )
            boundsBuilder.include(position)
        }
        if (stops.isNotEmpty()) {
            val bounds = boundsBuilder.build()
            val padding = 100
            stopsMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
        stopItems.clear()
        stops.forEach { (pos, name) -> stopItems.add(Pair(name, pos)) }
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
                    busMeta.clear()
                    val busesJson = json.optJSONArray("buses") ?: org.json.JSONArray()
                    for (i in 0 until busesJson.length()) {
                        val b = busesJson.getJSONObject(i)
                        val id = b.getString("id")
                        val name = b.getString("name")
                        val routeId = b.optString("routeId", "")
                        busMeta[id] = Pair(name, routeId)
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
                            val id = o.getString("busId")
                            positions.add(Triple(id, o.getDouble("lat"), o.getDouble("lng")))
                            val ts = o.optLong("ts", System.currentTimeMillis())
                            busLastTs[id] = ts
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
        val icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        for ((busId, lat, lng) in positions) {
            val pos = LatLng(lat, lng)
            val existing = liveMarkers[busId]
            if (existing != null) {
                animateMarkerTo(existing, pos)
            } else {
                val marker = liveMap.addMarker(MarkerOptions().position(pos).icon(icon).title("Bus: $busId"))
                if (marker != null) liveMarkers[busId] = marker
            }
        }
    }

    private fun animateMarkerTo(marker: Marker, toPos: LatLng) {
        val from = marker.position
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 500
        animator.addUpdateListener { va ->
            val t = va.animatedValue as Float
            val lat = from.latitude + (toPos.latitude - from.latitude) * t
            val lng = from.longitude + (toPos.longitude - from.longitude) * t
            marker.position = LatLng(lat, lng)
        }
        animator.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        livePollingThread?.interrupt()
        livePollingThread = null
    }

    private fun getScaledBusIcon(dpSize: Int = 28): BitmapDescriptor {
        // Fallback to default marker to avoid missing drawable resource
        return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0]
    }
}

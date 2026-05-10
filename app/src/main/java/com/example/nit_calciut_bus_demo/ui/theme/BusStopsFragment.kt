package com.example.nit_calciut_bus_demo.ui.theme

import android.animation.ValueAnimator
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.nit_calciut_bus_demo.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
// chip controls removed
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class BusStopsFragment : Fragment() {

    private lateinit var stopPreviewMap: GoogleMap
    private lateinit var liveMap: GoogleMap
    private val liveBusPoints: MutableList<LiveBusPoint> = mutableListOf()
    private var livePollingThread: Thread? = null
    private var userLocation: Location? = null

    private var allStops: List<Stop> = emptyList()
    private var allRoutes: List<Route> = emptyList()
    private var allBuses: List<Bus> = emptyList()

    private lateinit var stopNameText: TextView
    private lateinit var liveStatusText: TextView
    private lateinit var locationButton: MaterialButton

    companion object {}

    // filter chips removed: Nearby / Favorites / My Routes

    private data class LiveBusPoint(
        val busId: String,
        val busName: String,
        val routeId: String?,
        val position: LatLng,
        val updatedAt: Long
    )

    private data class BusCluster(
        val center: LatLng,
        val buses: List<LiveBusPoint>
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bus_stops, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        stopNameText = view.findViewById(R.id.stopNameText)
        liveStatusText = view.findViewById(R.id.liveStatusText)
        locationButton = view.findViewById(R.id.locationButton)

        locationButton.setOnClickListener { focusOnUserLocation() }

        val fused = LocationServices.getFusedLocationProviderClient(requireContext())
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                userLocation = loc
                refreshStopCard()
                renderPreviewStop()
                renderLiveMarkers()
            }
        }

        val previewFrag = childFragmentManager.findFragmentById(R.id.stopPreviewMap) as SupportMapFragment
        previewFrag.view?.let { enableParentScrollLockWhileTouching(it) }
        previewFrag.getMapAsync { map ->
            stopPreviewMap = map
            configureMapUi(stopPreviewMap, preview = true)
            renderPreviewStop()
        }

        val liveFrag = childFragmentManager.findFragmentById(R.id.liveMap) as SupportMapFragment
        liveFrag.view?.let { enableParentScrollLockWhileTouching(it) }
        liveFrag.getMapAsync { map ->
            liveMap = map
            configureMapUi(liveMap, preview = false)
            liveMap.setOnCameraIdleListener { renderLiveMarkers() }
            startLivePolling()
        }

        // Attach touch overlays (transparent Views placed above the fragment content)
        // Wire overlays to forward touches to the underlying map view while still
        // preventing the parent NestedScrollView from intercepting during gestures.
        view.findViewById<View?>(R.id.stopPreviewTouchOverlay)?.let { overlay ->
            wireOverlayToMap(overlay, R.id.stopPreviewMap)
        }
        view.findViewById<View?>(R.id.liveMapTouchOverlay)?.let { overlay ->
            wireOverlayToMap(overlay, R.id.liveMap)
        }

        loadAndRenderStops()
    }

    // filter chips removed

    private fun configureMapUi(map: GoogleMap, preview: Boolean) {
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isMapToolbarEnabled = false
            isCompassEnabled = !preview
            isTiltGesturesEnabled = !preview
            isRotateGesturesEnabled = !preview
            isScrollGesturesEnabled = true
            isScrollGesturesEnabledDuringRotateOrZoom = true
        }
        runCatching {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.bus_map_style))
        }
        map.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            true
        }
        if (preview) {
            map.setOnCameraMoveListener {
                true
            }
        }
    }

    private fun addBusStopMarkers(stops: List<Stop>) {
        allStops = stops
        renderPreviewStop()
        refreshStopCard()
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
                    val stopsList = mutableListOf<Stop>()
                    for (i in 0 until stopsJson.length()) {
                        val s = stopsJson.getJSONObject(i)
                        val lat = s.getDouble("lat")
                        val lng = s.getDouble("lng")
                        val name = s.getString("name")
                        val id = s.optString("id", name)
                        stopsList.add(Stop(id, name, lat, lng))
                    }
                    val routesJson = json.optJSONArray("routes") ?: org.json.JSONArray()
                    val routesList = mutableListOf<Route>()
                    for (i in 0 until routesJson.length()) {
                        val r = routesJson.getJSONObject(i)
                        val stopIdsJson = r.optJSONArray("stopIds") ?: org.json.JSONArray()
                        val stopIds = mutableListOf<String>()
                        for (j in 0 until stopIdsJson.length()) {
                            stopIds.add(stopIdsJson.getString(j))
                        }
                        routesList.add(
                            Route(
                                id = r.getString("id"),
                                name = r.optString("name", r.getString("id")),
                                color = r.optString("color", "#6750A4"),
                                stopIds = stopIds
                            )
                        )
                    }

                    val busesList = mutableListOf<Bus>()
                    val busesJson = json.optJSONArray("buses") ?: org.json.JSONArray()
                    for (i in 0 until busesJson.length()) {
                        val b = busesJson.getJSONObject(i)
                        val id = b.getString("id")
                        val name = b.getString("name")
                        val routeId = b.optString("routeId", "")
                        busesList.add(Bus(id = id, name = name, routeId = routeId.ifBlank { null }))
                    }
                    requireActivity().runOnUiThread {
                        if (stopsList.isNotEmpty()) {
                            addBusStopMarkers(stopsList)
                        } else {
                            Log.w("BusStops", "No stops in config")
                            Toast.makeText(requireContext(), "No stops found for $code", Toast.LENGTH_SHORT).show()
                        }
                        allRoutes = routesList
                        allBuses = busesList
                        refreshStopCard()
                        renderPreviewStop()
                        renderLiveMarkers()
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
                        val positions = mutableListOf<LiveBusPoint>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val id = o.getString("busId")
                            val bus = allBuses.firstOrNull { it.id == id }
                            positions.add(
                                LiveBusPoint(
                                    busId = id,
                                    busName = bus?.name ?: id,
                                    routeId = bus?.routeId,
                                    position = LatLng(o.getDouble("lat"), o.getDouble("lng")),
                                    updatedAt = o.optLong("ts", System.currentTimeMillis())
                                )
                            )
                        }
                        requireActivity().runOnUiThread {
                            liveBusPoints.clear()
                            liveBusPoints.addAll(positions)
                            renderLiveMarkers()
                        }
                    }
                    conn.disconnect()
                    Thread.sleep(15000)
                }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                Log.e("LivePolling", "error", e)
            }
        }
        livePollingThread!!.start()
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

    private fun renderPreviewStop() {
        if (!::stopPreviewMap.isInitialized || allStops.isEmpty()) return
        val stop = currentStop() ?: allStops.firstOrNull() ?: return
        stopPreviewMap.clear()
        
        val nearbyStops = allStops.filter {
            distanceMeters(stop.lat, stop.lng, it.lat, it.lng) <= 800f
        }.take(5)
        
        val boundsBuilder = LatLngBounds.Builder()
        nearbyStops.forEach { s ->
            val markerColor = if (s.id == stop.id) BitmapDescriptorFactory.HUE_VIOLET else BitmapDescriptorFactory.HUE_ROSE
            stopPreviewMap.addMarker(
                MarkerOptions()
                    .position(LatLng(s.lat, s.lng))
                    .title(s.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
            )
            boundsBuilder.include(LatLng(s.lat, s.lng))
        }
        
        stopPreviewMap.addCircle(
            CircleOptions()
                .center(LatLng(stop.lat, stop.lng))
                .radius(800.0)
                .strokeColor(Color.parseColor("#8663E6"))
                .fillColor(Color.parseColor("#338663E6"))
                .strokeWidth(2f)
        )
        
        runCatching {
            val bounds = boundsBuilder.build()
            stopPreviewMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        }
    }

    private fun refreshStopCard() {
    }



    private fun renderLiveMarkers() {
        if (!::liveMap.isInitialized) return
        val filteredPoints = filteredLivePoints()
        liveMap.clear()

        if (filteredPoints.isEmpty()) {
            val fallbackStop = currentStop()
            if (fallbackStop != null) {
                liveMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(fallbackStop.lat, fallbackStop.lng))
                        .title("No live buses yet")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
                )
            }
            return
        }

        val clusters = clusterLivePoints(filteredPoints, liveMap.cameraPosition.zoom)
        val boundsBuilder = LatLngBounds.Builder()
        clusters.forEach { cluster ->
            boundsBuilder.include(cluster.center)
            if (cluster.buses.size == 1) {
                val bus = cluster.buses.first()
                liveMap.addMarker(
                    MarkerOptions()
                        .position(cluster.center)
                        .title(bus.busName)
                        .snippet(routeLabel(bus.routeId))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
            } else {
                liveMap.addMarker(
                    MarkerOptions()
                        .position(cluster.center)
                        .title("${cluster.buses.size} buses")
                        .snippet(cluster.buses.take(3).joinToString { it.busName })
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
                )
            }
        }

        runCatching {
            if (clusters.size == 1) {
                liveMap.animateCamera(CameraUpdateFactory.newLatLngZoom(clusters.first().center, max(15f, liveMap.cameraPosition.zoom)))
            } else {
                liveMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
            }
        }
    }

    private fun filteredLivePoints(): List<LiveBusPoint> {
        return liveBusPoints
    }

    private fun clusterLivePoints(points: List<LiveBusPoint>, zoom: Float): List<BusCluster> {
        val bucket = when {
            zoom < 12f -> 0.01
            zoom < 14f -> 0.005
            zoom < 16f -> 0.002
            else -> 0.0007
        }
        return points.groupBy {
            val latBucket = floor(it.position.latitude / bucket).toInt()
            val lngBucket = floor(it.position.longitude / bucket).toInt()
            "$latBucket:$lngBucket"
        }.values.map { group ->
            val center = LatLng(
                group.map { it.position.latitude }.average(),
                group.map { it.position.longitude }.average()
            )
            BusCluster(center, group)
        }
    }

    private fun currentStop(): Stop? {
        if (allStops.isEmpty()) return null
        val loc = userLocation
        if (loc == null) return allStops.firstOrNull()
        return allStops.minByOrNull { stop ->
            distanceMeters(loc.latitude, loc.longitude, stop.lat, stop.lng)
        }
    }

    private fun distanceLabel(stop: Stop): String {
        val loc = userLocation ?: return "Nearby stop • ${stop.lat}, ${stop.lng}"
        val meters = distanceMeters(loc.latitude, loc.longitude, stop.lat, stop.lng).roundToInt()
        val walkMinutes = max(1, ceil(meters / 75.0).toInt())
        return "$walkMinutes min walk • ${meters}m"
    }

    private fun enableParentScrollLockWhileTouching(mapView: View) {
        mapView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    private fun wireOverlayToMap(overlay: View, mapFragmentId: Int) {
        val mapView = childFragmentManager.findFragmentById(mapFragmentId)?.view
        overlay.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_UP -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            // Forward the event to the underlying map view so the map handles gestures.
            // If mapView is null, return false to allow normal propagation.
            return@setOnTouchListener (mapView?.dispatchTouchEvent(event) ?: false)
        }
    }

    private fun routeLabel(routeId: String?): String {
        if (routeId.isNullOrBlank()) return "Live on campus route"
        return allRoutes.firstOrNull { it.id == routeId }?.name ?: routeId
    }

    private fun focusOnUserLocation() {
        val loc = userLocation
        if (loc == null) {
            Toast.makeText(requireContext(), "Location unavailable yet", Toast.LENGTH_SHORT).show()
            return
        }
        val latLng = LatLng(loc.latitude, loc.longitude)
        if (::stopPreviewMap.isInitialized) {
            stopPreviewMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }
        if (::liveMap.isInitialized) {
            liveMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        livePollingThread?.interrupt()
        livePollingThread = null
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0]
    }
}

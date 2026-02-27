package com.example.nit_calciut_bus_demo.ui.theme

import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.nit_calciut_bus_demo.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

class RoutesFragment : Fragment() {

    private lateinit var viewModel: RoutesViewModel
    private lateinit var stopsAdapter: StopsTimelineAdapter
    private lateinit var stepsAdapter: StepsAdapter
    private var routeMapWebView: WebView? = null
    private var isLeafletReady: Boolean = false
    private var userLocation: Location? = null
    private var selectedRoutingMode: RoutingMode = RoutingMode.GOOGLE
    private var pendingStopIndex: Int? = null

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            fetchUserLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_routes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[RoutesViewModel::class.java]

        // Ensure location permission and fetch latest location
        ensureLocationAndFetch()

        val busSelector = view.findViewById<Spinner>(R.id.busSelector)
        val routeTitle = view.findViewById<TextView>(R.id.routeTitle)
        val busTitle = view.findViewById<TextView>(R.id.busTitle)

        val recyclerView = view.findViewById<RecyclerView>(R.id.stopsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        stopsAdapter = StopsTimelineAdapter { state ->
            val current = viewModel.uiState.value
            if (selectedRoutingMode == RoutingMode.GOOGLE) {
                val dest = current?.routePoints?.getOrNull(state.index)
                val src = userLocation
                if (dest != null) {
                    try {
                        val uri = if (src != null) {
                            android.net.Uri.parse("https://www.openstreetmap.org/directions?engine=fossgis_osrm_foot&route=${src.latitude},${src.longitude};${dest.latitude},${dest.longitude}")
                        } else {
                            android.net.Uri.parse("https://www.openstreetmap.org/?mlat=${dest.latitude}&mlon=${dest.longitude}#map=18/${dest.latitude}/${dest.longitude}")
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Open in OpenStreetMap failed", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                if (userLocation == null) {
                    pendingStopIndex = state.index
                    Toast.makeText(requireContext(), "Fetching location...", Toast.LENGTH_SHORT).show()
                    ensureLocationAndFetch()
                } else {
                    viewModel.onStopClicked(state)
                }
            }
        }
        recyclerView.adapter = stopsAdapter

        val stepsRecycler = view.findViewById<RecyclerView>(R.id.stepsRecyclerView)
        stepsRecycler.layoutManager = LinearLayoutManager(requireContext())
        stepsAdapter = StepsAdapter()
        stepsRecycler.adapter = stepsAdapter

        val webView = view.findViewById<WebView>(R.id.routeMapWebView)
        routeMapWebView = webView
        setupLeafletMap(webView)

        val routingModeGroup = view.findViewById<RadioGroup>(R.id.routingModeGroup)
        val routingModeNote = view.findViewById<TextView>(R.id.routingModeNote)
        val stepsHeader = view.findViewById<TextView>(R.id.stepsHeader)

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (state == null) return@observe
            routeTitle.text = state.routeName ?: "Route"
            busTitle.text = state.busName ?: "Bus"
            // Populate bus selector when options ready
            if (busSelector.adapter == null && state.busOptions.isNotEmpty()) {
                val names = state.busOptions.map { it.second }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                busSelector.adapter = adapter
                // Default selection
                val defIndex = state.defaultBusIndex.coerceIn(0, names.size - 1)
                busSelector.setSelection(defIndex)
            }
            // Update routing mode selector without triggering listener loops
            selectedRoutingMode = state.routingMode
            val desiredCheckedId = if (state.routingMode == RoutingMode.GOOGLE) {
                R.id.routingModeGoogle
            } else {
                R.id.routingModeCampus
            }
            if (routingModeGroup.checkedRadioButtonId != desiredCheckedId) {
                routingModeGroup.check(desiredCheckedId)
            }
            routingModeNote.visibility = if (state.routingMode == RoutingMode.DIJKSTRA) View.VISIBLE else View.GONE
            // Render map overlays
            if (state.routingMode == RoutingMode.DIJKSTRA && state.dijkstraPath.isNotEmpty()) {
                renderDijkstraOnMap(state.dijkstraPath)
            } else {
                renderRouteOnMap(state.routePoints, state.stopMarkers)
            }
            updateBusMarker(state.busLatLng)

            if (state.routingMode == RoutingMode.DIJKSTRA && state.dijkstraSteps.isNotEmpty()) {
                stepsHeader.visibility = View.VISIBLE
                stepsRecycler.visibility = View.VISIBLE
                stepsAdapter.submitList(state.dijkstraSteps)
            } else {
                stepsHeader.visibility = View.GONE
                stepsRecycler.visibility = View.GONE
                stepsAdapter.submitList(emptyList())
            }

            // Loading / error states
            if (state.loading) {
                Toast.makeText(requireContext(), "Computing route...", Toast.LENGTH_SHORT).show()
            }
            state.error?.let { msg ->
                if (msg.isNotEmpty()) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.stopStates.observe(viewLifecycleOwner) { states ->
            stopsAdapter.submitList(states)
        }

        // Observe campus route results (algorithmic)
        viewModel.routeResponse.observe(viewLifecycleOwner) { result ->
            if (result == null) {
                stepsHeader.visibility = View.GONE
                stepsRecycler.visibility = View.GONE
                stepsAdapter.submitList(emptyList())
                return@observe
            }
            val path = result.nodes.map { it.latLng }
            renderDijkstraOnMap(path)
            stepsHeader.visibility = View.VISIBLE
            stepsRecycler.visibility = View.VISIBLE
            stepsAdapter.submitList(result.steps)
        }

        // On algorithmic failure, stay in-app and show message only
        viewModel.routeError.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrEmpty()) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        }

        routingModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.routingModeCampus) RoutingMode.DIJKSTRA else RoutingMode.GOOGLE
            selectedRoutingMode = mode
            viewModel.setRoutingMode(mode)
        }

        // Load config immediately; WebView rendering will occur when ready.
        viewModel.loadConfig(requireContext())
    }

    private fun renderRouteOnMap(points: List<LatLng>, stops: List<Pair<LatLng, String>>) {
        val routeJson = JSONArray().apply {
            points.forEach { p ->
                put(JSONObject().apply {
                    put("lat", p.latitude)
                    put("lng", p.longitude)
                })
            }
        }
        val stopsJson = JSONArray().apply {
            stops.forEach { (pos, name) ->
                put(JSONObject().apply {
                    put("lat", pos.latitude)
                    put("lng", pos.longitude)
                    put("name", name)
                })
            }
        }
        evalLeafletJs("window.renderRoute(${routeJson}, ${stopsJson}, '#2196F3');")
    }

    private fun ensureLocationAndFetch() {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            fetchUserLocation()
        } else {
            locationPermLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun fetchUserLocation() {
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                userLocation = loc
                viewModel.setUserLocation(loc)
                pendingStopIndex?.let {
                    viewModel.onStopIndexSelected(it)
                    pendingStopIndex = null
                }
            } else {
                // Try a one-shot current location request
                try {
                    fused.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    ).addOnSuccessListener { cur ->
                        if (cur != null) {
                            userLocation = cur
                            viewModel.setUserLocation(cur)
                            pendingStopIndex?.let {
                                viewModel.onStopIndexSelected(it)
                                pendingStopIndex = null
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun renderDijkstraOnMap(path: List<LatLng>) {
        val pathJson = JSONArray().apply {
            path.forEach { p ->
                put(JSONObject().apply {
                    put("lat", p.latitude)
                    put("lng", p.longitude)
                })
            }
        }
        evalLeafletJs("window.renderRoute(${pathJson}, [], '#E91E63');")
    }

    private fun openGoogleMapsToSelectedStop() {
        val current = viewModel.uiState.value ?: return
        val selectedIndex = viewModel.getSelectedStopIndex()
        if (selectedIndex == null || selectedIndex < 0) return
        val dest = current.stopMarkers.getOrNull(selectedIndex)?.first ?: return
        val src = userLocation
        try {
            val uri = if (src != null) {
                android.net.Uri.parse("https://www.openstreetmap.org/directions?engine=fossgis_osrm_foot&route=${src.latitude},${src.longitude};${dest.latitude},${dest.longitude}")
            } else {
                android.net.Uri.parse("https://www.openstreetmap.org/?mlat=${dest.latitude}&mlon=${dest.longitude}#map=18/${dest.latitude}/${dest.longitude}")
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Open in OpenStreetMap failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateBusMarker(pos: LatLng?) {
        if (pos == null) {
            evalLeafletJs("window.updateBusMarker(null);")
            return
        }
        evalLeafletJs("window.updateBusMarker({lat:${pos.latitude},lng:${pos.longitude}});")
    }

    private fun setupLeafletMap(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLeafletReady = true
                val state = viewModel.uiState.value
                if (state != null) {
                    if (state.routingMode == RoutingMode.DIJKSTRA && state.dijkstraPath.isNotEmpty()) {
                        renderDijkstraOnMap(state.dijkstraPath)
                    } else {
                        renderRouteOnMap(state.routePoints, state.stopMarkers)
                    }
                    updateBusMarker(state.busLatLng)
                }
            }
        }
        webView.loadUrl("file:///android_asset/leaflet_route_map.html")
    }

    private fun evalLeafletJs(script: String) {
        if (!isLeafletReady) return
        routeMapWebView?.evaluateJavascript(script, null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        routeMapWebView?.destroy()
        routeMapWebView = null
        isLeafletReady = false
    }
}


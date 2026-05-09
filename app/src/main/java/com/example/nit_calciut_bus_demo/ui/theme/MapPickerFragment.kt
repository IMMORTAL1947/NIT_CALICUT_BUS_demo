package com.example.nit_calciut_bus_demo.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.nit_calciut_bus_demo.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapPickerFragment : Fragment(), OnMapReadyCallback {
    companion object {
        private const val CALLBACK_KEY = "callback_key"
        fun newInstance(onLocationSelected: (String) -> Unit): MapPickerFragment {
            return MapPickerFragment().apply {
                this.onLocationSelected = onLocationSelected
            }
        }
    }

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private var selectedLocation: LatLng? = null
    private var onLocationSelected: ((String) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_map_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.onResume()
        mapView.getMapAsync(this)

        val selectedLocationText = view.findViewById<TextView>(R.id.selectedLocationText)
        val confirmButton = view.findViewById<Button>(R.id.confirmLocationButton)
        val cancelButton = view.findViewById<Button>(R.id.cancelMapButton)

        confirmButton.setOnClickListener {
            if (selectedLocation != null) {
                val locString = "${selectedLocation!!.latitude},${selectedLocation!!.longitude}"
                onLocationSelected?.invoke(locString)
                parentFragmentManager.popBackStack()
            } else {
                Toast.makeText(requireContext(), "Please select a location on the map", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Set default location to college campus (NIT Calicut)
        val defaultLocation = LatLng(11.3215, 75.9342)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
        
        // Add marker when user taps on map
        map.setOnMapClickListener { location ->
            selectedLocation = location
            map.clear()
            map.addMarker(MarkerOptions()
                .position(location)
                .title("Selected Location")
                .snippet("${location.latitude}, ${location.longitude}")
            )?.showInfoWindow()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}

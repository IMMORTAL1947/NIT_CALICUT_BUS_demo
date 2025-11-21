package com.example.nit_calciut_bus_demo.ui.theme

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.nit_calciut_bus_demo.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class MapsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Default tab: Bus Stops
        if (savedInstanceState == null) {
            loadFragment(BusStopsFragment())
            bottomNav.selectedItemId = R.id.navigation_bus_stops
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_bus_stops -> {
                    loadFragment(BusStopsFragment())
                    true
                }
                R.id.navigation_routes -> {
                    loadFragment(RoutesFragment())
                    true
                }
                R.id.navigation_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}


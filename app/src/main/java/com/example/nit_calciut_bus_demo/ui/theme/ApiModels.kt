package com.example.nit_calciut_bus_demo.ui.theme

// Data models matching backend JSON

data class CollegeConfig(
    val code: String,
    val name: String?,
    val stops: List<Stop> = emptyList(),
    val routes: List<Route> = emptyList(),
    val buses: List<Bus> = emptyList()
)

data class Stop(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double
)

data class Route(
    val id: String,
    val name: String,
    val color: String,
    val stopIds: List<String>
)

data class Bus(
    val id: String,
    val name: String,
    val routeId: String?
)

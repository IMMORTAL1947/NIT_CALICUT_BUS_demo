package com.example.nit_calciut_bus_demo.ui.theme

import com.google.android.gms.maps.model.LatLng

data class SpecialRequest(
    val id: String,
    val route: String,
    val pickup: LatLng?,
    val destination: String,
    val dateTime: String,
    val passengers: Int,
    val purpose: String,
    val roundTrip: Boolean,
    val status: RequestStatus,
    val lastUpdated: Long
)

enum class RequestStatus { PENDING, APPROVED, REJECTED }

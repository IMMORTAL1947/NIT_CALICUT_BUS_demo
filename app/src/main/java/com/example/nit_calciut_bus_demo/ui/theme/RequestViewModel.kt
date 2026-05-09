package com.example.nit_calciut_bus_demo.ui.theme

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

class RequestViewModel : ViewModel() {
    private val _requests = MutableLiveData<List<SpecialRequest>>(emptyList())
    val requests: LiveData<List<SpecialRequest>> = _requests

    private var pollingThread: Thread? = null
    private var appCtx: Context? = null

    fun loadRequests(context: Context) {
        appCtx = context.applicationContext
        fetchOnce()
        startPolling()
    }

    private fun fetchOnce() {
        val ctx = appCtx ?: return
        Thread {
            try {
                val base = AppPrefs.getServerUrl(ctx).trimEnd('/')
                val code = AppPrefs.getCollegeCode(ctx) ?: ""
                val url = java.net.URL("${base}/api/colleges/${code}/requests")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val s = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(s)
                    val list = mutableListOf<SpecialRequest>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.optString("id", "${System.currentTimeMillis()}_${i}")
                        val route = o.optString("route", "")
                        val dest = o.optString("destination", "")
                        val dt = o.optString("dateTime", "")
                        val pass = o.optInt("passengers", 1)
                        val pur = o.optString("purpose", "")
                        val round = o.optBoolean("roundTrip", false)
                        val status = when (o.optString("status", "PENDING").uppercase()) {
                            "APPROVED" -> RequestStatus.APPROVED
                            "REJECTED" -> RequestStatus.REJECTED
                            else -> RequestStatus.PENDING
                        }
                        val last = o.optLong("lastUpdated", System.currentTimeMillis())
                        val pickup = if (o.has("pickupLat") && o.has("pickupLng")) LatLng(o.optDouble("pickupLat"), o.optDouble("pickupLng")) else null
                        list.add(SpecialRequest(id, route, pickup, dest, dt, pass, pur, round, status, last))
                    }
                    _requests.postValue(list)
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("RequestVM", "fetch error", e)
            }
        }.start()
    }

    fun submitRequest(context: Context, payload: JSONObject, onComplete: (Boolean)->Unit) {
        Thread {
            try {
                val base = AppPrefs.getServerUrl(context).trimEnd('/')
                val code = AppPrefs.getCollegeCode(context) ?: ""
                val url = java.net.URL("${base}/api/colleges/${code}/requests")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }
                val ok = conn.responseCode in 200..299
                conn.disconnect()
                onComplete(ok)
                if (ok) fetchOnce()
            } catch (e: Exception) {
                Log.e("RequestVM", "submit error", e)
                onComplete(false)
            }
        }.start()
    }

    private fun startPolling() {
        pollingThread?.interrupt()
        pollingThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    fetchOnce()
                    Thread.sleep(15000)
                }
            } catch (_: InterruptedException) {
            }
        }
        pollingThread!!.start()
    }

    override fun onCleared() {
        super.onCleared()
        pollingThread?.interrupt()
        pollingThread = null
    }
}

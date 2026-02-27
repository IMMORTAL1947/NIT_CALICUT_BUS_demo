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
import kotlin.math.abs

data class CampusRouteNode(val id: String?, val name: String?, val latLng: LatLng)
data class CampusRouteResult(
    val nodes: List<CampusRouteNode>,
    val totalDistanceMeters: Int,
    val estimatedTimeSeconds: Int,
    val steps: List<String>,
    val reason: String?
)

data class StopState(
    val index: Int,
    val name: String,
    val scheduledTime: String?,
    val status: StopStatus,
    val showBusHere: Boolean
)

enum class StopStatus { COMPLETED, CURRENT, UPCOMING }

data class RouteUiState(
    val routeName: String?,
    val busName: String?,
    val busOptions: List<Pair<String, String>>, // busId -> name
    val defaultBusIndex: Int,
    val routePoints: List<LatLng>,
    val stopMarkers: List<Pair<LatLng, String>>,
    val busLatLng: LatLng?,
    val routingMode: RoutingMode,
    val dijkstraPath: List<LatLng>,
    val dijkstraSteps: List<String>,
    val loading: Boolean,
    val error: String?
)

class RoutesViewModel : ViewModel() {
    private val _uiState = MutableLiveData<RouteUiState?>()
    val uiState: LiveData<RouteUiState?> = _uiState

    private val _stopStates = MutableLiveData<List<StopState>>()
    val stopStates: LiveData<List<StopState>> = _stopStates

    private val _routeResponse = MutableLiveData<CampusRouteResult?>()
    val routeResponse: LiveData<CampusRouteResult?> = _routeResponse
    private val _routeError = MutableLiveData<String?>()
    val routeError: LiveData<String?> = _routeError

    private var configJson: JSONObject? = null
    private var selectedBusIndex: Int = 0
    private var busOptions: List<Pair<String, String>> = emptyList()
    private var routePoints: List<LatLng> = emptyList()
    private var stopOrder: List<String> = emptyList() // stop IDs in order
    private var stopById: Map<String, StopMeta> = emptyMap()
    private var routeName: String? = null
    private var currentBusLatLng: LatLng? = null
    private var pollingThread: Thread? = null
    private var userLocation: Location? = null
    private var routingMode: RoutingMode = RoutingMode.GOOGLE
    private var dijkstraPath: List<LatLng> = emptyList()
    private var dijkstraSteps: List<String> = emptyList()
    private var selectedStopIndex: Int? = null
    private var loading: Boolean = false
    private var error: String? = null
    private var appContext: Context? = null

    fun setUserLocation(loc: Location?) {
        userLocation = loc
    }

    fun loadConfig(context: Context) {
        appContext = context.applicationContext
        val code = AppPrefs.getCollegeCode(context)
        val baseUrl = AppPrefs.getServerUrl(context)
        if (code.isNullOrEmpty()) {
            _uiState.postValue(null)
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
                    configJson = JSONObject(response)
                    prepareInitialState()
                    startLivePolling(baseUrl, code)
                } else {
                    Log.e("RoutesVM", "Config fetch failed: ${conn.responseCode}")
                    _uiState.postValue(null)
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("RoutesVM", "Error fetching config", e)
                _uiState.postValue(null)
            }
        }.start()
    }

    fun selectBus(index: Int) {
        selectedBusIndex = index.coerceIn(0, busOptions.lastIndex)
        // Recompute route points and stop states for selected bus
        recomputeRouteForSelectedBus()
        publishUi()
    }

    fun setRoutingMode(mode: RoutingMode) {
        routingMode = mode
        // Clear Dijkstra overlays when switching back to Google mode
        if (routingMode == RoutingMode.GOOGLE) {
            dijkstraPath = emptyList()
            dijkstraSteps = emptyList()
            error = null
            loading = false
            publishUi()
        } else {
            // If a stop is already selected and location available, fetch immediately
            selectedStopIndex?.let { idx ->
                requestDijkstraForStop(idx)
            }
        }
    }

    private fun prepareInitialState() {
        val json = configJson ?: return
        // Build stops map
        val stopsArr = json.getJSONArray("stops")
        val stopsMap = mutableMapOf<String, StopMeta>()
        for (i in 0 until stopsArr.length()) {
            val s = stopsArr.getJSONObject(i)
            val id = s.getString("id")
            val name = s.getString("name")
            val lat = s.getDouble("lat")
            val lng = s.getDouble("lng")
            stopsMap[id] = StopMeta(id, name, LatLng(lat, lng))
        }
        stopById = stopsMap

        // Build bus options
        val busesArr = json.optJSONArray("buses") ?: JSONArray()
        val options = mutableListOf<Pair<String, String>>()
        val routeByBus = mutableMapOf<String, String>()
        for (i in 0 until busesArr.length()) {
            val b = busesArr.getJSONObject(i)
            val id = b.getString("id")
            val name = b.getString("name")
            val routeId = b.optString("routeId", "")
            options.add(Pair(id, name))
            if (routeId.isNotEmpty()) routeByBus[id] = routeId
        }
        busOptions = options

        // Default bus selection: 0; could adjust via nearest later
        selectedBusIndex = 0

        recomputeRouteForSelectedBus()
        publishUi()
    }

    private fun recomputeRouteForSelectedBus() {
        val json = configJson ?: return
        if (busOptions.isEmpty()) return
        val (busId, busName) = busOptions[selectedBusIndex]

        // find route for this bus
        val busesArr = json.optJSONArray("buses") ?: JSONArray()
        var routeId: String? = null
        for (i in 0 until busesArr.length()) {
            val b = busesArr.getJSONObject(i)
            if (b.getString("id") == busId) {
                val rid = b.optString("routeId", "")
                routeId = if (rid.isNotEmpty()) rid else null
                break
            }
        }
        val routesArr = json.getJSONArray("routes")
        var chosenRoute: JSONObject? = null
        for (i in 0 until routesArr.length()) {
            val r = routesArr.getJSONObject(i)
            if (routeId == null || r.getString("id") == routeId) {
                chosenRoute = r
                break
            }
        }
        if (chosenRoute == null && routesArr.length() > 0) chosenRoute = routesArr.getJSONObject(0)

        routeName = chosenRoute?.optString("name")
        val stopIds = chosenRoute?.getJSONArray("stopIds") ?: JSONArray()
        stopOrder = (0 until stopIds.length()).map { stopIds.getString(it) }
        routePoints = stopOrder.mapNotNull { id -> stopById[id]?.latLng }

        // Scheduled times map
        val stopTimes = chosenRoute?.optJSONObject("stopTimes")
        val states = stopOrder.mapIndexed { idx, id ->
            val meta = stopById[id]
            StopState(
                index = idx,
                name = meta?.name ?: id,
                scheduledTime = stopTimes?.optString(id),
                status = StopStatus.UPCOMING,
                showBusHere = false
            )
        }
        _stopStates.postValue(states)
        publishUi()
    }

    private fun publishUi() {
        val (busId, busName) = busOptions.getOrNull(selectedBusIndex) ?: Pair("", "")
        _uiState.postValue(
            RouteUiState(
                routeName = routeName,
                busName = busName,
                busOptions = busOptions,
                defaultBusIndex = selectedBusIndex,
                routePoints = routePoints,
                stopMarkers = routePoints.mapIndexed { i, latLng ->
                    Pair(latLng, stopById[stopOrder[i]]?.name ?: stopOrder[i])
                },
                busLatLng = currentBusLatLng,
                routingMode = routingMode,
                dijkstraPath = dijkstraPath,
                dijkstraSteps = dijkstraSteps,
                loading = loading,
                error = error
            )
        )
    }

    fun getSelectedStopIndex(): Int? = selectedStopIndex

    private fun startLivePolling(baseUrl: String, code: String) {
        pollingThread?.interrupt()
        pollingThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val url = java.net.URL("${baseUrl}/api/colleges/${code}/live")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    var busPos: LatLng? = null
                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        val arr = json.getJSONArray("buses")
                        val (targetBusId, _) = busOptions.getOrNull(selectedBusIndex) ?: Pair("", "")
                        var latestTs = 0L
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val id = o.getString("busId")
                            val ts = o.optLong("ts", System.currentTimeMillis())
                            if (targetBusId.isNotEmpty()) {
                                if (id == targetBusId) {
                                    busPos = LatLng(o.getDouble("lat"), o.getDouble("lng"))
                                    latestTs = ts
                                    break
                                }
                            } else {
                                if (ts >= latestTs) {
                                    busPos = LatLng(o.getDouble("lat"), o.getDouble("lng"))
                                    latestTs = ts
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    if (busPos != null) {
                        currentBusLatLng = busPos
                        computeStopProgress(busPos!!)
                        publishUi()
                    }
                    Thread.sleep(5000)
                }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                Log.e("RoutesVM", "polling error", e)
            }
        }
        pollingThread!!.start()
    }

    fun onStopClicked(state: StopState) {
        selectedStopIndex = state.index
        if (routingMode == RoutingMode.DIJKSTRA) {
            requestDijkstraForStop(state.index)
        } else {
            // In Google mode, we keep UI as-is; Route to be launched from Fragment via intent if needed
            publishUi()
        }
    }

    // Allow triggering by index when Fragment obtains location later
    fun onStopIndexSelected(index: Int) {
        selectedStopIndex = index
        if (routingMode == RoutingMode.DIJKSTRA) {
            requestDijkstraForStop(index)
        } else {
            publishUi()
        }
    }

    private fun requestDijkstraForStop(index: Int) {
        val stopId = stopOrder.getOrNull(index) ?: return
        val loc = userLocation
        if (loc == null) {
            error = "Location unavailable"
            loading = false
            publishUi()
            return
        }
        loading = true
        error = null
        _routeError.postValue(null)
        publishUi()

        Thread {
            try {
                val ctx = appContext ?: return@Thread
                val baseUrl = AppPrefs.getServerUrl(ctx).trimEnd('/')
                val collegeCode = configJson?.optString("code")
                    ?.takeIf { it.isNotBlank() }
                    ?: (AppPrefs.getCollegeCode(ctx) ?: "")

                if (collegeCode.isBlank()) {
                    _routeResponse.postValue(null)
                    _routeError.postValue("College code is missing.")
                    loading = false
                    publishUi()
                    return@Thread
                }

                val encodedCollege = java.net.URLEncoder.encode(collegeCode, "UTF-8")
                val encodedStopId = java.net.URLEncoder.encode(stopId, "UTF-8")
                val urlStr = StringBuilder()
                    .append(baseUrl)
                    .append("/api/colleges/")
                    .append(encodedCollege)
                    .append("/route-to-stop?userLat=")
                    .append(loc.latitude)
                    .append("&userLng=")
                    .append(loc.longitude)
                    .append("&stopId=")
                    .append(encodedStopId)
                    .append("&mode=shortest&algo=dijkstra")
                    .toString()
                Log.d("RoutesVM", "Dijkstra URL: $urlStr")
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val nodesArr = json.getJSONArray("nodes")
                    val nodeList = mutableListOf<CampusRouteNode>()
                    val pts = mutableListOf<LatLng>()
                    for (i in 0 until nodesArr.length()) {
                        val n = nodesArr.getJSONObject(i)
                        val lat = n.optDouble("lat")
                        val lng = n.optDouble("lng")
                        if (!lat.isNaN() && !lng.isNaN()) {
                            val ll = LatLng(lat, lng)
                            pts.add(ll)
                            nodeList.add(CampusRouteNode(n.optString("id"), n.optString("name"), ll))
                        }
                    }
                    val stepsArr = json.optJSONArray("steps") ?: JSONArray()
                    val steps = mutableListOf<String>()
                    for (i in 0 until stepsArr.length()) steps.add(stepsArr.getString(i))
                    dijkstraPath = pts
                    dijkstraSteps = steps
                    error = null
                    val result = CampusRouteResult(
                        nodes = nodeList,
                        totalDistanceMeters = json.optInt("totalDistanceMeters", 0),
                        estimatedTimeSeconds = json.optInt("estimatedTimeSeconds", 0),
                        steps = steps,
                        reason = json.optString("reason")
                    )
                    _routeResponse.postValue(result)
                    _routeError.postValue(null)
                } else {
                    _routeResponse.postValue(null)
                    val errText = try {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }
                    } catch (_: Exception) { null }
                    val msg = if (!errText.isNullOrEmpty()) {
                        try {
                            JSONObject(errText).optString("error", "Campus routing failed (${conn.responseCode}).")
                        } catch (_: Exception) {
                            "Campus routing failed (${conn.responseCode})."
                        }
                    } else {
                        "Campus routing failed (${conn.responseCode})."
                    }
                    val detailed = if (conn.responseCode == 404) {
                        "$msg Check server URL and ensure /api/colleges/{code}/route-to-stop exists."
                    } else msg
                    _routeError.postValue(detailed)
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("RoutesVM", "dijkstra fetch error", e)
                _routeResponse.postValue(null)
                _routeError.postValue("Campus routing unavailable.")
            } finally {
                loading = false
                publishUi()
            }
        }.start()
    }


    private fun computeStopProgress(bus: LatLng) {
        if (routePoints.isEmpty()) return
        // Find nearest stop index
        var nearestIdx = 0
        var minDist = Float.MAX_VALUE
        for (i in routePoints.indices) {
            val d = distanceMeters(bus, routePoints[i])
            if (d < minDist) {
                minDist = d
                nearestIdx = i
            }
        }
        val threshold = 150f
        val currentIndex = if (minDist <= threshold) nearestIdx else findSegmentIndex(bus)

        val states = _stopStates.value?.map { it.copy() }?.toMutableList() ?: mutableListOf()
        if (states.isEmpty()) return
        for (i in states.indices) {
            states[i] = states[i].copy(
                status = when {
                    currentIndex == i && minDist <= threshold -> StopStatus.CURRENT
                    i < currentIndex -> StopStatus.COMPLETED
                    else -> StopStatus.UPCOMING
                },
                showBusHere = currentIndex == i && minDist > threshold
            )
        }
        _stopStates.postValue(states)
    }

    private fun findSegmentIndex(bus: LatLng): Int {
        // Determine between which two stops the bus lies (nearest segment)
        var bestIdx = 0
        var bestDist = Double.MAX_VALUE
        for (i in 0 until routePoints.size - 1) {
            val a = routePoints[i]
            val b = routePoints[i + 1]
            val d = pointToSegmentDistance(bus, a, b)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun distanceMeters(p1: LatLng, p2: LatLng): Float {
        val res = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res)
        return res[0]
    }

    private fun pointToSegmentDistance(p: LatLng, a: LatLng, b: LatLng): Double {
        // Approximate using simple projection in Lat/Lng space
        val ax = a.latitude
        val ay = a.longitude
        val bx = b.latitude
        val by = b.longitude
        val px = p.latitude
        val py = p.longitude
        val vx = bx - ax
        val vy = by - ay
        val wx = px - ax
        val wy = py - ay
        val c1 = vx * wx + vy * wy
        val c2 = vx * vx + vy * vy
        val t = if (c2 > 0) (c1 / c2) else 0.0
        val tt = t.coerceIn(0.0, 1.0)
        val projX = ax + tt * vx
        val projY = ay + tt * vy
        val dx = px - projX
        val dy = py - projY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    override fun onCleared() {
        super.onCleared()
        pollingThread?.interrupt()
        pollingThread = null
    }
}

data class StopMeta(val id: String, val name: String, val latLng: LatLng)

enum class RoutingMode { GOOGLE, DIJKSTRA }

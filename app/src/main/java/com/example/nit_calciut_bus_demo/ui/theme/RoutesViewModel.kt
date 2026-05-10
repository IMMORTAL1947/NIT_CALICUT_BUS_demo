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
    ,
    val directionReversed: Boolean = false,
    val crowdLevel: String? = null,    // LOW, MEDIUM, HIGH, FULL, UNAVAILABLE
    val crowdColor: String? = null,    // hex color
    val crowdPercent: Int = 0,         // 0-100 occupancy
    val crowdPeopleCount: Int = 0      // actual people count
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
    private var selectedRouteIndex: Int = 0
    private var routeOptions: List<Pair<String, String>> = emptyList() // routeId -> name
    private var routePoints: List<LatLng> = emptyList()
    private var stopOrder: List<String> = emptyList() // stop IDs in order
    private var stopById: Map<String, StopMeta> = emptyMap()
    private var routeName: String? = null
    private var selectedBusId: String? = null  // bus serving selected route
    private var selectedBusName: String? = null
    private var currentBusLatLng: LatLng? = null
    private var pollingThread: Thread? = null
    private var userLocation: Location? = null
    // Crowd monitoring state
    private var crowdLevel: String? = null
    private var crowdColor: String? = null
    private var crowdPercent: Int = 0
    private var crowdPeopleCount: Int = 0
    private var crowdPollingThread: Thread? = null
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

    // selectBus() and setDirectionReversed() have been replaced by selectRoute()
    // Direction selection has been removed from the UI

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

        // Build route options
        val routesArr = json.optJSONArray("routes") ?: JSONArray()
        val routeOpts = mutableListOf<Pair<String, String>>()
        for (i in 0 until routesArr.length()) {
            val r = routesArr.getJSONObject(i)
            val id = r.getString("id")
            val name = r.getString("name")
            routeOpts.add(Pair(id, name))
        }
        routeOptions = routeOpts

        // Default route selection: 0
        selectedRouteIndex = 0

        recomputeRouteForSelectedRoute()
        publishUi()
    }

    private fun recomputeRouteForSelectedRoute() {
        val json = configJson ?: return
        if (routeOptions.isEmpty()) return
        val (routeId, routeName) = routeOptions[selectedRouteIndex]
        this.routeName = routeName

        // Find first bus serving this route
        val busesArr = json.optJSONArray("buses") ?: JSONArray()
        var busId = ""
        var busName = ""
        for (i in 0 until busesArr.length()) {
            val b = busesArr.getJSONObject(i)
            if (b.optString("routeId", "").lowercase() == routeId.lowercase()) {
                busId = b.getString("id")
                busName = b.getString("name")
                break
            }
        }
        
        selectedBusId = busId
        selectedBusName = busName

        // Get the route details from routes array
        val routesArr = json.optJSONArray("routes") ?: JSONArray()
        var chosenRoute: JSONObject? = null
        for (i in 0 until routesArr.length()) {
            val r = routesArr.getJSONObject(i)
            if (r.getString("id").lowercase() == routeId.lowercase()) {
                chosenRoute = r
                break
            }
        }

        val stopIds = chosenRoute?.getJSONArray("stopIds") ?: JSONArray()
        stopOrder = (0 until stopIds.length()).map { stopIds.getString(it) }
        routePoints = stopOrder.mapNotNull { id -> stopById[id]?.latLng }

        // Fetch schedules to get departure times for this bus+route combo
        var departureTime: String? = null
        if (busId.isNotEmpty()) {
            val schedulesArr = json.optJSONArray("schedules") ?: JSONArray()
            for (i in 0 until schedulesArr.length()) {
                val sch = schedulesArr.getJSONObject(i)
                if (sch.optString("routeId").lowercase() == routeId.lowercase() && sch.optString("busId") == busId) {
                    departureTime = sch.optString("departureTime")
                    break
                }
            }
        }

        val states = stopOrder.mapIndexed { idx, id ->
            val meta = stopById[id]
            StopState(
                index = idx,
                name = meta?.name ?: id,
                scheduledTime = if (idx == 0) departureTime else null,
                status = StopStatus.UPCOMING,
                showBusHere = false
            )
        }
        _stopStates.postValue(states)
        
        // Start crowd polling for the selected bus
        if (busId.isNotEmpty()) {
            startCrowdPolling(busId)
        }
    }

    fun selectRoute(index: Int) {
        selectedRouteIndex = index.coerceIn(0, routeOptions.lastIndex)
        recomputeRouteForSelectedRoute()
        publishUi()
    }

    private fun publishUi() {
        _uiState.postValue(
            RouteUiState(
                routeName = routeName,
                busName = selectedBusName,
                busOptions = routeOptions,  // Now contains route options
                defaultBusIndex = selectedRouteIndex,
                routePoints = routePoints,
                stopMarkers = routePoints.mapIndexed { i, latLng ->
                    Pair(latLng, stopById[stopOrder.getOrNull(i)]?.name ?: stopOrder.getOrNull(i) ?: "Stop")
                },
                busLatLng = currentBusLatLng,
                routingMode = routingMode,
                dijkstraPath = dijkstraPath,
                dijkstraSteps = dijkstraSteps,
                loading = loading,
                error = error,
                directionReversed = false,
                crowdLevel = crowdLevel,
                crowdColor = crowdColor,
                crowdPercent = crowdPercent,
                crowdPeopleCount = crowdPeopleCount
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
                        val (targetBusId, _) = routeOptions.getOrNull(selectedRouteIndex) ?: Pair("", "")
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
        crowdPollingThread?.interrupt()
        crowdPollingThread = null
    }

    private fun startCrowdPolling(busId: String) {
        // Cancel previous crowd polling thread if any
        crowdPollingThread?.interrupt()
        crowdPollingThread = null
        
        if (busId.isEmpty()) {
            crowdLevel = null
            crowdColor = null
            crowdPercent = 0
            crowdPeopleCount = 0
            publishUi()
            return
        }
        
        crowdPollingThread = Thread {
            try {
                while (true) {
                    val ctx = appContext ?: return@Thread
                    val baseUrl = AppPrefs.getServerUrl(ctx).trimEnd('/')
                    val collegeCode = AppPrefs.getCollegeCode(ctx) ?: return@Thread
                    
                    try {
                        val url = java.net.URL("${baseUrl}/api/crowd-status/${busId}")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        
                        if (conn.responseCode == 200) {
                            val response = conn.inputStream.bufferedReader().readText()
                            val json = JSONObject(response)
                            
                            crowdLevel = json.optString("crowdLevel", "UNAVAILABLE")
                            crowdColor = json.optString("color", "#CCCCCC")
                            crowdPercent = json.optInt("occupancyPercent", 0)
                            crowdPeopleCount = json.optInt("peopleCount", 0)
                            publishUi()
                        } else {
                            crowdLevel = "UNAVAILABLE"
                            crowdColor = "#CCCCCC"
                            crowdPercent = 0
                            crowdPeopleCount = 0
                            publishUi()
                        }
                        conn.disconnect()
                    } catch (e: Exception) {
                        Log.e("RoutesVM", "Crowd fetch error: ${e.message}")
                        crowdLevel = "UNAVAILABLE"
                        crowdColor = "#CCCCCC"
                        crowdPercent = 0
                        crowdPeopleCount = 0
                        publishUi()
                    }
                    
                    // Poll every 10 seconds
                    Thread.sleep(10000)
                }
            } catch (_: InterruptedException) {
            }
        }
        crowdPollingThread!!.start()
    }
}

data class StopMeta(val id: String, val name: String, val latLng: LatLng)

enum class RoutingMode { GOOGLE, DIJKSTRA }

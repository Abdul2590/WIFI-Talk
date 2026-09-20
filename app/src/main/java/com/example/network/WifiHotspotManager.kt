package com.example.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.hotspot2.PasspointConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.AutoWifiState
import com.example.model.DiscoveredWifiNetwork
import com.example.model.WifiHotspotState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WifiHotspotManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deviceId: String,
    private val onNetworkChanged: () -> Unit
) {
    private val tag = "WifiHotspotManager"

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _hotspotState = MutableStateFlow(WifiHotspotState())
    val hotspotState: StateFlow<WifiHotspotState> = _hotspotState.asStateFlow()

    private val _autoWifiState = MutableStateFlow(AutoWifiState())
    val autoWifiState: StateFlow<AutoWifiState> = _autoWifiState.asStateFlow()

    private val _discoveredNetworks = MutableStateFlow<List<DiscoveredWifiNetwork>>(emptyList())
    val discoveredNetworks: StateFlow<List<DiscoveredWifiNetwork>> = _discoveredNetworks.asStateFlow()

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null

    private var scanReceiver: BroadcastReceiver? = null
    private var periodicScanJob: Job? = null

    companion object {
        const val WALKIE_SSID_PREFIX = "Walkie"
    }

    private var _currentDynamicSsid: String = generateDynamicOpenSsid()
    val currentDynamicSsid: String get() = _currentDynamicSsid

    fun generateDynamicOpenSsid(): String {
        val cleanDevicePart = deviceId.replace(Regex("[^A-Za-z0-9]"), "").takeLast(4).uppercase()
        val randomSuffix = if (cleanDevicePart.length >= 2) cleanDevicePart else (1000..9999).random().toString()
        val generated = "Walkie-Open-$randomSuffix"
        _currentDynamicSsid = generated
        return generated
    }

    fun setDynamicSsid(ssid: String) {
        if (ssid.isNotBlank()) {
            _currentDynamicSsid = ssid.trim()
        }
    }

    init {
        registerScanReceiver()
        startPeriodicScan()
    }

    /**
     * Checks if the required permission to start a local hotspot is currently granted.
     */
    fun hasHotspotPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if permission to scan for Wi-Fi networks is currently granted.
     */
    fun hasScanPermission(): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return hasLocation || hasNearby
    }

    /**
     * Creates a 2.4 GHz Wi-Fi Open SSID / Local-Only Hotspot directly from the application.
     * User requirement: hosted SSID should have no security (open network with no password).
     */
    fun create24GhzHotspot() {
        if (_hotspotState.value.isHosting || _hotspotState.value.isStarting) {
            Log.w(tag, "Hotspot is already hosting or starting")
            return
        }

        if (!hasHotspotPermission()) {
            Log.w(tag, "Nearby devices permission is required to host a hotspot")
            _hotspotState.value = WifiHotspotState(
                isHosting = false,
                isStarting = false,
                statusMessage = "Nearby Devices permission required"
            )
            return
        }

        val dynamicSsid = if (_currentDynamicSsid.isBlank()) generateDynamicOpenSsid() else _currentDynamicSsid

        _hotspotState.value = _hotspotState.value.copy(
            isStarting = true,
            ssid = dynamicSsid,
            password = "",
            isOpenNoPassword = true,
            statusMessage = "Starting Open 2.4 GHz Hotspot ($dynamicSsid)..."
        )

        val callback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                super.onStarted(reservation)
                hotspotReservation = reservation

                var activeSsid = dynamicSsid
                var activePassword = ""
                var isOpenSecurity = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val softApConfig = reservation.softApConfiguration
                    if (softApConfig != null) {
                        val osSsid = softApConfig.ssid
                        if (!osSsid.isNullOrEmpty()) {
                            activeSsid = osSsid
                        }
                        val passphrase = softApConfig.passphrase
                        val secType = softApConfig.securityType
                        // SECURITY_TYPE_OPEN is 0
                        if (!passphrase.isNullOrEmpty() && secType != 0) {
                            activePassword = passphrase
                            isOpenSecurity = false
                        } else {
                            activePassword = ""
                            isOpenSecurity = true
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val wifiConfig = reservation.wifiConfiguration
                    if (wifiConfig != null) {
                        val osSsid = wifiConfig.SSID?.replace("\"", "")
                        if (!osSsid.isNullOrEmpty()) {
                            activeSsid = osSsid
                        }
                        val key = wifiConfig.preSharedKey?.replace("\"", "")
                        if (!key.isNullOrEmpty()) {
                            activePassword = key
                            isOpenSecurity = false
                        } else {
                            activePassword = ""
                            isOpenSecurity = true
                        }
                    }
                }

                _currentDynamicSsid = activeSsid

                _hotspotState.value = WifiHotspotState(
                    isHosting = true,
                    isStarting = false,
                    ssid = activeSsid,
                    password = activePassword,
                    isOpenNoPassword = isOpenSecurity,
                    band = "2.4 GHz",
                    ipAddress = "192.168.43.1",
                    statusMessage = if (isOpenSecurity) {
                        "Active: Broadcasting Open \"$activeSsid\" (No password)"
                    } else {
                        "Active: Broadcasting \"$activeSsid\""
                    }
                )
                Log.i(tag, "2.4GHz Hotspot active: $activeSsid (Open: $isOpenSecurity)")

                scope.launch(Dispatchers.IO) {
                    delay(1000)
                    onNetworkChanged()
                }
            }

            override fun onStopped() {
                super.onStopped()
                Log.i(tag, "Hotspot stopped")
                hotspotReservation = null
                _hotspotState.value = WifiHotspotState(
                    isHosting = false,
                    isStarting = false,
                    statusMessage = "Hotspot Stopped"
                )
                onNetworkChanged()
            }

            override fun onFailed(reason: Int) {
                super.onFailed(reason)
                val reasonStr = when (reason) {
                    ERROR_NO_CHANNEL -> "No Wi-Fi channel available"
                    ERROR_GENERIC -> "Generic hotspot error"
                    ERROR_INCOMPATIBLE_MODE -> "Incompatible mode"
                    ERROR_TETHERING_DISALLOWED -> "Tethering disallowed"
                    else -> "Error code $reason"
                }
                Log.w(tag, "Hotspot failed: $reasonStr")
                hotspotReservation = null
                _hotspotState.value = WifiHotspotState(
                    isHosting = false,
                    isStarting = false,
                    statusMessage = "Failed: $reasonStr"
                )
            }
        }

        try {
            var startedWithCustomConfig = false
            // On Android 11+ (API 30+), attempt to configure OPEN security via reflection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val builderClass = Class.forName("android.net.wifi.SoftApConfiguration\$Builder")
                    val builder = builderClass.getDeclaredConstructor().newInstance()

                    // Try setting dynamic open SSID
                    try {
                        val setSsidMethod = builderClass.getMethod("setSsid", String::class.java)
                        setSsidMethod.invoke(builder, dynamicSsid)
                    } catch (_: Exception) {}

                    // Set security type OPEN (0) and null passphrase
                    try {
                        val setPassphraseMethod = builderClass.getMethod(
                            "setPassphrase",
                            String::class.java,
                            Int::class.javaPrimitiveType
                        )
                        setPassphraseMethod.invoke(builder, null, 0 /* SECURITY_TYPE_OPEN */)
                    } catch (_: Exception) {}

                    val buildMethod = builderClass.getMethod("build")
                    val config = buildMethod.invoke(builder)

                    val startMethod = wifiManager.javaClass.getMethod(
                        "startLocalOnlyHotspotWithConfiguration",
                        Class.forName("android.net.wifi.SoftApConfiguration"),
                        java.util.concurrent.Executor::class.java,
                        WifiManager.LocalOnlyHotspotCallback::class.java
                    )
                    val executor = ContextCompat.getMainExecutor(context)
                    startMethod.invoke(wifiManager, config, executor, callback)
                    startedWithCustomConfig = true
                    Log.i(tag, "Initiated open hotspot with custom config: $dynamicSsid")
                } catch (e: Exception) {
                    Log.d(tag, "startLocalOnlyHotspotWithConfiguration unavailable, fallback to standard: ${e.message}")
                }
            }

            if (!startedWithCustomConfig) {
                wifiManager.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
            }
        } catch (e: SecurityException) {
            Log.w(tag, "Missing permission for starting hotspot: ${e.message}")
            _hotspotState.value = WifiHotspotState(
                isHosting = false,
                isStarting = false,
                statusMessage = "Nearby Devices permission required"
            )
        } catch (e: Exception) {
            Log.w(tag, "Error starting hotspot: ${e.message}")
            _hotspotState.value = WifiHotspotState(
                isHosting = false,
                isStarting = false,
                statusMessage = "Hotspot error: ${e.localizedMessage ?: "Unknown"}"
            )
        }
    }

    /**
     * Stops the running hotspot.
     */
    fun stopHotspot() {
        try {
            hotspotReservation?.close()
            hotspotReservation = null
            _hotspotState.value = WifiHotspotState(
                isHosting = false,
                isStarting = false,
                statusMessage = "Hotspot Inactive"
            )
            onNetworkChanged()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping hotspot", e)
        }
    }

    /**
     * Auto-connects to a discovered Walkie talkie peer hotspot.
     * Hosted SSIDs have no security (open networks); no password is required.
     */
    fun connectToWalkieNetwork(targetSsid: String, explicitPassword: String? = null) {
        if (_hotspotState.value.isHosting) {
            Log.w(tag, "Device is currently hosting. Stop hotspot before connecting to another device.")
            _autoWifiState.value = _autoWifiState.value.copy(
                statusMessage = "Cannot connect while hosting hotspot"
            )
            return
        }

        disconnectFromPeerNetwork()

        // Find discovered network to determine if it requires password
        val discovered = _discoveredNetworks.value.firstOrNull { it.ssid == targetSsid }
        val isNetworkOpen = explicitPassword.isNullOrEmpty() && (discovered == null || discovered.isOpen)

        _autoWifiState.value = _autoWifiState.value.copy(
            isConnecting = true,
            targetSsid = targetSsid,
            statusMessage = if (isNetworkOpen) "Connecting to Open SSID: $targetSsid (no password)..." else "Connecting to $targetSsid..."
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val builder = WifiNetworkSpecifier.Builder().setSsid(targetSsid)
                // For open networks, do NOT set passphrase, allowing automatic connection without password
                if (!isNetworkOpen && !explicitPassword.isNullOrEmpty()) {
                    builder.setWpa2Passphrase(explicitPassword)
                }

                val specifier = builder.build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.i(tag, "Peer Wi-Fi network available: $targetSsid")
                        boundNetwork = network
                        connectivityManager.bindProcessToNetwork(network)

                        _autoWifiState.value = _autoWifiState.value.copy(
                            isConnecting = false,
                            connectedSsid = targetSsid,
                            statusMessage = "Connected to $targetSsid"
                        )

                        updateDiscoveredNetworksConnected(targetSsid, true)

                        scope.launch(Dispatchers.IO) {
                            delay(600)
                            onNetworkChanged()
                        }
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        Log.w(tag, "Peer Wi-Fi network lost: $targetSsid")
                        if (boundNetwork == network) {
                            connectivityManager.bindProcessToNetwork(null)
                            boundNetwork = null
                        }
                        _autoWifiState.value = _autoWifiState.value.copy(
                            isConnecting = false,
                            connectedSsid = null,
                            statusMessage = "Disconnected from $targetSsid"
                        )
                        updateDiscoveredNetworksConnected(targetSsid, false)
                        onNetworkChanged()
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        Log.w(tag, "Peer Wi-Fi connection unavailable")
                        _autoWifiState.value = _autoWifiState.value.copy(
                            isConnecting = false,
                            statusMessage = "Could not connect to $targetSsid"
                        )
                    }
                }

                activeNetworkCallback = callback
                connectivityManager.requestNetwork(request, callback)
            } else {
                _autoWifiState.value = _autoWifiState.value.copy(
                    isConnecting = false,
                    statusMessage = "Manual Wi-Fi connect required on Android 9 and below"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to connect to network $targetSsid", e)
            _autoWifiState.value = _autoWifiState.value.copy(
                isConnecting = false,
                statusMessage = "Connection failed: ${e.localizedMessage ?: "Error"}"
            )
        }
    }

    fun disconnectFromPeerNetwork() {
        try {
            activeNetworkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
            activeNetworkCallback = null
            if (boundNetwork != null) {
                connectivityManager.bindProcessToNetwork(null)
                boundNetwork = null
            }
            val prevSsid = _autoWifiState.value.connectedSsid
            _autoWifiState.value = _autoWifiState.value.copy(
                isConnecting = false,
                connectedSsid = null,
                targetSsid = null,
                statusMessage = "Disconnected"
            )
            prevSsid?.let { updateDiscoveredNetworksConnected(it, false) }
            onNetworkChanged()
        } catch (e: Exception) {
            Log.e(tag, "Error disconnecting from peer network", e)
        }
    }

    fun toggleAutoConnect(enabled: Boolean) {
        _autoWifiState.value = _autoWifiState.value.copy(autoConnectEnabled = enabled)
        if (enabled) {
            scanForWalkieNetworks()
        }
    }

    fun scanForWalkieNetworks() {
        if (!hasScanPermission()) {
            _autoWifiState.value = _autoWifiState.value.copy(
                isScanning = false,
                statusMessage = "Nearby Devices or Location permission required to scan"
            )
            return
        }

        _autoWifiState.value = _autoWifiState.value.copy(
            isScanning = true,
            statusMessage = "Scanning for 2.4 GHz Walkie SSIDs..."
        )
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (e: Exception) {
            Log.w(tag, "startScan warning: ${e.message}")
            _autoWifiState.value = _autoWifiState.value.copy(isScanning = false)
        }
    }

    private fun registerScanReceiver() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION == intent?.action) {
                    processScanResults()
                }
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, filter)
    }

    private fun processScanResults() {
        try {
            @Suppress("DEPRECATION")
            val results: List<ScanResult> = wifiManager.scanResults ?: emptyList()
            val list = mutableListOf<DiscoveredWifiNetwork>()
            val connectedSsid = _autoWifiState.value.connectedSsid

            for (res in results) {
                val ssid = res.SSID ?: continue
                if (ssid.isEmpty()) continue

                val isWalkie = ssid.startsWith(WALKIE_SSID_PREFIX, ignoreCase = true) ||
                        ssid.startsWith("WalkieTalkie", ignoreCase = true) ||
                        ssid.contains("Walkie", ignoreCase = true) ||
                        ssid.startsWith("AndroidShare_", ignoreCase = true)

                val capabilities = res.capabilities ?: ""
                val isOpen = !capabilities.contains("WPA", ignoreCase = true) &&
                        !capabilities.contains("WEP", ignoreCase = true) &&
                        !capabilities.contains("PSK", ignoreCase = true) &&
                        !capabilities.contains("EAP", ignoreCase = true)

                val network = DiscoveredWifiNetwork(
                    ssid = ssid,
                    bssid = res.BSSID ?: "",
                    frequencyMhz = res.frequency,
                    level = res.level,
                    isWalkieNetwork = isWalkie,
                    isConnected = ssid == connectedSsid,
                    isOpen = isOpen,
                    securityInfo = if (isOpen) "Open (No password)" else "Secured"
                )
                list.add(network)
            }

            val sorted = list.sortedWith(
                compareByDescending<DiscoveredWifiNetwork> { it.isWalkieNetwork }
                    .thenByDescending { it.isOpen }
                    .thenByDescending { it.is24Ghz }
                    .thenByDescending { it.level }
            ).distinctBy { it.ssid }

            _discoveredNetworks.value = sorted
            _autoWifiState.value = _autoWifiState.value.copy(
                isScanning = false,
                statusMessage = if (sorted.any { it.isWalkieNetwork }) {
                    "Found ${sorted.count { it.isWalkieNetwork }} Walkie Network(s)"
                } else {
                    "Scan complete (${sorted.size} Wi-Fi networks)"
                }
            )

            // Auto-connect if enabled and peer walkie network detected
            if (_autoWifiState.value.autoConnectEnabled &&
                !_hotspotState.value.isHosting &&
                _autoWifiState.value.connectedSsid == null &&
                !_autoWifiState.value.isConnecting
            ) {
                // Prioritize open walkie networks (no password needed)
                val candidate = sorted.firstOrNull {
                    it.isWalkieNetwork && it.ssid != _hotspotState.value.ssid && it.isOpen
                } ?: sorted.firstOrNull {
                    it.isWalkieNetwork && it.ssid != _hotspotState.value.ssid
                }
                if (candidate != null) {
                    Log.i(tag, "Auto-connecting to discovered open walkie peer SSID: ${candidate.ssid}")
                    connectToWalkieNetwork(candidate.ssid)
                }
            }
        } catch (e: SecurityException) {
            Log.w(tag, "Missing location/nearby permission to read scan results", e)
            _autoWifiState.value = _autoWifiState.value.copy(isScanning = false)
        } catch (e: Exception) {
            Log.e(tag, "Error processing scan results", e)
            _autoWifiState.value = _autoWifiState.value.copy(isScanning = false)
        }
    }

    private fun updateDiscoveredNetworksConnected(ssid: String, connected: Boolean) {
        _discoveredNetworks.value = _discoveredNetworks.value.map {
            if (it.ssid == ssid) it.copy(isConnected = connected) else it.copy(isConnected = false)
        }
    }

    private fun startPeriodicScan() {
        periodicScanJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (_autoWifiState.value.autoConnectEnabled &&
                    !_hotspotState.value.isHosting &&
                    _autoWifiState.value.connectedSsid == null
                ) {
                    try {
                        @Suppress("DEPRECATION")
                        wifiManager.startScan()
                    } catch (_: Exception) {}
                }
                delay(12000) // Scan every 12 seconds
            }
        }
    }

    fun release() {
        stopHotspot()
        disconnectFromPeerNetwork()
        periodicScanJob?.cancel()
        try {
            scanReceiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
    }
}

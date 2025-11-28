package com.clipnotes.app.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.*
import com.clipnotes.app.NoteApplication
import com.clipnotes.app.data.NoteEntity
import org.json.JSONArray
import org.json.JSONObject

class NetworkDiscoveryService(private val context: Context) {
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var myRegisteredServiceName: String? = null

    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices

    companion object {
        private const val SERVICE_TYPE = "_clipnotes._tcp."
        private const val SERVICE_NAME = "ClipboardNotes"
        private const val TAG = "NetworkDiscovery"
    }

    data class DeviceInfo(
        val name: String,
        val host: String,
        val port: Int,
        val deviceId: String
    )

    fun registerService(port: Int) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service registration failed: $errorCode")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: $errorCode")
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${serviceInfo.serviceName}")
                myRegisteredServiceName = serviceInfo.serviceName
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered")
            }
        }

        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun discoverServices() {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}")
                if (service.serviceType == SERVICE_TYPE && service.serviceName != myRegisteredServiceName) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                nsdManager?.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
                nsdManager?.stopServiceDiscovery(this)
            }
        }

        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Resolve succeeded: ${serviceInfo.host}")
                val device = DeviceInfo(
                    name = serviceInfo.serviceName,
                    host = serviceInfo.host.hostAddress ?: "",
                    port = serviceInfo.port,
                    deviceId = serviceInfo.serviceName
                )
                val currentList = _discoveredDevices.value.toMutableList()
                if (!currentList.any { it.deviceId == device.deviceId }) {
                    currentList.add(device)
                    _discoveredDevices.value = currentList
                }
            }
        }

        nsdManager?.resolveService(serviceInfo, resolveListener)
    }

    fun startServer(port: Int, onReceiveRequest: (String, (Boolean) -> Unit) -> Unit) {
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                registerService(port)

                while (isActive) {
                    try {
                        val socket = serverSocket?.accept()
                        socket?.let { handleClient(it, onReceiveRequest) }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server", e)
            }
        }
    }

    private fun handleClient(socket: Socket, onReceiveRequest: (String, (Boolean) -> Unit) -> Unit) {
        scope.launch {
            try {
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

                val request = input.readLine()
                Log.d(TAG, "Received: $request")

                val response: String = when {
                    request.startsWith("SEND_NOTES:") -> {
                        val notesJson = request.substring("SEND_NOTES:".length)
                        var accepted = false
                        val responseJob = CompletableDeferred<Boolean>()
                        
                        withContext(Dispatchers.Main) {
                            onReceiveRequest(notesJson) { userAccepted ->
                                responseJob.complete(userAccepted)
                            }
                        }
                        
                        accepted = responseJob.await()
                        if (accepted) "ACCEPTED" else "REJECTED"
                    }
                    else -> "UNKNOWN"
                }

                output.write(response)
                output.newLine()
                output.flush()
                
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client", e)
            }
        }
    }

    suspend fun sendNotes(device: DeviceInfo, notes: List<NoteEntity>): String {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket(device.host, device.port)
                val output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))

                val jsonArray = JSONArray()
                notes.forEach { note ->
                    val jsonObject = JSONObject().apply {
                        put("content", note.content)
                        put("contentType", note.contentType.name)
                        put("textColor", note.textColor)
                    }
                    jsonArray.put(jsonObject)
                }

                output.write("SEND_NOTES:${jsonArray}")
                output.newLine()
                output.flush()

                val response = input.readLine()
                socket.close()

                response ?: "NO_RESPONSE"
            } catch (e: Exception) {
                Log.e(TAG, "Error sending notes", e)
                "ERROR: ${e.message}"
            }
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        registrationListener?.let { nsdManager?.unregisterService(it) }
        serverSocket?.close()
        scope.cancel()
    }
}

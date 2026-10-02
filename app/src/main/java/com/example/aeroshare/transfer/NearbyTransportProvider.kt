package com.example.aeroshare.transfer

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NearbyTransportProvider(private val context: Context) : TransportProvider {

    companion object {
        const val TAG = "NearbyTransport"
        const val SERVICE_ID = "com.aistudio.aeroshare.p2p"
        val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context.applicationContext)
    }

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var activePayloadCallback: PayloadCallback? = null

    override fun startAdvertising(
        localName: String,
        onConnectionInitiated: (endpointId: String, endpointName: String, authPin: String, isIncoming: Boolean) -> Unit,
        onConnectionResult: (endpointId: String, success: Boolean) -> Unit,
        onDisconnected: (endpointId: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        val callback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                Log.d(TAG, "Incoming connection initiated from: $endpointId (${connectionInfo.endpointName})")
                val pin = connectionInfo.authenticationDigits ?: "0000"
                onConnectionInitiated(endpointId, connectionInfo.endpointName, pin, connectionInfo.isIncomingConnection)
            }

            override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
                val success = resolution.status.isSuccess
                Log.d(TAG, "Connection result for $endpointId: success=$success (status=${resolution.status.statusCode})")
                onConnectionResult(endpointId, success)
            }

            override fun onDisconnected(endpointId: String) {
                Log.d(TAG, "Disconnected from endpoint: $endpointId")
                onDisconnected(endpointId)
            }
        }

        connectionsClient.startAdvertising(localName, SERVICE_ID, callback, options)
            .addOnSuccessListener {
                Log.d(TAG, "Started advertising as $localName")
                _isAdvertising.value = true
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to start advertising", e)
                _isAdvertising.value = false
                onError(e)
            }
    }

    override fun stopAdvertising() {
        if (_isAdvertising.value) {
            connectionsClient.stopAdvertising()
            _isAdvertising.value = false
            Log.d(TAG, "Stopped advertising")
        }
    }

    override fun startDiscovery(
        onEndpointFound: (endpointId: String, endpointName: String, serviceId: String) -> Unit,
        onEndpointLost: (endpointId: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        val callback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
                onEndpointFound(endpointId, info.endpointName, info.serviceId)
            }

            override fun onEndpointLost(endpointId: String) {
                Log.d(TAG, "Endpoint lost: $endpointId")
                onEndpointLost(endpointId)
            }
        }

        connectionsClient.startDiscovery(SERVICE_ID, callback, options)
            .addOnSuccessListener {
                Log.d(TAG, "Started discovery")
                _isDiscovering.value = true
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to start discovery", e)
                _isDiscovering.value = false
                onError(e)
            }
    }

    override fun stopDiscovery() {
        if (_isDiscovering.value) {
            connectionsClient.stopDiscovery()
            _isDiscovering.value = false
            Log.d(TAG, "Stopped discovery")
        }
    }

    override fun requestConnection(
        endpointId: String,
        localName: String,
        onConnectionInitiated: (endpointId: String, endpointName: String, authPin: String, isIncoming: Boolean) -> Unit,
        onConnectionResult: (endpointId: String, success: Boolean) -> Unit,
        onDisconnected: (endpointId: String) -> Unit
    ) {
        val callback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                Log.d(TAG, "Outgoing connection initiated: $endpointId (${connectionInfo.endpointName})")
                val pin = connectionInfo.authenticationDigits ?: "0000"
                onConnectionInitiated(endpointId, connectionInfo.endpointName, pin, connectionInfo.isIncomingConnection)
            }

            override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
                val success = resolution.status.isSuccess
                Log.d(TAG, "Connection result for $endpointId: success=$success")
                onConnectionResult(endpointId, success)
            }

            override fun onDisconnected(endpointId: String) {
                Log.d(TAG, "Disconnected from: $endpointId")
                onDisconnected(endpointId)
            }
        }

        connectionsClient.requestConnection(localName, endpointId, callback)
            .addOnSuccessListener {
                Log.d(TAG, "Requested connection to $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to request connection to $endpointId", e)
                onConnectionResult(endpointId, false)
            }
    }

    override fun acceptConnection(endpointId: String, payloadCallback: PayloadCallback) {
        activePayloadCallback = payloadCallback
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                Log.d(TAG, "Accepted connection with $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to accept connection with $endpointId", e)
            }
    }

    override fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
            .addOnSuccessListener {
                Log.d(TAG, "Rejected connection with $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to reject connection with $endpointId", e)
            }
    }

    override fun disconnect(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
        Log.d(TAG, "Disconnected endpoint $endpointId")
    }

    override fun sendPayload(
        endpointId: String,
        payload: Payload,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener {
                Log.d(TAG, "Payload ${payload.id} successfully queued for endpoint $endpointId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send payload ${payload.id} to $endpointId", e)
                onError(e)
            }
    }

    override fun cancelPayload(payloadId: Long) {
        connectionsClient.cancelPayload(payloadId)
            .addOnSuccessListener {
                Log.d(TAG, "Cancelled payload $payloadId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to cancel payload $payloadId", e)
            }
    }

    override fun release() {
        stopAdvertising()
        stopDiscovery()
        connectionsClient.stopAllEndpoints()
        activePayloadCallback = null
        Log.d(TAG, "Released NearbyTransportProvider and stopped all endpoints")
    }
}

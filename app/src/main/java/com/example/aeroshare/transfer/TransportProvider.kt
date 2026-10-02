package com.example.aeroshare.transfer

import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import kotlinx.coroutines.flow.StateFlow

interface TransportProvider {
    val isAdvertising: StateFlow<Boolean>
    val isDiscovering: StateFlow<Boolean>

    fun startAdvertising(
        localName: String,
        onConnectionInitiated: (endpointId: String, endpointName: String, authPin: String, isIncoming: Boolean) -> Unit,
        onConnectionResult: (endpointId: String, success: Boolean) -> Unit,
        onDisconnected: (endpointId: String) -> Unit,
        onError: (Exception) -> Unit
    )

    fun stopAdvertising()

    fun startDiscovery(
        onEndpointFound: (endpointId: String, endpointName: String, serviceId: String) -> Unit,
        onEndpointLost: (endpointId: String) -> Unit,
        onError: (Exception) -> Unit
    )

    fun stopDiscovery()

    fun requestConnection(
        endpointId: String,
        localName: String,
        onConnectionInitiated: (endpointId: String, endpointName: String, authPin: String, isIncoming: Boolean) -> Unit,
        onConnectionResult: (endpointId: String, success: Boolean) -> Unit,
        onDisconnected: (endpointId: String) -> Unit
    )

    fun acceptConnection(endpointId: String, payloadCallback: PayloadCallback)

    fun rejectConnection(endpointId: String)

    fun disconnect(endpointId: String)

    fun sendPayload(endpointId: String, payload: Payload, onSuccess: () -> Unit, onError: (Exception) -> Unit)

    fun cancelPayload(payloadId: Long)

    fun release()
}

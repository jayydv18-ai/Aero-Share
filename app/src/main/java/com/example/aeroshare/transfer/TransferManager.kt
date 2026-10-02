package com.example.aeroshare.transfer

import android.content.Context
import android.util.Log
import com.example.aeroshare.data.model.NearbyDevice
import com.example.aeroshare.data.model.TransferDirection
import com.example.aeroshare.data.model.TransferItem
import com.example.aeroshare.data.model.TransferSession
import com.example.aeroshare.data.model.TransferStatus
import com.example.aeroshare.data.repository.TransferRepository
import com.example.aeroshare.service.TransferForegroundService
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

data class IncomingRequest(
    val endpointId: String,
    val endpointName: String,
    val authPin: String,
    val files: List<TransferItem> = emptyList(),
    val totalBytes: Long = 0L,
    val sessionId: String = UUID.randomUUID().toString()
)

class TransferManager(
    private val context: Context,
    private val transport: TransportProvider,
    private val storageManager: StorageManager,
    private val repository: TransferRepository
) {
    companion object {
        const val TAG = "TransferManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    private val _incomingRequest = MutableStateFlow<IncomingRequest?>(null)
    val incomingRequest: StateFlow<IncomingRequest?> = _incomingRequest.asStateFlow()

    private val _activeSession = MutableStateFlow<TransferSession?>(null)
    val activeSession: StateFlow<TransferSession?> = _activeSession.asStateFlow()

    private var activeEndpointId: String? = null
    private val outgoingPayloadMap = mutableMapOf<Long, String>() // payloadId -> itemId
    private val incomingPartialFiles = mutableMapOf<Long, Pair<File, TransferItem>>() // payloadId -> (file, item)
    private val incomingStreamThreads = mutableMapOf<Long, Thread>()

    private var lastSpeedCalcTime = 0L
    private var lastBytesTransferred = 0L

    init {
        // Register payload callback for transfers
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val jsonStr = String(bytes, StandardCharsets.UTF_8)
                    handleControlPayload(endpointId, jsonStr)
                }
                Payload.Type.STREAM -> {
                    val stream = payload.asStream()?.asInputStream() ?: return
                    handleIncomingStream(payload.id, stream)
                }
                Payload.Type.FILE -> {
                    // Handled if file payload used
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val transferred = update.bytesTransferred
            val total = update.totalBytes

            updateTransferProgress(payloadId, transferred, total, update.status)
        }
    }

    fun startDiscovery(onError: (Exception) -> Unit) {
        _discoveredDevices.value = emptyList()
        transport.startDiscovery(
            onEndpointFound = { endpointId, endpointName, serviceId ->
                val parts = endpointName.split("::")
                val name = parts.getOrNull(0) ?: endpointName
                val avatar = parts.getOrNull(1) ?: "avatar_1"
                val device = NearbyDevice(
                    endpointId = endpointId,
                    deviceName = name,
                    avatarId = avatar,
                    serviceId = serviceId
                )
                val current = _discoveredDevices.value.toMutableList()
                if (current.none { it.endpointId == endpointId }) {
                    current.add(device)
                    _discoveredDevices.value = current
                }
            },
            onEndpointLost = { endpointId ->
                _discoveredDevices.value = _discoveredDevices.value.filter { it.endpointId != endpointId }
            },
            onError = onError
        )
    }

    fun stopDiscovery() {
        transport.stopDiscovery()
    }

    fun startAdvertising(localName: String, avatarId: String, onError: (Exception) -> Unit) {
        val advertisedName = "$localName::$avatarId"
        transport.startAdvertising(
            localName = advertisedName,
            onConnectionInitiated = { endpointId, endpointName, authPin, isIncoming ->
                val parts = endpointName.split("::")
                val name = parts.getOrNull(0) ?: endpointName
                Log.d(TAG, "Connection initiated from: $name, PIN: $authPin")
                _incomingRequest.value = IncomingRequest(
                    endpointId = endpointId,
                    endpointName = name,
                    authPin = authPin
                )
            },
            onConnectionResult = { endpointId, success ->
                if (success) {
                    activeEndpointId = endpointId
                    transport.acceptConnection(endpointId, payloadCallback)
                } else {
                    _incomingRequest.value = null
                    _activeSession.value = _activeSession.value?.copy(status = TransferStatus.FAILED)
                }
            },
            onDisconnected = { endpointId ->
                handleDisconnection(endpointId)
            },
            onError = onError
        )
    }

    fun stopAdvertising() {
        transport.stopAdvertising()
        _incomingRequest.value = null
    }

    fun connectToDevice(device: NearbyDevice, localName: String, avatarId: String, files: List<TransferItem>) {
        val advertisedName = "$localName::$avatarId"
        val totalBytes = files.sumOf { it.fileSize }
        val session = TransferSession(
            sessionId = UUID.randomUUID().toString(),
            direction = TransferDirection.SEND,
            endpointId = device.endpointId,
            remoteDeviceName = device.deviceName,
            items = files,
            totalBytes = totalBytes,
            status = TransferStatus.CONNECTING
        )
        _activeSession.value = session
        activeEndpointId = device.endpointId

        transport.requestConnection(
            endpointId = device.endpointId,
            localName = advertisedName,
            onConnectionInitiated = { endpointId, endpointName, authPin, _ ->
                _activeSession.value = _activeSession.value?.copy(
                    status = TransferStatus.AUTHENTICATING,
                    authPin = authPin
                )
                // Sender auto-accepts once user initiated
                transport.acceptConnection(endpointId, payloadCallback)
            },
            onConnectionResult = { endpointId, success ->
                if (success) {
                    _activeSession.value = _activeSession.value?.copy(status = TransferStatus.ACCEPTED)
                    // Once receiver accepts connection, send the metadata header
                    sendMetadataHeader(endpointId, _activeSession.value!!)
                } else {
                    _activeSession.value = _activeSession.value?.copy(status = TransferStatus.FAILED)
                }
            },
            onDisconnected = { endpointId ->
                handleDisconnection(endpointId)
            }
        )
    }

    fun acceptIncomingRequest() {
        val request = _incomingRequest.value ?: return
        val endpointId = request.endpointId
        activeEndpointId = endpointId

        val session = TransferSession(
            sessionId = request.sessionId,
            direction = TransferDirection.RECEIVE,
            endpointId = endpointId,
            remoteDeviceName = request.endpointName,
            items = request.files,
            totalBytes = request.totalBytes,
            status = TransferStatus.ACCEPTED,
            authPin = request.authPin
        )
        _activeSession.value = session
        _incomingRequest.value = null

        transport.acceptConnection(endpointId, payloadCallback)
    }

    fun declineIncomingRequest() {
        val request = _incomingRequest.value ?: return
        transport.rejectConnection(request.endpointId)
        _incomingRequest.value = null
    }

    private fun sendMetadataHeader(endpointId: String, session: TransferSession) {
        scope.launch {
            val json = JSONObject().apply {
                put("type", "METADATA")
                put("sessionId", session.sessionId)
                val filesArr = JSONArray()
                session.items.forEach { item ->
                    val fileObj = JSONObject().apply {
                        put("id", item.id)
                        put("name", item.fileName)
                        put("size", item.fileSize)
                        put("mime", item.mimeType)
                    }
                    filesArr.put(fileObj)
                }
                put("files", filesArr)
                put("totalBytes", session.totalBytes)
            }

            val payload = Payload.fromBytes(json.toString().toByteArray(StandardCharsets.UTF_8))
            transport.sendPayload(
                endpointId = endpointId,
                payload = payload,
                onSuccess = {
                    Log.d(TAG, "Metadata header sent. Initiating file streams...")
                    startSendingFiles(endpointId, session)
                },
                onError = { e ->
                    Log.e(TAG, "Failed to send metadata", e)
                    _activeSession.value = _activeSession.value?.copy(status = TransferStatus.FAILED)
                }
            )
        }
    }

    private fun startSendingFiles(endpointId: String, session: TransferSession) {
        scope.launch {
            _activeSession.value = _activeSession.value?.copy(status = TransferStatus.TRANSFERRING)
            TransferForegroundService.startService(
                context,
                isSending = true,
                totalFiles = session.items.size,
                currentFile = session.items.firstOrNull()?.fileName ?: "File"
            )

            for ((index, item) in session.items.withIndex()) {
                val current = _activeSession.value ?: break
                if (current.status == TransferStatus.CANCELLED) break

                _activeSession.value = current.copy(currentItemIndex = index)

                val inputStream = storageManager.openInputStream(item.uriString)
                if (inputStream == null) {
                    Log.e(TAG, "Could not open stream for ${item.fileName}")
                    markItemFailed(item.id, "Unable to read source file")
                    continue
                }

                val payload = Payload.fromStream(inputStream)
                outgoingPayloadMap[payload.id] = item.id

                transport.sendPayload(
                    endpointId = endpointId,
                    payload = payload,
                    onSuccess = {
                        Log.d(TAG, "Stream queued for ${item.fileName} with payload ${payload.id}")
                    },
                    onError = { e ->
                        Log.e(TAG, "Failed sending ${item.fileName}", e)
                        markItemFailed(item.id, e.message)
                    }
                )
            }
        }
    }

    private fun handleControlPayload(endpointId: String, jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            when (json.optString("type")) {
                "METADATA" -> {
                    val sessionId = json.getString("sessionId")
                    val totalBytes = json.getLong("totalBytes")
                    val filesArr = json.getJSONArray("files")
                    val items = mutableListOf<TransferItem>()
                    for (i in 0 until filesArr.length()) {
                        val obj = filesArr.getJSONObject(i)
                        items.add(
                            TransferItem(
                                id = obj.getString("id"),
                                fileName = obj.getString("name"),
                                fileSize = obj.getLong("size"),
                                mimeType = obj.getString("mime"),
                                uriString = ""
                            )
                        )
                    }

                    val currentReq = _incomingRequest.value
                    if (currentReq != null && currentReq.endpointId == endpointId) {
                        _incomingRequest.value = currentReq.copy(
                            files = items,
                            totalBytes = totalBytes,
                            sessionId = sessionId
                        )
                    }

                    // If active session already accepted
                    val currentSession = _activeSession.value
                    if (currentSession != null && currentSession.endpointId == endpointId) {
                        _activeSession.value = currentSession.copy(
                            items = items,
                            totalBytes = totalBytes,
                            status = TransferStatus.TRANSFERRING
                        )
                        TransferForegroundService.startService(
                            context,
                            isSending = false,
                            totalFiles = items.size,
                            currentFile = items.firstOrNull()?.fileName ?: "File"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling control payload", e)
        }
    }

    private fun handleIncomingStream(payloadId: Long, stream: InputStream) {
        val session = _activeSession.value ?: return
        val currentItem = session.items.getOrNull(session.currentItemIndex)
            ?: session.items.firstOrNull { it.status == TransferStatus.QUEUED }
            ?: return

        val partialFile = storageManager.createPartialFile(currentItem.fileName)
        incomingPartialFiles[payloadId] = Pair(partialFile, currentItem)

        val thread = Thread {
            try {
                FileOutputStream(partialFile).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                }
                Log.d(TAG, "Finished writing partial file for ${currentItem.fileName}")
            } catch (e: Exception) {
                Log.e(TAG, "Stream write error for ${currentItem.fileName}", e)
            }
        }
        incomingStreamThreads[payloadId] = thread
        thread.start()
    }

    private fun updateTransferProgress(
        payloadId: Long,
        transferred: Long,
        total: Long,
        status: Int
    ) {
        val session = _activeSession.value ?: return

        val now = System.currentTimeMillis()
        val timeDiff = now - lastSpeedCalcTime
        val speed = if (timeDiff > 500 && transferred > lastBytesTransferred) {
            val bytesDiff = transferred - lastBytesTransferred
            val calculatedSpeed = (bytesDiff * 1000) / timeDiff
            lastSpeedCalcTime = now
            lastBytesTransferred = transferred
            calculatedSpeed
        } else {
            session.speedBytesPerSec
        }

        when (status) {
            PayloadTransferUpdate.Status.IN_PROGRESS -> {
                val updatedItems = session.items.map { item ->
                    val matchesOutgoing = outgoingPayloadMap[payloadId] == item.id
                    val matchesIncoming = incomingPartialFiles[payloadId]?.second?.id == item.id
                    if (matchesOutgoing || matchesIncoming) {
                        item.copy(bytesTransferred = transferred, status = TransferStatus.TRANSFERRING)
                    } else item
                }
                val totalTransferred = updatedItems.sumOf { it.bytesTransferred }
                _activeSession.value = session.copy(
                    items = updatedItems,
                    bytesTransferred = totalTransferred,
                    speedBytesPerSec = speed,
                    status = TransferStatus.TRANSFERRING
                )

                val currentFile = session.items.getOrNull(session.currentItemIndex)?.fileName ?: "File"
                TransferForegroundService.updateProgress(
                    context,
                    bytesTransferred = totalTransferred,
                    totalBytes = session.totalBytes,
                    speedBytesPerSec = speed,
                    currentFile = currentFile
                )
            }
            PayloadTransferUpdate.Status.SUCCESS -> {
                handlePayloadSuccess(payloadId, session)
            }
            PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                handlePayloadFailure(payloadId, session)
            }
        }
    }

    private fun handlePayloadSuccess(payloadId: Long, session: TransferSession) {
        scope.launch {
            if (session.direction == TransferDirection.RECEIVE) {
                val pair = incomingPartialFiles[payloadId]
                if (pair != null) {
                    val (partialFile, item) = pair
                    try {
                        val finalUri = storageManager.finalizeReceivedFile(
                            partialFile = partialFile,
                            originalFileName = item.fileName,
                            mimeType = item.mimeType
                        )
                        markItemCompleted(item.id, finalUri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to finalize received file ${item.fileName}", e)
                        markItemFailed(item.id, e.message)
                    }
                }
            } else {
                val itemId = outgoingPayloadMap[payloadId]
                if (itemId != null) {
                    markItemCompleted(itemId, null)
                }
            }

            checkAllCompleted()
        }
    }

    private fun handlePayloadFailure(payloadId: Long, session: TransferSession) {
        val itemId = outgoingPayloadMap[payloadId] ?: incomingPartialFiles[payloadId]?.second?.id
        if (itemId != null) {
            markItemFailed(itemId, "Transfer interrupted")
        }
        scope.launch {
            checkAllCompleted()
        }
    }

    private fun markItemCompleted(itemId: String, pathOrUri: String?) {
        val session = _activeSession.value ?: return
        val updated = session.items.map { item ->
            if (item.id == itemId) {
                item.copy(
                    bytesTransferred = item.fileSize,
                    status = TransferStatus.COMPLETED,
                    localFilePath = pathOrUri ?: item.localFilePath
                )
            } else item
        }
        val nextIndex = (session.currentItemIndex + 1).coerceAtMost(session.items.size - 1)
        _activeSession.value = session.copy(
            items = updated,
            currentItemIndex = nextIndex,
            bytesTransferred = updated.sumOf { it.bytesTransferred }
        )
    }

    private fun markItemFailed(itemId: String, error: String?) {
        val session = _activeSession.value ?: return
        val updated = session.items.map { item ->
            if (item.id == itemId) {
                item.copy(status = TransferStatus.FAILED, errorMessage = error)
            } else item
        }
        _activeSession.value = session.copy(items = updated)
    }

    private suspend fun checkAllCompleted() {
        val session = _activeSession.value ?: return
        val allDone = session.items.all { it.status == TransferStatus.COMPLETED || it.status == TransferStatus.FAILED }
        if (allDone) {
            val anySuccess = session.items.any { it.status == TransferStatus.COMPLETED }
            val finalStatus = if (anySuccess) TransferStatus.COMPLETED else TransferStatus.FAILED
            val finalSession = session.copy(
                status = finalStatus,
                endTime = System.currentTimeMillis()
            )
            _activeSession.value = finalSession

            TransferForegroundService.stopService(context)

            // Save to Room persistent database
            repository.saveSession(finalSession)
        }
    }

    fun cancelActiveTransfer() {
        val session = _activeSession.value ?: return
        outgoingPayloadMap.keys.forEach { payloadId ->
            transport.cancelPayload(payloadId)
        }
        activeEndpointId?.let { transport.disconnect(it) }

        val cancelledSession = session.copy(
            status = TransferStatus.CANCELLED,
            endTime = System.currentTimeMillis()
        )
        _activeSession.value = cancelledSession

        TransferForegroundService.stopService(context)

        scope.launch {
            repository.saveSession(cancelledSession)
        }
    }

    fun resetSession() {
        _activeSession.value = null
        activeEndpointId = null
        outgoingPayloadMap.clear()
        incomingPartialFiles.clear()
        incomingStreamThreads.clear()
    }

    private fun handleDisconnection(endpointId: String) {
        val session = _activeSession.value
        if (session != null && session.endpointId == endpointId && session.status == TransferStatus.TRANSFERRING) {
            _activeSession.value = session.copy(status = TransferStatus.CONNECTION_LOST)
            TransferForegroundService.stopService(context)
        }
    }

    fun release() {
        cancelActiveTransfer()
        transport.release()
    }
}

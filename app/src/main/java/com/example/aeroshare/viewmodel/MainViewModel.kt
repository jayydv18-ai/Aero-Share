package com.example.aeroshare.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AeroShareApp
import com.example.aeroshare.ads.AdsManager
import com.example.aeroshare.billing.PremiumManager
import com.example.aeroshare.data.db.AppDatabase
import com.example.aeroshare.data.db.TransferItemEntity
import com.example.aeroshare.data.db.TransferSessionEntity
import com.example.aeroshare.data.model.NearbyDevice
import com.example.aeroshare.data.model.QrSessionPayload
import com.example.aeroshare.data.model.TransferItem
import com.example.aeroshare.data.model.TransferSession
import com.example.aeroshare.data.model.TransferStatus
import com.example.aeroshare.data.repository.SettingsRepository
import com.example.aeroshare.data.repository.TransferRepository
import com.example.aeroshare.transfer.IncomingRequest
import com.example.aeroshare.transfer.NearbyTransportProvider
import com.example.aeroshare.transfer.StorageManager
import com.example.aeroshare.transfer.TransferManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    SPLASH,
    PROFILE_SETUP,
    HOME,
    SEND_PICKER,
    SEND_REVIEW,
    DEVICE_DISCOVERY,
    TRANSFER_PROGRESS,
    TRANSFER_COMPLETE,
    RECEIVE_WAITING,
    HISTORY,
    HISTORY_DETAIL,
    SETTINGS,
    PROFILE_EDIT,
    QR_DISPLAY,
    QR_SCANNER,
    REMOVE_ADS,
    PERMISSIONS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as? AeroShareApp
    val settingsRepository: SettingsRepository = app?.settingsRepository ?: SettingsRepository(application)
    val transferRepository: TransferRepository = app?.transferRepository ?: TransferRepository(AppDatabase.getInstance(application).transferDao())
    val storageManager: StorageManager = app?.storageManager ?: StorageManager(application)
    val transferManager: TransferManager = app?.transferManager ?: TransferManager(application, NearbyTransportProvider(application), storageManager, transferRepository)
    val adsManager: AdsManager = app?.adsManager ?: AdsManager(application)
    val premiumManager: PremiumManager = app?.premiumManager ?: PremiumManager(application, settingsRepository)

    private val _currentScreen = MutableStateFlow(AppScreen.SPLASH)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val screenStack = mutableListOf(AppScreen.SPLASH)

    val deviceName: StateFlow<String> = settingsRepository.deviceNameFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = settingsRepository.defaultDeviceName
    )

    val avatarId: StateFlow<String> = settingsRepository.avatarIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "avatar_1"
    )

    val themePreference: StateFlow<String> = settingsRepository.themePreferenceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "system"
    )

    val isFirstLaunchCompleted: StateFlow<Boolean> = settingsRepository.isFirstLaunchCompletedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    val isPro: StateFlow<Boolean> = premiumManager.isPro

    val discoveredDevices: StateFlow<List<NearbyDevice>> = transferManager.discoveredDevices
    val incomingRequest: StateFlow<IncomingRequest?> = transferManager.incomingRequest
    val activeSession: StateFlow<TransferSession?> = transferManager.activeSession

    val allHistorySessions: StateFlow<List<TransferSessionEntity>> = transferRepository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedFiles = MutableStateFlow<List<TransferItem>>(emptyList())
    val selectedFiles: StateFlow<List<TransferItem>> = _selectedFiles.asStateFlow()

    private val _selectedHistorySessionId = MutableStateFlow<String?>(null)
    val selectedHistorySessionId: StateFlow<String?> = _selectedHistorySessionId.asStateFlow()

    private val _selectedSessionItems = MutableStateFlow<List<TransferItemEntity>>(emptyList())
    val selectedSessionItems: StateFlow<List<TransferItemEntity>> = _selectedSessionItems.asStateFlow()

    init {
        viewModelScope.launch {
            activeSession.collect { session ->
                if (session != null) {
                    if (session.status == TransferStatus.COMPLETED) {
                        _currentScreen.value = AppScreen.TRANSFER_COMPLETE
                    } else if (session.status == TransferStatus.TRANSFERRING ||
                        session.status == TransferStatus.CONNECTING ||
                        session.status == TransferStatus.AUTHENTICATING ||
                        session.status == TransferStatus.ACCEPTED
                    ) {
                        if (_currentScreen.value != AppScreen.TRANSFER_PROGRESS &&
                            _currentScreen.value != AppScreen.TRANSFER_COMPLETE
                        ) {
                            _currentScreen.value = AppScreen.TRANSFER_PROGRESS
                        }
                    }
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            screenStack.add(screen)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.size - 1)
            val previous = screenStack.last()
            _currentScreen.value = previous
            return true
        }
        return false
    }

    fun onSplashFinished() {
        screenStack.clear()
        screenStack.add(AppScreen.HOME)
        _currentScreen.value = AppScreen.HOME
    }

    fun saveProfile(name: String, avatarId: String) {
        viewModelScope.launch {
            settingsRepository.saveProfile(name, avatarId)
            screenStack.clear()
            screenStack.add(AppScreen.HOME)
            _currentScreen.value = AppScreen.HOME
        }
    }

    fun setThemePreference(theme: String) {
        viewModelScope.launch {
            settingsRepository.setThemePreference(theme)
        }
    }

    fun setSelectedFiles(files: List<TransferItem>) {
        _selectedFiles.value = files
    }

    fun startDiscovery() {
        transferManager.startDiscovery(onError = {})
    }

    fun stopDiscovery() {
        transferManager.stopDiscovery()
    }

    fun startReceiving() {
        transferManager.startAdvertising(
            localName = deviceName.value,
            avatarId = avatarId.value,
            onError = {}
        )
    }

    fun stopReceiving() {
        transferManager.stopAdvertising()
    }

    fun connectToDevice(device: NearbyDevice) {
        transferManager.connectToDevice(
            device = device,
            localName = deviceName.value,
            avatarId = avatarId.value,
            files = selectedFiles.value
        )
        navigateTo(AppScreen.TRANSFER_PROGRESS)
    }

    fun connectViaQr(payload: QrSessionPayload) {
        val targetDevice = NearbyDevice(
            endpointId = payload.endpointId,
            deviceName = payload.deviceName,
            avatarId = payload.avatarId,
            serviceId = payload.serviceId
        )
        connectToDevice(targetDevice)
    }

    fun acceptIncomingRequest() {
        transferManager.acceptIncomingRequest()
        navigateTo(AppScreen.TRANSFER_PROGRESS)
    }

    fun declineIncomingRequest() {
        transferManager.declineIncomingRequest()
    }

    fun cancelActiveTransfer() {
        transferManager.cancelActiveTransfer()
    }

    fun onTransferCompleteDone() {
        transferManager.resetSession()
        _selectedFiles.value = emptyList()
        screenStack.clear()
        screenStack.add(AppScreen.HOME)
        _currentScreen.value = AppScreen.HOME
    }

    fun selectHistorySession(sessionId: String) {
        _selectedHistorySessionId.value = sessionId
        viewModelScope.launch {
            transferRepository.getItemsForSession(sessionId).collect { items ->
                _selectedSessionItems.value = items
            }
        }
        navigateTo(AppScreen.HISTORY_DETAIL)
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            transferRepository.clearAllHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        transferManager.release()
    }
}

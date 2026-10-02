package com.example

import android.app.Application
import android.util.Log
import com.example.aeroshare.ads.AdsManager
import com.example.aeroshare.billing.PremiumManager
import com.example.aeroshare.data.db.AppDatabase
import com.example.aeroshare.data.repository.SettingsRepository
import com.example.aeroshare.data.repository.TransferRepository
import com.example.aeroshare.transfer.NearbyTransportProvider
import com.example.aeroshare.transfer.StorageManager
import com.example.aeroshare.transfer.TransferManager

class AeroShareApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var transferRepository: TransferRepository
        private set

    lateinit var storageManager: StorageManager
        private set

    lateinit var transportProvider: NearbyTransportProvider
        private set

    lateinit var transferManager: TransferManager
        private set

    lateinit var adsManager: AdsManager
        private set

    lateinit var premiumManager: PremiumManager
        private set

    override fun onCreate() {
        super.onCreate()

        try {
            database = AppDatabase.getInstance(this)
            settingsRepository = SettingsRepository(this)
            transferRepository = TransferRepository(database.transferDao())
            storageManager = StorageManager(this)
            transportProvider = NearbyTransportProvider(this)
            transferManager = TransferManager(this, transportProvider, storageManager, transferRepository)

            adsManager = AdsManager(this)
            adsManager.initialize()

            premiumManager = PremiumManager(this, settingsRepository)
            premiumManager.initialize()
        } catch (e: Throwable) {
            Log.e("AeroShareApp", "Initialization exception", e)
        }
    }
}

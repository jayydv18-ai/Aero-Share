package com.example.aeroshare.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdsManager(private val context: Context) {

    companion object {
        const val TAG = "AdsManager"
        const val UNITY_GAME_ID = "6198533"
        const val BANNER_PLACEMENT_ID = "Banner_Android"
        const val TEST_MODE = true // Test mode enabled during development as instructed
    }

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isBannerLoaded = MutableStateFlow(false)
    val isBannerLoaded: StateFlow<Boolean> = _isBannerLoaded.asStateFlow()

    private val _isBannerFailed = MutableStateFlow(false)
    val isBannerFailed: StateFlow<Boolean> = _isBannerFailed.asStateFlow()

    private var activeBannerView: BannerView? = null
    private var isProEnabled = false

    fun setProPurchased(isPro: Boolean) {
        isProEnabled = isPro
        if (isPro) {
            destroyBanner()
        }
    }

    fun isAdsEnabled(): Boolean = !isProEnabled

    fun initialize(onInitialized: (() -> Unit)? = null) {
        try {
            if (UnityAds.isInitialized) {
                _isInitialized.value = true
                onInitialized?.invoke()
                return
            }

            UnityAds.initialize(
                context.applicationContext,
                UNITY_GAME_ID,
                TEST_MODE,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        Log.d(TAG, "Unity Ads initialized successfully with Game ID: $UNITY_GAME_ID")
                        _isInitialized.value = true
                        onInitialized?.invoke()
                    }

                    override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                        Log.e(TAG, "Unity Ads initialization failed: $error - $message")
                        _isInitialized.value = false
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Exception initializing Unity Ads", e)
            _isInitialized.value = false
        }
    }

    fun createBanner(activity: Activity): BannerView? {
        if (!isAdsEnabled()) {
            Log.d(TAG, "Ads disabled for Pro user. Skipping banner creation.")
            return null
        }

        if (activeBannerView != null) {
            return activeBannerView
        }

        return try {
            val banner = BannerView(activity, BANNER_PLACEMENT_ID, UnityBannerSize(320, 50))
            banner.listener = object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView?) {
                    Log.d(TAG, "Unity Banner loaded successfully")
                    _isBannerLoaded.value = true
                    _isBannerFailed.value = false
                }

                override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
                    Log.w(TAG, "Unity Banner failed to load: ${errorInfo?.errorMessage}")
                    _isBannerLoaded.value = false
                    _isBannerFailed.value = true
                }

                override fun onBannerClick(bannerAdView: BannerView?) {
                    Log.d(TAG, "Unity Banner clicked")
                }

                override fun onBannerLeftApplication(bannerAdView: BannerView?) {
                    Log.d(TAG, "Unity Banner left application")
                }

                override fun onBannerShown(bannerAdView: BannerView?) {
                    Log.d(TAG, "Unity Banner shown")
                }
            }

            banner.load()
            activeBannerView = banner
            banner
        } catch (e: Throwable) {
            Log.e(TAG, "Exception creating Unity Banner", e)
            _isBannerFailed.value = true
            null
        }
    }

    fun destroyBanner() {
        activeBannerView?.destroy()
        activeBannerView = null
        _isBannerLoaded.value = false
    }
}

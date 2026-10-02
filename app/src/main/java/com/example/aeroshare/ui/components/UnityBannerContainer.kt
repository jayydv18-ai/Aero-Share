package com.example.aeroshare.ui.components

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aeroshare.ads.AdsManager
import com.unity3d.services.banners.BannerView

@Composable
fun UnityBannerContainer(
    adsManager: AdsManager,
    isPro: Boolean,
    modifier: Modifier = Modifier
) {
    if (isPro || !adsManager.isAdsEnabled()) {
        // Zero ad rendering for Pro user
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val isBannerLoaded by adsManager.isBannerLoaded.collectAsState()
    val isBannerFailed by adsManager.isBannerFailed.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .testTag("unity_ad_banner_container"),
        contentAlignment = Alignment.Center
    ) {
        if (!isBannerFailed) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                factory = { ctx ->
                    val frameLayout = FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val banner = adsManager.createBanner(activity)
                    if (banner != null) {
                        (banner.parent as? ViewGroup)?.removeView(banner)
                        frameLayout.addView(banner)
                    }
                    frameLayout
                }
            )
        } else {
            // Graceful non-intrusive fallback placeholder if ad fails to load
            Text(
                text = "AeroShare • Ultra-Fast Nearby Sharing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

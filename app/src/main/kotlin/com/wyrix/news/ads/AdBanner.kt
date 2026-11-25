package com.wyrix.news.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Unity banner (320x50). Takes space while loading; collapses if loading fails. */
@Composable
fun AdBanner(
    placementId: String,
    sdkReady: Boolean,
    modifier: Modifier = Modifier,
    onStatus: (String) -> Unit = {},
) {
    val activity = LocalContext.current.findActivity() ?: return
    // Stays at full height while loading (the banner needs a real size); collapses only on failure.
    val failed = remember(placementId) { mutableStateOf(false) }
    val banner = remember(placementId) {
        BannerView(activity, placementId, UnityBannerSize(320, 50)).apply {
            listener = object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView?) {
                    failed.value = false
                    onStatus("Banner loaded")
                }

                override fun onBannerShown(bannerAdView: BannerView?) {}
                override fun onBannerClick(bannerAdView: BannerView?) {}
                override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
                    failed.value = true
                    onStatus("Banner FAILED: ${errorInfo?.errorCode} ${errorInfo?.errorMessage}")
                }

                override fun onBannerLeftApplication(bannerAdView: BannerView?) {}
            }
        }
    }
    LaunchedEffect(banner, sdkReady) { if (sdkReady) banner.load() }
    DisposableEffect(banner) { onDispose { banner.destroy() } }

    val isFailed by failed
    Box(
        modifier.fillMaxWidth().height(if (isFailed) 0.dp else 50.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { banner })
    }
}

package com.wyrix.news.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/** Banner that takes no space until an ad has actually loaded. */
@Composable
fun AdBanner(unitId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loaded = remember(unitId) { mutableStateOf(false) }
    val adView = remember(unitId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = unitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded.value = false
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }

    val isLoaded by loaded
    Box(
        modifier.fillMaxWidth().height(if (isLoaded) 50.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { adView })
    }
}

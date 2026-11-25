package com.wyrix.news.ads

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds
import com.wyrix.news.BuildConfig

interface AdController {
    val isEnabled: Boolean
    val bannerUnitId: String

    /** True once the ad SDK has finished initialising (banner loads only after this). */
    val isReady: Boolean
}

/** Unity Ads. Debug builds run in TEST mode; ids come from app/build.gradle.kts. */
class UnityAdController(
    private val gameId: String,
    override val bannerUnitId: String,
) : AdController {
    override val isEnabled: Boolean = gameId.isNotBlank() && bannerUnitId.isNotBlank()

    override var isReady: Boolean by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        if (!isEnabled || UnityAds.isInitialized) {
            isReady = UnityAds.isInitialized
            return
        }
        UnityAds.initialize(
            context.applicationContext,
            gameId,
            BuildConfig.DEBUG,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    isReady = true
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?,
                ) {
                    isReady = false
                }
            },
        )
    }
}

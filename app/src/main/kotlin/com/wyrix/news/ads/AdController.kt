package com.wyrix.news.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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

    private var appContext: Context? = null

    /** Debug builds only: shows Unity's status/error on screen so problems are visible. */
    fun debugToast(msg: String) {
        Log.d("UnityAds", msg)
        val c = appContext ?: return
        if (BuildConfig.DEBUG) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(c, "Unity: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (!isEnabled) debugToast("Game ID empty, ads disabled")
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
                    debugToast("SDK initialised")
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?,
                ) {
                    isReady = false
                    debugToast("Init FAILED: $error $message")
                }
            },
        )
    }
}

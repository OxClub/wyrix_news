package com.wyrix.news.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds

interface AdController {
    val isEnabled: Boolean
    val bannerUnitId: String
}

/** Google AdMob. Defaults to Google's public TEST ad units (see app/build.gradle.kts). */
class AdMobController(override val bannerUnitId: String) : AdController {
    override val isEnabled: Boolean = bannerUnitId.isNotBlank()

    fun initialize(context: Context) {
        MobileAds.initialize(context)
    }
}

package com.wyrix.news.ads

/**
 * Seam for a future ads SDK (e.g. AdMob). Nothing is shown today: adding AdMob needs a real
 * AdMob app id and a consent flow, so it is intentionally not wired into this build.
 */
interface AdController {
    val isEnabled: Boolean
}

object NoOpAdController : AdController {
    override val isEnabled: Boolean = false
}

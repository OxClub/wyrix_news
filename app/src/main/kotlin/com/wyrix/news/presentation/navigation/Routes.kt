package com.wyrix.news.presentation.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val INDIA = "india"
    const val WORLD = "world"
    const val SAVED = "saved"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val FAVORITES = "favorites"

    const val ARTICLE = "article/{id}"
    const val FEED = "feed/{kind}/{arg}"
    const val STATE = "state/{id}"
    const val COUNTRY = "country/{iso}"
    const val INFO = "info/{type}"

    fun article(id: String) = "article/$id"
    fun feed(kind: String, arg: String = "-") = "feed/$kind/${Uri.encode(arg)}"
    fun state(id: String) = "state/$id"
    fun country(iso: String) = "country/$iso"
    fun info(type: String) = "info/$type"
}

package com.wyrix.news.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.wyrix.news.R
import com.wyrix.news.domain.model.Article

data class ArticleActions(
    val onOpen: (Article) -> Unit,
    val onToggleBookmark: (Article) -> Unit,
    val onShare: (Article) -> Unit,
)

object ExternalActions {
    fun openUrl(context: Context, url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") return
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        } catch (e: ActivityNotFoundException) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun share(context: Context, article: Article) {
        val text = context.getString(R.string.share_text, article.title, article.sourceName, article.url)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, article.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(send, null)) }
    }
}

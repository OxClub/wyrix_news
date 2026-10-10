package com.wyrix.news

import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.ArticleIds

fun testArticle(
    n: Int,
    title: String = "Headline number $n about local development projects",
    host: String = "news$n.example.com",
    publishedAt: Long = 1_000_000L - n,
    url: String = "https://$host/story/$n",
) = Article(
    id = ArticleIds.forUrl(url),
    title = title,
    url = url,
    description = null,
    sourceName = host,
    sourceHost = host,
    publishedAt = publishedAt,
)

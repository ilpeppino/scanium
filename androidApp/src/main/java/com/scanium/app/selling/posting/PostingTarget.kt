package com.scanium.app.selling.posting

enum class PostingTargetType {
    URL,
    DEEPLINK,
    APP,
}

data class PostingTarget(
    val id: String,
    val label: String,
    val type: PostingTargetType,
    val value: String,
)

object PostingTargetDefaults {
    const val DEFAULT_BROWSER_ID = "browser"
    const val CUSTOM_TARGET_ID = "custom"

    fun presets(): List<PostingTarget> {
        return listOf(
            PostingTarget(
                id = DEFAULT_BROWSER_ID,
                label = "Browser",
                type = PostingTargetType.URL,
                value = "https://www.google.com",
            ),
            PostingTarget(
                id = "marktplaats",
                label = "Marktplaats",
                type = PostingTargetType.URL,
                value = "https://www.marktplaats.nl/",
            ),
        )
    }

    fun custom(url: String): PostingTarget {
        return PostingTarget(
            id = CUSTOM_TARGET_ID,
            label = "Custom URL",
            type = PostingTargetType.URL,
            value = url,
        )
    }
}

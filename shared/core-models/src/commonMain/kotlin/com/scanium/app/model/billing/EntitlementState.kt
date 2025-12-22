package com.scanium.app.model.billing

import com.scanium.app.model.user.UserEdition

enum class EntitlementSource {
    LOCAL_CACHE,
    PLAY_BILLING,
    SERVER_VERIFIED
}

data class EntitlementState(
    val status: UserEdition,
    val source: EntitlementSource,
    val expiresAt: Long? = null,
    val lastUpdatedAt: Long,
    val isGracePeriod: Boolean = false
) {
    companion object {
        val DEFAULT = EntitlementState(
            status = UserEdition.FREE,
            source = EntitlementSource.LOCAL_CACHE,
            lastUpdatedAt = 0L
        )
    }
}

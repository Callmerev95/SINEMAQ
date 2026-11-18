package com.nuvio.app.features.membership

enum class MemberTier {
    FREE,
    SUPPORTER,
    LIFETIME,
}

fun MemberTier?.profileLimit(): Int = when (this) {
    MemberTier.LIFETIME -> 6
    MemberTier.SUPPORTER -> 5
    else -> 3
}

enum class CosmeticEntitlement {
    GOLD_THEME,
    JADE_THEME,
    ROSE_GOLD_THEME,
    ARCTIC_BLUE_THEME,
    GRAPHITE_THEME,
    PROFILE_BACKGROUNDS,
    PROFILE_AVATARS,
}

data class CosmeticEntitlements(
    private val values: Set<CosmeticEntitlement> = emptySet(),
) {
    fun includes(entitlement: CosmeticEntitlement): Boolean = entitlement in values

    fun names(): Set<String> = values.mapTo(linkedSetOf()) { it.name }

    companion object {
        val None = CosmeticEntitlements()
    }
}

data class MemberAccess(
    val tier: MemberTier? = null,
    val entitlements: CosmeticEntitlements = CosmeticEntitlements.None,
) {
    companion object {
        val None = MemberAccess()
    }
}

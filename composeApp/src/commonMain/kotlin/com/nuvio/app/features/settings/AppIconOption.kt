package com.nuvio.app.features.settings

internal enum class AppIconOption(
    val key: String,
    val platformName: String?,
) {
    ORIGINAL(
        key = "original",
        platformName = null,
    ),
    EMERALD(
        key = "emerald",
        platformName = "AppIconEmerald",
    ),
    GOLD(
        key = "gold",
        platformName = "AppIconGold",
    ),
    OCEAN(
        key = "ocean",
        platformName = "AppIconOcean",
    ),
    ROYAL(
        key = "royal",
        platformName = "AppIconRoyal",
    ),
    SUNSET(
        key = "sunset",
        platformName = "AppIconSunset",
    ),
    ;

    companion object {
        fun fromPlatformName(name: String?): AppIconOption =
            entries.firstOrNull { it.platformName == name } ?: ORIGINAL
    }
}

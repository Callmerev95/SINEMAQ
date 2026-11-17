package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.AppTheme
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

internal val AppIconOption.labelResource: StringResource
    get() = when (this) {
        AppIconOption.ORIGINAL -> Res.string.settings_appearance_app_icon_original
        AppIconOption.EMERALD -> Res.string.settings_appearance_app_icon_emerald
        AppIconOption.GOLD -> Res.string.settings_appearance_app_icon_gold
        AppIconOption.OCEAN -> Res.string.settings_appearance_app_icon_ocean
        AppIconOption.ROYAL -> Res.string.settings_appearance_app_icon_royal
        AppIconOption.SUNSET -> Res.string.settings_appearance_app_icon_sunset
    }

internal val AppIconOption.previewResource: DrawableResource
    get() = when (this) {
        AppIconOption.ORIGINAL -> Res.drawable.app_icon_original
        AppIconOption.EMERALD -> Res.drawable.app_icon_emerald
        AppIconOption.GOLD -> Res.drawable.app_icon_gold
        AppIconOption.OCEAN -> Res.drawable.app_icon_ocean
        AppIconOption.ROYAL -> Res.drawable.app_icon_royal
        AppIconOption.SUNSET -> Res.drawable.app_icon_sunset
    }

internal val AppIconOption.wordmarkResource: DrawableResource
    get() = when (this) {
        AppIconOption.ORIGINAL -> Res.drawable.app_logo_wordmark_original
        AppIconOption.EMERALD -> Res.drawable.app_logo_wordmark_emerald
        AppIconOption.GOLD -> Res.drawable.app_logo_wordmark_gold
        AppIconOption.OCEAN -> Res.drawable.app_logo_wordmark_ocean
        AppIconOption.ROYAL -> Res.drawable.app_logo_wordmark_royal
        AppIconOption.SUNSET -> Res.drawable.app_logo_wordmark_sunset
    }

internal fun AppTheme.wordmarkResource(fallback: AppIconOption): DrawableResource =
    when (this) {
        AppTheme.GOLD -> AppIconOption.GOLD.wordmarkResource
        AppTheme.JADE -> AppIconOption.EMERALD.wordmarkResource
        AppTheme.ROSE_GOLD -> AppIconOption.OCEAN.wordmarkResource
        AppTheme.ARCTIC_BLUE -> AppIconOption.GOLD.wordmarkResource
        AppTheme.GRAPHITE -> AppIconOption.SUNSET.wordmarkResource
        else -> fallback.wordmarkResource
    }

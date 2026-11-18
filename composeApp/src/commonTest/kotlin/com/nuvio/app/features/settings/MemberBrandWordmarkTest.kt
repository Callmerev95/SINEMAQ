package com.nuvio.app.features.settings

import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.membership.MemberTier
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberBrandWordmarkTest {
    @Test
    fun supporterBadgeMatchesWebsiteWarmGradient() {
        val style = MemberTier.SUPPORTER.badgeStyle()

        assertEquals("Supporter", style.label)
        assertEquals(
            listOf(
                0f to Color(0xFFD4843D),
                0.5f to Color(0xFFFFDE90),
                1f to Color(0xFFD4843D),
            ),
            style.colorStops,
        )
    }

    @Test
    fun lifetimeBadgeMatchesGoldToPurpleGradient() {
        val style = MemberTier.LIFETIME.badgeStyle()

        assertEquals("Lifetime", style.label)
        assertEquals(
            listOf(
                0f to Color(0xFFD4843D),
                0.45f to Color(0xFFFFDE90),
                0.75f to Color(0xFFB57BFF),
                1f to Color(0xFF9C6BFF),
            ),
            style.colorStops,
        )
    }

    @Test
    fun freeBadgeMatchesNeutralGrayGradient() {
        val style = MemberTier.FREE.badgeStyle()

        assertEquals("Free", style.label)
        assertEquals(
            listOf(
                0f to Color(0xFF7A7F87),
                0.5f to Color(0xFFB6BAC0),
                1f to Color(0xFF7A7F87),
            ),
            style.colorStops,
        )
    }

    @Test
    fun badgeMotionMatchesWebsiteGradientGeometryAndTiming() {
        assertEquals(100f, MemberBadgeGradientAngleDegrees)
        assertEquals(2.4f, MemberBadgeGradientWidthMultiplier)
        assertEquals(2_750, MemberBadgeSweepHalfDurationMs)
    }
}

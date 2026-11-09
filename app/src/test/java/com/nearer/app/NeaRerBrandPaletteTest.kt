package com.nearer.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class NeaRerBrandPaletteTest {
    @Test fun primaryPaletteUsesBlueAndGoldOnMidnightPanels() {
        assertEquals(Color(0xFF101A2B), Midnight)
        assertEquals(Color(0xFF1A2940), Panel)
        assertEquals(Color(0xFF6EA8FF), Mint)
        assertEquals(Color(0xFFFFD12D), Gold)
    }

    @Test fun blueAndGoldAccentsRemainDistinctAndOpaque() {
        assertEquals(1f, Mint.alpha)
        assertEquals(1f, Gold.alpha)
        assertEquals(Color(0xFF2563EB), BrandBlue)
        assertEquals(Color(0xFF6EA8FF), Mint)
        assertEquals(Color(0xFFFFD12D), Gold)
    }
}

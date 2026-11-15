package com.nearer.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class NeaRerBrandPaletteTest {
    @Test fun royalBlueAndGoldBrandPaletteIsRestored() {
        assertEquals(Color(0xFF101A2B), Midnight)
        assertEquals(Color(0xFF1A2940), Panel)
        // Mint is retained as a compatibility name for screens not yet migrated.
        assertEquals(Color(0xFF2868E8), Mint)
        assertEquals(Color(0xFFA8B6C9), Muted)
        assertEquals(Color(0xFFF6F8FC), White)
        assertEquals(Color(0xFF2868E8), BrandBlue)
        assertEquals(Color(0xFFFFD12D), Gold)
    }

    @Test fun sharedDesignSystemMatchesMainThemeTokens() {
        assertEquals(Midnight, NeaRerPalette.Midnight)
        assertEquals(Panel, NeaRerPalette.Panel)
        assertEquals(BrandBlue, NeaRerPalette.AccentBlue)
        assertEquals(Gold, NeaRerPalette.Gold)
        assertEquals(Mint, NeaRerPalette.Mint)
        assertEquals(White, NeaRerPalette.TextPrimary)
        assertEquals(Muted, NeaRerPalette.TextMuted)
    }

    @Test fun signatureAccentsRemainOpaqueAndDistinct() {
        assertEquals(1f, Mint.alpha, 0f)
        assertEquals(1f, Gold.alpha, 0f)
        assertEquals(Color(0xFF2868E8), Mint)
        assertEquals(Color(0xFFFFD12D), Gold)
        assertNotEquals(Mint, Gold)
    }
}

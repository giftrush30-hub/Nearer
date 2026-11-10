package com.nearer.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class NeaRerBrandPaletteTest {
    @Test fun primaryPaletteUsesBlueAndGoldOnMidnightPanels() {
        assertEquals(Color(0xFF07182A), Midnight)
        assertEquals(Color(0xFF102B46), Panel)
        assertEquals(Color(0xFF78B9FF), BlueAccent)
        assertEquals(Color(0xFFFFC928), Gold)
    }

    @Test fun blueAndGoldAccentsRemainDistinctAndOpaque() {
        assertEquals(1f, BlueAccent.alpha, 0f)
        assertEquals(1f, Gold.alpha, 0f)
        assertEquals(Color(0xFF004B90), BrandBlue)
        assertEquals(Color(0xFF78B9FF), BlueAccent)
        assertEquals(Color(0xFFFFC928), Gold)
    }
}

package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeaRerNavigationTest {
    @Test
    fun bottomNavigationHasFiveUniquePhoneDestinations() {
        val destinations = NeaRerNavigation.destinations
        assertEquals(5, destinations.size)
        assertEquals(5, destinations.map { it.id }.distinct().size)
        assertEquals(listOf("home", "nearby", "chats", "community", "more"), destinations.map { it.id })
    }

    @Test
    fun secondaryFeaturesStayReachableFromMoreHub() {
        assertTrue(NeaRerNavigation.destinations.any { it.id == "more" })
        assertTrue(NeaRerNavigation.destinations.none { it.id == "games" || it.id == "flash" })
    }
}

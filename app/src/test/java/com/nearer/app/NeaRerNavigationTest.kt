package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeaRerNavigationTest {
    @Test
    fun bottomNavigationMatchesMessagingFirstPhoneDestinations() {
        val destinations = NeaRerNavigation.destinations
        assertEquals(5, destinations.size)
        assertEquals(5, destinations.map { it.id }.distinct().size)
        assertEquals(listOf("chats", "people", "share", "games", "more"), destinations.map { it.id })
        assertEquals(listOf("Chats", "People", "Share", "Games", "More"), destinations.map { it.label })
    }

    @Test
    fun gamesRemainReachableAndSecondaryEcosystemSpacesStayInMore() {
        val destinations = NeaRerNavigation.destinations
        assertTrue(destinations.any { it.id == "games" })
        assertTrue(destinations.any { it.id == "more" })
        assertFalse(destinations.any { it.id == "studio" || it.id == "academy" || it.id == "movies" })
    }

    @Test
    fun chatsRemainTheFirstDefaultDestination() {
        assertEquals("chats", NeaRerNavigation.destinations.first().id)
    }
}

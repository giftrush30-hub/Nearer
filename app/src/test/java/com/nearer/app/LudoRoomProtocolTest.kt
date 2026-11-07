package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LudoRoomProtocolTest {
    @Test fun encodesAndParsesInvite() {
        val raw = LudoRoomProtocol.encode("invite", "room-123", "Host")
        val packet = LudoRoomProtocol.parse(raw!!)
        assertEquals("invite", packet?.type)
        assertEquals("room-123", packet?.roomId)
        assertEquals(0L, packet?.sequence)
    }
    @Test fun parsesRoomChat() {
        val packet = LudoRoomProtocol.parse("""{"type":"chat","roomId":"room-123","body":"Good game"}""")
        assertEquals("Good game", packet?.body)
    }
    @Test fun roundTripsSequencedMoveIntent() {
        val raw = LudoRoomProtocol.encode("intent", "room-123", """{"action":"roll"}""", 4L)!!
        val packet = LudoRoomProtocol.parse(raw)
        assertEquals(4L, packet?.sequence)
        assertEquals("""{"action":"roll"}""", packet?.body)
    }
    @Test fun roundTripsSequencedStateEnvelope() {
        val raw = LudoRoomProtocol.encode("state", "room-123", "snapshot", 7L)!!
        val packet = LudoRoomProtocol.parse(raw)
        assertEquals("snapshot", packet?.body)
        assertEquals(7L, packet?.sequence)
    }
    @Test fun rejectsUnknownTypesAndOversizedPayloads() {
        assertNull(LudoRoomProtocol.parse("""{"type":"admin","roomId":"x"}"""))
        val tooLong = "x".repeat(301)
        assertNull(LudoRoomProtocol.parse("""{"type":"chat","roomId":"x","body":"$tooLong"}"""))
        assertNull(LudoRoomProtocol.parse("{"))
        assertNull(LudoRoomProtocol.parse("x".repeat(4097)))
    }
}

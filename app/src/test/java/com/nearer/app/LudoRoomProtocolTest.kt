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
    }
    @Test fun parsesRoomChat() {
        val packet = LudoRoomProtocol.parse("""{"type":"chat","roomId":"room-123","body":"Good game"}""")
        assertEquals("Good game", packet?.body)
    }
    @Test fun rejectsUnknownTypesAndOversizedPayloads() {
        assertNull(LudoRoomProtocol.parse("""{"type":"admin","roomId":"x"}"""))
        val tooLong = "x".repeat(301)
        assertNull(LudoRoomProtocol.parse("""{"type":"chat","roomId":"x","body":"$tooLong"}"""))
        assertNull(LudoRoomProtocol.parse("{"))
        assertNull(LudoRoomProtocol.parse("x".repeat(4097)))
    }
}

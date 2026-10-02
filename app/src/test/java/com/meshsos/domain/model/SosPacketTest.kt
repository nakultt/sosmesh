package com.meshsos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosPacketTest {

    private val packet = SosPacket(senderId = "ABCD1234", incident = IncidentInfo(message = "hi"))

    @Test
    fun `packets round trip through json`() {
        assertEquals(packet, SosPacket.fromBytes(packet.toBytes()))
        val ack = AckPacket(originalPacketId = packet.id, alertId = "A", uploadedBy = "B")
        assertEquals(ack, AckPacket.fromBytes(ack.toBytes()))
    }

    @Test
    fun `packet types are not confused`() {
        val ack = AckPacket(originalPacketId = packet.id, alertId = "A", uploadedBy = "B")
        assertNull(SosPacket.fromBytes(ack.toBytes()))
        assertNull(AckPacket.fromBytes(packet.toBytes()))
    }

    @Test
    fun `missing optional fields get defaults instead of nulls`() {
        val parsed = SosPacket.fromJson("""{"id":"x","type":"SOS","senderId":"s","incident":{}}""")
        assertNotNull(parsed)
        assertTrue(parsed!!.metadata.route.isEmpty())
        assertEquals("", parsed.incident.message)
        assertEquals(IncidentCategory.OTHER, parsed.incident.category)
    }

    @Test
    fun `unknown enum values and null route entries are tolerated`() {
        val parsed = SosPacket.fromJson(
            """{"id":"x","type":"SOS","senderId":"s","incident":{"category":"BOGUS"},"metadata":{"route":[null,{"deviceId":"d"}]}}"""
        )!!
        assertEquals(IncidentCategory.OTHER, parsed.incident.category)
        assertEquals(1, parsed.metadata.route.size)
    }

    @Test
    fun `required fields are enforced`() {
        assertNull(SosPacket.fromJson("""{"type":"SOS","senderId":"s","incident":{}}"""))
        assertNull(SosPacket.fromJson("""{"id":"x","type":"SOS","incident":{}}"""))
        assertNull(SosPacket.fromJson("""{"id":"x","type":"SOS","senderId":"s"}"""))
        assertNull(AckPacket.fromBytes("""{"id":"a","type":"ACK"}""".toByteArray()))
        assertNull(SosPacket.fromJson("garbage{"))
    }

    @Test
    fun `hop handling`() {
        val hopped = packet.incrementHop("RELAY001")
        assertEquals(1, hopped.metadata.currentHops)
        assertEquals("RELAY001", hopped.metadata.route.last().deviceId)
        assertFalse(hopped.isHopLimitReached())
        val atLimit = packet.copy(metadata = packet.metadata.copy(currentHops = 10, maxHops = 10))
        assertTrue(atLimit.isHopLimitReached())
    }

    @Test
    fun `expiry`() {
        assertFalse(packet.isExpired())
        val old = packet.copy(metadata = packet.metadata.copy(createdAt = 0))
        assertTrue(old.isExpired())
    }
}

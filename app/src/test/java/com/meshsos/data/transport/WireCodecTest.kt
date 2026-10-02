package com.meshsos.data.transport

import com.meshsos.domain.model.AckPacket
import com.meshsos.domain.model.IncidentInfo
import com.meshsos.domain.model.SosPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WireCodecTest {

    private val packet = SosPacket(senderId = "ABCD1234", incident = IncidentInfo(message = "help"))
    private val ack = AckPacket(originalPacketId = packet.id, alertId = "ALERT-1", uploadedBy = "EF012345")

    @Test
    fun `sos round trip`() {
        assertEquals(WireCodec.Message.Sos(packet), WireCodec.decode(WireCodec.encodeSos(packet)))
    }

    @Test
    fun `ack round trip`() {
        assertEquals(WireCodec.Message.Ack(ack), WireCodec.decode(WireCodec.encodeAck(ack)))
    }

    @Test
    fun `hello round trip keeps names containing separators`() {
        val decoded = WireCodec.decode(WireCodec.encodeHello("ABCD1234", "Pixel 7 (ABCD)"))
        assertEquals(WireCodec.Message.Hello("ABCD1234", "Pixel 7 (ABCD)"), decoded)
    }

    @Test
    fun `legacy raw json is still accepted`() {
        assertEquals(WireCodec.Message.Sos(packet), WireCodec.decode(packet.toBytes()))
        assertEquals(WireCodec.Message.Ack(ack), WireCodec.decode(ack.toBytes()))
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(WireCodec.decode(ByteArray(0)))
        assertNull(WireCodec.decode(byteArrayOf(0x7F, 1, 2)))
        assertNull(WireCodec.decode(byteArrayOf(0x01) + "not json".toByteArray()))
        // An ACK body framed as SOS must not be accepted as an SOS
        assertNull(WireCodec.decode(byteArrayOf(0x01) + ack.toBytes()))
    }

    @Test
    fun `identity parsing`() {
        assertEquals("ABCD1234" to "Pixel", WireCodec.parseIdentity("ABCD1234|Pixel"))
        assertEquals("Legacy-Name" to "Legacy-Name", WireCodec.parseIdentity("Legacy-Name"))
        assertEquals("ABCD1234" to "ABCD1234", WireCodec.parseIdentity("ABCD1234|"))
        assertNull(WireCodec.parseIdentity("  "))
    }
}

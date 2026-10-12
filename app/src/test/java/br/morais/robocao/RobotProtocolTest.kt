package br.morais.robocao

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RobotProtocolTest {

    private val appId = byteArrayOf(0x12, 0x34, 0x56)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun matchPacket_familyA_matchesOriginalLayout() {
        val protocol = RobotProtocol(appId, RobotFamily.A)

        val packet = protocol.matchPacket()

        // D0 + appId + bId(0,0,0) + counter 0 + cmd 0 + checksum + AA
        val checksum = (0xD0 + 0x12 + 0x34 + 0x56) and 0xFF
        assertArrayEquals(bytes(0xD0, 0x12, 0x34, 0x56, 0, 0, 0, 0, 0, checksum, 0xAA), packet)
    }

    @Test
    fun matchPacket_familyB_usesD5Header() {
        val protocol = RobotProtocol(appId, RobotFamily.B)

        assertEquals(0xD5.toByte(), protocol.matchPacket()[0])
    }

    @Test
    fun parseReply_validReply_returnsRobotId() {
        val protocol = RobotProtocol(appId, RobotFamily.A)
        val scanRecord = bytes(0x0C, 0xFF, 0xD1, 0x12, 0x34, 0x56, 0xA1, 0xB2, 0xC3, 0x00)

        val robotId = protocol.parseReply(scanRecord)

        assertArrayEquals(bytes(0xA1, 0xB2, 0xC3), robotId)
    }

    @Test
    fun parseReply_otherAppId_isIgnored() {
        val protocol = RobotProtocol(appId, RobotFamily.A)
        val scanRecord = bytes(0x0C, 0xFF, 0xD1, 0x99, 0x34, 0x56, 0xA1, 0xB2, 0xC3, 0x00)

        assertNull(protocol.parseReply(scanRecord))
    }

    @Test
    fun parseReply_wrongFamilyHeader_isIgnored() {
        val protocol = RobotProtocol(appId, RobotFamily.B)
        val scanRecord = bytes(0x0C, 0xFF, 0xD1, 0x12, 0x34, 0x56, 0xA1, 0xB2, 0xC3, 0x00)

        assertNull(protocol.parseReply(scanRecord))
    }

    @Test
    fun parseReply_tooShort_isIgnored() {
        val protocol = RobotProtocol(appId, RobotFamily.A)

        assertNull(protocol.parseReply(bytes(0x02, 0xFF, 0xD1)))
    }

    @Test
    fun commandPacket_containsRobotIdCommandAndChecksum() {
        val protocol = RobotProtocol(appId, RobotFamily.A)
        protocol.robotId = bytes(0xA1, 0xB2, 0xC3)

        val packet = protocol.commandPacket(Commands.FORWARD)

        val checksum = (0xE0 + 0x12 + 0x34 + 0x56 + 0xA1 + 0xB2 + 0xC3 + 1 + 1) and 0xFF
        assertArrayEquals(bytes(0xE0, 0x12, 0x34, 0x56, 0xA1, 0xB2, 0xC3, 1, 1, checksum, 0xAA), packet)
    }

    @Test
    fun commandPacket_familyB_usesE5Header() {
        val protocol = RobotProtocol(appId, RobotFamily.B)

        assertEquals(0xE5.toByte(), protocol.commandPacket(Commands.FORWARD)[0])
    }

    @Test
    fun commandPacket_sameCommandRepeated_keepsCounter() {
        val protocol = RobotProtocol(appId, RobotFamily.A)

        val first = protocol.commandPacket(Commands.FORWARD)
        val second = protocol.commandPacket(Commands.FORWARD)

        assertEquals(first[7], second[7])
    }

    @Test
    fun commandPacket_newCommand_incrementsCounter() {
        val protocol = RobotProtocol(appId, RobotFamily.A)

        protocol.commandPacket(Commands.FORWARD)
        val packet = protocol.commandPacket(Commands.STOP)

        assertEquals(2.toByte(), packet[7])
    }

    @Test
    fun commandPacket_initialStop_doesNotIncrementCounter() {
        val protocol = RobotProtocol(appId, RobotFamily.A)

        assertEquals(0.toByte(), protocol.commandPacket(Commands.STOP)[7])
    }

    @Test
    fun commandPacket_counterWrapsAt255() {
        val protocol = RobotProtocol(appId, RobotFamily.A)

        repeat(254) { i -> protocol.commandPacket(if (i % 2 == 0) Commands.FORWARD else Commands.STOP) }
        val packet = protocol.commandPacket(Commands.LEFT)

        assertEquals(0.toByte(), packet[7])
    }

    @Test
    fun advertisePayload_prefixesWith07() {
        val packet = bytes(0xE0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0xAA)

        val payload = RobotProtocol.advertisePayload(packet)

        assertEquals(12, payload.size)
        assertEquals(0x07.toByte(), payload[0])
        assertArrayEquals(packet, payload.copyOfRange(1, 12))
    }
}

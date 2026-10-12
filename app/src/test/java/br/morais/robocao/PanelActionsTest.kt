package br.morais.robocao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PanelActionsTest {

    @Test
    fun familyB_hasClawWithCode5() {
        assertEquals(5, Commands.claw(RobotFamily.B))
    }

    @Test
    fun familyA_hasNoClaw() {
        assertNull(Commands.claw(RobotFamily.A))
    }

    @Test
    fun panel_doesNotRepeatClaw() {
        val codes = Commands.panelActions(RobotFamily.B).map { it.code }
        assertFalse(codes.contains(Commands.CLAW_B))
    }

    @Test
    fun panel_codesAreUniquePerFamily() {
        RobotFamily.entries.forEach { family ->
            val codes = Commands.panelActions(family).map { it.code }
            assertEquals("códigos repetidos em $family", codes.size, codes.toSet().size)
        }
    }

    @Test
    fun panel_matchesOriginalAppCodes() {
        assertEquals(
            listOf(10, 9, 12, 17, 11, 15, 18, 8, 6, 7, 13, 14, 19),
            Commands.panelActions(RobotFamily.B).map { it.code },
        )
        assertEquals(
            listOf(56, 40, 57, 43, 41, 10, 62, 46, 12, 15, 13),
            Commands.panelActions(RobotFamily.A).map { it.code },
        )
    }
}

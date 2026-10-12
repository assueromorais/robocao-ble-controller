package br.morais.robocao

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyOrderTest {

    @Test
    fun auto_withoutMemory_triesAThenB() {
        assertEquals(listOf(RobotFamily.A, RobotFamily.B), familyOrder(selected = null, remembered = null))
    }

    @Test
    fun auto_rememberedB_triesBFirst() {
        assertEquals(listOf(RobotFamily.B, RobotFamily.A), familyOrder(selected = null, remembered = RobotFamily.B))
    }

    @Test
    fun manualSelection_ignoresMemory() {
        assertEquals(listOf(RobotFamily.A), familyOrder(selected = RobotFamily.A, remembered = RobotFamily.B))
    }

    @Test
    fun fromName_unknownValue_returnsNull() {
        assertEquals(null, RobotFamily.fromName("Z"))
        assertEquals(null, RobotFamily.fromName(null))
        assertEquals(RobotFamily.B, RobotFamily.fromName("B"))
    }
}

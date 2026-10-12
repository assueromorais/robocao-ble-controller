package br.morais.robocao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramTest {

    private val appId = byteArrayOf(1, 2, 3)

    @Test
    fun forcedCommand_incrementsCounterEvenWhenRepeated() {
        val protocol = RobotProtocol(appId, RobotFamily.B)

        val first = protocol.commandPacket(Commands.FORWARD, force = true)
        val second = protocol.commandPacket(Commands.FORWARD, force = true)

        assertEquals((first[7] + 1).toByte(), second[7])
    }

    @Test
    fun familyB_usesStart16AndRun20() {
        assertEquals(16, Program.startCode(RobotFamily.B))
        assertEquals(20, Program.runCode(RobotFamily.B))
    }

    @Test
    fun familyA_usesStart39AndRun55() {
        assertEquals(39, Program.startCode(RobotFamily.A))
        assertEquals(55, Program.runCode(RobotFamily.A))
    }

    @Test
    fun familyB_stepsMatchOriginalApp_withAffectionFixed() {
        assertEquals(
            listOf(1, 2, 3, 4, 13, 14, 10, 9, 12, 17, 11, 18, 15, 6, 7, 5),
            Program.steps(RobotFamily.B).map { it.code },
        )
    }

    @Test
    fun familyA_stepsUse81To95() {
        assertEquals((81..95).toList(), Program.steps(RobotFamily.A).map { it.code })
    }

    @Test
    fun transmission_startsResetsSendsStepsThenRuns() {
        val plan = Program.transmission(RobotFamily.B, listOf(1, 1, 9))

        assertEquals(
            listOf(
                Transmission(16, forced = false, holdMs = Program.START_HOLD_MS),
                Transmission(0, forced = false, holdMs = Program.RESET_HOLD_MS),
                Transmission(1, forced = true, holdMs = Program.STEP_HOLD_MS),
                Transmission(1, forced = true, holdMs = Program.STEP_HOLD_MS),
                Transmission(9, forced = true, holdMs = Program.STEP_HOLD_MS),
                Transmission(20, forced = true, holdMs = Program.RUN_HOLD_MS),
                Transmission(0, forced = false, holdMs = 0),
            ),
            plan,
        )
    }

    @Test
    fun transmission_limitsTo16Steps() {
        val plan = Program.transmission(RobotFamily.B, List(20) { 1 })

        assertEquals(Program.MAX_STEPS, plan.count { it.code == 1 })
    }

    @Test
    fun familyB_hidesDiagonals_becauseTheirCodesAreActions() {
        assertTrue(Program.steps(RobotFamily.B).any { it.code == Commands.FORWARD_RIGHT })
        assertEquals(false, Commands.diagonalsSafe(RobotFamily.B))
        assertEquals(true, Commands.diagonalsSafe(RobotFamily.A))
    }

    @Test
    fun stepsOffset_pointsToFirstStepInPlan() {
        val plan = Program.transmission(RobotFamily.B, listOf(9, 10))

        assertEquals(Transmission(9, forced = true, holdMs = Program.STEP_HOLD_MS), plan[Program.STEPS_OFFSET])
        assertEquals(10, plan[Program.STEPS_OFFSET + 1].code)
    }
}

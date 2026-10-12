package br.morais.robocao

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TiltControllerTest {

    private val g = 9.81f

    /** Vetor gravidade de um celular inclinado [pitchDeg] (frente+) e [rollDeg] (direita+) a partir de deitado. */
    private fun gravity(pitchDeg: Double, rollDeg: Double): FloatArray {
        val p = Math.toRadians(pitchDeg)
        val r = Math.toRadians(rollDeg)
        return floatArrayOf(
            (-g * sin(r) * cos(p)).toFloat(),
            (-g * sin(p)).toFloat(),
            (g * cos(p) * cos(r)).toFloat(),
        )
    }

    private fun calibratedFlat() = TiltController().apply { calibrate(gravity(0.0, 0.0)) }

    @Test
    fun flat_isStop() {
        assertEquals(Commands.STOP, calibratedFlat().update(gravity(0.0, 0.0)))
    }

    @Test
    fun smallShake_staysInDeadZone() {
        assertEquals(Commands.STOP, calibratedFlat().update(gravity(8.0, -8.0)))
    }

    @Test
    fun tiltForward_isForward() {
        assertEquals(Commands.FORWARD, calibratedFlat().update(gravity(25.0, 0.0)))
    }

    @Test
    fun tiltBack_isBackward() {
        assertEquals(Commands.BACKWARD, calibratedFlat().update(gravity(-25.0, 0.0)))
    }

    @Test
    fun tiltRight_isRight() {
        assertEquals(Commands.RIGHT, calibratedFlat().update(gravity(0.0, 25.0)))
    }

    @Test
    fun tiltLeft_isLeft() {
        assertEquals(Commands.LEFT, calibratedFlat().update(gravity(0.0, -25.0)))
    }

    @Test
    fun dominantAxisWins() {
        assertEquals(Commands.FORWARD, calibratedFlat().update(gravity(30.0, 18.0)))
        assertEquals(Commands.LEFT, calibratedFlat().update(gravity(18.0, -30.0)))
    }

    @Test
    fun neutralIsWhereverTheUserHeldThePhone() {
        val tilt = TiltController().apply { calibrate(gravity(40.0, 0.0)) }

        assertEquals(Commands.STOP, tilt.update(gravity(40.0, 0.0)))
        assertEquals(Commands.FORWARD, tilt.update(gravity(62.0, 0.0)))
        assertEquals(Commands.BACKWARD, tilt.update(gravity(18.0, 0.0)))
    }

    @Test
    fun hysteresis_keepsMovingUntilBackNearNeutral() {
        val tilt = calibratedFlat()

        assertEquals(Commands.FORWARD, tilt.update(gravity(20.0, 0.0)))
        // 12° já não ativaria o movimento, mas mantém porque ainda está acima da saída (10°).
        assertEquals(Commands.FORWARD, tilt.update(gravity(12.0, 0.0)))
        assertEquals(Commands.STOP, tilt.update(gravity(6.0, 0.0)))
    }

    @Test
    fun withoutCalibration_isStop() {
        assertEquals(Commands.STOP, TiltController().update(gravity(40.0, 0.0)))
    }
}

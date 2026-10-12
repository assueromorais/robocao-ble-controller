package br.morais.robocao

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Converte a inclinação do celular (vetor gravidade do sensor) em comando de movimento.
 *
 * A posição em que o celular estava ao ligar o modo vira o "neutro". Fora da zona morta,
 * o eixo mais inclinado decide: frente/trás ou esquerda/direita.
 */
class TiltController(
    private val enterDegrees: Double = 15.0,
    private val exitDegrees: Double = 10.0,
) {
    private var neutralPitch: Double? = null
    private var neutralRoll = 0.0
    private var current = Commands.STOP

    fun calibrate(gravity: FloatArray) {
        neutralPitch = pitch(gravity)
        neutralRoll = roll(gravity)
        current = Commands.STOP
    }

    fun update(gravity: FloatArray): Int {
        val basePitch = neutralPitch ?: return Commands.STOP
        val dPitch = pitch(gravity) - basePitch
        val dRoll = roll(gravity) - neutralRoll
        val strongest = maxOf(abs(dPitch), abs(dRoll))

        // Histerese: para começar a andar precisa passar de [enterDegrees];
        // para parar precisa voltar abaixo de [exitDegrees].
        val threshold = if (current == Commands.STOP) enterDegrees else exitDegrees
        current = when {
            strongest < threshold -> Commands.STOP
            abs(dPitch) >= abs(dRoll) -> if (dPitch > 0) Commands.FORWARD else Commands.BACKWARD
            else -> if (dRoll > 0) Commands.RIGHT else Commands.LEFT
        }
        return current
    }

    /** Positivo quando o topo do celular desce (inclinar para frente). */
    private fun pitch(g: FloatArray) = Math.toDegrees(atan2(-g[1].toDouble(), g[2].toDouble()))

    /** Positivo quando o lado direito do celular desce. */
    private fun roll(g: FloatArray) = Math.toDegrees(atan2(-g[0].toDouble(), g[2].toDouble()))
}

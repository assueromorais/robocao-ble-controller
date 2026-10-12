package br.morais.robocao

/** Conta falhas seguidas de pareamento para sugerir desligar e ligar o robô. */
class ConnectionTips(private val failuresBeforeTip: Int = 2) {

    private var consecutiveFailures = 0

    /** Retorna true quando já é hora de mostrar a dica de reiniciar o robô. */
    fun recordFailure(): Boolean {
        consecutiveFailures++
        return consecutiveFailures >= failuresBeforeTip
    }

    fun recordSuccess() {
        consecutiveFailures = 0
    }
}

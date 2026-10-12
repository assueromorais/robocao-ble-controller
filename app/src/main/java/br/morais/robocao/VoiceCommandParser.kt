package br.morais.robocao

import java.text.Normalizer

/** Converte o texto reconhecido (offline, pelo Vosk) em código de comando do robô. */
object VoiceCommandParser {

    // Ordem importa: a primeira frase encontrada no texto vence.
    private val phrases: List<Pair<String, Int>> = listOf(
        "muito bem" to Commands.GOOD_BOY,
        "para" to Commands.STOP,
        "parado" to Commands.STOP,
        "frente" to Commands.VOICE_FORWARD,
        "anda" to Commands.VOICE_FORWARD,
        "vem" to Commands.VOICE_FORWARD,
        "trás" to Commands.VOICE_BACKWARD,
        "volta" to Commands.VOICE_BACKWARD,
        "esquerda" to Commands.VOICE_LEFT,
        "direita" to Commands.VOICE_RIGHT,
        "senta" to Commands.SIT,
        "sentado" to Commands.SIT,
        "pata" to Commands.SHAKE_HANDS,
        "deita" to Commands.LIE_DOWN,
        "deitado" to Commands.LIE_DOWN,
        "exercício" to Commands.EXERCISE,
        "dança" to Commands.DANCE,
        "canta" to Commands.SING,
        "oi" to Commands.HELLO,
        "olá" to Commands.HELLO,
        "fofo" to Commands.CUTE,
    )

    fun keywords(): List<String> = phrases.map { it.first }

    /**
     * Gramática para o Vosk: limitar o vocabulário às palavras de comando deixa o
     * reconhecimento bem mais preciso. "[unk]" absorve qualquer outra fala.
     */
    fun grammarJson(): String =
        (keywords() + "[unk]").joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

    fun parse(text: String): Int? {
        val words = normalize(text).split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        for ((phrase, command) in phrases) {
            val phraseWords = normalize(phrase).split(" ")
            if (containsSequence(words, phraseWords)) return command
        }
        return null
    }

    private fun containsSequence(words: List<String>, sequence: List<String>): Boolean =
        words.windowed(sequence.size).any { it == sequence }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}

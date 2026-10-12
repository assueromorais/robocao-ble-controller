package br.morais.robocao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun parse_singleWords_mapToVoiceCodes() {
        assertEquals(Commands.VOICE_FORWARD, VoiceCommandParser.parse("frente"))
        assertEquals(Commands.VOICE_BACKWARD, VoiceCommandParser.parse("trás"))
        assertEquals(Commands.VOICE_LEFT, VoiceCommandParser.parse("esquerda"))
        assertEquals(Commands.VOICE_RIGHT, VoiceCommandParser.parse("direita"))
        assertEquals(Commands.SIT, VoiceCommandParser.parse("senta"))
        assertEquals(Commands.SHAKE_HANDS, VoiceCommandParser.parse("pata"))
        assertEquals(Commands.LIE_DOWN, VoiceCommandParser.parse("deita"))
        assertEquals(Commands.EXERCISE, VoiceCommandParser.parse("exercício"))
        assertEquals(Commands.DANCE, VoiceCommandParser.parse("dança"))
        assertEquals(Commands.SING, VoiceCommandParser.parse("canta"))
        assertEquals(Commands.HELLO, VoiceCommandParser.parse("oi"))
        assertEquals(Commands.CUTE, VoiceCommandParser.parse("fofo"))
        assertEquals(Commands.STOP, VoiceCommandParser.parse("para"))
    }

    @Test
    fun parse_phrase_findsCommandInsideSentence() {
        assertEquals(Commands.SIT, VoiceCommandParser.parse("rex senta aí"))
        assertEquals(Commands.SHAKE_HANDS, VoiceCommandParser.parse("dá a pata"))
        assertEquals(Commands.GOOD_BOY, VoiceCommandParser.parse("muito bem"))
    }

    @Test
    fun parse_ignoresCaseAndAccents() {
        assertEquals(Commands.VOICE_BACKWARD, VoiceCommandParser.parse("TRAS"))
        assertEquals(Commands.DANCE, VoiceCommandParser.parse("Danca"))
        assertEquals(Commands.EXERCISE, VoiceCommandParser.parse("exercicio"))
    }

    @Test
    fun parse_synonyms() {
        assertEquals(Commands.VOICE_FORWARD, VoiceCommandParser.parse("anda"))
        assertEquals(Commands.VOICE_FORWARD, VoiceCommandParser.parse("vem"))
        assertEquals(Commands.VOICE_BACKWARD, VoiceCommandParser.parse("volta"))
        assertEquals(Commands.SIT, VoiceCommandParser.parse("sentado"))
        assertEquals(Commands.LIE_DOWN, VoiceCommandParser.parse("deitado"))
        assertEquals(Commands.HELLO, VoiceCommandParser.parse("olá"))
        assertEquals(Commands.STOP, VoiceCommandParser.parse("parado"))
    }

    @Test
    fun parse_unknownOrEmpty_returnsNull() {
        assertNull(VoiceCommandParser.parse(""))
        assertNull(VoiceCommandParser.parse("banana"))
        assertNull(VoiceCommandParser.parse("[unk]"))
    }

    @Test
    fun parse_doesNotMatchPartialWords() {
        // "pata" não pode disparar dentro de "patati"; "oi" não pode disparar dentro de "oito"
        assertNull(VoiceCommandParser.parse("patati"))
        assertNull(VoiceCommandParser.parse("oito"))
    }

    @Test
    fun grammar_containsEveryKeywordPlusUnknown() {
        val grammar = VoiceCommandParser.grammarJson()

        VoiceCommandParser.keywords().forEach { keyword ->
            assert(grammar.contains("\"$keyword\"")) { "faltou $keyword na gramática" }
        }
        assert(grammar.contains("\"[unk]\""))
    }
}

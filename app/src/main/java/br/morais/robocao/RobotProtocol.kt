package br.morais.robocao

/**
 * Protocolo BLE do robô cão, extraído do app original (com.frank.yeldog).
 *
 * O robô não usa conexão GATT: o celular transmite pacotes de 11 bytes como
 * "manufacturer data" (ID 0x1100) em anúncios BLE, e o robô responde ao
 * pareamento com outro anúncio.
 *
 * Pacote: [header, appId0, appId1, appId2, robotId0, robotId1, robotId2, contador, comando, checksum, 0xAA]
 */
enum class RobotFamily(val matchHeader: Int, val replyHeader: Int, val commandHeader: Int, val label: Int) {
    /** Modelos 66005, 66006 e 66008 do app original. */
    A(0xD0, 0xD1, 0xE0, R.string.family_a),

    /** Modelos 66009, 66010 e 66011 do app original. */
    B(0xD5, 0xD6, 0xE5, R.string.family_b);

    companion object {
        fun fromName(name: String?): RobotFamily? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Ordem de tentativa no pareamento. Escolha manual tenta só aquela família;
 * no automático, a última que funcionou vem primeiro para conectar mais rápido.
 */
fun familyOrder(selected: RobotFamily?, remembered: RobotFamily?): List<RobotFamily> {
    if (selected != null) return listOf(selected)
    val first = remembered ?: RobotFamily.A
    return listOf(first) + RobotFamily.entries.filter { it != first }
}

object Commands {
    const val STOP = 0
    const val FORWARD = 1
    const val BACKWARD = 2
    const val LEFT = 3
    const val RIGHT = 4
    const val FORWARD_LEFT = 16
    const val FORWARD_RIGHT = 17
    const val BACKWARD_LEFT = 18
    const val BACKWARD_RIGHT = 19

    // Códigos usados pelo comando de voz do app original. Valem para todos os modelos.
    const val VOICE_FORWARD = 65
    const val VOICE_BACKWARD = 66
    const val VOICE_LEFT = 67
    const val VOICE_RIGHT = 68
    const val SIT = 69
    const val SHAKE_HANDS = 70
    const val LIE_DOWN = 71
    const val EXERCISE = 72
    const val DANCE = 73
    const val SING = 74
    const val HELLO = 75
    const val GOOD_BOY = 76
    const val CUTE = 77

    /**
     * Garra (abre/fecha). No app original é o botão "ivAction14", que só existe na família B;
     * no modelo 66010 ele mostra o ícone de garra. Nos outros modelos B o mesmo código faz outra ação.
     */
    const val CLAW_B = 5

    fun claw(family: RobotFamily): Int? = if (family == RobotFamily.B) CLAW_B else null

    /**
     * Na família B os códigos 16 a 19 são ações (16 até inicia a gravação de programa),
     * então as diagonais do controle ficam escondidas.
     */
    fun diagonalsSafe(family: RobotFamily): Boolean = family == RobotFamily.A

    /**
     * Ações extras do painel do app original, com o nome deduzido do ícone de cada botão.
     * A garra fica fora desta lista porque tem um card próprio na tela.
     */
    fun panelActions(family: RobotFamily): List<PanelAction> = when (family) {
        RobotFamily.A -> listOf(
            PanelAction(56, "🛌", R.string.panel_lie),
            PanelAction(40, "🐕", R.string.panel_sit),
            PanelAction(57, "🙆", R.string.panel_stretch),
            PanelAction(43, "🐩", R.string.panel_back_paw),
            PanelAction(41, "🐛", R.string.panel_crawl),
            PanelAction(10, "💬", R.string.panel_affection),
            PanelAction(62, "🗣️", R.string.panel_bark),
            PanelAction(46, "🚀", R.string.panel_turbo),
            PanelAction(12, "📖", R.string.panel_story),
            PanelAction(15, "🎶", R.string.panel_music),
            PanelAction(13, "🕺", R.string.panel_dance),
        )
        RobotFamily.B -> listOf(
            PanelAction(10, "🛌", R.string.panel_lie),
            PanelAction(9, "🐕", R.string.panel_sit),
            PanelAction(12, "🙆", R.string.panel_stretch),
            PanelAction(17, "🐩", R.string.panel_back_paw),
            PanelAction(11, "🐛", R.string.panel_crawl),
            PanelAction(15, "💬", R.string.panel_affection),
            PanelAction(18, "🗺️", R.string.panel_patrol),
            PanelAction(8, "📖", R.string.panel_story),
            PanelAction(6, "🎶", R.string.panel_music),
            PanelAction(7, "🕺", R.string.panel_dance),
            PanelAction(13, "🏊", R.string.panel_swim),
            PanelAction(14, "🚶", R.string.panel_walk_upright),
            PanelAction(19, "⏸️", R.string.panel_pause),
        )
    }
}

data class PanelAction(val code: Int, val emoji: String, val label: Int)

class RobotProtocol(private val appId: ByteArray, val family: RobotFamily) {

    var robotId = ByteArray(3)

    private var counter = 0
    private var lastCommand = Commands.STOP

    init {
        require(appId.size == 3) { "appId precisa ter 3 bytes" }
    }

    fun matchPacket(): ByteArray =
        buildPacket(family.matchHeader, robotId = ByteArray(3), counter = 0, command = 0)

    /**
     * Lê o anúncio do robô. Retorna o ID do robô quando a resposta é para este celular.
     * O app original compara os bytes crus do scan record a partir do índice 2.
     */
    fun parseReply(scanRecord: ByteArray): ByteArray? {
        if (scanRecord.size <= 8) return null
        if (scanRecord[2] != family.replyHeader.toByte()) return null
        if (scanRecord[3] != appId[0] || scanRecord[4] != appId[1] || scanRecord[5] != appId[2]) return null
        return scanRecord.copyOfRange(6, 9)
    }

    /**
     * O contador só avança quando o comando muda; o robô usa isso para ignorar repetições.
     * [force] avança mesmo repetindo, para o robô contar como um passo novo (programação).
     */
    fun commandPacket(command: Int, force: Boolean = false): ByteArray {
        if (command != lastCommand || force) {
            counter++
            lastCommand = command
        }
        counter %= 255
        return buildPacket(family.commandHeader, robotId, counter, command)
    }

    private fun buildPacket(header: Int, robotId: ByteArray, counter: Int, command: Int): ByteArray {
        val packet = byteArrayOf(
            header.toByte(),
            appId[0], appId[1], appId[2],
            robotId[0], robotId[1], robotId[2],
            counter.toByte(),
            command.toByte(),
            0,
            0xAA.toByte(),
        )
        packet[9] = checksum(packet)
        return packet
    }

    companion object {
        const val MANUFACTURER_ID = 0x1100

        fun advertisePayload(packet: ByteArray): ByteArray = byteArrayOf(0x07) + packet

        private fun checksum(packet: ByteArray): Byte {
            var sum = 0
            for (i in 0 until 9) sum += packet[i]
            return sum.toByte()
        }
    }
}

package br.morais.robocao

/** Um passo que a criança pode colocar na sequência. */
data class ProgramStep(val code: Int, val emoji: String, val label: Int)

/** Um pacote a transmitir e por quanto tempo mantê-lo no ar antes do próximo. */
data class Transmission(val code: Int, val forced: Boolean, val holdMs: Long)

/**
 * Modo programação, extraído da tela "Programa" do app original:
 * código de início → cada passo com contador forçado → código de executar.
 */
object Program {
    const val MAX_STEPS = 16

    /** Pacotes enviados antes do primeiro passo: início do modo programação e reset. */
    const val STEPS_OFFSET = 2
    const val START_HOLD_MS = 400L
    const val RESET_HOLD_MS = 300L
    const val STEP_HOLD_MS = 450L
    const val RUN_HOLD_MS = 500L

    fun startCode(family: RobotFamily): Int = if (family == RobotFamily.B) 16 else 39

    fun runCode(family: RobotFamily): Int = if (family == RobotFamily.B) 20 else 55

    fun steps(family: RobotFamily): List<ProgramStep> = when (family) {
        RobotFamily.A -> listOf(
            ProgramStep(81, "⬆️", R.string.step_forward),
            ProgramStep(82, "⬇️", R.string.step_backward),
            ProgramStep(83, "⬅️", R.string.step_left),
            ProgramStep(84, "➡️", R.string.step_right),
            ProgramStep(85, "⏪", R.string.step_spin_left),
            ProgramStep(86, "⏩", R.string.step_spin_right),
            ProgramStep(87, "🛌", R.string.panel_lie),
            ProgramStep(88, "🐕", R.string.panel_sit),
            ProgramStep(89, "🙆", R.string.panel_stretch),
            ProgramStep(90, "🐩", R.string.panel_back_paw),
            ProgramStep(91, "🐛", R.string.panel_crawl),
            ProgramStep(92, "🗣️", R.string.panel_bark),
            ProgramStep(93, "💬", R.string.panel_affection),
            ProgramStep(94, "🎶", R.string.panel_music),
            ProgramStep(95, "🕺", R.string.panel_dance),
        )
        // Ordem e códigos do modelo 66010 (o da garra). No original o passo "Carinho"
        // enviava 5 (garra) nesse modelo; aqui usa 15, igual ao painel e ao modelo 66009.
        RobotFamily.B -> listOf(
            ProgramStep(Commands.FORWARD, "⬆️", R.string.step_forward),
            ProgramStep(Commands.BACKWARD, "⬇️", R.string.step_backward),
            ProgramStep(Commands.LEFT, "⬅️", R.string.step_left),
            ProgramStep(Commands.RIGHT, "➡️", R.string.step_right),
            ProgramStep(13, "🏊", R.string.panel_swim),
            ProgramStep(14, "🚶", R.string.panel_walk_upright),
            ProgramStep(10, "🛌", R.string.panel_lie),
            ProgramStep(9, "🐕", R.string.panel_sit),
            ProgramStep(12, "🙆", R.string.panel_stretch),
            ProgramStep(17, "🐩", R.string.panel_back_paw),
            ProgramStep(11, "🐛", R.string.panel_crawl),
            ProgramStep(18, "🗺️", R.string.panel_patrol),
            ProgramStep(15, "💬", R.string.panel_affection),
            ProgramStep(6, "🎶", R.string.panel_music),
            ProgramStep(7, "🕺", R.string.panel_dance),
            ProgramStep(Commands.CLAW_B, "🦀", R.string.claw),
        )
    }

    fun transmission(family: RobotFamily, stepCodes: List<Int>): List<Transmission> =
        listOf(
            Transmission(startCode(family), forced = false, holdMs = START_HOLD_MS),
            Transmission(Commands.STOP, forced = false, holdMs = RESET_HOLD_MS),
        ) +
            stepCodes.take(MAX_STEPS).map { Transmission(it, forced = true, holdMs = STEP_HOLD_MS) } +
            listOf(
                Transmission(runCode(family), forced = true, holdMs = RUN_HOLD_MS),
                Transmission(Commands.STOP, forced = false, holdMs = 0),
            )
}

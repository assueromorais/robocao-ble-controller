package br.morais.robocao

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import kotlin.random.Random

class MainActivity : Activity(), RobotLink.Listener, VoiceControl.Listener {

    private lateinit var link: RobotLink
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var tricksGrid: GridLayout
    private val modelCards = mutableListOf<View>()
    private var currentFamily: RobotFamily? = null
    private val programSteps = mutableListOf<ProgramStep>()
    private lateinit var voice: VoiceControl
    private lateinit var voiceButton: View
    private lateinit var voiceHeard: TextView
    private lateinit var micHalo: View
    private var micPulse: ValueAnimator? = null
    private val prefs by lazy { getSharedPreferences("robo", MODE_PRIVATE) }
    private val connectionTips = ConnectionTips()

    /** O modelo de voz offline embutido é só em português; em outros idiomas o microfone fica escondido. */
    private val voiceSupported by lazy { resources.configuration.locales[0].language == "pt" }
    private var resumed = false

    // Controle por inclinação
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val tilt = TiltController()
    private var tiltActive = false
    private var tiltNeedsCalibration = false
    private var tiltCommand = Commands.STOP
    private val filteredGravity = FloatArray(3)
    private val dpadIds = mapOf(
        Commands.FORWARD to R.id.btn_forward,
        Commands.BACKWARD to R.id.btn_backward,
        Commands.LEFT to R.id.btn_left,
        Commands.RIGHT to R.id.btn_right,
    )
    private var voiceWanted = false

    /** Bluetooth desligado com o app aberto: desconecta em vez de mostrar "Conectado" à toa. */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                link.disconnect()
                onStatus(getString(R.string.bluetooth_off))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status)
        connectButton = findViewById(R.id.connect)
        tricksGrid = findViewById(R.id.tricks_grid)
        link = RobotLink(this, loadOrCreateAppId(), this)
        voice = VoiceControl(this, this)
        voiceButton = findViewById(R.id.voice_toggle)
        voiceHeard = findViewById(R.id.voice_heard)
        micHalo = findViewById(R.id.mic_halo)
        val advancedToggle = findViewById<TextView>(R.id.advanced_toggle)
        val advancedSection = findViewById<View>(R.id.advanced_section)
        advancedToggle.setOnClickListener {
            val open = advancedSection.visibility != View.VISIBLE
            advancedSection.visibility = if (open) View.VISIBLE else View.GONE
            advancedToggle.setText(if (open) R.string.advanced_open else R.string.advanced_closed)
        }
        voiceButton.setOnClickListener { toggleVoice() }
        findViewById<View>(R.id.tilt_toggle).setOnClickListener { if (tiltActive) stopTilt() else startTilt() }
        findViewById<View>(R.id.restart_tip_close).setOnClickListener {
            findViewById<View>(R.id.restart_tip).visibility = View.GONE
        }
        // Abre no navegador do aparelho; o app em si continua sem permissão de internet.
        findViewById<View>(R.id.privacy_link).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url))))
        }

        connectButton.setOnClickListener {
            if (link.isConnected) link.disconnect() else requestPermissionsThenConnect()
        }
        // Conectado, o botão grande some; desconectar fica na pílula e pede confirmação,
        // para uma criança não derrubar a conexão sem querer.
        findViewById<View>(R.id.status_pill).setOnClickListener {
            if (!link.isConnected) return@setOnClickListener
            AlertDialog.Builder(this)
                .setMessage(R.string.disconnect_confirm)
                .setPositiveButton(R.string.disconnect) { _, _ -> link.disconnect() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        bindHoldButton(R.id.btn_forward, Commands.FORWARD)
        bindHoldButton(R.id.btn_backward, Commands.BACKWARD)
        bindHoldButton(R.id.btn_left, Commands.LEFT)
        bindHoldButton(R.id.btn_right, Commands.RIGHT)
        bindHoldButton(R.id.btn_forward_left, Commands.FORWARD_LEFT)
        bindHoldButton(R.id.btn_forward_right, Commands.FORWARD_RIGHT)
        bindHoldButton(R.id.btn_backward_left, Commands.BACKWARD_LEFT)
        bindHoldButton(R.id.btn_backward_right, Commands.BACKWARD_RIGHT)
        bindHoldButton(R.id.btn_stop, Commands.STOP)

        bindHoldButton(R.id.btn_shake, Commands.SHAKE_HANDS)
        bindHoldButton(R.id.btn_exercise, Commands.EXERCISE)
        bindHoldButton(R.id.btn_dance, Commands.DANCE)
        bindHoldButton(R.id.btn_sing, Commands.SING)
        bindHoldButton(R.id.btn_hello, Commands.HELLO)
        bindHoldButton(R.id.btn_good_boy, Commands.GOOD_BOY)
        bindHoldButton(R.id.btn_cute, Commands.CUTE)

        restoreFamilySelection()
        // Mostra todas as ações do último robô pareado mesmo antes de conectar,
        // para o usuário ver tudo o que dá para fazer.
        rememberedFamily()?.let { buildPanel(it) }
        setControlsEnabled(false)
        setupTabs()
        setupProgram()
    }

    private fun setupTabs() {
        val control = findViewById<TextView>(R.id.tab_btn_control)
        val program = findViewById<TextView>(R.id.tab_btn_program)
        fun select(showProgram: Boolean) {
            findViewById<View>(R.id.tab_control).visibility = if (showProgram) View.GONE else View.VISIBLE
            findViewById<View>(R.id.tab_program).visibility = if (showProgram) View.VISIBLE else View.GONE
            findViewById<View>(R.id.voice_fab_area).visibility =
                if (showProgram || !voiceSupported) View.GONE else View.VISIBLE
            if (showProgram) stopVoice()
            control.setTextColor(getColor(if (showProgram) R.color.text_secondary else R.color.primary))
            program.setTextColor(getColor(if (showProgram) R.color.primary else R.color.text_secondary))
        }
        control.setOnClickListener { select(showProgram = false) }
        program.setOnClickListener { select(showProgram = true) }
        select(showProgram = false)
    }

    private fun setupProgram() {
        findViewById<View>(R.id.program_undo).setOnClickListener {
            if (programSteps.isNotEmpty()) programSteps.removeAt(programSteps.lastIndex)
            renderSequence()
        }
        findViewById<View>(R.id.program_clear).setOnClickListener {
            programSteps.clear()
            renderSequence()
        }
        findViewById<View>(R.id.program_run).setOnClickListener { runProgram() }
        buildPalette(currentFamily ?: rememberedFamily())
        renderSequence()
    }

    /** Ações que podem entrar na sequência; dependem da família do robô. */
    private fun buildPalette(family: RobotFamily?) {
        val palette = findViewById<GridLayout>(R.id.program_palette)
        palette.removeAllViews()
        findViewById<View>(R.id.program_palette_empty).visibility = if (family == null) View.VISIBLE else View.GONE
        if (family == null) return
        Program.steps(family).forEach { step ->
            val card = stepCard(palette, step.emoji, getString(step.label))
            card.setOnClickListener {
                if (programSteps.size >= Program.MAX_STEPS) {
                    findViewById<TextView>(R.id.program_status).setText(R.string.program_full)
                    return@setOnClickListener
                }
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                programSteps.add(step)
                renderSequence()
            }
            palette.addView(card)
        }
    }

    /** [highlight] = passo sendo transmitido agora (-1 = nenhum). */
    private fun renderSequence(highlight: Int = -1) {
        val strip = findViewById<LinearLayout>(R.id.program_sequence)
        strip.removeAllViews()
        programSteps.forEachIndexed { index, step ->
            val label = getString(step.label)
            val chip = layoutInflater.inflate(R.layout.program_chip, strip, false)
            chip.findViewById<TextView>(R.id.step_number).text = (index + 1).toString()
            chip.findViewById<TextView>(R.id.step_emoji).text = step.emoji
            chip.findViewById<TextView>(R.id.step_label).text = label
            chip.contentDescription = getString(R.string.program_step_desc, index + 1, label)
            chip.isSelected = index == highlight
            strip.addView(chip)
        }
        // Mantém à vista o último passo adicionado ou o que está sendo enviado.
        val scroll = findViewById<HorizontalScrollView>(R.id.program_strip)
        val target = if (highlight >= 0) highlight else programSteps.lastIndex
        scroll.post { strip.getChildAt(target)?.let { scroll.smoothScrollTo(maxOf(0, it.left - 40), 0) } }
        findViewById<View>(R.id.program_empty).visibility = if (programSteps.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.program_count).text =
            getString(R.string.program_sequence, programSteps.size, Program.MAX_STEPS)
        findViewById<View>(R.id.program_run).isEnabled = programSteps.isNotEmpty() && !link.isRunningProgram
    }

    private fun stepCard(parent: GridLayout, emoji: String, label: String): View {
        val card = layoutInflater.inflate(R.layout.program_step, parent, false)
        card.findViewById<TextView>(R.id.step_emoji).text = emoji
        card.findViewById<TextView>(R.id.step_label).text = label
        card.contentDescription = label
        return card
    }

    private fun runProgram() {
        val status = findViewById<TextView>(R.id.program_status)
        val family = currentFamily
        if (!link.isConnected || family == null) {
            status.setText(R.string.program_need_connection)
            return
        }
        stopTilt()
        val plan = Program.transmission(family, programSteps.map { it.code })
        findViewById<View>(R.id.program_run).isEnabled = false
        link.runProgram(
            plan,
            onProgress = { sent ->
                status.text = getString(R.string.program_sending, sent, plan.size)
                // O plano é: início, reset, passos..., executar, parar. Destaca o passo em envio.
                renderSequence(highlight = sent - Program.STEPS_OFFSET - 1)
            },
            onDone = {
                status.setText(R.string.program_sent)
                renderSequence()
            },
        )
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        // Reconecta sozinho se já pareou antes e as permissões continuam concedidas.
        if (rememberedFamily() != null && hasPermissions(requiredPermissions()) && link.isBluetoothOn) {
            connect()
        }
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        stopTilt()
        unregisterReceiver(bluetoothReceiver)
        // Para o robô, desliga o microfone e libera o Bluetooth quando o app sai da tela.
        stopVoice()
        link.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.release()
    }

    private fun toggleVoice() {
        if (voice.isListening) {
            stopVoice()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), VOICE_PERMISSION_REQUEST)
            return
        }
        showVoiceBubble(getString(R.string.voice_loading))
        voiceButton.isEnabled = false
        voiceWanted = true
        voice.load()
    }

    private fun stopVoice() {
        voiceWanted = false
        voice.stop()
        voiceButton.isEnabled = true
        voiceButton.isActivated = false
        voiceButton.contentDescription = getString(R.string.voice_start)
        micPulse?.cancel()
        hideVoiceBubbleLater()
        micHalo.alpha = 0f
    }

    override fun onVoiceReady() {
        // O modelo carrega em segundo plano: se o usuário saiu da tela ou desistiu nesse meio-tempo,
        // o microfone não pode ser aberto.
        if (!voiceWanted || !resumed) {
            voiceButton.isEnabled = true
            return
        }
        voice.start()
        voiceButton.isEnabled = true
        if (voice.isListening) {
            voiceButton.isActivated = true
            voiceButton.contentDescription = getString(R.string.voice_stop)
            showVoiceBubble(getString(R.string.voice_listening))
            startMicPulse()
        }
    }

    /** Halo laranja que pulsa em volta do microfone enquanto ele escuta. */
    private fun startMicPulse() {
        micPulse?.cancel()
        micPulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val progress = it.animatedValue as Float
                micHalo.scaleX = 1f + progress * 0.35f
                micHalo.scaleY = 1f + progress * 0.35f
                micHalo.alpha = 0.5f * (1f - progress)
            }
            start()
        }
    }

    override fun onVoiceHeard(text: String, command: Int?) {
        if (command == null) {
            showVoiceBubble(getString(R.string.voice_heard_unknown, text))
            return
        }
        if (link.isRunningProgram) {
            showVoiceBubble(getString(R.string.voice_wait_program))
            return
        }
        showVoiceBubble(getString(R.string.voice_heard_command, text))
        link.sendOnce(command)
    }

    override fun onVoiceError(message: String) {
        stopVoice()
        showVoiceBubble(message)
        hideVoiceBubbleLater()
    }

    private val hideVoiceBubble = Runnable { if (!voice.isListening) voiceHeard.visibility = View.GONE }

    private fun showVoiceBubble(text: String) {
        voiceHeard.removeCallbacks(hideVoiceBubble)
        voiceHeard.text = text
        voiceHeard.visibility = View.VISIBLE
    }

    private fun hideVoiceBubbleLater() {
        voiceHeard.removeCallbacks(hideVoiceBubble)
        voiceHeard.postDelayed(hideVoiceBubble, 3000)
    }

    /**
     * Gravidade do sensor TYPE_GRAVITY quando existe; senão o acelerômetro com filtro passa-baixa,
     * para tremidas da mão não virarem comandos.
     */
    private val tiltListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!tiltActive) return
            if (event.sensor.type == Sensor.TYPE_GRAVITY) {
                event.values.copyInto(filteredGravity, endIndex = 3)
            } else if (tiltNeedsCalibration) {
                // Primeira leitura semeia o filtro; sem isso a calibração sairia de um valor pela metade.
                event.values.copyInto(filteredGravity, endIndex = 3)
            } else {
                for (i in 0 until 3) filteredGravity[i] = 0.85f * filteredGravity[i] + 0.15f * event.values[i]
            }
            if (tiltNeedsCalibration) {
                tilt.calibrate(filteredGravity)
                tiltNeedsCalibration = false
                return
            }
            val command = tilt.update(filteredGravity)
            if (command == tiltCommand) return
            dpadIds[tiltCommand]?.let { findViewById<View>(it).isPressed = false }
            dpadIds[command]?.let { findViewById<View>(it).isPressed = true }
            tiltCommand = command
            link.send(command)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private fun startTilt() {
        if (!link.isConnected) {
            hintConnectFirst()
            return
        }
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            statusText.setText(R.string.tilt_unavailable)
            return
        }
        tiltActive = true
        tiltNeedsCalibration = true
        tiltCommand = Commands.STOP
        sensorManager.registerListener(tiltListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        val chip = findViewById<TextView>(R.id.tilt_toggle)
        chip.setText(R.string.tilt_on)
        chip.setBackgroundResource(R.drawable.bg_chip_active)
        chip.setTextColor(getColor(R.color.on_primary))
        findViewById<TextView>(R.id.move_hint).setText(R.string.tilt_hint)
    }

    private fun stopTilt() {
        if (!tiltActive) return
        tiltActive = false
        sensorManager.unregisterListener(tiltListener)
        dpadIds[tiltCommand]?.let { findViewById<View>(it).isPressed = false }
        tiltCommand = Commands.STOP
        link.send(Commands.STOP)
        val chip = findViewById<TextView>(R.id.tilt_toggle)
        chip.setText(R.string.tilt_off)
        chip.setBackgroundResource(R.drawable.bg_chip)
        chip.setTextColor(getColor(R.color.text_primary))
        findViewById<TextView>(R.id.move_hint).setText(R.string.move_hint)
    }

    /** Como no app original: segurar envia o comando, soltar envia "parar". */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindHoldButton(id: Int, command: Int) = bindHoldButton(findViewById<View>(id), command)

    @SuppressLint("ClickableViewAccessibility")
    private fun bindHoldButton(view: View, command: Int) {
        view.setOnTouchListener { v, event ->
            // Durante a transmissão de um programa os botões comuns não podem interferir.
            if (link.isRunningProgram) return@setOnTouchListener true
            // No modo inclinar as setas só mostram a direção; tocar nelas brigaria com o sensor.
            if (tiltActive && v.id in dpadIds.values) return@setOnTouchListener true
            // Desconectado: avisa em vez de vibrar como se tivesse funcionado.
            if (!link.isConnected) {
                if (event.action == MotionEvent.ACTION_UP) hintConnectFirst()
                return@setOnTouchListener true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    link.send(command)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    link.send(Commands.STOP)
                }
            }
            true
        }
    }

    /** Coloca as ações do modelo no início da grade de truques, antes dos truques comuns. */
    private fun buildPanel(family: RobotFamily) {
        findViewById<View>(R.id.tricks_hint).visibility = View.GONE
        modelCards.forEach { tricksGrid.removeView(it) }
        modelCards.clear()
        Commands.panelActions(family).forEachIndexed { index, action ->
            val card = layoutInflater.inflate(R.layout.panel_button, tricksGrid, false)
            card.findViewById<TextView>(R.id.panel_emoji).text = action.emoji
            card.findViewById<TextView>(R.id.panel_label).setText(action.label)
            card.contentDescription = getString(action.label)
            bindHoldButton(card, action.code)
            tricksGrid.addView(card, index)
            modelCards.add(card)
        }

        val diagonals = if (Commands.diagonalsSafe(family)) View.VISIBLE else View.INVISIBLE
        listOf(R.id.btn_forward_left, R.id.btn_forward_right, R.id.btn_backward_left, R.id.btn_backward_right)
            .forEach { findViewById<View>(it).visibility = diagonals }

        val clawCard = findViewById<View>(R.id.btn_claw)
        val clawCode = Commands.claw(family)
        clawCard.visibility = if (clawCode != null) View.VISIBLE else View.GONE
        if (clawCode != null) bindHoldButton(clawCard, clawCode)
    }

    private fun hintConnectFirst() {
        statusText.setText(R.string.connect_first)
        connectButton.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).withEndAction {
            connectButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
    }

    private fun selectedFamily(): RobotFamily? =
        when (findViewById<RadioGroup>(R.id.family).checkedRadioButtonId) {
            R.id.family_a -> RobotFamily.A
            R.id.family_b -> RobotFamily.B
            else -> null
        }

    private fun rememberedFamily(): RobotFamily? = RobotFamily.fromName(prefs.getString(KEY_FAMILY, null))

    private fun restoreFamilySelection() {
        val id = when (RobotFamily.fromName(prefs.getString(KEY_MANUAL_FAMILY, null))) {
            RobotFamily.A -> R.id.family_a
            RobotFamily.B -> R.id.family_b
            null -> R.id.family_auto
        }
        findViewById<RadioGroup>(R.id.family).check(id)
    }

    private fun connect() {
        if (!link.isBluetoothOn) {
            val canAskToEnable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (canAskToEnable) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH_REQUEST)
            } else {
                onStatus(getString(R.string.bluetooth_off))
            }
            return
        }
        val selected = selectedFamily()
        prefs.edit().putString(KEY_MANUAL_FAMILY, selected?.name).apply()
        link.connect(familyOrder(selected, rememberedFamily()))
    }

    @Deprecated("Activity sem AndroidX; a API antiga é suficiente aqui")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ENABLE_BLUETOOTH_REQUEST) return
        if (resultCode == RESULT_OK) connect() else onStatus(getString(R.string.bluetooth_off))
    }

    private fun hasPermissions(permissions: Array<String>) =
        permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            // Até o Android 11 o sistema exige localização para procurar dispositivos BLE.
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun requestPermissionsThenConnect() {
        val missing = requiredPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            connect()
        } else {
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == VOICE_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                toggleVoice()
            } else {
                showVoiceBubble(getString(R.string.voice_permission_denied))
                hideVoiceBubbleLater()
            }
            return
        }
        if (requestCode != PERMISSION_REQUEST) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            connect()
        } else {
            onStatus(getString(R.string.permission_denied))
        }
    }

    /** ID aleatório deste celular (3 bytes de 1 a 254), igual ao app original. Fica só no aparelho. */
    private fun loadOrCreateAppId(): ByteArray {
        if (!prefs.contains("app_id_0")) {
            prefs.edit()
                .putInt("app_id_0", Random.nextInt(1, 255))
                .putInt("app_id_1", Random.nextInt(1, 255))
                .putInt("app_id_2", Random.nextInt(1, 255))
                .apply()
        }
        return ByteArray(3) { prefs.getInt("app_id_$it", 1).toByte() }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        findViewById<View>(R.id.controls).alpha = if (enabled) 1f else 0.4f
        connectButton.visibility = if (enabled) View.GONE else View.VISIBLE
        connectButton.setText(if (enabled) R.string.disconnect else R.string.connect)
        // Conectado: botão vira secundário (cinza) para o foco ir para os controles.
        connectButton.isActivated = enabled
        connectButton.setTextColor(getColor(if (enabled) R.color.text_primary else R.color.on_primary))
        connectButton.compoundDrawableTintList =
            ColorStateList.valueOf(getColor(if (enabled) R.color.text_primary else R.color.on_primary))
        // Tela acesa enquanto controla o robô; libera quando desconecta.
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private enum class Pill(val color: Int, val label: Int) {
        OFF(R.color.status_off, R.string.pill_off),
        SEARCHING(R.color.status_wait, R.string.pill_searching),
        ON(R.color.status_ok, R.string.pill_on),
    }

    private fun setPill(pill: Pill) {
        findViewById<View>(R.id.status_dot).backgroundTintList = ColorStateList.valueOf(getColor(pill.color))
        findViewById<TextView>(R.id.status_pill_text).setText(pill.label)
    }

    override fun onStatus(message: String) {
        statusText.text = message
        if (!link.isConnected) setPill(Pill.OFF)
    }

    override fun onSearching(family: RobotFamily) {
        statusText.text = getString(R.string.searching, getString(family.label))
        setPill(Pill.SEARCHING)
    }

    override fun onConnectFailed() {
        if (connectionTips.recordFailure()) {
            findViewById<View>(R.id.restart_tip).visibility = View.VISIBLE
        }
    }

    override fun onConnected(family: RobotFamily) {
        connectionTips.recordSuccess()
        findViewById<View>(R.id.restart_tip).visibility = View.GONE
        prefs.edit().putString(KEY_FAMILY, family.name).apply()
        if (currentFamily != family) buildPalette(family)
        currentFamily = family
        statusText.text = getString(R.string.connected, getString(family.label))
        setPill(Pill.ON)
        buildPanel(family)
        setControlsEnabled(true)
    }

    override fun onDisconnected() {
        stopTilt()
        statusText.setText(R.string.disconnected)
        // Se caiu no meio de um programa, não deixa "Enviando passo…" congelado na tela.
        findViewById<TextView>(R.id.program_status).text = ""
        setPill(Pill.OFF)
        renderSequence()
        setControlsEnabled(false)
    }

    companion object {
        private const val PERMISSION_REQUEST = 1
        private const val VOICE_PERMISSION_REQUEST = 2
        private const val ENABLE_BLUETOOTH_REQUEST = 3
        private const val KEY_FAMILY = "last_family"
        private const val KEY_MANUAL_FAMILY = "manual_family"
    }
}

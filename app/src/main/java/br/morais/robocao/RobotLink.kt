package br.morais.robocao

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Conversa com o robô por anúncios BLE. Tudo fica local: não existe rede neste app.
 *
 * As permissões de Bluetooth são verificadas na MainActivity antes de chamar qualquer método daqui.
 */
@SuppressLint("MissingPermission")
class RobotLink(private val context: Context, private val appId: ByteArray, private val listener: Listener) {

    interface Listener {
        fun onStatus(message: String)
        fun onSearching(family: RobotFamily)
        fun onConnected(family: RobotFamily)
        fun onDisconnected()
        fun onConnectFailed()
    }

    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())

    private var protocol: RobotProtocol? = null
    private var pendingFamilies = ArrayDeque<RobotFamily>()
    private var currentCommand = Commands.STOP
    private var connected = false

    val isConnected get() = connected

    val isBluetoothOn get() = adapter?.isEnabled == true

    fun connect(families: List<RobotFamily>) {
        disconnect()
        if (adapter == null || !adapter.isEnabled) {
            listener.onStatus(context.getString(R.string.bluetooth_off))
            return
        }
        if (adapter.bluetoothLeAdvertiser == null) {
            listener.onStatus(context.getString(R.string.ble_advertise_unsupported))
            return
        }
        pendingFamilies = ArrayDeque(families)
        tryNextFamily()
    }

    fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        stopScan()
        stopAdvertising()
        protocol = null
        currentCommand = Commands.STOP
        isRunningProgram = false
        if (connected) {
            connected = false
            listener.onDisconnected()
        }
    }

    /** Define o comando atual; ele é reenviado a cada 300 ms até mudar, como no app original. */
    fun send(command: Int) = send(command, forced = false)

    private fun send(command: Int, forced: Boolean) {
        if (!connected) return
        handler.removeCallbacks(stopAfterPulse)
        currentCommand = command
        handler.removeCallbacks(resendLoop)
        advertise(protocol!!.commandPacket(command, forced))
        handler.postDelayed(resendLoop, RESEND_INTERVAL_MS)
    }

    /** True enquanto um programa é transmitido; os outros comandos ficam bloqueados. */
    var isRunningProgram = false
        private set

    /** Transmite a sequência do modo programação, um pacote de cada vez no tempo de cada um. */
    fun runProgram(plan: List<Transmission>, onProgress: (sent: Int) -> Unit, onDone: () -> Unit) {
        if (!connected || isRunningProgram) return
        isRunningProgram = true
        fun step(index: Int) {
            if (!connected) {
                isRunningProgram = false
                return
            }
            if (index == plan.size) {
                isRunningProgram = false
                onDone()
                return
            }
            val item = plan[index]
            send(item.code, item.forced)
            onProgress(index + 1)
            handler.postDelayed({ step(index + 1) }, item.holdMs)
        }
        step(0)
    }

    /**
     * Envia o comando por um instante e volta para "parar", como o comando de voz do app original:
     * o robô dispara a ação quando o contador muda, não precisa segurar.
     */
    fun sendOnce(command: Int) {
        send(command)
        handler.postDelayed(stopAfterPulse, PULSE_MS)
    }

    private val stopAfterPulse = Runnable { send(Commands.STOP) }

    private val resendLoop = object : Runnable {
        override fun run() {
            val current = protocol ?: return
            advertise(current.commandPacket(currentCommand))
            handler.postDelayed(this, RESEND_INTERVAL_MS)
        }
    }

    private fun tryNextFamily() {
        val family = pendingFamilies.removeFirstOrNull()
        if (family == null) {
            stopScan()
            stopAdvertising()
            listener.onStatus(context.getString(R.string.robot_no_reply))
            listener.onConnectFailed()
            return
        }
        val newProtocol = RobotProtocol(appId, family)
        protocol = newProtocol
        listener.onSearching(family)
        advertise(newProtocol.matchPacket())
        startScan()
        handler.postDelayed({
            stopScan()
            tryNextFamily()
        }, PAIRING_TIMEOUT_MS)
    }

    private fun onRobotFound(robotId: ByteArray) {
        val current = protocol ?: return
        handler.removeCallbacksAndMessages(null)
        stopScan()
        current.robotId = robotId
        connected = true
        listener.onConnected(current.family)
        send(Commands.STOP)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bytes = result.scanRecord?.bytes ?: return
            val robotId = protocol?.parseReply(bytes) ?: return
            handler.post { if (!connected) onRobotFound(robotId) }
        }

        override fun onScanFailed(errorCode: Int) {
            listener.onStatus(context.getString(R.string.scan_failed, errorCode))
        }
    }

    private fun startScan() {
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        adapter?.bluetoothLeScanner?.startScan(null, settings, scanCallback)
    }

    private fun stopScan() {
        if (adapter?.isEnabled == true) adapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            // Reiniciar o anúncio a cada 300 ms às vezes colide com o anterior; isso é inofensivo.
            if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) return
            listener.onStatus(context.getString(R.string.advertise_failed, errorCode))
        }
    }

    private fun advertise(packet: ByteArray) {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        val data = AdvertiseData.Builder()
            .addManufacturerData(RobotProtocol.MANUFACTURER_ID, RobotProtocol.advertisePayload(packet))
            .build()
        advertiser.stopAdvertising(advertiseCallback)
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertising() {
        if (adapter?.isEnabled == true) adapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
    }

    companion object {
        private const val RESEND_INTERVAL_MS = 300L
        private const val PAIRING_TIMEOUT_MS = 6_000L
        private const val PULSE_MS = 600L
    }
}

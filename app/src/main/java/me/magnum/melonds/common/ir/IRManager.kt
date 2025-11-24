package me.magnum.melonds.common.ir

import android.content.Context
import android.util.Log
import me.magnum.melonds.MelonEmulator

enum class IRTransportType {
    NONE,
    USB_SERIAL,
    TCP,
    DIRECT_STORAGE
}

/**
 * No-operation IR transport implementation
 * Used when IR is disabled or no transport is available
 */
class NoOpenIRTransport : IRTransport {
    companion object {
        private const val TAG = "NoOpenIRTransport"
    }

    override fun open(): Boolean {
        Log.d(TAG, "NoOpen open() called - IR is disabled")
        return false
    }

    override fun close() {
        Log.d(TAG, "NoOpen close() called")
    }

    override fun write(data: ByteArray, length: Int): Int {
        return 0
    }

    override fun read(buffer: ByteArray, maxLength: Int): Int {
        return 0
    }

    override fun isOpen(): Boolean {
        return false
    }

    override fun isAvailable(): Boolean {
        return false
    }

    override fun isDataAvailable(): Boolean {
        return false
    }

    override fun dispose() {
        Log.d(TAG, "NoOpen dispose() called")
    }
}

/**
 * Manager class for IR (Infrared) communication
 * Bridges between native C++ code and Android IR transports
 * Handles transport selection based on user preferences
 */
class IRManager(private val context: Context) {
    companion object {
        private const val TAG = "IRManager"

        init {
            System.loadLibrary("melonDS-android-frontend")
        }
    }

    interface TransportStatusListener {
        fun onTransportChanged(isAvailable: Boolean, transportType: String)
    }

    private var currentTransport: IRTransport = NoOpenIRTransport()
    private val usbSerialManager = UsbSerialManager(context)
    private var statusListener: TransportStatusListener? = null

    init {
        // Set up listener for when USB permission is granted
        usbSerialManager.setPermissionGrantListener {
            updateTransport()
        }

        updateTransport() // TODO: Might need to optimize?
        Log.d(TAG, "IRManager created with transport: ${getCurrentTransportType()}")
    }

    fun setStatusListener(listener: TransportStatusListener?) {
        statusListener = listener
    }

    /**
     * Updates the current transport based on user preference and availability
     * Should be called when USB state changes or preferences change
     */
    fun updateTransport() {
        val previousTransport = currentTransport
        val previousAvailable = currentTransport.isAvailable()

        val preferences = context.getSharedPreferences("ir_settings", Context.MODE_PRIVATE)
        val selectedType = try {
            IRTransportType.valueOf(
                preferences.getString("ir_transport_type", IRTransportType.NONE.name) ?: IRTransportType.NONE.name
            )
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid transport type in preferences, defaulting to NONE")
            IRTransportType.NONE
        }

        currentTransport = when (selectedType) {
            IRTransportType.NONE -> {
                Log.d(TAG, "Transport set to NoOpen (user selected NONE)")
                NoOpenIRTransport()
            }
            IRTransportType.USB_SERIAL -> {
                if (usbSerialManager.isAvailable()) {
                    Log.d(TAG, "USB Serial transport available")
                    usbSerialManager
                } else {
                    Log.d(TAG, "USB Serial selected but not available, using NoOpen")
                    NoOpenIRTransport()
                }
            }
            IRTransportType.TCP -> {
                Log.d(TAG, "TCP transport not yet implemented, using NoOpen")
                NoOpenIRTransport()  // TODO: implement
            }
            IRTransportType.DIRECT_STORAGE -> {
                Log.d(TAG, "Direct Storage transport not yet implemented, using NoOpen")
                NoOpenIRTransport()  // TODO: implement
            }
        }

        MelonEmulator.setIRMode(selectedType.ordinal)

        val currentAvailable = currentTransport.isAvailable()

        // Handle transport changes
        if (previousTransport != currentTransport) {
            Log.d(TAG, "Transport changed: ${previousTransport::class.simpleName} -> ${currentTransport::class.simpleName}")
            if (previousTransport.isOpen()) {
                previousTransport.close()
            }
            statusListener?.onTransportChanged(currentAvailable, currentTransport::class.simpleName ?: "Unknown")
        } else if (previousAvailable != currentAvailable) {
            statusListener?.onTransportChanged(currentAvailable, currentTransport::class.simpleName ?: "Unknown")
        }
    }

    /**
     * Request USB device permission (triggers system dialog)
     * Returns true if a device was found
     */
    fun requestUsbDevice(): Boolean {
        Log.d(TAG, "Requesting USB device")
        return usbSerialManager.requestDevice()
    }

    /**
     * Check if USB transport is available
     */
    fun isUsbAvailable(): Boolean {
        return usbSerialManager.isAvailable()
    }

    /**
     * Get the current transport type for debugging/logging
     */
    fun getCurrentTransportType(): String {
        return currentTransport::class.simpleName ?: "Unknown"
    }

    /**
     * Cleanup the IR manager
     * Call this when stopping emulation
     */
    fun cleanup() {
        closeSerial()
        currentTransport.dispose()
        usbSerialManager.dispose()
        Log.d(TAG, "IRManager cleaned up")
    }

    // ==================== JNI Methods (called from native code) ====================

    /**
     * Open the serial port
     * Called from native code via JNI
     */
    fun openSerial(): Boolean {
        updateTransport()
        Log.d(TAG, "openSerial() called from native (transport: ${getCurrentTransportType()})")
        return currentTransport.open()
    }

    /**
     * Close the serial port
     * Called from native code via JNI
     */
    fun closeSerial() {
        Log.d(TAG, "closeSerial() called from native")
        currentTransport.close()
    }

    /**
     * Write data to serial port
     * Called from native code via JNI
     * Returns the number of bytes written
     */
    fun writeSerial(data: ByteArray, length: Int): Int {
        return currentTransport.write(data, length)
    }

    /**
     * Read data from serial port
     * Called from native code via JNI
     * Returns the number of bytes read
     */
    fun readSerial(buffer: ByteArray, maxLength: Int): Int {
        return currentTransport.read(buffer, maxLength)
    }

    /**
     * Check if serial port is open
     * Called from native code via JNI
     */
    fun isSerialOpen(): Boolean {
        return currentTransport.isOpen()
    }

    /**
     * Check if data is available to read
     * Called from native code via JNI
     */
    fun hasDataAvailable(): Boolean {
        return currentTransport.isDataAvailable()
    }

    // ==================== Debug Methods ====================

    /**
     * Log detailed USB device information for debugging
     */
    fun logUsbDiagnostics() {
        Log.d(TAG, "Logging USB diagnostics")
        usbSerialManager.logDiagnostics()
    }
}

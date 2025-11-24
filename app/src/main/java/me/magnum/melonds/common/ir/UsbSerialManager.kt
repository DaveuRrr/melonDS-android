package me.magnum.melonds.common.ir

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import me.magnum.melonds.common.ir.IRTransport
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Manager class for USB Serial communication on Android
 * Implements IRTransport interface for use with IRManager
 * Uses usb-serial-for-android library
 */
class UsbSerialManager(private val context: Context) : IRTransport {
    companion object {
        private const val TAG = "UsbSerialManager"
        private const val ACTION_USB_PERMISSION = "me.magnum.melonds.USB_PERMISSION"

        // Serial port settings (matching desktop: 115200 8N1)
        private const val BAUD_RATE = 115200
        private const val DATA_BITS = 8
        private const val STOP_BITS = UsbSerialPort.STOPBITS_1
        private const val PARITY = UsbSerialPort.PARITY_NONE

        // Timeouts - original values
        private const val WRITE_TIMEOUT_MS = 1000
        private const val READ_TIMEOUT_MS = 100

        private const val ENABLE_DTR = true
        private const val ENABLE_RTS = false
        private const val VERBOSE_LOGGING = false

        // USB descriptor constants
        private const val USB_DIR_IN = 0x80
        private const val USB_REQ_GET_DESCRIPTOR = 0x06
        private const val USB_DT_STRING = 0x03
        private const val USB_LANG_EN_US = 0x0409

        /**
         * Create a custom USB serial prober that includes Raspberry Pi Pico/Pico 2
         * and other CDC ACM devices that might not be in the default list
         */
        private fun createCustomProber(): UsbSerialProber {
            val customTable = ProbeTable()

            // Raspberry Pi Foundation VID: 0x2E8A
            // Add Raspberry Pi Pico (RP2040)
            customTable.addProduct(0x2E8A, 0x0005, CdcAcmSerialDriver::class.java) // RP2040 CDC
            customTable.addProduct(0x2E8A, 0x000A, CdcAcmSerialDriver::class.java) // RP2040 CDC (alt)

            // Add Raspberry Pi Pico 2 (RP2350)
            customTable.addProduct(0x2E8A, 0x000F, CdcAcmSerialDriver::class.java) // RP2350 CDC
            customTable.addProduct(0x2E8A, 0x0003, CdcAcmSerialDriver::class.java) // RP2350 MicroPython

            // Create prober with both default and custom tables
            return UsbSerialProber(customTable)
        }
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbSerialPort: UsbSerialPort? = null
    private val isOpen = AtomicBoolean(false)

    // Async I/O to prevent blocking the emulator thread
    private val readBuffer = LinkedBlockingQueue<Byte>(1024)  // Buffer for received data
    private var readerThread: Thread? = null
    private val shouldStopReader = AtomicBoolean(false)
    private var permissionGrantListener: (() -> Unit)? = null

    fun setPermissionGrantListener(listener: () -> Unit) {
        permissionGrantListener = listener
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(TAG, "USB permission granted for device: ${it.deviceName}")
                            usbDevice = it
                            permissionGrantListener?.invoke()
                        } ?: run {
                            Log.e(TAG, "Permission granted but device is null!")
                        }
                    } else {
                        Log.w(TAG, "USB permission denied for device: ${device?.deviceName}")
                        usbDevice = null
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbPermissionReceiver, filter)
        }
    }

    /**
     * Read a USB string descriptor from a device
     * Returns the string or null if reading failed
     */
    private fun readStringDescriptor(device: UsbDevice, index: Int): String? {
        if (index == 0) return null

        val connection = usbManager.openDevice(device) ?: return null
        return try {
            val buffer = ByteArray(255)

            // Send GET_DESCRIPTOR request for string
            val len = connection.controlTransfer(
                USB_DIR_IN,
                USB_REQ_GET_DESCRIPTOR,
                (USB_DT_STRING shl 8) or index,
                USB_LANG_EN_US,
                buffer,
                buffer.size,
                1000
            )

            if (len > 2) {
                // Skip first 2 bytes (length and type), decode UTF-16LE
                String(buffer, 2, len - 2, Charsets.UTF_16LE)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read string descriptor $index", e)
            null
        } finally {
            connection.close()
        }
    }

    /**
     * Get the user-selected USB port from SharedPreferences for the current device
     * Returns null if no preference is set or if the device doesn't match
     */
    private fun getUserSelectedPort(device: UsbDevice): Int? {
        return try {
            val prefs = context.getSharedPreferences("usb_settings", Context.MODE_PRIVATE)
            val selectedDeviceKey = prefs.getString("selected_usb_device_key", null)

            if (selectedDeviceKey != null) {
                // Device key format is: "${device.deviceName}:${portIndex}"
                val deviceName = device.deviceName
                if (selectedDeviceKey.startsWith("$deviceName:")) {
                    // Extract the port index from the device key
                    val portIndex = selectedDeviceKey.substringAfterLast(":").toIntOrNull()
                    if (portIndex != null) {
                        Log.d(TAG, "Found user-selected port $portIndex for device $deviceName")
                        return portIndex
                    }
                } else {
                    Log.d(TAG, "Selected device key '$selectedDeviceKey' doesn't match current device '$deviceName'")
                }
            }
            null // No matching preference found
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read USB port preference", e)
            null
        }
    }

    /**
     * Find the IR serial port in a multi-interface USB device
     * Returns the port index (0-based) or null if not found
     */
    private fun findIRPort(driver: UsbSerialDriver): Int? {
        val device = driver.device

        // Try to find IR port by reading interface string descriptors
        for (portIndex in driver.ports.indices) {
            for (interfaceIndex in 0 until device.interfaceCount) {
                val iface = device.getInterface(interfaceIndex)
                val interfaceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    iface.name
                } else {
                    null
                }

                if (interfaceName?.contains("IR", ignoreCase = true) == true) {
                    Log.d(TAG, "Found IR port at index $portIndex via interface name: $interfaceName")
                    return portIndex
                }
            }
        }

        // Fallback: try reading common string descriptor indices
        // Typically manufacturer=1, product=2, serial=3, interface strings start at 4
        for (stringIndex in 4..10) {
            val descriptor = readStringDescriptor(device, stringIndex)
            if (descriptor?.contains("IR", ignoreCase = true) == true) {
                Log.d(TAG, "Found IR descriptor at index $stringIndex: '$descriptor'")
                // Heuristic: string indices 4-5 usually correspond to first CDC interface
                // indices 6-7 to second CDC interface, etc.
                // For Picowalker: index 5 = "Picowalker IR" -> port 1 (second CDC)
                val portIndex = (stringIndex - 4) / 2
                if (portIndex < driver.ports.size) {
                    return portIndex
                }
            }
        }

        Log.w(TAG, "Could not identify IR port via descriptors")
        return null
    }

    /**
     * Check if we have a USB device (without requesting permission)
     * Returns true if a USB serial device exists (with or without permission)
     */
    fun hasDevice(): Boolean {
        return try {
            val defaultDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val customDrivers = createCustomProber().findAllDrivers(usbManager)
            val availableDrivers = (defaultDrivers + customDrivers).distinctBy { it.device.deviceName }
            availableDrivers.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking for USB devices", e)
            false
        }
    }

    /**
     * Find and request permission for a USB serial device
     * Returns true if a device was found and permission requested/granted
     */
    fun findAndRequestDevice(): Boolean {
        val defaultDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val customDrivers = createCustomProber().findAllDrivers(usbManager)
        val availableDrivers = (defaultDrivers + customDrivers).distinctBy { it.device.deviceName }

        Log.d(TAG, "Found ${defaultDrivers.size} device(s) via default prober, " +
                "${customDrivers.size} via custom prober, " +
                "${availableDrivers.size} total unique device(s)")

        if (availableDrivers.isEmpty()) {
            Log.w(TAG, "No USB serial devices found")
            return false
        }

        Log.d(TAG, "Found ${availableDrivers.size} USB serial device(s)")

        // Take the first available driver
        val driver = availableDrivers[0]
        val device = driver.device

        Log.d(TAG, "Found device: ${device.deviceName}, " +
                "VID: ${String.format("0x%04X", device.vendorId)}, " +
                "PID: ${String.format("0x%04X", device.productId)}, " +
                "Driver: ${driver.javaClass.simpleName}, " +
                "Ports: ${driver.ports.size}")

        if (!usbManager.hasPermission(device)) {
            Log.d(TAG, "***** NO PERMISSION - Requesting permission for device: ${device.deviceName} *****")
            Log.d(TAG, "A system dialog should appear asking for USB permission")

            // Create broadcast intent for permission callback
            val intent = Intent(ACTION_USB_PERMISSION)
            intent.setPackage(context.packageName)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                0
            }

            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
            return false // Permission requested but not yet granted
        } else {
            usbDevice = device
            Log.d(TAG, "Already have permission for device: ${device.deviceName}")
            return true
        }
    }

    /**
     * Open the serial port connection
     * Returns true if successfully opened
     */
    override fun open(): Boolean {
        if (isOpen.get()) {
            Log.d(TAG, "Serial port already open")
            return true
        }

        if (usbDevice == null) {
            if (!findAndRequestDevice()) {
                Log.e(TAG, "No USB device available or permission not granted")
                return false
            }
        }

        val device = usbDevice ?: run {
            Log.e(TAG, "USB device is null")
            return false
        }

        if (!usbManager.hasPermission(device)) {
            Log.e(TAG, "No permission for USB device")
            return false
        }

        try {
            // Find the driver for this device using both probers
            val defaultDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val customDrivers = createCustomProber().findAllDrivers(usbManager)
            val allDrivers = (defaultDrivers + customDrivers).distinctBy { it.device.deviceName }

            val driver = allDrivers.firstOrNull { it.device == device }

            if (driver == null) {
                Log.e(TAG, "No driver found for device")
                return false
            }

            // Priority: 1) User selection, 2) Auto-detect via descriptors, 3) Default to port 0
            val userSelectedPort = getUserSelectedPort(device)
            val portIndex = when {
                userSelectedPort != null -> {
                    Log.d(TAG, "Using user-selected port: $userSelectedPort")
                    userSelectedPort
                }
                else -> {
                    val autoDetectedPort = findIRPort(driver)
                    if (autoDetectedPort != null) {
                        Log.d(TAG, "Using auto-detected IR port: $autoDetectedPort")
                        autoDetectedPort
                    } else {
                        Log.d(TAG, "No user selection or auto-detection, using default port 0")
                        0
                    }
                }
            }

            Log.d(TAG, "Opening port $portIndex of ${driver.ports.size} available ports")

            if (portIndex >= driver.ports.size) {
                Log.e(TAG, "Invalid port index $portIndex (device has ${driver.ports.size} ports)")
                return false
            }

            val connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Failed to open USB device connection")
                return false
            }

            val port = driver.ports[portIndex]
            port.open(connection)
            port.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY)

            // Configure DTR and RTS if enabled
            // DTR = Data Terminal Ready, RTS = Request To Send
            if (ENABLE_DTR || ENABLE_RTS) {
                try {
                    port.dtr = ENABLE_DTR
                    port.rts = ENABLE_RTS
                    Log.d(TAG, "Flow control: DTR=$ENABLE_DTR, RTS=$ENABLE_RTS")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set DTR/RTS (device may not support it)", e)
                }
            } else {
                Log.d(TAG, "Flow control: Using defaults (DTR/RTS not set)")
            }

            usbSerialPort = port
            isOpen.set(true)

            startReaderThread()

            Log.d(TAG, "Serial port opened successfully: ${device.deviceName} port $portIndex (${BAUD_RATE} 8N1)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening serial port", e)
            close()
            return false
        }
    }

    /**
     * Start background thread to continuously read from serial port
     */
    private fun startReaderThread() {
        stopReaderThread()

        shouldStopReader.set(false)
        readBuffer.clear()

        readerThread = thread(name = "USBSerialReader") {
            Log.d(TAG, "Reader thread started")
            val tempBuffer = ByteArray(256)

            while (!shouldStopReader.get() && isOpen.get()) {
                try {
                    val port = usbSerialPort
                    if (port == null) {
                        break
                    }

                    val bytesRead = port.read(tempBuffer, READ_TIMEOUT_MS)
                    if (bytesRead > 0) {
                        // Add bytes to queue immediately
                        for (i in 0 until bytesRead) {
                            readBuffer.offer(tempBuffer[i])
                        }
                        if (VERBOSE_LOGGING) {
                            Log.d(TAG, "Reader thread: read $bytesRead bytes")
                        }
                    }
                } catch (e: Exception) {
                    if (!shouldStopReader.get() && VERBOSE_LOGGING) {
                        Log.e(TAG, "Reader thread error", e)
                    }
                }
            }
            Log.d(TAG, "Reader thread stopped")
        }
    }

    /**
     * Stop background reader thread
     */
    private fun stopReaderThread() {
        shouldStopReader.set(true)
        readerThread?.interrupt()
        readerThread?.join(1000)
        readerThread = null
    }

    /**
     * Close the serial port connection
     */
    override fun close() {
        stopReaderThread()

        try {
            usbSerialPort?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing serial port", e)
        } finally {
            usbSerialPort = null
            isOpen.set(false)
            readBuffer.clear()
            Log.d(TAG, "Serial port closed")
        }
    }

    /**
     * Write data to the serial port
     * Returns the number of bytes written, or -1 on error
     */
    override fun write(data: ByteArray, length: Int): Int {
        if (!isOpen.get()) {
            if (VERBOSE_LOGGING) Log.w(TAG, "Serial write failed: port not open")
            return -1
        }

        return try {
            val port = usbSerialPort ?: return -1
            val dataToWrite = data.copyOfRange(0, length)
            port.write(dataToWrite, WRITE_TIMEOUT_MS)

            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Serial wrote $length bytes: ${dataToWrite.joinToString(" ") { "%02X".format(it) }}")
            }
            length
        } catch (e: Exception) {
            Log.e(TAG, "Serial write error", e)
            -1
        }
    }

    /**
     * Read data from the serial port (non-blocking - reads from queue)
     * Returns the number of bytes read
     */
    override fun read(buffer: ByteArray, maxLength: Int): Int {
        if (!isOpen.get()) {
            return 0
        }

        var bytesRead = 0
        while (bytesRead < maxLength) {
            val byte = readBuffer.poll()
            if (byte == null) {
                break
            }
            buffer[bytesRead++] = byte
        }

        if (bytesRead > 0 && VERBOSE_LOGGING) {
            Log.d(TAG, "Serial read $bytesRead bytes: ${buffer.take(bytesRead).joinToString(" ") { "%02X".format(it) }}")
        }

        return bytesRead
    }

    /**
     * Check if the serial port is open
     */
    override fun isOpen(): Boolean = isOpen.get()

    /**
     * Log detailed information about all connected USB devices
     * Useful for debugging USB descriptor detection
     */
    fun logAllUsbDevices() {
        val devices = usbManager.deviceList.values
        Log.d(TAG, "=== USB Device Scan ===")
        Log.d(TAG, "Found ${devices.size} USB device(s)")

        for (device in devices) {
            Log.d(TAG, "")
            Log.d(TAG, "=== Device: ${device.deviceName} ===")
            Log.d(TAG, "VID: 0x${String.format("%04X", device.vendorId)}")
            Log.d(TAG, "PID: 0x${String.format("%04X", device.productId)}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Only read these if we have permission (they require USB access)
                try {
                    if (usbManager.hasPermission(device)) {
                        Log.d(TAG, "Manufacturer: ${device.manufacturerName}")
                        Log.d(TAG, "Product: ${device.productName}")
                        Log.d(TAG, "Serial: ${device.serialNumber}")
                    } else {
                        Log.d(TAG, "Manufacturer: (no permission)")
                        Log.d(TAG, "Product: (no permission)")
                        Log.d(TAG, "Serial: (no permission - call findAndRequestDevice first)")
                    }
                } catch (e: SecurityException) {
                    Log.d(TAG, "Manufacturer: (permission required)")
                    Log.d(TAG, "Product: (permission required)")
                    Log.d(TAG, "Serial: (permission required)")
                }
            }

            Log.d(TAG, "Interface count: ${device.interfaceCount}")

            // Log all interfaces
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                Log.d(TAG, "  Interface $i:")
                Log.d(TAG, "    Class: 0x${String.format("%02X", iface.interfaceClass)} " +
                        "(${getUsbClassName(iface.interfaceClass)})")
                Log.d(TAG, "    Subclass: 0x${String.format("%02X", iface.interfaceSubclass)}")
                Log.d(TAG, "    Protocol: 0x${String.format("%02X", iface.interfaceProtocol)}")
                Log.d(TAG, "    Endpoints: ${iface.endpointCount}")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Log.d(TAG, "    ID: ${iface.id}")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Log.d(TAG, "    Name: ${iface.name}")
                }
            }

            Log.d(TAG, "  String Descriptors:")
            for (stringIndex in 0..10) {
                val descriptor = readStringDescriptor(device, stringIndex)
                if (descriptor != null) {
                    Log.d(TAG, "    [$stringIndex]: '$descriptor'")
                }
            }

            val defaultDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val customDrivers = createCustomProber().findAllDrivers(usbManager)
            val allDrivers = (defaultDrivers + customDrivers).distinctBy { it.device.deviceName }

            val deviceDrivers = allDrivers.filter { it.device == device }
            Log.d(TAG, "  Serial Drivers: ${deviceDrivers.size} (default: ${defaultDrivers.filter { it.device == device }.size}, custom: ${customDrivers.filter { it.device == device }.size})")
            for (driver in deviceDrivers) {
                Log.d(TAG, "    Driver: ${driver.javaClass.simpleName}")
                Log.d(TAG, "    Ports: ${driver.ports.size}")
            }
        }

        Log.d(TAG, "=== End USB Device Scan ===")
    }

    private fun getUsbClassName(classCode: Int): String {
        return when (classCode) {
            0x00 -> "Per Interface"
            0x02 -> "CDC-Communications"
            0x03 -> "HID"
            0x08 -> "Mass Storage"
            0x09 -> "Hub"
            0x0A -> "CDC-Data"
            0x0E -> "Video"
            0xFF -> "Vendor Specific"
            else -> "Unknown"
        }
    }

    /**
     * Check if USB transport is available (device connected)
     * Does not trigger permission dialogs
     */
    override fun isAvailable(): Boolean {
        return try {
            hasDevice()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking USB availability", e)
            false
        }
    }

    /**
     * Check if data is available to read
     * @return true if there is data waiting in the read buffer
     */
    override fun isDataAvailable(): Boolean {
        return readBuffer.isNotEmpty()
    }

    /**
     * Cleanup and dispose of resources
     */
    override fun dispose() {
        close()
        try {
            context.unregisterReceiver(usbPermissionReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    /**
     * Request USB device permission (shows system dialog)
     * Returns true if device was found
     */
    fun requestDevice(): Boolean {
        Log.d(TAG, "Requesting USB device permission")
        return findAndRequestDevice()
    }

    /**
     * Log USB device diagnostics
     */
    fun logDiagnostics() {
        logAllUsbDevices()
    }
}

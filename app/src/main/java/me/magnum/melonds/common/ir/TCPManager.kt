package me.magnum.melonds.common.ir

import android.content.Context
import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * TCP/IP Manager for IR communication
 * Implements IRTransport interface for use with IRManager
 *
 * Ported from melonDS-IR Qt6 implementation (IR.cpp)
 * Supports both server and client modes
 */
class TCPManager(private val context: Context) : IRTransport {
    companion object {
        private const val TAG = "TCPManager"

        // Default configuration values
        private const val DEFAULT_SERVER_PORT = 8081
        private const val DEFAULT_CLIENT_HOST = "127.0.0.1"
        private const val DEFAULT_CLIENT_PORT = 8081

        // Timeout values
        private const val CONNECTION_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 100
        private const val SOCKET_SO_TIMEOUT_MS = 100

        // Buffer sizes
        private const val READ_BUFFER_SIZE = 1024

        private const val VERBOSE_LOGGING = true
    }

    // TCP socket objects
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    // State tracking
    private val isOpen = AtomicBoolean(false)
    private val shouldStop = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)

    // Async I/O
    private val readBuffer = LinkedBlockingQueue<Byte>(READ_BUFFER_SIZE)
    private var readerThread: Thread? = null
    private var connectionThread: Thread? = null

    // Configuration
    private var isServerMode: Boolean = true
    private var serverPort: Int = DEFAULT_SERVER_PORT
    private var clientHost: String = DEFAULT_CLIENT_HOST
    private var clientPort: Int = DEFAULT_CLIENT_PORT

    init {
        loadConfiguration()
    }

    /**
     * Load TCP configuration from SharedPreferences
     */
    private fun loadConfiguration() {
        val prefs = context.getSharedPreferences("tcp_settings", Context.MODE_PRIVATE)
        isServerMode = prefs.getBoolean("tcp_is_server", true)
        serverPort = prefs.getInt("tcp_server_port", DEFAULT_SERVER_PORT)
        clientHost = prefs.getString("tcp_client_host", DEFAULT_CLIENT_HOST) ?: DEFAULT_CLIENT_HOST
        clientPort = prefs.getInt("tcp_client_port", DEFAULT_CLIENT_PORT)

        Log.d(TAG, "Loaded configuration: mode=${if (isServerMode) "SERVER" else "CLIENT"}, " +
                "serverPort=$serverPort, clientHost=$clientHost, clientPort=$clientPort")
    }

    /**
     * Open TCP connection (server or client based on configuration)
     * For server mode: starts listening and waits for client connection
     * For client mode: connects to specified host
     */
    override fun open(): Boolean {
        Log.d(TAG, "open() called, current state: isOpen=${isOpen.get()}, isConnecting=${isConnecting.get()}")

        if (isOpen.get()) {
            Log.d(TAG, "TCP connection already open")
            return true
        }

        if (!isConnecting.compareAndSet(false, true)) {
            Log.d(TAG, "TCP connection already in progress, waiting...")
            return true
        }

        loadConfiguration()
        shouldStop.set(false)
        readBuffer.clear()

        Log.d(TAG, "Opening TCP in ${if (isServerMode) "SERVER" else "CLIENT"} mode")

        return if (isServerMode) {
            openServerMode()
        } else {
            openClientMode()
        }
    }

    /**
     * Open TCP connection in server mode
     * Listens on configured port and accepts one client connection
     */
    private fun openServerMode(): Boolean {
        try {
            // Create server socket
            serverSocket = ServerSocket()
            serverSocket?.reuseAddress = true
            serverSocket?.bind(InetSocketAddress(serverPort))
            serverSocket?.soTimeout = SOCKET_SO_TIMEOUT_MS

            Log.d(TAG, "TCP server listening on port $serverPort")

            connectionThread = thread(name = "TCPServerAccept") {
                try {
                    while (!shouldStop.get() && serverSocket != null) {
                        try {
                            val socket = serverSocket?.accept()
                            if (socket != null) {
                                clientSocket = socket
                                clientSocket?.soTimeout = SOCKET_SO_TIMEOUT_MS
                                isOpen.set(true)
                                isConnecting.set(false) // Reset connecting flag

                                Log.d(TAG, "Client connected from ${socket.inetAddress.hostAddress}")

                                startReaderThread()
                                break
                            }
                        } catch (e: SocketTimeoutException) {
                            continue // Timeout is expected, continue loop
                        }
                    }
                } catch (e: Exception) {
                    if (!shouldStop.get()) {
                        Log.e(TAG, "Server accept error", e)
                    }
                    isConnecting.set(false) // Reset on error
                }
            }

            return true // Server started successfully
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TCP server", e)
            isConnecting.set(false) // Reset on error
            cleanup()
            return false
        }
    }

    /**
     * Open TCP connection in client mode
     * Connects to configured host and port
     */
    private fun openClientMode(): Boolean {
        connectionThread = thread(name = "TCPClientConnect") {
            try {
                Log.d(TAG, "Attempting to connect to $clientHost:$clientPort")

                val socket = Socket()
                socket.connect(InetSocketAddress(clientHost, clientPort), CONNECTION_TIMEOUT_MS)
                socket.soTimeout = SOCKET_SO_TIMEOUT_MS

                clientSocket = socket
                isOpen.set(true)
                isConnecting.set(false) // Reset connecting flag

                Log.d(TAG, "Connected to server at $clientHost:$clientPort")

                startReaderThread()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to $clientHost:$clientPort", e)
                isConnecting.set(false) // Reset on error
                cleanup()
            }
        }

        return true // Connection attempt started
    }

    /**
     * Start background thread to continuously read from socket
     */
    private fun startReaderThread() {
        stopReaderThread()

        readerThread = thread(name = "TCPReader") {
            Log.d(TAG, "Reader thread started")
            val tempBuffer = ByteArray(256)

            while (!shouldStop.get() && isOpen.get()) {
                try {
                    val socket = clientSocket
                    if (socket == null || !socket.isConnected) {
                        break
                    }

                    val inputStream = socket.getInputStream()
                    if (inputStream.available() > 0) {
                        val bytesRead = inputStream.read(tempBuffer)
                        if (bytesRead > 0) {
                            for (i in 0 until bytesRead) {
                                readBuffer.offer(tempBuffer[i])
                            }
                            if (VERBOSE_LOGGING) {
                                Log.d(TAG, "Reader thread: read $bytesRead bytes")
                            }
                        } else if (bytesRead == -1) {
                            Log.d(TAG, "Connection closed by remote")
                            break
                        }
                    } else {
                        Thread.sleep(10) // No data available, sleep briefly
                    }
                } catch (e: SocketTimeoutException) {
                    continue // Timeout is expected, continue loop
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Reader thread interrupted")
                    break
                } catch (e: Exception) {
                    if (!shouldStop.get()) {
                        Log.e(TAG, "Reader thread error", e)
                    }
                    break
                }
            }

            Log.d(TAG, "Reader thread stopped")
            isOpen.set(false)
        }
    }

    /**
     * Stop background reader thread
     */
    private fun stopReaderThread() {
        readerThread?.interrupt()
        readerThread?.join(1000)
        readerThread = null
    }

    /**
     * Stop connection thread
     */
    private fun stopConnectionThread() {
        connectionThread?.interrupt()
        connectionThread?.join(1000)
        connectionThread = null
    }

    /**
     * Close the TCP connection
     */
    override fun close() {
        shouldStop.set(true)

        stopReaderThread()
        stopConnectionThread()

        cleanup()

        Log.d(TAG, "TCP connection closed")
    }

    /**
     * Cleanup all TCP resources
     */
    private fun cleanup() {
        try {
            clientSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }

        clientSocket = null
        serverSocket = null
        isOpen.set(false)
        readBuffer.clear()
    }

    /**
     * Write data to TCP socket
     * Matches Qt6 IR_TCP_SendPacket() behavior
     *
     * @param data The data to write
     * @param length The number of bytes to write
     * @return The number of bytes written, or -1 on error
     */
    override fun write(data: ByteArray, length: Int): Int {
        if (!isOpen.get()) {
            if (VERBOSE_LOGGING) Log.w(TAG, "TCP write failed: not connected")
            return -1
        }

        return try {
            val socket = clientSocket ?: return -1

            if (!socket.isConnected) {
                if (VERBOSE_LOGGING) Log.w(TAG, "TCP write failed: socket not connected")
                return -1
            }

            val outputStream = socket.getOutputStream()
            outputStream.write(data, 0, length)
            outputStream.flush()

            if (VERBOSE_LOGGING) {
                Log.d(TAG, "TCP wrote $length bytes")
            }

            length
        } catch (e: IOException) {
            Log.e(TAG, "TCP write error", e)
            isOpen.set(false)
            -1
        }
    }

    /**
     * Read data from TCP socket (non-blocking - reads from queue)
     * Matches Qt6 IR_TCP_RecievePacket() behavior
     *
     * @param buffer The buffer to read into
     * @param maxLength The maximum number of bytes to read
     * @return The number of bytes read
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
            Log.d(TAG, "TCP read $bytesRead bytes")
        }

        return bytesRead
    }

    /**
     * Check if TCP connection is open or in the process of opening
     */
    override fun isOpen(): Boolean {
        return (isOpen.get() && clientSocket?.isConnected == true) || isConnecting.get()
    }

    /**
     * Check if TCP transport is available (always true - network is assumed available)
     */
    override fun isAvailable(): Boolean {
        return true
    }

    /**
     * Check if data is available to read
     */
    override fun isDataAvailable(): Boolean {
        return readBuffer.isNotEmpty()
    }

    /**
     * Dispose and cleanup resources
     */
    override fun dispose() {
        close()
        Log.d(TAG, "TCPManager disposed")
    }
}

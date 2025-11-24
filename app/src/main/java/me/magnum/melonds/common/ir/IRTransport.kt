package me.magnum.melonds.common.ir

/**
 * Interface for IR (Infrared) transport mechanisms
 * Similar to DSiCameraSource - allows switching between different transports
 */
interface IRTransport {
    /**
     * Open the IR transport connection
     * @return true if successfully opened
     */
    fun open(): Boolean

    /**
     * Close the IR transport connection
     */
    fun close()

    /**
     * Write data to the IR transport
     * @param data The data to write
     * @param length The number of bytes to write
     * @return The number of bytes written, or -1 on error
     */
    fun write(data: ByteArray, length: Int): Int

    /**
     * Read data from the IR transport
     * @param buffer The buffer to read into
     * @param maxLength The maximum number of bytes to read
     * @return The number of bytes read
     */
    fun read(buffer: ByteArray, maxLength: Int): Int

    /**
     * Check if the transport is currently open
     * @return true if open
     */
    fun isOpen(): Boolean

    /**
     * Check if this transport is currently available
     * @return true if available (e.g., USB device connected)
     */
    fun isAvailable(): Boolean

    /**
     * Check if data is available to read
     * @return true if there is data waiting to be read
     */
    fun isDataAvailable(): Boolean

    /**
     * Dispose/cleanup the transport
     */
    fun dispose()
}

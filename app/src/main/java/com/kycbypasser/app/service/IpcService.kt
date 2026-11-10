package com.kycbypasser.app.service

import android.app.Service
import android.content.Intent
import android.os.*
import java.io.*

/**
 * Socket compat for older SDKs that may not have java.net.LocalSocket etc.
 */
object SocketCompat {
    private val LocalSocketClass by lazy { Class.forName("java.net.LocalSocket") }
    private val LocalServerSocketClass by lazy { Class.forName("java.net.LocalServerSocket") }
    private val LocalSocketAddressClass by lazy { Class.forName("java.net.LocalSocketAddress") }
    private val NamespaceClass by lazy { LocalSocketAddressClass.getDeclaredField("Namespace").get(null) }
    private val Absolute by lazy { NamespaceClass!!.javaClass.getDeclaredField("ABSOLUTE").get(null) }

    fun newLocalSocket(): Any = LocalSocketClass.getDeclaredConstructor().newInstance()
    fun newLocalServerSocket(path: String): Any = LocalServerSocketClass.getDeclaredConstructor(String::class.java).newInstance(path)
    fun newLocalSocketAddress(path: String): Any = LocalSocketAddressClass.getDeclaredConstructor(String::class.java, Any::class.java).newInstance(path, Absolute)
}

/**
 * IPC service that communicates with the Zygisk companion process.
 * Uses Unix domain sockets for fast, local communication.
 *
 * The Zygisk module's companion handler receives commands from this service
 * and updates the module state (active/inactive, media paths, etc.)
 */
class IpcService : Service() {

    companion object {
        const val SOCKET_PATH = "/dev/socket/kyc_bypasser"
        const val CMD_SET_ACTIVE = "ACTIVE"
        const val CMD_SET_INACTIVE = "INACTIVE"
        const val CMD_SET_IMAGE = "IMAGE"
        const val CMD_SET_VIDEO = "VIDEO"
        const val CMD_SET_TARGETS = "TARGETS"
        const val CMD_STATUS = "STATUS"

        // Global reference to socket server thread
        var socketThread: Thread? = null
    }

    @Suppress("UNCHECKED_CAST")
    private var serverSocket: Any? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): IpcService = this@IpcService
    }

    override fun onCreate() {
        super.onCreate()
        startSocketServer()
    }

    override fun onDestroy() {
        try {
            serverSocket?.javaClass?.getMethod("close")?.invoke(serverSocket)
        } catch (e: Exception) {
            android.util.Log.e("IpcService", "Error closing socket: ${e.message}")
        }
        socketThread?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Start a Unix domain socket server to receive commands from the Zygisk module.
     * The module connects to this socket and sends status updates.
     */
    private fun startSocketServer() {
        socketThread = Thread {
            try {
                // Remove old socket if exists
                val oldSocket = File(SOCKET_PATH)
                if (oldSocket.exists()) oldSocket.delete()

                @Suppress("UNCHECKED_CAST")
                serverSocket = SocketCompat.newLocalServerSocket(SOCKET_PATH)

                while (!Thread.interrupted()) {
                    val client = serverSocket!!.javaClass.getMethod("accept").invoke(serverSocket)
                    if (client != null) {
                        handleClient(client)
                    }
                }
            } catch (e: IOException) {
                android.util.Log.e("IpcService", "Socket error: ${e.message}")
            }
        }
        socketThread?.start()
    }

    /**
     * Handle a client connection from the Zygisk module.
     */
    private fun handleClient(client: Any) {
        try {
            val input = client.javaClass.getMethod("getInputStream").invoke(client) as InputStream
            val output = client.javaClass.getMethod("getOutputStream").invoke(client) as OutputStream
            val reader = input.bufferedReader()
            val writer = output.bufferedWriter()

            val command: String? = reader.readLine()
            if (command != null) {
                when {
                    command.startsWith(CMD_STATUS) -> {
                        // Module asks for current status
                        val response = buildStatusResponse()
                        writer.write(response)
                        writer.flush()
                    }
                    command.startsWith(CMD_SET_ACTIVE) -> {
                        // Module reports it's active in this process
                        android.util.Log.d("IpcService", "Module active in process")
                    }
                    command.startsWith(CMD_SET_INACTIVE) -> {
                        // Module reports it's inactive
                        android.util.Log.d("IpcService", "Module inactive in process")
                    }
                }
            }

            client.javaClass.getMethod("close").invoke(client)
        } catch (e: IOException) {
            android.util.Log.e("IpcService", "Client error: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("IpcService", "Client error: ${e.message}")
        }
    }

    /**
     * Build status response for the module.
     */
    private fun buildStatusResponse(): String {
        // Read current config and return status
        return "STATUS:OK"
    }

    /**
     * Send a command to the Zygisk companion process.
     * This is called from the UI when the user changes settings.
     */
    fun sendCommand(command: String, data: String? = null) {
        try {
            val socket = SocketCompat.newLocalSocket()
            val address = SocketCompat.newLocalSocketAddress(SOCKET_PATH)
            socket.javaClass.getMethod("connect", SocketCompat.LocalSocketAddressClass)
                .invoke(socket, address)

            val output = socket.javaClass.getMethod("getOutputStream").invoke(socket) as OutputStream
            val writer = output.bufferedWriter()
            writer.write(command)
            if (data != null) {
                writer.write(":$data")
            }
            writer.write("\n")
            writer.flush()

            // Read response
            val input = socket.javaClass.getMethod("getInputStream").invoke(socket) as InputStream
            val reader = input.bufferedReader()
            val response: String? = reader.readLine()
            android.util.Log.d("IpcService", "Response: $response")

            socket.javaClass.getMethod("close").invoke(socket)
        } catch (e: IOException) {
            android.util.Log.e("IpcService", "Send command error: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("IpcService", "Send command error: ${e.message}")
        }
    }

    /**
     * Update the module config file directly.
     * This is the simpler approach - write to /data/adb/modules/kyc_bypasser/config.json
     */
    fun updateConfigFile(key: String, value: Any) {
        val configPath = com.kycbypasser.app.KYCApp.configPath()
        val configFile = File(configPath)

        // Simple key=value format for now (upgrade to JSON later)
        val line = "$key=$value\n"
        configFile.appendText(if (configFile.length() == 0L) line else "\n$line")

        android.util.Log.d("IpcService", "Config updated: $key = $value")
    }
}

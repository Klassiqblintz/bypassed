package com.kycbypasser.app.service

import android.app.Service
import android.content.Intent
import android.os.*
import java.io.*

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

    private var serverSocket: LocalServerSocket? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): IpcService = this@IpcService
    }

    override fun onCreate() {
        super.onCreate()
        startSocketServer()
    }

    override fun onDestroy() {
        serverSocket?.close()
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

                serverSocket = LocalServerSocket(SOCKET_PATH)

                while (!Thread.interrupted()) {
                    val client = serverSocket?.accept()
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
    private fun handleClient(client: LocalSocket) {
        try {
            val input = client.inputStream.bufferedReader()
            val output = client.outputStream.bufferedWriter()

            val command = input.readLine()
            if (command != null) {
                when {
                    command.startsWith(CMD_STATUS) -> {
                        // Module asks for current status
                        val response = buildStatusResponse()
                        output.write(response)
                        output.flush()
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

            client.close()
        } catch (e: IOException) {
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
            val socket = LocalSocket()
            socket.connect(LocalSocketAddress(SOCKET_PATH, LocalSocketAddress.Namespace.ABSOLUTE))

            val output = socket.outputStream.bufferedWriter()
            output.write("$command")
            if (data != null) {
                output.write(":$data")
            }
            output.write("\n")
            output.flush()

            // Read response
            val input = socket.inputStream.bufferedReader()
            val response = input.readLine()
            android.util.Log.d("IpcService", "Response: $response")

            socket.close()
        } catch (e: IOException) {
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

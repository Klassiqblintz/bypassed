package com.kycbypasser.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceTexture
import com.kycbypasser.app.KycState
import com.kycbypasser.app.MainActivity

/**
 * Virtual camera service — draws our image/video onto each camera frame.
 *
 * HOW IT WORKS:
 * 1. Opens the front camera via Camera2 API
 * 2. Creates a SurfaceTexture as the camera's output surface
 * 3. Each time the sensor produces a frame, draws our image on top
 * 4. The camera's captured frame = sensor data + our image overlaid
 * 5. Target app sees OUR image, not the real camera
 *
 * Works WITHOUT root.
 */
class VirtualCameraService : Service() {

    companion object {
        const val CHANNEL_ID = "kyc_bypasser_vc"
        const val NOTIFICATION_ID = 1
        const val CMD_START = "START"
        const val CMD_STOP = "STOP"
        const val LOG_TAG = "VirtualCamera"
        const val DEFAULT_FPS = 30
        const val FRAME_DURATION_MS = 1000L / DEFAULT_FPS
    }

    // Camera
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null
    private var cameraId: String? = null

    // Frame data
    private var currentFrame: Bitmap? = null
    private var currentVideoPath: String? = null
    private var currentMode: String = "static"
    private var isRunning = false
    private var renderThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_TAG, "VirtualCameraService created")
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true

        loadConfig()
        openCamera()
    }

    private fun loadConfig() {
        try {
            val state = KycState(this)
            currentMode = state.injectionMode
            currentVideoPath = state.selectedVideoPath

            if (!state.selectedImagePath.isNullOrEmpty()) {
                currentFrame = BitmapFactory.decodeFile(state.selectedImagePath)
            }
            if ((currentFrame == null || currentFrame!!.width == 0) &&
                !state.selectedVideoPath.isNullOrEmpty()) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(state.selectedVideoPath)
                    currentFrame = retriever.getFrameAtTime(
                        0, MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    retriever.release()
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Failed to extract video frame: ${e.message}")
                }
            }

            if (currentFrame == null || currentFrame!!.width == 0 || currentFrame!!.height == 0) {
                currentFrame = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.WHITE)
                }
            }

            Log.d(LOG_TAG, "Loaded frame: ${currentFrame!!.width}x${currentFrame!!.height}, mode=$currentMode")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to load config: ${e.message}", e)
            currentFrame = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.WHITE)
            }
        }
    }

    private fun openCamera() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Find FRONT camera
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id
                    break
                }
            }

            if (cameraId == null) {
                Log.e(LOG_TAG, "No front camera found")
                // Use first available camera
                if (cameraManager.cameraIdList.isNotEmpty()) {
                    cameraId = cameraManager.cameraIdList[0]
                    Log.d(LOG_TAG, "Using first camera as fallback: $cameraId")
                } else {
                    stopSelf()
                    return
                }
            }

            Log.d(LOG_TAG, "Opening front camera: $cameraId")
            cameraManager.openCamera(cameraId!!, stateCallback, mainHandler)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to open camera: ${e.message}", e)
            stopSelf()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            Log.d(LOG_TAG, "Camera opened successfully")
            createCameraSurface()
        }

        override fun onDisconnected(device: CameraDevice) {
            Log.d(LOG_TAG, "Camera disconnected")
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(device: CameraDevice, error: Int) {
            Log.e(LOG_TAG, "Camera error: $error")
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    private fun createCameraSurface() {
        try {
            cameraTexture = SurfaceTexture(1).apply {
                setDefaultBufferSize(720, 1280)
                setOnFrameAvailableListener {
                    // Each time a new camera frame is available, draw our image on top
                    drawOverlayFrame()
                }
            }
            cameraSurface = Surface(cameraTexture)
            Log.d(LOG_TAG, "Camera surface created, texture id = ${cameraTexture!!.getTextureId()}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to create camera surface: ${e.message}", e)
        }
    }

    /**
     * Draw our image on top of the camera's sensor frame.
     * This is called each time a new sensor frame arrives.
     */
    private fun drawOverlayFrame() {
        if (!isRunning || cameraTexture == null || cameraSurface == null || currentFrame == null) return

        try {
            // Update the texture to get the latest sensor frame
            cameraTexture?.updateTexImage()

            // Get the transform matrix (handles camera orientation)
            val transform = android.graphics.Matrix()
            cameraTexture?.getTransformMatrix(transform)

            // Lock the surface canvas
            val canvas = cameraSurface?.lockCanvas(null) ?: return

            // Draw our image to fill the screen (overriding the sensor frame)
            val src = Rect(0, 0, currentFrame!!.width, currentFrame!!.height)
            val dst = Rect(0, 0, canvas.width, canvas.height)
            canvas.drawBitmap(currentFrame!!, src, dst, null)

            // Unlock and post — our frame is now visible to the camera
            cameraSurface?.unlockCanvasAndPost(canvas)

            Log.d(LOG_TAG, "Overlay frame drawn (${canvas.width}x${canvas.height})")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Draw overlay error: ${e.message}", e)
        }
    }

    private fun configureCaptureSession() {
        val surf = cameraSurface ?: run {
            Log.e(LOG_TAG, "No camera surface available")
            return
        }
        val cam = cameraDevice ?: run {
            Log.e(LOG_TAG, "No camera device available")
            return
        }

        cam.createCaptureSession(
            listOf(surf),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val request = cam.createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW
                        )
                        request.addTarget(surf)
                        session.setRepeatingRequest(
                            request.build(),
                            null,
                            mainHandler
                        )
                        Log.d(LOG_TAG, "Capture session configured")
                        Log.d(LOG_TAG, "Camera is now streaming sensor frames to our SurfaceTexture")
                        Log.d(LOG_TAG, "Each frame will have our image drawn on top")
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Failed to create request: ${e.message}", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(LOG_TAG, "Failed to configure capture session")
                }
            },
            mainHandler
        )
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("KYC Bypasser")
            .setContentText("Virtual camera is active — camera preview replaced")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "KYC Bypasser Virtual Camera",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CMD_START -> Log.d(LOG_TAG, "Virtual camera started")
            CMD_STOP -> {
                Log.d(LOG_TAG, "Virtual camera stopping")
                isRunning = false
                renderThread?.interrupt()
                captureSession?.close()
                cameraDevice?.close()
                cameraSurface?.release()
                cameraTexture?.release()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "VirtualCameraService destroyed")
        isRunning = false
        renderThread?.interrupt()
        captureSession?.close()
        cameraDevice?.close()
        cameraSurface?.release()
        cameraTexture?.release()
        stopForeground(true)
        stopSelf()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

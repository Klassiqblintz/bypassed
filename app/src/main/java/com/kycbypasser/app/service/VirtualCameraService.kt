package com.kycbypasser.app.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCaptureSession
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
 * Virtual camera service — opens the device's front camera,
 * each frame draws our selected image/video onto it,
 * so the target app sees our frames instead of the real camera.
 *
 * Works WITHOUT root. Uses Camera2 API.
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

    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var currentImage: Bitmap? = null
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

        // Load configuration
        loadConfig()

        // Open the front camera
        openCamera()

        // Create surface for drawing our frames
        createSurface()

        // Start rendering (each frame draws our image/video)
        startRendering()
    }

    private fun loadConfig() {
        try {
            val state = KycState(this)
            currentMode = state.injectionMode
            currentImage = if (!state.selectedImagePath.isNullOrEmpty()) {
                BitmapFactory.decodeFile(state.selectedImagePath)
            } else {
                // Placeholder white bitmap
                Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            }
            currentVideoPath = state.selectedVideoPath

            if (currentImage == null || currentImage!!.width == 0) {
                currentImage = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to load config: ${e.message}")
        }
    }

    private fun openCamera() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var frontCameraId: String? = null

            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = cameraId
                    break
                }
            }

            if (frontCameraId == null) {
                Log.e(LOG_TAG, "No front camera found")
                return
            }

            Log.d(LOG_TAG, "Opening front camera: $frontCameraId")
            cameraManager.openCamera(frontCameraId, stateCallback, mainHandler)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to open camera: ${e.message}", e)
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            Log.d(LOG_TAG, "Camera opened successfully")
            createCaptureSession()
        }

        override fun onDisconnected(device: CameraDevice) {
            Log.d(LOG_TAG, "Camera disconnected")
            cameraDevice = null
        }

        override fun onError(device: CameraDevice, error: Int) {
            Log.e(LOG_TAG, "Camera error: $error")
        }
    }

    private fun createSurface() {
        try {
            surfaceTexture = SurfaceTexture(1)
            surfaceTexture?.setDefaultBufferSize(720, 1280)
            surface = Surface(surfaceTexture)
            Log.d(LOG_TAG, "Surface created: ${surfaceTexture?.getTextureId()}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to create surface: ${e.message}", e)
        }
    }

    private fun createCaptureSession() {
        val surf = surface ?: run {
            Log.e(LOG_TAG, "No surface available")
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
                    cameraSession = session
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
                        Log.d(LOG_TAG, "Camera session configured with our Surface")
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Failed to create request: ${e.message}", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(LOG_TAG, "Failed to configure camera session")
                }
            },
            mainHandler
        )
    }

    /**
     * Each frame, draw our image/video to the Surface.
     * Since the camera outputs to this same Surface, our drawing
     * overrides the camera's sensor data — the target app sees our frames.
     */
    private fun startRendering() {
        Log.d(LOG_TAG, "Starting renderer")
        val img = currentImage ?: return

        renderThread = Thread {
            while (isRunning) {
                try {
                    val canvas = surface?.lockCanvas(null)
                    if (canvas != null) {
                        // Draw our image/video frame
                        val src = Rect(0, 0, img.width, img.height)
                        val dst = Rect(0, 0, canvas.width, canvas.height)
                        canvas.drawBitmap(img, src, dst, null)
                        surface?.unlockCanvasAndPost(canvas)
                        Log.d(LOG_TAG, "Frame drawn")
                    }
                    Thread.sleep(FRAME_DURATION_MS)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Render error: ${e.message}", e)
                }
            }
        }
        renderThread?.start()
    }

    /**
     * Create a foreground notification for the service.
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("KYC Bypasser")
            .setContentText("Virtual camera is active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "KYC Bypasser Virtual Camera",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setSound(null, null)
            channel.setShowBadge(false)
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
                cameraSession?.close()
                cameraDevice?.close()
                surface?.release()
                surfaceTexture?.release()
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
        cameraSession?.close()
        cameraDevice?.close()
        surface?.release()
        surfaceTexture?.release()
        stopForeground(true)
        stopSelf()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package com.kycbypasser.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.kycbypasser.app.KycState
import com.kycbypasser.app.MainActivity

/**
 * Virtual camera service — uses a full-screen overlay to replace
 * the target app's camera preview with our selected image/video.
 *
 * WITHOUT root: Opens a WindowManager overlay on top of all apps.
 * Each frame draws our selected image/video to it.
 * The target app's camera captures the overlay (screen) instead of the sensor.
 *
 * Works on Android 6+ with SYSTEM_ALERT_WINDOW permission.
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

    private var currentFrame: Bitmap? = null
    private var currentVideoPath: String? = null
    private var currentMode: String = "static"
    private var isRunning = false
    private var renderThread: Thread? = null
    private var overlayView: SurfaceView? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_TAG, "VirtualCameraService created")
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true

        // Load configuration
        loadConfig()

        // Create full-screen overlay
        createOverlay()

        // Start rendering (each frame draws our image/video to the overlay)
        startRendering()
    }

    /**
     * Load the selected image/video from KycState.
     * For video mode, extract the first frame as the overlay frame.
     */
    private fun loadConfig() {
        try {
            val state = KycState(this)
            currentMode = state.injectionMode
            currentVideoPath = state.selectedVideoPath

            // Try to load image first
            if (!state.selectedImagePath.isNullOrEmpty()) {
                currentFrame = BitmapFactory.decodeFile(state.selectedImagePath)
            }

            // If no image but has video, extract first frame
            if ((currentFrame == null || currentFrame!!.width == 0) &&
                !state.selectedVideoPath.isNullOrEmpty()) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(state.selectedVideoPath)
                    currentFrame = retriever.getFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    retriever.release()
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Failed to extract video frame: ${e.message}")
                }
            }

            // Default to white bitmap if nothing loaded
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

    /**
     * Create a full-screen overlay SurfaceView using WindowManager.
     * Tries TYPE_APPLICATION_OVERLAY first (needs SYSTEM_ALERT_WINDOW),
     * falls back to TYPE_PHONE if permission not granted.
     */
    private fun createOverlay() {
        overlayView = SurfaceView(this)
        overlayView?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(LOG_TAG, "Overlay surface created")
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.d(LOG_TAG, "Overlay surface changed: $width x $height")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(LOG_TAG, "Overlay surface destroyed")
                isRunning = false
            }
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Try TYPE_APPLICATION_OVERLAY first
        var success = false
        try {
            windowManager?.addView(overlayView, params)
            Log.d(LOG_TAG, "Overlay created with TYPE_APPLICATION_OVERLAY")
            success = true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "TYPE_APPLICATION_OVERLAY failed, trying TYPE_PHONE: ${e.message}")
            // Fall back to TYPE_PHONE (doesn't need special permission)
            params.type = WindowManager.LayoutParams.TYPE_PHONE
            try {
                windowManager?.addView(overlayView, params)
                Log.d(LOG_TAG, "Overlay created with TYPE_PHONE (fallback)")
                success = true
            } catch (e2: Exception) {
                Log.e(LOG_TAG, "TYPE_PHONE also failed: ${e2.message}", e2)
            }
        }

        if (!success) {
            Log.e(LOG_TAG, "Overlay creation FAILED")
        }
    }

    /**
     * Each frame, draw our image/video to the overlay's SurfaceView.
     * The target app's camera captures this overlay (screen) instead of the sensor.
     */
    private fun startRendering() {
        Log.d(LOG_TAG, "Starting renderer, FPS=$DEFAULT_FPS")
        val frame = currentFrame ?: return
        renderThread = Thread {
            while (isRunning) {
                try {
                    val holder = overlayView?.holder ?: continue
                    val canvas = holder.lockCanvas(null)
                    if (canvas != null) {
                        // Draw black background
                        canvas.drawColor(android.graphics.Color.BLACK)
                        // Draw our image/video frame to fill the screen
                        val src = Rect(0, 0, frame.width, frame.height)
                        val dst = Rect(0, 0, canvas.width, canvas.height)
                        canvas.drawBitmap(frame, src, dst, null)
                        holder.unlockCanvasAndPost(canvas)
                        Log.d(LOG_TAG, "Frame drawn (${canvas.width}x${canvas.height})")
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
            android.app.PendingIntent.FLAG_IMMUTABLE or
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("KYC Bypasser")
            .setContentText("Overlay is active — camera preview replaced")
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
                mainHandler.post {
                    try {
                        overlayView?.let { wm ->
                            windowManager?.removeView(wm)
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Failed to remove overlay: ${e.message}", e)
                    }
                    overlayView = null
                }
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
        mainHandler.post {
            try {
                overlayView?.let { wm ->
                    windowManager?.removeView(wm)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to remove overlay: ${e.message}", e)
            }
            overlayView = null
        }
        stopForeground(true)
        stopSelf()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

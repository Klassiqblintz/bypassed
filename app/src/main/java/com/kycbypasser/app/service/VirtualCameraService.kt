package com.kycbypasser.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.kycbypasser.app.KycState
import com.kycbypasser.app.MainActivity
import java.io.File

/**
 * Virtual camera service — streams injected media to a surface
 * that the target app reads as its camera output.
 *
 * Works WITHOUT root. Uses a background thread to continuously
 * render the selected image or video to a SurfaceTexture/Surface pair.
 */
class VirtualCameraService : Service() {

    companion object {
        const val VIRTUAL_CAMERA_ID = "100"
        const val CHANNEL_ID = "kyc_bypasser_vc"
        const val NOTIFICATION_ID = 1
        const val CMD_START = "START"
        const val CMD_STOP = "STOP"
        const val SURFACE_PATH = "/data/local/tmp/kyc_bypasser_surface"
        const val LOG_TAG = "VirtualCamera"
        const val DEFAULT_FPS = 30
        const val FRAME_DURATION_MS = 1000L / DEFAULT_FPS
    }

    private var surfaceTexture: android.graphics.SurfaceTexture? = null
    private var surface: android.view.Surface? = null
    private var isRunning = false
    private var renderThread: Thread? = null

    // Media state
    private var currentImage: Bitmap? = null
    private var currentVideoPath: String? = null
    private var currentMode: String = "static"

    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_TAG, "VirtualCameraService created")
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true

        // Load configuration
        try {
            val state = KycState(this)
            currentMode = state.injectionMode
            currentImage = if (!state.selectedImagePath.isNullOrEmpty()) {
                android.graphics.BitmapFactory.decodeFile(state.selectedImagePath)
            } else {
                // Placeholder white bitmap
                Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            }
            currentVideoPath = state.selectedVideoPath
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to load config: ${e.message}")
        }

        // Create a placeholder image if none exists
        if (currentImage == null || currentImage!!.width == 0) {
            currentImage = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        }

        // Setup virtual camera surface
        setupSurface()

        // Start rendering based on mode
        when (currentMode) {
            "video" -> startVideoRenderer()
            else -> startImageRenderer()
        }

        // Write surface path to file for Zygisk hook
        writeSurfacePath()
    }

    /**
     * Create a SurfaceTexture + Surface pair.
     * The Surface will be used by the target app's camera as output.
     */
    private fun setupSurface() {
        try {
            surfaceTexture = android.graphics.SurfaceTexture(1)
            surfaceTexture?.setDefaultBufferSize(720, 1280)
            surface = android.view.Surface(surfaceTexture)
            surfaceTexture?.setOnFrameAvailableListener {
                // Frame is available, can process if needed
            }
            Log.d(LOG_TAG, "Surface created successfully")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to create surface: ${e.message}", e)
        }
    }

    /**
     * Start image renderer — continuously draws the selected image to the surface.
     */
    private fun startImageRenderer() {
        Log.d(LOG_TAG, "Starting image renderer")
        val img = currentImage ?: return

        renderThread = Thread {
            while (isRunning && surface != null) {
                try {
                    val canvas = surface?.lockCanvas(null)
                    if (canvas != null) {
                        val src = android.graphics.Rect(0, 0, img.width, img.height)
                        val dst = android.graphics.Rect(0, 0, canvas.width, canvas.height)
                        canvas.drawBitmap(img, src, dst, null)
                        surface?.unlockCanvasAndPost(canvas)
                    }
                    Thread.sleep(FRAME_DURATION_MS)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Image render error: ${e.message}", e)
                }
            }
        }
        renderThread?.start()
    }

    /**
     * Start video renderer — uses MediaExtractor + MediaCodec to decode and push frames.
     */
    private fun startVideoRenderer() {
        Log.d(LOG_TAG, "Starting video renderer")
        val videoPath = currentVideoPath ?: run {
            Log.w(LOG_TAG, "No video path, falling back to image renderer")
            startImageRenderer()
            return
        }

        renderThread = Thread {
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(videoPath)

                // Find video track
                var videoTrack = -1
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        videoTrack = i
                        break
                    }
                }

                if (videoTrack < 0) {
                    Log.e(LOG_TAG, "No video track found, falling back to image renderer")
                    startImageRenderer()
                    return@Thread
                }

                extractor.selectTrack(videoTrack)

                val trackFmt = extractor.getTrackFormat(videoTrack)
                val decoder = MediaCodec.createDecoderByType(
                    trackFmt.getString(MediaFormat.KEY_MIME) ?: "video/avc"
                )
                decoder.configure(trackFmt, surface, null, 0)
                decoder.start()

                // Decoding loop
                var done = false
                while (isRunning && !done) {
                    val inputIdx = decoder.dequeueInputBuffer(FRAME_DURATION_MS)
                    if (inputIdx >= 0 && extractor.getSampleTime() >= 0) {
                        val buf = decoder.getInputBuffer(inputIdx)
                        if (buf != null) {
                            val chunkSize = extractor.readSampleData(buf, 0)
                            if (chunkSize > 0) {
                                decoder.queueInputBuffer(inputIdx, 0, chunkSize,
                                    extractor.getSampleTime(), 0)
                                extractor.advance()
                            } else {
                                decoder.queueInputBuffer(inputIdx, 0, 0, -1,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        }
                    }

                    val info = MediaCodec.BufferInfo()
                    val outIdx = decoder.dequeueOutputBuffer(info, FRAME_DURATION_MS)
                    when {
                        outIdx >= 0 -> {
                            decoder.releaseOutputBuffer(outIdx, false)
                        }
                        outIdx == android.media.MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            Thread.sleep(10)
                        }
                        else -> {
                            // Format changed or other info
                        }
                    }
                }

                decoder.stop()
                decoder.release()
                extractor.release()

            } catch (e: Exception) {
                Log.e(LOG_TAG, "Video render error: ${e.message}", e)
                startImageRenderer()
            }
        }
        renderThread?.start()
    }

    /**
     * Write surface info to a file for the Zygisk hook to find.
     */
    private fun writeSurfacePath() {
        try {
            File("/data/local/tmp/kyc_bypasser").mkdirs()
            File(SURFACE_PATH).writeText("surface_id=1\nresolution=720x1280\nmode=$currentMode\n")
            Log.d(LOG_TAG, "Surface path written to $SURFACE_PATH")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to write surface path: ${e.message}")
        }
    }

    /**
     * Create a foreground notification for the service (no NotificationCompat needed).
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
        surface?.release()
        surfaceTexture?.release()
        stopForeground(true)
        stopSelf()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

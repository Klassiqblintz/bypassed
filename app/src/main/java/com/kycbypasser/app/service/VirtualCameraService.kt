package com.kycbypasser.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.*
import android.util.Log
import com.kycbypasser.app.KycState
import com.kycbypasser.app.MainActivity
import java.io.File
import java.nio.ByteBuffer

/**
 * Virtual camera service — streams injected media to a surface
 * that the target app reads as its camera output.
 *
 * Works WITHOUT root. Uses a background thread to continuously
 * render the selected image or video to a SurfaceTexture/Surface pair.
 *
 * The Zygisk module's camera hook (in camera_hook.cpp) redirects the
 * target app's camera to this surface via the VIRTUAL_CAMERA_ID ("100").
 */
class VirtualCameraService : Service() {

    companion object {
        const val VIRTUAL_CAMERA_ID = "100"
        const val CHANNEL_ID = "kyc_bypasser_vc"
        const val NOTIFICATION_ID = 1
        const val CMD_START = "START"
        const val CMD_STOP = "RESTOP"
        const val SOCKET_PATH = "/dev/socket/kyc_vcamera"
        const val SURFACE_PATH = "/data/local/tmp/kyc_vcamera_surface"
        const val LOG_TAG = "VirtualCamera"

        // Frame rate
        const val DEFAULT_FPS = 30
        const val FRAME_DURATION_MS = 1000 / DEFAULT_FPS
    }

    private var surfaceTexture: android.graphics.SurfaceTexture? = null
    private var surface: android.view.Surface? = null
    private var encoder: MediaCodec? = null
    private var muxer: android.media.MediaMuxer? = null
    private var isRunning = false
    private var renderThread: Thread? = null
    private var videoFrameBuffer: ByteBuffer? = null

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
            currentImage = state.selectedImagePath?.let {
                android.graphics.BitmapFactory.decodeFile(it)
            }
            currentVideoPath = state.selectedVideoPath
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to load config: ${e.message}")
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
            // Create SurfaceTexture with a unique ID
            surfaceTexture = android.graphics.SurfaceTexture(1)

            // Set buffer size to match common KYC camera resolution
            surfaceTexture?.setDefaultBufferSize(720, 1280)

            // Create Surface from SurfaceTexture
            surface = android.view.Surface(surfaceTexture)

            // Set frame available listener
            surfaceTexture?.setOnFrameAvailableListener {
                // Each time the surface receives a frame, we process it
                processFrame()
            }

            Log.d(LOG_TAG, "Surface created successfully: ${surface}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to create surface: ${e.message}", e)
        }
    }

    /**
     * Start image renderer — continuously draws the selected image to the surface.
     */
    private fun startImageRenderer() {
        Log.d(LOG_TAG, "Starting image renderer")

        if (currentImage == null) {
            // Use a placeholder white bitmap
            currentImage = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        }

        renderThread = Thread {
            while (isRunning && surface != null && currentImage != null) {
                try {
                    // Lock the surface for writing
                    val canvas = surface?.lockCanvas(null)
                    if (canvas != null) {
                        // Draw the image scaled to fill the canvas
                        val srcRect = android.graphics.Rect(0, 0, currentImage!!.width, currentImage!!.height)
                        val dstRect = android.graphics.Rect(0, 0, canvas.width, canvas.height)
                        canvas.drawBitmap(currentImage!!, srcRect, dstRect, null)

                        // Unlock and post
                        surface?.unlockCanvasAndPost(canvas)
                        Log.d(LOG_TAG, "Image frame posted")
                    }

                    // Sleep for frame duration (30fps)
                    Thread.sleep(FRAME_DURATION_MS)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Image render error: ${e.message}", e)
                }
            }
        }
        renderThread?.start()
    }

    /**
     * Start video renderer — uses MediaCodec to decode and encode the video,
     * then pushes each frame to the surface.
     */
    private fun startVideoRenderer() {
        Log.d(LOG_TAG, "Starting video renderer: $currentVideoPath")

        if (currentVideoPath.isNullOrEmpty()) {
            Log.w(LOG_TAG, "No video path, falling back to image renderer")
            startImageRenderer()
            return
        }

        renderThread = Thread {
            try {
                // Decode video using MediaExtractor + MediaCodec
                val extractor = android.media.MediaExtractor()
                extractor.setDataSource(currentVideoPath)

                // Find video track
                var videoTrack = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mimeType = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    if (mimeType.startsWith("video/")) {
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

                // Create decoder
                val trackFormat = extractor.getTrackFormat(videoTrack)
                val decoder = MediaCodec.createDecoderByType(trackFormat.getString(android.media.MediaFormat.KEY_MIME) ?: "video/avc")

                // Configure decoder with our surface as output
                decoder.configure(trackFormat, surface, null, 0)
                decoder.start()

                // Create encoder for the virtual camera output
                val encoderFormat = MediaFormat.createVideoFormat("video/avc", 720, 1280)
                encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000) // 1 Mbps
                encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30)
                encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

                val videoEncoder = MediaCodec.createEncoderByType("video/avc")
                videoEncoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                videoEncoder.start()

                // Start muxer
                val outputDir = "/data/local/tmp/kyc_bypasser/vcamera"
                File(outputDir).mkdirs()
                val outputFile = File("$outputDir/frame.h264")

                muxer = android.media.MediaMuxer(outputFile.path, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP)
                val encoderTrack = muxer!!.addTrack(encoderFormat)
                muxer!!.start()

                val inputBufferIndex = decoder.dequeueInputBuffer(FRAME_DURATION_MS.toLong())
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                    val chunkSize = extractor.readSampleData(inputBuffer, 0)
                    if (chunkSize > 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, chunkSize,
                            extractor.getSampleTime(), 0)
                        extractor.advance()
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0,
                            -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }

                // Render loop
                var done = false
                while (isRunning && !done) {
                    // Decode
                    val outIndex = decoder.dequeueOutputBuffer(decoder.Info(), FRAME_DURATION_MS.toLong())
                    if (outIndex >= 0) {
                        val info = decoder.Info()
                        if (info.size > 0) {
                            // Encode
                            val encInIndex = videoEncoder.dequeueInputBuffer(FRAME_DURATION_MS.toLong())
                            if (encInIndex >= 0) {
                                val encInBuf = videoEncoder.getInputBuffer(encInIndex)
                                if (encInBuf != null) {
                                    encInBuf.clear()
                                    // Copy decoded frame to encoder input
                                    // (simplified - in practice would use surface-to-surface copy)
                                }
                                videoEncoder.queueInputBuffer(encInIndex, 0, 0,
                                    info.timeStamp, 0)

                                val encOutIndex = videoEncoder.dequeueOutputBuffer(videoEncoder.Info(), 0)
                                if (encOutIndex >= 0) {
                                    val encInfo = videoEncoder.Info()
                                    if (encInfo.size > 0) {
                                        val encBuf = videoEncoder.getOutputBuffer(encOutIndex)
                                        if (encBuf != null) {
                                            muxer!!.writeSampleData(encoderTrack, encBuf, encInfo)
                                        }
                                        videoEncoder.releaseOutputBuffer(encOutIndex, false)
                                    }
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                    }

                    // Check for end of stream
                    if (decoder.getOperationStatus() == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // Format changed, update muxer
                    }

                    // Advance extractor for next frame
                    if (extractor.getSampleTime() < 0 || !extractor.advance()) {
                        // Loop video
                        extractor.seekTo(0)
                    }
                }

                // Cleanup
                decoder.stop()
                decoder.release()
                videoEncoder.stop()
                videoEncoder.release()
                muxer?.stop()
                muxer?.release()
                extractor.release()

            } catch (e: Exception) {
                Log.e(LOG_TAG, "Video render error: ${e.message}", e)
                // Fallback to image renderer
                startImageRenderer()
            }
        }
        renderThread?.start()
    }

    /**
     * Process each frame received from the surface.
     * Can be used for post-processing or analysis.
     */
    private fun processFrame() {
        // Each time a frame is written to our surface, we can process it
        // For now, we just log it
        // In the future: liveness detection, face tracking, etc.
    }

    /**
     * Write the surface path to a file so the Zygisk hook can find it.
     */
    private fun writeSurfacePath() {
        try {
            val dir = File("/data/local/tmp/kyc_bypasser")
            dir.mkdirs()
            val file = File(SURFACE_PATH)
            file.writeText("surface_id=1\nresolution=720x1280\nmode=$currentMode\n")
            Log.d(LOG_TAG, "Surface path written to $SURFACE_PATH")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to write surface path: ${e.message}")
        }
    }

    /**
     * Create a foreground notification for the service.
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KYC Bypasser")
            .setContentText("Virtual camera is active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        // Create notification channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KYC Bypasser Virtual Camera",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setSound(null, null)
            channel.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CMD_START -> {
                Log.d(LOG_TAG, "Virtual camera started")
            }
            CMD_STOP -> {
                Log.d(LOG_TAG, "Virtual camera stopping")
                stop()
            }
        }
        return START_STICKY
    }

    /**
     * Stop the virtual camera service.
     */
    private fun stop() {
        isRunning = false
        renderThread?.interrupt()
        surface?.release()
        surfaceTexture?.release()
        encoder?.stop()
        encoder?.release()
        muxer?.stop()
        muxer?.release()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "VirtualCameraService destroyed")
        stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

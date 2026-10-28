package com.capsulecorp.kiosk

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Range
import kotlin.math.abs

/**
 * Notices someone in front of the screen with the front camera, cheaply: a tiny
 * picture a few times a second, reduced to a 24 x 16 grid of brightness values and
 * compared with the previous one. Nothing is kept or sent anywhere; only the last
 * grid stays in memory.
 */
class MotionDetector(private val context: Context, private val onMotion: () -> Unit) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previous: IntArray? = null
    private var lastFrame = 0L

    val running get() = camera != null

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (thread != null || !hasPermission()) return
        val id = frontCamera() ?: return
        val size = manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.filter { it.width >= 160 }
            ?.minByOrNull { it.width * it.height } ?: return
        val t = HandlerThread("motion").also { it.start() }
        val h = Handler(t.looper)
        thread = t
        val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
        r.setOnImageAvailableListener({ it.acquireLatestImage()?.use(::analyse) }, h)
        reader = r
        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    @Suppress("DEPRECATION")
                    device.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            session = s
                            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(r.surface)
                                slowestFps(id)?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                            }
                            try {
                                s.setRepeatingRequest(request.build(), null, h)
                            } catch (_: Exception) {
                                stop()
                            }
                        }

                        override fun onConfigureFailed(s: CameraCaptureSession) = stop()
                    }, h)
                }

                override fun onDisconnected(device: CameraDevice) = stop()
                override fun onError(device: CameraDevice, error: Int) = stop()
            }, h)
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        try { session?.close() } catch (_: Exception) {}
        try { camera?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        thread?.quitSafely()
        session = null
        camera = null
        reader = null
        thread = null
        previous = null
    }

    private fun frontCamera(): String? = try {
        manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
    } catch (_: Exception) {
        null
    }

    private fun slowestFps(id: String): Range<Int>? =
        manager.getCameraCharacteristics(id).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.minWithOrNull(compareBy<Range<Int>> { it.upper }.thenBy { it.lower })

    private fun analyse(image: Image) {
        val now = SystemClock.uptimeMillis()
        if (now - lastFrame < FRAME_MS) return
        lastFrame = now
        val plane = image.planes[0] // brightness (Y)
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height
        val grid = IntArray(COLS * ROWS)
        for (gy in 0 until ROWS) {
            for (gx in 0 until COLS) {
                // Average a 3 x 3 sample inside each cell.
                var sum = 0
                for (sy in 1..3) for (sx in 1..3) {
                    val x = (gx * w + sx * w / 4) / COLS
                    val y = (gy * h + sy * h / 4) / ROWS
                    sum += buf.get(y * rowStride + x * pixelStride).toInt() and 0xFF
                }
                grid[gy * COLS + gx] = sum / 9
            }
        }
        val prev = previous
        previous = grid
        if (prev == null) return
        var changed = 0
        for (i in grid.indices) if (abs(grid[i] - prev[i]) > CELL_DIFF) changed++
        if (changed >= MIN_CELLS) main.post(onMotion)
    }

    companion object {
        private const val COLS = 24
        private const val ROWS = 16
        /** About 3 pictures a second. */
        private const val FRAME_MS = 330L
        /** How much a cell's brightness (0–255) must change to count. */
        private const val CELL_DIFF = 18
        /** How many of the 384 cells must change: about 4 %. */
        private const val MIN_CELLS = 15
    }
}

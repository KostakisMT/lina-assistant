package dev.lina.feature.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.ByteArrayOutputStream

/**
 * Nimmt ein Standbild der Rückkamera auf (CameraX, headless – ohne Preview).
 * Fürs Dokument-Vorlesen: Nutzer legt Post/Zeitung in den festen Rahmen hinter
 * dem Tablet, Lina fotografiert und schickt das Bild an die Vision-Auswertung.
 *
 * [capture] bindet die Kamera bei Bedarf, macht ein Foto und gibt JPEG-Bytes
 * (herunterskaliert) zurück – oder null bei jedem Fehler. Callback auf Main.
 */
class DocumentCamera(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var owner: CaptureLifecycleOwner? = null

    /**
     * Eigener Lifecycle statt dem der Activity: Das Tablet steht stationär und
     * der Bildschirm ist meist aus – eine gestoppte Activity würde die Kamera
     * sofort wieder abkoppeln ("Camera is closed").
     */
    private class CaptureLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun start() { registry.currentState = Lifecycle.State.RESUMED }
        fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
    }

    fun capture(onResult: (ByteArray?) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                imageCapture = capture

                cameraProvider.unbindAll()
                val captureOwner = CaptureLifecycleOwner().also { owner = it }
                cameraProvider.bindToLifecycle(
                    captureOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    capture,
                )
                captureOwner.start()

                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bytes = try {
                                toJpegBytes(image)
                            } catch (e: Exception) {
                                Log.e(TAG, "Bildkonvertierung fehlgeschlagen", e)
                                null
                            } finally {
                                image.close()
                            }
                            release()
                            onResult(bytes)
                        }

                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "Aufnahme fehlgeschlagen", exc)
                            release()
                            onResult(null)
                        }
                    },
                )
            } catch (e: Exception) {
                Log.e(TAG, "Kamera-Initialisierung fehlgeschlagen", e)
                release()
                onResult(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        try {
            owner?.stop()
            provider?.unbindAll()
        } catch (_: Exception) {
        }
        owner = null
        imageCapture = null
    }

    /** ImageProxy (JPEG) → herunterskalierte JPEG-Bytes, EXIF-Rotation korrigiert. */
    private fun toJpegBytes(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val raw = ByteArray(buffer.remaining()).also { buffer.get(it) }

        var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: return raw // Notfall: unskaliert weitergeben

        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }

        val longEdge = maxOf(bmp.width, bmp.height)
        if (longEdge > MAX_LONG_EDGE) {
            val scale = MAX_LONG_EDGE.toFloat() / longEdge
            bmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt(),
                (bmp.height * scale).toInt(),
                true,
            )
        }

        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    companion object {
        private const val TAG = "DocumentCamera"
        // Sonnet 5 kann hochauflösend (bis 2576px); 2000px reicht für Text und
        // hält Token/Latenz im Rahmen
        private const val MAX_LONG_EDGE = 2000
        private const val JPEG_QUALITY = 85
    }
}

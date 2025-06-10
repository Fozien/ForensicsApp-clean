package com.example.forensicsapp.cvcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.forensicsapp.R
import com.example.forensicsapp.yandexgpt.YandexGPTActivity
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var overlayView: OverlayView
    private lateinit var textViewHint: TextView
    private var isProcessing = false
    private var lastCaptureTime = 0L
    private var demoMode = false
    private var viewFinderWidth = 0
    private var viewFinderHeight = 0

    // Настройки детекции
    private companion object {
        const val REQUEST_CODE_PERMISSIONS = 10
        const val CAPTURE_DELAY = 2000L // 2 секунды стабильного положения
        const val IDEAL_FACE_WIDTH_RATIO = 0.7f // 70% ширины кадра
        const val MAX_HEAD_TILT = 10f // Максимальный допустимый наклон головы
        const val DEAD_ZONE_PERCENT = 0.1f // 10% мертвая зона по краям
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayView = findViewById(R.id.overlayView)
        textViewHint = findViewById(R.id.textViewHint)

        // Получаем размеры PreviewView после его отрисовки
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        viewFinder.post {
            viewFinderWidth = viewFinder.width
            viewFinderHeight = viewFinder.height
            // Передаем размеры в overlayView для правильного масштабирования
            overlayView.setViewFinderDimensions(viewFinderWidth, viewFinderHeight)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        val btnDemo = findViewById<Button>(R.id.btnDemo)

        // Обычный клик - переход в GPT
        btnDemo.setOnClickListener {
            val drawable = ContextCompat.getDrawable(this, R.drawable.sample_face) ?: return@setOnClickListener
            val bitmap = (drawable as BitmapDrawable).bitmap

            // Сохраняем как обычное фото
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(getExternalFilesDir("ForensicsApp"), "DEMO_$timestamp.jpg")

            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            // Переход в GPT
            val intent = Intent(this, YandexGPTActivity::class.java)
            intent.putExtra("imagePath", file.absolutePath)
            intent.putExtra("photoPath", file.absolutePath)
            startActivity(intent)
        }

        // Секретное долгое нажатие (2 секунды)
        btnDemo.setOnLongClickListener {
            demoMode = !demoMode // Переключаем режим

            if (demoMode) {
                textViewHint.text = "Идеальное положение!"
                overlayView.clearFaces() // Скрываем красный квадрат

                // Симулируем автосъемку
                val bitmap = captureBitmapFromPreview()
                if (bitmap != null) {
                    saveAndGoToGPT(bitmap)
                } else {
                    textViewHint.text = "Не удалось захватить изображение"
                }
            } else {
                textViewHint.text = "Расположите лицо в центре"
            }

            true
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Ошибка запуска камеры", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        isProcessing = true

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        // Сохраняем размеры изображения для корректного масштабирования
        val imageWidth = image.width
        val imageHeight = image.height

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        val detector = FaceDetection.getClient(options)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (demoMode) {
                    return@addOnSuccessListener
                }

                if (faces.isNotEmpty()) {
                    // Передаем размеры изображения для корректного масштабирования
                    overlayView.setImageDimensions(imageWidth, imageHeight, imageProxy.imageInfo.rotationDegrees)
                    overlayView.setFaces(faces)
                    updatePositionHints(faces[0], imageWidth, imageHeight)
                    checkAutoCapture(faces[0], imageWidth, imageHeight)
                } else {
                    textViewHint.text = "Лицо не найдено. Расположите лицо в центре"
                    overlayView.clearFaces()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Ошибка анализа лица", e)
                textViewHint.text = "Ошибка обнаружения лица"
            }
            .addOnCompleteListener {
                imageProxy.close()
                isProcessing = false
            }
    }

    private fun updatePositionHints(face: Face, imageWidth: Int, imageHeight: Int) {
        if (demoMode) {
            textViewHint.text = "Идеальное положение!"
            return
        }

        val hints = mutableListOf<String>()
        val bounds = face.boundingBox

        // Масштабируем координаты лица к размеру отображения
        val scaledFaceWidth = bounds.width() * (overlayView.width.toFloat() / imageWidth)
        val targetWidth = overlayView.width * IDEAL_FACE_WIDTH_RATIO

        // 1. Проверка размера лица (относительно масштабированной ширины)
        when {
            scaledFaceWidth < targetWidth * 0.9 -> hints.add("Подойдите ближе")
            scaledFaceWidth > targetWidth * 1.1 -> hints.add("Отойдите дальше")
        }

        // 2. Проверка центра - корректируем с учетом ориентации и масштабирования
        // Учтем, что лицо для фронтальной камеры ориентировано зеркально
        val scaledCenterX = bounds.centerX() * (overlayView.width.toFloat() / imageWidth)
        val screenCenterX = overlayView.width / 2

        // Определяем отклонение от центра (с учетом зеркального отображения)
        val xOffset = screenCenterX - scaledCenterX
        val deadZone = (overlayView.width * DEAD_ZONE_PERCENT).toInt()

        when {
            xOffset < -deadZone -> hints.add("Поверните лицо влево")
            xOffset > deadZone -> hints.add("Поверните лицо вправо")
        }

        // 3. Проверка наклона головы (здесь оставляем как есть, но инвертируем для фронтальной камеры)
        val angle = -face.headEulerAngleZ // Инвертируем для фронтальной камеры
        when {
            angle < -MAX_HEAD_TILT -> hints.add("Выровняйте голову: наклон влево ${"%.1f".format(abs(angle))}°")
            angle > MAX_HEAD_TILT -> hints.add("Выровняйте голову: наклон вправо ${"%.1f".format(angle)}°")
        }

        // Обновление подсказки
        textViewHint.text = if (hints.isEmpty()) {
            "Положение идеально!"
        } else {
            hints.joinToString("\n")
        }
    }

    private fun checkAutoCapture(face: Face, imageWidth: Int, imageHeight: Int) {
        if (demoMode || isPerfectPosition(face, imageWidth, imageHeight)) {
            if (System.currentTimeMillis() - lastCaptureTime > CAPTURE_DELAY) {
                lastCaptureTime = System.currentTimeMillis()
                runOnUiThread {
                    textViewHint.text = "Положение идеально! Делаем снимок..."
                    // Можно добавить логику автосъемки
                    val bitmap = captureBitmapFromPreview()
                    if (bitmap != null) {
                        saveAndGoToGPT(bitmap)
                    }
                }
            }
        } else {
            lastCaptureTime = System.currentTimeMillis()
        }
    }

    private fun isPerfectPosition(face: Face, imageWidth: Int, imageHeight: Int): Boolean {
        val bounds = face.boundingBox

        // Масштабирование координат лица
        val scaledFaceWidth = bounds.width() * (overlayView.width.toFloat() / imageWidth)
        val targetWidth = overlayView.width * IDEAL_FACE_WIDTH_RATIO

        // Проверка размера с учетом масштабирования
        if (scaledFaceWidth < targetWidth * 0.9f || scaledFaceWidth > targetWidth * 1.1f) {
            return false
        }

        // Проверка положения в центре с учетом масштабирования
        val scaledCenterX = bounds.centerX() * (overlayView.width.toFloat() / imageWidth)
        val screenCenterX = overlayView.width / 2
        val xOffset = screenCenterX - scaledCenterX
        val deadZone = (overlayView.width * DEAD_ZONE_PERCENT).toInt()

        if (abs(xOffset) > deadZone) {
            return false
        }

        // Проверка наклона головы с инвертированием для фронтальной камеры
        if (abs(-face.headEulerAngleZ) > MAX_HEAD_TILT) {
            return false
        }

        return true
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun saveAndGoToGPT(bitmap: Bitmap) {
        val filename = "face_${System.currentTimeMillis()}.jpg"
        val dir = File(getExternalFilesDir(null), "ForensicsApp")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)

        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()

            val intent = Intent(this, YandexGPTActivity::class.java)
            intent.putExtra("imagePath", file.absolutePath)
            intent.putExtra("photoPath", file.absolutePath)
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(intent)
            }, 2000)

        } catch (e: IOException) {
            Log.e("SaveImage", "Ошибка сохранения файла", e)
            textViewHint.text = "Ошибка сохранения фото"
        }
    }

    private fun captureBitmapFromPreview(): Bitmap {
        val previewView = findViewById<PreviewView>(R.id.viewFinder)
        return previewView.bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
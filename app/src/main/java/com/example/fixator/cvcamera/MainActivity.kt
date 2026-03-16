package com.example.fixator.cvcamera

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
import com.example.fixator.R
import com.example.fixator.yandexgpt.YandexGPTActivity
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
    private var viewFinderWidth = 0
    private var viewFinderHeight = 0

    private companion object {
        @Volatile private var isCaptured = false
        const val REQUEST_CODE_PERMISSIONS = 10
        const val CAPTURE_DELAY = 2000L

        // Целевой размер лица — 70% ширины кадра, допуск ±10%
        const val IDEAL_FACE_WIDTH_RATIO = 0.7f
        const val FACE_SIZE_TOLERANCE = 0.1f

        // Максимальный наклон головы по оси Z (крен)
        const val MAX_HEAD_TILT_Z = 10f

        // Максимальный поворот головы по оси Y (влево-вправо, рыскание)
        const val MAX_HEAD_TILT_Y = 15f

        // Допустимое смещение центра лица от центра кадра — 10% ширины/высоты
        const val MAX_CENTER_OFFSET_RATIO = 0.10f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayView = findViewById(R.id.overlayView)
        textViewHint = findViewById(R.id.textViewHint)

        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        viewFinder.post {
            viewFinderWidth = viewFinder.width
            viewFinderHeight = viewFinder.height
            overlayView.setViewFinderDimensions(viewFinderWidth, viewFinderHeight)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        val btnDemo = findViewById<Button>(R.id.btnDemo)

        btnDemo.setOnClickListener {
            val drawable = ContextCompat.getDrawable(this, R.drawable.sample_face) ?: return@setOnClickListener
            val bitmap = (drawable as BitmapDrawable).bitmap
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(getExternalFilesDir("ForensicsApp"), "DEMO_$timestamp.jpg")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            val intent = Intent(this, YandexGPTActivity::class.java)
            intent.putExtra("imagePath", file.absolutePath)
            intent.putExtra("photoPath", file.absolutePath)
            startActivity(intent)
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
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
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
        val imageWidth = image.width
        val imageHeight = image.height

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            // Включаем классификацию углов Эйлера — нужны Y и Z
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        val detector = FaceDetection.getClient(options)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    overlayView.setImageDimensions(imageWidth, imageHeight, imageProxy.imageInfo.rotationDegrees)
                    overlayView.setFaces(faces)
                    updatePositionHints(faces[0], imageWidth, imageHeight)
                    checkAutoCapture(faces[0], imageWidth, imageHeight)
                } else {
                    runOnUiThread {
                        textViewHint.text = "Лицо не найдено. Расположите лицо в центре"
                        overlayView.clearFaces()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Ошибка анализа лица", e)
                runOnUiThread { textViewHint.text = "Ошибка обнаружения лица" }
            }
            .addOnCompleteListener {
                imageProxy.close()
                isProcessing = false
            }
    }

    /**
     * Вычисляет масштабные коэффициенты с учётом поворота изображения.
     * При повороте 90°/270° ширина и высота изображения меняются местами.
     */
    private fun getScaleFactors(imageWidth: Int, imageHeight: Int, rotation: Int): Pair<Float, Float> {
        val vw = overlayView.width.toFloat()
        val vh = overlayView.height.toFloat()
        if (vw <= 0 || vh <= 0) return Pair(1f, 1f)

        return if (rotation == 90 || rotation == 270) {
            Pair(vw / imageHeight, vh / imageWidth)
        } else {
            Pair(vw / imageWidth, vh / imageHeight)
        }
    }

    private fun updatePositionHints(face: Face, imageWidth: Int, imageHeight: Int) {
        val hints = mutableListOf<String>()
        val bounds = face.boundingBox
        val rotation = overlayView.getImageRotation()
        val (scaleX, scaleY) = getScaleFactors(imageWidth, imageHeight, rotation)

        val vw = overlayView.width.toFloat()
        val vh = overlayView.height.toFloat()
        if (vw <= 0 || vh <= 0) return

        // --- Размер лица ---
        // При повороте 90/270 ширина bounding box в координатах изображения
        // соответствует высоте на экране, поэтому используем scaleX (уже учитывает поворот)
        val scaledFaceWidth = bounds.width() * scaleX
        val targetWidth = vw * IDEAL_FACE_WIDTH_RATIO

        when {
            scaledFaceWidth < targetWidth * (1f - FACE_SIZE_TOLERANCE) ->
                hints.add("Подойдите ближе")
            scaledFaceWidth > targetWidth * (1f + FACE_SIZE_TOLERANCE) ->
                hints.add("Отойдите дальше")
        }

        // --- Горизонтальное центрирование ---
        // Фронтальная камера: X инвертируется. Центр лица в координатах экрана:
        val screenFaceCenterX = vw - bounds.centerX() * scaleX
        val screenCenterX = vw / 2f
        val xOffset = screenFaceCenterX - screenCenterX
        val maxXOffset = vw * MAX_CENTER_OFFSET_RATIO

        when {
            xOffset < -maxXOffset -> hints.add("Сместите лицо вправо")
            xOffset > maxXOffset  -> hints.add("Сместите лицо влево")
        }

        // --- Вертикальное центрирование ---
        val screenFaceCenterY = bounds.centerY() * scaleY
        val screenCenterY = vh / 2f
        val yOffset = screenFaceCenterY - screenCenterY
        val maxYOffset = vh * MAX_CENTER_OFFSET_RATIO

        when {
            yOffset < -maxYOffset -> hints.add("Опустите камеру ниже")
            yOffset > maxYOffset  -> hints.add("Поднимите камеру выше")
        }

        // --- Наклон головы (крен, ось Z) ---
        // Для фронтальной камеры инвертируем знак угла
        val tiltZ = -face.headEulerAngleZ
        when {
            tiltZ < -MAX_HEAD_TILT_Z ->
                hints.add("Выровняйте голову: наклон вправо ${"%.1f".format(abs(tiltZ))}°")
            tiltZ > MAX_HEAD_TILT_Z ->
                hints.add("Выровняйте голову: наклон влево ${"%.1f".format(tiltZ)}°")
        }

        // --- Поворот головы влево-вправо (рыскание, ось Y) ---
        // headEulerAngleY: положительное = повёрнута вправо (от камеры), отрицательное = влево
        val tiltY = face.headEulerAngleY
        when {
            tiltY < -MAX_HEAD_TILT_Y ->
                hints.add("Повернитесь чуть правее")
            tiltY > MAX_HEAD_TILT_Y ->
                hints.add("Повернитесь чуть левее")
        }

        runOnUiThread {
            textViewHint.text = if (hints.isEmpty()) {
                "Положение идеально!"
            } else {
                hints.joinToString("\n")
            }
        }
    }

    private fun isPerfectPosition(face: Face, imageWidth: Int, imageHeight: Int): Boolean {
        val bounds = face.boundingBox
        val rotation = overlayView.getImageRotation()
        val (scaleX, scaleY) = getScaleFactors(imageWidth, imageHeight, rotation)

        val vw = overlayView.width.toFloat()
        val vh = overlayView.height.toFloat()

        // Защита от нулевых размеров view (ещё не отрисована)
        if (vw <= 0 || vh <= 0) return false

        // 1. Размер лица
        val scaledFaceWidth = bounds.width() * scaleX
        val targetWidth = vw * IDEAL_FACE_WIDTH_RATIO
        if (scaledFaceWidth < targetWidth * (1f - FACE_SIZE_TOLERANCE)) return false
        if (scaledFaceWidth > targetWidth * (1f + FACE_SIZE_TOLERANCE)) return false

        // 2. Горизонтальное центрирование (с учётом зеркала фронтальной камеры)
        val screenFaceCenterX = vw - bounds.centerX() * scaleX
        val xOffset = screenFaceCenterX - vw / 2f
        if (abs(xOffset) > vw * MAX_CENTER_OFFSET_RATIO) return false

        // 3. Вертикальное центрирование
        val screenFaceCenterY = bounds.centerY() * scaleY
        val yOffset = screenFaceCenterY - vh / 2f
        if (abs(yOffset) > vh * MAX_CENTER_OFFSET_RATIO) return false

        // 4. Крен (ось Z)
        if (abs(face.headEulerAngleZ) > MAX_HEAD_TILT_Z) return false

        // 5. Рыскание (ось Y) — анфас
        if (abs(face.headEulerAngleY) > MAX_HEAD_TILT_Y) return false

        return true
    }

    private fun checkAutoCapture(face: Face, imageWidth: Int, imageHeight: Int) {
        if (isPerfectPosition(face, imageWidth, imageHeight)) {
            if (!isCaptured && System.currentTimeMillis() - lastCaptureTime > CAPTURE_DELAY) {
                isCaptured = true  // выставляем ДО runOnUiThread — блокирует следующие кадры мгновенно
                runOnUiThread {
                    textViewHint.text = "Положение идеально! Делаем снимок..."
                    val bitmap = captureBitmapFromPreview()
                    if (bitmap != null) {
                        saveAndGoToGPT(bitmap)
                    } else {
                        // Если bitmap не удалось получить — сбрасываем флаг чтобы попробовать снова
                        isCaptured = false
                    }
                }
            }
        } else {
            lastCaptureTime = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        isCaptured = false
        lastCaptureTime = System.currentTimeMillis()
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
            runOnUiThread { textViewHint.text = "Ошибка сохранения фото" }
        }
    }

    private fun captureBitmapFromPreview(): Bitmap? {
        val previewView = findViewById<PreviewView>(R.id.viewFinder)
        val raw = previewView.bitmap ?: return null

        // Принудительно конвертируем в SOFTWARE-backed ARGB_8888
        // PdfDocument.Canvas не принимает HARDWARE bitmap
        return if (raw.config == Bitmap.Config.HARDWARE) {
            val soft = raw.copy(Bitmap.Config.ARGB_8888, false)
            raw.recycle()
            soft
        } else {
            raw
        }
    }
}
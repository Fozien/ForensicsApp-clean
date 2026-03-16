package com.example.fixator.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportGenerator {

    private const val TAG = "ReportGenerator"

    // A4 в points (72 dpi)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40

    // Максимальный размер длинной стороны bitmap при декодировании
    // Достаточно для PDF, не съедает память
    private const val MAX_BITMAP_SIDE = 1200

    /**
     * Генерирует PDF-отчёт и возвращает File.
     * Бросает исключение при ошибке — не глотает молча.
     */
    fun generateReport(context: Context, imageFile: File, description: String): Uri {
        Log.d(TAG, "generateReport() start")

        val pdfDocument = PdfDocument()
        val byteArray: ByteArray

        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            drawPage(page.canvas, imageFile, description)
            pdfDocument.finishPage(page)

            val stream = java.io.ByteArrayOutputStream()
            pdfDocument.writeTo(stream)
            byteArray = stream.toByteArray()

        } finally {
            pdfDocument.close()
        }

        val fileName = "report_${System.currentTimeMillis()}.pdf"
        return saveToDownloads(context, fileName, byteArray)
    }

    private fun saveToDownloads(context: Context, fileName: String, data: ByteArray): Uri {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ — через MediaStore, разрешения не нужны
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS + "/Fixator")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw IOException("MediaStore insert failed")

            resolver.openOutputStream(uri)?.use { it.write(data) }

            values.clear()
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            Log.d(TAG, "Saved to Downloads/Fixator/$fileName via MediaStore")
            uri

        } else {
            // Android 9 и ниже — напрямую в Downloads
            val dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS + "/Fixator"
            )
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(data) }

            Log.d(TAG, "Saved to ${file.absolutePath}")
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        }
    }

    // -------------------------------------------------------------------------
    // Отрисовка содержимого страницы
    // -------------------------------------------------------------------------

    private fun drawPage(canvas: Canvas, imageFile: File, description: String) {
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        var y = MARGIN.toFloat()

        // --- Заголовок ---
        val titlePaint = makePaint(size = 20f, bold = true)
        y = drawCenteredText(canvas, "КРИМИНАЛИСТИЧЕСКИЙ ОТЧЁТ", y + 30f, titlePaint)
        y += 10f

        // Разделительная линия под заголовком
        val linePaint = makePaint(size = 1f).apply { strokeWidth = 1f }
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, linePaint)
        y += 20f

        // --- Изображение ---
        y = drawImage(canvas, imageFile, y, contentWidth)

        // --- Описание ---
        val labelPaint = makePaint(size = 12f, bold = true)
        canvas.drawText("Описание происшествия:", MARGIN.toFloat(), y, labelPaint)
        y += 20f

        val bodyPaint = makePaint(size = 11f)
        for (line in wrapText(description, contentWidth.toFloat(), bodyPaint)) {
            if (y > PAGE_HEIGHT - MARGIN - 30f) break
            canvas.drawText(line, MARGIN.toFloat(), y, bodyPaint)
            y += 18f
        }

        // --- Дата ---
        val datePaint = makePaint(size = 10f).apply { color = Color.GRAY }
        val dateStr = "Дата составления: " +
                SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        // Рисуем дату внизу страницы независимо от текста
        val dateY = (PAGE_HEIGHT - MARGIN).toFloat()
        canvas.drawLine(MARGIN.toFloat(), dateY - 15f, (PAGE_WIDTH - MARGIN).toFloat(), dateY - 15f, linePaint)
        canvas.drawText(dateStr, MARGIN.toFloat(), dateY, datePaint)
    }

    // -------------------------------------------------------------------------
    // Загрузка и отрисовка изображения
    // -------------------------------------------------------------------------

    private fun drawImage(canvas: Canvas, imageFile: File, startY: Float, contentWidth: Int): Float {
        if (!imageFile.exists()) {
            Log.w(TAG, "Image file does not exist: ${imageFile.absolutePath}")
            return startY
        }

        val bitmap = loadBitmap(imageFile)
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode bitmap from ${imageFile.absolutePath}")
            return startY
        }

        Log.d(TAG, "Bitmap loaded: ${bitmap.width}x${bitmap.height}")

        try {
            val maxImageHeight = 260f
            val maxImageWidth = contentWidth.toFloat()

            val scale = minOf(
                maxImageWidth / bitmap.width.toFloat(),
                maxImageHeight / bitmap.height.toFloat()
            )
            val drawW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val drawH = (bitmap.height * scale).toInt().coerceAtLeast(1)

            // Центрируем по горизонтали
            val left = MARGIN + (contentWidth - drawW) / 2
            val destRect = Rect(left, startY.toInt(), left + drawW, startY.toInt() + drawH)

            canvas.drawBitmap(bitmap, null, destRect, null)
            Log.d(TAG, "Bitmap drawn at $destRect")

            return startY + drawH + 20f

        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Декодирует bitmap с:
     * 1. inSampleSize чтобы не грузить полный размер в память
     * 2. Коррекцией EXIF-поворота (снимки с камеры часто повёрнуты)
     */
    private fun loadBitmap(file: File): Bitmap? {
        // Шаг 1: читаем только размеры
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)

        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        Log.d(TAG, "Raw image size: ${rawW}x${rawH}")

        if (rawW <= 0 || rawH <= 0) {
            Log.e(TAG, "Invalid image dimensions: ${rawW}x${rawH}")
            return null
        }

        // Шаг 2: вычисляем subsampling
        var sampleSize = 1
        val longSide = maxOf(rawW, rawH)
        while (longSide / sampleSize > MAX_BITMAP_SIDE) {
            sampleSize *= 2
        }
        Log.d(TAG, "Using inSampleSize=$sampleSize")

        // Шаг 3: декодируем уменьшенный bitmap
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        if (bitmap == null) {
            Log.e(TAG, "BitmapFactory.decodeFile returned null")
            return null
        }

        // Шаг 4: читаем EXIF и поворачиваем если нужно
        return applyExifRotation(file, bitmap)
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val rotation = try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            Log.d(TAG, "EXIF orientation: $orientation")
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90    -> 90f
                ExifInterface.ORIENTATION_ROTATE_180   -> 180f
                ExifInterface.ORIENTATION_ROTATE_270   -> 270f
                ExifInterface.ORIENTATION_TRANSPOSE    -> 90f   // поворот + зеркало
                ExifInterface.ORIENTATION_TRANSVERSE   -> 270f  // поворот + зеркало
                else -> 0f
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF, assuming 0° rotation", e)
            0f
        }

        if (rotation == 0f) return bitmap

        Log.d(TAG, "Rotating bitmap by $rotation°")
        val matrix = Matrix().apply { postRotate(rotation) }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            rotated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate bitmap", e)
            bitmap  // возвращаем оригинал если поворот не удался
        }
    }

    // -------------------------------------------------------------------------
    // Вспомогательные методы
    // -------------------------------------------------------------------------

    private fun makePaint(size: Float, bold: Boolean = false) = Paint().apply {
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        color = Color.BLACK
        isAntiAlias = true
    }

    /** Рисует текст по центру, возвращает новый Y */
    private fun drawCenteredText(canvas: Canvas, text: String, y: Float, paint: Paint): Float {
        val x = (PAGE_WIDTH - paint.measureText(text)) / 2f
        canvas.drawText(text, x, y, paint)
        return y + paint.textSize + 4f
    }

    /** Разбивает текст на строки по ширине */
    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank()) return emptyList()

        val result = mutableListOf<String>()

        // Сначала разбиваем по реальным переносам строк
        for (paragraph in text.split("\n")) {
            val words = paragraph.trim().split(" ").filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                result.add("")
                continue
            }
            var current = ""
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) result.add(current)
                    // Если одно слово шире страницы — всё равно добавляем
                    current = word
                }
            }
            if (current.isNotEmpty()) result.add(current)
        }

        return result
    }
}
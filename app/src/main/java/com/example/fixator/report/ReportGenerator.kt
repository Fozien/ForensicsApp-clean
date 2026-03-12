package com.example.fixator.report

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 50
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    fun generateReport(context: Context, imageFile: File, description: String): File {
        val pdfDocument = PdfDocument()

        // Создаем страницу
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Настройка Paint для текста
        val titlePaint = Paint().apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.DEFAULT
            color = Color.BLACK
            isAntiAlias = true
        }

        var yPosition = MARGIN + 50f

        // Заголовок
        val title = "Криминалистический отчет"
        val titleWidth = titlePaint.measureText(title)
        val titleX = (PAGE_WIDTH - titleWidth) / 2
        canvas.drawText(title, titleX, yPosition, titlePaint)
        yPosition += 80f

        // Добавляем изображение
        if (imageFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                if (bitmap != null) {
                    // Масштабируем изображение
                    val maxImageWidth = CONTENT_WIDTH - 100
                    val maxImageHeight = 300f

                    val scaleFactor = minOf(
                        maxImageWidth / bitmap.width.toFloat(),
                        maxImageHeight / bitmap.height.toFloat()
                    )

                    val scaledWidth = (bitmap.width * scaleFactor).toInt()
                    val scaledHeight = (bitmap.height * scaleFactor).toInt()
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

                    // Центрируем изображение
                    val imageX = (PAGE_WIDTH - scaledWidth) / 2f
                    val destRect = RectF(imageX, yPosition, imageX + scaledWidth, yPosition + scaledHeight)
                    canvas.drawBitmap(scaledBitmap, null, destRect, null)

                    yPosition += scaledHeight + 40f

                    // Освобождаем память
                    if (scaledBitmap != bitmap) {
                        scaledBitmap.recycle()
                    }
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Добавляем описание
        val descriptionTitle = "Описание происшествия:"
        canvas.drawText(descriptionTitle, MARGIN.toFloat(), yPosition, bodyPaint)
        yPosition += 30f

        // Разбиваем описание на строки
        val lines = wrapText(description, CONTENT_WIDTH.toFloat(), bodyPaint)
        for (line in lines) {
            canvas.drawText(line, MARGIN.toFloat(), yPosition, bodyPaint)
            yPosition += 25f

            // Проверяем, не выходим ли за границы страницы
            if (yPosition > PAGE_HEIGHT - MARGIN) {
                break // В реальном приложении здесь можно добавить новую страницу
            }
        }

        // Добавляем дату создания
        yPosition += 40f
        val dateText = "Дата создания отчета: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
        canvas.drawText(dateText, MARGIN.toFloat(), yPosition, bodyPaint)

        // Завершаем страницу
        pdfDocument.finishPage(page)

        // Сохраняем файл
        val reportsDir = File(context.getExternalFilesDir(null), "ForensicsReports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val reportFile = File(reportsDir, "report_${System.currentTimeMillis()}.pdf")

        try {
            val outputStream = FileOutputStream(reportFile)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }

        return reportFile
    }

    /**
     * Разбивает текст на строки, чтобы он поместился в заданную ширину
     */
    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val textWidth = paint.measureText(testLine)

            if (textWidth <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    currentLine = word
                } else {
                    // Если одно слово не помещается, разбиваем его
                    lines.add(word)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }
}
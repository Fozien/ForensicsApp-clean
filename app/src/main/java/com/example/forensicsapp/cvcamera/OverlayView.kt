package com.example.forensicsapp.cvcamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.face.Face

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var faces: List<Face> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var rotation = 0
    private var viewFinderWidth = 0
    private var viewFinderHeight = 0

    // Масштабные коэффициенты
    private var scaleX = 1.0f
    private var scaleY = 1.0f

    // Устанавливаем размеры ViewFinder для правильного масштабирования
    fun setViewFinderDimensions(width: Int, height: Int) {
        viewFinderWidth = width
        viewFinderHeight = height
        calculateScaleFactors()
        invalidate()
    }

    // Устанавливаем размеры изображения и угол поворота
    fun setImageDimensions(width: Int, height: Int, rotationDegrees: Int) {
        imageWidth = width
        imageHeight = height
        rotation = rotationDegrees
        calculateScaleFactors()
    }

    // Вычисляем масштабные коэффициенты на основе поворота
    private fun calculateScaleFactors() {
        if (imageWidth <= 0 || imageHeight <= 0 || viewFinderWidth <= 0 || viewFinderHeight <= 0) {
            return
        }

        if (rotation == 90 || rotation == 270) {
            // Если изображение повернуто на 90 или 270 градусов, меняем местами ширину и высоту
            scaleX = viewFinderWidth.toFloat() / imageHeight
            scaleY = viewFinderHeight.toFloat() / imageWidth
        } else {
            scaleX = viewFinderWidth.toFloat() / imageWidth
            scaleY = viewFinderHeight.toFloat() / imageHeight
        }
    }

    // Устанавливаем обнаруженные лица
    fun setFaces(facesList: List<Face>) {
        faces = facesList
        invalidate()
    }

    // Очищаем обнаруженные лица
    fun clearFaces() {
        faces = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Если нет лиц или не инициализированы размеры, не рисуем ничего
        if (faces.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
            return
        }

        for (face in faces) {
            val bounds = face.boundingBox

            // Масштабируем координаты лица к размеру отображения
            val scaledLeft: Float
            val scaledTop: Float
            val scaledRight: Float
            val scaledBottom: Float

            // Обработка координат с учетом поворота и зеркального отображения для фронтальной камеры
            when (rotation) {
                0 -> {
                    // Зеркально отражаем для фронтальной камеры
                    scaledLeft = width - bounds.right * scaleX
                    scaledTop = bounds.top * scaleY
                    scaledRight = width - bounds.left * scaleX
                    scaledBottom = bounds.bottom * scaleY
                }
                90 -> {
                    // Для поворота 90 градусов
                    scaledLeft = bounds.top * scaleX
                    scaledTop = height - bounds.right * scaleY
                    scaledRight = bounds.bottom * scaleX
                    scaledBottom = height - bounds.left * scaleY
                }
                180 -> {
                    // Для поворота 180 градусов
                    scaledLeft = width - bounds.left * scaleX
                    scaledTop = height - bounds.top * scaleY
                    scaledRight = width - bounds.right * scaleX
                    scaledBottom = height - bounds.bottom * scaleY
                }
                270 -> {
                    // Для поворота 270 градусов
                    scaledLeft = width - bounds.bottom * scaleX
                    scaledTop = bounds.left * scaleY
                    scaledRight = width - bounds.top * scaleX
                    scaledBottom = bounds.right * scaleY
                }
                else -> {
                    // По умолчанию
                    scaledLeft = width - bounds.right * scaleX
                    scaledTop = bounds.top * scaleY
                    scaledRight = width - bounds.left * scaleX
                    scaledBottom = bounds.bottom * scaleY
                }
            }

            // Рисуем прямоугольник вокруг лица
            canvas.drawRect(
                scaledLeft,
                scaledTop,
                scaledRight,
                scaledBottom,
                paint
            )
        }
    }
}
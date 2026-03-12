package com.example.fixator.cvcamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.face.Face

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.GREEN          // Зелёный когда лицо найдено
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var faces: List<Face> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var rotationDegrees = 0
    private var viewFinderWidth = 0
    private var viewFinderHeight = 0

    private var scaleX = 1.0f
    private var scaleY = 1.0f

    fun setViewFinderDimensions(width: Int, height: Int) {
        viewFinderWidth = width
        viewFinderHeight = height
        calculateScaleFactors()
        invalidate()
    }

    fun setImageDimensions(width: Int, height: Int, rotation: Int) {
        imageWidth = width
        imageHeight = height
        rotationDegrees = rotation
        calculateScaleFactors()
    }

    /** Позволяет MainActivity использовать те же коэффициенты поворота */
    fun getImageRotation(): Int = rotationDegrees

    private fun calculateScaleFactors() {
        if (imageWidth <= 0 || imageHeight <= 0 || viewFinderWidth <= 0 || viewFinderHeight <= 0) return

        if (rotationDegrees == 90 || rotationDegrees == 270) {
            // Изображение повёрнуто: его ширина соответствует высоте экрана и наоборот
            scaleX = viewFinderWidth.toFloat() / imageHeight
            scaleY = viewFinderHeight.toFloat() / imageWidth
        } else {
            scaleX = viewFinderWidth.toFloat() / imageWidth
            scaleY = viewFinderHeight.toFloat() / imageHeight
        }
    }

    fun setFaces(facesList: List<Face>) {
        faces = facesList
        invalidate()
    }

    fun clearFaces() {
        faces = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (faces.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return

        for (face in faces) {
            val bounds = face.boundingBox

            // Координаты рамки в пространстве экрана с учётом поворота и зеркала
            val left: Float
            val top: Float
            val right: Float
            val bottom: Float

            when (rotationDegrees) {
                0 -> {
                    // Фронтальная камера при portrait: зеркалим по X
                    left   = width - bounds.right  * scaleX
                    top    = bounds.top            * scaleY
                    right  = width - bounds.left   * scaleX
                    bottom = bounds.bottom         * scaleY
                }
                90 -> {
                    left   = bounds.top            * scaleX
                    top    = height - bounds.right * scaleY
                    right  = bounds.bottom         * scaleX
                    bottom = height - bounds.left  * scaleY
                }
                180 -> {
                    left   = width - bounds.left   * scaleX
                    top    = height - bounds.top   * scaleY
                    right  = width - bounds.right  * scaleX
                    bottom = height - bounds.bottom * scaleY
                }
                270 -> {
                    left   = width - bounds.bottom * scaleX
                    top    = bounds.left           * scaleY
                    right  = width - bounds.top    * scaleX
                    bottom = bounds.right          * scaleY
                }
                else -> {
                    left   = width - bounds.right  * scaleX
                    top    = bounds.top            * scaleY
                    right  = width - bounds.left   * scaleX
                    bottom = bounds.bottom         * scaleY
                }
            }

            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
}
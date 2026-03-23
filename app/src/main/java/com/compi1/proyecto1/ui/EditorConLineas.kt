package com.compi1.proyecto1.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class EditorConLineas @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val rect = Rect()
    private val paint = Paint()

    init {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#AAAAAA")
        paint.textSize = textSize
        paint.isAntiAlias = true
        paint.typeface = Typeface.MONOSPACE

        setHorizontallyScrolling(true)

        // Deshabilitamos cualquier intento de elipsis (los tres puntitos ...)
        ellipsize = null

        typeface = Typeface.MONOSPACE

        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        setPadding(120, paddingTop, paddingRight, paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        var baseline = baseline

        for (i in 0 until lineCount) {
            val numeroLinea = (i + 1).toString()
            canvas.drawText(numeroLinea, 20f, baseline.toFloat(), paint)

            baseline += lineHeight
        }

        super.onDraw(canvas)
    }
}
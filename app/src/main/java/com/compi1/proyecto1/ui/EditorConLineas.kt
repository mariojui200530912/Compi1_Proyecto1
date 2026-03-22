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
        paint.color = Color.parseColor("#AAAAAA") // Un gris ligeramente más claro para que resalte
        paint.textSize = textSize
        paint.isAntiAlias = true
        paint.typeface = Typeface.MONOSPACE // Forzamos tipografía monospace para los números

        // --- 2. EL PARCHE DEFINITIVO CONTRA EL AJUSTE DE LÍNEA ---
        // Forzamos el scroll horizontal a nivel de código para asegurar que las líneas no bajen
        setHorizontallyScrolling(true)

        // Deshabilitamos cualquier intento de elipsis (los tres puntitos ...)
        ellipsize = null

        // Tipografía monospace obligatoria para el editor (¡Crucial para que los números cuadren!)
        typeface = Typeface.MONOSPACE

        // --- 3. CONFIGURACIÓN DE ENTRADA (Opcional, pero recomendado para un IDE) ---
        // Desactivamos autocorrect, sugerencias y forzamos multilínea
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        // --- 4. ESPACIADO DEL MARGEN IZQUIERDO ---
        // Empujamos el texto hacia la derecha para hacerle espacio a los números (puedes ajustar el 120)
        setPadding(120, paddingTop, paddingRight, paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        // Averiguamos dónde está la primera línea
        var baseline = baseline

        // Contamos cuántas líneas tiene el código en total
        for (i in 0 until lineCount) {
            val numeroLinea = (i + 1).toString()

            // Dibujamos el número en el margen izquierdo (posición X: 20f)
            canvas.drawText(numeroLinea, 20f, baseline.toFloat(), paint)

            // Bajamos a la siguiente línea
            baseline += lineHeight
        }

        super.onDraw(canvas)
    }
}
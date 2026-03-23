package com.compi1.proyecto1.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.compi1.proyecto1.modelos.*
import com.compi1.proyecto1.interprete.Evaluador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import com.google.gson.Gson
import com.google.gson.JsonObject

data class ValidadorPregunta(
    val tipoPregunta: String,
    val vistaEntrada: View,
    val respuestaCorrecta: Any?
)

class GeneradorVistas(
    private val context: Context,
    private val evaluador: Evaluador
) {
    private val validadores = mutableListOf<ValidadorPregunta>()

    fun generarFormulario(componentes: List<Instruccion>): View {
        val layoutPrincipal = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        for (comp in componentes) {
            if (comp is ComponenteVisual) {
                val vista = renderizarComponente(comp)
                if (vista != null) {
                    layoutPrincipal.addView(vista)
                    layoutPrincipal.addView(Space(context).apply { minimumHeight = 40 })
                }
            }
        }

        return layoutPrincipal
    }

    private fun renderizarComponente(comp: ComponenteVisual): View? {
        val vista: View = when (comp) {
            is Seccion -> crearVistaSeccion(comp)
            is TextoGeneral -> crearVistaTexto(comp)
            is Tabla -> crearVistaTabla(comp)
            is PreguntaAbierta -> crearVistaPreguntaAbierta(comp)
            is PreguntaSeleccion -> crearVistaPreguntaSeleccion(comp)
            is PreguntaDesplegable -> crearVistaPreguntaDesplegable(comp)
            is PreguntaMultiple -> crearVistaPreguntaMultiple(comp)
            else -> return null
        }

        aplicarEstilosBase(vista, comp)
        return vista
    }

    private fun crearVistaSeccion(seccion: Seccion): View {
        val layout = LinearLayout(context)
        layout.orientation = if (seccion.orientation == "HORIZONTAL") LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        for (elementoInterno in seccion.elementos) {
            if (elementoInterno is ComponenteVisual) {
                val vistaInterna = renderizarComponente(elementoInterno)
                if (vistaInterna != null) {
                    layout.addView(vistaInterna)
                }
            }
        }
        return layout
    }

    private fun crearVistaTexto(texto: TextoGeneral): View {
        val textView = TextView(context)
        textView.text = texto.content
        return textView
    }

    private fun crearVistaPreguntaAbierta(pregunta: PreguntaAbierta): View {
        val contenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(context).apply {
            text = pregunta.label
            setTypeface(null, Typeface.BOLD)
        }
        val campoTexto = EditText(context).apply {
            hint = "Escribe tu respuesta aquí..."
            setPadding(24, 24, 24, 24)
        }

        validadores.add(ValidadorPregunta("ABIERTA", campoTexto, null))

        contenedor.addView(label)
        contenedor.addView(campoTexto)
        return contenedor
    }

    private fun crearVistaPreguntaSeleccion(pregunta: PreguntaSeleccion): View {
        val contenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(context).apply {
            text = pregunta.label
            setTypeface(null, Typeface.BOLD)
        }

        if (pregunta.opciones.size > 5) {
            AlertDialog.Builder(context)
                .setTitle("Advertencia")
                .setMessage("La pregunta '${pregunta.label}' tiene ${pregunta.opciones.size} opciones. Se recomienda un máximo de 5.")
                .setPositiveButton("Entendido", null)
                .show()
        }

        val radioGroup = RadioGroup(context)

        for (opcion in pregunta.opciones) {
            val radioButton = RadioButton(context).apply { text = opcion }
            aplicarEstilosHijos(radioButton, pregunta.estilo)
            radioGroup.addView(radioButton)
        }

        val indiceCorrecto = pregunta.respuestaCorrecta?.let { (evaluador.evaluar(it) as? Double)?.toInt() }
        validadores.add(ValidadorPregunta("RADIO_GROUP", radioGroup, indiceCorrecto))

        contenedor.addView(label)
        contenedor.addView(radioGroup)
        return contenedor
    }

    private fun crearVistaPreguntaDesplegable(pregunta: PreguntaDesplegable): View {
        val contenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(context).apply {
            text = pregunta.label
            setTypeface(null, Typeface.BOLD)
        }

        val spinner = Spinner(context)
        val primeraOpcion = pregunta.opciones.firstOrNull()

        if (primeraOpcion is Expresion.LlamadaPokemon) {
            val inicio = (evaluador.evaluar(primeraOpcion.rangoInicio) as? Double)?.toInt() ?: 1
            val fin = (evaluador.evaluar(primeraOpcion.rangoFin) as? Double)?.toInt() ?: 10
            descargarPokemonAsincrono(spinner, inicio, fin)
        } else {
            val opcionesTexto = pregunta.opciones.mapNotNull { evaluador.evaluar(it)?.toString() }
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, opcionesTexto)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        val indiceCorrecto = pregunta.respuestaCorrecta?.let { (evaluador.evaluar(it) as? Double)?.toInt() }
        validadores.add(ValidadorPregunta("SPINNER", spinner, indiceCorrecto))

        contenedor.addView(label)
        contenedor.addView(spinner)
        return contenedor
    }

    private fun crearVistaPreguntaMultiple(pregunta: PreguntaMultiple): View {
        val contenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(context).apply {
            text = pregunta.label
            setTypeface(null, Typeface.BOLD)
        }

        val checkContenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        for (opcion in pregunta.opciones) {
            val checkBox = CheckBox(context).apply { text = opcion }
            aplicarEstilosHijos(checkBox, pregunta.estilo)
            checkContenedor.addView(checkBox)
        }

        val indicesCorrectos = pregunta.respuestasCorrectas.mapNotNull {
            (evaluador.evaluar(it) as? Double)?.toInt()
        }
        val correctosAEnviar = if (indicesCorrectos.isNotEmpty()) indicesCorrectos else null
        validadores.add(ValidadorPregunta("CHECKBOXES", checkContenedor, correctosAEnviar))

        contenedor.addView(label)
        contenedor.addView(checkContenedor)
        return contenedor
    }

    private fun crearVistaTabla(tabla: Tabla): View {
        // Contenedor principal de la tabla
        val layoutTabla = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (fila in tabla.filas) {
            // Cada fila de la matriz
            val layoutFila = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                // Esto obliga a que todas las filas midan exactamente lo mismo de alto.
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            for (celda in fila) {

                val contenedorCelda = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setPadding(16, 16, 16, 16)

                    val grosorAst = tabla.estilo?.bordeGrosor?.let { (evaluador.evaluar(it) as? Double)?.toInt() } ?: 3
                    val colorHex = tabla.estilo?.bordeColor ?: "BLACK"
                    val tipoBorde = tabla.estilo?.bordeTipo ?: "LINE"

                    val scale = context.resources.displayMetrics.density
                    val strokeWidth = (grosorAst * scale).toInt()
                    val strokeColor = traducirColor(colorHex)

                    val border = android.graphics.drawable.GradientDrawable()
                    border.setColor(Color.TRANSPARENT) // Fondo transparente

                    if (tipoBorde == "DOTTED") {
                        border.setStroke(strokeWidth, strokeColor, 10f * scale, 10f * scale)
                    } else {
                        border.setStroke(strokeWidth, strokeColor)
                    }

                    background = border
                }

                if (celda is ComponenteVisual) {
                    val vistaCelda = renderizarComponente(celda)
                    if (vistaCelda != null) {
                        val params = vistaCelda.layoutParams as LinearLayout.LayoutParams
                        params.setMargins(0, 0, 0, 0)
                        vistaCelda.layoutParams = params

                        contenedorCelda.addView(vistaCelda)
                    }
                }
                layoutFila.addView(contenedorCelda)
            }
            layoutTabla.addView(layoutFila)
        }
        return layoutTabla
    }

    // --- METODOS AUXILIARES DE ESTILO ---
    private fun aplicarEstilosBase(vista: View, comp: ComponenteVisual) {
        val scale = context.resources.displayMetrics.density

        val widthEval = comp.width?.let { evaluador.evaluar(it) as? Double }
        val heightEval = comp.height?.let { evaluador.evaluar(it) as? Double }

        val wPix = if (widthEval != null && widthEval > 0) {
            (widthEval * scale).toInt()
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }

        val hPix = if (heightEval != null && heightEval > 0) {
            (heightEval * scale).toInt()
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT // Se ajusta al contenido si no se especifica o es 0
        }

        val params = LinearLayout.LayoutParams(wPix, hPix)
        val margen = (8 * scale).toInt()
        params.setMargins(margen, margen, margen, margen)
        vista.layoutParams = params

        comp.estilo?.let { estilo ->
            estilo.colorFondo?.let { colorTxt -> vista.setBackgroundColor(traducirColor(colorTxt)) }
            val textViewPrincipal = if (vista is TextView) vista else if (vista is LinearLayout && vista.childCount > 0) vista.getChildAt(0) as? TextView else null

            if (textViewPrincipal != null) {
                estilo.colorTexto?.let { textViewPrincipal.setTextColor(traducirColor(it)) }
                estilo.tamanoTexto?.let {
                    val size = (evaluador.evaluar(it) as? Double)?.toFloat()
                    if (size != null) textViewPrincipal.textSize = size
                }
                estilo.familiaFuente?.let {
                    when (it.uppercase()) {
                        "MONO" -> textViewPrincipal.typeface = Typeface.MONOSPACE
                        "SANS_SERIF" -> textViewPrincipal.typeface = Typeface.SANS_SERIF
                        "CURSIVE" -> textViewPrincipal.typeface = Typeface.create("cursive", Typeface.NORMAL)
                    }
                }
            }
        }
    }

    // Funcion auxiliar para aplicarle el color y letra a los radiobuttons y checkboxes
    private fun aplicarEstilosHijos(vistaHijo: TextView, estilo: Estilo?) {
        if (estilo == null) return
        estilo.colorTexto?.let { vistaHijo.setTextColor(traducirColor(it)) }
        estilo.tamanoTexto?.let {
            val size = (evaluador.evaluar(it) as? Double)?.toFloat()
            if (size != null) vistaHijo.textSize = size - 2f
        }
        estilo.familiaFuente?.let {
            when (it.uppercase()) {
                "MONO" -> vistaHijo.typeface = Typeface.MONOSPACE
                "SANS_SERIF" -> vistaHijo.typeface = Typeface.SANS_SERIF
                "CURSIVE" -> vistaHijo.typeface = Typeface.create("cursive", Typeface.NORMAL)
            }
        }
    }

    private fun traducirColor(colorDef: String): Int {
        // Quitamos comillas por si vienen desde el AST como '"RED"'
        val limpio = colorDef.replace("\"", "").trim()

        return try {
            when (limpio.uppercase()) {
                "RED" -> Color.parseColor("#F44336")
                "BLUE" -> Color.parseColor("#2196F3")
                "GREEN" -> Color.parseColor("#4CAF50")
                "YELLOW" -> Color.parseColor("#FFEB3B")
                "BLACK" -> Color.parseColor("#000000")
                "WHITE" -> Color.parseColor("#FFFFFF")
                "PURPLE" -> Color.parseColor("#9C27B0")
                "SKY" -> Color.parseColor("#03A9F4")
                else -> {
                    if (limpio.startsWith("#")) Color.parseColor(limpio) else Color.TRANSPARENT
                }
            }
        } catch (e: Exception) {
            Color.TRANSPARENT
        }
    }

    private fun descargarPokemonAsincrono(spinner: Spinner, inicio: Int, fin: Int) {
        val adapterCargando = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("Cargando Pokémon..."))
        spinner.adapter = adapterCargando

        CoroutineScope(Dispatchers.IO).launch {
            val listaNombres = mutableListOf<String>()
            try {
                for (id in inicio..fin) {
                    val url = "https://pokeapi.co/api/v2/pokemon/$id"
                    val jsonString = URL(url).readText()
                    val jsonObject = Gson().fromJson(jsonString, JsonObject::class.java)
                    val nombre = jsonObject.get("name").asString
                    listaNombres.add(nombre.replaceFirstChar { it.uppercase() })
                }
                withContext(Dispatchers.Main) {
                    val adapterFinal = ArrayAdapter(context, android.R.layout.simple_spinner_item, listaNombres)
                    adapterFinal.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinner.adapter = adapterFinal
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val adapterError = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("Error de conexión"))
                    spinner.adapter = adapterError
                }
            }
        }
    }

    fun calificarFormulario() {
        var correctas = 0
        var totalEvaluables = 0

        for (validador in validadores) {
            if (validador.respuestaCorrecta == null) continue

            // Variables para la evaluación
            var esCorrecta = false

            when (validador.tipoPregunta) {
                "ABIERTA" -> {
                    totalEvaluables++
                    val input = (validador.vistaEntrada as EditText).text.toString().trim()
                    val esperada = validador.respuestaCorrecta.toString().trim()
                    if (input.equals(esperada, ignoreCase = true)) {
                        esCorrecta = true
                    }
                }
                "SPINNER" -> {
                    totalEvaluables++
                    val spinner = validador.vistaEntrada as Spinner
                    val indiceSeleccionado = spinner.selectedItemPosition
                    val indiceEsperado = validador.respuestaCorrecta as Int
                    if (indiceSeleccionado == (indiceEsperado - 1)) { // Ajuste típico de índice 1 a 0
                        esCorrecta = true
                    }
                }
                "RADIO_GROUP" -> {
                    totalEvaluables++
                    val radioGroup = validador.vistaEntrada as RadioGroup
                    val idSeleccionado = radioGroup.checkedRadioButtonId

                    if (idSeleccionado != -1) {
                        val botonSeleccionado = radioGroup.findViewById<RadioButton>(idSeleccionado)
                        val indiceSeleccionado = radioGroup.indexOfChild(botonSeleccionado)
                        val indiceEsperado = validador.respuestaCorrecta as Int
                        if (indiceSeleccionado == (indiceEsperado - 1)) { // Ajuste típico de índice 1 a 0
                            esCorrecta = true
                        }
                    }
                }
                "CHECKBOXES" -> {
                    totalEvaluables++
                    @Suppress("UNCHECKED_CAST")
                    val indicesEsperadosOriginales = validador.respuestaCorrecta as List<Int>
                    // Ajustamos a base 0
                    val indicesEsperados = indicesEsperadosOriginales.map { it - 1 }

                    val checkContenedor = validador.vistaEntrada as LinearLayout
                    val indicesSeleccionados = mutableListOf<Int>()

                    for (i in 0 until checkContenedor.childCount) {
                        val checkBox = checkContenedor.getChildAt(i) as CheckBox
                        if (checkBox.isChecked) {
                            indicesSeleccionados.add(i)
                        }
                    }

                    if (indicesSeleccionados.size == indicesEsperados.size && indicesSeleccionados.containsAll(indicesEsperados)) {
                        esCorrecta = true
                    }
                }
            }

            if (esCorrecta) {
                correctas++
            }
        }

        val mensaje = if (totalEvaluables > 0) {
            "Obtuviste $correctas de $totalEvaluables respuestas correctas."
        } else {
            "Gracias por llenar el formulario (No había respuestas a calificar)."
        }

        AlertDialog.Builder(context)
            .setTitle("Resultado")
            .setMessage(mensaje)
            .setPositiveButton("Cerrar", null)
            .show()
    }
}
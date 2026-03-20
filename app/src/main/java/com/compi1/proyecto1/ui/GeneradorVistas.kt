package com.compi1.proyecto1.ui

import android.content.Context
import android.graphics.Color
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
    val vistaEntrada: View, // Puede ser un EditText, un Spinner, etc.
    val respuestaCorrecta: Any? // String para texto, Int para índices, List<Int> para múltiples
)

class GeneradorVistas(
    private val context: Context,
    private val evaluador: Evaluador // Lo necesitamos para calcular width/height si son variables
) {
    private val validadores = mutableListOf<ValidadorPregunta>()
    fun generarFormulario(componentes: List<Instruccion>): View {
        // Lista maestra que recordará todas las preguntas del formulario

        val layoutPrincipal = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        // Renderizamos todo el AST
        for (comp in componentes) {
            // Revisamos si la instrucción es un componente que se puede dibujar
            if (comp is ComponenteVisual) {
                // Al entrar al 'if', Kotlin mágicamente convierte 'comp' a ComponenteVisual (Smart Cast)
                val vista = renderizarComponente(comp)
                if (vista != null) {
                    layoutPrincipal.addView(vista)
                    // Espaciador entre preguntas
                    layoutPrincipal.addView(Space(context).apply { minimumHeight = 40 })
                }
            }
        }

        // 2. ¡AGREGAMOS EL BOTÓN DE ENVIAR AL FINAL!
        val btnCalificar = Button(context).apply {
            text = "Enviar Respuestas"
            setBackgroundColor(Color.parseColor("#4CAF50")) // Color Verde
            setTextColor(Color.WHITE)
            textSize = 18f
            setOnClickListener {
                calificarFormulario() // Dispara la evaluación
            }
        }
        layoutPrincipal.addView(btnCalificar)

        return layoutPrincipal
    }

    private fun renderizarComponente(comp: ComponenteVisual): View? {
        val vista: View = when (comp) {
            is Seccion -> crearVistaSeccion(comp)
            is TextoGeneral -> crearVistaTexto(comp)
            is PreguntaAbierta -> crearVistaPreguntaAbierta(comp)
            is PreguntaSeleccion -> crearVistaPreguntaSeleccion(comp)
            is PreguntaDesplegable -> crearVistaPreguntaDesplegable(comp)
            is PreguntaMultiple -> crearVistaPreguntaMultiple(comp)
            else -> return null
        }

        // Una vez creada la vista base, le aplicamos los estilos genéricos (width, height, colores)
        aplicarEstilosBase(vista, comp)
        return vista
    }

    // --- 1. SECCIÓN (Contenedor) ---
    private fun crearVistaSeccion(seccion: Seccion): View {
        val layout = LinearLayout(context)

        // Orientación según el AST
        layout.orientation = if (seccion.orientation == "HORIZONTAL") {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }

        // Una sección contiene otros elementos adentro, así que llamamos a la recursividad
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

    // --- 2. TEXTO GENERAL ---
    private fun crearVistaTexto(texto: TextoGeneral): View {
        val textView = TextView(context)
        textView.text = texto.content
        return textView
    }

    // --- 3. PREGUNTA ABIERTA ---
    private fun crearVistaPreguntaAbierta(pregunta: PreguntaAbierta): View {
        // Creamos un contenedor vertical para agrupar el Label y el campo de texto
        val contenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(context).apply { text = pregunta.label }
        val campoTexto = EditText(context).apply {
            hint = "Escribe tu respuesta aquí..."
        }

        contenedor.addView(label)
        contenedor.addView(campoTexto)
        return contenedor
    }

    // --- 4. PREGUNTA DE SELECCIÓN (RadioButtons) ---
    private fun crearVistaPreguntaSeleccion(pregunta: PreguntaSeleccion): View {
        val contenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(context).apply { text = pregunta.label }

        // RadioGroup asegura que solo se pueda seleccionar una opción
        val radioGroup = RadioGroup(context)

        for (opcion in pregunta.opciones) {
            val radioButton = RadioButton(context).apply {
                text = opcion
            }
            radioGroup.addView(radioButton)
        }

        val indiceCorrecto = pregunta.respuestaCorrecta?.let { (evaluador.evaluar(it) as? Double)?.toInt() }
        // REGISTRAMOS LA PREGUNTA
        validadores.add(ValidadorPregunta("RADIO_GROUP", radioGroup, indiceCorrecto))

        contenedor.addView(label)
        contenedor.addView(radioGroup)
        return contenedor
    }

    private fun crearVistaPreguntaDesplegable(pregunta: PreguntaDesplegable): View {
        val contenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        // Etiqueta de la pregunta (con emojis traducidos si tienes la función)
        val label = TextView(context).apply { text = pregunta.label }

        // El Spinner es el componente nativo de Android para listas desplegables
        val spinner = Spinner(context)

        // Revisamos cuál es la primera opción que nos mandó el parser
        val primeraOpcion = pregunta.opciones.firstOrNull()

        if (primeraOpcion is Expresion.LlamadaPokemon) {
            // ¡Es una llamada a la PokeAPI!
            // Evaluamos los rangos (por si el usuario escribió algo como 5 + 5)
            val inicio = (evaluador.evaluar(primeraOpcion.rangoInicio) as? Double)?.toInt() ?: 1
            val fin = (evaluador.evaluar(primeraOpcion.rangoFin) as? Double)?.toInt() ?: 10

            // Llamamos a la función asíncrona que descarga los datos
            descargarPokemonAsincrono(spinner, inicio, fin)

        } else {
            val opcionesTexto = pregunta.opciones.mapNotNull { evaluador.evaluar(it)?.toString() }
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, opcionesTexto)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        // 3. Evaluamos la respuesta correcta (índice)
        val indiceCorrecto = pregunta.respuestaCorrecta?.let { (evaluador.evaluar(it) as? Double)?.toInt() }

        // 4. AHORA SÍ lo registramos (porque el spinner ya existe)
        validadores.add(ValidadorPregunta("SPINNER", spinner, indiceCorrecto))

        contenedor.addView(label)
        contenedor.addView(spinner)
        return contenedor
    }

    private fun crearVistaPreguntaMultiple(pregunta: PreguntaMultiple): View {
        val contenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(context).apply { text = pregunta.label }

        // Un contenedor normal para los CheckBoxes (aquí sí se pueden seleccionar varios)
        val checkContenedor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        for (opcion in pregunta.opciones) {
            val checkBox = CheckBox(context).apply {
                text = opcion
            }
            checkContenedor.addView(checkBox)
        }

        // Para selección múltiple, el "correct" es una LISTA de índices
        val indicesCorrectos = pregunta.respuestasCorrectas.mapNotNull {
            (evaluador.evaluar(it) as? Double)?.toInt()
        }

        // Registramos (pasamos el contenedor de los checkboxes y la lista de correctos)
        val correctosAEnviar = if (indicesCorrectos.isNotEmpty()) indicesCorrectos else null
        validadores.add(ValidadorPregunta("CHECKBOXES", checkContenedor, correctosAEnviar))

        contenedor.addView(label)
        contenedor.addView(checkContenedor)
        return contenedor
    }

    // --- MÉTODOS AUXILIARES DE ESTILO ---
    private fun aplicarEstilosBase(vista: View, comp: ComponenteVisual) {
        // 1. Dimensiones (width y height)
        // Evaluamos la expresión por si el usuario escribió "width: miVar + 10"
        val widthEval = comp.width?.let { evaluador.evaluar(it) as? Double }
        val heightEval = comp.height?.let { evaluador.evaluar(it) as? Double }

        val params = LinearLayout.LayoutParams(
            widthEval?.toInt() ?: ViewGroup.LayoutParams.MATCH_PARENT,
            heightEval?.toInt() ?: ViewGroup.LayoutParams.WRAP_CONTENT
        )
        // Añadimos un pequeño margen por defecto para que no se peguen los elementos
        params.setMargins(16, 16, 16, 16)
        vista.layoutParams = params

        // 2. Estilos personalizados
        comp.estilo?.let { estilo ->
            // Color de fondo
            estilo.colorFondo?.let { colorTxt ->
                vista.setBackgroundColor(traducirColor(colorTxt))
            }

            // Si es un TextView (o hereda de él como EditText o RadioButton), le aplicamos color de letra
            if (vista is TextView && estilo.colorTexto != null) {
                vista.setTextColor(traducirColor(estilo.colorTexto!!))
            }

            // Aquí puedes agregar la lógica para fontSize, fontFamily, borders, etc.
        }
    }

    private fun traducirColor(colorDef: String): Int {
        return try {
            when (colorDef.uppercase()) {
                "RED" -> Color.RED
                "BLUE" -> Color.BLUE
                "GREEN" -> Color.GREEN
                "YELLOW" -> Color.YELLOW
                "BLACK" -> Color.BLACK
                "WHITE" -> Color.WHITE
                // Para HEX como #FF0000
                else -> if (colorDef.startsWith("#")) Color.parseColor(colorDef) else Color.TRANSPARENT
            }
        } catch (e: Exception) {
            Color.TRANSPARENT // Si hay un error, lo dejamos transparente
        }
    }

    private fun descargarPokemonAsincrono(spinner: Spinner, inicio: Int, fin: Int) {
        // Ponemos un texto temporal mientras descarga
        val adapterCargando = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("Cargando Pokémon..."))
        spinner.adapter = adapterCargando

        // Iniciamos un hilo en segundo plano (Dispatchers.IO)
        CoroutineScope(Dispatchers.IO).launch {
            val listaNombres = mutableListOf<String>()

            try {
                // Iteramos desde el inicio hasta el fin del rango
                for (id in inicio..fin) {
                    val url = "https://pokeapi.co/api/v2/pokemon/$id"

                    // Hacemos la petición HTTP simple
                    val jsonString = URL(url).readText()

                    // Usamos Gson para leer el JSON y sacar solo el "name"
                    val jsonObject = Gson().fromJson(jsonString, JsonObject::class.java)
                    val nombre = jsonObject.get("name").asString

                    // Lo ponemos con mayúscula inicial y lo agregamos a la lista
                    listaNombres.add(nombre.replaceFirstChar { it.uppercase() })
                }

                // Una vez que descargamos todos, volvemos al hilo principal (UI Thread) para dibujar
                withContext(Dispatchers.Main) {
                    val adapterFinal = ArrayAdapter(context, android.R.layout.simple_spinner_item, listaNombres)
                    adapterFinal.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinner.adapter = adapterFinal
                }

            } catch (e: Exception) {
                // Si no hay internet o el rango es inválido
                withContext(Dispatchers.Main) {
                    val adapterError = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("Error de conexión"))
                    spinner.adapter = adapterError
                }
            }
        }
    }

    private fun calificarFormulario() {
        var correctas = 0
        var totalEvaluables = 0

        for (validador in validadores) {
            if (validador.respuestaCorrecta == null) continue

            totalEvaluables++
            var esCorrecta = false

            when (validador.tipoPregunta) {
                "ABIERTA" -> {
                    val input = (validador.vistaEntrada as EditText).text.toString().trim()
                    val esperada = validador.respuestaCorrecta.toString().trim()
                    if (input.equals(esperada, ignoreCase = true)) {
                        esCorrecta = true
                    }
                }

                "SPINNER" -> {
                    val spinner = validador.vistaEntrada as Spinner
                    val indiceSeleccionado = spinner.selectedItemPosition
                    val indiceEsperado = validador.respuestaCorrecta as Int
                    if (indiceSeleccionado == indiceEsperado) {
                        esCorrecta = true
                    }
                }

                "RADIO_GROUP" -> {
                    val radioGroup = validador.vistaEntrada as RadioGroup
                    // Obtenemos qué RadioButton está seleccionado
                    val idSeleccionado = radioGroup.checkedRadioButtonId

                    if (idSeleccionado != -1) {
                        // Buscamos qué índice tiene ese botón dentro del grupo
                        val botonSeleccionado = radioGroup.findViewById<RadioButton>(idSeleccionado)
                        val indiceSeleccionado = radioGroup.indexOfChild(botonSeleccionado)
                        val indiceEsperado = validador.respuestaCorrecta as Int

                        if (indiceSeleccionado == indiceEsperado) {
                            esCorrecta = true
                        }
                    }
                }

                "CHECKBOXES" -> {
                    @Suppress("UNCHECKED_CAST")
                    val indicesEsperados = validador.respuestaCorrecta as List<Int>
                    val checkContenedor = validador.vistaEntrada as LinearLayout

                    val indicesSeleccionados = mutableListOf<Int>()

                    // Revisamos cuáles están marcados
                    for (i in 0 until checkContenedor.childCount) {
                        val checkBox = checkContenedor.getChildAt(i) as CheckBox
                        if (checkBox.isChecked) {
                            indicesSeleccionados.add(i)
                        }
                    }

                    // Es correcta si seleccionó los mismos y la misma cantidad (sin importar el orden)
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

        Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
    }
}
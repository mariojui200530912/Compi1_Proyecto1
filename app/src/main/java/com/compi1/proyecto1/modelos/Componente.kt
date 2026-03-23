package com.compi1.proyecto1.modelos

data class Estilo(
    var colorTexto: String? = null,
    var colorFondo: String? = null,
    var familiaFuente: String? = null,
    var tamanoTexto: Expresion? = null,
    var bordeGrosor: Expresion? = null,
    var bordeTipo: String? = null,
    var bordeColor: String? = null
)

sealed class ComponenteVisual : Instruccion() {
    var width: Expresion? = null
    var height: Expresion? = null
    var estilo: Estilo? = null
}

// --- Contenedores ---
data class Seccion(
    var pointX: Expresion? = null,
    var pointY: Expresion? = null,
    var orientation: String = "VERTICAL",
    var elementos: List<Instruccion> = emptyList() // Puede contener otras secciones, preguntas, IFs, FORs...
) : ComponenteVisual()

data class Tabla(
    var pointX: Expresion? = null,
    var pointY: Expresion? = null,
    var filas: List<List<Instruccion>> = emptyList() // Matriz de elementos
) : ComponenteVisual()

// --- Elementos Finales (Hojas) ---
data class TextoGeneral(var content: String) : ComponenteVisual()

sealed class Pregunta(var label: String) : ComponenteVisual()

class PreguntaAbierta(label: String) : Pregunta(label)

class PreguntaDesplegable(
    label: String,
    var opciones: List<Expresion>,
    var respuestaCorrecta: Expresion? = null
) : Pregunta(label)

class PreguntaSeleccion(
    label: String,
    var opciones: List<String>,
    var respuestaCorrecta: Expresion? = null
) : Pregunta(label)

class PreguntaMultiple(
    label: String,
    var opciones: List<String>,
    var respuestasCorrectas: List<Expresion> = emptyList()
) : Pregunta(label)
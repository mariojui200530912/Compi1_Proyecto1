package com.compi1.proyecto1.interprete

import com.compi1.proyecto1.modelos.*
import com.compi1.proyecto1.interprete.Evaluador
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportadorPKM(private val evaluador: Evaluador) {

    // Contadores para los Metadatos
    private var totalSecciones = 0
    private var totalPreguntas = 0
    private var abiertas = 0
    private var desplegables = 0
    private var seleccion = 0
    private var multiples = 0

    fun generarArchivoPKM(componentes: List<Instruccion>, autor: String, descripcion: String): String {
        // 1. Reiniciamos contadores
        totalSecciones = 0
        totalPreguntas = 0
        abiertas = 0
        desplegables = 0
        seleccion = 0
        multiples = 0

        // 2. Generamos el cuerpo primero (para que los contadores se llenen)
        val cuerpoBuilder = java.lang.StringBuilder()
        for (comp in componentes) {
            if (comp is ComponenteVisual) {
                cuerpoBuilder.append(generarComponente(comp, 0))
            }
        }

        // 3. Generamos los Metadatos ahora que tenemos los totales
        val fechaActual = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val horaActual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val metaBuilder = java.lang.StringBuilder()
        metaBuilder.append("###\n")
        metaBuilder.append("  Author: $autor\n")
        metaBuilder.append("  Fecha: $fechaActual\n")
        metaBuilder.append("  Hora: $horaActual\n")
        metaBuilder.append("  Description: $descripcion\n")
        metaBuilder.append("  Total de Secciones: $totalSecciones\n")
        metaBuilder.append("  Total de Preguntas: $totalPreguntas\n")
        metaBuilder.append("      Abiertas: $abiertas\n")
        metaBuilder.append("      Desplegables: $desplegables\n")
        metaBuilder.append("      Selección: $seleccion\n")
        metaBuilder.append("      Múltiples: $multiples\n")
        metaBuilder.append("###\n\n")

        // 4. Unimos metadatos y cuerpo
        return metaBuilder.toString() + cuerpoBuilder.toString()
    }

    private fun generarComponente(comp: ComponenteVisual, nivel: Int): String {
        val tab = "  ".repeat(nivel)
        val sb = java.lang.StringBuilder()

        // Evaluamos dimensiones (por si venían como sumas o variables en el código original)
        val w = eval(comp.width) ?: 0
        val h = eval(comp.height) ?: 0
        val estilos = generarEstilos(comp.estilo, tab + "  ")

        when (comp) {
            is Seccion -> {
                totalSecciones++
                val pX = eval(comp.pointX) ?: 0
                val pY = eval(comp.pointY) ?: 0
                val orient = comp.orientation ?: "VERTICAL"

                sb.append("$tab<section=$w,$h,$pX,$pY,$orient>\n")
                if (estilos.isNotEmpty()) sb.append(estilos)

                sb.append("$tab  <content>\n")
                for (hijo in comp.elementos) {
                    if (hijo is ComponenteVisual) {
                        sb.append(generarComponente(hijo, nivel + 2))
                    }
                }
                sb.append("$tab  </content>\n")
                sb.append("$tab</section>\n\n")
            }

            is Tabla -> {
                sb.append("$tab<table>\n")
                if (estilos.isNotEmpty()) sb.append(estilos)
                sb.append("$tab  <content>\n")

                for (fila in comp.filas) {
                    sb.append("$tab    <line>\n")
                    for (celda in fila) {
                        sb.append("$tab      <element>\n")
                        if (celda is ComponenteVisual) {
                            // Recursividad para el elemento dentro de la celda
                            sb.append(generarComponente(celda, nivel + 4))
                        }
                        sb.append("$tab      </element>\n")
                    }
                    sb.append("$tab    </line>\n")
                }
                sb.append("$tab  </content>\n")
                sb.append("$tab</table>\n\n")
            }

            is TextoGeneral -> {
                val txt = revertirEmojis(comp.content)
                if (estilos.isEmpty()) {
                    sb.append("$tab<open=$w,$h,\"$txt\"/>\n")
                } else {
                    sb.append("$tab<open=$w,$h,\"$txt\">\n")
                    sb.append(estilos)
                    sb.append("$tab</open>\n")
                }
            }

            is PreguntaAbierta -> {
                totalPreguntas++; abiertas++
                val lbl = revertirEmojis(comp.label)
                if (estilos.isEmpty()) {
                    sb.append("$tab<open=$w,$h,\"$lbl\"/>\n")
                } else {
                    sb.append("$tab<open=$w,$h,\"$lbl\">\n")
                    sb.append(estilos)
                    sb.append("$tab</open>\n")
                }
            }

            is PreguntaDesplegable -> {
                totalPreguntas++; desplegables++
                val lbl = revertirEmojis(comp.label)
                val ops = formatOpciones(comp.opciones)
                val corr = eval(comp.respuestaCorrecta) ?: -1

                if (estilos.isEmpty()) {
                    sb.append("$tab<drop=$w,$h,\"$lbl\",$ops,$corr/>\n")
                } else {
                    sb.append("$tab<drop=$w,$h,\"$lbl\",$ops,$corr>\n")
                    sb.append(estilos)
                    sb.append("$tab</drop>\n")
                }
            }

            is PreguntaSeleccion -> {
                totalPreguntas++; seleccion++
                val lbl = revertirEmojis(comp.label)
                // Opciones en PreguntaSeleccion son List<String>
                val ops = "{" + comp.opciones.joinToString(",") { "\"$it\"" } + "}"
                val corr = eval(comp.respuestaCorrecta) ?: -1

                if (estilos.isEmpty()) {
                    sb.append("$tab<select=$w,$h,\"$lbl\",$ops,$corr/>\n")
                } else {
                    sb.append("$tab<select=$w,$h,\"$lbl\",$ops,$corr>\n")
                    sb.append(estilos)
                    sb.append("$tab</select>\n")
                }
            }

            is PreguntaMultiple -> {
                totalPreguntas++; multiples++
                val lbl = revertirEmojis(comp.label)
                val ops = "{" + comp.opciones.joinToString(",") { "\"$it\"" } + "}"

                // Formatear lista de respuestas correctas
                val corrList = comp.respuestasCorrectas.mapNotNull { eval(it) }
                val corrFormat = if (corrList.isEmpty()) "{}" else "{" + corrList.joinToString(",") + "}"

                if (estilos.isEmpty()) {
                    sb.append("$tab<multiple=$w,$h,\"$lbl\",$ops,$corrFormat/>\n")
                } else {
                    sb.append("$tab<multiple=$w,$h,\"$lbl\",$ops,$corrFormat>\n")
                    sb.append(estilos)
                    sb.append("$tab</multiple>\n")
                }
            }
        }
        return sb.toString()
    }

    // --- FUNCIONES AUXILIARES ---

    private fun generarEstilos(estilo: Estilo?, tab: String): String {
        if (estilo == null) return ""
        val sb = java.lang.StringBuilder()
        sb.append("$tab<style>\n")

        estilo.colorTexto?.let { sb.append("$tab  <color=$it/>\n") }
        estilo.colorFondo?.let { sb.append("$tab  <background color=$it/>\n") }
        estilo.familiaFuente?.let { sb.append("$tab  <font family=$it/>\n") }
        estilo.tamanoTexto?.let { sb.append("$tab  <text size=${eval(it)}/>\n") }
        // Aquí podrías agregar borders si lo tienes implementado

        sb.append("$tab</style>\n")
        return sb.toString()
    }

    // Traduce las listas de expresiones a strings formato {"op1", "op2"}
    private fun formatOpciones(opciones: List<Expresion>): String {
        // Validamos si es una llamada a PokeAPI
        val primera = opciones.firstOrNull()
        if (primera is Expresion.LlamadaPokemon) {
            val i = eval(primera.rangoInicio) ?: 1
            val f = eval(primera.rangoFin) ?: 10
            return "WHO_IS_THAT_POKEMON(number, $i, $f)" // Según tu sintaxis original
        }

        val textos = opciones.map { evalString(it) }
        return "{" + textos.joinToString(",") { "\"$it\"" } + "}"
    }

    private fun eval(exp: Expresion?): Int? {
        if (exp == null) return null
        return (evaluador.evaluar(exp) as? Double)?.toInt()
    }

    private fun evalString(exp: Expresion?): String {
        if (exp == null) return ""
        return evaluador.evaluar(exp)?.toString() ?: ""
    }

    // Requisito del enunciado: Los emojis no se guardan como caracteres, sino como etiquetas
    private fun revertirEmojis(texto: String): String {
        var txt = texto
        txt = txt.replace("❤️", "@[:heart:]")
        txt = txt.replace("⭐", "@[:star:]")
        txt = txt.replace("😄", "@[:smile:]")
        txt = txt.replace("😢", "@[:sad:]")
        txt = txt.replace("😐", "@[:serious:]")
        txt = txt.replace("🐱", "@[:cat:]")
        txt = txt.replace("😘", "@[:<<<<33333:]")
        return txt
    }
}
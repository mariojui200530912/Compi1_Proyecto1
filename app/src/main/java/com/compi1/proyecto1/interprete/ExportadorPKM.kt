package com.compi1.proyecto1.interprete

import com.compi1.proyecto1.modelos.*
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

        val cuerpoBuilder = StringBuilder()
        for (comp in componentes) {
            if (comp is ComponenteVisual) {
                cuerpoBuilder.append(generarComponente(comp, 0))
            }
        }

        val fechaActual = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val horaActual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val metaBuilder = StringBuilder()
        metaBuilder.append("###\n")
        metaBuilder.append("    Author: $autor\n")
        metaBuilder.append("    Fecha: $fechaActual\n")
        metaBuilder.append("    Hora: $horaActual\n")
        metaBuilder.append("    Description: $descripcion\n")
        metaBuilder.append("    Total de Secciones: $totalSecciones\n")
        metaBuilder.append("    Total de Preguntas: $totalPreguntas\n")
        metaBuilder.append("        Abiertas: $abiertas\n")
        metaBuilder.append("        Desplegables: $desplegables\n")
        metaBuilder.append("        Selección: $seleccion\n")
        metaBuilder.append("        Múltiples: $multiples\n")
        metaBuilder.append("###\n\n")

        // Unimos metadatos y cuerpo
        return metaBuilder.toString() + cuerpoBuilder.toString()
    }

    private fun generarComponente(comp: ComponenteVisual, nivel: Int): String {
        val tab = "  ".repeat(nivel)
        val sb = StringBuilder()

        val evalW = eval(comp.width)
        val w = if (evalW != null && evalW > 0) evalW else 400

        val evalH = eval(comp.height)
        val h = if (evalH != null && evalH > 0) evalH else 150

        val estilos = generarEstilos(comp.estilo, tab + "  ")

        when (comp) {
            is Seccion -> {
                totalSecciones++
                val pX = eval(comp.pointX) ?: 20
                val pY = eval(comp.pointY) ?: 20
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
                // ✨ Valores por defecto para posición en lugar de 0
                val pX = eval(comp.pointX) ?: 20
                val pY = eval(comp.pointY) ?: 20

                sb.append("$tab<table=$w,$h,$pX,$pY>\n")

                if (estilos.isNotEmpty()) sb.append(estilos)
                sb.append("$tab  <content>\n")

                for (fila in comp.filas) {
                    sb.append("$tab    <line>\n")
                    for (celda in fila) {
                        sb.append("$tab      <element>\n")
                        if (celda is ComponenteVisual) {
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
                    sb.append("$tab<text=$w,$h,\"$txt\"/>\n")
                } else {
                    sb.append("$tab<text=$w,$h,\"$txt\">\n")
                    sb.append(estilos)
                    sb.append("$tab</text>\n")
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
        val sb = StringBuilder()
        sb.append("$tab<style>\n")

        // ✨ ETIQUETAS EXACTAS (con espacios, sin comillas en los valores para que Lexer no falle)
        estilo.colorTexto?.let { sb.append("$tab  <color=${it.replace("\"", "")}/>\n") }
        estilo.colorFondo?.let { sb.append("$tab  <background color=${it.replace("\"", "")}/>\n") }
        estilo.familiaFuente?.let { sb.append("$tab  <font family=${it.replace("\"", "")}/>\n") }
        estilo.tamanoTexto?.let { sb.append("$tab  <text size=${eval(it)}/>\n") }

        if (estilo.bordeGrosor != null && estilo.bordeTipo != null && estilo.bordeColor != null) {
            val grosor = eval(estilo.bordeGrosor) ?: 3
            val tipo = estilo.bordeTipo?.replace("\"", "")
            val color = estilo.bordeColor?.replace("\"", "")
            sb.append("$tab  <border,$grosor,$tipo,$color/>\n")
        }

        sb.append("$tab</style>\n")
        return sb.toString()
    }

    private fun formatOpciones(opciones: List<Expresion>): String {
        val primera = opciones.firstOrNull()

        if (primera is Expresion.LlamadaPokemon) {
            val i = eval(primera.rangoInicio) ?: 1
            val f = eval(primera.rangoFin) ?: 10

            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            val future = executor.submit(java.util.concurrent.Callable {
                val nombres = mutableListOf<String>()
                for (id in i..f) {
                    try {
                        val url = java.net.URL("https://pokeapi.co/api/v2/pokemon/$id")
                        val con = url.openConnection() as java.net.HttpURLConnection
                        con.requestMethod = "GET"
                        val res = con.inputStream.bufferedReader().use { it.readText() }

                        val name = org.json.JSONObject(res).getString("name")
                        nombres.add("\"$name\"")
                    } catch (e: Exception) {
                        nombres.add("\"Pokemon $id\"")
                    }
                }
                nombres.joinToString(",")
            })

            // Se espera procesar todo
            val nombresUnidos = future.get()
            executor.shutdown()

            return "{$nombresUnidos}"
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

    private fun revertirEmojis(texto: String): String {
        var txt = texto
        txt = txt.replace("❤️", "@[:heart:]")
        txt = txt.replace("⭐", "@[:star:]")
        txt = txt.replace("😀", "@[:smile:]") // Cambiado a la carita real que usa JFlex
        txt = txt.replace("😢", "@[:sad:]")
        txt = txt.replace("😐", "@[:serious:]")
        txt = txt.replace("🐱", "@[:cat:]")
        txt = txt.replace("🙂", "@[:-)]")
        txt = txt.replace("😲", "@[:0]")
        return txt
    }
}
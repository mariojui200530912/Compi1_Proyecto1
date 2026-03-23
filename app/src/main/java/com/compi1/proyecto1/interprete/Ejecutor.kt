package com.compi1.proyecto1.interprete

import com.compi1.proyecto1.modelos.*

class Ejecutor(private val tabla: TablaSimbolos, private val evaluador: Evaluador) {

    fun ejecutar(instrucciones: List<Instruccion>): List<ComponenteVisual> {
        val componentesA_Dibujar = mutableListOf<ComponenteVisual>()

        for (instruccion in instrucciones) {
            when (instruccion) {
                // --- MANEJO DE VARIABLES ---
                is DeclaracionVariable -> {
                    val valor = instruccion.valorInicial?.let { evaluador.evaluar(it) }
                    tabla.declararVariable(instruccion.tipo, instruccion.nombre, valor ?: 0.0)
                }

                is AsignacionVariable -> {
                    val valor = evaluador.evaluar(instruccion.valor)
                    if (valor != null) {
                        tabla.asignarVariable(instruccion.nombre, valor)
                    }
                }

                is DeclaracionEspecial -> {
                    tabla.declararVariable("special", instruccion.id, instruccion.pregunta)
                }

                is LlamadaDraw -> {
                    val valorVariable = tabla.obtenerVariable(instruccion.id)

                    if (valorVariable !is ComponenteVisual) {
                        ManejadorErrores.agregarError("Semántico", 0, 0, "Semántico", "La variable '${instruccion.id}' no es 'special' o no existe.")
                    } else {
                        val preguntaOriginal = valorVariable // Ya es nuestro componente

                        val valoresArgumentos = instruccion.argumentos.map {
                            (evaluador.evaluar(it) as? Double) ?: 0.0
                        }.toMutableList()

                        val preguntaListaParaDibujar = clonarEInyectar(preguntaOriginal, valoresArgumentos)

                        if (valoresArgumentos.isNotEmpty()) {
                            ManejadorErrores.agregarError("Semántico", 0, 0, "Semántico", "Sobran parámetros en ${instruccion.id}.draw().")
                        }

                        componentesA_Dibujar.add(preguntaListaParaDibujar)
                    }
                }

                // --- CONDICIONALES ---
                is SentenciaIf -> {
                    val condicionPrincipal = (evaluador.evaluar(instruccion.condicion) as? Double) ?: 0.0

                    if (condicionPrincipal >= 1.0) {
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloqueTrue))
                    } else {
                        var bloqueEjecutado = false

                        instruccion.bloqueElseIf?.forEach { elseIf ->
                            if (!bloqueEjecutado) {
                                val condElseIf = (evaluador.evaluar(elseIf.condicion) as? Double) ?: 0.0
                                if (condElseIf >= 1.0) {
                                    componentesA_Dibujar.addAll(ejecutar(elseIf.bloqueTrue))
                                    bloqueEjecutado = true
                                }
                            }
                        }

                        if (!bloqueEjecutado && instruccion.bloqueElse != null) {
                            componentesA_Dibujar.addAll(ejecutar(instruccion.bloqueElse))
                        }
                    }
                }

                // --- CICLOS ---
                is SentenciaWhile -> {
                    while (((evaluador.evaluar(instruccion.condicion) as? Double) ?: 0.0) >= 1.0) {
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloque))
                    }
                }

                is SentenciaDoWhile -> {
                    do {
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloque))
                    } while (((evaluador.evaluar(instruccion.condicion) as? Double) ?: 0.0) >= 1.0)
                }

                is SentenciaFor -> {
                    ejecutar(listOf(instruccion.asignacionInicial))

                    while (((evaluador.evaluar(instruccion.condicion) as? Double) ?: 0.0) >= 1.0) {
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloque))
                        ejecutar(listOf(instruccion.actualizacion))
                    }
                }

                is SentenciaForRango -> {
                    val inicio = evaluador.evaluar(instruccion.rangoInicio) as? Double ?: continue
                    val fin = evaluador.evaluar(instruccion.rangoFin) as? Double ?: continue

                    val nombreVar = instruccion.variable

                    // MAGIA: Si la variable (ej: 'i') no existe, la declaramos automáticamente
                    if (!tabla.existeVariable(nombreVar)) {
                        tabla.declararVariable("number", nombreVar, inicio)
                    } else {
                        tabla.asignarVariable(nombreVar, inicio)
                    }

                    // Ejecutamos el ciclo respetando el rango
                    var actual = inicio
                    while (actual <= fin) {
                        tabla.asignarVariable(nombreVar, actual)

                        val resultadoBloque = ejecutar(instruccion.bloque)
                        // ✨ CORRECCIÓN 2: Usamos tu lista real 'componentesA_Dibujar'
                        componentesA_Dibujar.addAll(resultadoBloque)

                        actual++
                    }
                }

                // --- COMPONENTES VISUALES ---
                is Seccion -> {
                    val elementosResueltos = ejecutar(instruccion.elementos)
                    instruccion.elementos = elementosResueltos
                    componentesA_Dibujar.add(instruccion)
                }

                is Tabla -> {
                    val filasResueltas = mutableListOf<List<Instruccion>>()
                    for (fila in instruccion.filas) {
                        filasResueltas.add(ejecutar(fila))
                    }
                    instruccion.filas = filasResueltas
                    componentesA_Dibujar.add(instruccion)
                }

                is ComponenteVisual -> {
                    componentesA_Dibujar.add(instruccion)
                }
            }
        }

        return componentesA_Dibujar
    }

    //  METODOS AUXILIARES PARA LOS COMODINES DE LAS VARIABLES SPECIAL

    private fun clonarEInyectar(pregunta: ComponenteVisual, valores: MutableList<Double>): ComponenteVisual {
        val w = inyectarComodines(pregunta.width, valores)
        val h = inyectarComodines(pregunta.height, valores)

        return when (pregunta) {
            is PreguntaAbierta -> PreguntaAbierta(pregunta.label).apply {
                width = w; height = h; estilo = pregunta.estilo
            }
            is PreguntaSeleccion -> PreguntaSeleccion(
                pregunta.label, pregunta.opciones, inyectarComodines(pregunta.respuestaCorrecta, valores)
            ).apply { width = w; height = h; estilo = pregunta.estilo }
            is PreguntaDesplegable -> PreguntaDesplegable(
                pregunta.label, pregunta.opciones, inyectarComodines(pregunta.respuestaCorrecta, valores)
            ).apply { width = w; height = h; estilo = pregunta.estilo }
            is PreguntaMultiple -> PreguntaMultiple(
                pregunta.label, pregunta.opciones, pregunta.respuestasCorrectas.mapNotNull { inyectarComodines(it, valores) }
            ).apply { width = w; height = h; estilo = pregunta.estilo }
            else -> pregunta
        }
    }

    private fun inyectarComodines(exp: Expresion?, valores: MutableList<Double>): Expresion? {
        if (exp == null) return null

        return when (exp) {
            // Si encontramos un '?' en el AST, sacamos un valor de la lista y lo convertimos a numero
            is Expresion.Comodin -> {
                if (valores.isNotEmpty()) {
                    Expresion.NumeroLiteral(valores.removeAt(0))
                } else {
                    ManejadorErrores.agregarError("Semántico", 0, 0, "Semántico", "Faltan parámetros en .draw() para llenar los comodines.")
                    Expresion.NumeroLiteral(0.0) // Valor por defecto para que no explote
                }
            }

            is Expresion.OperacionAritmetica -> Expresion.OperacionAritmetica(
                inyectarComodines(exp.izq, valores)!!, exp.operador, inyectarComodines(exp.der, valores)!!
            )
            is Expresion.OperacionRelacional -> Expresion.OperacionRelacional(
                inyectarComodines(exp.izq, valores)!!, exp.operador, inyectarComodines(exp.der, valores)!!
            )
            is Expresion.OperacionLogica -> Expresion.OperacionLogica(
                inyectarComodines(exp.izq, valores)!!, exp.operador, inyectarComodines(exp.der, valores)!!
            )
            is Expresion.NegacionLogica -> Expresion.NegacionLogica(
                inyectarComodines(exp.expresion, valores)!!
            )
            is Expresion.MenosUnario -> Expresion.MenosUnario(
                inyectarComodines(exp.expresion, valores)!!
            )
            is Expresion.LlamadaPokemon -> Expresion.LlamadaPokemon(
                inyectarComodines(exp.rangoInicio, valores)!!, inyectarComodines(exp.rangoFin, valores)!!
            )
            else -> exp
        }
    }
}
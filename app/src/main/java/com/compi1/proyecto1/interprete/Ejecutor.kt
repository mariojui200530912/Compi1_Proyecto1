package com.compi1.proyecto1.interprete

import com.compi1.proyecto1.modelos.*

class Ejecutor(private val tabla: TablaSimbolos, private val evaluador: Evaluador) {

    /**
     * Recorre una lista de instrucciones y devuelve únicamente los componentes
     * visuales que deben dibujarse en la pantalla de Android.
     */
    fun ejecutar(instrucciones: List<Instruccion>): List<ComponenteVisual> {
        val componentesA_Dibujar = mutableListOf<ComponenteVisual>()

        for (instruccion in instrucciones) {
            when (instruccion) {
                // --- MANEJO DE VARIABLES ---
                is DeclaracionVariable -> {
                    val valor = instruccion.valorInicial?.let { evaluador.evaluar(it) }
                    tabla.declararVariable(instruccion.tipo, instruccion.nombre, valor)
                }

                is AsignacionVariable -> {
                    val valor = evaluador.evaluar(instruccion.valor)
                    if (valor != null) {
                        tabla.asignarVariable(instruccion.nombre, valor)
                    }
                }

                // --- CONDICIONALES ---
                is SentenciaIf -> {
                    // El manual dicta que si la evaluación es mayor o igual a 1 (o > 0), es verdadero[cite: 133].
                    // Si es 0 o menor, el condicional es falso[cite: 134].
                    val condicionPrincipal = (evaluador.evaluar(instruccion.condicion) as? Double) ?: 0.0

                    if (condicionPrincipal > 0.0) {
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloqueTrue))
                    } else {
                        var bloqueEjecutado = false

                        // Evaluar los ELSE IF secuencialmente
                        instruccion.bloqueElseIf?.forEach { elseIf ->
                            if (!bloqueEjecutado) {
                                val condElseIf = (evaluador.evaluar(elseIf.condicion) as? Double) ?: 0.0
                                if (condElseIf > 0.0) {
                                    componentesA_Dibujar.addAll(ejecutar(elseIf.bloqueTrue))
                                    bloqueEjecutado = true
                                }
                            }
                        }

                        // Si ninguno fue verdadero, ejecutar el ELSE
                        if (!bloqueEjecutado && instruccion.bloqueElse != null) {
                            componentesA_Dibujar.addAll(ejecutar(instruccion.bloqueElse))
                        }
                    }
                }

                // --- CICLOS ---
                is SentenciaWhile -> {
                    while (((evaluador.evaluar(instruccion.condicion) as? Double) ?: 0.0) > 0.0) {
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloque))
                    }
                }

                is SentenciaDoWhile -> {
                    do {
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloque))
                    } while (((evaluador.evaluar(instruccion.condicion) as? Double) ?: 0.0) > 0.0) 
                }

                is SentenciaFor -> {
                    // Ejecutamos la asignación inicial (ej: i = 0)
                    ejecutar(listOf(instruccion.asignacionInicial))

                    while (((evaluador.evaluar(instruccion.condicion) as? Double) ?: 0.0) > 0.0) {
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloque))
                        // Ejecutamos la actualización (ej: i = i + 1)
                        ejecutar(listOf(instruccion.actualizacion))
                    }
                }

                is SentenciaForRango -> {
                    val inicio = (evaluador.evaluar(instruccion.rangoInicio) as? Double) ?: 0.0
                    val fin = (evaluador.evaluar(instruccion.rangoFin) as? Double) ?: 0.0

                    // El manual asume que se declara la variable si aún no existe y se asume tipo number[cite: 139].
                    if (tabla.obtenerVariable(instruccion.variable) == null) {
                        tabla.declararVariable("number", instruccion.variable, inicio)
                    } else {
                        tabla.asignarVariable(instruccion.variable, inicio)
                    }

                    var iterador = inicio
                    while (iterador <= fin) {
                        tabla.asignarVariable(instruccion.variable, iterador)
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloque))
                        iterador++
                    }
                }

                // --- COMPONENTES VISUALES ---
                is Seccion -> {
                    // Una sección puede tener IFs o FORs adentro. Llamamos a ejecutar recursivamente
                    // para limpiar la lógica y quedarnos solo con los componentes visuales puros.
                    val elementosResueltos = ejecutar(instruccion.elementos)
                    instruccion.elementos = elementosResueltos
                    componentesA_Dibujar.add(instruccion)
                }

                is Tabla -> {
                    // Para la tabla, iteramos sobre sus filas y celdas para limpiar la lógica interna
                    val filasResueltas = mutableListOf<List<Instruccion>>()
                    for (fila in instruccion.filas) {
                        filasResueltas.add(ejecutar(fila))
                    }
                    instruccion.filas = filasResueltas
                    componentesA_Dibujar.add(instruccion)
                }

                is ComponenteVisual -> {
                    // Si es un Texto, PreguntaAbierta, etc., va directo a la lista final
                    componentesA_Dibujar.add(instruccion)
                }
            }
        }

        return componentesA_Dibujar
    }
}
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
                    tabla.declararVariable(instruccion.tipo, instruccion.nombre, valor ?: 0.0)
                }

                is AsignacionVariable -> {
                    val valor = evaluador.evaluar(instruccion.valor)
                    if (valor != null) {
                        tabla.asignarVariable(instruccion.nombre, valor)
                    }
                }

                is DeclaracionEspecial -> {
                    // Guardamos la pregunta completa en la tabla de símbolos como tipo "special"
                    tabla.declararVariable("special", instruccion.id, instruccion.pregunta)
                }

                is LlamadaDraw -> {
                    // Obtenemos directamente el valor de la variable
                    val valorVariable = tabla.obtenerVariable(instruccion.id)

                    // Verificamos si existe y si es un componente visual (o sea, si es 'special')
                    if (valorVariable !is ComponenteVisual) {
                        ManejadorErrores.agregarError("Semántico", 0, 0, "Semántico", "La variable '${instruccion.id}' no es 'special' o no existe.")
                    } else {
                        val preguntaOriginal = valorVariable // Ya es nuestro componente

                        // 1. Evaluamos los argumentos que pasaron en el draw()
                        val valoresArgumentos = instruccion.argumentos.map {
                            (evaluador.evaluar(it) as? Double) ?: 0.0
                        }.toMutableList()

                        // 2. Clonamos la pregunta e inyectamos los comodines
                        val preguntaListaParaDibujar = clonarEInyectar(preguntaOriginal, valoresArgumentos)

                        // 3. Verificamos si sobraron argumentos
                        if (valoresArgumentos.isNotEmpty()) {
                            ManejadorErrores.agregarError("Semántico", 0, 0, "Semántico", "Sobran parámetros en ${instruccion.id}.draw().")
                        }

                        // 4. ¡Añadimos la pregunta lista a nuestra lista de dibujado!
                        componentesA_Dibujar.add(preguntaListaParaDibujar)
                    }
                }

                // --- CONDICIONALES ---
                is SentenciaIf -> {
                    val condicionPrincipal = (evaluador.evaluar(instruccion.condicion) as? Double) ?: 0.0

                    // El manual dice: mayor o igual a 1 es verdadero, 0 o menor es falso
                    if (condicionPrincipal >= 1.0) {
                        componentesA_Dibujar.addAll(ejecutar(instruccion.bloqueTrue))
                    } else {
                        var bloqueEjecutado = false

                        // Evaluar los ELSE IF secuencialmente
                        instruccion.bloqueElseIf?.forEach { elseIf ->
                            if (!bloqueEjecutado) {
                                val condElseIf = (evaluador.evaluar(elseIf.condicion) as? Double) ?: 0.0
                                if (condElseIf >= 1.0) {
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
                    val inicio = (evaluador.evaluar(instruccion.rangoInicio) as? Double) ?: 0.0
                    val fin = (evaluador.evaluar(instruccion.rangoFin) as? Double) ?: 0.0

                    // Se declara la variable asumiendo el tipo number
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

    // =========================================================================
    //  MÉTODOS AUXILIARES PARA LOS COMODINES DE LAS VARIABLES SPECIAL
    // =========================================================================

    private fun clonarEInyectar(pregunta: ComponenteVisual, valores: MutableList<Double>): ComponenteVisual {
        // Reemplazamos los comodines en el width y height (que aplican a todas las preguntas)
        val w = inyectarComodines(pregunta.width, valores)
        val h = inyectarComodines(pregunta.height, valores)

        // Clonamos el objeto dependiendo de su tipo específico para no arruinar la variable original en memoria
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
            // Si encontramos un '?' en el AST, sacamos un valor de la lista y lo convertimos a número
            is Expresion.Comodin -> {
                if (valores.isNotEmpty()) {
                    Expresion.NumeroLiteral(valores.removeAt(0))
                } else {
                    ManejadorErrores.agregarError("Semántico", 0, 0, "Semántico", "Faltan parámetros en .draw() para llenar los comodines.")
                    Expresion.NumeroLiteral(0.0) // Valor por defecto para que no explote
                }
            }
            // Si es una operación, buscamos recursivamente en sus ramas izquierda y derecha
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
            is Expresion.LlamadaPokemon -> Expresion.LlamadaPokemon(
                inyectarComodines(exp.rangoInicio, valores)!!, inyectarComodines(exp.rangoFin, valores)!!
            )
            else -> exp // Si es un Numero, Variable o Cadena, lo devolvemos tal cual
        }
    }
}
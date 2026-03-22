package com.compi1.proyecto1.interprete

import com.compi1.proyecto1.modelos.Expresion

class Evaluador(private val tabla: TablaSimbolos) {

    // Funcion recursiva que evalua cualquier expresión matemática, logica o relacional
    fun evaluar(expresion: Expresion): Any? {
        return when (expresion) {
            is Expresion.NumeroLiteral -> expresion.valor
            is Expresion.CadenaLiteral -> expresion.valor

            is Expresion.Variable -> {
                tabla.obtenerVariable(expresion.nombre)
            }

            is Expresion.Comodin -> {
                ManejadorErrores.agregarError("Semántico", 0, 0, "Semántico", "No se puede evaluar un comodín (?) si no se inyecta su valor con .draw().")
                return 0.0
            }

            is Expresion.OperacionAritmetica -> {
                val izq = evaluar(expresion.izq) as? Double ?: return null
                val der = evaluar(expresion.der) as? Double ?: return null

                when (expresion.operador) {
                    "+" -> izq + der
                    "-" -> izq - der
                    "*" -> izq * der
                    "/" -> if (der != 0.0) izq / der else {
                        tabla.erroresSemanticos.add("Error: División por cero.")
                        null
                    }
                    "^" -> Math.pow(izq, der)
                    "%" -> izq % der
                    else -> null
                }
            }

            is Expresion.OperacionRelacional -> {
                val izq = evaluar(expresion.izq) as? Double ?: return null
                val der = evaluar(expresion.der) as? Double ?: return null

                val resultadoBooleano = when (expresion.operador) {
                    ">" -> izq > der
                    ">=" -> izq >= der
                    "<" -> izq < der
                    "<=" -> izq <= der
                    "==" -> izq == der
                    "!!" -> izq != der
                    else -> false
                }
                if (resultadoBooleano) 1.0 else 0.0
            }

            is Expresion.OperacionLogica -> {
                val operadoresUsados = obtenerOperadoresLogicos(expresion)
                if (operadoresUsados.size > 1) {
                    ManejadorErrores.agregarError("Semántico", 0, 0, "Semántico", "No se permite combinar diferentes operadores lógicos (&&, ||) en la misma expresión.")
                    return 0.0
                }
                // Evaluamos recursivamente el lado izquierdo y derecho
                val izq = evaluar(expresion.izq) as? Double ?: return null
                val der = evaluar(expresion.der) as? Double ?: return null

                // Aplicamos la regla del manual: mayor a 0 es verdadero, 0 o menor es falso
                val boolIzq = izq > 0.0
                val boolDer = der > 0.0

                // Evaluamos segun el operador logico
                val resultadoBooleano = when (expresion.operador) {
                    "||" -> boolIzq || boolDer
                    "&&" -> boolIzq && boolDer
                    else -> false
                }

                // Retornamos 1.0 o 0.0 para mantener la consistencia numerica
                if (resultadoBooleano) 1.0 else 0.0
            }

            is Expresion.NegacionLogica -> {
                // Evaluamos la expresion interna
                val valor = evaluar(expresion.expresion) as? Double ?: return null

                // Aplicamos la inversion lógica
                val boolValor = valor > 0.0
                if (!boolValor) 1.0 else 0.0
            }

            is Expresion.MenosUnario -> {
                // Evaluamos la expresion (ej: 50) y la multiplicamos por -1
                val valor = evaluar(expresion.expresion) as? Double ?: return null
                -valor
            }

            is Expresion.LlamadaPokemon -> {
                // Evaluamos los rangos por si el usuario puso variables o sumas
                val inicio = evaluar(expresion.rangoInicio) as? Double ?: return null
                val fin = evaluar(expresion.rangoFin) as? Double ?: return null

                // Validamos que el rango tenga sentido (no puedes buscar el Pokemon -5)
                if (inicio < 1 || fin < inicio) {
                    tabla.erroresSemanticos.add("Error: Rango de Pokémon inválido ($inicio a $fin).")
                    return null
                }
                // Retornamos una estructura especial (un Mapa) que la interfaz de Android
                return mapOf(
                    "accion" to "FETCH_POKEMON",
                    "inicio" to inicio.toInt(),
                    "fin" to fin.toInt()
                )
            }

            // Trampa final para cualquier otra expresion desconocida
            else -> null
        }
    }

    private fun obtenerOperadoresLogicos(exp: Expresion?): Set<String> {
        val operadores = mutableSetOf<String>()
        if (exp == null) return operadores

        when (exp) {
            is Expresion.OperacionLogica -> {
                operadores.add(exp.operador) // Guarda el "&&" o "||"
                operadores.addAll(obtenerOperadoresLogicos(exp.izq))
                operadores.addAll(obtenerOperadoresLogicos(exp.der))
            }
            is Expresion.NegacionLogica -> operadores.addAll(obtenerOperadoresLogicos(exp.expresion))
            is Expresion.OperacionAritmetica -> {
                operadores.addAll(obtenerOperadoresLogicos(exp.izq))
                operadores.addAll(obtenerOperadoresLogicos(exp.der))
            }
            is Expresion.OperacionRelacional -> {
                operadores.addAll(obtenerOperadoresLogicos(exp.izq))
                operadores.addAll(obtenerOperadoresLogicos(exp.der))
            }
            else -> {}
        }
        return operadores
    }
}
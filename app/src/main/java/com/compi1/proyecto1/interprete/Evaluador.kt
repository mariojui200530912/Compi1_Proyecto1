package com.compi1.proyecto1.interprete

import com.compi1.proyecto1.modelos.Expresion

class Evaluador(private val tabla: TablaSimbolos) {

    // Función recursiva que evalúa cualquier expresión matemática, lógica o relacional
    fun evaluar(expresion: Expresion): Any? {
        return when (expresion) {
            is Expresion.NumeroLiteral -> expresion.valor
            is Expresion.CadenaLiteral -> expresion.valor

            is Expresion.Variable -> {
                // Va a la tabla de símbolos y saca el valor actual
                tabla.obtenerVariable(expresion.nombre)
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

                // Las comparaciones devuelven un número, porque el manual dice que
                // "si es mayor a 0 es verdadero, 0 o menor es falso" para los condicionales.
                // Usaremos 1.0 para true y 0.0 para false
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
                // 1. Evaluamos recursivamente el lado izquierdo y derecho
                val izq = evaluar(expresion.izq) as? Double ?: return null
                val der = evaluar(expresion.der) as? Double ?: return null

                // 2. Aplicamos la regla del manual: mayor a 0 es verdadero, 0 o menor es falso
                val boolIzq = izq > 0.0
                val boolDer = der > 0.0

                // 3. Evaluamos según el operador lógico
                val resultadoBooleano = when (expresion.operador) {
                    "||" -> boolIzq || boolDer
                    "&&" -> boolIzq && boolDer
                    else -> false
                }

                // 4. Retornamos 1.0 o 0.0 para mantener la consistencia numérica
                if (resultadoBooleano) 1.0 else 0.0
            }

            is Expresion.NegacionLogica -> {
                // Evaluamos la expresión interna
                val valor = evaluar(expresion.expresion) as? Double ?: return null

                // Aplicamos la inversión lógica
                val boolValor = valor > 0.0
                if (!boolValor) 1.0 else 0.0
            }

            is Expresion.LlamadaPokemon -> {
                // 1. Evaluamos los rangos por si el usuario puso variables o sumas
                val inicio = evaluar(expresion.rangoInicio) as? Double ?: return null
                val fin = evaluar(expresion.rangoFin) as? Double ?: return null

                // 2. Validamos que el rango tenga sentido (no puedes buscar el Pokémon -5)
                if (inicio < 1 || fin < inicio) {
                    tabla.erroresSemanticos.add("Error: Rango de Pokémon inválido ($inicio a $fin).")
                    return null
                }

                // 3. Retornamos una estructura especial (un Mapa) que la interfaz de Android
                // reconocerá más adelante para hacer la petición HTTP usando Retrofit o Volley.
                return mapOf(
                    "accion" to "FETCH_POKEMON",
                    "inicio" to inicio.toInt(),
                    "fin" to fin.toInt()
                )
            }

            // Trampa final para cualquier otra expresión desconocida
            else -> null
        }
    }
}
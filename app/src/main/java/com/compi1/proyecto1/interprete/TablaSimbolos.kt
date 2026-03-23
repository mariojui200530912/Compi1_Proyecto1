package com.compi1.proyecto1.interprete

import com.compi1.proyecto1.modelos.ComponenteVisual

// Representa una variable guardada en memoria
data class Simbolo(val tipo: String, var valor: Any)

class TablaSimbolos {
    private val variables = HashMap<String, Simbolo>()

    val erroresSemanticos = mutableListOf<String>()

    fun declararVariable(tipo: String, nombre: String, valorInicial: Any?) {
        if (variables.containsKey(nombre)) {
            erroresSemanticos.add("Error: La variable '$nombre' ya fue definida.")
            return
        }

        // Asignacion de valores por defecto si no viene inicializada
        val valorFinal = valorInicial ?: when (tipo) {
            "number" -> 0.0
            "string" -> ""
            "special" -> null
            else -> null
        }

        if (valorFinal == null && tipo == "special") {
            erroresSemanticos.add("Error: La variable special '$nombre' debe inicializarse siempre.")
            return
        }

        if (!validarTipo(tipo, valorFinal!!)) {
            erroresSemanticos.add("Error: El valor asignado a '$nombre' no coincide con el tipo '$tipo'.")
            return
        }

        variables[nombre] = Simbolo(tipo, valorFinal)
    }

    fun asignarVariable(nombre: String, nuevoValor: Any) {
        val simbolo = variables[nombre]
        if (simbolo == null) {
            erroresSemanticos.add("Error: La variable '$nombre' no ha sido declarada.")
            return
        }

        if (!validarTipo(simbolo.tipo, nuevoValor)) {
            erroresSemanticos.add("Error: No se puede asignar un valor de otro tipo a la variable '$nombre' (${simbolo.tipo}).")
            return
        }

        simbolo.valor = nuevoValor
    }

    fun obtenerVariable(nombre: String): Any? {
        if (!variables.containsKey(nombre)) {
            erroresSemanticos.add("Error: La variable '$nombre' no existe.")
            return null
        }
        return variables[nombre]?.valor
    }

    private fun validarTipo(tipoEsperado: String, valor: Any): Boolean {
        return when (tipoEsperado) {
            "number" -> valor is Double || valor is Int
            "string" -> valor is String
            "special" -> valor is ComponenteVisual
            else -> false
        }
    }

    // Funcion auxiliar para verificar existencia silenciosamente
    fun existeVariable(nombre: String): Boolean {
        return variables.containsKey(nombre)
    }
}
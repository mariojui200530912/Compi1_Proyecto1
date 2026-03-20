package com.compi1.proyecto1.modelos

data class ErrorAnalisis(
    val lexema: String,
    val linea: Int,
    val columna: Int,
    val tipo: String,
    val descripcion: String
)

object ManejadorErrores {
    val errores = mutableListOf<ErrorAnalisis>()

    @JvmStatic // ¡Mágico! Permite que JFlex y CUP (Java) llamen a esta función de Kotlin
    fun agregarError(lexema: String, linea: Int, columna: Int, tipo: String, descripcion: String) {
        errores.add(ErrorAnalisis(lexema, linea, columna, tipo, descripcion))
    }

    fun limpiar() {
        errores.clear()
    }
}
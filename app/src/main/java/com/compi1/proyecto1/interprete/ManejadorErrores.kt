package com.compi1.proyecto1.interprete

data class ErrorAnalisis(
    val lexema: String,
    val linea: Int,
    val columna: Int,
    val tipo: String,
    val descripcion: String
)

object ManejadorErrores {
    val errores = mutableListOf<ErrorAnalisis>()

    @JvmStatic
    fun agregarError(lexema: String, linea: Int, columna: Int, tipo: String, descripcion: String) {
        errores.add(ErrorAnalisis(lexema, linea, columna, tipo, descripcion))
    }

    fun limpiar() {
        errores.clear()
    }
}
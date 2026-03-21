package com.compi1.proyecto1.modelos

sealed class Instruccion

// Manejo de Variables (number, string, special)
data class DeclaracionVariable(val tipo: String, val nombre: String, val valorInicial: Expresion?) : Instruccion()
data class AsignacionVariable(val nombre: String, val valor: Expresion) : Instruccion()

data class DeclaracionEspecial(val id: String, val pregunta: ComponenteVisual) : Instruccion()
data class LlamadaDraw(val id: String, val argumentos: List<Expresion>) : Instruccion()

// Estructuras de Control de Flujo
data class SentenciaIf(
    val condicion: Expresion,
    val bloqueTrue: List<Instruccion>,
    val bloqueElseIf: List<SentenciaIf>? = null, // Para los anidados ELSE IF
    val bloqueElse: List<Instruccion>? = null
) : Instruccion()

data class SentenciaWhile(val condicion: Expresion, val bloque: List<Instruccion>) : Instruccion()
data class SentenciaDoWhile(val bloque: List<Instruccion>, val condicion: Expresion) : Instruccion()

// Para el FOR clásico y el FOR de rango (in ..)
data class SentenciaFor(
    val asignacionInicial: Instruccion, // ej: i = 0
    val condicion: Expresion,           // ej: i <= 10
    val actualizacion: Instruccion,     // ej: i = i + 1
    val bloque: List<Instruccion>
) : Instruccion()

data class SentenciaForRango(
    val variable: String,
    val rangoInicio: Expresion,
    val rangoFin: Expresion,
    val bloque: List<Instruccion>
) : Instruccion()


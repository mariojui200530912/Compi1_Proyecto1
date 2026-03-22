package com.compi1.proyecto1.modelos

sealed class Expresion {
    // Literales base
    class Comodin : Expresion()
    data class NumeroLiteral(val valor: Double) : Expresion()
    data class CadenaLiteral(val valor: String) : Expresion()
    data class Variable(val nombre: String) : Expresion()

    // Funcion especial de la PokéAPI
    data class LlamadaPokemon(val rangoInicio: Expresion, val rangoFin: Expresion) : Expresion()

    // Operaciones (Aritmeticas, Relacionales, Logicas)
    data class OperacionAritmetica(val izq: Expresion, val operador: String, val der: Expresion) : Expresion()
    data class OperacionRelacional(val izq: Expresion, val operador: String, val der: Expresion) : Expresion()
    data class OperacionLogica(val izq: Expresion, val operador: String, val der: Expresion) : Expresion()
    data class NegacionLogica(val expresion: Expresion) : Expresion()

    data class MenosUnario(val expresion: Expresion) : Expresion()
}
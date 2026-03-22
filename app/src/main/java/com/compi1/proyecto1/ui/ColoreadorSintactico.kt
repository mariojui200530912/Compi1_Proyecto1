package com.compi1.proyecto1.ui

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.compi1.proyecto1.analizadores.Lexer
import com.compi1.proyecto1.analizadores.sym
import java.io.StringReader

class ColoreadorSintactico {
    fun colorearCodigo(codigoFuente: String): SpannableString {
        val textoColoreado = SpannableString(codigoFuente)

        val spansViejos = textoColoreado.getSpans(0, textoColoreado.length, Object::class.java)
        for (span in spansViejos) {
            textoColoreado.removeSpan(span)
        }

        if (codigoFuente.isBlank()) return textoColoreado

        val lexer = Lexer(StringReader(codigoFuente))

        lexer.modoEditor = true

        try {
            while (true) {
                val token = lexer.next_token()
                if (token.sym == sym.EOF) break

                val inicio = lexer.getYyChar()
                val lexema = lexer.yytext()
                val fin = inicio + lexema.length

                if (inicio >= 0 && fin <= textoColoreado.length) {
                    val color = obtenerColorParaToken(token.sym)

                    if (color != null) {
                        textoColoreado.setSpan(
                            ForegroundColorSpan(color),
                            inicio,
                            fin,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Ignoramos errores parciales
        }

        return textoColoreado
    }

    private fun obtenerColorParaToken(tipoToken: Int): Int? {
        return when (tipoToken) {
            // ✨ LOS TOKENS ESPECIALES DEL EDITOR ✨
            999 -> Color.parseColor("#9E9E9E") // Comentarios (Gris)
            996 -> Color.parseColor("#FF9800") // Comillas (Naranja)
            998 -> Color.parseColor("#FF9800") // Texto dentro de cadena (Naranja)
            997 -> Color.parseColor("#FFEB3B") // ¡EMOJIS! (Amarillo)

            // 1. Operadores aritméticos -> Verde
            sym.MAS, sym.MENOS, sym.POR, sym.DIVIDIDO, sym.POTENCIA, sym.MODULO -> Color.parseColor("#4CAF50")

            // 2. Números literales -> Celeste
            sym.NUMERO -> Color.parseColor("#00BCD4")

            // 3. Palabras reservadas -> Morado
            sym.SECTION, sym.TABLE, sym.TEXT, sym.OPEN_QUESTION, sym.DROP_QUESTION,
            sym.SELECT_QUESTION, sym.MULTIPLE_QUESTION, sym.WIDTH, sym.HEIGHT,
            sym.POINTX, sym.POINTY, sym.ORIENTATION, sym.ELEMENTS, sym.STYLES,
            sym.CONTENT, sym.LABEL, sym.OPTIONS, sym.CORRECT, sym.WHO_IS_THAT_POKEMON,
            sym.VERTICAL, sym.HORIZONTAL, sym.MONO, sym.SANS_SERIF, sym.CURSIVE,
            sym.LINE, sym.DOTTED, sym.DOUBLE, sym.TIPO_NUMBER, sym.TIPO_STRING,
            sym.TIPO_SPECIAL, sym.IF, sym.ELSE, sym.WHILE, sym.DO, sym.FOR, sym.IN,
            sym.COLOR_RED, sym.COLOR_BLUE, sym.COLOR_GREEN, sym.COLOR_PURPLE,
            sym.COLOR_SKY, sym.COLOR_YELLOW, sym.COLOR_BLACK, sym.COLOR_WHITE, sym.COLOR_HEX -> Color.parseColor("#9C27B0")

            // 4. Llaves, corchetes, paréntesis -> Azul
            sym.LLAVE_IZQ, sym.LLAVE_DER, sym.CORCHETE_IZQ, sym.CORCHETE_DER,
            sym.PAR_IZQ, sym.PAR_DER -> Color.parseColor("#2196F3")

            // 5. Variables y Otros -> Blanco
            sym.IDENTIFICADOR, sym.COMA, sym.PUNTO_COMA, sym.DOS_PUNTOS, sym.ASIGNACION,
            sym.MAYOR, sym.MAYOR_IGUAL, sym.MENOR, sym.MENOR_IGUAL, sym.IGUALDAD, sym.DIFERENTE,
            sym.OR, sym.AND, sym.NOT, sym.PUNTO, sym.RANGO, sym.COMODIN -> Color.parseColor("#FFFFFF")

            else -> Color.parseColor("#FFFFFF")
        }
    }
}
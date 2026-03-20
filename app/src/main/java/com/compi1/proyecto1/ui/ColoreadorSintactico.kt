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

        // Creamos una instancia del Lexer SOLO para leer tokens
        val lexer = Lexer(StringReader(codigoFuente))

        try {
            while (true) {
                // Pedimos el siguiente token
                val token = lexer.next_token()

                // Si llegamos al final del archivo (EOF), rompemos el ciclo
                if (token.sym == sym.EOF) break

                // Obtenemos dónde empieza y su longitud
                val inicio = lexer.getYyChar() // La variable que habilitamos con %char
                val lexema = lexer.yytext()
                val fin = inicio + lexema.length

                // Determinamos el color según el tipo de Token (revisa tu sym.java)
                val color = obtenerColorParaToken(token.sym)

                // Si tiene un color asignado, pintamos ese pedazo de texto
                if (color != null && inicio >= 0 && fin <= textoColoreado.length) {
                    textoColoreado.setSpan(
                        ForegroundColorSpan(color),
                        inicio,
                        fin,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        } catch (e: Exception) {
            // Si el Lexer encuentra un error mientras escribimos a medias, lo ignoramos
            // para que no parpadee la pantalla y el usuario pueda seguir tecleando.
        }

        return textoColoreado
    }

    private fun obtenerColorParaToken(tipoToken: Int): Int? {
        return when (tipoToken) {
            // Palabras reservadas principales (Morado/Violeta)
            sym.SECTION, sym.TABLE, sym.TEXT, sym.IF, sym.FOR, sym.WHILE -> Color.parseColor("#9C27B0")

            // Tipos de datos (Azul)
            sym.TIPO_NUMBER, sym.TIPO_STRING, sym.TIPO_SPECIAL -> Color.parseColor("#2196F3")

            // Cadenas de texto (Naranja)
            sym.CADENA -> Color.parseColor("#FF9800")

            // Números (Verde)
            sym.NUMERO -> Color.parseColor("#4CAF50")

            // Atributos como WIDTH, HEIGHT (Celeste)
            sym.WIDTH, sym.HEIGHT, sym.COLOR -> Color.parseColor("#00BCD4")

            // Si es un símbolo cualquiera o un identificador, no lo pintamos (null)
            else -> null
        }
    }
}
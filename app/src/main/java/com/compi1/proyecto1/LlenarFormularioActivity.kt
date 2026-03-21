package com.compi1.proyecto1

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.compi1.proyecto1.analizadores.LexerPKM
import com.compi1.proyecto1.analizadores.ParserPKM
import com.compi1.proyecto1.interprete.Evaluador
import com.compi1.proyecto1.interprete.TablaSimbolos
import com.compi1.proyecto1.modelos.Instruccion
import com.compi1.proyecto1.ui.GeneradorVistas
import java.io.StringReader

class LlenarFormularioActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llenar_formulario)

        val contenedor = findViewById<LinearLayout>(R.id.contenedorFormularioFinal)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarFormulario)

        // Recibimos el código compilado (.pkm) de la pantalla anterior
        val pkmString = intent.getStringExtra("PKM_DATA") ?: ""

        if (pkmString.isNotBlank()) {
            try {
                // Usamos nuestro analizador PKM para dibujar la pantalla limpia
                val lexer = LexerPKM(StringReader(pkmString))
                val parser = ParserPKM(lexer)
                val resultado = parser.parse()
                val arbol = resultado.value as? List<Instruccion>

                if (arbol != null) {
                    val tablaSimbolos = TablaSimbolos()
                    val evaluador = Evaluador(tablaSimbolos)
                    val generador = GeneradorVistas(this, evaluador)

                    // Dibujamos el formulario en el contenedor
                    contenedor.addView(generador.generarFormulario(arbol))
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error al procesar el formulario", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "El formulario está vacío", Toast.LENGTH_SHORT).show()
        }

        // LÓGICA DEL BOTÓN ENVIAR (Según el enunciado)
        btnEnviar.setOnClickListener {

            // Aquí puedes agregar lógica extra si tu GeneradorVistas extrae las respuestas correctas.
            // Por defecto, mostramos el mensaje genérico que pide el enunciado.
            AlertDialog.Builder(this)
                .setTitle("¡Formulario Enviado!")
                .setMessage("Tus respuestas han sido registradas.\n\n(Si este formulario es un cuestionario con respuestas correctas, se te notificará tu punteo próximamente).")
                .setPositiveButton("Salir") { _, _ ->
                    finish() // Cierra esta pantalla y devuelve al usuario al menú/editor
                }
                .setCancelable(false)
                .show()
        }
    }
}
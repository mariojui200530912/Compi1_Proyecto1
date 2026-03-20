package com.compi1.proyecto1

import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import java.io.BufferedReader
import java.io.InputStreamReader

import com.compi1.proyecto1.analizadores.Lexer
import com.compi1.proyecto1.analizadores.LexerPKM
import com.compi1.proyecto1.analizadores.ParserPKM
import com.compi1.proyecto1.analizadores.Sintactico
import com.compi1.proyecto1.modelos.Instruccion
import com.compi1.proyecto1.interprete.Ejecutor
import com.compi1.proyecto1.interprete.Evaluador
import com.compi1.proyecto1.interprete.TablaSimbolos
import com.compi1.proyecto1.modelos.ManejadorErrores
import com.compi1.proyecto1.ui.ColoreadorSintactico
import com.compi1.proyecto1.ui.GeneradorVistas
import java.io.StringReader

class MainActivity : ComponentActivity() {
    private lateinit var etEditorCodigo: EditText
    private lateinit var btnCompilar: Button
    private lateinit var tvConsolaErrores: TextView
    private lateinit var contenedorFormulario: LinearLayout
    private lateinit var btnAbrirArchivo: Button
    private lateinit var btnGuardarArchivo: Button
    private var arbolASTActual: List<Instruccion>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Vincular las vistas del XML
        etEditorCodigo = findViewById(R.id.etEditorCodigo)
        btnCompilar = findViewById(R.id.btnCompilar)
        tvConsolaErrores = findViewById(R.id.tvConsolaErrores)
        contenedorFormulario = findViewById(R.id.contenedorFormulario)
        btnAbrirArchivo = findViewById(R.id.btnAbrirArchivo)
        btnGuardarArchivo = findViewById(R.id.btnGuardarArchivo)

        etEditorCodigo.addTextChangedListener(object : TextWatcher {
            // Bandera de seguridad para no crear un bucle infinito
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s == null || isFormatting) return

                isFormatting = true // Bloqueamos temporalmente

                // 1. Limpiamos todos los colores anteriores para que no se superpongan
                val spansViejos = s.getSpans(0, s.length, ForegroundColorSpan::class.java)
                for (span in spansViejos) {
                    s.removeSpan(span)
                }

                // 2. Llamamos a nuestra clase Coloreador
                val coloreador = ColoreadorSintactico()
                val textoColoreado = coloreador.colorearCodigo(s.toString())

                // 3. Extraemos los colores nuevos que calculó el Lexer y se los pegamos al EditText
                val spansNuevos = textoColoreado.getSpans(0, textoColoreado.length, ForegroundColorSpan::class.java)
                for (span in spansNuevos) {
                    val inicio = textoColoreado.getSpanStart(span)
                    val fin = textoColoreado.getSpanEnd(span)

                    // Doble validación de seguridad por si el usuario borra texto muy rápido
                    if (inicio >= 0 && fin <= s.length) {
                        s.setSpan(span, inicio, fin, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }

                isFormatting = false // Desbloqueamos para la siguiente tecla
            }
        })

        btnAbrirArchivo.setOnClickListener {
            // Le pedimos a Android que abra el explorador de archivos
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // Permitir cualquier tipo de archivo de texto
            }
            abrirArchivoLauncher.launch(intent)
        }

        // Acción de GUARDAR
        btnGuardarArchivo.setOnClickListener {
            if (arbolASTActual == null) {
                Toast.makeText(this, "Primero debes compilar un formulario válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Lanzamos el explorador de archivos para crear el .pkm
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // o "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "mi_formulario.pkm")
            }
            guardarArchivoLauncher.launch(intent)
        }

        // Acción del botón
        btnCompilar.setOnClickListener {
            compilarCodigo()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun compilarCodigo() {
        // 1. Damos feedback inmediato de que el botón sí se presionó
        Toast.makeText(this, "Analizando código...", Toast.LENGTH_SHORT).show()

        // 2. Limpiar todo el estado anterior
        contenedorFormulario.removeAllViews()
        tvConsolaErrores.visibility = View.GONE
        tvConsolaErrores.text = ""
        ManejadorErrores.limpiar()

        val codigoFuente = etEditorCodigo.text.toString()

        if (codigoFuente.trim().isEmpty()) {
            Toast.makeText(this, "El editor está vacío", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // --- FASE 1: ANÁLISIS ---
            val lexer = Lexer(StringReader(codigoFuente))
            val parser = Sintactico(lexer)

            val resultado = parser.parse()

            // Si hay errores léxicos o sintácticos recolectados, paramos
            if (ManejadorErrores.errores.isNotEmpty()) {
                mostrarErroresEnConsola()
                return
            }

            // Validamos que el árbol no sea nulo (por si el parser falló en silencio)
            val arbolAST = resultado?.value as? List<Instruccion>
            arbolASTActual = arbolAST
            if (arbolAST == null || arbolAST.isEmpty()) {
                ManejadorErrores.agregarError("AST", 0, 0, "Estructura", "El código no generó ningún componente visual válido.")
                mostrarErroresEnConsola()
                return
            }

            // --- FASE 2: EJECUCIÓN ---
            val tablaSimbolos = TablaSimbolos()
            val evaluador = Evaluador(tablaSimbolos)
            val ejecutor = Ejecutor(tablaSimbolos, evaluador)

            val componentesVisuales = ejecutor.ejecutar(arbolAST)

            // Validamos errores semánticos (matemáticas, variables mal usadas)
            if (tablaSimbolos.erroresSemanticos.isNotEmpty()) {
                for (error in tablaSimbolos.erroresSemanticos) {
                    ManejadorErrores.agregarError("Ejecución", 0, 0, "Semántico", error)
                }
                mostrarErroresEnConsola()
                return
            }

            // --- FASE 3: RENDERIZADO VISUAL ---
            val generadorVistas = GeneradorVistas(this, evaluador)
            val vistaFinal = generadorVistas.generarFormulario(componentesVisuales)

            contenedorFormulario.addView(vistaFinal)
            Toast.makeText(this, "¡Formulario generado con éxito!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            // AQUÍ ESTABA EL PROBLEMA: Si el sistema falla, ahora lo obligamos a mostrar por qué
            e.printStackTrace() // Esto lo imprimirá en el Logcat de Android Studio (abajo)

            // Si la lista estaba vacía pero caímos en un error, fue un fallo del programa
            if (ManejadorErrores.errores.isEmpty()) {
                ManejadorErrores.agregarError("Desconocido", 0, 0, "Fatal (Kotlin)", e.message ?: "Error interno al compilar")
            }

            mostrarErroresEnConsola()
        }
    }

    private fun mostrarErroresEnConsola() {
        if (ManejadorErrores.errores.isEmpty()) return

        val reporte = StringBuilder()
        reporte.append("Se encontraron errores en tu código:\n\n")

        for (error in ManejadorErrores.errores) {
            // Ejemplo: [Léxico] Símbolo no reconocido: '@' en línea 5, columna 12
            reporte.append("[${error.tipo}] ${error.descripcion}: '${error.lexema}' en línea ${error.linea}, columna ${error.columna}\n")
        }

        tvConsolaErrores.text = reporte.toString()
        tvConsolaErrores.visibility = View.VISIBLE
    }

    private val abrirArchivoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    // 1. Abrimos el archivo y leemos todo su texto
                    val inputStream = contentResolver.openInputStream(uri)
                    val lector = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                    val textoLeido = lector.readText()
                    lector.close()

                    // 2. Extraemos el nombre del archivo de Android para ver su extensión
                    var nombreArchivo = ""
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index != -1) nombreArchivo = it.getString(index)
                        }
                    }

                    // 3. DILUCIDAMOS QUÉ HACER CON EL ARCHIVO
                    if (nombreArchivo.lowercase().endsWith(".pkm")) {
                        // === ES UN ARCHIVO COMPILADO (.pkm) ===
                        Toast.makeText(this, "Cargando formulario compilado...", Toast.LENGTH_SHORT).show()

                        // Limpiamos la pantalla por si había un formulario anterior
                        contenedorFormulario.removeAllViews()
                        tvConsolaErrores.visibility = View.GONE
                        ManejadorErrores.limpiar()

                        // AQUÍ VA EXACTAMENTE TU CÓDIGO PKM
                        val lexer = LexerPKM(StringReader(textoLeido))
                        val parser = ParserPKM(lexer)
                        val resultado = parser.parse()

                        val arbolReconstruido = resultado.value as? List<Instruccion>

                        if (arbolReconstruido != null) {
                            // Instanciamos la tabla y evaluador básicos (por si los componentes los requieren)
                            val tablaSimbolos = TablaSimbolos()
                            val evaluador = Evaluador(tablaSimbolos)

                            // ¡Se lo pasamos directo al generador gráfico!
                            val generadorVistas = GeneradorVistas(this@MainActivity, evaluador)
                            val vistaFinal = generadorVistas.generarFormulario(arbolReconstruido)
                            contenedorFormulario.addView(vistaFinal)
                        }

                    } else {
                        // === ES CÓDIGO FUENTE NORMAL ===
                        // Simplemente lo ponemos en el editor para que el usuario lo modifique
                        etEditorCodigo.setText(textoLeido)
                        Toast.makeText(this, "Código fuente cargado en el editor", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error al procesar el archivo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- LAUNCHER PARA GUARDAR ARCHIVOS ---
    private val guardarArchivoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    // Verificamos que tengamos un árbol para exportar
                    arbolASTActual?.let { arbol ->

                        // Necesitamos un evaluador para el exportador
                        val tablaSimbolos = TablaSimbolos()
                        val evaluador = Evaluador(tablaSimbolos)

                        // Instanciamos el exportador
                        val exportador = com.compi1.proyecto1.interprete.ExportadorPKM(evaluador)

                        // Generamos el texto del archivo .pkm (puedes pedirle el autor y descripción al usuario en un cuadro de diálogo luego)
                        val textoPKM = exportador.generarArchivoPKM(arbol, "Autor Proyecto", "Formulario generado desde la App")

                        // Lo escribimos en el archivo físico
                        val outputStream = contentResolver.openOutputStream(uri)
                        outputStream?.write(textoPKM.toByteArray())
                        outputStream?.close()

                        Toast.makeText(this, "Archivo .pkm guardado con éxito", Toast.LENGTH_LONG).show()
                    }

                } catch (e: Exception) {
                    Toast.makeText(this, "Error al guardar el archivo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
package com.compi1.proyecto1

import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.compi1.proyecto1.analizadores.Lexer
import com.compi1.proyecto1.analizadores.LexerPKM
import com.compi1.proyecto1.analizadores.ParserPKM
import com.compi1.proyecto1.analizadores.Sintactico
import com.compi1.proyecto1.modelos.Instruccion
import com.compi1.proyecto1.interprete.Ejecutor
import com.compi1.proyecto1.interprete.Evaluador
import com.compi1.proyecto1.interprete.ExportadorPKM
import com.compi1.proyecto1.interprete.TablaSimbolos
import com.compi1.proyecto1.interprete.ManejadorErrores
import com.compi1.proyecto1.ui.ColoreadorSintactico
import com.compi1.proyecto1.ui.GeneradorVistas
import java.io.StringReader

class MainActivity : ComponentActivity() {

    // --- VARIABLES GLOBALES DE LA VISTA ---
    private lateinit var editorCodigo: com.compi1.proyecto1.ui.EditorConLineas
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var contenedorFormulario: LinearLayout

    // Botones Pantalla 0 (Editor)
    private lateinit var btnCompilar: Button
    private lateinit var btnProbar: Button
    private lateinit var btnMenu: Button
    private lateinit var btnColor: Button

    // Botones Pantalla 1 (Formulario)
    private lateinit var btnModificar: Button
    private lateinit var btnEnviar: Button

    // --- VARIABLES DE LÓGICA ---
    private var arbolASTActual: List<Instruccion>? = null
    private var arbolFinalGlobal: List<Instruccion>? = null
    private var tipoArchivoAGuardar = ""
    private var modoActual = "FORM"
    private var generadorActual: GeneradorVistas? = null
    private var autorPKM = "Usuario"
    private var tituloPKM = "Formulario"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContentView(R.layout.activity_main)

        // 1. ENLACE DE VISTAS (Evitando duplicados)
        viewFlipper = findViewById(R.id.viewFlipper)
        editorCodigo = findViewById(R.id.editorCodigo)
        contenedorFormulario = findViewById(R.id.contenedorFormulario)

        btnCompilar = findViewById(R.id.btnCompilar)
        btnProbar = findViewById(R.id.btnProbar)
        btnMenu = findViewById(R.id.btnMenu)
        btnColor = findViewById(R.id.btnColor)

        btnModificar = findViewById(R.id.btnModificar)
        btnEnviar = findViewById(R.id.btnEnviar)

        // 2. COLOREADO DE CÓDIGO EN TIEMPO REAL
        editorCodigo.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null || isFormatting) return
                isFormatting = true
                val spansViejos = s.getSpans(0, s.length, ForegroundColorSpan::class.java)
                for (span in spansViejos) s.removeSpan(span)
                val coloreador = ColoreadorSintactico()
                val textoColoreado = coloreador.colorearCodigo(s.toString())
                val spansNuevos = textoColoreado.getSpans(0, textoColoreado.length, ForegroundColorSpan::class.java)
                for (span in spansNuevos) {
                    val inicio = textoColoreado.getSpanStart(span)
                    val fin = textoColoreado.getSpanEnd(span)
                    if (inicio >= 0 && fin <= s.length) {
                        s.setSpan(span, inicio, fin, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                isFormatting = false
            }
        })

        // MENU DE OPCIONES PRINCIPAL
        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "📝 Insertar Plantilla")
            popup.menu.add(0, 2, 0, "💾 Guardar código (.form)")
            popup.menu.add(0, 3, 0, "📦 Guardar formulario (.pkm)")
            popup.menu.add(0, 4, 0, "☁️ Guardar en la Nube (.pkm)")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> mostrarDialogoPlantillas()
                    2 -> iniciarGuardadoLocal("FORM")
                    3 -> iniciarGuardadoLocal("PKM")
                    4 -> guardarEnLaNube()
                }
                true
            }
            popup.show()
        }

        // Boton para color
        btnColor.setOnClickListener {
            mostrarDialogoColor()
        }

        btnCompilar.setOnClickListener {
            val codigoFuente = editorCodigo.text.toString()
            if (codigoFuente.trim().isEmpty()) {
                Toast.makeText(this, "El editor está vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Compilando...", Toast.LENGTH_SHORT).show()
            contenedorFormulario.removeAllViews()
            ManejadorErrores.limpiar()

            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    val tablaSimbolos = TablaSimbolos()
                    val evaluador = Evaluador(tablaSimbolos)
                    val arbolFinal: List<Instruccion>

                    if (modoActual == "PKM") {
                        val parser = ParserPKM(LexerPKM(StringReader(codigoFuente)))
                        val arbolPKM = parser.parse().value as? List<Instruccion>
                        if (ManejadorErrores.errores.isNotEmpty() || arbolPKM.isNullOrEmpty()) {
                            mostrarErroresEnHiloPrincipal()
                            return@launch
                        }
                        arbolFinal = arbolPKM
                        arbolASTActual = arbolFinal
                        arbolFinalGlobal = arbolFinal
                    } else {
                        val parser = Sintactico(Lexer(StringReader(codigoFuente)))
                        val arbolForm = parser.parse().value as? List<Instruccion>
                        if (ManejadorErrores.errores.isNotEmpty() || arbolForm.isNullOrEmpty()) {
                            mostrarErroresEnHiloPrincipal()
                            return@launch
                        }
                        arbolFinal = Ejecutor(tablaSimbolos, evaluador).ejecutar(arbolForm)
                        arbolASTActual = arbolForm
                        arbolFinalGlobal = arbolFinal
                        if (tablaSimbolos.erroresSemanticos.isNotEmpty() || ManejadorErrores.errores.isNotEmpty()) {
                            tablaSimbolos.erroresSemanticos.forEach { ManejadorErrores.agregarError("Ejecución", 0, 0, "Semántico", it) }
                            mostrarErroresEnHiloPrincipal()
                            return@launch // Cancela el Compilacion exitosa
                        }
                    }

                    // Regresamos al hilo principal para dibujar
                    withContext(Dispatchers.Main) {
                        val generador = GeneradorVistas(this@MainActivity, evaluador)
                        this@MainActivity.generadorActual = generador
                        val vistaFinal = generador.generarFormulario(arbolFinal)
                        contenedorFormulario.addView(vistaFinal)

                        val accion = intent.getStringExtra("ACCION") ?: "EDITAR"

                        if (accion == "RESPONDER") {
                            // Si viene a responder, lo lanzamos directo a la cara 2 del ViewFlipper
                            Toast.makeText(this@MainActivity, "Formulario cargado exitosamente", Toast.LENGTH_SHORT).show()
                            viewFlipper.displayedChild = 1
                        } else {
                            // Si viene a editar, lo dejamos en el editor (cara 0)
                            Toast.makeText(this@MainActivity, "¡Compilación Exitosa! Todo listo para probar.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error en compilación: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // TRANSICIONES DEL VIEW FLIPPER
        btnProbar.setOnClickListener {
            if (ManejadorErrores.errores.isNotEmpty()) {
                Toast.makeText(this, "Corrige los errores antes de probar", Toast.LENGTH_SHORT).show()
            } else if (contenedorFormulario.childCount == 0 || arbolASTActual == null) {
                Toast.makeText(this, "Primero debes compilar el código", Toast.LENGTH_SHORT).show()
            } else {
                // Pasamos a la pantalla del formulario (índice 1)
                viewFlipper.displayedChild = 1
            }
        }

        btnModificar.setOnClickListener {
            // Regresamos a la pantalla del editor (índice 0)
            viewFlipper.displayedChild = 0
        }

        btnEnviar.setOnClickListener {
            if (this@MainActivity.generadorActual != null) {
                this@MainActivity.generadorActual?.calificarFormulario()
            } else {
                Toast.makeText(this, "No hay formulario activo para calificar.", Toast.LENGTH_SHORT).show()
            }
        }

        // RECUPERAR DATOS DEL INTENT (Si vienes de abrir un archivo)
        modoActual = intent.getStringExtra("MODO") ?: "FORM"
        val contenido = intent.getStringExtra("CONTENIDO") ?: ""
        val accion = intent.getStringExtra("ACCION") ?: "EDITAR" // ✨ Leemos si viene a responder

        if (contenido.isNotBlank()) {
            editorCodigo.setText(contenido)

            if (modoActual == "PKM" || accion == "RESPONDER") {
                btnCompilar.performClick()
            }
        }
    }

    // --- FUNCIONES AUXILIARES ---

    private suspend fun mostrarErroresEnHiloPrincipal() {
        withContext(Dispatchers.Main) {
            if (ManejadorErrores.errores.isEmpty()) return@withContext
            val reporte = java.lang.StringBuilder()
            ManejadorErrores.errores.forEach {
                reporte.append("• [${it.tipo}] Lín ${it.linea}, Col ${it.columna}:\n  ${it.descripcion} ('${it.lexema}')\n\n")
            }

            // Mostramos los errores en un cuadro de diálogo elegante
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Errores de Compilación")
                .setMessage(reporte.toString())
                .setPositiveButton("Entendido", null)
                .show()
        }
    }

    private fun iniciarGuardadoLocal(tipo: String) {
        tipoArchivoAGuardar = tipo
        if (tipo == "FORM" && editorCodigo.text.toString().trim().isEmpty()) return
        if (tipo == "PKM" && arbolASTActual == null) {
            Toast.makeText(this, "Compila el formulario primero", Toast.LENGTH_SHORT).show()
            return
        }

        if (tipo == "PKM") {
            // Si es PKM, lanzamos el cuadro de diálogo antes de guardar
            mostrarDialogoDatosPKM()
        } else {
            // Si es FORM, guardamos directo
            lanzarIntentGuardado("codigo.form")
        }
    }

    private fun mostrarDialogoDatosPKM() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }
        val inputAutor = EditText(this).apply { hint = "Ingresa el Autor" }
        val inputTitulo = EditText(this).apply { hint = "Título / Descripción" }

        layout.addView(inputAutor)
        layout.addView(inputTitulo)

        AlertDialog.Builder(this)
            .setTitle("Datos del Archivo PKM")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                autorPKM = inputAutor.text.toString().ifBlank { "Anonimo" }
                tituloPKM = inputTitulo.text.toString().ifBlank { "Sin titulo" }
                lanzarIntentGuardado("form.pkm")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun lanzarIntentGuardado(nombreArchivo: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, nombreArchivo)
        }
        guardarArchivoLauncher.launch(intent)
    }

    private val guardarArchivoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    val texto = if (tipoArchivoAGuardar == "FORM") {
                        editorCodigo.text.toString()
                    } else {
                        ExportadorPKM(Evaluador(TablaSimbolos())).generarArchivoPKM(arbolFinalGlobal!!, autorPKM, tituloPKM)
                    }
                    outputStream?.write(texto.toByteArray())
                    outputStream?.close()
                    Toast.makeText(this, "Archivo guardado exitosamente", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun guardarEnLaNube() {
        if (arbolASTActual == null) {
            Toast.makeText(this, "Compila primero antes de subir a la nube", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Subiendo formulario...", Toast.LENGTH_SHORT).show()
        val textoPKM = ExportadorPKM(Evaluador(TablaSimbolos())).generarArchivoPKM(arbolFinalGlobal!!, "Usuario", "Nube")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.compi1.proyecto1.utils.RetrofitClient.apiService.guardarFormulario(
                    com.compi1.proyecto1.utils.FormularioPKM(autor = "Usuario", titulo = "Nube", contenidoPkm = textoPKM)
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "¡Guardado exitosamente en MySQL!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarDialogoPlantillas() {
        val nombresPlantillas = arrayOf("1. Sección Básica", "2. Pregunta Abierta", "3. Pregunta de Selección", "4. Tabla Estándar")
        val codigoPlantillas = arrayOf(
            "SECTION [\n    width: 300,\n    height: 400,\n    orientation: VERTICAL,\n    elements: {\n        $ Aquí van tus elementos\n    }\n]\n",
            "OPEN_QUESTION [\n    label: \"¿Cuál es tu nombre?\"\n]\n",
            "SELECT_QUESTION [\n    label: \"Selecciona una opción:\",\n    options: {\"Op1\", \"Op2\", \"Op3\"},\n    correct: 1\n]\n",
            "TABLE [\n    width: 300,\n    height: 300,\n    elements: {\n        [ { TEXT [ content: \"Celda 1\" ] }, { TEXT [ content: \"Celda 2\" ] } ]\n    }\n]\n"
        )
        AlertDialog.Builder(this)
            .setTitle("Insertar Plantilla")
            .setItems(nombresPlantillas) { _, index -> insertarTextoEnEditor(codigoPlantillas[index]) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun mostrarDialogoColor() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 40, 60, 40) }
        val vistaPrevia = View(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200); setBackgroundColor(Color.BLACK) }
        val txtR = TextView(this).apply { text = "Rojo: 0"; setPadding(0,20,0,0) }
        val seekR = SeekBar(this).apply { max = 255 }
        val txtG = TextView(this).apply { text = "Verde: 0"; setPadding(0,20,0,0) }
        val seekG = SeekBar(this).apply { max = 255 }
        val txtB = TextView(this).apply { text = "Azul: 0"; setPadding(0,20,0,0) }
        val seekB = SeekBar(this).apply { max = 255 }

        fun actualizarColor() {
            val r = seekR.progress; val g = seekG.progress; val b = seekB.progress
            vistaPrevia.setBackgroundColor(Color.rgb(r, g, b))
            txtR.text = "Rojo: $r"; txtG.text = "Verde: $g"; txtB.text = "Azul: $b"
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { actualizarColor() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }
        seekR.setOnSeekBarChangeListener(listener); seekG.setOnSeekBarChangeListener(listener); seekB.setOnSeekBarChangeListener(listener)
        layout.addView(vistaPrevia); layout.addView(txtR); layout.addView(seekR); layout.addView(txtG); layout.addView(seekG); layout.addView(txtB); layout.addView(seekB)

        AlertDialog.Builder(this)
            .setTitle("Selecciona un Color")
            .setView(layout)
            .setPositiveButton("HEX (#)") { _, _ -> insertarTextoEnEditor("\"#${String.format("%02X%02X%02X", seekR.progress, seekG.progress, seekB.progress)}\"") }
            .setNeutralButton("RGB ()") { _, _ -> insertarTextoEnEditor("(${seekR.progress},${seekG.progress},${seekB.progress})") }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun insertarTextoEnEditor(texto: String) {
        val inicio = Math.max(editorCodigo.selectionStart, 0)
        val fin = Math.max(editorCodigo.selectionEnd, 0)
        editorCodigo.text?.replace(Math.min(inicio, fin), Math.max(inicio, fin), texto)
    }
}
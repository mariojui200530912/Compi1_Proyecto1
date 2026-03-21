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
    private lateinit var etEditorCodigo: EditText
    private lateinit var btnCompilar: Button
    private lateinit var btnProbar: Button
    private lateinit var btnMenuOpciones: Button
    private lateinit var tvConsolaErrores: TextView
    private lateinit var contenedorFormulario: LinearLayout

    private var arbolASTActual: List<Instruccion>? = null
    private var tipoArchivoAGuardar = ""
    private var modoActual = "FORM"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etEditorCodigo = findViewById(R.id.etEditorCodigo)
        btnCompilar = findViewById(R.id.btnCompilar)
        btnProbar = findViewById(R.id.btnProbar)
        btnMenuOpciones = findViewById(R.id.btnMenuOpciones)
        tvConsolaErrores = findViewById(R.id.tvConsolaErrores)
        contenedorFormulario = findViewById(R.id.contenedorFormulario)

        etEditorCodigo.addTextChangedListener(object : TextWatcher {
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

        // --- MENÚ DESPLEGABLE NATIVO ---
        btnMenuOpciones.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "📝 Insertar Plantilla")
            popup.menu.add(0, 2, 0, "🎨 Selector de Color")
            popup.menu.add(0, 3, 0, "💾 Guardar .form (Teléfono)")
            popup.menu.add(0, 4, 0, "📦 Guardar .pkm (Teléfono)")
            popup.menu.add(0, 5, 0, "☁️ Guardar .pkm (Nube)")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> mostrarDialogoPlantillas()
                    2 -> mostrarDialogoColor()
                    3 -> iniciarGuardadoLocal("FORM")
                    4 -> iniciarGuardadoLocal("PKM")
                    5 -> guardarEnLaNube()
                }
                true
            }
            popup.show()
        }

        // --- COMPILACIÓN ASÍNCRONA (SÚPER RÁPIDA) ---
        btnCompilar.setOnClickListener {
            val codigoFuente = etEditorCodigo.text.toString()
            if (codigoFuente.trim().isEmpty()) {
                Toast.makeText(this, "El editor está vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Compilando en segundo plano...", Toast.LENGTH_SHORT).show()
            contenedorFormulario.removeAllViews()
            tvConsolaErrores.visibility = View.GONE
            ManejadorErrores.limpiar()

            // Lanzamos la compilación matemática pesada en un hilo de fondo
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
                    } else {
                        val parser = Sintactico(Lexer(StringReader(codigoFuente)))
                        val arbolForm = parser.parse().value as? List<Instruccion>
                        if (ManejadorErrores.errores.isNotEmpty() || arbolForm.isNullOrEmpty()) {
                            mostrarErroresEnHiloPrincipal()
                            return@launch
                        }
                        arbolFinal = Ejecutor(tablaSimbolos, evaluador).ejecutar(arbolForm)
                        arbolASTActual = arbolForm
                        if (tablaSimbolos.erroresSemanticos.isNotEmpty()) {
                            tablaSimbolos.erroresSemanticos.forEach { ManejadorErrores.agregarError("Ejecución", 0, 0, "Semántico", it) }
                            mostrarErroresEnHiloPrincipal()
                            return@launch
                        }
                    }

                    // Regresamos al hilo de la pantalla para dibujar las vistas
                    withContext(Dispatchers.Main) {
                        val vistaFinal = GeneradorVistas(this@MainActivity, evaluador).generarFormulario(arbolFinal)
                        contenedorFormulario.addView(vistaFinal)
                        Toast.makeText(this@MainActivity, "¡Formulario generado!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    mostrarErroresEnHiloPrincipal()
                }
            }
        }

        // --- BOTÓN PROBAR (CON SEGURO CONTRA ERRORES) ---
        btnProbar.setOnClickListener {
            if (arbolASTActual == null) {
                Toast.makeText(this, "Primero compila sin errores", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val exportador = ExportadorPKM(Evaluador(TablaSimbolos()))
                val textoPKM = exportador.generarArchivoPKM(arbolASTActual!!, "Temp", "Temp")

                val intent = Intent(this, LlenarFormularioActivity::class.java)
                intent.putExtra("PKM_DATA", textoPKM)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Error al abrir prueba: Asegúrate de registrar LlenarFormularioActivity en el Manifest", Toast.LENGTH_LONG).show()
            }
        }

        modoActual = intent.getStringExtra("MODO") ?: "FORM"
        val contenido = intent.getStringExtra("CONTENIDO") ?: ""
        etEditorCodigo.setText(contenido)

        if (modoActual == "PKM" && contenido.isNotBlank()) {
            btnCompilar.performClick() // Autocompilar al abrir un PKM
        }
    }

    private suspend fun mostrarErroresEnHiloPrincipal() {
        withContext(Dispatchers.Main) {
            if (ManejadorErrores.errores.isEmpty()) return@withContext
            val reporte = StringBuilder("Errores encontrados:\n\n")
            ManejadorErrores.errores.forEach { reporte.append("[${it.tipo}] ${it.descripcion}: '${it.lexema}' línea ${it.linea}, col ${it.columna}\n") }
            tvConsolaErrores.text = reporte.toString()
            tvConsolaErrores.visibility = View.VISIBLE
        }
    }

    private fun iniciarGuardadoLocal(tipo: String) {
        tipoArchivoAGuardar = tipo
        if (tipo == "FORM" && etEditorCodigo.text.toString().trim().isEmpty()) return
        if (tipo == "PKM" && arbolASTActual == null) {
            Toast.makeText(this, "Compila primero", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, if (tipo == "FORM") "codigo.form" else "form.pkm")
        }
        guardarArchivoLauncher.launch(intent)
    }

    private val guardarArchivoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    val texto = if (tipoArchivoAGuardar == "FORM") etEditorCodigo.text.toString() else ExportadorPKM(Evaluador(TablaSimbolos())).generarArchivoPKM(arbolASTActual!!, "App", "App")
                    outputStream?.write(texto.toByteArray())
                    outputStream?.close()
                    Toast.makeText(this, "Guardado exitosamente", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun guardarEnLaNube() {
        if (arbolASTActual == null) return
        Toast.makeText(this, "Enviando...", Toast.LENGTH_SHORT).show()
        val textoPKM = ExportadorPKM(Evaluador(TablaSimbolos())).generarArchivoPKM(arbolASTActual!!, "Usuario", "Nube")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.compi1.proyecto1.utils.RetrofitClient.apiService.guardarFormulario(com.compi1.proyecto1.utils.FormularioPKM(autor = "Usuario", titulo = "Nube", contenidoPkm = textoPKM))
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Guardado en MySQL!", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error API: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // --- FUNCIONES DE PLANTILLAS Y COLOR ---
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
        val inicio = Math.max(etEditorCodigo.selectionStart, 0)
        val fin = Math.max(etEditorCodigo.selectionEnd, 0)
        etEditorCodigo.text.replace(Math.min(inicio, fin), Math.max(inicio, fin), texto)
    }
}
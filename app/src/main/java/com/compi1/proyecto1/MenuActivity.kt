package com.compi1.proyecto1

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenuActivity : ComponentActivity() {
    private var accionPendiente = "EDITAR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val btnCrearNuevo = findViewById<Button>(R.id.btnCrearNuevo)
        val btnAbrirLocal = findViewById<Button>(R.id.btnAbrirLocal)
        val btnAbrirNube = findViewById<Button>(R.id.btnAbrirNube)
        val btnResponder = findViewById<Button>(R.id.btnResponderMenu)

        // CREAR NUEVO: Simplemente abre el editor vacío
        btnCrearNuevo.setOnClickListener {
            abrirEspacioDeTrabajo("NUEVO", "")
        }

        // ABRIR LOCAL: Lanza el explorador de archivos
        btnAbrirLocal.setOnClickListener {
            accionPendiente = "EDITAR"
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            abrirArchivoLauncher.launch(intent)
        }

        // ABRIR DE LA NUBE: Consulta a la API
        btnAbrirNube.setOnClickListener {
            accionPendiente = "EDITAR"
            descargarFormulariosDeNube()
        }

        btnResponder.setOnClickListener {
            accionPendiente = "RESPONDER"
            val opciones = arrayOf("📁 Archivo en el Teléfono", "☁️ Formulario en la Nube")
            AlertDialog.Builder(this)
                .setTitle("¿Dónde está el formulario a llenar?")
                .setItems(opciones) { _, index ->
                    if (index == 0) {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        abrirArchivoLauncher.launch(intent)
                    } else {
                        descargarFormulariosDeNube()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // --- LOGICA DE ABRIR LOCAL ---
    private val abrirArchivoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val textoLeido = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

                    var nombreArchivo = ""
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index != -1) nombreArchivo = it.getString(index)
                        }
                    }

                    val tipo = if (nombreArchivo.lowercase().endsWith(".pkm")) "PKM" else "FORM"
                    abrirEspacioDeTrabajo(tipo, textoLeido)

                } catch (e: Exception) {
                    Toast.makeText(this, "Error al leer archivo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- LOGICA DE ABRIR NUBE ---
    private fun descargarFormulariosDeNube() {
        Toast.makeText(this, "Conectando con el servidor...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val lista = com.compi1.proyecto1.utils.RetrofitClient.apiService.obtenerTodosLosFormularios()

                withContext(Dispatchers.Main) {
                    if (lista.isEmpty()) {
                        Toast.makeText(this@MenuActivity, "No hay formularios en la nube", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    // Creamos un contenedor scrolleable
                    val scrollView = android.widget.ScrollView(this@MenuActivity)
                    val layoutPrincipal = android.widget.LinearLayout(this@MenuActivity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(40, 40, 40, 40)
                    }

                    val builder = AlertDialog.Builder(this@MenuActivity)
                        .setTitle("☁️ Formularios Disponibles")
                        .setView(scrollView)
                        .setNegativeButton("Cancelar", null)

                    val dialog = builder.create()

                    lista.forEach { form ->
                        val card = android.widget.LinearLayout(this@MenuActivity).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(40, 30, 40, 30)
                            val params = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            params.setMargins(0, 0, 0, 25) // Espacio entre tarjetas
                            layoutParams = params
                            setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                        }

                        val tvTitulo = android.widget.TextView(this@MenuActivity).apply {
                            text = form.titulo
                            textSize = 18f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(android.graphics.Color.BLACK)
                        }

                        val tvAutor = android.widget.TextView(this@MenuActivity).apply {
                            text = "Autor: ${form.autor}"
                            textSize = 14f
                            setTextColor(android.graphics.Color.DKGRAY)
                            setPadding(0, 8, 0, 0)
                        }

                        card.addView(tvTitulo)
                        card.addView(tvAutor)

                        card.setOnClickListener {
                            dialog.dismiss() // Cerramos el menú
                            abrirEspacioDeTrabajo("PKM", form.contenidoPkm) // Abrimos el formulario
                        }

                        layoutPrincipal.addView(card)
                    }

                    scrollView.addView(layoutPrincipal)
                    dialog.show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MenuActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- FUNCION PUENTE HACIA EL EDITOR ---
    private fun abrirEspacioDeTrabajo(modo: String, contenido: String) {
        val intent = Intent(this, MainActivity::class.java)
        // Le mandamos el modo (PKM o FORM) y el texto
        intent.putExtra("MODO", modo)
        intent.putExtra("CONTENIDO", contenido)

        // Le avisamos si viene a editar o a responder
        intent.putExtra("ACCION", accionPendiente)

        startActivity(intent)
    }
}
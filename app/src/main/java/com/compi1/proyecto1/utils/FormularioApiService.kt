package com.compi1.proyecto1.utils

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// 1. El modelo de datos en Kotlin (Idéntico a tu Formulario.java de Spring Boot)
data class FormularioPKM(
    val autor: String,
    val titulo: String,
    val contenidoPkm: String
)

// 2. Las rutas de tu API
interface FormularioApiService {

    @POST("/api/formularios")
    suspend fun guardarFormulario(@Body formulario: FormularioPKM): FormularioPKM

    @GET("/api/formularios")
    suspend fun obtenerTodosLosFormularios(): List<FormularioPKM>
}

// 3. El constructor de la conexión
object RetrofitClient {
    // IMPORTANTE: 10.0.2.2 es el localhost de tu compu visto desde el Emulador de Android
    private const val BASE_URL = "http://10.0.2.2:8080/"

    val apiService: FormularioApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FormularioApiService::class.java)
    }
}
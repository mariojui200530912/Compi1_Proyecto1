package com.compi1.proyecto1.utils

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class FormularioPKM(
    val autor: String,
    val titulo: String,
    val contenidoPkm: String
)

interface FormularioApiService {

    @POST("/api/formularios")
    suspend fun guardarFormulario(@Body formulario: FormularioPKM): FormularioPKM

    @GET("/api/formularios")
    suspend fun obtenerTodosLosFormularios(): List<FormularioPKM>
}

object RetrofitClient {
    // 10.0.2.2 es el localhost
    private const val BASE_URL = "http://10.0.2.2:8080/"

    val apiService: FormularioApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FormularioApiService::class.java)
    }
}
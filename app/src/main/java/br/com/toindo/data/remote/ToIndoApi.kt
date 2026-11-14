package br.com.toindo.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface ToIndoApi {

    @GET
    suspend fun ping(
        @Url url: String,
        @Header("X-Token") token: String,
    ): RespostaComandoDto

    @POST
    suspend fun enviarComando(
        @Url url: String,
        @Header("X-Token") token: String,
        @Body comando: ComandoDto,
    ): RespostaComandoDto
}

data class ComandoDto(
    val acao: String,
    val controle: String? = null,
    val valor: Int? = null,
)

data class RespostaComandoDto(
    val ok: Boolean = false,
    val estado: String? = null,
    val nome: String? = null,
)

package br.com.toindo.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface ToIndoApi {

    @POST("objetos/{endereco}/comando")
    suspend fun enviarComando(
        @Path("endereco", encoded = true) endereco: String,
        @Body comando: ComandoDto,
    ): RespostaComandoDto
}

@JsonClass(generateAdapter = true)
data class ComandoDto(
    val acao: String,
    val controle: String? = null,
    val valor: Int? = null,
)

@JsonClass(generateAdapter = true)
data class RespostaComandoDto(
    val ok: Boolean,
    val estado: String? = null,
)

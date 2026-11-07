package br.com.toindo.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato HTTP com o servidor/microcontrolador que comanda o ar-condicionado
 * (seção 5.1 — "possivelmente MQTT; simulação" e RNF03 — HTTPS autenticado).
 * Em produção, troca-se a base URL por um gateway real; no protótipo usamos um mock.
 */
interface ToIndoApi {

    @POST("objetos/{endereco}/comando")
    suspend fun enviarComando(
        @Path("endereco", encoded = true) endereco: String,
        @Body comando: ComandoDto,
    ): RespostaComandoDto
}

@JsonClass(generateAdapter = true)
data class ComandoDto(
    /** "LIGAR", "DESLIGAR" ou "AJUSTAR". */
    val acao: String,
    /** Nome do controle quando AJUSTAR (ex.: "TEMPERATURA"); null nos demais. */
    val controle: String? = null,
    val valor: Int? = null,
)

@JsonClass(generateAdapter = true)
data class RespostaComandoDto(
    val ok: Boolean,
    val estado: String? = null,
)

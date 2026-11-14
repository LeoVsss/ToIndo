package br.com.toindo.data.remote

import br.com.toindo.BuildConfig
import br.com.toindo.model.Controle
import kotlin.coroutines.cancellation.CancellationException

interface ServicoDispositivo {
    suspend fun ping(endereco: String): Boolean
    suspend fun ligar(endereco: String): Boolean
    suspend fun desligar(endereco: String): Boolean
    suspend fun ajustar(endereco: String, controle: Controle, valor: Int): Boolean
}

class ServicoDispositivoHttp(private val api: ToIndoApi) : ServicoDispositivo {

    override suspend fun ping(endereco: String) =
        executar { api.ping(urlDe(endereco, "ping"), BuildConfig.TOKEN_DISPOSITIVO) }

    override suspend fun ligar(endereco: String) =
        comando(endereco, ComandoDto("LIGAR"))

    override suspend fun desligar(endereco: String) =
        comando(endereco, ComandoDto("DESLIGAR"))

    override suspend fun ajustar(endereco: String, controle: Controle, valor: Int) =
        comando(endereco, ComandoDto("AJUSTAR", controle.name, valor))

    private suspend fun comando(endereco: String, comando: ComandoDto) =
        executar { api.enviarComando(urlDe(endereco, "comando"), BuildConfig.TOKEN_DISPOSITIVO, comando) }

    private suspend fun executar(chamada: suspend () -> RespostaComandoDto): Boolean =
        try {
            chamada().ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    companion object {
        fun urlDe(endereco: String, caminho: String): String {
            val base = endereco.trim().trimEnd('/')
            val comEsquema = if (base.startsWith("http://") || base.startsWith("https://")) base else "http://$base"
            return "$comEsquema/$caminho"
        }
    }
}

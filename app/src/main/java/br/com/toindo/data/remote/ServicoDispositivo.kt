package br.com.toindo.data.remote

import android.util.Log
import br.com.toindo.model.Controle
import kotlinx.coroutines.delay

/**
 * Fachada que o repositório usa para comandar um objeto remotamente (RF06).
 * Abstrai o transporte: hoje um mock (sem hardware — risco da seção 6), amanhã Retrofit real.
 */
interface ServicoDispositivo {
    suspend fun ligar(endereco: String): Boolean
    suspend fun desligar(endereco: String): Boolean
    suspend fun ajustar(endereco: String, controle: Controle, valor: Int): Boolean
}

/** Implementação real sobre [ToIndoApi]. Resultado booleano = o comando foi aceito. */
class ServicoDispositivoRetrofit(private val api: ToIndoApi) : ServicoDispositivo {
    override suspend fun ligar(endereco: String) =
        api.enviarComando(endereco, ComandoDto("LIGAR")).ok

    override suspend fun desligar(endereco: String) =
        api.enviarComando(endereco, ComandoDto("DESLIGAR")).ok

    override suspend fun ajustar(endereco: String, controle: Controle, valor: Int) =
        api.enviarComando(endereco, ComandoDto("AJUSTAR", controle.name, valor)).ok
}

/**
 * Mock usado quando não há servidor/hardware (mitigação do risco "Falta de hardware real").
 * Simula a latência de rede e sempre confirma o comando.
 */
class ServicoDispositivoMock : ServicoDispositivo {
    private val TAG = "ServicoDispositivo"
    override suspend fun ligar(endereco: String): Boolean {
        delay(300); Log.i(TAG, "LIGAR $endereco"); return true
    }
    override suspend fun desligar(endereco: String): Boolean {
        delay(300); Log.i(TAG, "DESLIGAR $endereco"); return true
    }
    override suspend fun ajustar(endereco: String, controle: Controle, valor: Int): Boolean {
        delay(200); Log.i(TAG, "AJUSTAR $endereco ${controle.name}=$valor"); return true
    }
}

package br.com.toindo.data.remote

import android.util.Log
import br.com.toindo.model.Controle
import kotlinx.coroutines.delay

interface ServicoDispositivo {
    suspend fun ligar(endereco: String): Boolean
    suspend fun desligar(endereco: String): Boolean
    suspend fun ajustar(endereco: String, controle: Controle, valor: Int): Boolean
}

class ServicoDispositivoRetrofit(private val api: ToIndoApi) : ServicoDispositivo {
    override suspend fun ligar(endereco: String) =
        api.enviarComando(endereco, ComandoDto("LIGAR")).ok

    override suspend fun desligar(endereco: String) =
        api.enviarComando(endereco, ComandoDto("DESLIGAR")).ok

    override suspend fun ajustar(endereco: String, controle: Controle, valor: Int) =
        api.enviarComando(endereco, ComandoDto("AJUSTAR", controle.name, valor)).ok
}

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

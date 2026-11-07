package br.com.toindo

import android.app.Application
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.data.remote.ServicoDispositivoMock

/**
 * Ponto de composição / injeção de dependência leve (seção 5 — "injeção de dependência").
 * Inicializa o repositório com Room e o serviço de comando antes de qualquer tela abrir.
 *
 * Para usar o servidor real, troque [ServicoDispositivoMock] por:
 *   ServicoDispositivoRetrofit(Rede.criarApi())
 */
class ToIndoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        br.com.toindo.prototipo.EstadoPrototipo.iniciar(this)
        RepositorioToIndo.iniciar(
            context = this,
            servicoDispositivo = ServicoDispositivoMock(),
        )
    }
}

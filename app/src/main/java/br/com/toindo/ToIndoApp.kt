package br.com.toindo

import android.app.Application
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.data.remote.ServicoDispositivoMock

class ToIndoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RepositorioToIndo.iniciar(
            context = this,
            servicoDispositivo = ServicoDispositivoMock(),
        )
    }
}

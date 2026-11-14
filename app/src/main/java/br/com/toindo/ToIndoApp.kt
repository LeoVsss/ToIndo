package br.com.toindo

import android.app.Application
import br.com.toindo.data.RepositorioToIndo

class ToIndoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RepositorioToIndo.iniciar(this)
    }
}

package br.com.toindo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import br.com.toindo.service.PresencaService
import br.com.toindo.ui.navigation.ToIndoApp
import br.com.toindo.ui.theme.ToIndoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lerPedidoDeNotificacao(intent)
        setContent {
            ToIndoTheme {
                ToIndoApp()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lerPedidoDeNotificacao(intent)
    }

    /** Notificação do protótipo ("Dispositivos desligados") pede para abrir o detalhe da saída. */
    private fun lerPedidoDeNotificacao(intent: android.content.Intent?) {
        val id = intent?.getLongExtra(br.com.toindo.prototipo.PedidoAbrirSaida.EXTRA, 0L) ?: 0L
        if (id != 0L) {
            br.com.toindo.prototipo.PedidoAbrirSaida.pendente.value = id
            intent?.removeExtra(br.com.toindo.prototipo.PedidoAbrirSaida.EXTRA)
        }
    }

    override fun onResume() {
        super.onResume()
        // Liga a automação por presença assim que houver permissão de localização (RF03–RF05).
        val temLocalizacao = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (temLocalizacao) PresencaService.iniciar(this)
    }
}

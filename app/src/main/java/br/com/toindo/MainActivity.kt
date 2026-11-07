package br.com.toindo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import br.com.toindo.service.PedidoAbrirSaida
import br.com.toindo.service.PresencaService
import br.com.toindo.ui.navigation.ToIndoApp
import br.com.toindo.ui.theme.ToIndoTheme

class MainActivity : ComponentActivity() {

    /** Pede localização precisa (GPS) e notificações juntos; com o GPS liberado, inicia o monitoramento. */
    private val pedirPermissoes = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        iniciarMonitoramentoSePuder()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lerPedidoDeNotificacao(intent)
        setContent {
            ToIndoTheme {
                ToIndoApp()
            }
        }
        if (savedInstanceState == null) pedirPermissoesQueFaltam()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lerPedidoDeNotificacao(intent)
    }

    override fun onResume() {
        super.onResume()
        iniciarMonitoramentoSePuder()
    }

    private fun pedirPermissoesQueFaltam() {
        val faltam = buildList {
            if (!tem(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 33 && !tem(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (faltam.isNotEmpty()) pedirPermissoes.launch(faltam.toTypedArray())
    }

    /** Liga a automação por presença (RF03–RF05) — exige localização PRECISA (GPS). */
    private fun iniciarMonitoramentoSePuder() {
        if (tem(Manifest.permission.ACCESS_FINE_LOCATION)) PresencaService.iniciar(this)
    }

    private fun tem(permissao: String) =
        ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED

    /** Notificação "Dispositivos desligados/ligados" pede para abrir o detalhe da saída. */
    private fun lerPedidoDeNotificacao(intent: Intent?) {
        val id = intent?.getLongExtra(PedidoAbrirSaida.EXTRA, 0L) ?: 0L
        if (id != 0L) {
            PedidoAbrirSaida.pendente.value = id
            intent?.removeExtra(PedidoAbrirSaida.EXTRA)
        }
    }
}

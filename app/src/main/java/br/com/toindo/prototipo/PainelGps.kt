package br.com.toindo.prototipo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Painel do modo GPS REAL do protótipo: liga o GPS do celular, define a casa onde você
 * está agora e ajusta o tamanho do "radar" (raio de saída).
 */
@Composable
fun PainelGps() {
    val context = LocalContext.current
    val ativo by GpsPrototipo.ativo.collectAsStateWithLifecycle()
    val precisao by GpsPrototipo.precisaoM.collectAsStateWithLifecycle()
    val residencia by EstadoPrototipo.residencia.collectAsStateWithLifecycle()

    val pedirNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Android 13+: sem esta permissão a notificação "Dispositivos desligados" não aparece.
    fun pedirNotificacaoSeFaltar() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            GpsPrototipo.iniciar(context)
            pedirNotificacaoSeFaltar()
        }
    }

    fun ligar() {
        val tem = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (tem) {
            GpsPrototipo.iniciar(context)
            pedirNotificacaoSeFaltar()
        } else pedirPermissao.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Teste com GPS real", style = MaterialTheme.typography.titleMedium)
            Text(
                "1) Ligue o GPS  2) Fique em casa e toque em \"Definir casa aqui\"  " +
                    "3) Ajuste o raio  4) Afaste-se com o celular.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (!ativo) {
                Button(onClick = { ligar() }, modifier = Modifier.fillMaxWidth()) { Text("Ligar GPS real") }
            } else {
                OutlinedButton(onClick = { GpsPrototipo.parar(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Desligar GPS (voltar ao simulador)")
                }
                Text(
                    when {
                        precisao == null -> "Aguardando sinal de GPS..."
                        precisao!! > GpsPrototipo.PRECISAO_MAX_M ->
                            "Sinal fraco (±${precisao!!.toInt()} m). Vá para perto de uma janela ou área aberta."
                        else -> "GPS ok (precisão ±${precisao!!.toInt()} m)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { GpsPrototipo.definirCasaNaProximaLeitura = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Definir casa aqui (onde estou agora)") }

                Text("Raio da casa (radar): ${residencia.raioSaidaM} m", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = residencia.raioSaidaM.toFloat(),
                    onValueChange = {
                        val saida = it.toInt()
                        EstadoPrototipo.definirRaios(saida, maxOf(residencia.raioAproximacaoM, saida))
                    },
                    valueRange = 5f..500f,
                )
                Text(
                    "Raio de chegada: ${residencia.raioAproximacaoM} m",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = residencia.raioAproximacaoM.toFloat(),
                    onValueChange = { EstadoPrototipo.definirRaios(residencia.raioSaidaM, it.toInt()) },
                    valueRange = 5f..2000f,
                )
                Row { Text("Dica: raios abaixo de ~15 m podem gerar saídas falsas, pois o GPS erra uns 5 a 20 m. Para testar, use o simulador ou espere o GPS ficar com precisão de ±5 m.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

package br.com.toindo.prototipo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.toindo.model.EstadoPresenca
import br.com.toindo.model.ObjetoInteligente
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

/**
 * Tela do PROTÓTIPO (sem BD, sem Arduino): simula o GPS do morador.
 *
 * Fluxo de demonstração:
 *   1. "Definir minha casa aqui" fixa a casa no ponto 0 do controle.
 *   2. Arraste o controle de distância (ou use os botões) para "andar" para longe.
 *   3. Ao passar do raio de saída e confirmar, os objetos desligam sozinhos.
 *   4. Volte para perto: os que ligam sozinhos religam ao entrar no raio de aproximação.
 *   5. O log mostra cada decisão; o estado fica salvo no app enquanto ele estiver aberto.
 */
@Composable
fun SimulacaoScreen(onVoltar: () -> Unit, onAbrirSaidas: () -> Unit = {}) {
    val saidas by androidx.compose.runtime.remember { EstadoPrototipo.saidas() }
        .collectAsStateWithLifecycle(emptyList())
    val residencia by EstadoPrototipo.residencia.collectAsStateWithLifecycle()
    val presenca by EstadoPrototipo.presenca.collectAsStateWithLifecycle()
    val objetos by EstadoPrototipo.objetos.collectAsStateWithLifecycle()
    val minhaLat by EstadoPrototipo.minhaLat.collectAsStateWithLifecycle()
    val minhaLon by EstadoPrototipo.minhaLon.collectAsStateWithLifecycle()
    val log by EstadoPrototipo.log.collectAsStateWithLifecycle()
    val usandoGps by GpsPrototipo.ativo.collectAsStateWithLifecycle()

    // Distância precisa ser recalculada quando a posição ou casa mudam.
    val distancia = rememberDistancia(minhaLat, minhaLon, residencia)

    // Ticker: enquanto confirma a saída, re-avalia a cada 500 ms (a transição depende do tempo).
    LaunchedEffect(presenca) {
        while (presenca == EstadoPresenca.CONFIRMANDO_SAIDA) {
            delay(500)
            EstadoPrototipo.reavaliar()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Simulador de presença", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onVoltar) { Text("Fechar") }
        }
        Text(
            "Protótipo em memória (sem banco, sem Arduino). Mova sua posição e veja a automação agir.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PainelGps()

        Button(onClick = onAbrirSaidas, modifier = Modifier.fillMaxWidth()) {
            Text("Ver histórico de saídas (${saidas.size})")
        }

        // ---- Painel de presença ----
        Card(colors = CardDefaults.cardColors(containerColor = corPresenca(presenca))) {
            Column(Modifier.padding(16.dp)) {
                Text("Você está:", style = MaterialTheme.typography.labelLarge)
                Text(rotuloPresenca(presenca), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Distância da casa: ${distancia.toInt()} m", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Raio de saída: ${residencia.raioSaidaM} m · aproximação: ${residencia.raioAproximacaoM} m",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Controle de "caminhada" (distância simulada em metros para o leste) ----
        Text("Sua distância da casa", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = distancia.coerceIn(0.0, 3000.0).toFloat(),
            onValueChange = { metros ->
                // Recoloca o morador a 'metros' da casa, para leste.
                val graus = metros / (111_320.0 * Math.cos(Math.toRadians(residencia.latitude)))
                EstadoPrototipo.moverPara(residencia.latitude, residencia.longitude + graus)
            },
            valueRange = 0f..3000f,
            enabled = !usandoGps,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { EstadoPrototipo.moverPara(residencia.latitude, residencia.longitude) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Home, null, modifier = Modifier.size(18.dp)); Text(" Em casa")
            }
            OutlinedButton(onClick = { EstadoPrototipo.afastar(-200) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Remove, null, modifier = Modifier.size(18.dp)); Text(" 200m")
            }
            OutlinedButton(onClick = { EstadoPrototipo.afastar(200) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp)); Text(" 200m")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { EstadoPrototipo.definirCasaNaPosicaoAtual() }, modifier = Modifier.weight(1f)) {
                Text("Definir casa aqui")
            }
            OutlinedButton(onClick = { EstadoPrototipo.reiniciar() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp)); Text(" Reiniciar")
            }
        }

        // ---- Dispositivos (estado em memória) ----
        Text("Dispositivos", style = MaterialTheme.typography.titleSmall)
        objetos.forEach { o -> CartaoDispositivo(o) }

        // ---- Log da simulação ----
        Spacer(Modifier.height(8.dp))
        Text("O que aconteceu", style = MaterialTheme.typography.titleSmall)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                log.forEach { linha ->
                    Text(
                        "${linha.hora}  ${linha.texto}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CartaoDispositivo(o: ObjetoInteligente) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.nome, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${o.comodo} · ${if (o.estado == br.com.toindo.model.EstadoDispositivo.INDISPONIVEL) "OFFLINE" else if (o.ligado) "LIGADO" else "DESLIGADO"}" + if (o.automacaoPausada) " · manual" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (o.ligado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (o.ligado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
            ) {
                Icon(
                    Icons.Rounded.PowerSettingsNew, contentDescription = null,
                    tint = if (o.ligado) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = { EstadoPrototipo.alternarOffline(o.id) }, modifier = Modifier.padding(start = 8.dp)) {
            Text(if (o.estado == br.com.toindo.model.EstadoDispositivo.INDISPONIVEL) "Voltar online" else "Simular offline")
        }
        TextButton(onClick = { EstadoPrototipo.alternarManual(o.id) }, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
            Text(if (o.ligado) "Desligar manualmente" else "Ligar manualmente")
        }
    }
}

@Composable
private fun rememberDistancia(lat: Double, lon: Double, residencia: br.com.toindo.model.Residencia): Double {
    return br.com.toindo.domain.Geo.distanciaM(lat, lon, residencia.latitude, residencia.longitude)
}

private fun rotuloPresenca(p: EstadoPresenca) = when (p) {
    EstadoPresenca.EM_CASA -> "EM CASA"
    EstadoPresenca.CONFIRMANDO_SAIDA -> "CONFIRMANDO SAÍDA..."
    EstadoPresenca.FORA -> "FORA DE CASA"
    EstadoPresenca.CHEGANDO -> "CHEGANDO"
}

@Composable
private fun corPresenca(p: EstadoPresenca): Color = when (p) {
    EstadoPresenca.EM_CASA -> MaterialTheme.colorScheme.primaryContainer
    EstadoPresenca.CONFIRMANDO_SAIDA -> MaterialTheme.colorScheme.tertiaryContainer
    EstadoPresenca.FORA -> MaterialTheme.colorScheme.errorContainer
    EstadoPresenca.CHEGANDO -> MaterialTheme.colorScheme.secondaryContainer
}

package br.com.toindo.prototipo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val formatoData = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR"))
private fun hora(millis: Long) = formatoData.format(Date(millis))

private fun textoSaida(r: String) = when (r) {
    EstadoPrototipo.DESLIGOU -> "Desligou"
    EstadoPrototipo.CONTINUOU_LIGADO -> "Continuou ligado"
    EstadoPrototipo.JA_DESLIGADO -> "Já estava desligado"
    EstadoPrototipo.OFFLINE -> "Offline"
    else -> r
}

private fun textoVolta(r: String?) = when (r) {
    null -> "Ainda não voltou"
    EstadoPrototipo.LIGOU -> "Ligou"
    EstadoPrototipo.JA_LIGADO -> "Estava ligado"
    EstadoPrototipo.CONTINUOU_DESLIGADO -> "Continuou desligado"
    EstadoPrototipo.OFFLINE -> "Offline"
    else -> r
}

/** Lista de saídas de casa (a contagem é o tamanho da lista). */
@Composable
fun SaidasScreen(onVoltar: () -> Unit, onAbrirSaida: (Long) -> Unit) {
    val saidas by remember { EstadoPrototipo.saidas() }.collectAsStateWithLifecycle(emptyList())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row {
            Text("Histórico de saídas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onVoltar) { Text("Voltar") }
        }
        Text(
            "Você saiu de casa ${saidas.size} ${if (saidas.size == 1) "vez" else "vezes"}.",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        )

        if (saidas.isEmpty()) {
            Text(
                "Nenhuma saída registrada ainda. Saia do raio da casa e aguarde a confirmação.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        saidas.forEachIndexed { i, s ->
            val desligou = s.itens.count { it.naSaida == EstadoPrototipo.DESLIGOU }
            val offline = s.itens.count { it.naSaida == EstadoPrototipo.OFFLINE }
            val ligou = s.itens.count { it.naVolta == EstadoPrototipo.LIGOU }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onAbrirSaida(s.saida.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Saída #${saidas.size - i}", style = MaterialTheme.typography.titleSmall)
                    Text("Saiu às ${hora(s.saida.saiuEmMillis)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        s.saida.voltouEmMillis?.let { "Voltou às ${hora(it)}" } ?: "Ainda fora de casa",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "$desligou desligado(s) · $ligou ligado(s) na volta · $offline offline",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (saidas.isNotEmpty()) {
            TextButton(onClick = { EstadoPrototipo.apagarHistorico() }) { Text("Apagar histórico") }
        }
    }
}

/** Detalhe de uma saída: o que aconteceu com cada dispositivo ao sair e ao voltar. */
@Composable
fun SaidaDetalheScreen(saidaId: Long, onVoltar: () -> Unit) {
    val dados by remember(saidaId) { EstadoPrototipo.saida(saidaId) }.collectAsStateWithLifecycle(null)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row {
            Text("Detalhes da saída", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onVoltar) { Text("Voltar") }
        }

        val d = dados
        if (d == null) {
            Text("Saída não encontrada (o histórico pode ter sido apagado).", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Text("Saiu às ${hora(d.saida.saiuEmMillis)}", style = MaterialTheme.typography.titleMedium)
        Text(
            d.saida.voltouEmMillis?.let { "Voltou às ${hora(it)}" } ?: "Ainda fora de casa",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Distância máxima: ${d.saida.distanciaMaxM} m", style = MaterialTheme.typography.bodySmall)

        Secao("Desligados ao sair", d.itens.filter { it.naSaida == EstadoPrototipo.DESLIGOU })
        Secao("Continuaram ligados", d.itens.filter { it.naSaida == EstadoPrototipo.CONTINUOU_LIGADO }, mostrarMotivo = true)
        Secao("Já estavam desligados", d.itens.filter { it.naSaida == EstadoPrototipo.JA_DESLIGADO })
        Secao("Offline (sem resposta)", d.itens.filter { it.naSaida == EstadoPrototipo.OFFLINE })

        Text("Na volta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        d.itens.forEach {
            Text("• ${it.nome}: ${textoVolta(it.naVolta)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Secao(titulo: String, itens: List<SaidaItemEntity>, mostrarMotivo: Boolean = false) {
    if (itens.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$titulo (${itens.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            itens.forEach {
                Text("${it.nome} · ${it.comodo}", style = MaterialTheme.typography.bodyMedium)
                if (mostrarMotivo) {
                    Text(it.motivoSaida, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

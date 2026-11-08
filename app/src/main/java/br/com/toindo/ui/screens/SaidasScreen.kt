package br.com.toindo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.data.local.ResultadoSaida
import br.com.toindo.data.local.ResultadoVolta
import br.com.toindo.data.local.SaidaComItens
import br.com.toindo.data.local.SaidaItemEntity
import br.com.toindo.ui.components.CardToIndo
import br.com.toindo.ui.components.LinhaDeLista
import br.com.toindo.ui.components.RotuloDeSecao
import br.com.toindo.ui.components.TopoComVoltar
import br.com.toindo.ui.theme.Espaco
import br.com.toindo.ui.theme.ToIndo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val formatoDataHora = SimpleDateFormat("dd/MM 'às' HH:mm:ss", Locale("pt", "BR"))
private fun quando(millis: Long) = formatoDataHora.format(Date(millis))

private fun duracao(s: SaidaComItens): String? {
    val volta = s.saida.voltouEmMillis ?: return null
    val seg = (volta - s.saida.saiuEmMillis) / 1000
    return when {
        seg < 60 -> "${seg}s fora"
        seg < 3600 -> "${seg / 60} min fora"
        else -> "${seg / 3600}h${"%02d".format((seg % 3600) / 60)} fora"
    }
}

private fun textoVolta(r: String?) = when (r) {
    null -> "ainda fora de casa"
    ResultadoVolta.LIGOU -> "ligou de novo"
    ResultadoVolta.JA_LIGADO -> "já estava ligado"
    ResultadoVolta.CONTINUOU_DESLIGADO -> "continuou desligado"
    ResultadoVolta.NAO_RELIGA_SEGURANCA -> "não religa sozinho (segurança)"
    ResultadoVolta.OFFLINE -> "offline"
    else -> r
}

@Composable
fun SaidasScreen(onVoltar: () -> Unit, onAbrirSaida: (Long) -> Unit) {
    val saidas by RepositorioToIndo.saidas.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TopoComVoltar(titulo = "Saídas de casa", onVoltar = onVoltar)
        Text(
            "Você saiu de casa ${saidas.size} ${if (saidas.size == 1) "vez" else "vezes"}",
            style = MaterialTheme.typography.titleMedium,
        )

        if (saidas.isEmpty()) {
            Text(
                "Nenhuma saída registrada ainda. Afaste-se da casa com o celular até passar do raio de saída.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        saidas.forEachIndexed { i, s ->
            val desligados = s.itens.count { it.naSaida == ResultadoSaida.DESLIGOU }
            val religados = s.itens.count { it.naVolta == ResultadoVolta.LIGOU }
            val offline = s.itens.count { it.naSaida == ResultadoSaida.OFFLINE || it.naVolta == ResultadoVolta.OFFLINE }
            CardToIndo(padding = Espaco.l) {
                LinhaDeLista(
                    "Saída #${saidas.size - i} · ${quando(s.saida.saiuEmMillis)}",
                    detalhe = buildString {
                        append(duracao(s) ?: "Ainda fora de casa")
                        append(" · $desligados desligado(s) · $religados religado(s)")
                        if (offline > 0) append(" · $offline offline")
                    },
                    onClick = { onAbrirSaida(s.saida.id) },
                    divisor = false,
                )
            }
        }

        if (saidas.isNotEmpty()) {
            TextButton(onClick = { RepositorioToIndo.apagarHistoricoSaidas() }, modifier = Modifier.padding(bottom = Espaco.l)) {
                Text("Apagar histórico de saídas", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SaidaDetalheScreen(saidaId: Long, onVoltar: () -> Unit) {
    val dados by remember(saidaId) { RepositorioToIndo.saida(saidaId) }.collectAsStateWithLifecycle(null)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TopoComVoltar(titulo = "Detalhes da saída", onVoltar = onVoltar)

        val d = dados
        if (d == null) {
            Text("Saída não encontrada (o histórico pode ter sido apagado).", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        CardToIndo(padding = Espaco.l) {
            LinhaDeLista("Saiu", valor = quando(d.saida.saiuEmMillis))
            LinhaDeLista("Voltou", valor = d.saida.voltouEmMillis?.let { quando(it) } ?: "ainda fora")
            LinhaDeLista("Mais longe que chegou", valor = "${d.saida.distanciaMaxM} m", divisor = false)
        }

        Grupo("Desligados ao sair", d.itens.filter { it.naSaida == ResultadoSaida.DESLIGOU }, ToIndo.extras.economiaTexto)
        Grupo("Continuaram ligados", d.itens.filter { it.naSaida == ResultadoSaida.CONTINUOU_LIGADO }, ToIndo.extras.energiaTexto, motivo = true)
        Grupo("Já estavam desligados", d.itens.filter { it.naSaida == ResultadoSaida.JA_DESLIGADO }, MaterialTheme.colorScheme.onSurfaceVariant)
        Grupo("Offline (sem resposta)", d.itens.filter { it.naSaida == ResultadoSaida.OFFLINE }, MaterialTheme.colorScheme.error, motivo = true)

        RotuloDeSecao("Na volta para casa")
        CardToIndo(padding = Espaco.l, modifier = Modifier.padding(bottom = Espaco.l)) {
            d.itens.forEachIndexed { i, item ->
                LinhaDeLista(
                    item.nome,
                    valor = textoVolta(item.naVolta),
                    corValor = if (item.naVolta == ResultadoVolta.LIGOU) ToIndo.extras.economiaTexto else MaterialTheme.colorScheme.onSurfaceVariant,
                    divisor = i < d.itens.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun Grupo(titulo: String, itens: List<SaidaItemEntity>, cor: Color, motivo: Boolean = false) {
    if (itens.isEmpty()) return
    RotuloDeSecao("$titulo (${itens.size})")
    CardToIndo(padding = Espaco.l) {
        itens.forEachIndexed { i, item ->
            LinhaDeLista(
                item.nome,
                detalhe = if (motivo) item.motivo else item.comodo,
                corValor = cor,
                divisor = i < itens.lastIndex,
            )
        }
    }
}

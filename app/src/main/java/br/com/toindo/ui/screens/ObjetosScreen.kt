package br.com.toindo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.model.EstadoDispositivo
import br.com.toindo.ui.components.BotaoPrincipal
import br.com.toindo.ui.components.CardToIndo
import br.com.toindo.ui.components.LinhaDeLista
import br.com.toindo.ui.components.TopoComVoltar
import br.com.toindo.ui.theme.Espaco

@Composable
fun ObjetosScreen(onVoltar: () -> Unit, onAbrirObjeto: (Long) -> Unit, onAdicionar: () -> Unit) {
    val objetos by RepositorioToIndo.objetos.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TopoComVoltar(titulo = "Objetos inteligentes", onVoltar = onVoltar)

        if (objetos.isEmpty()) {
            Text(
                "Nenhum objeto cadastrado ainda.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CardToIndo(padding = Espaco.l) {
                objetos.forEachIndexed { i, o ->
                    LinhaDeLista(
                        o.nome,
                        detalhe = "${o.comodo} · ${o.potenciaW} W · ${o.enderecoRemoto}",
                        valor = when (o.estado) {
                            EstadoDispositivo.LIGADO -> "Ligado"
                            EstadoDispositivo.DESLIGADO -> "Desligado"
                            EstadoDispositivo.INDISPONIVEL -> "Sem conexão"
                        },
                        onClick = { onAbrirObjeto(o.id) },
                        divisor = i < objetos.lastIndex,
                    )
                }
            }
        }

        BotaoPrincipal("Adicionar objeto", onClick = onAdicionar, modifier = Modifier.padding(bottom = Espaco.xl))
    }
}
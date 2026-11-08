package br.com.toindo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.model.EventoObjeto
import br.com.toindo.model.OrigemEvento
import br.com.toindo.ui.components.CardToIndo
import br.com.toindo.ui.components.ChipOrigem
import br.com.toindo.ui.components.Faixa
import br.com.toindo.ui.components.RotuloDeSecao
import br.com.toindo.ui.components.TituloDeTela
import br.com.toindo.ui.components.icone
import br.com.toindo.ui.theme.Espaco
import br.com.toindo.ui.theme.ToIndo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoricoUi(
    val filtro: OrigemEvento? = null,
    val porDia: Map<String, List<EventoObjeto>> = emptyMap(),
    val pendentes: Int = 0,
)

class HistoricoViewModel : ViewModel() {
    private val filtro = MutableStateFlow<OrigemEvento?>(null)
    val ui = combine(RepositorioToIndo.eventos, filtro) { eventos, f ->
        HistoricoUi(
            filtro = f,
            porDia = eventos.filter { f == null || it.origem == f }.groupBy { it.dia },
            pendentes = eventos.count { !it.sincronizado },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoricoUi())

    fun filtrar(o: OrigemEvento?) { filtro.value = o }
}

@Composable
fun HistoricoScreen(vm: HistoricoViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem, vertical = Espaco.l),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TituloDeTela("Histórico")
        Row(horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
            FilterChip(ui.filtro == null, { vm.filtrar(null) }, { Text("Todos") }, shape = MaterialTheme.shapes.extraSmall)
            OrigemEvento.entries.forEach { o ->
                FilterChip(ui.filtro == o, { vm.filtrar(o) }, { Text(o.rotulo) }, shape = MaterialTheme.shapes.extraSmall)
            }
        }
        if (ui.pendentes > 0) {
            Faixa(
                "Sem internet: ${ui.pendentes} ${if (ui.pendentes == 1) "evento aguardando" else "eventos aguardando"} sincronização",
                Icons.Rounded.WifiOff, ToIndo.extras.energiaFundo, ToIndo.extras.energiaTexto,
            )
        }
        ui.porDia.forEach { (dia, eventos) ->
            RotuloDeSecao(dia)
            CardToIndo(padding = Espaco.l) {
                eventos.forEachIndexed { i, e ->
                    LinhaEvento(e)
                    if (i < eventos.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
                }
            }
        }
    }
}

@Composable
private fun LinhaEvento(e: EventoObjeto) {
    val automatico = e.origem == OrigemEvento.AUTOMATICO
    Row(Modifier.fillMaxWidth().padding(vertical = Espaco.m).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Espaco.m)) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (automatico) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                e.tipoObjeto?.icone() ?: Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = if (automatico) ToIndo.extras.presencaTexto else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(e.titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(e.hora, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                ChipOrigem(e.origem)
                Text(e.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!e.sincronizado) Text("· pendente", style = MaterialTheme.typography.bodySmall, color = ToIndo.extras.energiaTexto)
            }
        }
    }
}

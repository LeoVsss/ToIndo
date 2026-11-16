package br.com.toindo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.ui.components.CardToIndo
import br.com.toindo.ui.components.LinhaDeLista
import br.com.toindo.ui.components.SeloEstimado
import br.com.toindo.ui.components.TituloDeTela
import br.com.toindo.ui.theme.Espaco
import br.com.toindo.ui.theme.ToIndo
import br.com.toindo.model.ObjetoInteligente
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import br.com.toindo.data.local.ResultadoSaida
import br.com.toindo.domain.CalculadoraConsumo
import br.com.toindo.model.ConsumoObjeto
import br.com.toindo.domain.AgregadorConsumo
import br.com.toindo.domain.BarraConsumo
import br.com.toindo.domain.Periodo
import br.com.toindo.domain.SessaoConsumo

// #MVVM
class ConsumoViewModel : ViewModel() {
    private val _periodo = MutableStateFlow(Periodo.SEMANA)
    val periodo = _periodo.asStateFlow()
    fun escolher(p: Periodo) { _periodo.value = p }

    private val relogio = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    private fun registrosEmAndamento(objetos: List<ObjetoInteligente>, agora: Long, tarifa: Double) =
        objetos.mapNotNull { o ->
            val inicio = o.ligadoDesdeMillis?.takeIf { o.ligado && it < agora } ?: return@mapNotNull null
            val kWh = CalculadoraConsumo.kWh(o.potenciaW, agora - inicio)
            SessaoConsumo(o.id, o.nome, inicio, agora, kWh, CalculadoraConsumo.custo(kWh, tarifa))
        }

    val ui: StateFlow<ConsumoUi> = combine(
        _periodo, RepositorioToIndo.registrosConsumo, RepositorioToIndo.saidas, RepositorioToIndo.configuracao,
        combine(RepositorioToIndo.objetos, relogio) { objetos, agora -> objetos to agora },
    ) { p, registrosFechados, saidas, config, estado ->
        val agora = estado.second
        val sessoes = registrosFechados.map {
            SessaoConsumo(it.objetoId, it.objetoNome, it.inicioMillis, it.fimMillis, it.kWh, it.custo)
        } + registrosEmAndamento(estado.first, agora, config.tarifaKwh)
        val resumo = AgregadorConsumo.resumir(sessoes, p, agora)

        val kWhEvitado = saidas.sumOf { s ->
            val fim = s.saida.voltouEmMillis ?: agora
            val noPeriodo = AgregadorConsumo.sobreposicaoMillis(s.saida.saiuEmMillis, fim, resumo.inicioMillis, resumo.fimMillis)
            s.itens.filter { it.naSaida == ResultadoSaida.DESLIGOU }
                .sumOf { CalculadoraConsumo.kWh(if (it.potenciaW > 0) it.potenciaW else RepositorioToIndo.potenciaW(it.objetoId), noPeriodo) }
        }

        ConsumoUi(
            totalKwh = resumo.totalKwh,
            totalCusto = resumo.totalCusto,
            porObjeto = resumo.porObjeto.map { ConsumoObjeto(it.nome, it.kWh, it.custo) },
            economia = CalculadoraConsumo.custo(kWhEvitado, config.tarifaKwh),
            tarifa = config.tarifaKwh,
            barras = resumo.barras,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConsumoUi())
}
data class ConsumoUi(
    val totalKwh: Double = 0.0,
    val totalCusto: Double = 0.0,
    val porObjeto: List<ConsumoObjeto> = emptyList(),
    val economia: Double = 0.0,
    val tarifa: Double = 0.79,
    val barras: List<BarraConsumo> = emptyList(),
)

fun Double.brl(): String = "R$ " + "%.2f".format(this).replace('.', ',')
fun Double.kwh(): String = "%.1f".format(this).replace('.', ',') + " kWh"

@Composable
fun ConsumoScreen(vm: ConsumoViewModel = viewModel()) {
    val periodo by vm.periodo.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var formulaAberta by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem, vertical = Espaco.l),
        verticalArrangement = Arrangement.spacedBy(Espaco.l),
    ) {
        TituloDeTela("Consumo")
        SeletorPeriodo(periodo, vm::escolher)

        CardToIndo(padding = Espaco.margem) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(ui.totalKwh.kwh(), style = MaterialTheme.typography.displaySmall)
                    Text(ui.totalCusto.brl(), style = MaterialTheme.typography.titleMedium, color = ToIndo.extras.energiaTexto)
                }
                SeloEstimado()
            }
            GraficoBarras(ui.barras)
        }

        CardToIndo(padding = Espaco.l) {
            if (ui.porObjeto.isEmpty()) {
                Text(
                    "Ainda sem consumo registrado neste período. O consumo é contado quando um objeto que estava ligado é desligado.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ui.porObjeto.forEach { c ->
                LinhaDeLista(c.nome, valor = "${c.kWh.kwh()} · ${c.custo.brl()}", corValor = ToIndo.extras.energiaTexto)
            }
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                Icon(Icons.AutoMirrored.Rounded.TrendingDown, null, tint = ToIndo.extras.economiaTexto)
                Text("Economia estimada: ${ui.economia.brl()} com a casa vazia", style = MaterialTheme.typography.bodyMedium, color = ToIndo.extras.economiaTexto)
            }
        }

        CardToIndo(padding = Espaco.l) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { formulaAberta = !formulaAberta },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Como calculamos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (formulaAberta) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(formulaAberta) {
                Text(
                    "Para cada objeto: kWh = potência (W) × horas ligado ÷ 1000. Custo = kWh × tarifa (${ui.tarifa.brl()}/kWh). " +
                        "É uma estimativa: os objetos não medem o consumo real.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Espaco.s),
                )
            }
        }
    }
}

@Composable
private fun SeletorPeriodo(atual: Periodo, onEscolher: (Periodo) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Periodo.entries.forEach { p ->
            val sel = p == atual
            Surface(
                modifier = Modifier.weight(1f).height(40.dp).selectable(sel, role = Role.Tab) { onEscolher(p) },
                shape = MaterialTheme.shapes.small,
                color = if (sel) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(p.rotulo, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GraficoBarras(barras: List<BarraConsumo>) {
    val valores = barras.map { it.kWh }
    val max = valores.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val atual = barras.indexOfFirst { it.atual }
    val denso = barras.size > 7
    val espaco = if (denso) 2.dp else 10.dp
    Column(Modifier.padding(top = Espaco.l).semantics(mergeDescendants = true) {
        contentDescription = "Consumo: " + barras.joinToString { "${it.descricao} ${it.kWh.kwh()}" }
    }) {
        Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(espaco), verticalAlignment = Alignment.Bottom) {
            barras.forEachIndexed { i, b ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (!denso) {
                        Text("%.1f".format(b.kWh).replace('.', ','), style = MaterialTheme.typography.labelSmall,
                            color = if (i == atual) ToIndo.extras.energiaTexto else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        Modifier.padding(top = 4.dp).fillMaxWidth().fillMaxHeight((b.kWh / max).toFloat() * 0.82f)
                            .clip(RoundedCornerShape(topStart = if (denso) 2.dp else 6.dp, topEnd = if (denso) 2.dp else 6.dp))
                            .background(if (i == atual) ToIndo.extras.energiaBarraDestaque else ToIndo.extras.energiaBarra)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(espaco)) {
            barras.forEachIndexed { i, b ->
                Text(b.rotulo, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                    maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (i == atual) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

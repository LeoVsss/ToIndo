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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Periodo(val rotulo: String) { DIA("Dia"), SEMANA("Semana"), MES("Mês") }

class ConsumoViewModel : ViewModel() {
    private val _periodo = MutableStateFlow(Periodo.SEMANA)
    val periodo = _periodo.asStateFlow()
    fun escolher(p: Periodo) { _periodo.value = p }

    // Exemplo da semana. Na fase Prototype: somar registro_consumo do Room por período.
    val dias = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Hoje")
    val valores = RepositorioToIndo.consumoSemana
    val porObjeto = RepositorioToIndo.consumoPorObjeto
    val totalKwh get() = valores.sum()
    val tarifa get() = RepositorioToIndo.configuracao.value.tarifaKwh
    val economia = RepositorioToIndo.ECONOMIA_ESTIMADA_SEMANA
}

fun Double.brl(): String = "R$ " + "%.2f".format(this).replace('.', ',')
fun Double.kwh(): String = "%.1f".format(this).replace('.', ',') + " kWh"

/** T6 — Consumo estimado (US04, RF08, RN04). */
@Composable
fun ConsumoScreen(vm: ConsumoViewModel = viewModel()) {
    val periodo by vm.periodo.collectAsStateWithLifecycle()
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
                    Text(vm.totalKwh.kwh(), style = MaterialTheme.typography.displaySmall)
                    Text((vm.totalKwh * vm.tarifa).brl(), style = MaterialTheme.typography.titleMedium, color = ToIndo.extras.energiaTexto)
                }
                SeloEstimado()
            }
            GraficoBarras(vm.dias, vm.valores)
        }

        CardToIndo(padding = Espaco.l) {
            vm.porObjeto.forEach { c ->
                LinhaDeLista(c.nome, valor = "${c.kWh.kwh()} · ${c.custo.brl()}", corValor = ToIndo.extras.energiaTexto)
            }
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                Icon(Icons.AutoMirrored.Rounded.TrendingDown, null, tint = ToIndo.extras.economiaTexto)
                Text("Economia estimada: ${vm.economia.brl()} com a casa vazia", style = MaterialTheme.typography.bodyMedium, color = ToIndo.extras.economiaTexto)
            }
        }

        // Princípio 3: a fórmula da estimativa sempre acessível.
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
                    "Para cada objeto: kWh = potência (W) × horas ligado ÷ 1000. Custo = kWh × tarifa (${vm.tarifa.brl()}/kWh). " +
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
private fun GraficoBarras(rotulos: List<String>, valores: List<Double>) {
    val max = valores.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val ultimo = valores.lastIndex
    Column(Modifier.padding(top = Espaco.l).semantics(mergeDescendants = true) {
        contentDescription = "Consumo diário: " + rotulos.zip(valores).joinToString { (d, v) -> "$d ${v.kwh()}" }
    }) {
        Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            valores.forEachIndexed { i, v ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("%.1f".format(v).replace('.', ','), style = MaterialTheme.typography.labelSmall,
                        color = if (i == ultimo) ToIndo.extras.energiaTexto else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.padding(top = 4.dp).fillMaxWidth().fillMaxHeight((v / max).toFloat() * 0.82f)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (i == ultimo) ToIndo.extras.energiaBarraDestaque else ToIndo.extras.energiaBarra)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rotulos.forEachIndexed { i, d ->
                Text(d, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (i == ultimo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

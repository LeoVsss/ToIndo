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
import br.com.toindo.data.local.RegistroConsumoEntity
import br.com.toindo.data.local.ResultadoSaida
import br.com.toindo.domain.CalculadoraConsumo
import br.com.toindo.model.ConsumoObjeto
import java.util.Calendar

enum class Periodo(val rotulo: String) { DIA("Dia"), SEMANA("Semana"), MES("Mês") }

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
            RegistroConsumoEntity(0, o.id, o.nome, inicio, agora, kWh, CalculadoraConsumo.custo(kWh, tarifa))
        }

    val ui: StateFlow<ConsumoUi> = combine(
        _periodo, RepositorioToIndo.registrosConsumo, RepositorioToIndo.saidas, RepositorioToIndo.configuracao,
        combine(RepositorioToIndo.objetos, relogio) { objetos, agora -> objetos to agora },
    ) { p, registrosFechados, saidas, config, estado ->
        val registros = registrosFechados + registrosEmAndamento(estado.first, estado.second, config.tarifaKwh)
        val desde = inicioDoPeriodo(p)
        val doPeriodo = registros.filter { it.fimMillis >= desde }
        val porObjeto = doPeriodo.groupBy { it.objetoNome }
            .map { (nome, l) -> ConsumoObjeto(nome, l.sumOf { it.kWh }, l.sumOf { it.custo }) }
            .sortedByDescending { it.kWh }

        val agora = System.currentTimeMillis()
        val kWhEvitado = saidas.filter { it.saida.saiuEmMillis >= desde }.sumOf { s ->
            val fim = s.saida.voltouEmMillis ?: agora
            s.itens.filter { it.naSaida == ResultadoSaida.DESLIGOU }
                .sumOf { CalculadoraConsumo.kWh(if (it.potenciaW > 0) it.potenciaW else RepositorioToIndo.potenciaW(it.objetoId), fim - s.saida.saiuEmMillis) }
        }

        val (rotulos, valores) = ultimosSeteDias(registros)
        ConsumoUi(
            totalKwh = doPeriodo.sumOf { it.kWh },
            totalCusto = doPeriodo.sumOf { it.custo },
            porObjeto = porObjeto,
            economia = CalculadoraConsumo.custo(kWhEvitado, config.tarifaKwh),
            tarifa = config.tarifaKwh,
            dias = rotulos,
            valores = valores,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConsumoUi())

    private fun inicioDoPeriodo(p: Periodo): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        when (p) {
            Periodo.DIA -> Unit
            Periodo.SEMANA -> c.add(Calendar.DAY_OF_YEAR, -6)
            Periodo.MES -> c.add(Calendar.DAY_OF_YEAR, -29)
        }
        return c.timeInMillis
    }

    private fun ultimosSeteDias(registros: List<RegistroConsumoEntity>): Pair<List<String>, List<Double>> {
        val nomes = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
        val rotulos = mutableListOf<String>()
        val valores = mutableListOf<Double>()
        for (diasAtras in 6 downTo 0) {
            val ini = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -diasAtras)
            }
            val fim = ini.timeInMillis + 24L * 60 * 60 * 1000
            rotulos += if (diasAtras == 0) "Hoje" else nomes[ini.get(Calendar.DAY_OF_WEEK) - 1]
            valores += registros.filter { it.fimMillis >= ini.timeInMillis && it.fimMillis < fim }.sumOf { it.kWh }
        }
        return rotulos to valores
    }
}

data class ConsumoUi(
    val totalKwh: Double = 0.0,
    val totalCusto: Double = 0.0,
    val porObjeto: List<ConsumoObjeto> = emptyList(),
    val economia: Double = 0.0,
    val tarifa: Double = 0.79,
    val dias: List<String> = emptyList(),
    val valores: List<Double> = emptyList(),
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
            GraficoBarras(ui.dias, ui.valores)
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

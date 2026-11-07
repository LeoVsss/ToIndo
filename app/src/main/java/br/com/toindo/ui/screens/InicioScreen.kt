package br.com.toindo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.model.EstadoPresenca
import br.com.toindo.model.ObjetoInteligente
import br.com.toindo.ui.components.CartaoAdicionar
import br.com.toindo.ui.components.CartaoObjeto
import br.com.toindo.ui.components.LogoToIndo
import br.com.toindo.ui.components.PilulaPresenca
import br.com.toindo.ui.theme.Espaco
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class InicioUi(
    val presenca: EstadoPresenca = EstadoPresenca.EM_CASA,
    val automacaoAtiva: Boolean = true,
    val comodos: List<String> = emptyList(),
    val filtro: String? = null,
    val objetos: List<ObjetoInteligente> = emptyList(),
    val ligados: Int = 0,
    val total: Int = 0,
)

class InicioViewModel : ViewModel() {
    private val filtro = MutableStateFlow<String?>(null)

    val ui: StateFlow<InicioUi> = combine(
        RepositorioToIndo.objetos, RepositorioToIndo.presenca, RepositorioToIndo.configuracao, filtro,
    ) { objetos, presenca, config, f ->
        InicioUi(
            presenca = presenca,
            automacaoAtiva = config.automacaoAtiva,
            comodos = objetos.map { it.comodo }.distinct().sorted(),
            filtro = f,
            objetos = if (f == null) objetos else objetos.filter { it.comodo == f },
            ligados = objetos.count { it.ligado },
            total = objetos.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InicioUi())

    fun filtrar(comodo: String?) = filtro.update { comodo }
}

/** T4 — Início (US01, US02, US05, RF03–RF05, RN05). */
@Composable
fun InicioScreen(
    onAbrirObjeto: (Long) -> Unit,
    onAdicionar: () -> Unit,
    onAbrirHistorico: () -> Unit,
    onAbrirSaidas: () -> Unit = {},
    vm: InicioViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val inicioConfirmacao by RepositorioToIndo.inicioConfirmacao.collectAsStateWithLifecycle()
    val config by RepositorioToIndo.configuracao.collectAsStateWithLifecycle()

    // Contagem regressiva da confirmação de saída, atualizada a cada segundo.
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.presenca) {
        while (ui.presenca == EstadoPresenca.CONFIRMANDO_SAIDA) {
            agora = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val segundosRestantes = inicioConfirmacao?.let {
        (config.tempoConfirmacaoSaidaSeg - ((agora - it) / 1000)).toInt().coerceAtLeast(0)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem, vertical = Espaco.l),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Boa tarde", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Casa", style = MaterialTheme.typography.headlineSmall)
            }
            LogoToIndo(32.dp)
        }

        PilulaPresenca(ui.presenca, ui.automacaoAtiva, segundosRestantes)
        PainelRadar()

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Seus objetos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${ui.ligados} de ${ui.total} ligados", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
            ChipComodo("Todos", ui.filtro == null) { vm.filtrar(null) }
            ui.comodos.forEach { c -> ChipComodo(c, ui.filtro == c) { vm.filtrar(c) } }
        }

        // Grade de 2 colunas; o último espaço é sempre "Adicionar objeto".
        val celulas: List<ObjetoInteligente?> = ui.objetos + listOf(null)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            celulas.chunked(2).forEach { linha ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    linha.forEach { obj ->
                        if (obj != null) CartaoObjeto(obj, onClick = { onAbrirObjeto(obj.id) }, modifier = Modifier.weight(1f))
                        else CartaoAdicionar(onAdicionar, Modifier.weight(1f))
                    }
                    if (linha.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        UltimaSaida(onAbrirSaidas)
    }
}

/** Distância até a casa, raio e qualidade do GPS — o "radar" visível na tela inicial. */
@Composable
private fun PainelRadar() {
    val distancia by RepositorioToIndo.distanciaM.collectAsStateWithLifecycle()
    val precisao by RepositorioToIndo.precisaoM.collectAsStateWithLifecycle()
    val casa by RepositorioToIndo.residencia.collectAsStateWithLifecycle()

    val texto = when {
        distancia == null && precisao == null -> "Procurando sinal de GPS..."
        precisao != null && precisao!! > RepositorioToIndo.PRECISAO_MAX_M ->
            "GPS impreciso (±${precisao} m) — vá para perto de uma janela"
        distancia != null -> "Você está a ${distancia!!.toInt()} m de casa · radar de ${casa.raioSaidaM} m"
        else -> "Aguardando leitura precisa do GPS..."
    }
    Text(
        texto + (precisao?.takeIf { it <= RepositorioToIndo.PRECISAO_MAX_M }?.let { " · GPS ±$it m" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Última saída real (ou convite para testar, se ainda não houver nenhuma). */
@Composable
private fun UltimaSaida(onClick: () -> Unit) {
    val saidas by RepositorioToIndo.saidas.collectAsStateWithLifecycle()
    val ultima = saidas.firstOrNull()
    val hora = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR")) }

    val (titulo, detalhe) = if (ultima == null) {
        "Nenhuma saída registrada" to "Afaste-se de casa com o celular para testar o radar"
    } else {
        val desligados = ultima.itens.count { it.naSaida == br.com.toindo.data.local.ResultadoSaida.DESLIGOU }
        "$desligados objeto(s) desligado(s) em ${hora.format(java.util.Date(ultima.saida.saiuEmMillis))}" to
            "Você saiu de casa ${saidas.size} ${if (saidas.size == 1) "vez" else "vezes"} · ver histórico de saídas"
    }

    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espaco.m)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChipComodo(texto: String, selecionado: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selecionado,
        onClick = onClick,
        label = { Text(texto) },
        shape = MaterialTheme.shapes.extraSmall,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.surface,
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

package br.com.toindo.ui.screens

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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.Controle
import br.com.toindo.model.ObjetoInteligente
import br.com.toindo.ui.components.CardToIndo
import br.com.toindo.ui.components.Faixa
import br.com.toindo.ui.components.RotuloDeSecao
import br.com.toindo.ui.components.Stepper
import br.com.toindo.ui.components.TopoComVoltar
import br.com.toindo.ui.theme.Espaco
import br.com.toindo.ui.theme.ToIndo

class DetalheObjetoViewModel : ViewModel() {
    val objetos = RepositorioToIndo.objetos
    fun alternar(id: Long) = RepositorioToIndo.alternar(id)
    fun ajustar(o: ObjetoInteligente, c: Controle, delta: Int) =
        RepositorioToIndo.ajustar(o.id, c, (o.parametros[c] ?: c.min) + delta * c.passo)
    fun acoes(o: ObjetoInteligente, aoSair: AcaoPresenca = o.acaoAoSair, aoChegar: AcaoPresenca = o.acaoAoChegar) =
        RepositorioToIndo.definirAcoes(o.id, aoSair, aoChegar)
}

/** T5 — Detalhe do objeto (US03, US05, RF06, RN03). Controles variam conforme o tipo. */
@Composable
fun DetalheObjetoScreen(id: Long, onVoltar: () -> Unit, onEditar: () -> Unit, vm: DetalheObjetoViewModel = viewModel()) {
    val objetos by vm.objetos.collectAsStateWithLifecycle()
    val o = objetos.firstOrNull { it.id == id } ?: run { Text("Objeto não encontrado", Modifier.padding(Espaco.margem)); return }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopoComVoltar(titulo = o.nome, subtitulo = "${o.comodo} · ${o.potenciaW} W", onVoltar = onVoltar) {
            IconButton(onClick = onEditar) { Icon(Icons.Rounded.Edit, contentDescription = "Editar objeto") }
        }

        CardToIndo(cor = if (o.ligado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, padding = Espaco.margem) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (o.ligado) o.ligadoDesde?.let { "Ligado desde $it" } ?: "Ligado" else "Desligado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (o.ligado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val temp = o.parametros[Controle.TEMPERATURA]
                    Text(if (temp != null) "$temp°" else if (o.ligado) "Ligado" else "Desligado", style = MaterialTheme.typography.displayMedium)
                }
                FilledIconButton(
                    onClick = { vm.alternar(o.id) },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (o.ligado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                        contentColor = if (o.ligado) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = if (o.ligado) "Desligar ${o.nome}" else "Ligar ${o.nome}", modifier = Modifier.size(28.dp)) }
            }
            if (o.tipo.controles.isNotEmpty()) {
                Spacer(Modifier.height(Espaco.l))
                o.tipo.controles.forEach { c -> CaixaControle(o, c, vm, Modifier.fillMaxWidth().padding(bottom = Espaco.s)) }
            }
        }

        RotuloDeSecao("Automação deste objeto")
        CardToIndo {
            Text("Ao sair de casa", style = MaterialTheme.typography.titleSmall)
            LinhaAcoes(o.tipo.acoesAoSair, o.acaoAoSair) { vm.acoes(o, aoSair = it) }
            Spacer(Modifier.height(Espaco.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ao chegar", style = MaterialTheme.typography.titleSmall)
                if (!o.tipo.podeLigarSozinho) {
                    Spacer(Modifier.size(6.dp))
                    Icon(Icons.Rounded.Lock, contentDescription = "Bloqueado por segurança", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinhaAcoes(o.tipo.acoesAoChegar, o.acaoAoChegar) { vm.acoes(o, aoChegar = it) }
        }

        if (!o.tipo.podeLigarSozinho) {
            Faixa(
                "Objetos que aquecem nunca ligam sozinhos. Se você sair com o ${o.nome.lowercase()} ligado, ele desliga quando a saída for confirmada.",
                Icons.Rounded.Warning, ToIndo.extras.energiaFundo, ToIndo.extras.energiaTexto,
            )
        }
        if (o.automacaoPausada) {
            Faixa(
                "Você mudou manualmente. A automação deste objeto volta na próxima saída ou chegada.",
                Icons.Rounded.Info, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(Espaco.xl))
    }
}

@Composable
private fun CaixaControle(o: ObjetoInteligente, c: Controle, vm: DetalheObjetoViewModel, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.rotulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            val v = o.parametros[c] ?: c.min
            Box { Stepper("$v ${c.unidade}".trim(), c.rotulo.lowercase(), { vm.ajustar(o, c, -1) }, { vm.ajustar(o, c, +1) }) }
        }
    }
}

@Composable
private fun LinhaAcoes(opcoes: List<AcaoPresenca>, atual: AcaoPresenca, onEscolher: (AcaoPresenca) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
        opcoes.forEach { a ->
            FilterChip(selected = a == atual, onClick = { onEscolher(a) }, label = { Text(a.rotulo) }, shape = MaterialTheme.shapes.extraSmall)
        }
    }
}

package br.com.toindo.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.EstadoDispositivo
import br.com.toindo.model.ObjetoInteligente
import br.com.toindo.model.TipoObjeto
import br.com.toindo.ui.components.BotaoPrincipal
import br.com.toindo.ui.components.CardToIndo
import br.com.toindo.ui.components.Faixa
import br.com.toindo.ui.components.TopoComVoltar
import br.com.toindo.ui.components.icone
import br.com.toindo.ui.theme.Espaco
import br.com.toindo.ui.theme.ToIndo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatusTeste { NAO_TESTADO, TESTANDO, OK, FALHOU }

data class NovoObjetoUi(
    val idEdicao: Long? = null,
    val tipo: TipoObjeto = TipoObjeto.AR_CONDICIONADO,
    val nome: String = TipoObjeto.AR_CONDICIONADO.rotulo,
    val comodo: String = "",
    val potenciaW: String = "",
    val endereco: String = "",
    val aoSair: AcaoPresenca = TipoObjeto.AR_CONDICIONADO.aoSairPadrao,
    val aoChegar: AcaoPresenca = TipoObjeto.AR_CONDICIONADO.aoChegarPadrao,
    val teste: StatusTeste = StatusTeste.NAO_TESTADO,
) {
    val valido get() = nome.isNotBlank() && (potenciaW.toIntOrNull() ?: 0) > 0 &&
        endereco.isNotBlank() && endereco.none { it.isWhitespace() }
}

class AdicionarObjetoViewModel : ViewModel() {
    private val _ui = MutableStateFlow(NovoObjetoUi())
    val ui = _ui.asStateFlow()

    fun tipo(t: TipoObjeto) = _ui.update {
        it.copy(tipo = t, nome = if (it.nome == it.tipo.rotulo || it.nome.isBlank()) t.rotulo else it.nome,
            aoSair = t.aoSairPadrao, aoChegar = t.aoChegarPadrao, teste = StatusTeste.NAO_TESTADO)
    }
    fun nome(v: String) = _ui.update { it.copy(nome = v) }
    fun comodo(v: String) = _ui.update { it.copy(comodo = v) }
    fun potencia(v: String) = _ui.update { it.copy(potenciaW = v.filter(Char::isDigit).take(5)) }
    fun endereco(v: String) = _ui.update { it.copy(endereco = v.trim(), teste = StatusTeste.NAO_TESTADO) }
    fun aoSair(a: AcaoPresenca) = _ui.update { it.copy(aoSair = a) }
    fun aoChegar(a: AcaoPresenca) = _ui.update { it.copy(aoChegar = a) }

    fun carregar(id: Long?) {
        if (id == null || _ui.value.idEdicao == id) return
        val o = RepositorioToIndo.objeto(id) ?: return
        _ui.value = NovoObjetoUi(
            idEdicao = o.id, tipo = o.tipo, nome = o.nome, comodo = o.comodo,
            potenciaW = o.potenciaW.toString(), endereco = o.enderecoRemoto,
            aoSair = o.acaoAoSair, aoChegar = o.acaoAoChegar,
        )
    }

    fun testar() {
        val endereco = _ui.value.endereco
        if (endereco.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(teste = StatusTeste.TESTANDO) }
            val ok = RepositorioToIndo.testarConexao(endereco)
            _ui.update { if (it.endereco == endereco) it.copy(teste = if (ok) StatusTeste.OK else StatusTeste.FALHOU) else it }
        }
    }

    fun salvar() {
        val u = _ui.value
        if (u.idEdicao != null) {
            RepositorioToIndo.editar(
                u.idEdicao, u.nome.trim(), u.tipo, u.comodo.trim().ifBlank { "Casa" },
                u.potenciaW.toInt(), u.endereco, u.aoSair, u.aoChegar,
            )
            return
        }
        RepositorioToIndo.adicionar(
            ObjetoInteligente(0, u.nome.trim(), u.tipo, u.comodo.trim().ifBlank { "Casa" }, u.potenciaW.toInt(), u.endereco,
                EstadoDispositivo.DESLIGADO, acaoAoSair = u.aoSair, acaoAoChegar = u.aoChegar)
        )
    }
}

@Composable
fun AdicionarObjetoScreen(
    idEdicao: Long?,
    onVoltar: () -> Unit,
    onConcluir: () -> Unit,
    vm: AdicionarObjetoViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val editando = RepositorioToIndo.cadastroConcluido.value
    LaunchedEffect(idEdicao) { vm.carregar(idEdicao) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopoComVoltar(titulo = null, subtitulo = if (editando) null else "Passo 2 de 2", onVoltar = onVoltar)
        Text(if (ui.idEdicao != null) "Editar objeto" else "Adicionar objeto", style = MaterialTheme.typography.headlineSmall)

        Text("Que tipo de objeto é?", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Espaco.s)) {
            TipoObjeto.entries.chunked(4).forEach { linha ->
                Row(horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
                    linha.forEach { t -> BotaoTipo(t, selecionado = t == ui.tipo, onClick = { vm.tipo(t) }, modifier = Modifier.weight(1f)) }
                }
            }
        }

        CardToIndo {
            Column(verticalArrangement = Arrangement.spacedBy(Espaco.m)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Campo("Nome", ui.nome, vm::nome, Modifier.weight(1f))
                    Campo("Cômodo", ui.comodo, vm::comodo, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Campo("Potência (W)", ui.potenciaW, vm::potencia, Modifier.weight(1f), numerico = true,
                        ajuda = "Está na etiqueta do aparelho")
                    Campo("Endereço (IP)", ui.endereco, vm::endereco, Modifier.weight(1f), ajuda = "Ex.: 192.168.0.50")
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = Espaco.m).height(1.dp).background(MaterialTheme.colorScheme.outline))
            Acoes("Ao sair de casa", ui.tipo.acoesAoSair, ui.aoSair, vm::aoSair)
            Spacer(Modifier.height(Espaco.s))
            Acoes("Ao chegar", ui.tipo.acoesAoChegar, ui.aoChegar, vm::aoChegar)
            if (!ui.tipo.podeLigarSozinho || !ui.tipo.podeDesligarSozinho) {
                Spacer(Modifier.height(Espaco.m))
                Faixa(
                    texto = if (!ui.tipo.podeDesligarSozinho) "Por segurança, a automação nunca desliga a geladeira."
                    else "Por segurança, objetos que aquecem (forno, torradeira) nunca ligam sozinhos.",
                    icone = Icons.Rounded.Warning, fundo = ToIndo.extras.energiaFundo, tinta = ToIndo.extras.energiaTexto,
                )
            }
        }

        TesteConexao(ui.teste, habilitado = ui.endereco.isNotBlank(), onTestar = vm::testar)

        BotaoPrincipal(
            if (ui.idEdicao != null) "Salvar alterações" else if (editando) "Adicionar objeto" else "Adicionar",
            onClick = { vm.salvar(); onConcluir() },
            habilitado = ui.valido,
            modifier = Modifier.padding(bottom = Espaco.xl),
        )
    }
}

@Composable
private fun BotaoTipo(t: TipoObjeto, selecionado: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(72.dp).selectable(selected = selecionado, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selecionado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selecionado) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(t.icone(), contentDescription = null, modifier = Modifier.size(22.dp),
                tint = if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(t.rotuloCurto, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selecionado) FontWeight.SemiBold else FontWeight.Medium),
                color = if (selecionado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Campo(rotulo: String, valor: String, onChange: (String) -> Unit, modifier: Modifier, numerico: Boolean = false, ajuda: String? = null) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(rotulo) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        supportingText = if (ajuda != null) { { Text(ajuda) } } else null,
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier,
    )
}

@Composable
private fun Acoes(rotulo: String, opcoes: List<AcaoPresenca>, atual: AcaoPresenca, onEscolher: (AcaoPresenca) -> Unit) {
    Text(rotulo, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
        opcoes.forEach { a ->
            FilterChip(selected = a == atual, onClick = { onEscolher(a) }, label = { Text(a.rotulo) }, shape = MaterialTheme.shapes.extraSmall)
        }
    }
}

@Composable
private fun TesteConexao(status: StatusTeste, habilitado: Boolean, onTestar: () -> Unit) {
    val (fundo, tinta, texto) = when (status) {
        StatusTeste.OK -> Triple(ToIndo.extras.economiaFundo, ToIndo.extras.economiaTexto, "Objeto respondeu ao teste")
        StatusTeste.FALHOU -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), MaterialTheme.colorScheme.error, "Sem resposta. Confira o IP, a rede e o Arduino.")
        StatusTeste.TESTANDO -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant, "Testando conexão…")
        StatusTeste.NAO_TESTADO -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant, "Teste se o objeto responde")
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(fundo).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (status) {
            StatusTeste.OK -> Box(Modifier.size(28.dp).clip(CircleShape).background(ToIndo.extras.economia), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
            }
            StatusTeste.TESTANDO -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else -> {}
        }
        Text(texto, style = MaterialTheme.typography.titleSmall, color = tinta, modifier = Modifier.weight(1f))
        TextButton(onClick = onTestar, enabled = habilitado && status != StatusTeste.TESTANDO) { Text("Testar") }
    }
}

package br.com.toindo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.ui.components.CardToIndo
import br.com.toindo.ui.components.LinhaDeLista
import br.com.toindo.ui.components.RotuloDeSecao
import br.com.toindo.ui.components.TituloDeTela
import br.com.toindo.ui.theme.Espaco
import br.com.toindo.ui.theme.ToIndo

class AjustesViewModel : ViewModel() {
    val configuracao = RepositorioToIndo.configuracao
    val residencia = RepositorioToIndo.residencia
    val objetos = RepositorioToIndo.objetos

    fun automacao(ativa: Boolean) = RepositorioToIndo.atualizarConfiguracao { it.copy(automacaoAtiva = ativa) }
    fun notificacoes(ativas: Boolean) = RepositorioToIndo.atualizarConfiguracao { it.copy(notificacoesAtivas = ativas) }
    fun tarifa(texto: String) {
        texto.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { v ->
            RepositorioToIndo.atualizarConfiguracao { it.copy(tarifaKwh = v) }
        }
    }
}

@Composable
fun AjustesScreen(
    onEditarResidencia: () -> Unit,
    onObjetos: () -> Unit,
    onAbrirSaidas: () -> Unit = {},
    vm: AjustesViewModel = viewModel(),
) {
    val config by vm.configuracao.collectAsStateWithLifecycle()
    val casa by vm.residencia.collectAsStateWithLifecycle()
    val objetos by vm.objetos.collectAsStateWithLifecycle()
    val saidas by RepositorioToIndo.saidas.collectAsStateWithLifecycle()
    var tarifaTexto by remember { mutableStateOf("%.2f".format(config.tarifaKwh).replace('.', ',')) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem, vertical = Espaco.l),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TituloDeTela("Ajustes")

        RotuloDeSecao("Automação")
        CardToIndo(padding = Espaco.l) {
            LinhaDeLista("Automação ativa", fim = {
                Switch(checked = config.automacaoAtiva, onCheckedChange = vm::automacao)
            })
            LinhaDeLista("Raio de saída", valor = formatarDistancia(casa.raioSaidaM), onClick = onEditarResidencia)
            LinhaDeLista("Raio de aproximação", valor = formatarDistancia(casa.raioAproximacaoM), onClick = onEditarResidencia)
            LinhaDeLista("Confirmar saída após", valor = formatarSegundos(config.tempoConfirmacaoSaidaSeg), onClick = onEditarResidencia)
            LinhaDeLista("Saídas de casa", valor = "${saidas.size}", onClick = onAbrirSaidas, divisor = false)
        }

        RotuloDeSecao("Energia")
        CardToIndo(padding = Espaco.l) {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tarifa de energia", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("R$ ", style = MaterialTheme.typography.titleSmall, color = ToIndo.extras.energiaTexto)
                OutlinedTextField(
                    value = tarifaTexto,
                    onValueChange = { tarifaTexto = it; vm.tarifa(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall.merge(TextStyle(color = ToIndo.extras.energiaTexto, textAlign = TextAlign.Center)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.width(88.dp),
                )
                Text(" /kWh", style = MaterialTheme.typography.titleSmall, color = ToIndo.extras.energiaTexto)
            }
        }

        RotuloDeSecao("Casa")
        CardToIndo(padding = Espaco.l) {
            LinhaDeLista("Editar residência", onClick = onEditarResidencia)
            LinhaDeLista("Objetos inteligentes", valor = "${objetos.size}", onClick = onObjetos)
            LinhaDeLista("Moradores", valor = "2", onClick = {  }, divisor = false)
        }

        RotuloDeSecao("Notificações e privacidade")
        CardToIndo(padding = Espaco.l) {
            LinhaDeLista("Avisar ligar e desligar", fim = {
                Switch(checked = config.notificacoesAtivas, onCheckedChange = vm::notificacoes)
            })
            LinhaDeLista("Localização fica só no aparelho", detalhe = "Nada é enviado a servidores (LGPD)")
            TextButton(onClick = {  }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Apagar meus dados", color = MaterialTheme.colorScheme.error)
            }
        }
        Column(Modifier.padding(bottom = Espaco.l)) {}
    }
}

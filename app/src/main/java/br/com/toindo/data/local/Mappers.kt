package br.com.toindo.data.local

import br.com.toindo.model.Configuracao
import br.com.toindo.model.ConsumoObjeto
import br.com.toindo.model.Controle
import br.com.toindo.model.EstadoDispositivo
import br.com.toindo.model.EventoObjeto
import br.com.toindo.model.ObjetoInteligente
import br.com.toindo.model.Residencia
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val horaFmt = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

fun ResidenciaEntity.paraDominio() = Residencia(
    nome = nome,
    latitude = latitude,
    longitude = longitude,
    raioSaidaM = raioSaidaM,
    raioAproximacaoM = raioAproximacaoM,
)

fun Residencia.paraEntity() = ResidenciaEntity(
    id = 1,
    nome = nome,
    latitude = latitude,
    longitude = longitude,
    raioSaidaM = raioSaidaM,
    raioAproximacaoM = raioAproximacaoM,
)

fun ConfiguracaoEntity.paraDominio() = Configuracao(
    tarifaKwh = tarifaKwh,
    tempoConfirmacaoSaidaSeg = tempoConfirmacaoSaidaSeg,
    notificacoesAtivas = notificacoesAtivas,
    automacaoAtiva = automacaoAtiva,
)

fun Configuracao.paraEntity(cadastroConcluido: Boolean) = ConfiguracaoEntity(
    id = 1,
    tarifaKwh = tarifaKwh,
    tempoConfirmacaoSaidaSeg = tempoConfirmacaoSaidaSeg,
    notificacoesAtivas = notificacoesAtivas,
    automacaoAtiva = automacaoAtiva,
    cadastroConcluido = cadastroConcluido,
)

fun ObjetoEntity.paraDominio() = ObjetoInteligente(
    id = id,
    nome = nome,
    tipo = tipo,
    comodo = comodo,
    potenciaW = potenciaW,
    enderecoRemoto = enderecoRemoto,
    estado = estado,
    parametros = parametros.mapNotNull { (chave, valor) ->
        runCatching { Controle.valueOf(chave) }.getOrNull()?.let { it to valor }
    }.toMap(),
    acaoAoSair = acaoAoSair,
    acaoAoChegar = acaoAoChegar,
    automacaoPausada = automacaoPausada,
    ligadoDesde = ligadoDesdeMillis?.let { horaFmt.format(Date(it)) },
)

fun ObjetoInteligente.paraEntity(ligadoDesdeMillis: Long?) = ObjetoEntity(
    id = id,
    nome = nome,
    tipo = tipo,
    comodo = comodo,
    potenciaW = potenciaW,
    enderecoRemoto = enderecoRemoto,
    estado = estado,
    parametros = parametros.mapKeys { it.key.name },
    acaoAoSair = acaoAoSair,
    acaoAoChegar = acaoAoChegar,
    automacaoPausada = automacaoPausada,
    ligadoDesdeMillis = if (estado == EstadoDispositivo.LIGADO) ligadoDesdeMillis else null,
)

fun EventoEntity.paraDominio(): EventoObjeto {
    val data = Date(instanteMillis)
    return EventoObjeto(
        id = id,
        titulo = titulo,
        tipo = tipo,
        origem = origem,
        motivo = motivo,
        hora = horaFmt.format(data),
        dia = rotuloDoDia(instanteMillis),
        tipoObjeto = tipoObjeto,
        sincronizado = sincronizado,
    )
}

fun ConsumoAgrupado.paraDominio() = ConsumoObjeto(nome = nome, kWh = kWh, custo = custo)

private fun rotuloDoDia(instanteMillis: Long): String {
    val inicioHoje = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val umDia = 24L * 60 * 60 * 1000
    return when {
        instanteMillis >= inicioHoje -> "Hoje"
        instanteMillis >= inicioHoje - umDia -> "Ontem"
        else -> SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(Date(instanteMillis))
    }
}

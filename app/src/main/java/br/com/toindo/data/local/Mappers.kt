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

/**
 * Conversões entity <-> domínio. Mantêm o modelo que as telas já usam intacto
 * (ObjetoInteligente, EventoObjeto, Residencia, Configuracao), isolando o Room.
 */

private val horaFmt = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

// ---------- Residência ----------

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

// ---------- Configuração ----------

fun ConfiguracaoEntity.paraDominio() = Configuracao(
    tarifaKwh = tarifaKwh,
    tempoConfirmacaoSaidaMin = tempoConfirmacaoSaidaMin,
    notificacoesAtivas = notificacoesAtivas,
    automacaoAtiva = automacaoAtiva,
)

fun Configuracao.paraEntity(cadastroConcluido: Boolean) = ConfiguracaoEntity(
    id = 1,
    tarifaKwh = tarifaKwh,
    tempoConfirmacaoSaidaMin = tempoConfirmacaoSaidaMin,
    notificacoesAtivas = notificacoesAtivas,
    automacaoAtiva = automacaoAtiva,
    cadastroConcluido = cadastroConcluido,
)

// ---------- Objeto ----------

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

/**
 * Converte domínio -> entity. Como o domínio guarda a hora formatada (String) e não o epoch,
 * [ligadoDesdeMillis] é informado pela camada que conhece o instante real (repositório/motor).
 */
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

// ---------- Evento ----------

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

// ---------- Consumo ----------

fun ConsumoAgrupado.paraDominio() = ConsumoObjeto(nome = nome, kWh = kWh, custo = custo)

/** "Hoje" / "Ontem" / dd/MM para agrupar o histórico (T7). */
private fun rotuloDoDia(instanteMillis: Long): String {
    val umDia = 24L * 60 * 60 * 1000
    val agora = System.currentTimeMillis()
    val inicioHoje = agora - (agora % umDia)
    return when {
        instanteMillis >= inicioHoje -> "Hoje"
        instanteMillis >= inicioHoje - umDia -> "Ontem"
        else -> SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(Date(instanteMillis))
    }
}

package br.com.toindo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.EstadoDispositivo
import br.com.toindo.model.OrigemEvento
import br.com.toindo.model.TipoEvento
import br.com.toindo.model.TipoObjeto

/**
 * Entidades do Room — materializam o "Modelo de dados preliminar" da seção 5.2 do PLAN.
 * Ficam separadas do domínio (model/Modelos.kt): os mappers em [Mappers.kt] convertem
 * entity <-> modelo, então as telas continuam usando ObjetoInteligente/EventoObjeto/etc.
 */

/** Residência única cadastrada (RF01). Usamos id fixo = 1 porque o app gerencia uma casa. */
@Entity(tableName = "residencia")
data class ResidenciaEntity(
    @PrimaryKey val id: Long = 1,
    val nome: String,
    val latitude: Double,
    val longitude: Double,
    val raioSaidaM: Int,
    val raioAproximacaoM: Int,
)

/** Objeto inteligente / dispositivo (RF02). O estado é persistido para sobreviver ao reinício. */
@Entity(tableName = "objetos")
data class ObjetoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val tipo: TipoObjeto,
    val comodo: String,
    val potenciaW: Int,
    val enderecoRemoto: String,
    val estado: EstadoDispositivo,
    /** Parâmetros (temperatura, timer, volume...) serializados como "CONTROLE=valor;..." */
    val parametros: Map<String, Int>,
    val acaoAoSair: AcaoPresenca,
    val acaoAoChegar: AcaoPresenca,
    val automacaoPausada: Boolean,
    /** Momento em que ligou, em epoch millis — base do cálculo de consumo (RN04). Null = desligado. */
    val ligadoDesdeMillis: Long?,
)

/** Evento de liga/desliga/ajuste (RF07). `sincronizado=false` = pendente de envio (RNF04). */
@Entity(tableName = "eventos")
data class EventoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objetoId: Long?,
    val titulo: String,
    val tipo: TipoEvento,
    val origem: OrigemEvento,
    val motivo: String,
    val tipoObjeto: TipoObjeto?,
    /** Instante do evento em epoch millis (ordenação e agrupamento por dia). */
    val instanteMillis: Long,
    val sincronizado: Boolean,
)

/** Configuração global do app (seção 5.2). Linha única id = 1. */
@Entity(tableName = "configuracao")
data class ConfiguracaoEntity(
    @PrimaryKey val id: Long = 1,
    val tarifaKwh: Double,
    val tempoConfirmacaoSaidaMin: Int,
    val notificacoesAtivas: Boolean,
    val automacaoAtiva: Boolean,
    /** Marca se o fluxo de cadastro (T1→T3) já foi concluído. */
    val cadastroConcluido: Boolean,
)

/**
 * Registro de consumo fechado (RN04): um intervalo ligado->desligado com kWh e custo.
 * Alimenta os relatórios da tela de Consumo (US04) por dia/semana/mês.
 */
@Entity(tableName = "registros_consumo")
data class RegistroConsumoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objetoId: Long,
    val objetoNome: String,
    val inicioMillis: Long,
    val fimMillis: Long,
    val kWh: Double,
    val custo: Double,
)

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
    /** Tempo fora do raio antes de confirmar a saída, em SEGUNDOS (0 = imediato). */
    val tempoConfirmacaoSaidaSeg: Int,
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

/**
 * Estado da presença (linha única id = 1). Persistido para que o app, ao ser reaberto ou o
 * serviço de GPS ser reiniciado pelo Android, continue sabendo se você está fora de casa.
 */
@Entity(tableName = "presenca")
data class PresencaEntity(
    @PrimaryKey val id: Long = 1,
    val estado: String,
    /** Quando saiu do raio (início da confirmação). Null = não está confirmando. */
    val inicioConfirmacaoMillis: Long?,
    /** Maior distância atingida desde que saiu (m) — usada para saber se está voltando. */
    val maxDistanciaForaM: Double,
    /** Saída em andamento (0 = nenhuma). */
    val saidaAbertaId: Long,
)

/** Uma saída de casa. O id é o instante da saída (epoch millis). */
@Entity(tableName = "saidas")
data class SaidaEntity(
    @PrimaryKey val id: Long,
    val saiuEmMillis: Long,
    val voltouEmMillis: Long?,
    val distanciaMaxM: Int,
)

/** O que aconteceu com UM dispositivo numa saída (e na volta). */
@Entity(tableName = "saida_itens")
data class SaidaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saidaId: Long,
    val objetoId: Long,
    val nome: String,
    val comodo: String,
    /** Ver [ResultadoSaida]. */
    val naSaida: String,
    val motivo: String,
    /** Ver [ResultadoVolta]. Null = ainda não voltou. */
    val naVolta: String?,
)

/** Resultado de cada dispositivo no momento da saída. */
object ResultadoSaida {
    const val DESLIGOU = "DESLIGOU"
    const val CONTINUOU_LIGADO = "CONTINUOU_LIGADO"
    const val JA_DESLIGADO = "JA_DESLIGADO"
    const val OFFLINE = "OFFLINE"
}

/** Resultado de cada dispositivo na volta para dentro do radar. */
object ResultadoVolta {
    const val LIGOU = "LIGOU"
    const val JA_LIGADO = "JA_LIGADO"
    const val CONTINUOU_DESLIGADO = "CONTINUOU_DESLIGADO"
    const val NAO_RELIGA_SEGURANCA = "NAO_RELIGA_SEGURANCA"
    const val OFFLINE = "OFFLINE"
}

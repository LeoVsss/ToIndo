package br.com.toindo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.EstadoDispositivo
import br.com.toindo.model.OrigemEvento
import br.com.toindo.model.TipoEvento
import br.com.toindo.model.TipoObjeto

@Entity(tableName = "residencia")
data class ResidenciaEntity(
    @PrimaryKey val id: Long = 1,
    val nome: String,
    val latitude: Double,
    val longitude: Double,
    val raioSaidaM: Int,
    val raioAproximacaoM: Int,
)

@Entity(tableName = "objetos")
data class ObjetoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val tipo: TipoObjeto,
    val comodo: String,
    val potenciaW: Int,
    val enderecoRemoto: String,
    val estado: EstadoDispositivo,
    val parametros: Map<String, Int>,
    val acaoAoSair: AcaoPresenca,
    val acaoAoChegar: AcaoPresenca,
    val automacaoPausada: Boolean,
    val ligadoDesdeMillis: Long?,
)

@Entity(tableName = "eventos")
data class EventoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objetoId: Long?,
    val titulo: String,
    val tipo: TipoEvento,
    val origem: OrigemEvento,
    val motivo: String,
    val tipoObjeto: TipoObjeto?,
    val instanteMillis: Long,
    val sincronizado: Boolean,
)

@Entity(tableName = "configuracao")
data class ConfiguracaoEntity(
    @PrimaryKey val id: Long = 1,
    val tarifaKwh: Double,
    val tempoConfirmacaoSaidaSeg: Int,
    val notificacoesAtivas: Boolean,
    val automacaoAtiva: Boolean,
    val cadastroConcluido: Boolean,
)

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

@Entity(tableName = "presenca")
data class PresencaEntity(
    @PrimaryKey val id: Long = 1,
    val estado: String,
    val inicioConfirmacaoMillis: Long?,
    val maxDistanciaForaM: Double,
    val saidaAbertaId: Long,
)

@Entity(tableName = "saidas")
data class SaidaEntity(
    @PrimaryKey val id: Long,
    val saiuEmMillis: Long,
    val voltouEmMillis: Long?,
    val distanciaMaxM: Int,
)

@Entity(tableName = "saida_itens")
data class SaidaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saidaId: Long,
    val objetoId: Long,
    val nome: String,
    val comodo: String,
    val naSaida: String,
    val motivo: String,
    val naVolta: String?,
)

object ResultadoSaida {
    const val DESLIGOU = "DESLIGOU"
    const val CONTINUOU_LIGADO = "CONTINUOU_LIGADO"
    const val JA_DESLIGADO = "JA_DESLIGADO"
    const val OFFLINE = "OFFLINE"
}

object ResultadoVolta {
    const val LIGOU = "LIGOU"
    const val JA_LIGADO = "JA_LIGADO"
    const val CONTINUOU_DESLIGADO = "CONTINUOU_DESLIGADO"
    const val NAO_RELIGA_SEGURANCA = "NAO_RELIGA_SEGURANCA"
    const val OFFLINE = "OFFLINE"
}

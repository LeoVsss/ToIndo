package br.com.toindo.prototipo

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Banco do PROTÓTIPO (arquivo separado: prototipo.db), para não mexer no schema do app principal.
 * Guarda: configuração da casa + presença, estado dos dispositivos e o HISTÓRICO DE SAÍDAS.
 */

/** Linha única (id = 1): casa, raios, posição atual e presença. */
@Entity(tableName = "proto_config")
data class ProtoConfigEntity(
    @PrimaryKey val id: Long = 1,
    val nome: String,
    val latitude: Double,
    val longitude: Double,
    val raioSaidaM: Int,
    val raioAproximacaoM: Int,
    val minhaLat: Double,
    val minhaLon: Double,
    val presenca: String,
    /** Maior distância já atingida desde que saiu (m). */
    val maxForaM: Double,
    /** Id da saída em andamento (0 = nenhuma). */
    val saidaAbertaId: Long,
)

/** Estado de cada dispositivo (a lista de dispositivos em si vem do código de exemplo). */
@Entity(tableName = "proto_objeto")
data class ProtoObjetoEntity(
    @PrimaryKey val id: Long,
    val estado: String,
    val automacaoPausada: Boolean,
    val ligadoDesde: String?,
)

/** Uma saída de casa. O id é o instante da saída (epoch millis). */
@Entity(tableName = "proto_saida")
data class SaidaEntity(
    @PrimaryKey val id: Long,
    val saiuEmMillis: Long,
    val voltouEmMillis: Long?,
    val distanciaMaxM: Int,
)

/** O que aconteceu com UM dispositivo numa saída. */
@Entity(tableName = "proto_saida_item")
data class SaidaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saidaId: Long,
    val objetoId: Long,
    val nome: String,
    val comodo: String,
    /** DESLIGOU | CONTINUOU_LIGADO | JA_DESLIGADO | OFFLINE */
    val naSaida: String,
    val motivoSaida: String,
    /** LIGOU | JA_LIGADO | CONTINUOU_DESLIGADO | OFFLINE (null = ainda não voltou) */
    val naVolta: String?,
)

data class SaidaComItens(
    @Embedded val saida: SaidaEntity,
    @Relation(parentColumn = "id", entityColumn = "saidaId") val itens: List<SaidaItemEntity>,
)

@Dao
abstract class PrototipoDao {
    @Query("SELECT * FROM proto_config WHERE id = 1")
    abstract suspend fun config(): ProtoConfigEntity?

    @Upsert
    abstract suspend fun salvarConfig(c: ProtoConfigEntity)

    @Query("SELECT * FROM proto_objeto")
    abstract suspend fun objetos(): List<ProtoObjetoEntity>

    @Upsert
    abstract suspend fun salvarObjetos(l: List<ProtoObjetoEntity>)

    @Insert
    abstract suspend fun inserirSaida(s: SaidaEntity)

    @Insert
    abstract suspend fun inserirItens(l: List<SaidaItemEntity>)

    @Transaction
    open suspend fun registrarSaida(s: SaidaEntity, itens: List<SaidaItemEntity>) {
        inserirSaida(s)
        inserirItens(itens)
    }

    @Query("UPDATE proto_saida SET voltouEmMillis = :voltou, distanciaMaxM = :dist WHERE id = :id")
    abstract suspend fun fecharSaida(id: Long, voltou: Long, dist: Int)

    @Query("UPDATE proto_saida_item SET naVolta = :resultado WHERE saidaId = :saidaId AND objetoId = :objetoId")
    abstract suspend fun resultadoVolta(saidaId: Long, objetoId: Long, resultado: String)

    @Transaction
    @Query("SELECT * FROM proto_saida ORDER BY saiuEmMillis DESC")
    abstract fun observarSaidas(): Flow<List<SaidaComItens>>

    @Transaction
    @Query("SELECT * FROM proto_saida WHERE id = :id")
    abstract fun observarSaida(id: Long): Flow<SaidaComItens?>

    @Query("DELETE FROM proto_saida")
    abstract suspend fun apagarSaidas()

    @Query("DELETE FROM proto_saida_item")
    abstract suspend fun apagarItens()
}

@Database(
    entities = [ProtoConfigEntity::class, ProtoObjetoEntity::class, SaidaEntity::class, SaidaItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PrototipoDb : RoomDatabase() {
    abstract fun dao(): PrototipoDao

    companion object {
        @Volatile private var instancia: PrototipoDb? = null

        fun obter(context: Context): PrototipoDb =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(context.applicationContext, PrototipoDb::class.java, "prototipo.db")
                    .fallbackToDestructiveMigration()
                    .build().also { instancia = it }
            }
    }
}

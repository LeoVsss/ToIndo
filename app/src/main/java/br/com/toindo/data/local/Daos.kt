package br.com.toindo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ResidenciaDao {
    @Query("SELECT * FROM residencia WHERE id = 1")
    fun observar(): Flow<ResidenciaEntity?>

    @Query("SELECT * FROM residencia WHERE id = 1")
    suspend fun obter(): ResidenciaEntity?

    @Upsert
    suspend fun salvar(residencia: ResidenciaEntity)
}

@Dao
interface ConfiguracaoDao {
    @Query("SELECT * FROM configuracao WHERE id = 1")
    fun observar(): Flow<ConfiguracaoEntity?>

    @Query("SELECT * FROM configuracao WHERE id = 1")
    suspend fun obter(): ConfiguracaoEntity?

    @Upsert
    suspend fun salvar(config: ConfiguracaoEntity)
}

@Dao
interface ObjetoDao {
    @Query("SELECT * FROM objetos ORDER BY id")
    fun observarTodos(): Flow<List<ObjetoEntity>>

    @Query("SELECT * FROM objetos WHERE id = :id")
    suspend fun obter(id: Long): ObjetoEntity?

    @Query("SELECT * FROM objetos")
    suspend fun listar(): List<ObjetoEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(objeto: ObjetoEntity): Long

    @Update
    suspend fun atualizar(objeto: ObjetoEntity)
}

@Dao
interface EventoDao {
    @Query("SELECT * FROM eventos ORDER BY instanteMillis DESC")
    fun observarTodos(): Flow<List<EventoEntity>>

    @Query("SELECT * FROM eventos WHERE sincronizado = 0 ORDER BY instanteMillis")
    suspend fun pendentes(): List<EventoEntity>

    @Insert
    suspend fun inserir(evento: EventoEntity): Long

    @Query("UPDATE eventos SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarSincronizado(id: Long)
}

@Dao
interface RegistroConsumoDao {
    @Insert
    suspend fun inserir(registro: RegistroConsumoEntity)

    @Query(
        "SELECT objetoNome AS nome, SUM(kWh) AS kWh, SUM(custo) AS custo " +
            "FROM registros_consumo WHERE fimMillis >= :desdeMillis " +
            "GROUP BY objetoNome ORDER BY kWh DESC"
    )
    fun observarPorObjetoDesde(desdeMillis: Long): Flow<List<ConsumoAgrupado>>

    @Query("SELECT inicioMillis, fimMillis, kWh FROM registros_consumo WHERE fimMillis >= :desdeMillis")
    fun observarIntervalosDesde(desdeMillis: Long): Flow<List<IntervaloConsumo>>

    @Query("SELECT * FROM registros_consumo WHERE fimMillis >= :desdeMillis ORDER BY fimMillis")
    fun observarDesde(desdeMillis: Long): Flow<List<RegistroConsumoEntity>>
}

data class ConsumoAgrupado(val nome: String, val kWh: Double, val custo: Double)
data class IntervaloConsumo(val inicioMillis: Long, val fimMillis: Long, val kWh: Double)

@Dao
interface PresencaDao {
    @Query("SELECT * FROM presenca WHERE id = 1")
    suspend fun obter(): PresencaEntity?

    @Upsert
    suspend fun salvar(p: PresencaEntity)
}

data class SaidaComItens(
    @androidx.room.Embedded val saida: SaidaEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "saidaId") val itens: List<SaidaItemEntity>,
)

@Dao
interface SaidaDao {
    @Insert
    suspend fun inserir(s: SaidaEntity)

    @Insert
    suspend fun inserirItens(itens: List<SaidaItemEntity>)

    @Query("SELECT * FROM saida_itens WHERE saidaId = :saidaId")
    suspend fun itens(saidaId: Long): List<SaidaItemEntity>

    @Query("UPDATE saidas SET voltouEmMillis = :voltou, distanciaMaxM = :distanciaMaxM WHERE id = :id")
    suspend fun fechar(id: Long, voltou: Long, distanciaMaxM: Int)

    @Query("UPDATE saida_itens SET naVolta = :resultado WHERE saidaId = :saidaId AND objetoId = :objetoId")
    suspend fun resultadoVolta(saidaId: Long, objetoId: Long, resultado: String)

    @androidx.room.Transaction
    @Query("SELECT * FROM saidas ORDER BY saiuEmMillis DESC")
    fun observarTodas(): Flow<List<SaidaComItens>>

    @androidx.room.Transaction
    @Query("SELECT * FROM saidas WHERE id = :id")
    fun observar(id: Long): Flow<SaidaComItens?>

    @Query("DELETE FROM saida_itens")
    suspend fun apagarItens()

    @Query("DELETE FROM saidas")
    suspend fun apagarSaidas()
}

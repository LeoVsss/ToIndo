package br.com.toindo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ResidenciaEntity::class,
        ObjetoEntity::class,
        EventoEntity::class,
        ConfiguracaoEntity::class,
        RegistroConsumoEntity::class,
        PresencaEntity::class,
        SaidaEntity::class,
        SaidaItemEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ToIndoDatabase : RoomDatabase() {
    abstract fun residenciaDao(): ResidenciaDao
    abstract fun objetoDao(): ObjetoDao
    abstract fun eventoDao(): EventoDao
    abstract fun configuracaoDao(): ConfiguracaoDao
    abstract fun registroConsumoDao(): RegistroConsumoDao
    abstract fun presencaDao(): PresencaDao
    abstract fun saidaDao(): SaidaDao

    companion object {
        @Volatile private var instancia: ToIndoDatabase? = null

        fun obter(context: Context): ToIndoDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    ToIndoDatabase::class.java,
                    "toindo.db",
                ).fallbackToDestructiveMigration()
                    .build().also { instancia = it }
            }
    }
}

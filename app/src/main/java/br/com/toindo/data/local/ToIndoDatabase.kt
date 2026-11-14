package br.com.toindo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
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
        private val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saida_itens ADD COLUMN potenciaW INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instancia: ToIndoDatabase? = null

        fun obter(context: Context): ToIndoDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    ToIndoDatabase::class.java,
                    "toindo.db",
                ).addMigrations(MIGRACAO_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { instancia = it }
            }
    }
}

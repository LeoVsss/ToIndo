package br.com.toindo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Banco local do app (seção 5: "Room (local) para eventos, regras e consumo").
 * Única fonte da verdade local — o estado dos objetos sobrevive ao fechamento do app (RNF04).
 */
@Database(
    entities = [
        ResidenciaEntity::class,
        ObjetoEntity::class,
        EventoEntity::class,
        ConfiguracaoEntity::class,
        RegistroConsumoEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ToIndoDatabase : RoomDatabase() {
    abstract fun residenciaDao(): ResidenciaDao
    abstract fun objetoDao(): ObjetoDao
    abstract fun eventoDao(): EventoDao
    abstract fun configuracaoDao(): ConfiguracaoDao
    abstract fun registroConsumoDao(): RegistroConsumoDao

    companion object {
        @Volatile private var instancia: ToIndoDatabase? = null

        fun obter(context: Context): ToIndoDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    ToIndoDatabase::class.java,
                    "toindo.db",
                ).build().also { instancia = it }
            }
    }
}

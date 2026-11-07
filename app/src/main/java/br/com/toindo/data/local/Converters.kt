package br.com.toindo.data.local

import androidx.room.TypeConverter
import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.EstadoDispositivo
import br.com.toindo.model.OrigemEvento
import br.com.toindo.model.TipoEvento
import br.com.toindo.model.TipoObjeto

/**
 * Converte os tipos do domínio que o SQLite não guarda nativamente.
 * Enums viram o próprio `name` (estável e legível); o mapa de parâmetros vira "CHAVE=valor;...".
 */
class Converters {

    @TypeConverter fun tipoObjetoToString(v: TipoObjeto?): String? = v?.name
    @TypeConverter fun stringToTipoObjeto(v: String?): TipoObjeto? = v?.let { TipoObjeto.valueOf(it) }

    @TypeConverter fun estadoToString(v: EstadoDispositivo): String = v.name
    @TypeConverter fun stringToEstado(v: String): EstadoDispositivo = EstadoDispositivo.valueOf(v)

    @TypeConverter fun acaoToString(v: AcaoPresenca): String = v.name
    @TypeConverter fun stringToAcao(v: String): AcaoPresenca = AcaoPresenca.valueOf(v)

    @TypeConverter fun tipoEventoToString(v: TipoEvento): String = v.name
    @TypeConverter fun stringToTipoEvento(v: String): TipoEvento = TipoEvento.valueOf(v)

    @TypeConverter fun origemToString(v: OrigemEvento): String = v.name
    @TypeConverter fun stringToOrigem(v: String): OrigemEvento = OrigemEvento.valueOf(v)

    @TypeConverter
    fun parametrosToString(mapa: Map<String, Int>): String =
        mapa.entries.joinToString(";") { "${it.key}=${it.value}" }

    @TypeConverter
    fun stringToParametros(texto: String): Map<String, Int> =
        if (texto.isBlank()) emptyMap()
        else texto.split(";").mapNotNull { par ->
            val (k, v) = par.split("=").let { if (it.size == 2) it[0] to it[1] else return@mapNotNull null }
            v.toIntOrNull()?.let { k to it }
        }.toMap()
}

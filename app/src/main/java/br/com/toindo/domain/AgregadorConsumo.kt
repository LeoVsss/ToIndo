package br.com.toindo.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class Periodo(val rotulo: String) { DIA("Dia"), SEMANA("Semana"), MES("Mês") }

data class SessaoConsumo(
    val objetoId: Long,
    val nome: String,
    val inicioMillis: Long,
    val fimMillis: Long,
    val kWh: Double,
    val custo: Double,
)

data class BarraConsumo(val descricao: String, val rotulo: String, val kWh: Double, val atual: Boolean = false)

data class ConsumoPorObjeto(val objetoId: Long, val nome: String, val kWh: Double, val custo: Double)

data class ResumoConsumo(
    val inicioMillis: Long,
    val fimMillis: Long,
    val totalKwh: Double,
    val totalCusto: Double,
    val porObjeto: List<ConsumoPorObjeto>,
    val barras: List<BarraConsumo>,
)

object AgregadorConsumo {

    private val DIAS_SEMANA = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

    private class Faixa(val inicio: Long, val fim: Long, val descricao: String, val rotulo: String, val atual: Boolean)

    fun sobreposicaoMillis(inicioA: Long, fimA: Long, inicioB: Long, fimB: Long): Long =
        maxOf(0L, minOf(fimA, fimB) - maxOf(inicioA, inicioB))

    fun resumir(
        sessoes: List<SessaoConsumo>,
        periodo: Periodo,
        agoraMillis: Long,
        zona: ZoneId = ZoneId.systemDefault(),
    ): ResumoConsumo {
        val faixas = faixasDe(periodo, agoraMillis, zona)
        val inicio = faixas.first().inicio
        val fim = faixas.last().fim

        val kWhPorFaixa = DoubleArray(faixas.size)
        val kWhPorObjeto = mutableMapOf<Long, Double>()
        val custoPorObjeto = mutableMapOf<Long, Double>()
        val nomePorObjeto = mutableMapOf<Long, Pair<Long, String>>()

        for (s in sessoes) {
            val duracao = s.fimMillis - s.inicioMillis
            if (duracao <= 0) continue
            var fracaoNoPeriodo = 0.0
            faixas.forEachIndexed { i, f ->
                val parte = sobreposicaoMillis(s.inicioMillis, s.fimMillis, f.inicio, f.fim) / duracao.toDouble()
                if (parte > 0) {
                    kWhPorFaixa[i] += s.kWh * parte
                    fracaoNoPeriodo += parte
                }
            }
            if (fracaoNoPeriodo <= 0) continue
            kWhPorObjeto[s.objetoId] = (kWhPorObjeto[s.objetoId] ?: 0.0) + s.kWh * fracaoNoPeriodo
            custoPorObjeto[s.objetoId] = (custoPorObjeto[s.objetoId] ?: 0.0) + s.custo * fracaoNoPeriodo
            val atual = nomePorObjeto[s.objetoId]
            if (atual == null || s.fimMillis >= atual.first) nomePorObjeto[s.objetoId] = s.fimMillis to s.nome
        }

        val porObjeto = kWhPorObjeto.map { (id, kWh) ->
            ConsumoPorObjeto(id, nomePorObjeto.getValue(id).second, kWh, custoPorObjeto.getValue(id))
        }.sortedByDescending { it.kWh }

        return ResumoConsumo(
            inicioMillis = inicio,
            fimMillis = fim,
            totalKwh = porObjeto.sumOf { it.kWh },
            totalCusto = porObjeto.sumOf { it.custo },
            porObjeto = porObjeto,
            barras = faixas.mapIndexed { i, f -> BarraConsumo(f.descricao, f.rotulo, kWhPorFaixa[i], f.atual) },
        )
    }

    private fun faixasDe(periodo: Periodo, agoraMillis: Long, zona: ZoneId): List<Faixa> {
        val hoje = ZonedDateTime.ofInstant(Instant.ofEpochMilli(agoraMillis), zona).toLocalDate().atStartOfDay(zona)
        return when (periodo) {
            Periodo.DIA -> (0 until 24).map { h ->
                val ini = hoje.plusHours(h.toLong())
                val fim = ini.plusHours(1)
                faixa(ini, fim, "${h}h", if (h % 3 == 0) "${h}h" else "", agoraMillis)
            }
            Periodo.SEMANA -> {
                val segunda = hoje.minusDays((hoje.dayOfWeek.value - 1).toLong())
                (0 until 7).map { d ->
                    val ini = segunda.plusDays(d.toLong())
                    val nome = DIAS_SEMANA[ini.dayOfWeek.value % 7]
                    val ehHoje = ini.toLocalDate() == hoje.toLocalDate()
                    faixa(ini, ini.plusDays(1), nome, if (ehHoje) "Hoje" else nome, agoraMillis)
                }
            }
            Periodo.MES -> {
                val primeiro = hoje.withDayOfMonth(1)
                (0 until hoje.toLocalDate().lengthOfMonth()).map { d ->
                    val ini = primeiro.plusDays(d.toLong())
                    val ehHoje = ini.toLocalDate() == hoje.toLocalDate()
                    val rotulo = if (ehHoje) "Hoje" else if (d % 5 == 0) "${ini.dayOfMonth}" else ""
                    faixa(ini, ini.plusDays(1), "%02d/%02d".format(ini.dayOfMonth, ini.monthValue), rotulo, agoraMillis)
                }
            }
        }
    }

    private fun faixa(ini: ZonedDateTime, fim: ZonedDateTime, descricao: String, rotulo: String, agoraMillis: Long): Faixa {
        val a = ini.toInstant().toEpochMilli()
        val b = fim.toInstant().toEpochMilli()
        return Faixa(a, b, descricao, rotulo, agoraMillis >= a && agoraMillis < b)
    }
}

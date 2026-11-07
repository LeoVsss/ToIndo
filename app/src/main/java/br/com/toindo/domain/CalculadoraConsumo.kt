package br.com.toindo.domain

/**
 * RN04 — Consumo = potência × tempo ligado; custo = consumo × tarifa.
 * Estimativa (documentada como tal, risco da seção 6), não medição elétrica de precisão.
 */
object CalculadoraConsumo {

    /** kWh de um objeto de [potenciaW] watts ligado por [duracaoMillis]. */
    fun kWh(potenciaW: Int, duracaoMillis: Long): Double {
        val horas = duracaoMillis / 3_600_000.0
        return potenciaW / 1000.0 * horas
    }

    /** Custo em R$ para um consumo de [kWh] à [tarifaKwh] R$/kWh. */
    fun custo(kWh: Double, tarifaKwh: Double): Double = kWh * tarifaKwh
}

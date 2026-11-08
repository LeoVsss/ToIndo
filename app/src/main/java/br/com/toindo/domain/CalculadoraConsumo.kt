package br.com.toindo.domain

object CalculadoraConsumo {

    fun kWh(potenciaW: Int, duracaoMillis: Long): Double {
        val horas = duracaoMillis / 3_600_000.0
        return potenciaW / 1000.0 * horas
    }

    fun custo(kWh: Double, tarifaKwh: Double): Double = kWh * tarifaKwh
}

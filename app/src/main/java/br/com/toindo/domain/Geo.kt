package br.com.toindo.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Cálculo de distância geográfica (Haversine) para a automação por proximidade. */
object Geo {
    private const val RAIO_TERRA_M = 6_371_000.0

    /** Distância em metros entre dois pontos (lat/lon em graus). */
    fun distanciaM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return RAIO_TERRA_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

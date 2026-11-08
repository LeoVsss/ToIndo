package br.com.toindo.domain

import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.EstadoPresenca
import br.com.toindo.model.ObjetoInteligente

object MotorAutomacao {

    enum class Decisao { LIGAR, DESLIGAR, MANTER }

    fun proximaPresenca(
        atual: EstadoPresenca,
        distanciaM: Double,
        raioSaidaM: Int,
        raioAproximacaoM: Int,
        inicioConfirmacaoMillis: Long?,
        agoraMillis: Long,
        tempoConfirmacaoSeg: Int,
        maxDistanciaForaM: Double,
    ): EstadoPresenca {
        val dentroSaida = distanciaM <= raioSaidaM
        val dentroAproximacao = distanciaM <= raioAproximacaoM
        val margemVoltaM = minOf(30.0, raioSaidaM.toDouble())
        val voltando = distanciaM <= maxDistanciaForaM - margemVoltaM

        return when (atual) {
            EstadoPresenca.EM_CASA ->
                if (!dentroSaida) EstadoPresenca.CONFIRMANDO_SAIDA else EstadoPresenca.EM_CASA

            EstadoPresenca.CONFIRMANDO_SAIDA -> when {
                dentroSaida -> EstadoPresenca.EM_CASA
                inicioConfirmacaoMillis != null &&
                    agoraMillis - inicioConfirmacaoMillis >= tempoConfirmacaoSeg * 1_000L -> EstadoPresenca.FORA
                else -> EstadoPresenca.CONFIRMANDO_SAIDA
            }

            EstadoPresenca.FORA -> when {
                dentroSaida -> EstadoPresenca.EM_CASA
                dentroAproximacao && voltando -> EstadoPresenca.CHEGANDO
                else -> EstadoPresenca.FORA
            }

            EstadoPresenca.CHEGANDO ->
                if (dentroSaida) EstadoPresenca.EM_CASA else EstadoPresenca.CHEGANDO
        }
    }

    fun decidir(
        objeto: ObjetoInteligente,
        de: EstadoPresenca,
        para: EstadoPresenca,
        automacaoGlobalAtiva: Boolean,
    ): Decisao {
        if (!automacaoGlobalAtiva) return Decisao.MANTER
        if (objeto.automacaoPausada) return Decisao.MANTER

        val saiu = para == EstadoPresenca.FORA && de != EstadoPresenca.FORA
        val chegando = para == EstadoPresenca.CHEGANDO && de == EstadoPresenca.FORA

        return when {
            saiu -> when (objeto.acaoAoSair) {
                AcaoPresenca.DESLIGAR -> if (objeto.tipo.podeDesligarSozinho) Decisao.DESLIGAR else Decisao.MANTER
                else -> Decisao.MANTER
            }
            chegando -> when (objeto.acaoAoChegar) {
                AcaoPresenca.LIGAR -> if (objeto.tipo.podeLigarSozinho) Decisao.LIGAR else Decisao.MANTER
                else -> Decisao.MANTER
            }
            else -> Decisao.MANTER
        }
    }

    fun deveLimparPausa(de: EstadoPresenca, para: EstadoPresenca): Boolean =
        (para == EstadoPresenca.FORA && de != EstadoPresenca.FORA) ||
            (para == EstadoPresenca.EM_CASA && de != EstadoPresenca.EM_CASA)
}

package br.com.toindo.domain

import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.EstadoPresenca
import br.com.toindo.model.ObjetoInteligente

/**
 * Regras puras da automação por presença (seção 5 — máquina de estados; RN01–RN05).
 * Sem dependência de Android/Room: recebe fatos, devolve decisões. Fácil de testar.
 */
object MotorAutomacao {

    /** O que fazer com um objeto numa transição de presença. */
    enum class Decisao { LIGAR, DESLIGAR, MANTER }

    /**
     * Calcula o novo estado de presença a partir da distância do morador até a casa.
     * - Sai do raio de saída -> começa a confirmar (RN01).
     * - Fica fora do raio de saída por >= tempoConfirmacao -> FORA.
     * - Entra no raio de aproximação -> CHEGANDO (RN02).
     * - Entra no raio de saída -> EM_CASA.
     */
    fun proximaPresenca(
        atual: EstadoPresenca,
        distanciaM: Double,
        raioSaidaM: Int,
        raioAproximacaoM: Int,
        inicioConfirmacaoMillis: Long?,
        agoraMillis: Long,
        tempoConfirmacaoMin: Int,
    ): EstadoPresenca {
        val dentroSaida = distanciaM <= raioSaidaM
        val dentroAproximacao = distanciaM <= raioAproximacaoM

        return when (atual) {
            EstadoPresenca.EM_CASA ->
                if (!dentroSaida) EstadoPresenca.CONFIRMANDO_SAIDA else EstadoPresenca.EM_CASA

            EstadoPresenca.CONFIRMANDO_SAIDA -> when {
                dentroSaida -> EstadoPresenca.EM_CASA // voltou: oscilação de GPS (RN01)
                inicioConfirmacaoMillis != null &&
                    agoraMillis - inicioConfirmacaoMillis >= tempoConfirmacaoMin * 60_000L -> EstadoPresenca.FORA
                else -> EstadoPresenca.CONFIRMANDO_SAIDA
            }

            EstadoPresenca.FORA ->
                if (dentroAproximacao) EstadoPresenca.CHEGANDO else EstadoPresenca.FORA

            EstadoPresenca.CHEGANDO ->
                if (dentroSaida) EstadoPresenca.EM_CASA else EstadoPresenca.CHEGANDO
        }
    }

    /**
     * Decide a ação para um objeto quando a presença muda.
     * Respeita: automação global, `automacaoPausada` (RN03), e as regras de segurança do tipo
     * (forno/torradeira não ligam sozinhos; geladeira nunca desliga sozinha).
     */
    fun decidir(
        objeto: ObjetoInteligente,
        de: EstadoPresenca,
        para: EstadoPresenca,
        automacaoGlobalAtiva: Boolean,
    ): Decisao {
        if (!automacaoGlobalAtiva) return Decisao.MANTER
        if (objeto.automacaoPausada) return Decisao.MANTER // RN03

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

    /** Uma transição EM_CASA/FORA limpa a pausa manual (RN03: "até a próxima transição"). */
    fun deveLimparPausa(de: EstadoPresenca, para: EstadoPresenca): Boolean =
        (para == EstadoPresenca.FORA && de != EstadoPresenca.FORA) ||
            (para == EstadoPresenca.EM_CASA && de != EstadoPresenca.EM_CASA)
}

package br.com.toindo.model

enum class EstadoDispositivo { LIGADO, DESLIGADO, INDISPONIVEL }

enum class EstadoPresenca { EM_CASA, CONFIRMANDO_SAIDA, FORA, CHEGANDO }

enum class AcaoPresenca(val rotulo: String) {
    LIGAR("Ligar"), DESLIGAR("Desligar"), NADA("Não fazer nada")
}

enum class OrigemEvento(val rotulo: String) { MANUAL("Manual"), AUTOMATICO("Automático") }

enum class TipoEvento { LIGOU, DESLIGOU, AJUSTOU }

enum class Controle(val rotulo: String, val unidade: String, val min: Int, val max: Int, val passo: Int) {
    TEMPERATURA("Temperatura", "°C", 16, 250, 1),
    TIMER("Timer", "min", 0, 180, 5),
    BRILHO("Brilho", "%", 10, 100, 10),
    VOLUME("Volume", "", 0, 100, 5),
    CANAL("Canal", "", 1, 999, 1),
}

enum class TipoObjeto(
    val rotulo: String,
    val rotuloCurto: String,
    val controles: List<Controle>,
    val aoSairPadrao: AcaoPresenca,
    val aoChegarPadrao: AcaoPresenca,
    val podeLigarSozinho: Boolean,
    val podeDesligarSozinho: Boolean = true,
) {
    AR_CONDICIONADO("Ar-condicionado", "Ar-cond.", listOf(Controle.TEMPERATURA), AcaoPresenca.DESLIGAR, AcaoPresenca.LIGAR, true),
    FORNO("Forno", "Forno", listOf(Controle.TEMPERATURA, Controle.TIMER), AcaoPresenca.DESLIGAR, AcaoPresenca.NADA, false),
    TORRADEIRA("Torradeira", "Torradeira", listOf(Controle.TIMER), AcaoPresenca.DESLIGAR, AcaoPresenca.NADA, false),
    GELADEIRA("Geladeira", "Geladeira", listOf(Controle.TEMPERATURA), AcaoPresenca.NADA, AcaoPresenca.NADA, false, podeDesligarSozinho = false),
    TV("TV", "TV", listOf(Controle.VOLUME, Controle.CANAL), AcaoPresenca.DESLIGAR, AcaoPresenca.NADA, true),
    LAMPADA("Lâmpada", "Lâmpada", listOf(Controle.BRILHO), AcaoPresenca.DESLIGAR, AcaoPresenca.LIGAR, true),
    TOMADA("Tomada", "Tomada", emptyList(), AcaoPresenca.DESLIGAR, AcaoPresenca.NADA, true),
    OUTRO("Outro", "Outro", emptyList(), AcaoPresenca.DESLIGAR, AcaoPresenca.NADA, true);

    fun faixa(c: Controle): IntRange = when {
        c == Controle.TEMPERATURA && this == AR_CONDICIONADO -> 16..30
        c == Controle.TEMPERATURA && this == GELADEIRA -> 1..10
        c == Controle.TEMPERATURA && this == FORNO -> 50..250
        else -> c.min..c.max
    }

    val acoesAoChegar: List<AcaoPresenca>
        get() = if (podeLigarSozinho) listOf(AcaoPresenca.LIGAR, AcaoPresenca.NADA) else listOf(AcaoPresenca.NADA)

    val acoesAoSair: List<AcaoPresenca>
        get() = if (podeDesligarSozinho) listOf(AcaoPresenca.DESLIGAR, AcaoPresenca.NADA) else listOf(AcaoPresenca.NADA)
}

data class ObjetoInteligente(
    val id: Long,
    val nome: String,
    val tipo: TipoObjeto,
    val comodo: String,
    val potenciaW: Int,
    val enderecoRemoto: String,
    val estado: EstadoDispositivo,
    val parametros: Map<Controle, Int> = emptyMap(),
    val acaoAoSair: AcaoPresenca = tipo.aoSairPadrao,
    val acaoAoChegar: AcaoPresenca = tipo.aoChegarPadrao,
    val automacaoPausada: Boolean = false,
    val ligadoDesde: String? = null,
    val ligadoDesdeMillis: Long? = null,
) {
    val ligado get() = estado == EstadoDispositivo.LIGADO
}

data class Residencia(
    val nome: String,
    val latitude: Double,
    val longitude: Double,
    val raioSaidaM: Int = 30,
    val raioAproximacaoM: Int = 100,
)

data class EventoObjeto(
    val id: Long,
    val titulo: String,
    val tipo: TipoEvento,
    val origem: OrigemEvento,
    val motivo: String,
    val hora: String,
    val dia: String,
    val tipoObjeto: TipoObjeto?,
    val sincronizado: Boolean = true,
)

data class ConsumoObjeto(val nome: String, val kWh: Double, val custo: Double)

data class Configuracao(
    val tarifaKwh: Double = 0.79,
    val tempoConfirmacaoSaidaSeg: Int = 10,
    val notificacoesAtivas: Boolean = true,
    val automacaoAtiva: Boolean = true,
)

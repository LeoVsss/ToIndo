package br.com.toindo.prototipo

import android.content.Context
import br.com.toindo.domain.Geo
import br.com.toindo.domain.MotorAutomacao
import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.EstadoDispositivo
import br.com.toindo.model.EstadoPresenca
import br.com.toindo.model.ObjetoInteligente
import br.com.toindo.model.Residencia
import br.com.toindo.model.TipoObjeto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/**
 * Estado do PROTÓTIPO, agora PERSISTIDO no Room (prototipo.db): casa, raios, presença,
 * estado dos dispositivos e HISTÓRICO DE SAÍDAS sobrevivem ao fechar o app.
 *
 * Sem Arduino: o "dispositivo" é só um estado salvo. A regra de automação é a mesma do app
 * real ([MotorAutomacao] e [Geo]). Chame [iniciar] uma vez, no Application.
 */
object EstadoPrototipo {

    data class LinhaLog(val hora: String, val texto: String)

    // Resultado gravado por dispositivo (ver SaidaItemEntity).
    const val DESLIGOU = "DESLIGOU"
    const val CONTINUOU_LIGADO = "CONTINUOU_LIGADO"
    const val JA_DESLIGADO = "JA_DESLIGADO"
    const val OFFLINE = "OFFLINE"
    const val LIGOU = "LIGOU"
    const val JA_LIGADO = "JA_LIGADO"
    const val CONTINUOU_DESLIGADO = "CONTINUOU_DESLIGADO"

    private val CASA_PADRAO = Residencia("Minha casa", -23.5505, -46.6333)

    /** Confirmação curta (segundos) para a demonstração não ser lenta. */
    private const val CONFIRMACAO_SAIDA_MS = 3_000L

    /** Quanto o morador precisa ter voltado (m) desde o ponto mais distante para contar como "chegando". */
    private const val MARGEM_VOLTA_M = 30.0

    private lateinit var appContext: Context
    private lateinit var dao: PrototipoDao
    private val escritor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + escritor)
    private var iniciado = false

    private val _residencia = MutableStateFlow(CASA_PADRAO)
    val residencia: StateFlow<Residencia> = _residencia.asStateFlow()

    private val _minhaLat = MutableStateFlow(CASA_PADRAO.latitude)
    val minhaLat: StateFlow<Double> = _minhaLat.asStateFlow()
    private val _minhaLon = MutableStateFlow(CASA_PADRAO.longitude)
    val minhaLon: StateFlow<Double> = _minhaLon.asStateFlow()

    private val _presenca = MutableStateFlow(EstadoPresenca.EM_CASA)
    val presenca: StateFlow<EstadoPresenca> = _presenca.asStateFlow()

    private val _objetos = MutableStateFlow(objetosExemplo())
    val objetos: StateFlow<List<ObjetoInteligente>> = _objetos.asStateFlow()

    private val _log = MutableStateFlow(
        listOf(LinhaLog(agoraTexto(), "Pronto. Você está EM CASA. Mova sua posição para fora do raio para testar."))
    )
    val log: StateFlow<List<LinhaLog>> = _log.asStateFlow()

    private var inicioConfirmacao: Long? = null
    private var maxForaM = 0.0
    private var saidaAbertaId = 0L

    val distanciaM: Double
        get() = Geo.distanciaM(_minhaLat.value, _minhaLon.value, _residencia.value.latitude, _residencia.value.longitude)

    // ---------- Histórico (vem do banco) ----------

    fun saidas(): Flow<List<SaidaComItens>> = dao.observarSaidas()
    fun saida(id: Long): Flow<SaidaComItens?> = dao.observarSaida(id)

    fun apagarHistorico() {
        scope.launch { dao.apagarItens(); dao.apagarSaidas() }
        saidaAbertaId = 0L
        persistir()
    }

    // ---------- Inicialização / persistência ----------

    /** Abre o banco e restaura o estado salvo. Chamar uma vez em Application.onCreate. */
    fun iniciar(context: Context) {
        if (iniciado) return
        iniciado = true
        appContext = context.applicationContext
        dao = PrototipoDb.obter(appContext).dao()

        runBlocking(escritor) {
            dao.config()?.let { c ->
                _residencia.value = Residencia(c.nome, c.latitude, c.longitude, c.raioSaidaM, c.raioAproximacaoM)
                _minhaLat.value = c.minhaLat
                _minhaLon.value = c.minhaLon
                _presenca.value = runCatching { EstadoPresenca.valueOf(c.presenca) }.getOrDefault(EstadoPresenca.EM_CASA)
                maxForaM = c.maxForaM
                saidaAbertaId = c.saidaAbertaId
            }
            val salvos = dao.objetos().associateBy { it.id }
            _objetos.value = objetosExemplo().map { o ->
                val s = salvos[o.id] ?: return@map o
                o.copy(
                    estado = runCatching { EstadoDispositivo.valueOf(s.estado) }.getOrDefault(o.estado),
                    automacaoPausada = s.automacaoPausada,
                    ligadoDesde = s.ligadoDesde,
                )
            }
        }
        if (_presenca.value != EstadoPresenca.EM_CASA) {
            logar("App reaberto: estado restaurado (${rotulo(_presenca.value)}).")
        }
    }

    private fun persistir() {
        if (!iniciado) return
        val r = _residencia.value
        val config = ProtoConfigEntity(
            nome = r.nome, latitude = r.latitude, longitude = r.longitude,
            raioSaidaM = r.raioSaidaM, raioAproximacaoM = r.raioAproximacaoM,
            minhaLat = _minhaLat.value, minhaLon = _minhaLon.value,
            presenca = _presenca.value.name, maxForaM = maxForaM, saidaAbertaId = saidaAbertaId,
        )
        val objs = _objetos.value.map {
            ProtoObjetoEntity(it.id, it.estado.name, it.automacaoPausada, it.ligadoDesde)
        }
        scope.launch { dao.salvarConfig(config); dao.salvarObjetos(objs) }
    }

    // ---------- Ações da tela ----------

    fun definirCasaNaPosicaoAtual() = definirCasa(_minhaLat.value, _minhaLon.value)

    fun definirCasa(lat: Double, lon: Double) {
        _residencia.update { it.copy(latitude = lat, longitude = lon) }
        logar("Casa definida em (%.5f, %.5f).".format(lat, lon))
        reavaliar()
    }

    fun definirRaios(raioSaidaM: Int, raioAproximacaoM: Int) {
        _residencia.update { it.copy(raioSaidaM = raioSaidaM, raioAproximacaoM = maxOf(raioAproximacaoM, raioSaidaM)) }
        reavaliar()
    }

    fun moverPara(lat: Double, lon: Double) {
        _minhaLat.value = lat
        _minhaLon.value = lon
        reavaliar()
    }

    fun afastar(metros: Int) {
        val graus = metros / (111_320.0 * Math.cos(Math.toRadians(_minhaLat.value)))
        _minhaLon.value += graus
        reavaliar()
    }

    /** Comando manual (pausa a automação do objeto — RN03). Dispositivo offline não responde. */
    fun alternarManual(id: Long) {
        _objetos.value = _objetos.value.map { o ->
            if (o.id != id) return@map o
            if (o.estado == EstadoDispositivo.INDISPONIVEL) {
                logar("${o.nome} está offline e não respondeu ao comando.")
                return@map o
            }
            val ligando = !o.ligado
            logar("${o.nome} ${if (ligando) "ligado" else "desligado"} manualmente.")
            o.copy(
                estado = if (ligando) EstadoDispositivo.LIGADO else EstadoDispositivo.DESLIGADO,
                automacaoPausada = true,
                ligadoDesde = if (ligando) agoraTexto() else null,
            )
        }
        persistir()
    }

    /** Simula o dispositivo cair / voltar da rede (para testar o "offline" no histórico). */
    fun alternarOffline(id: Long) {
        _objetos.value = _objetos.value.map { o ->
            if (o.id != id) return@map o
            if (o.estado == EstadoDispositivo.INDISPONIVEL) {
                logar("${o.nome} voltou a ficar online (desligado).")
                o.copy(estado = EstadoDispositivo.DESLIGADO, ligadoDesde = null)
            } else {
                logar("${o.nome} ficou OFFLINE.")
                o.copy(estado = EstadoDispositivo.INDISPONIVEL, ligadoDesde = null)
            }
        }
        persistir()
    }

    /** Volta casa, posição e dispositivos ao padrão. O histórico de saídas é mantido. */
    fun reiniciar() {
        _objetos.value = objetosExemplo()
        _residencia.value = CASA_PADRAO
        _minhaLat.value = CASA_PADRAO.latitude
        _minhaLon.value = CASA_PADRAO.longitude
        _presenca.value = EstadoPresenca.EM_CASA
        inicioConfirmacao = null
        maxForaM = 0.0
        saidaAbertaId = 0L
        _log.value = listOf(LinhaLog(agoraTexto(), "Simulação reiniciada. Você está EM CASA."))
        persistir()
    }

    // ---------- Núcleo: recalcula presença e aplica automação ----------

    /** Chamado sempre que a posição, a casa ou os raios mudam. */
    fun reavaliar() {
        // Até 2 passos: FORA -> CHEGANDO -> EM_CASA pode acontecer numa única leitura.
        if (passo()) passo()
        persistir()
    }

    /** Executa uma avaliação. Retorna true se a presença mudou. */
    private fun passo(): Boolean {
        val casa = _residencia.value
        val dist = distanciaM
        val agora = System.currentTimeMillis()
        val dentroSaida = dist <= casa.raioSaidaM
        val dentroAproximacao = dist <= casa.raioAproximacaoM
        val atual = _presenca.value

        if (atual == EstadoPresenca.FORA || atual == EstadoPresenca.CONFIRMANDO_SAIDA) {
            maxForaM = maxOf(maxForaM, dist)
        }

        val proxima = when (atual) {
            EstadoPresenca.EM_CASA ->
                if (!dentroSaida) {
                    inicioConfirmacao = agora
                    maxForaM = dist
                    logar("Você saiu do raio. Confirmando saída...")
                    EstadoPresenca.CONFIRMANDO_SAIDA
                } else EstadoPresenca.EM_CASA

            EstadoPresenca.CONFIRMANDO_SAIDA -> {
                if (inicioConfirmacao == null) inicioConfirmacao = agora
                when {
                    dentroSaida -> EstadoPresenca.EM_CASA // voltou: foi oscilação (RN01)
                    agora - inicioConfirmacao!! >= CONFIRMACAO_SAIDA_MS -> EstadoPresenca.FORA
                    else -> EstadoPresenca.CONFIRMANDO_SAIDA
                }
            }

            // Só vira "chegando" se de fato VOLTOU em direção à casa. Antes, estar a 400 m (dentro do
            // raio de aproximação de 2 km) fazia o app "chegar" logo após sair e religar tudo.
            EstadoPresenca.FORA ->
                if (dentroSaida || (dentroAproximacao && dist <= maxForaM - minOf(MARGEM_VOLTA_M, casa.raioSaidaM.toDouble()))) EstadoPresenca.CHEGANDO
                else EstadoPresenca.FORA

            EstadoPresenca.CHEGANDO -> if (dentroSaida) EstadoPresenca.EM_CASA else EstadoPresenca.CHEGANDO
        }

        if (proxima == atual) return false
        aplicarTransicao(atual, proxima)
        _presenca.value = proxima
        if (proxima == EstadoPresenca.EM_CASA || proxima == EstadoPresenca.FORA) inicioConfirmacao = null
        return true
    }

    private fun aplicarTransicao(de: EstadoPresenca, para: EstadoPresenca) {
        logar("Presença: ${rotulo(de)} → ${rotulo(para)}")
        val agora = System.currentTimeMillis()
        val saiu = para == EstadoPresenca.FORA && de != EstadoPresenca.FORA
        val chegou = para == EstadoPresenca.CHEGANDO && de == EstadoPresenca.FORA
        val limparPausa = MotorAutomacao.deveLimparPausa(de, para)

        val itens = mutableListOf<SaidaItemEntity>()
        val volta = mutableMapOf<Long, String>()

        fun item(o: ObjetoInteligente, resultado: String, motivo: String) {
            itens += SaidaItemEntity(
                saidaId = agora, objetoId = o.id, nome = o.nome, comodo = o.comodo,
                naSaida = resultado, motivoSaida = motivo, naVolta = null,
            )
        }

        _objetos.value = _objetos.value.map { o ->
            if (o.estado == EstadoDispositivo.INDISPONIVEL) {
                if (saiu) item(o, OFFLINE, "Sem conexão: não foi possível comandar")
                if (chegou) volta[o.id] = OFFLINE
                return@map o
            }
            val estavaLigado = o.ligado
            when (MotorAutomacao.decidir(o, de, para, automacaoGlobalAtiva = true)) {
                MotorAutomacao.Decisao.DESLIGAR -> {
                    logar("${o.nome} desligou (automático · você saiu).")
                    if (saiu) item(o, DESLIGOU, "Desligado automaticamente ao sair")
                    o.copy(estado = EstadoDispositivo.DESLIGADO, ligadoDesde = null, automacaoPausada = false)
                }
                MotorAutomacao.Decisao.LIGAR -> {
                    logar("${o.nome} ligou (automático · você está chegando).")
                    if (chegou) volta[o.id] = LIGOU
                    o.copy(estado = EstadoDispositivo.LIGADO, ligadoDesde = agoraTexto(), automacaoPausada = false)
                }
                MotorAutomacao.Decisao.MANTER -> {
                    if (saiu) {
                        if (!estavaLigado) item(o, JA_DESLIGADO, "Já estava desligado")
                        else item(o, CONTINUOU_LIGADO, motivoManter(o))
                    }
                    if (chegou) volta[o.id] = if (o.ligado) JA_LIGADO else CONTINUOU_DESLIGADO
                    if (limparPausa && o.automacaoPausada) o.copy(automacaoPausada = false) else o
                }
            }
        }

        if (saiu) registrarSaida(agora, itens)
        if (chegou && saidaAbertaId != 0L) registrarVolta(saidaAbertaId, agora, volta)
    }

    private fun motivoManter(o: ObjetoInteligente): String = when {
        !o.tipo.podeDesligarSozinho -> "Regra de segurança: ${o.tipo.rotulo.lowercase()} nunca desliga sozinha"
        o.automacaoPausada -> "Controle manual: automação pausada"
        o.acaoAoSair == AcaoPresenca.NADA -> "Configurado para não fazer nada ao sair"
        else -> "Mantido ligado"
    }

    private fun registrarSaida(id: Long, itens: List<SaidaItemEntity>) {
        saidaAbertaId = id
        val saida = SaidaEntity(id = id, saiuEmMillis = id, voltouEmMillis = null, distanciaMaxM = maxForaM.toInt())
        scope.launch { dao.registrarSaida(saida, itens) }
        logar("Saída registrada (${itens.count { it.naSaida == DESLIGOU }} dispositivo(s) desligado(s)).")
        NotificacaoPrototipo.saida(
            appContext, id,
            desligados = itens.filter { it.naSaida == DESLIGOU }.map { it.nome },
            mantidos = itens.filter { it.naSaida == CONTINUOU_LIGADO }.map { it.nome },
            offline = itens.filter { it.naSaida == OFFLINE }.map { it.nome },
        )
    }

    private fun registrarVolta(saidaId: Long, agora: Long, volta: Map<Long, String>) {
        val dist = maxForaM.toInt()
        scope.launch {
            dao.fecharSaida(saidaId, agora, dist)
            volta.forEach { (objetoId, resultado) -> dao.resultadoVolta(saidaId, objetoId, resultado) }
        }
        val nomes = _objetos.value.associate { it.id to it.nome }
        NotificacaoPrototipo.volta(appContext, saidaId, volta.filterValues { it == LIGOU }.keys.mapNotNull { nomes[it] })
        saidaAbertaId = 0L
    }

    // ---------- Auxiliares ----------

    private fun logar(texto: String) {
        _log.update { (listOf(LinhaLog(agoraTexto(), texto)) + it).take(40) }
    }

    fun rotulo(p: EstadoPresenca) = when (p) {
        EstadoPresenca.EM_CASA -> "EM CASA"
        EstadoPresenca.CONFIRMANDO_SAIDA -> "CONFIRMANDO SAÍDA"
        EstadoPresenca.FORA -> "FORA"
        EstadoPresenca.CHEGANDO -> "CHEGANDO"
    }

    private fun agoraTexto(): String {
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale("pt", "BR"))
        return fmt.format(java.util.Date())
    }

    private fun objetosExemplo(): List<ObjetoInteligente> = listOf(
        ObjetoInteligente(1, "Ar do quarto", TipoObjeto.AR_CONDICIONADO, "Quarto", 1000, "sim/ar",
            EstadoDispositivo.LIGADO, acaoAoSair = AcaoPresenca.DESLIGAR, acaoAoChegar = AcaoPresenca.LIGAR, ligadoDesde = "agora"),
        ObjetoInteligente(2, "TV da sala", TipoObjeto.TV, "Sala", 120, "sim/tv",
            EstadoDispositivo.LIGADO, acaoAoSair = AcaoPresenca.DESLIGAR, acaoAoChegar = AcaoPresenca.NADA, ligadoDesde = "agora"),
        ObjetoInteligente(3, "Geladeira", TipoObjeto.GELADEIRA, "Cozinha", 150, "sim/geladeira",
            EstadoDispositivo.LIGADO, acaoAoSair = AcaoPresenca.NADA, acaoAoChegar = AcaoPresenca.NADA),
        ObjetoInteligente(4, "Lâmpada da sala", TipoObjeto.LAMPADA, "Sala", 15, "sim/lampada",
            EstadoDispositivo.LIGADO, acaoAoSair = AcaoPresenca.DESLIGAR, acaoAoChegar = AcaoPresenca.LIGAR, ligadoDesde = "agora"),
    )
}

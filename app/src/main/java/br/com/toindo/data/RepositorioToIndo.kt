package br.com.toindo.data

import android.content.Context
import br.com.toindo.data.local.EventoEntity
import br.com.toindo.data.local.ObjetoEntity
import br.com.toindo.data.local.PresencaEntity
import br.com.toindo.data.local.RegistroConsumoEntity
import br.com.toindo.data.local.ResultadoSaida
import br.com.toindo.data.local.ResultadoVolta
import br.com.toindo.data.local.SaidaComItens
import br.com.toindo.data.local.SaidaEntity
import br.com.toindo.data.local.SaidaItemEntity
import br.com.toindo.data.local.ToIndoDatabase
import br.com.toindo.data.local.paraDominio
import br.com.toindo.data.local.paraEntity
import br.com.toindo.data.remote.ServicoDispositivo
import br.com.toindo.data.remote.ServicoDispositivoMock
import br.com.toindo.domain.CalculadoraConsumo
import br.com.toindo.domain.Geo
import br.com.toindo.domain.MotorAutomacao
import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.Configuracao
import br.com.toindo.model.Controle
import br.com.toindo.model.EstadoDispositivo
import br.com.toindo.model.EstadoPresenca
import br.com.toindo.model.EventoObjeto
import br.com.toindo.model.ObjetoInteligente
import br.com.toindo.model.OrigemEvento
import br.com.toindo.model.Residencia
import br.com.toindo.model.TipoEvento
import br.com.toindo.model.TipoObjeto
import br.com.toindo.service.Notificacoes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object RepositorioToIndo {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var appContext: Context
    private lateinit var db: ToIndoDatabase
    private var servico: ServicoDispositivo = ServicoDispositivoMock()
    @Volatile private var iniciado = false

    private val mutexPresenca = Mutex()

    private val ligadoDesdeMillis = mutableMapOf<Long, Long>()

    const val PRECISAO_MAX_M = 30

    private val _residencia = MutableStateFlow(Residencia("Casa", -23.5505, -46.6333))
    val residencia: StateFlow<Residencia> = _residencia.asStateFlow()

    private val _presenca = MutableStateFlow(EstadoPresenca.EM_CASA)
    val presenca: StateFlow<EstadoPresenca> = _presenca.asStateFlow()

    private val _distanciaM = MutableStateFlow<Double?>(null)
    val distanciaM: StateFlow<Double?> = _distanciaM.asStateFlow()

    private val _precisaoM = MutableStateFlow<Int?>(null)
    val precisaoM: StateFlow<Int?> = _precisaoM.asStateFlow()

    private val _inicioConfirmacao = MutableStateFlow<Long?>(null)
    val inicioConfirmacao: StateFlow<Long?> = _inicioConfirmacao.asStateFlow()

    private val _configuracao = MutableStateFlow(Configuracao())
    val configuracao: StateFlow<Configuracao> = _configuracao.asStateFlow()

    private val _cadastroConcluido = MutableStateFlow(false)
    val cadastroConcluido: StateFlow<Boolean> = _cadastroConcluido.asStateFlow()

    private val _objetos = MutableStateFlow<List<ObjetoInteligente>>(emptyList())
    val objetos: StateFlow<List<ObjetoInteligente>> = _objetos.asStateFlow()

    private val _eventos = MutableStateFlow<List<EventoObjeto>>(emptyList())
    val eventos: StateFlow<List<EventoObjeto>> = _eventos.asStateFlow()

    private val _registrosConsumo = MutableStateFlow<List<RegistroConsumoEntity>>(emptyList())
    val registrosConsumo: StateFlow<List<RegistroConsumoEntity>> = _registrosConsumo.asStateFlow()

    private val _saidas = MutableStateFlow<List<SaidaComItens>>(emptyList())
    val saidas: StateFlow<List<SaidaComItens>> = _saidas.asStateFlow()

    fun saida(id: Long): Flow<SaidaComItens?> = db.saidaDao().observar(id)

    fun potenciaW(objetoId: Long): Int = _objetos.value.firstOrNull { it.id == objetoId }?.potenciaW ?: 0

    private var maxDistanciaForaM = 0.0
    private var saidaAbertaId = 0L

    fun iniciar(context: Context, servicoDispositivo: ServicoDispositivo? = null) {
        if (iniciado) return
        iniciado = true
        appContext = context.applicationContext
        db = ToIndoDatabase.obter(appContext)
        servicoDispositivo?.let { servico = it }

        runBlocking(Dispatchers.IO) {
            db.presencaDao().obter()?.let { p ->
                _presenca.value = runCatching { EstadoPresenca.valueOf(p.estado) }.getOrDefault(EstadoPresenca.EM_CASA)
                _inicioConfirmacao.value = p.inicioConfirmacaoMillis
                maxDistanciaForaM = p.maxDistanciaForaM
                saidaAbertaId = p.saidaAbertaId
            }
            db.residenciaDao().obter()?.let { _residencia.value = it.paraDominio() }
            db.configuracaoDao().obter()?.let {
                _configuracao.value = it.paraDominio()
                _cadastroConcluido.value = it.cadastroConcluido
            }
        }

        escopo.launch { semearSeVazio() }

        escopo.launch {
            db.objetoDao().observarTodos().collect { lista ->
                lista.forEach { e -> e.ligadoDesdeMillis?.let { ligadoDesdeMillis[e.id] = it } }
                _objetos.value = lista.map { it.paraDominio() }
            }
        }
        escopo.launch {
            db.eventoDao().observarTodos().collect { lista -> _eventos.value = lista.map { it.paraDominio() } }
        }
        escopo.launch {
            db.residenciaDao().observar().collect { e -> e?.let { _residencia.value = it.paraDominio() } }
        }
        escopo.launch {
            db.configuracaoDao().observar().collect { e ->
                e?.let {
                    _configuracao.value = it.paraDominio()
                    _cadastroConcluido.value = it.cadastroConcluido
                }
            }
        }
        escopo.launch {
            val trintaEUmDias = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
            db.registroConsumoDao().observarDesde(trintaEUmDias).collect { _registrosConsumo.value = it }
        }
        escopo.launch {
            db.saidaDao().observarTodas().collect { _saidas.value = it }
        }
    }

    private suspend fun semearSeVazio() {
        if (db.configuracaoDao().obter() == null) {
            db.configuracaoDao().salvar(Configuracao().paraEntity(cadastroConcluido = false))
        }
        if (db.objetoDao().listar().isEmpty()) {
            db.residenciaDao().salvar(Residencia("Casa", -23.5505, -46.6333).paraEntity())
            val agora = System.currentTimeMillis()
            val exemplos = listOf(
                ObjetoEntity(0, "Forno", TipoObjeto.FORNO, "Cozinha", 1500, "casa/cozinha/forno",
                    EstadoDispositivo.LIGADO, mapOf("TEMPERATURA" to 180, "TIMER" to 30),
                    TipoObjeto.FORNO.aoSairPadrao, TipoObjeto.FORNO.aoChegarPadrao, false, agora),
                ObjetoEntity(0, "Ar do quarto", TipoObjeto.AR_CONDICIONADO, "Quarto", 1000, "casa/quarto/ar",
                    EstadoDispositivo.LIGADO, mapOf("TEMPERATURA" to 23),
                    TipoObjeto.AR_CONDICIONADO.aoSairPadrao, TipoObjeto.AR_CONDICIONADO.aoChegarPadrao, false, agora),
                ObjetoEntity(0, "Geladeira", TipoObjeto.GELADEIRA, "Cozinha", 150, "casa/cozinha/geladeira",
                    EstadoDispositivo.LIGADO, mapOf("TEMPERATURA" to 4),
                    TipoObjeto.GELADEIRA.aoSairPadrao, TipoObjeto.GELADEIRA.aoChegarPadrao, false, agora),
                ObjetoEntity(0, "TV da sala", TipoObjeto.TV, "Sala", 120, "casa/sala/tv",
                    EstadoDispositivo.LIGADO, mapOf("VOLUME" to 20, "CANAL" to 5),
                    TipoObjeto.TV.aoSairPadrao, TipoObjeto.TV.aoChegarPadrao, false, agora),
                ObjetoEntity(0, "Lâmpada da sala", TipoObjeto.LAMPADA, "Sala", 15, "casa/sala/lampada",
                    EstadoDispositivo.LIGADO, mapOf("BRILHO" to 80),
                    TipoObjeto.LAMPADA.aoSairPadrao, TipoObjeto.LAMPADA.aoChegarPadrao, false, agora),
                ObjetoEntity(0, "Torradeira", TipoObjeto.TORRADEIRA, "Cozinha", 800, "casa/cozinha/torradeira",
                    EstadoDispositivo.DESLIGADO, mapOf("TIMER" to 5),
                    TipoObjeto.TORRADEIRA.aoSairPadrao, TipoObjeto.TORRADEIRA.aoChegarPadrao, false, null),
            )
            exemplos.forEach { db.objetoDao().inserir(it) }
        }
    }

    fun objeto(id: Long): ObjetoInteligente? = _objetos.value.firstOrNull { it.id == id }

    fun alternar(id: Long) {
        escopo.launch {
            val e = db.objetoDao().obter(id) ?: return@launch
            val ligando = e.estado != EstadoDispositivo.LIGADO
            val aceito = if (ligando) servico.ligar(e.enderecoRemoto) else servico.desligar(e.enderecoRemoto)
            if (!aceito) return@launch

            val agora = System.currentTimeMillis()
            if (ligando) {
                ligadoDesdeMillis[id] = agora
                db.objetoDao().atualizar(e.copy(estado = EstadoDispositivo.LIGADO, automacaoPausada = true, ligadoDesdeMillis = agora))
            } else {
                fecharConsumo(id, agora)
                db.objetoDao().atualizar(e.copy(estado = EstadoDispositivo.DESLIGADO, automacaoPausada = true, ligadoDesdeMillis = null))
            }
            registrarEvento(
                objeto = e,
                tipo = if (ligando) TipoEvento.LIGOU else TipoEvento.DESLIGOU,
                origem = OrigemEvento.MANUAL,
                titulo = "${e.nome} ${if (ligando) "ligou" else "desligou"}",
                motivo = "pelo app",
                instante = agora,
            )
        }
    }

    fun ajustar(id: Long, controle: Controle, valor: Int) {
        escopo.launch {
            val e = db.objetoDao().obter(id) ?: return@launch
            val v = valor.coerceIn(controle.min, controle.max)
            if (!servico.ajustar(e.enderecoRemoto, controle, v)) return@launch
            db.objetoDao().atualizar(e.copy(parametros = e.parametros + (controle.name to v), automacaoPausada = true))
        }
    }

    fun definirAcoes(id: Long, aoSair: AcaoPresenca, aoChegar: AcaoPresenca) {
        escopo.launch {
            val e = db.objetoDao().obter(id) ?: return@launch
            db.objetoDao().atualizar(e.copy(acaoAoSair = aoSair, acaoAoChegar = aoChegar))
        }
    }

    fun adicionar(novo: ObjetoInteligente) {
        escopo.launch {
            val agora = System.currentTimeMillis()
            db.objetoDao().inserir(
                novo.paraEntity(ligadoDesdeMillis = if (novo.ligado) agora else null).copy(id = 0)
            )
        }
    }

    fun salvarResidencia(r: Residencia, tempoConfirmacaoSeg: Int) {
        escopo.launch {
            db.residenciaDao().salvar(r.paraEntity())
            _residencia.value = r
            atualizarConfigSuspenso { it.copy(tempoConfirmacaoSaidaSeg = tempoConfirmacaoSeg) }
        }
    }

    fun atualizarConfiguracao(transform: (Configuracao) -> Configuracao) {
        escopo.launch { atualizarConfigSuspenso(transform) }
    }

    fun concluirCadastro() {
        escopo.launch {
            val atual = db.configuracaoDao().obter() ?: Configuracao().paraEntity(false)
            db.configuracaoDao().salvar(atual.copy(cadastroConcluido = true))
        }
    }

    fun apagarHistoricoSaidas() {
        escopo.launch {
            mutexPresenca.withLock {
                db.saidaDao().apagarItens()
                db.saidaDao().apagarSaidas()
                saidaAbertaId = 0L
                salvarPresenca()
            }
        }
    }

    fun avaliarPosicao(lat: Double, lon: Double, precisaoM: Int?) {
        escopo.launch {
            mutexPresenca.withLock {
                _precisaoM.value = precisaoM
                if (precisaoM != null && precisaoM > PRECISAO_MAX_M) return@withLock

                val casa = _residencia.value
                val distancia = Geo.distanciaM(lat, lon, casa.latitude, casa.longitude)
                _distanciaM.value = distancia
                avaliarSuspenso(distancia)
            }
        }
    }

    private suspend fun avaliarSuspenso(distancia: Double) {
        repeat(2) {
            val atual = _presenca.value
            val agora = System.currentTimeMillis()
            if (atual == EstadoPresenca.FORA || atual == EstadoPresenca.CONFIRMANDO_SAIDA) {
                maxDistanciaForaM = maxOf(maxDistanciaForaM, distancia)
            }
            val casa = _residencia.value
            val proxima = MotorAutomacao.proximaPresenca(
                atual = atual,
                distanciaM = distancia,
                raioSaidaM = casa.raioSaidaM,
                raioAproximacaoM = casa.raioAproximacaoM,
                inicioConfirmacaoMillis = _inicioConfirmacao.value,
                agoraMillis = agora,
                tempoConfirmacaoSeg = _configuracao.value.tempoConfirmacaoSaidaSeg,
                maxDistanciaForaM = maxDistanciaForaM,
            )
            if (proxima == atual) {
                salvarPresenca()
                return
            }

            when (proxima) {
                EstadoPresenca.CONFIRMANDO_SAIDA -> {
                    _inicioConfirmacao.value = agora
                    maxDistanciaForaM = distancia
                }
                EstadoPresenca.FORA -> _inicioConfirmacao.value = null
                EstadoPresenca.EM_CASA -> {
                    _inicioConfirmacao.value = null
                    maxDistanciaForaM = 0.0
                }
                EstadoPresenca.CHEGANDO -> Unit
            }
            aplicarTransicao(atual, proxima, agora)
            _presenca.value = proxima
            salvarPresenca()
        }
    }

    private suspend fun salvarPresenca() {
        db.presencaDao().salvar(
            PresencaEntity(
                estado = _presenca.value.name,
                inicioConfirmacaoMillis = _inicioConfirmacao.value,
                maxDistanciaForaM = maxDistanciaForaM,
                saidaAbertaId = saidaAbertaId,
            )
        )
    }

    private suspend fun aplicarTransicao(de: EstadoPresenca, para: EstadoPresenca, agora: Long) {
        val automacaoAtiva = _configuracao.value.automacaoAtiva
        val saiu = para == EstadoPresenca.FORA && de != EstadoPresenca.FORA
        val chegando = para == EstadoPresenca.CHEGANDO && de == EstadoPresenca.FORA
        val voltou = para == EstadoPresenca.EM_CASA && (de == EstadoPresenca.FORA || de == EstadoPresenca.CHEGANDO)
        val limparPausa = saiu || voltou

        when {
            saiu -> registrarSaida(agora, automacaoAtiva)
            chegando -> preLigarAoChegar(agora, automacaoAtiva)
            voltou -> registrarVolta(agora, automacaoAtiva)
        }

        if (limparPausa) {
            db.objetoDao().listar().filter { it.automacaoPausada }.forEach {
                db.objetoDao().atualizar(it.copy(automacaoPausada = false))
            }
        }
    }

    private suspend fun registrarSaida(agora: Long, automacaoAtiva: Boolean) {
        val itens = mutableListOf<SaidaItemEntity>()
        fun item(e: ObjetoEntity, resultado: String, motivo: String) {
            itens += SaidaItemEntity(
                saidaId = agora, objetoId = e.id, nome = e.nome, comodo = e.comodo,
                naSaida = resultado, motivo = motivo, naVolta = null,
            )
        }

        val saida = SaidaEntity(id = agora, saiuEmMillis = agora, voltouEmMillis = null, distanciaMaxM = maxDistanciaForaM.toInt())

        db.objetoDao().listar().forEach { e ->
            val dominio = e.paraDominio()
            if (e.estado == EstadoDispositivo.INDISPONIVEL) {
                item(e, ResultadoSaida.OFFLINE, "Sem conexão: não foi possível comandar")
                return@forEach
            }
            when (MotorAutomacao.decidir(dominio, EstadoPresenca.CONFIRMANDO_SAIDA, EstadoPresenca.FORA, automacaoAtiva)) {
                MotorAutomacao.Decisao.DESLIGAR -> {
                    if (servico.desligar(e.enderecoRemoto)) {
                        fecharConsumo(e.id, agora)
                        db.objetoDao().atualizar(e.copy(estado = EstadoDispositivo.DESLIGADO, ligadoDesdeMillis = null, automacaoPausada = false))
                        registrarEvento(e, TipoEvento.DESLIGOU, OrigemEvento.AUTOMATICO, "${e.nome} desligou", "você saiu", agora)
                        item(e, ResultadoSaida.DESLIGOU, "Desligado automaticamente ao sair")
                    } else {
                        item(e, ResultadoSaida.OFFLINE, "Não respondeu ao comando de desligar")
                    }
                }
                else -> {
                    if (e.estado != EstadoDispositivo.LIGADO) item(e, ResultadoSaida.JA_DESLIGADO, "Já estava desligado")
                    else item(e, ResultadoSaida.CONTINUOU_LIGADO, motivoManter(dominio, automacaoAtiva))
                }
            }
        }

        db.saidaDao().inserir(saida)
        db.saidaDao().inserirItens(itens)
        saidaAbertaId = agora

        if (_configuracao.value.notificacoesAtivas) {
            Notificacoes.saida(
                appContext, agora,
                desligados = itens.filter { it.naSaida == ResultadoSaida.DESLIGOU }.map { it.nome },
                mantidos = itens.filter { it.naSaida == ResultadoSaida.CONTINUOU_LIGADO }.map { it.nome },
                offline = itens.filter { it.naSaida == ResultadoSaida.OFFLINE }.map { it.nome },
            )
        }
    }

    private suspend fun preLigarAoChegar(agora: Long, automacaoAtiva: Boolean) {
        db.objetoDao().listar().forEach { e ->
            if (e.estado == EstadoDispositivo.INDISPONIVEL) return@forEach
            if (MotorAutomacao.decidir(e.paraDominio(), EstadoPresenca.FORA, EstadoPresenca.CHEGANDO, automacaoAtiva) ==
                MotorAutomacao.Decisao.LIGAR
            ) ligarAutomatico(e, agora, "você está chegando")
        }
    }

    private suspend fun registrarVolta(agora: Long, automacaoAtiva: Boolean) {
        val saidaId = saidaAbertaId
        val itens = if (saidaId != 0L) db.saidaDao().itens(saidaId) else emptyList()
        val desligadosNaSaida = itens.filter { it.naSaida == ResultadoSaida.DESLIGOU }.map { it.objetoId }.toSet()
        val resultado = mutableMapOf<Long, String>()

        db.objetoDao().listar().forEach { e ->
            val podeReligar = e.id in desligadosNaSaida && !e.automacaoPausada
            resultado[e.id] = when {
                e.estado == EstadoDispositivo.INDISPONIVEL -> ResultadoVolta.OFFLINE
                e.estado == EstadoDispositivo.LIGADO ->
                    if (itens.firstOrNull { it.objetoId == e.id }?.naVolta == ResultadoVolta.LIGOU) ResultadoVolta.LIGOU
                    else ResultadoVolta.JA_LIGADO
                !podeReligar || !automacaoAtiva -> ResultadoVolta.CONTINUOU_DESLIGADO
                !e.tipo.podeLigarSozinho -> ResultadoVolta.NAO_RELIGA_SEGURANCA
                ligarAutomatico(e, agora, "você voltou para casa") -> ResultadoVolta.LIGOU
                else -> ResultadoVolta.OFFLINE
            }
        }

        if (saidaId != 0L) {
            db.saidaDao().fechar(saidaId, agora, maxDistanciaForaM.toInt())
            resultado.forEach { (objetoId, r) -> db.saidaDao().resultadoVolta(saidaId, objetoId, r) }
        }
        saidaAbertaId = 0L
        maxDistanciaForaM = 0.0

        if (_configuracao.value.notificacoesAtivas) {
            val nomes = db.objetoDao().listar().associate { it.id to it.nome }
            fun com(r: String) = resultado.filterValues { it == r }.keys.mapNotNull { nomes[it] }
            Notificacoes.volta(
                appContext, saidaId,
                ligados = com(ResultadoVolta.LIGOU),
                naoReligados = com(ResultadoVolta.NAO_RELIGA_SEGURANCA),
                offline = com(ResultadoVolta.OFFLINE),
            )
        }
    }

    private suspend fun ligarAutomatico(e: ObjetoEntity, agora: Long, motivo: String): Boolean {
        if (!servico.ligar(e.enderecoRemoto)) return false
        ligadoDesdeMillis[e.id] = agora
        db.objetoDao().atualizar(e.copy(estado = EstadoDispositivo.LIGADO, ligadoDesdeMillis = agora, automacaoPausada = false))
        registrarEvento(e, TipoEvento.LIGOU, OrigemEvento.AUTOMATICO, "${e.nome} ligou", motivo, agora)
        if (saidaAbertaId != 0L) db.saidaDao().resultadoVolta(saidaAbertaId, e.id, ResultadoVolta.LIGOU)
        return true
    }

    private fun motivoManter(o: ObjetoInteligente, automacaoAtiva: Boolean): String = when {
        !automacaoAtiva -> "Automação desativada nos Ajustes"
        !o.tipo.podeDesligarSozinho -> "Regra de segurança: ${o.tipo.rotulo.lowercase()} nunca desliga sozinha"
        o.automacaoPausada -> "Controle manual: automação pausada"
        o.acaoAoSair == AcaoPresenca.NADA -> "Configurado para não fazer nada ao sair"
        else -> "Mantido ligado"
    }

    fun sincronizarPendentes() {
        escopo.launch {
            db.eventoDao().pendentes().forEach { db.eventoDao().marcarSincronizado(it.id) }
        }
    }

    private suspend fun atualizarConfigSuspenso(transform: (Configuracao) -> Configuracao) {
        val atualEntity = db.configuracaoDao().obter()
        val atual = atualEntity?.paraDominio() ?: Configuracao()
        val cadastro = atualEntity?.cadastroConcluido ?: _cadastroConcluido.value
        val novo = transform(atual)
        _configuracao.value = novo
        db.configuracaoDao().salvar(novo.paraEntity(cadastroConcluido = cadastro))
    }

    private suspend fun registrarEvento(
        objeto: ObjetoEntity,
        tipo: TipoEvento,
        origem: OrigemEvento,
        titulo: String,
        motivo: String,
        instante: Long,
    ) {
        db.eventoDao().inserir(
            EventoEntity(
                id = 0,
                objetoId = objeto.id,
                titulo = titulo,
                tipo = tipo,
                origem = origem,
                motivo = motivo,
                tipoObjeto = objeto.tipo,
                instanteMillis = instante,
                sincronizado = false,
            )
        )
    }

    private suspend fun fecharConsumo(id: Long, fimMillis: Long) {
        val e = db.objetoDao().obter(id) ?: return
        val inicio = ligadoDesdeMillis.remove(id) ?: e.ligadoDesdeMillis ?: return
        if (fimMillis <= inicio) return
        val kWh = CalculadoraConsumo.kWh(e.potenciaW, fimMillis - inicio)
        val custo = CalculadoraConsumo.custo(kWh, _configuracao.value.tarifaKwh)
        db.registroConsumoDao().inserir(RegistroConsumoEntity(0, e.id, e.nome, inicio, fimMillis, kWh, custo))
    }
}

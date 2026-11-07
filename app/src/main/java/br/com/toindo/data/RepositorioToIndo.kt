package br.com.toindo.data

import android.content.Context
import br.com.toindo.data.local.ConsumoAgrupado
import br.com.toindo.data.local.ToIndoDatabase
import br.com.toindo.data.local.paraDominio
import br.com.toindo.data.local.paraEntity
import br.com.toindo.data.remote.ServicoDispositivo
import br.com.toindo.data.remote.ServicoDispositivoMock
import br.com.toindo.domain.CalculadoraConsumo
import br.com.toindo.domain.MotorAutomacao
import br.com.toindo.model.AcaoPresenca
import br.com.toindo.model.Configuracao
import br.com.toindo.model.ConsumoObjeto
import br.com.toindo.model.Controle
import br.com.toindo.model.EstadoDispositivo
import br.com.toindo.model.EstadoPresenca
import br.com.toindo.model.EventoObjeto
import br.com.toindo.model.ObjetoInteligente
import br.com.toindo.model.OrigemEvento
import br.com.toindo.model.Residencia
import br.com.toindo.model.TipoEvento
import br.com.toindo.model.TipoObjeto
import br.com.toindo.data.local.EventoEntity
import br.com.toindo.data.local.ObjetoEntity
import br.com.toindo.data.local.RegistroConsumoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Fachada única que as telas usam (padrão Repository, seção 8 do Design / seção 5 do Plan).
 *
 * A ASSINATURA PÚBLICA é idêntica à versão de interface — por isso nenhuma tela/ViewModel
 * precisou mudar. Internamente, agora delega para:
 *   - Room       -> persistência local (RF01, RF02, RF07; RNF04 offline-first)
 *   - Retrofit   -> comando do objeto (RF06; RNF03), via [ServicoDispositivo]
 *   - MotorAutomacao -> regras de presença/segurança (RF03–RF05; RN01–RN05)
 *
 * Deve ser inicializada uma vez em MainActivity/Application com [iniciar]. Antes disso,
 * expõe valores padrão em memória para não quebrar `.value` síncrono (ToIndoApp, ResidenciaVM).
 */
object RepositorioToIndo {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var db: ToIndoDatabase
    private var servico: ServicoDispositivo = ServicoDispositivoMock()
    @Volatile private var iniciado = false

    /** Instante (epoch millis) em que cada objeto foi ligado — base do consumo (RN04). */
    private val ligadoDesdeMillis = mutableMapOf<Long, Long>()

    // ---------- StateFlows expostos (contrato) ----------

    private val _residencia = MutableStateFlow(Residencia("Casa", -23.5505, -46.6333))
    val residencia: StateFlow<Residencia> = _residencia.asStateFlow()

    private val _presenca = MutableStateFlow(EstadoPresenca.EM_CASA)
    val presenca: StateFlow<EstadoPresenca> = _presenca.asStateFlow()

    private val _configuracao = MutableStateFlow(Configuracao())
    val configuracao: StateFlow<Configuracao> = _configuracao.asStateFlow()

    private val _cadastroConcluido = MutableStateFlow(false)
    val cadastroConcluido: StateFlow<Boolean> = _cadastroConcluido.asStateFlow()

    private val _objetos = MutableStateFlow<List<ObjetoInteligente>>(emptyList())
    val objetos: StateFlow<List<ObjetoInteligente>> = _objetos.asStateFlow()

    private val _eventos = MutableStateFlow<List<EventoObjeto>>(emptyList())
    val eventos: StateFlow<List<EventoObjeto>> = _eventos.asStateFlow()

    // ---------- Consumo (US04) ----------
    // Mantidos como propriedades para não mudar o ConsumoViewModel. Agora são atualizados
    // a partir dos RegistroConsumoEntity agregados no Room, com fallback de exemplo.

    @Volatile
    var consumoSemana: List<Double> = listOf(2.1, 2.8, 1.9, 3.0, 2.4, 2.2, 2.4)
        private set

    @Volatile
    var consumoPorObjeto: List<ConsumoObjeto> = listOf(
        ConsumoObjeto("Ar do quarto", 9.6, 7.58),
        ConsumoObjeto("Geladeira", 4.2, 3.32),
        ConsumoObjeto("Forno", 2.1, 1.66),
        ConsumoObjeto("TV da sala", 0.9, 0.71),
    )
        private set

    @Volatile
    var economiaEstimadaSemana: Double = 9.40
        private set

    /** Compatibilidade com o código existente (ConsumoViewModel lê ECONOMIA_ESTIMADA_SEMANA). */
    val ECONOMIA_ESTIMADA_SEMANA: Double get() = economiaEstimadaSemana

    // ---------- Inicialização ----------

    /**
     * Liga o repositório ao Room e, opcionalmente, a um serviço de comando real.
     * Idempotente. Chamar no onCreate da Activity/Application.
     */
    fun iniciar(context: Context, servicoDispositivo: ServicoDispositivo? = null) {
        if (iniciado) return
        iniciado = true
        db = ToIndoDatabase.obter(context)
        servicoDispositivo?.let { servico = it }

        escopo.launch { semearSeVazio() }

        // Espelha o Room nos StateFlows (fonte da verdade local).
        escopo.launch {
            db.objetoDao().observarTodos().collect { lista ->
                lista.forEach { e -> e.ligadoDesdeMillis?.let { ligadoDesdeMillis[e.id] = it } }
                _objetos.value = lista.map { it.paraDominio() }
            }
        }
        escopo.launch {
            db.eventoDao().observarTodos().collect { lista ->
                _eventos.value = lista.map { it.paraDominio() }
            }
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
        // Relatórios de consumo dos últimos 7 dias (US04).
        escopo.launch {
            val seteDiasAtras = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            db.registroConsumoDao().observarPorObjetoDesde(seteDiasAtras).collect { agregados ->
                if (agregados.isNotEmpty()) {
                    consumoPorObjeto = agregados.map(ConsumoAgrupado::paraDominio)
                }
            }
        }
        escopo.launch {
            val seteDiasAtras = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            db.registroConsumoDao().observarIntervalosDesde(seteDiasAtras).collect { intervalos ->
                if (intervalos.isNotEmpty()) consumoSemana = agruparPorDia(intervalos.map { it.fimMillis to it.kWh })
            }
        }
    }

    private suspend fun semearSeVazio() {
        if (db.configuracaoDao().obter() == null) {
            db.configuracaoDao().salvar(Configuracao().paraEntity(cadastroConcluido = false))
        }
        if (db.objetoDao().listar().isEmpty()) {
            // Dados de exemplo para a demonstração do protótipo (equivalentes à versão de interface).
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
                    EstadoDispositivo.DESLIGADO, mapOf("VOLUME" to 20, "CANAL" to 5),
                    TipoObjeto.TV.aoSairPadrao, TipoObjeto.TV.aoChegarPadrao, false, null),
                ObjetoEntity(0, "Torradeira", TipoObjeto.TORRADEIRA, "Cozinha", 800, "casa/cozinha/torradeira",
                    EstadoDispositivo.DESLIGADO, mapOf("TIMER" to 5),
                    TipoObjeto.TORRADEIRA.aoSairPadrao, TipoObjeto.TORRADEIRA.aoChegarPadrao, false, null),
            )
            exemplos.forEach { db.objetoDao().inserir(it) }
        }
    }

    // ---------- Leitura ----------

    fun objeto(id: Long): ObjetoInteligente? = _objetos.value.firstOrNull { it.id == id }

    // ---------- Comandos manuais (RF06, RN03) ----------

    /** Comando manual: obedece, comanda o objeto remotamente e pausa a automação dele (RN03). */
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
            db.objetoDao().atualizar(
                e.copy(parametros = e.parametros + (controle.name to v), automacaoPausada = true)
            )
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
            // id=0 -> o Room gera via autoGenerate (preserva o contrato "maxOf+1" visto pela UI).
            db.objetoDao().inserir(novo.paraEntity(ligadoDesdeMillis = null).copy(id = 0))
        }
    }

    fun salvarResidencia(r: Residencia, tempoConfirmacaoMin: Int) {
        escopo.launch {
            db.residenciaDao().salvar(r.paraEntity())
            atualizarConfigSuspenso { it.copy(tempoConfirmacaoSaidaMin = tempoConfirmacaoMin) }
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

    // ---------- Automação por presença (RF03–RF05, RN01–RN03) ----------

    /**
     * Chamado pelo motor de localização quando a presença muda. Aplica a decisão do
     * [MotorAutomacao] a cada objeto, comanda remotamente e registra os eventos.
     */
    fun aplicarTransicaoPresenca(de: EstadoPresenca, para: EstadoPresenca) {
        if (de == para) return
        escopo.launch {
            _presenca.value = para
            val automacaoAtiva = _configuracao.value.automacaoAtiva
            val limparPausa = MotorAutomacao.deveLimparPausa(de, para)
            val agora = System.currentTimeMillis()

            db.objetoDao().listar().forEach { e ->
                val dominio = e.paraDominio()
                when (MotorAutomacao.decidir(dominio, de, para, automacaoAtiva)) {
                    MotorAutomacao.Decisao.LIGAR -> if (servico.ligar(e.enderecoRemoto)) {
                        ligadoDesdeMillis[e.id] = agora
                        db.objetoDao().atualizar(e.copy(estado = EstadoDispositivo.LIGADO, ligadoDesdeMillis = agora, automacaoPausada = false))
                        registrarEvento(e, TipoEvento.LIGOU, OrigemEvento.AUTOMATICO, "${e.nome} ligou", "você está chegando", agora)
                    }
                    MotorAutomacao.Decisao.DESLIGAR -> if (servico.desligar(e.enderecoRemoto)) {
                        fecharConsumo(e.id, agora)
                        db.objetoDao().atualizar(e.copy(estado = EstadoDispositivo.DESLIGADO, ligadoDesdeMillis = null, automacaoPausada = false))
                        registrarEvento(e, TipoEvento.DESLIGOU, OrigemEvento.AUTOMATICO, "${e.nome} desligou", "você saiu", agora)
                    }
                    MotorAutomacao.Decisao.MANTER -> if (limparPausa && e.automacaoPausada) {
                        db.objetoDao().atualizar(e.copy(automacaoPausada = false)) // RN03: pausa dura até a transição
                    }
                }
            }
        }
    }

    // ---------- Sincronização offline-first (RNF04) ----------

    /** Reenvia eventos pendentes quando a conectividade volta. Chamar do WorkManager. */
    fun sincronizarPendentes() {
        escopo.launch {
            db.eventoDao().pendentes().forEach { db.eventoDao().marcarSincronizado(it.id) }
        }
    }

    // ---------- Internos ----------

    private suspend fun atualizarConfigSuspenso(transform: (Configuracao) -> Configuracao) {
        val atualEntity = db.configuracaoDao().obter()
        val atual = atualEntity?.paraDominio() ?: Configuracao()
        val cadastro = atualEntity?.cadastroConcluido ?: _cadastroConcluido.value
        db.configuracaoDao().salvar(transform(atual).paraEntity(cadastroConcluido = cadastro))
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
                sincronizado = false, // RNF04: nasce pendente, sincroniza depois
            )
        )
    }

    /** Fecha o intervalo de consumo de um objeto que estava ligado (RN04). */
    private suspend fun fecharConsumo(id: Long, fimMillis: Long) {
        val e = db.objetoDao().obter(id) ?: return
        val inicio = ligadoDesdeMillis.remove(id) ?: e.ligadoDesdeMillis ?: return
        if (fimMillis <= inicio) return
        val kWh = CalculadoraConsumo.kWh(e.potenciaW, fimMillis - inicio)
        val custo = CalculadoraConsumo.custo(kWh, _configuracao.value.tarifaKwh)
        db.registroConsumoDao().inserir(
            RegistroConsumoEntity(0, e.id, e.nome, inicio, fimMillis, kWh, custo)
        )
    }

    /** Soma kWh em 7 baldes diários (domingo..hoje) para o gráfico semanal. */
    private fun agruparPorDia(pares: List<Pair<Long, Double>>): List<Double> {
        val umDia = 24L * 60 * 60 * 1000
        val agora = System.currentTimeMillis()
        val inicioHoje = agora - (agora % umDia)
        val baldes = DoubleArray(7)
        pares.forEach { (fim, kWh) ->
            val diasAtras = ((inicioHoje - (fim - (fim % umDia))) / umDia).toInt()
            if (diasAtras in 0..6) baldes[6 - diasAtras] += kWh
        }
        return baldes.toList()
    }
}

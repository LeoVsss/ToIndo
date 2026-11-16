# Padrões de Projeto — ToIndo

Este documento descreve os padrões de projeto aplicados no código-fonte do ToIndo, onde cada um aparece e por que foi escolhido.

---

## MVVM — Model-View-ViewModel

| Arquivo | Onde |
|---|---|
| `ui/screens/*Screen.kt` | `InicioViewModel`, `DetalheObjetoViewModel` e os demais 5 ViewModels das telas |

**O que é:** separa a lógica de apresentação (ViewModel) da interface (Composable). A tela só lê estado; ela não toma decisões.

**Por que foi usado:** Jetpack Compose foi projetado para trabalhar com esse fluxo unidirecional de dados. O ViewModel sobrevive a recomposições e rotações de tela, mantendo o estado consistente. As telas observam `StateFlow` via `collectAsStateWithLifecycle()` e recompõem somente quando o valor muda, o que evita trabalho desnecessário na UI thread.

**Exemplo no código:**
```kotlin
// InicioScreen.kt
class InicioViewModel : ViewModel() {
    val ui: StateFlow<InicioUi> = combine(
        RepositorioToIndo.objetos, RepositorioToIndo.presenca, ...
    ) { ... }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InicioUi())
}

@Composable
fun InicioScreen(vm: InicioViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // a tela apenas lê `ui`, nunca grava diretamente no repositório
}
```

---

## Repository

| Arquivo | Onde |
|---|---|
| `data/RepositorioToIndo.kt` | `object RepositorioToIndo` |

**O que é:** centraliza todas as operações de dados (banco local, chamadas remotas e lógica de presença) atrás de uma única API. ViewModels e serviços em background nunca tocam o banco ou a rede diretamente.

**Por que foi usado:** os ViewModels não precisam saber se um dado vem do Room, de uma chamada HTTP ou de um cache em memória. Isso também facilita testes: basta substituir o repositório por um fake. No ToIndo, o `RepositorioToIndo` junta o `ToIndoDatabase` (Room), o `ServicoDispositivo` (HTTP) e toda a lógica de transição de presença num único ponto de acesso.

**Exemplo no código:**
```kotlin
// RepositorioToIndo.kt
object RepositorioToIndo {
    private lateinit var db: ToIndoDatabase
    private lateinit var servico: ServicoDispositivo

    fun alternar(id: Long) { /* chama servico.ligar/desligar e persiste no db */ }
    fun avaliarPosicao(lat: Double, lon: Double, precisaoM: Int?) { /* lógica de presença */ }
}
```

---

## Singleton

| Arquivo | Onde |
|---|---|
| `data/RepositorioToIndo.kt` | `object RepositorioToIndo` |
| `domain/MotorAutomacao.kt` | `object MotorAutomacao` |
| `data/local/ToIndoDatabase.kt` | `instancia` no `companion object`, com `@Volatile` e `synchronized` |

**O que é:** garante que exista apenas uma instância de um componente durante toda a vida do processo.

**Por que foi usado:** o repositório carrega conexões com banco e corrotinas de longa duração — criá-lo mais de uma vez desperdiçaria recursos e introduziria estados inconsistentes. O `MotorAutomacao` é puro (sem estado próprio), então um `object` é suficiente e evita alocações desnecessárias. O `ToIndoDatabase` usa o padrão clássico de *double-checked locking* com `@Volatile` para ser seguro em múltiplas threads.

**Exemplo no código:**
```kotlin
// ToIndoDatabase.kt — double-checked locking
companion object {
    @Volatile private var instancia: ToIndoDatabase? = null

    fun obter(context: Context): ToIndoDatabase =
        instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(...).build().also { instancia = it }
        }
}
```

---

## DAO — Data Access Object

| Arquivo | Onde |
|---|---|
| `data/local/Daos.kt` | `ResidenciaDao`, `ConfiguracaoDao`, `ObjetoDao`, `EventoDao`, `RegistroConsumoDao`, `PresencaDao`, `SaidaDao` |

**O que é:** interfaces que declaram operações de banco de dados. O Room gera a implementação em tempo de compilação.

**Por que foi usado:** cada DAO isola as queries SQL de uma entidade específica. O código que chama `objetoDao().observarTodos()` não precisa escrever SQL nem gerenciar cursores. O Room também valida as queries em tempo de compilação, eliminando erros de sintaxe em produção. O uso de `Flow` nos métodos de leitura conecta o banco diretamente ao sistema reativo do Kotlin.

**Exemplo no código:**
```kotlin
// Daos.kt
@Dao
interface ObjetoDao {
    @Query("SELECT * FROM objetos ORDER BY id")
    fun observarTodos(): Flow<List<ObjetoEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(objeto: ObjetoEntity): Long
}
```

---

## Observer

| Arquivo | Onde |
|---|---|
| `data/RepositorioToIndo.kt` | `StateFlow` e `SharedFlow` para objetos, presença, avisos e demais estados |

**O que é:** permite que consumidores (ViewModels, telas) reajam automaticamente a mudanças de dados sem fazer polling.

**Por que foi usado:** num aplicativo reativo em Compose, o Observer é o mecanismo central de comunicação. `StateFlow` mantém sempre o último valor e entrega uma nova emissão a cada coletor que se inscreve, o que é ideal para estado de UI. `SharedFlow` (usado em `avisos`) é adequado para eventos pontuais como mensagens de erro, que não têm um "valor atual" significativo. O Room também expõe `Flow` nos DAOs, fechando a cadeia: banco → repositório → ViewModel → tela, tudo reativo.

**Exemplo no código:**
```kotlin
// RepositorioToIndo.kt
private val _objetos = MutableStateFlow<List<ObjetoInteligente>>(emptyList())
val objetos: StateFlow<List<ObjetoInteligente>> = _objetos.asStateFlow()

private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 8)
val avisos: SharedFlow<String> = _avisos.asSharedFlow()
```

---

## Strategy

| Arquivo | Onde |
|---|---|
| `data/remote/ServicoDispositivo.kt` | Interface `ServicoDispositivo` e implementação `ServicoDispositivoHttp` |

**O que é:** define uma família de algoritmos (ou protocolos de comunicação) por trás de uma interface comum, permitindo trocar a implementação sem alterar o código que a usa.

**Por que foi usado:** o protocolo de comunicação com os dispositivos físicos pode mudar. Hoje é HTTP REST via Retrofit (`ServicoDispositivoHttp`), mas o `RepositorioToIndo.iniciar()` aceita qualquer `ServicoDispositivo` como parâmetro, o que permite injetar uma implementação MQTT, BLE ou um fake de testes sem nenhuma alteração no repositório. Isso também isola as dependências de rede: só `ServicoDispositivoHttp` conhece o Retrofit.

**Exemplo no código:**
```kotlin
// ServicoDispositivo.kt
interface ServicoDispositivo {
    suspend fun ping(endereco: String): Boolean
    suspend fun ligar(endereco: String): Boolean
    suspend fun desligar(endereco: String): Boolean
    suspend fun ajustar(endereco: String, controle: Controle, valor: Int): Boolean
}

// RepositorioToIndo.kt — a implementação é injetável
fun iniciar(context: Context, servicoDispositivo: ServicoDispositivo? = null) {
    servico = servicoDispositivo ?: ServicoDispositivoHttp(Rede.criarApi())
}
```

---

## State

| Arquivo | Onde |
|---|---|
| `model/Modelos.kt` | Enums `EstadoPresenca` e `EstadoDispositivo` |
| `domain/MotorAutomacao.kt` | Função `proximaPresenca`, que contém as transições |

**O que é:** modela um objeto que se comporta de forma diferente dependendo do estado atual, tornando as transições explícitas e rastreáveis.

**Por que foi usado:** a presença do usuário tem quatro estados bem definidos (`EM_CASA`, `CONFIRMANDO_SAIDA`, `FORA`, `CHEGANDO`), e as transições dependem de distância, tempo decorrido e estado anterior. Colocar toda essa lógica numa função pura (`proximaPresenca`) que recebe o estado atual e retorna o próximo torna o comportamento testável sem efeitos colaterais e evita `if`/`else` espalhados pelo código. `EstadoDispositivo` serve o mesmo propósito para os objetos inteligentes.

**Exemplo no código:**
```kotlin
// MotorAutomacao.kt
fun proximaPresenca(atual: EstadoPresenca, distanciaM: Double, ...): EstadoPresenca {
    return when (atual) {
        EstadoPresenca.EM_CASA ->
            if (!dentroSaida) EstadoPresenca.CONFIRMANDO_SAIDA else EstadoPresenca.EM_CASA

        EstadoPresenca.CONFIRMANDO_SAIDA -> when {
            dentroSaida -> EstadoPresenca.EM_CASA
            tempoExpirou -> EstadoPresenca.FORA
            else -> EstadoPresenca.CONFIRMANDO_SAIDA
        }
        // ...
    }
}
```

---

## Mapper / DTO

| Arquivo | Onde |
|---|---|
| `data/local/Mappers.kt` | Funções `paraDominio()` e `paraEntity()` |
| `data/remote/ToIndoApi.kt` | `ComandoDto` e `RespostaComandoDto` |

**O que é:** o Mapper converte entre camadas de dados (entidade do banco ↔ modelo de domínio). O DTO (*Data Transfer Object*) é uma estrutura simples que carrega dados pela rede, sem lógica de negócio.

**Por que foi usado:** separar a entidade do Room do modelo de domínio protege a lógica de negócio de mudanças no esquema do banco e vice-versa. Se uma coluna for renomeada, só o `Mapper` e a `Entity` mudam; o resto do app continua usando `ObjetoInteligente`. Os DTOs `ComandoDto` e `RespostaComandoDto` cumprem papel similar na camada de rede: o Retrofit serializa e desserializa essas classes simples, e o `ServicoDispositivoHttp` as converte para o tipo que o resto do código entende (`Boolean`).

**Exemplo no código:**
```kotlin
// Mappers.kt
fun ObjetoEntity.paraDominio() = ObjetoInteligente(
    id = id, nome = nome, tipo = tipo, estado = estado, ...
)

fun ObjetoInteligente.paraEntity(ligadoDesdeMillis: Long?) = ObjetoEntity(
    id = id, nome = nome, tipo = tipo, estado = estado, ...
)

// ToIndoApi.kt
data class ComandoDto(val acao: String, val controle: String? = null, val valor: Int? = null)
data class RespostaComandoDto(val ok: Boolean = false, val estado: String? = null)
```

---

## Resumo

| Padrão | Arquivo | Onde |
|---|---|---|
| MVVM | `ui/screens/*Screen.kt` | Os 7 ViewModels das telas. As telas Compose observam o estado deles. |
| Repository | `data/RepositorioToIndo.kt` | O `object RepositorioToIndo` junta banco, dispositivo e lógica de presença. |
| Singleton | `data/RepositorioToIndo.kt` | `object RepositorioToIndo` |
| Singleton | `domain/MotorAutomacao.kt` | `object MotorAutomacao` |
| Singleton | `data/local/ToIndoDatabase.kt` | `instancia` no `companion object`, com `@Volatile` e `synchronized` |
| DAO | `data/local/Daos.kt` | `ResidenciaDao`, `ConfiguracaoDao`, `ObjetoDao`, `EventoDao`, `RegistroConsumoDao`, `PresencaDao` e `SaidaDao` |
| Observer | `data/RepositorioToIndo.kt` | Os `StateFlow` e `SharedFlow` (objetos, presença, avisos e outros). ViewModels e telas consomem esses fluxos. |
| Strategy | `data/remote/ServicoDispositivo.kt` | A interface `ServicoDispositivo` e a implementação `ServicoDispositivoHttp`. |
| State | `model/Modelos.kt` | Os enums `EstadoPresenca` e `EstadoDispositivo`. |
| State | `domain/MotorAutomacao.kt` | A função `proximaPresenca`, que faz as transições. |
| Mapper | `data/local/Mappers.kt` | Funções como `paraDominio()` e `paraEntity()`, que convertem entidade do Room em modelo de domínio e o contrário. |
| DTO | `data/remote/ToIndoApi.kt` | `ComandoDto` e `RespostaComandoDto`, que levam os dados pela rede. |

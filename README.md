# Tô indo! — interface (Kotlin + Jetpack Compose)

Implementação da interface definida no documento da fase Design: identidade, 8 telas, navegação e arquitetura MVVM.

## Como abrir

1. Android Studio (Koala ou mais novo) → **File › Open** → escolha a pasta `ToIndo`.
2. Aceite o "Gradle sync". Se ele pedir o Gradle Wrapper, clique em **OK / Use Gradle wrapper**.
3. Rode no emulador ou celular (Android 8.0+, API 26).

## Fonte Space Grotesk (títulos e números grandes)

A Inter já vem no projeto. A Space Grotesk precisa ser adicionada uma vez:

1. Abra o **Resource Manager** (aba à esquerda) → **+** → **More fonts…**
2. Procure **Space Grotesk**, escolha o peso **Medium**, marque **Add font to project** e salve como `space_grotesk_medium`.

Enquanto ela não for adicionada, o app usa a Inter no lugar; nada quebra.

## Estrutura

```
app/src/main/java/br/com/toindo/
├── MainActivity.kt              ponto de entrada (edge-to-edge + tema)
├── model/Modelos.kt             ObjetoInteligente, TipoObjeto (capacidades e regras de segurança), enums
├── data/RepositorioToIndo.kt    Repository único (hoje em memória, com dados de exemplo)
└── ui/
    ├── theme/                   Color, Type, Theme (paleta, tipografia, cantos, espaçamentos)
    ├── components/              cartões, pílula de presença, chips, stepper, faixas
    ├── navigation/ToIndoApp.kt  rotas, NavHost e barra inferior
    └── screens/                 T1 a T8, cada uma com seu ViewModel (StateFlow)
```

| Tela | Arquivo |
| --- | --- |
| T1 Boas-vindas e permissões | `BoasVindasScreen.kt` |
| T2 Residência (mapa offline + GPS) | `ResidenciaScreen.kt` |
| T3 Adicionar objeto (8 tipos) | `AdicionarObjetoScreen.kt` |
| T4 Início | `InicioScreen.kt` |
| T5 Detalhe do objeto | `DetalheObjetoScreen.kt` |
| T6 Consumo | `ConsumoScreen.kt` |
| T7 Histórico | `HistoricoScreen.kt` |
| T8 Ajustes | `AjustesScreen.kt` |

## O que está pronto e o que falta

Pronto: todas as telas navegáveis, tema claro/escuro, regras de segurança por tipo (forno e torradeira não ligam sozinhos; geladeira nunca desliga), comando manual pausando a automação do objeto (RN03), mapa osmdroid com os dois raios e posição pelo `GPS_PROVIDER`.

Para a fase Prototype (a equipe de desenvolvimento):

- Trocar o corpo de `RepositorioToIndo` por **Room** (local) + **MQTT/Retrofit** (comandos), mantendo as funções. As telas não mudam.
- **Geofencing** e serviço em primeiro plano para a automação com o app fechado (máquina de estados da seção 5).
- Baixar o mapa da região no cadastro com `CacheManager` do osmdroid (comentário em `ResidenciaScreen.kt`).
- Pedir `ACCESS_BACKGROUND_LOCATION` em um segundo passo, depois da localização comum.
- **Hilt** para injeção de dependência e **WorkManager** para sincronizar eventos pendentes (RNF04).

Os números de consumo e o histórico são exemplos para a interface.

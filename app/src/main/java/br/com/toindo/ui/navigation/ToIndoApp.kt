package br.com.toindo.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.ui.screens.AdicionarObjetoScreen
import br.com.toindo.ui.screens.AjustesScreen
import br.com.toindo.ui.screens.BoasVindasScreen
import br.com.toindo.ui.screens.ConsumoScreen
import br.com.toindo.ui.screens.DetalheObjetoScreen
import br.com.toindo.ui.screens.HistoricoScreen
import br.com.toindo.ui.screens.InicioScreen
import br.com.toindo.ui.screens.ResidenciaScreen
import br.com.toindo.ui.screens.SaidaDetalheScreen
import br.com.toindo.ui.screens.SaidasScreen

object Rotas {
    const val BOAS_VINDAS = "boas_vindas"
    const val RESIDENCIA = "residencia"
    const val ADICIONAR_OBJETO = "adicionar_objeto"
    const val ADICIONAR_OBJETO_ROTA = "adicionar_objeto?id={id}"
    fun editarObjeto(id: Long) = "adicionar_objeto?id=$id"
    const val INICIO = "inicio"
    const val OBJETO = "objeto/{id}"
    const val CONSUMO = "consumo"
    const val HISTORICO = "historico"
    const val AJUSTES = "ajustes"

    fun objeto(id: Long) = "objeto/$id"
    const val SAIDAS = "saidas"
    const val SAIDA = "saida/{id}"
    fun saida(id: Long) = "saida/$id"
}

private data class Aba(val rota: String, val rotulo: String, val icone: ImageVector)

private val abas = listOf(
    Aba(Rotas.INICIO, "Início", Icons.Rounded.Home),
    Aba(Rotas.CONSUMO, "Consumo", Icons.Rounded.Bolt),
    Aba(Rotas.HISTORICO, "Histórico", Icons.Rounded.History),
    Aba(Rotas.AJUSTES, "Ajustes", Icons.Rounded.Tune),
)

@Composable
fun ToIndoApp(navController: NavHostController = rememberNavController()) {
    val inicio = remember { if (RepositorioToIndo.cadastroConcluido.value) Rotas.INICIO else Rotas.BOAS_VINDAS }
    val entrada by navController.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route
    val mostrarBarra = abas.any { it.rota == rotaAtual }

    val pedidoSaida by br.com.toindo.service.PedidoAbrirSaida.pendente.collectAsState()
    LaunchedEffect(pedidoSaida) {
        pedidoSaida?.let { id ->
            navController.navigate(Rotas.saida(id))
            br.com.toindo.service.PedidoAbrirSaida.pendente.value = null
        }
    }

    val contexto = LocalContext.current
    LaunchedEffect(Unit) {
        RepositorioToIndo.avisos.collect { Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (mostrarBarra) BarraInferior(rotaAtual, navController) },
    ) { padding ->
        NavHost(navController, startDestination = inicio, modifier = Modifier.padding(padding)) {
            composable(Rotas.BOAS_VINDAS) {
                BoasVindasScreen(onContinuar = { navController.navigate(Rotas.RESIDENCIA) })
            }
            composable(Rotas.RESIDENCIA) {
                ResidenciaScreen(
                    onVoltar = { navController.popBackStack() },
                    onContinuar = {
                        if (RepositorioToIndo.cadastroConcluido.value) navController.popBackStack()
                        else navController.navigate(Rotas.ADICIONAR_OBJETO)
                    },
                )
            }
            composable(
                Rotas.ADICIONAR_OBJETO_ROTA,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { e ->
                AdicionarObjetoScreen(
                    idEdicao = e.arguments?.getLong("id")?.takeIf { it > 0 },
                    onVoltar = { navController.popBackStack() },
                    onConcluir = {
                        if (RepositorioToIndo.cadastroConcluido.value) {
                            navController.popBackStack()
                        } else {
                            RepositorioToIndo.concluirCadastro()
                            navController.navigate(Rotas.INICIO) {
                                popUpTo(Rotas.BOAS_VINDAS) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(Rotas.INICIO) {
                InicioScreen(
                    onAbrirObjeto = { navController.navigate(Rotas.objeto(it)) },
                    onAdicionar = { navController.navigate(Rotas.ADICIONAR_OBJETO) },
                    onAbrirHistorico = { navegarAba(navController, Rotas.HISTORICO) },
                    onAbrirSaidas = { navController.navigate(Rotas.SAIDAS) },
                )
            }
            composable(Rotas.OBJETO, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                DetalheObjetoScreen(
                    id = e.arguments?.getLong("id") ?: 0L,
                    onVoltar = { navController.popBackStack() },
                    onEditar = { navController.navigate(Rotas.editarObjeto(e.arguments?.getLong("id") ?: 0L)) },
                )
            }
            composable(Rotas.CONSUMO) { ConsumoScreen() }
            composable(Rotas.HISTORICO) { HistoricoScreen() }
            composable(Rotas.AJUSTES) {
                AjustesScreen(
                    onEditarResidencia = { navController.navigate(Rotas.RESIDENCIA) },
                    onObjetos = { navController.navigate(Rotas.ADICIONAR_OBJETO) },
                    onAbrirSaidas = { navController.navigate(Rotas.SAIDAS) },
                    onDadosApagados = {
                        navController.navigate(Rotas.BOAS_VINDAS) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(Rotas.SAIDAS) {
                SaidasScreen(
                    onVoltar = { navController.popBackStack() },
                    onAbrirSaida = { navController.navigate(Rotas.saida(it)) },
                )
            }
            composable(Rotas.SAIDA, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                SaidaDetalheScreen(
                    saidaId = e.arguments?.getLong("id") ?: 0L,
                    onVoltar = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun navegarAba(navController: NavHostController, rota: String) {
    navController.navigate(rota) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BarraInferior(rotaAtual: String?, navController: NavHostController) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        abas.forEach { aba ->
            NavigationBarItem(
                selected = rotaAtual == aba.rota,
                onClick = { navegarAba(navController, aba.rota) },
                icon = { Icon(aba.icone, contentDescription = null) },
                label = { Text(aba.rotulo, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

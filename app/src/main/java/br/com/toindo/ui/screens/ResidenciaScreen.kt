package br.com.toindo.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.toindo.data.RepositorioToIndo
import br.com.toindo.model.Residencia
import br.com.toindo.ui.components.BotaoPrincipal
import br.com.toindo.ui.components.CardToIndo
import br.com.toindo.ui.components.Stepper
import br.com.toindo.ui.components.TopoComVoltar
import br.com.toindo.ui.theme.Espaco
import br.com.toindo.ui.theme.ToIndo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

data class ResidenciaUi(
    val latitude: Double,
    val longitude: Double,
    val raioSaidaM: Int,
    val raioAproximacaoM: Int,
    val confirmacaoMin: Int,
    val precisaoM: Int? = null,
)

class ResidenciaViewModel : ViewModel() {
    private val r = RepositorioToIndo.residencia.value
    private val _ui = MutableStateFlow(
        ResidenciaUi(r.latitude, r.longitude, r.raioSaidaM, r.raioAproximacaoM, RepositorioToIndo.configuracao.value.tempoConfirmacaoSaidaMin)
    )
    val ui = _ui.asStateFlow()

    fun posicao(lat: Double, lon: Double, precisao: Int?) = _ui.update { it.copy(latitude = lat, longitude = lon, precisaoM = precisao) }
    fun raioSaida(m: Int) = _ui.update { it.copy(raioSaidaM = m, raioAproximacaoM = maxOf(it.raioAproximacaoM, m)) }
    /** RN02: aproximação nunca menor que a saída. */
    fun raioAproximacao(m: Int) = _ui.update { it.copy(raioAproximacaoM = maxOf(m, it.raioSaidaM)) }
    fun confirmacao(delta: Int) = _ui.update { it.copy(confirmacaoMin = (it.confirmacaoMin + delta).coerceIn(1, 30)) }

    fun salvar() {
        val u = _ui.value
        RepositorioToIndo.salvarResidencia(Residencia("Casa", u.latitude, u.longitude, u.raioSaidaM, u.raioAproximacaoM), u.confirmacaoMin)
    }
}

/** T2 — Cadastro da residência (US06, RF01, RN01, RN02). Mapa e GPS funcionam sem internet. */
@Composable
fun ResidenciaScreen(onVoltar: () -> Unit, onContinuar: () -> Unit, vm: ResidenciaViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val semInternet = remember { !temInternet(context) }
    val editando = RepositorioToIndo.cadastroConcluido.value

    // Pede uma leitura ativa de GPS; se faltar permissão, o launcher abaixo solicita e repete.
    fun capturarLocalizacao() {
        obterLocalizacaoAtual(
            context = context,
            onSucesso = { lat, lon, precisao -> vm.posicao(lat, lon, precisao) },
            onFalha = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
        )
    }

    val pedirPermissaoLoc = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val concedeu = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (concedeu) capturarLocalizacao()
        else Toast.makeText(context, "Permissão de localização negada.", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Espaco.margem),
        verticalArrangement = Arrangement.spacedBy(Espaco.l),
    ) {
        TopoComVoltar(titulo = null, subtitulo = if (editando) "Editar residência" else "Passo 1 de 2", onVoltar = onVoltar)
        Text("Onde fica sua casa?", style = MaterialTheme.typography.headlineSmall)

        Box(Modifier.fillMaxWidth().height(280.dp).clip(MaterialTheme.shapes.large)) {
            MapaOffline(ui)
            Selo(
                texto = ui.precisaoM?.let { "GPS · precisão de $it m" } ?: "GPS · procurando satélites",
                ponto = if (ui.precisaoM != null) ToIndo.extras.economia else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            if (semInternet) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.onSurface).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(14.dp))
                    Text("Mapa offline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surface)
                }
            }
        }

        OutlinedButton(
            onClick = {
                if (temPermissaoLoc(context)) capturarLocalizacao()
                else pedirPermissaoLoc.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(20.dp))
            Text("  Usar minha localização atual", style = MaterialTheme.typography.titleSmall)
        }

        CardToIndo {
            ControleRaio("Raio de saída", "${ui.raioSaidaM} m", ui.raioSaidaM.toFloat(), 100f..1000f, ToIndo.extras.presencaTexto) {
                vm.raioSaida(((it / 50).toInt() * 50))
            }
            ControleRaio(
                "Raio de aproximação", formatarDistancia(ui.raioAproximacaoM), ui.raioAproximacaoM.toFloat(), 500f..10000f,
                MaterialTheme.colorScheme.primary,
            ) { vm.raioAproximacao(((it / 500).toInt() * 500)) }
            Row(Modifier.fillMaxWidth().padding(top = Espaco.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Confirmar saída após", style = MaterialTheme.typography.titleSmall)
                    Text("Evita desligar por oscilação do GPS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Stepper("${ui.confirmacaoMin} min", "tempo de confirmação", { vm.confirmacao(-1) }, { vm.confirmacao(+1) })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espaco.s)) {
            Icon(Icons.Rounded.Check, null, tint = ToIndo.extras.economiaTexto, modifier = Modifier.size(18.dp))
            Text("Mapa da região salvo para uso sem internet", style = MaterialTheme.typography.bodyMedium, color = ToIndo.extras.economiaTexto)
        }

        BotaoPrincipal(if (editando) "Salvar" else "Continuar", onClick = { vm.salvar(); onContinuar() }, modifier = Modifier.padding(bottom = Espaco.xl))
    }
}

@Composable
private fun ControleRaio(rotulo: String, valor: String, atual: Float, faixa: ClosedFloatingPointRange<Float>, cor: Color, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(rotulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valor, style = MaterialTheme.typography.titleSmall, color = cor)
        }
        Slider(
            value = atual.coerceIn(faixa),
            onValueChange = onChange,
            valueRange = faixa,
            colors = SliderDefaults.colors(thumbColor = cor, activeTrackColor = cor),
        )
    }
}

@Composable
private fun Selo(texto: String, ponto: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surface).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(ponto))
        Text(texto, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Mapa OpenStreetMap (osmdroid). Os tiles ficam em cache no aparelho; para garantir a área da casa
 * sem internet, baixe a região uma vez no cadastro com org.osmdroid.tileprovider.cachemanager.CacheManager
 * (downloadAreaAsync com o retângulo do raio de aproximação, zoom 13 a 18).
 */
@Composable
private fun MapaOffline(ui: ResidenciaUi) {
    val context = LocalContext.current
    val primaria = MaterialTheme.colorScheme.primary.toArgb()
    val presenca = MaterialTheme.colorScheme.secondary
    val mapa = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }
    DisposableEffect(Unit) { onDispose { mapa.onDetach() } }
    AndroidView(factory = { mapa }, modifier = Modifier.fillMaxSize()) { m ->
        val casa = GeoPoint(ui.latitude, ui.longitude)
        m.overlays.clear()
        m.overlays.add(Polygon(m).apply {
            setPoints(Polygon.pointsAsCircle(casa, ui.raioAproximacaoM.toDouble()))
            outlinePaint.color = primaria
            outlinePaint.strokeWidth = 3f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 14f), 0f)
            fillPaint.color = android.graphics.Color.TRANSPARENT
        })
        m.overlays.add(Polygon(m).apply {
            setPoints(Polygon.pointsAsCircle(casa, ui.raioSaidaM.toDouble()))
            outlinePaint.color = presenca.toArgb()
            outlinePaint.strokeWidth = 4f
            fillPaint.color = presenca.copy(alpha = 0.18f).toArgb()
        })
        m.overlays.add(Marker(m).apply {
            position = casa
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Casa"
        })
        m.controller.setCenter(casa)
        m.invalidate()
    }
}

private fun temPermissaoLoc(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Obtém a posição atual do aparelho de forma ATIVA (não só o último fix em cache).
 * Usa o FusedLocationProviderClient (GPS + rede/WiFi). Chama [onSucesso] com lat/lon/precisão
 * ou [onFalha] com uma mensagem amigável. Requer permissão já concedida.
 */
@SuppressLint("MissingPermission")
private fun obterLocalizacaoAtual(
    context: Context,
    onSucesso: (Double, Double, Int?) -> Unit,
    onFalha: (String) -> Unit,
) {
    if (!temPermissaoLoc(context)) {
        onFalha("Permissão de localização necessária.")
        return
    }
    // Avisa se o GPS/localização do sistema estiver desligado.
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val ligado = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    if (!ligado) {
        onFalha("Ative a localização (GPS) do aparelho.")
        return
    }

    val cliente = LocationServices.getFusedLocationProviderClient(context)
    val cancel = CancellationTokenSource()

    cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
        .addOnSuccessListener { loc ->
            if (loc != null) {
                onSucesso(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy.toInt() else null)
            } else {
                // Fallback: último fix conhecido, caso o fix ativo volte nulo.
                cliente.lastLocation
                    .addOnSuccessListener { ultima ->
                        if (ultima != null) onSucesso(ultima.latitude, ultima.longitude, if (ultima.hasAccuracy()) ultima.accuracy.toInt() else null)
                        else onFalha("Não foi possível obter o GPS agora. Tente novamente ao ar livre.")
                    }
                    .addOnFailureListener { onFalha("Falha ao ler o GPS.") }
            }
        }
        .addOnFailureListener { onFalha("Falha ao ler o GPS.") }
}

private fun temInternet(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun formatarDistancia(m: Int): String = if (m >= 1000) {
    val km = m / 1000.0
    if (km % 1.0 == 0.0) "${km.toInt()} km" else "${"%.1f".format(km).replace('.', ',')} km"
} else "$m m"

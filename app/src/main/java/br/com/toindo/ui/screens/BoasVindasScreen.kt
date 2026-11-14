package br.com.toindo.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.toindo.ui.components.BotaoPrincipal
import br.com.toindo.ui.components.CardToIndo
import br.com.toindo.ui.components.LogoToIndo
import br.com.toindo.ui.theme.Espaco

@Composable
fun BoasVindasScreen(onContinuar: () -> Unit) {
    val permissoes = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val politicaAberta = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onContinuar() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espaco.margem, vertical = Espaco.xl),
        verticalArrangement = Arrangement.spacedBy(Espaco.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoToIndo()
            Text("Tô indo!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
        }
        IlustracaoRaios()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Chegue em casa no clima certo", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Com o Tô indo!, o que ficou ligado desliga quando você sai, e a casa se prepara quando você está chegando: " +
                    "ar, forno, TV, lâmpadas e outros objetos inteligentes. Tudo pelo GPS do celular, mesmo sem internet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CardToIndo(padding = Espaco.l) {
            ItemPermissao(Icons.Rounded.LocationOn, "Localização o tempo todo", "Para saber quando você sai ou chega, com o app fechado.")
            ItemPermissao(Icons.Rounded.Notifications, "Notificações", "Avisamos sempre que um objeto for ligado ou desligado.")
            ItemPermissao(Icons.Rounded.Shield, "Seus dados ficam no aparelho", "A localização não é enviada a ninguém (LGPD).", ultimo = true)
        }
        if (politicaAberta.value) PoliticaPrivacidade { politicaAberta.value = false }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            BotaoPrincipal("Permitir e continuar", onClick = { pedir.launch(permissoes) })
            TextButton(onClick = { politicaAberta.value = true }) { Text("Ver política de privacidade") }
        }
    }
}

@Composable
private fun PoliticaPrivacidade(onFechar: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Política de privacidade") },
        text = {
            Text(
                "Usamos sua localização apenas para saber quando você sai ou chega em casa e acionar seus objetos. " +
                    "A localização é processada e guardada somente neste aparelho e não é enviada a servidores. " +
                    "Os comandos são enviados apenas aos objetos que você cadastrou, na sua rede. " +
                    "Você pode apagar todos os dados em Ajustes, opção \"Apagar meus dados\"."
            )
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Entendi") } },
    )
}

@Composable
private fun ItemPermissao(icone: ImageVector, titulo: String, texto: String, ultimo: Boolean = false) {
    Row(Modifier.padding(vertical = Espaco.m), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (!ultimo) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun IlustracaoRaios() {
    val primaria = MaterialTheme.colorScheme.primary
    val presenca = MaterialTheme.colorScheme.secondary
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics { contentDescription = "Casa no centro do raio de saída e do raio de aproximação" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(180.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(primaria, radius = size.minDimension * 0.47f, center = c,
                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f))))
            drawCircle(presenca.copy(alpha = 0.18f), radius = size.minDimension * 0.28f, center = c)
            drawCircle(presenca, radius = size.minDimension * 0.28f, center = c, style = Stroke(width = 3f))
        }
        Icon(Icons.Rounded.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
    }
}

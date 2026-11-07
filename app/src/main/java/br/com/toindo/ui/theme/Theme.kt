package br.com.toindo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Claro = lightColorScheme(
    primary = AzulToIndo,
    onPrimary = Color.White,
    primaryContainer = Nevoa,
    onPrimaryContainer = NevoaTinta,
    secondary = Frescor,
    onSecondary = Color.White,
    tertiary = AmbarEnergia,
    onTertiary = Tinta,
    error = Alerta,
    background = Fundo,
    onBackground = Tinta,
    surface = Superficie,
    onSurface = Tinta,
    surfaceVariant = Trilho,
    onSurfaceVariant = TintaSuave,
    outline = Contorno,
    outlineVariant = Contorno,
)

private val Escuro = darkColorScheme(
    primary = AzulToIndoEscuro,
    onPrimary = Color(0xFF0B1A4D),
    primaryContainer = NevoaEscuro,
    onPrimaryContainer = Nevoa,
    secondary = FrescorEscuro,
    tertiary = AmbarEnergiaEscuro,
    error = AlertaEscuro,
    background = FundoEscuro,
    onBackground = TintaEscuro,
    surface = SuperficieEscuro,
    onSurface = TintaEscuro,
    surfaceVariant = Color(0xFF1C2638),
    onSurfaceVariant = TintaSuaveEscuro,
    outline = ContornoEscuro,
    outlineVariant = ContornoEscuro,
)

/** Cores de significado fora do esquema M3: energia/custo, economia e presença. */
@Immutable
data class CoresExtras(
    val energiaTexto: Color,
    val energiaFundo: Color,
    val energiaBarra: Color,
    val energiaBarraDestaque: Color,
    val economiaTexto: Color,
    val economiaFundo: Color,
    val economia: Color,
    val presenca: Color,
    val presencaTexto: Color,
)

private val ExtrasClaro = CoresExtras(
    energiaTexto = AmbarTexto, energiaFundo = AmbarFundo, energiaBarra = AmbarClaro, energiaBarraDestaque = AmbarEnergia,
    economiaTexto = EconomiaTexto, economiaFundo = EconomiaFundo, economia = Economia,
    presenca = Frescor, presencaTexto = FrescorTexto,
)

private val ExtrasEscuro = CoresExtras(
    energiaTexto = AmbarEnergiaEscuro, energiaFundo = Color(0xFF3A2A12), energiaBarra = Color(0xFF6B4A1E), energiaBarraDestaque = AmbarEnergiaEscuro,
    economiaTexto = EconomiaEscuro, economiaFundo = Color(0xFF12301F), economia = EconomiaEscuro,
    presenca = FrescorEscuro, presencaTexto = FrescorEscuro,
)

val LocalCoresExtras = staticCompositionLocalOf { ExtrasClaro }

/** Cantos do documento: chips 8, botões e campos 16, cards 24. */
val FormasToIndo = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Espaçamentos em grade de 4 dp. */
object Espaco {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val margem = 20.dp
    val xl = 24.dp
}

@Composable
fun ToIndoTheme(escuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val display = rememberFonteDisplay()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalCoresExtras provides if (escuro) ExtrasEscuro else ExtrasClaro,
    ) {
        MaterialTheme(
            colorScheme = if (escuro) Escuro else Claro,
            typography = tipografiaToIndo(display),
            shapes = FormasToIndo,
            content = content,
        )
    }
}

object ToIndo {
    val extras: CoresExtras
        @Composable get() = LocalCoresExtras.current
}

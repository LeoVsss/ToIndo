package br.com.toindo.ui.theme

import android.annotation.SuppressLint
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.toindo.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

@SuppressLint("DiscouragedApi")
@Composable
fun rememberFonteDisplay(): FontFamily {
    val context = LocalContext.current
    return remember {
        val id = context.resources.getIdentifier("space_grotesk_medium", "font", context.packageName)
        if (id != 0) FontFamily(Font(id, FontWeight.Medium)) else Inter
    }
}

private const val NUMEROS_TABULARES = "tnum"

fun tipografiaToIndo(display: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 64.sp, lineHeight = 72.sp, fontFeatureSettings = NUMEROS_TABULARES),
    displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 56.sp, lineHeight = 60.sp, fontFeatureSettings = NUMEROS_TABULARES),
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 48.sp, fontFeatureSettings = NUMEROS_TABULARES),
    headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp, fontFeatureSettings = NUMEROS_TABULARES),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

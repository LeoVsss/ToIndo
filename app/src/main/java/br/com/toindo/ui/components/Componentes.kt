package br.com.toindo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BreakfastDining
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Microwave
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.toindo.R
import br.com.toindo.model.EstadoPresenca
import br.com.toindo.model.ObjetoInteligente
import br.com.toindo.model.OrigemEvento
import br.com.toindo.model.TipoObjeto
import br.com.toindo.model.Controle
import br.com.toindo.ui.theme.Espaco
import br.com.toindo.ui.theme.ToIndo

/** Ícone Material Symbols Rounded de cada tipo de objeto. */
fun TipoObjeto.icone(): ImageVector = when (this) {
    TipoObjeto.AR_CONDICIONADO -> Icons.Rounded.AcUnit
    TipoObjeto.FORNO -> Icons.Rounded.Microwave
    TipoObjeto.TORRADEIRA -> Icons.Rounded.BreakfastDining
    TipoObjeto.GELADEIRA -> Icons.Rounded.Kitchen
    TipoObjeto.TV -> Icons.Rounded.Tv
    TipoObjeto.LAMPADA -> Icons.Rounded.Lightbulb
    TipoObjeto.TOMADA -> Icons.Rounded.Power
    TipoObjeto.OUTRO -> Icons.Rounded.DevicesOther
}

/** Valor curto mostrado no cartão (ex.: "180°"). */
fun ObjetoInteligente.destaque(): String? = when {
    !ligado -> null
    tipo == TipoObjeto.GELADEIRA -> "sempre ligada"
    parametros[Controle.TEMPERATURA] != null -> "${parametros[Controle.TEMPERATURA]}°"
    parametros[Controle.BRILHO] != null -> "${parametros[Controle.BRILHO]}%"
    else -> null
}

@Composable
fun LogoToIndo(tamanho: Dp = 28.dp) {
    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(tamanho))
}

/** Card branco de 24 dp, base de quase todas as seções. */
@Composable
fun CardToIndo(
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.surface,
    padding: Dp = Espaco.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = cor) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun BotaoPrincipal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) { Text(texto, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun TopoComVoltar(titulo: String?, subtitulo: String? = null, onVoltar: () -> Unit, acao: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onVoltar, modifier = Modifier.padding(start = 0.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
        }
        Column(Modifier.weight(1f)) {
            if (titulo != null) Text(titulo, style = MaterialTheme.typography.titleLarge)
            if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        acao?.invoke()
    }
}

@Composable
fun TituloDeTela(texto: String) {
    Text(texto, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.heightIn(min = 48.dp).padding(top = Espaco.s))
}

@Composable
fun RotuloDeSecao(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Espaco.xs),
    )
}

/** Pílula de presença: estado da automação visível sem toque (princípio 1). */
@Composable
fun PilulaPresenca(estado: EstadoPresenca, automacaoAtiva: Boolean, minutosRestantes: Int? = null) {
    val (texto, cor) = when (estado) {
        EstadoPresenca.EM_CASA -> "Você está em casa" to ToIndo.extras.presenca
        EstadoPresenca.CONFIRMANDO_SAIDA -> "Desligando em ${minutosRestantes ?: "?"} min" to MaterialTheme.colorScheme.tertiary
        EstadoPresenca.FORA -> "Você está fora" to MaterialTheme.colorScheme.onSurfaceVariant
        EstadoPresenca.CHEGANDO -> "Você está chegando" to MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco.s),
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(cor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(cor))
        }
        Text(texto, style = MaterialTheme.typography.titleSmall)
        Text(
            if (automacaoAtiva) "· automação ativa" else "· automação pausada",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Chip Manual / Automático (princípio 2: automação explicável). */
@Composable
fun ChipOrigem(origem: OrigemEvento) {
    val automatico = origem == OrigemEvento.AUTOMATICO
    Text(
        origem.rotulo,
        style = MaterialTheme.typography.labelMedium,
        color = if (automatico) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (automatico) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Rótulo obrigatório em todo valor de kWh e R$ (princípio 3, RN04). */
@Composable
fun SeloEstimado() {
    Text(
        "estimado",
        style = MaterialTheme.typography.labelMedium,
        color = ToIndo.extras.energiaTexto,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(ToIndo.extras.energiaFundo)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Seletor − valor + com alvos de 44 dp. */
@Composable
fun Stepper(valor: String, rotulo: String, onMenos: () -> Unit, onMais: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espaco.xs)) {
        OutlinedIconButton(
            onClick = onMenos,
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Diminuir $rotulo") }
        Text(valor, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedIconButton(
            onClick = onMais,
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) { Icon(Icons.Rounded.Add, contentDescription = "Aumentar $rotulo") }
    }
}

/** Cartão de um objeto na Início. Ligado = fundo Névoa. */
@Composable
fun CartaoObjeto(objeto: ObjetoInteligente, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ligado = objeto.ligado
    val fundo = if (ligado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val tintaSec = if (ligado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier.height(116.dp).semantics {
            contentDescription = "${objeto.nome}, ${if (ligado) "ligado" else "desligado"}, ${objeto.comodo}"
        },
        shape = RoundedCornerShape(20.dp),
        color = fundo,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (ligado) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        objeto.tipo.icone(), contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = if (ligado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                objeto.destaque()?.let {
                    Text(
                        it,
                        style = if (it.endsWith("°") || it.endsWith("%")) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelSmall,
                        color = if (it.endsWith("°") || it.endsWith("%")) MaterialTheme.colorScheme.onSurface else tintaSec,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(objeto.nome, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                "${if (ligado) "Ligado" else "Desligado"} · ${objeto.comodo}",
                style = MaterialTheme.typography.bodySmall, color = tintaSec, maxLines = 1,
            )
        }
    }
}

@Composable
fun CartaoAdicionar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(116.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text("Adicionar objeto", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** Linha de lista dentro de card (Ajustes, regras, consumo por objeto). */
@Composable
fun LinhaDeLista(
    rotulo: String,
    modifier: Modifier = Modifier,
    detalhe: String? = null,
    valor: String? = null,
    corValor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    divisor: Boolean = true,
    onClick: (() -> Unit)? = null,
    fim: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = Espaco.s)) {
                Text(rotulo, style = MaterialTheme.typography.bodyMedium)
                if (detalhe != null) Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (valor != null) Text(valor, style = MaterialTheme.typography.titleSmall, color = corValor)
            fim?.invoke()
            if (onClick != null && fim == null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (divisor) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
    }
}

/** Faixa de aviso (âmbar = atenção, Névoa = informação, verde = ok). */
@Composable
fun Faixa(texto: String, icone: ImageVector, fundo: Color, tinta: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(fundo).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icone, contentDescription = null, tint = tinta, modifier = Modifier.size(20.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = tinta)
    }
}

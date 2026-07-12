package com.jupiter.filemanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * The repeatable raised card used by dashboard and utility screens.
 *
 * It deliberately uses semantic Material colors so the surface stays legible in
 * every user-selected theme/accent while preserving the reference design's soft
 * outline, deep surface and generous curve.
 */
@Composable
fun JupiterCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    JupiterCardContent(
        modifier = modifier,
        contentPadding = contentPadding,
        shape = JupiterDesign.CardShape,
        brush = null,
        border = null,
        content = content,
    )
}

/**
 * Styled-card overload. [shape] is intentionally required: this keeps the original
 * `JupiterCard(modifier, padding, content)` source signature unambiguous for both positional and
 * trailing-lambda callers while still allowing the reference layouts to customize their cards.
 */
@Composable
fun JupiterCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(JupiterDesign.CardPadding),
    shape: Shape,
    brush: Brush? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    JupiterCardContent(
        modifier = modifier,
        contentPadding = contentPadding,
        shape = shape,
        brush = brush,
        border = border,
        content = content,
    )
}

@Composable
private fun JupiterCardContent(
    modifier: Modifier,
    contentPadding: PaddingValues,
    shape: Shape,
    brush: Brush?,
    border: BorderStroke?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedBrush = brush ?: if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        JupiterDesign.PanelBrush
    } else {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        )
    }
    val resolvedBorder = border ?: BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
    )
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.32f),
                spotColor = Color.Black.copy(alpha = 0.42f),
            )
            .clip(shape)
            .background(resolvedBrush)
            .border(resolvedBorder, shape),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/** The visual wordmark used by the supplied references; the app/package remains Jupiter. */
@Composable
fun JupiterWordmark(
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                ),
            ) { append("jupi") }
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                ),
            ) { append("scan") }
        },
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = fontSize * 1.15f,
        letterSpacing = (-0.6).sp,
    )
}

/** A compact coloured icon tile; colour augments but never replaces the icon/label. */
@Composable
fun JupiterIconBadge(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = JupiterDesign.IconBadgeShape,
        color = tint.copy(alpha = 0.16f),
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/**
 * A data-driven circular storage meter. The caller supplies the centre content
 * so no placeholder storage values can accidentally be rendered.
 */
@Composable
fun JupiterStorageRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    strokeWidth: Dp = 12.dp,
    center: @Composable BoxScope.() -> Unit,
) {
    val progress = fraction.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progressBrush = Brush.sweepGradient(
        listOf(JupiterDesign.TealStart, JupiterDesign.TealEnd, JupiterDesign.TealStart),
    )
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2f
                val arcSize = this.size.minDimension - stroke
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = progressBrush,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
        content = center,
    )
}

/** Gradient progress indicator matching the storage/analysis reference surfaces. */
@Composable
fun JupiterProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 7.dp,
) {
    val progress = fraction.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(JupiterDesign.PillShape)
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(height)
                .clip(JupiterDesign.PillShape)
                .background(JupiterDesign.TealBrush),
        )
    }
}

/** A full-width selectable pill that remains accessible as a normal click target. */
@Composable
fun JupiterPill(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(JupiterDesign.PillShape)
            .clickable(onClick = onClick),
        shape = JupiterDesign.PillShape,
        color = if (selected) {
            JupiterDesign.TealStart.copy(alpha = 0.13f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        content = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
                content = content,
            )
        },
    )
}

package com.jupiter.filemanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    Card(
        modifier = modifier,
        shape = JupiterDesign.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        },
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
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
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
                    color = primary,
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
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
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

package com.jupiter.filemanager.feature.browser.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.feature.browser.Breadcrumb

/**
 * A horizontally scrollable breadcrumb trail rendering the navigation path
 * above the file listing.
 *
 * Each [Breadcrumb] is a tappable chip; tapping one invokes [onCrumbClick] with
 * that crumb so the caller can navigate to its path. Separators are drawn
 * between segments. The last segment is highlighted as the current location.
 *
 * Pure UI: performs no IO.
 *
 * @param crumbs ordered trail from the root to the current directory.
 * @param onCrumbClick invoked when a crumb is tapped.
 */
@Composable
fun Breadcrumbs(
    crumbs: List<Breadcrumb>,
    onCrumbClick: (Breadcrumb) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            val isLast = index == crumbs.lastIndex

            CrumbChip(
                crumb = crumb,
                isCurrent = isLast,
                onClick = { onCrumbClick(crumb) },
            )

            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/**
 * A single tappable breadcrumb chip. The current (last) crumb is rendered with
 * an emphasised container colour.
 */
@Composable
private fun CrumbChip(
    crumb: Breadcrumb,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (isCurrent) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
    ) {
        Text(
            text = crumb.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

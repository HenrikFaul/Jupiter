package com.jupiter.filemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Centered, indeterminate progress indicator used while content is loading.
 */
@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        JupiterCard(
            modifier = Modifier.widthIn(max = 320.dp),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp),
            )
        }
    }
}

/**
 * Friendly empty state with an illustrative icon, a title and a supporting message.
 */
@Composable
fun EmptyView(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 32.dp, vertical = 24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        JupiterCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
        ) {
            JupiterIconBadge(
                icon = icon,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = 64.dp,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Error state showing a message and an optional retry action.
 */
@Composable
fun ErrorView(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 32.dp, vertical = 24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        JupiterCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
        ) {
            JupiterIconBadge(
                icon = Icons.Outlined.ErrorOutline,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = 64.dp,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 20.dp),
                ) {
                    Text(text = "Retry")
                }
            }
        }
    }
}

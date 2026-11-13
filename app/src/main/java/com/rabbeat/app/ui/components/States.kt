package com.rabbeat.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rabbeat.app.R
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>
    data object Empty : ScreenState<Nothing>
    data object Failed : ScreenState<Nothing>
    data class Ready<T>(val value: T) : ScreenState<T>
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(modifier.size(84.dp).clip(RoundedCornerShape(22))) {
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { layer ->
            Image(
                painterResource(layer),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                },
            )
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val accent = LocalAccentPalette.current
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = accent.color, modifier = Modifier.size(40.dp))
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = LocalRabBeatPalette.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.MusicOff,
            contentDescription = null,
            tint = palette.tertiaryLabel,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = palette.label,
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action, color = LocalAccentPalette.current.color, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun FailedState(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    val palette = LocalRabBeatPalette.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = palette.tertiaryLabel,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.error_generic),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = palette.label,
        )
        TextButton(onClick = onRetry) {
            Text(
                stringResource(R.string.error_retry),
                color = LocalAccentPalette.current.color,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

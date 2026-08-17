package com.vamshi.field.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vamshi.field.ui.util.youtubeThumbnailUrl

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * Shared card-top thumbnail slot for Test Library and Recommendations cards. Always
 * occupies [height] regardless of whether a video exists, so list items stay uniform
 * height — renders a branded athletic gradient header when [youtubeId] is null.
 */
@Composable
fun TestThumbnail(
    youtubeId: String?,
    testName: String,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 135.dp,
    cornerShape: RoundedCornerShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(cornerShape)
            .then(if (youtubeId != null) Modifier.clickable(onClick = onPlayClick) else Modifier)
    ) {
        if (youtubeId != null) {
            AsyncImage(
                model = youtubeThumbnailUrl(youtubeId),
                contentDescription = "Watch $testName demonstration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Netflix-style subtle dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                        )
                    )
            )

            Surface(
                modifier = Modifier.size(40.dp).align(Alignment.Center),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier.padding(10.dp).align(Alignment.BottomStart),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Text(
                    "Video Guide",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

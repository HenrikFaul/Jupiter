package com.jupiter.filemanager.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Shared visual constants for the dark, layered Jupiter design language.
 *
 * Components must prefer Material semantic colors for actual content and state
 * contrast. These values only define the repeatable geometry and the few
 * semantic-independent accent treatments (such as category icon badges).
 */
object JupiterDesign {
    val CardShape = RoundedCornerShape(24.dp)
    val CompactCardShape = RoundedCornerShape(18.dp)
    val PillShape = RoundedCornerShape(50)
    val IconBadgeShape = RoundedCornerShape(16.dp)
    val FloatingNavShape = RoundedCornerShape(34.dp)

    val CategoryPhoto = Color(0xFF74D943)
    val CategoryVideo = Color(0xFFB170FF)
    val CategoryAudio = Color(0xFFF65AB5)
    val CategoryDocument = Color(0xFF4D9BFF)
    val CategoryArchive = Color(0xFFFFC23D)
    val CategoryApk = Color(0xFF7DDC4D)
    val CategoryDownload = Color(0xFF42A5FF)
    val CategoryOther = Color(0xFF9AA9B5)
    val Safe = Color(0xFF65D96C)
    val Warning = Color(0xFFFFC34D)
}

/** Broad, calm curves shared by Material components that do not use a scoped shape. */
val JupiterShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

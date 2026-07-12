package com.jupiter.filemanager.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shared visual constants for the dark, layered Jupiter design language.
 *
 * Components must prefer Material semantic colors for actual content and state
 * contrast. These values only define the repeatable geometry and the few
 * semantic-independent accent treatments (such as category icon badges).
 */
object JupiterDesign {
    // Geometry measured from the supplied 841 px-wide phone references. Compact
    // panels use a calmer 16–18 dp curve; only hero/navigation surfaces receive
    // the larger radius.
    val CardShape = RoundedCornerShape(18.dp)
    val HeroCardShape = RoundedCornerShape(24.dp)
    val CompactCardShape = RoundedCornerShape(16.dp)
    val PillShape = RoundedCornerShape(50)
    val IconBadgeShape = RoundedCornerShape(14.dp)
    val FloatingNavShape = RoundedCornerShape(36.dp)

    // Shared layout rhythm. Feature screens consume these tokens instead of
    // drifting between unrelated hard-coded paddings.
    val ScreenPadding = 16.dp
    val SectionSpacing = 22.dp
    val CardPadding = 18.dp
    val CompactPadding = 14.dp
    val ControlHeight = 52.dp
    val TouchTarget = 48.dp

    // Art-direction palette used by component-scoped brushes. Material semantic
    // colors remain the source for text/state contrast.
    val Canvas = Color(0xFF080D15)
    val CanvasRaised = Color(0xFF0C131D)
    val PanelTop = Color(0xFF202A34)
    val PanelBottom = Color(0xFF121B25)
    val PanelSoft = Color(0xFF18222D)
    val TealStart = Color(0xFF19B8AA)
    val TealEnd = Color(0xFF65E3D8)
    val Divider = Color(0xFF2A3541)
    val Danger = Color(0xFFFF625A)
    val Purple = Color(0xFFA47CFF)

    val PanelBrush: Brush
        get() = Brush.verticalGradient(listOf(PanelTop, PanelBottom))

    val TealBrush: Brush
        get() = Brush.horizontalGradient(listOf(TealStart, TealEnd))

    val CategoryPhoto = Color(0xFF74D943)
    val CategoryVideo = Color(0xFFB170FF)
    val CategoryAudio = Color(0xFFF65AB5)
    val CategoryDocument = Color(0xFF4D9BFF)
    val CategoryPdf = Color(0xFFFF5C58)
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

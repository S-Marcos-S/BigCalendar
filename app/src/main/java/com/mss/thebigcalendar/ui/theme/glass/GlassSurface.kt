// SPDX-License-Identifier: GPL-3.0-or-later
package com.mss.thebigcalendar.ui.theme.glass

import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mss.thebigcalendar.ui.theme.LocalGlassThemeActive

/**
 * Universal glass container that renders refractive liquid/frosted glass backdrop
 * mapped 1:1 to screen coordinates.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerPx: Float? = null,
    topCornerPx: Float? = null,
    bottomCornerPx: Float? = null,
    cornerFraction: Float = 0.28f,
    tintOpacity: Int = 20,
    style: GlassStyle = GlassStyle.DEFAULT,
    surfaceColor: Int = MaterialTheme.colorScheme.secondaryContainer.toArgb(),
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val (screenWidthPx, screenHeightPx) = remember(context, view, configuration) {
        CalendarGlassProvider.getRealWindowSize(context, view)
    }
    var positionInWindow by remember { mutableStateOf(Offset.Zero) }
    val elementBounds = remember { RectF() }

    LaunchedEffect(isDark) {
        CalendarGlassProvider.init(context, isDark = isDark)
    }
    val wallpaperTick by CalendarGlassProvider.wallpaperTick.collectAsState()

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                positionInWindow = coords.positionInWindow()
            },
        contentAlignment = Alignment.Center,
    ) {
        val tick = wallpaperTick
        Canvas(modifier = Modifier.matchParentSize()) {
            val dummy = tick
            drawIntoCanvas { composeCanvas ->
                val defaultCorner = cornerPx ?: (size.width * cornerFraction)
                val resolvedTopCorner = topCornerPx ?: defaultCorner
                val resolvedBottomCorner = bottomCornerPx ?: defaultCorner
                elementBounds.set(0f, 0f, size.width, size.height)
                CalendarGlassProvider.drawGlassElement(
                    canvas = composeCanvas.nativeCanvas,
                    bounds = elementBounds,
                    positionInWindow = positionInWindow,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx,
                    topCorner = resolvedTopCorner,
                    bottomCorner = resolvedBottomCorner,
                    style = style,
                    surfaceColor = surfaceColor,
                    tintOpacity = tintOpacity,
                    density = density,
                    night = isDark,
                )
            }
        }
        content()
    }
}

/**
 * Universal container for modal dialogs and sheets that automatically
 * applies full refractive liquid glass backdrop when the Glass theme is active.
 */
@Composable
fun GlassDialogContainer(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Dp = 24.dp,
    topCornerDp: Dp = cornerRadiusDp,
    bottomCornerDp: Dp = cornerRadiusDp,
    isGlassActive: Boolean = LocalGlassThemeActive.current,
    style: GlassStyle = GlassStyle.DIALOG,
    tintOpacity: Int? = null,
    surfaceColor: Int = MaterialTheme.colorScheme.surface.toArgb(),
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedTintOpacity = tintOpacity ?: if (isDark) 45 else 55
    val density = LocalDensity.current
    val topPx = with(density) { topCornerDp.toPx() }
    val bottomPx = with(density) { bottomCornerDp.toPx() }
    val shape = RoundedCornerShape(
        topStart = topCornerDp,
        topEnd = topCornerDp,
        bottomEnd = bottomCornerDp,
        bottomStart = bottomCornerDp,
    )

    if (isGlassActive) {
        GlassSurface(
            modifier = modifier.clip(shape),
            topCornerPx = topPx,
            bottomCornerPx = bottomPx,
            style = style,
            tintOpacity = resolvedTintOpacity,
            surfaceColor = surfaceColor,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.clip(shape),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            tonalElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

/**
 * Universal container for cards across all calendar screens.
 * Renders refractive liquid glass when Glass theme is active,
 * or smooth surface with subtle border when standard theme is active.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Dp = 12.dp,
    topCornerDp: Dp = cornerRadiusDp,
    bottomCornerDp: Dp = cornerRadiusDp,
    isGlassActive: Boolean = LocalGlassThemeActive.current,
    style: GlassStyle = GlassStyle.DEFAULT,
    tintOpacity: Int? = null,
    surfaceColor: Int = MaterialTheme.colorScheme.secondaryContainer.toArgb(),
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedTintOpacity = tintOpacity ?: if (isDark) 35 else 48
    val density = LocalDensity.current
    val topPx = with(density) { topCornerDp.toPx() }
    val bottomPx = with(density) { bottomCornerDp.toPx() }
    val shape = RoundedCornerShape(
        topStart = topCornerDp,
        topEnd = topCornerDp,
        bottomEnd = bottomCornerDp,
        bottomStart = bottomCornerDp,
    )

    val baseModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier.clip(shape)
    }

    if (isGlassActive) {
        GlassSurface(
            modifier = baseModifier,
            topCornerPx = topPx,
            bottomCornerPx = bottomPx,
            style = style,
            tintOpacity = resolvedTintOpacity,
            surfaceColor = surfaceColor,
            content = content,
        )
    } else {
        Surface(
            modifier = baseModifier,
            shape = shape,
            color = fallbackColor,
            border = border,
            tonalElevation = 2.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

/**
 * Renders the procedural background canvas behind the application
 * exclusively when the Glass theme is active.
 */
@Composable
fun GlassAppBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    primaryColorHex: String? = null
) {
    if (!LocalGlassThemeActive.current) return
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val (screenWidthPx, screenHeightPx) = remember(context, view, configuration) {
        CalendarGlassProvider.getRealWindowSize(context, view)
    }

    LaunchedEffect(isDark, primaryColorHex) {
        CalendarGlassProvider.init(context, isDark = isDark, primaryColorHex = primaryColorHex)
    }
    val tick by CalendarGlassProvider.wallpaperTick.collectAsState()

    Canvas(modifier = modifier.fillMaxSize()) {
        val dummy = tick
        drawIntoCanvas { composeCanvas ->
            CalendarGlassProvider.drawBackgroundCanvas(
                canvas = composeCanvas.nativeCanvas,
                width = screenWidthPx,
                height = screenHeightPx,
                night = isDark
            )
        }
    }
}

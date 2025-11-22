package com.mss.thebigcalendar.ui.components

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mss.thebigcalendar.R

private data class TutorialItem(
    val key: String,
    @StringRes val titleId: Int,
    @StringRes val descriptionId: Int,
    val coordinates: LayoutCoordinates
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun OnboardingOverlay(
    positions: Map<String, LayoutCoordinates>,
    onFinish: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    val tourOrder = listOf("trash", "today", "charts", "calendar")

    val tutorialItems = remember(positions) {
        tourOrder.mapNotNull { key ->
            positions[key]?.let { layoutCoordinates ->
                val titleId = when (key) {
                    "trash" -> R.string.onboarding_trash_title
                    "today" -> R.string.onboarding_today_title
                    "charts" -> R.string.onboarding_charts_title
                    "calendar" -> R.string.onboarding_calendar_swipe_title
                    else -> null
                }
                val descId = when (key) {
                    "trash" -> R.string.onboarding_trash_desc
                    "today" -> R.string.onboarding_today_desc
                    "charts" -> R.string.onboarding_charts_desc
                    "calendar" -> R.string.onboarding_calendar_swipe_desc
                    else -> null
                }

                if (titleId != null && descId != null) {
                    TutorialItem(
                        key = key,
                        titleId = titleId,
                        descriptionId = descId,
                        coordinates = layoutCoordinates
                    )
                } else {
                    null
                }
            }
        }
    }

    if (tutorialItems.isEmpty() || currentStep >= tutorialItems.size) {
        return
    }

    val currentItem = tutorialItems[currentStep]
    val itemPosition = currentItem.coordinates.positionInWindow()
    val itemSize = currentItem.coordinates.size

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (currentStep < tutorialItems.lastIndex) {
                    currentStep++
                } else {
                    onFinish()
                }
            }
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.8f))

            if (currentItem.key != "calendar") {
                val circleRadius = (itemSize.width.coerceAtLeast(itemSize.height) / 2f) + 16.dp.toPx()
                // Punch a hole
                drawCircle(
                    color = Color.Transparent,
                    center = Offset(itemPosition.x + itemSize.width / 2, itemPosition.y + itemSize.height / 2),
                    radius = circleRadius,
                    blendMode = BlendMode.DstOut
                )
                // Draw the outline
                drawCircle(
                    color = Color.White,
                    style = Stroke(width = 2.dp.toPx()),
                    center = Offset(itemPosition.x + itemSize.width / 2, itemPosition.y + itemSize.height / 2),
                    radius = circleRadius
                )
            } else {
                // Draw arrows for the calendar swipe hint
                val arrowZoneY = itemPosition.y + itemSize.height - 40.dp.toPx() // Bottom part of calendar
                val centerX = itemPosition.x + itemSize.width / 2
                val arrowWidth = 20.dp.toPx()
                val arrowHeight = 10.dp.toPx()
                val spacing = 15.dp.toPx()

                // Up arrow
                val upArrowPath = Path().apply {
                    moveTo(centerX, arrowZoneY - spacing - arrowHeight)
                    lineTo(centerX - arrowWidth / 2, arrowZoneY - spacing)
                    moveTo(centerX, arrowZoneY - spacing - arrowHeight)
                    lineTo(centerX + arrowWidth / 2, arrowZoneY - spacing)
                }
                drawPath(upArrowPath, Color.White, style = Stroke(width = 2.dp.toPx()))

                // Down arrow
                val downArrowPath = Path().apply {
                    moveTo(centerX, arrowZoneY + spacing + arrowHeight)
                    lineTo(centerX - arrowWidth / 2, arrowZoneY + spacing)
                    moveTo(centerX, arrowZoneY + spacing + arrowHeight)
                    lineTo(centerX + arrowWidth / 2, arrowZoneY + spacing)
                }
                drawPath(downArrowPath, Color.White, style = Stroke(width = 2.dp.toPx()))
            }
        }

        val isTopHalf = itemPosition.y + (itemSize.height / 2) < screenHeightPx / 2
        
        val infoBoxWidth = 250.dp
        val infoBoxWidthPx = with(density) { infoBoxWidth.toPx() }
        val horizontalPadding = 16.dp
        val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }

        val desiredX = itemPosition.x + (itemSize.width / 2) - (infoBoxWidthPx / 2)
        val clampedX = desiredX.coerceIn(horizontalPaddingPx, screenWidthPx - infoBoxWidthPx - horizontalPaddingPx)
        
        val xOffset = with(density) { clampedX.toDp() }
        
        val yOffset: Dp = if (currentItem.key == "calendar") {
            // Position text box in the middle of the calendar item vertically, above the arrows
            with(density) { itemPosition.y.toDp() + itemSize.height.toDp() / 2 - 60.dp }
        } else if (isTopHalf) {
            with(density) { (itemPosition.y + itemSize.height).toDp() + 32.dp } // Below item
        } else {
            with(density) { itemPosition.y.toDp() } - 120.dp // Above item
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
        ) {
            TutorialInfoBox(
                modifier = Modifier
                    .width(infoBoxWidth)
                    .offset(x = xOffset, y = yOffset)
            ) {
                Text(
                    text = stringResource(id = currentItem.titleId),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = currentItem.descriptionId),
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center, // Center the skip button
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(id = R.string.onboarding_skip))
                }
            }
        }
    }
}

@Composable
private fun TutorialInfoBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}
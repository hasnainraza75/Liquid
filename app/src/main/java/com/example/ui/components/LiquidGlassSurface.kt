package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalLiquidGlassTokens

/**
 * Full-bleed atmospheric backdrop.
 * - In overlay mode (transparentWindow = true), leaves background transparent so real
 *   Android 12-15 WindowManager FLAG_BLUR_BEHIND blurs the live app underneath.
 * - In main app mode, renders our bundled ambient aurora/silk wallpaper with real GPU
 *   Modifier.blur (RenderEffect on Android 12+) so Liquid Glass refraction is rich and tangible.
 */
@Composable
fun LiquidGlassBackdrop(
    transparentWindow: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlassTokens.current
    Box(modifier = modifier.fillMaxSize()) {
        if (!transparentWindow) {
            val wallpaperRes = if (glass.isDark) {
                R.drawable.img_backdrop_dark
            } else {
                R.drawable.img_backdrop_light
            }
            Image(
                painter = painterResource(id = wallpaperRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(
                        radius = glass.blurRadiusDp.dp,
                        edgeTreatment = BlurredEdgeTreatment.Rectangle
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                glass.scrimTint.copy(alpha = glass.scrimTint.alpha * 0.72f),
                                glass.scrimTint,
                                glass.scrimTint.copy(alpha = glass.scrimTint.alpha * 1.15f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (glass.isDark) {
                            Color(0xFF040914).copy(alpha = 0.44f)
                        } else {
                            Color(0xFFEAF3FC).copy(alpha = 0.36f)
                        }
                    )
            )
        }
        content()
    }
}

/**
 * Refractive Liquid Glass panel container with diagonal top-left specular highlight rim,
 * multi-stop translucent body gradient, and soft top sheen.
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    accentGlow: Color? = null,
    contentPadding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlassTokens.current
    val fillBrush = Brush.linearGradient(
        colors = listOf(
            glass.panelTopFill,
            glass.panelBottomFill
        ),
        start = Offset(0f, 0f),
        end = Offset(600f, 900f)
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            glass.specularBorderTop,
            glass.specularBorderMid,
            glass.specularBorderBottom,
            glass.specularBorderMid
        ),
        start = Offset(0f, 0f),
        end = Offset(700f, 700f)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(fillBrush, shape)
            .then(
                if (accentGlow != null) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentGlow.copy(alpha = if (glass.isDark) 0.24f else 0.16f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.25f, size.height * 0.2f),
                                radius = size.maxDimension * 0.85f
                            )
                        )
                    }
                } else {
                    Modifier
                }
            )
            .border(width = 1.15.dp, brush = borderBrush, shape = shape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Tactile circular Liquid Glass control tile with glowing active core, specular glass rim,
 * spring-bounce press feedback, and accessible 48dp+ touch target.
 */
@Composable
fun LiquidGlassCircleTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    isActive: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isUnavailable: Boolean = false,
    circleSize: Dp = 58.dp
) {
    val glass = LocalLiquidGlassTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tileScale"
    )

    val circleFill by animateColorAsState(
        targetValue = when {
            isUnavailable -> glass.tileInactiveFill.copy(alpha = glass.tileInactiveFill.alpha * 0.55f)
            isActive -> activeColor
            else -> glass.tileInactiveFill
        },
        label = "tileFill"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            isUnavailable -> glass.mutedInk.copy(alpha = 0.55f)
            isActive -> Color.White
            else -> glass.primaryInk
        },
        label = "tileIconTint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 42.dp, color = activeColor),
                role = Role.Button,
                onClick = onClick
            )
            .testTag(testTag)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(circleSize)
                .then(
                    if (isActive && !isUnavailable) {
                        Modifier.drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        activeColor.copy(alpha = 0.45f),
                                        Color.Transparent
                                    ),
                                    radius = size.maxDimension * 0.78f
                                )
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(
                    if (isActive && !isUnavailable) {
                        Brush.linearGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.98f),
                                activeColor.copy(alpha = 0.78f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                circleFill,
                                circleFill.copy(alpha = circleFill.alpha * 0.78f)
                            )
                        )
                    },
                    CircleShape
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (isActive) Color.White.copy(alpha = 0.65f) else glass.specularBorderTop,
                            if (isActive) activeColor.copy(alpha = 0.35f) else glass.specularBorderBottom
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$label: $subtitle",
                tint = iconTint,
                modifier = Modifier.size(25.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isUnavailable) glass.mutedInk else glass.primaryInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive && !isUnavailable) {
                if (glass.isDark) activeColor.copy(alpha = 0.95f) else activeColor
            } else {
                glass.mutedInk
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Compact pill button for header actions and status indicators.
 */
@Composable
fun LiquidGlassPillButton(
    text: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val glass = LocalLiquidGlassTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(
                if (highlighted) {
                    Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.88f), accentColor.copy(alpha = 0.68f))
                    )
                } else {
                    Brush.linearGradient(listOf(glass.panelTopFill, glass.panelBottomFill))
                }
            )
            .border(
                width = 1.dp,
                color = if (highlighted) Color.White.copy(alpha = 0.45f) else glass.specularBorderTop.copy(alpha = 0.65f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                role = Role.Button,
                onClick = onClick
            )
            .testTag(testTag)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (highlighted) Color.White else glass.primaryInk,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) Color.White else glass.primaryInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.example.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.models.ChatWallpaper

@Composable
fun glassContainerColor(
    darkAlpha: Float = 0.26f,
    lightAlpha: Float = 0.44f
): Color {
    val isDark = LocalIsDarkTheme.current
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) darkAlpha else lightAlpha)
}

@Composable
fun glassBarColor(
    darkAlpha: Float = 0.24f,
    lightAlpha: Float = 0.40f
): Color {
    val isDark = LocalIsDarkTheme.current
    return MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) darkAlpha else lightAlpha)
}

@Composable
fun glassModalColor(): Color {
    val isDark = LocalIsDarkTheme.current
    return MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.78f else 0.86f)
}

@Composable
fun glassBorderBrush(accent: Color? = null): Brush {
    val isDark = LocalIsDarkTheme.current
    val highlight = if (isDark) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.82f)
    val mid = (accent ?: MaterialTheme.colorScheme.secondary).copy(alpha = if (isDark) 0.28f else 0.34f)
    val tail = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.22f)
    return Brush.linearGradient(colors = listOf(highlight, mid, tail))
}

@Composable
fun glassBorderStroke(
    accent: Color? = null,
    width: Dp = 1.dp
): BorderStroke = BorderStroke(width, glassBorderBrush(accent))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopAppBarColors(
    accentAlpha: Boolean = false
): TopAppBarColors {
    return TopAppBarDefaults.topAppBarColors(
        containerColor = glassBarColor(
            darkAlpha = if (accentAlpha) 0.20f else 0.26f,
            lightAlpha = if (accentAlpha) 0.34f else 0.42f
        ),
        scrolledContainerColor = glassBarColor(darkAlpha = 0.38f, lightAlpha = 0.56f),
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
        actionIconContentColor = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun glassTextFieldColors(
    focusedBorderColor: Color = MaterialTheme.colorScheme.secondary,
    unfocusedBorderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
): TextFieldColors {
    val isDark = LocalIsDarkTheme.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = focusedBorderColor,
        unfocusedBorderColor = unfocusedBorderColor,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.30f else 0.52f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.20f else 0.38f),
        focusedLabelColor = focusedBorderColor,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = focusedBorderColor
    )
}

@Composable
fun FuturisticBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val violetGlow = if (isDark) NeonViolet.copy(alpha = 0.24f) else LightVioletAccent.copy(alpha = 0.14f)
    val cyanGlow = if (isDark) ElectricCyan.copy(alpha = 0.18f) else LightCyanAccent.copy(alpha = 0.13f)
    val midGlow = if (isDark) NeonVioletBright.copy(alpha = 0.10f) else LightCyanAccent.copy(alpha = 0.08f)
    val shimmer = if (isDark) Color.White.copy(alpha = 0.035f) else Color.White.copy(alpha = 0.28f)
    val base = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(violetGlow, Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.08f),
                        radius = size.maxDimension * 0.62f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(cyanGlow, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.82f),
                        radius = size.maxDimension * 0.58f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(midGlow, Color.Transparent),
                        center = Offset(size.width * 0.72f, size.height * 0.28f),
                        radius = size.maxDimension * 0.42f
                    )
                )
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(shimmer, Color.Transparent, shimmer.copy(alpha = shimmer.alpha * 0.5f)),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )
            }
    ) {
        content()
    }
}

@Composable
fun ChatWallpaperBackground(
    wallpaper: ChatWallpaper,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val base = MaterialTheme.colorScheme.background
    val primaryAccent = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                when (wallpaper) {
                    ChatWallpaper.COSMIC_GRAPHITE -> {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(primaryAccent.copy(alpha = if (isDark) 0.24f else 0.13f), Color.Transparent),
                                center = Offset(size.width * 0.18f, size.height * 0.14f),
                                radius = size.maxDimension * 0.60f
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(secondaryAccent.copy(alpha = if (isDark) 0.18f else 0.11f), Color.Transparent),
                                center = Offset(size.width * 0.86f, size.height * 0.78f),
                                radius = size.maxDimension * 0.52f
                            )
                        )
                    }
                    ChatWallpaper.NEON_AURORA -> {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    primaryAccent.copy(alpha = if (isDark) 0.24f else 0.13f),
                                    Color.Transparent,
                                    secondaryAccent.copy(alpha = if (isDark) 0.22f else 0.13f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height)
                            )
                        )
                    }
                    ChatWallpaper.MIDNIGHT_GRID -> {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(primaryAccent.copy(alpha = if (isDark) 0.12f else 0.08f), Color.Transparent),
                                center = Offset(size.width * 0.5f, size.height * 0.25f),
                                radius = size.maxDimension * 0.55f
                            )
                        )
                        val step = 44.dp.toPx()
                        val lineColor = outlineColor.copy(alpha = if (isDark) 0.22f else 0.26f)
                        var x = 0f
                        while (x < size.width) {
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += step
                        }
                    }
                    ChatWallpaper.MINIMAL_CLEAN -> {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(secondaryAccent.copy(alpha = if (isDark) 0.08f else 0.05f), Color.Transparent),
                                center = Offset(size.width * 0.8f, size.height * 0.2f),
                                radius = size.maxDimension * 0.45f
                            )
                        )
                    }
                }
            }
    ) {
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    containerColor: Color = glassContainerColor(),
    cornerRadius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(cornerRadius)
    val topSheen = if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.45f)
    val borderStroke = if (borderColor != null) {
        BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    if (isDark) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.8f),
                    borderColor,
                    borderColor.copy(alpha = borderColor.alpha * 0.65f)
                )
            )
        )
    } else {
        glassBorderStroke()
    }

    Surface(
        modifier = modifier.shadow(
            elevation = if (isDark) 8.dp else 4.dp,
            shape = shape,
            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            spotColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
        ),
        shape = shape,
        color = containerColor,
        border = borderStroke,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(topSheen, Color.Transparent, Color.Transparent)
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}

@Composable
fun NeonPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    testTag: String? = null
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
            ElectricCyanDim.copy(alpha = 0.88f)
        )
    )
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.42f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
            )
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = glassContainerColor(darkAlpha = 0.24f, lightAlpha = 0.35f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(54.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) gradient else Brush.horizontalGradient(
                        listOf(
                            glassContainerColor(darkAlpha = 0.26f, lightAlpha = 0.42f),
                            glassContainerColor(darkAlpha = 0.26f, lightAlpha = 0.42f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                OrbitalPulseLoader(size = 24.dp)
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NeonOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    testTag: String? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.15.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.28f), accentColor.copy(alpha = 0.72f))
            )
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = accentColor.copy(alpha = 0.10f),
            contentColor = accentColor
        ),
        modifier = modifier
            .height(52.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NeonAvatar(
    photoUrl: String?,
    displayName: String,
    isOnline: Boolean,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val ringBrush = if (isOnline) {
        Brush.sweepGradient(
            listOf(
                MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha),
                MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )
        )
    }

    val initial = displayName.trim().firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, ringBrush, CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(NeonVioletDeep.copy(alpha = 0.85f), ElectricCyanDeep.copy(alpha = 0.85f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = photoUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    error = {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                )
            } else {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.26f).coerceIn(10f, 16f).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(SignalEmerald)
            )
        }
    }
}

@Composable
fun OrbitalPulseLoader(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "orbital_loader")
    val angle1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "angle1"
    )
    val angle2 by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "angle2"
    )
    val c1 = MaterialTheme.colorScheme.secondary
    val c2 = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(size)) {
        val strokeOuter = Stroke(width = size.toPx() * 0.09f, cap = StrokeCap.Round)
        val strokeInner = Stroke(width = size.toPx() * 0.07f, cap = StrokeCap.Round)
        drawArc(
            brush = Brush.sweepGradient(listOf(c1, c2, Color.Transparent)),
            startAngle = angle1,
            sweepAngle = 250f,
            useCenter = false,
            style = strokeOuter
        )
        val inset = size.toPx() * 0.18f
        drawArc(
            color = c2.copy(alpha = 0.85f),
            startAngle = angle2,
            sweepAngle = 190f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                this.size.width - inset * 2,
                this.size.height - inset * 2
            ),
            style = strokeInner
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = dotColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

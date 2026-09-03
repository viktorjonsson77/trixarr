package org.jellyfin.androidtv.ui.cinematic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults

object CinematicColors {
	val Background = Color(0xFF08090C)
	val Surface = Color(0xFF17181D)
	val SurfaceRaised = Color(0xFF22242B)
	val TextPrimary = Color(0xFFF7F7FA)
	val TextSecondary = Color(0xFFB8B9C2)
	val Purple = Color(0xFF9B4DFF)
	val Cyan = Color(0xFF19C7F3)
	val Success = Color(0xFF58C86B)
	val Warning = Color(0xFFF2B84B)
	val Danger = Color(0xFFE45B64)
	val FocusBrush = Brush.linearGradient(listOf(Purple, Cyan))
}

object CinematicDimensions {
	val SafeHorizontal = 32.dp
	val SafeVertical = 22.dp
	val CardCorner = 12.dp
	val DialogCorner = 16.dp
	const val FocusScale = 1.06f
}

@Composable
fun CinematicScaffold(
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	Box(modifier.fillMaxSize().background(CinematicColors.Background)) {
		Box(
			Modifier
				.fillMaxSize()
				.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .18f), Color.Transparent, Color.Black.copy(alpha = .55f))))
		)
		content()
	}
}

@Composable
fun FocusableTvButton(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	content: @Composable RowScope.() -> Unit,
) = Button(
	onClick = onClick,
	modifier = modifier,
	enabled = enabled,
	shape = RoundedCornerShape(10.dp),
	colors = ButtonDefaults.colors(
		containerColor = CinematicColors.Surface.copy(alpha = .88f),
		contentColor = CinematicColors.TextPrimary,
		focusedContainerColor = Color(0xFF352342),
		focusedContentColor = Color.White,
	),
	contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
	content = content,
)

@Composable
fun EmptyState(
	title: String,
	description: String,
	modifier: Modifier = Modifier,
	action: (@Composable () -> Unit)? = null,
) {
	Column(
		modifier = modifier.padding(CinematicDimensions.SafeHorizontal),
		verticalArrangement = Arrangement.spacedBy(10.dp),
		horizontalAlignment = Alignment.Start,
	) {
		Text(title, color = CinematicColors.TextPrimary, fontSize = 24.sp)
		Text(description, color = CinematicColors.TextSecondary, fontSize = 15.sp)
		action?.invoke()
	}
}

@Composable
fun ErrorState(
	title: String,
	description: String,
	onRetry: (() -> Unit)?,
	retryLabel: String,
	modifier: Modifier = Modifier,
) = EmptyState(title, description, modifier) {
	if (onRetry != null) FocusableTvButton(onClick = onRetry) { Text(retryLabel) }
}

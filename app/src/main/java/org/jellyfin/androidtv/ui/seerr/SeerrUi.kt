package org.jellyfin.androidtv.ui.seerr

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.RequestStatus
import org.jellyfin.androidtv.seerr.model.SeerrMedia
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinematic.CinematicColors
import org.jellyfin.androidtv.ui.cinematic.CinematicDimensions

@Composable
fun SeerrMediaCard(media: SeerrMedia, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier, cardWidth: Dp = 122.dp, reserveMetadataSpace: Boolean = false, landscape: Boolean = false) {
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) CinematicDimensions.FocusScale else 1f, label = "SeerrCardFocus")
	val shape = RoundedCornerShape(CinematicDimensions.CardCorner)
	Column(
		modifier.width(cardWidth).scale(scale).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
			.clickable(onClick = onClick).focusable(),
		verticalArrangement = Arrangement.spacedBy(5.dp),
	) {
		Box(Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f).clip(shape).background(CinematicColors.Surface)
			.border(if (focused) 2.dp else 1.dp, if (focused) CinematicColors.Cyan else Color.White.copy(.12f), shape)) {
			AsyncImage(model = if (landscape) media.backdropUrl ?: media.posterUrl else media.posterUrl, contentDescription = media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
			StatusChip(media.availability, Modifier.padding(6.dp))
		}
		Text(media.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
		if (reserveMetadataSpace || media.year != null) Text(media.year?.toString().orEmpty(), color = CinematicColors.TextSecondary, fontSize = 10.sp, maxLines = 1)
	}
}

@Composable
fun StatusChip(availability: MediaAvailability, modifier: Modifier = Modifier) {
	val label = availabilityLabel(availability) ?: return
	val color = when (availability) {
		MediaAvailability.Available, MediaAvailability.InLibrary -> CinematicColors.Success
		MediaAvailability.Requestable -> CinematicColors.Purple
		MediaAvailability.Declined, MediaAvailability.Failed -> CinematicColors.Danger
		else -> CinematicColors.Warning
	}
	Text(label, color = Color.White, fontSize = 9.sp, modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(.9f)).padding(horizontal = 6.dp, vertical = 3.dp))
}

fun availabilityLabel(value: MediaAvailability): String? = when (value) {
	MediaAvailability.InLibrary -> "I biblioteket"
	MediaAvailability.Requestable -> "Kan begäras"
	MediaAvailability.Requested -> "Begärd"
	MediaAvailability.Processing -> "Hämtas"
	MediaAvailability.PartiallyAvailable -> "Delvis tillgänglig"
	MediaAvailability.Available -> "Tillgänglig"
	MediaAvailability.Declined -> "Avslagen"
	MediaAvailability.Failed -> "Misslyckades"
	MediaAvailability.Unknown -> null
}

fun requestStatusLabel(value: RequestStatus): String = when (value) {
	RequestStatus.Requested -> "Begärd"
	RequestStatus.AwaitingApproval -> "Väntar på godkännande"
	RequestStatus.Approved -> "Godkänd"
	RequestStatus.Processing -> "Hämtas"
	RequestStatus.PartiallyAvailable -> "Delvis tillgänglig"
	RequestStatus.Available -> "Tillgänglig"
	RequestStatus.Declined -> "Avslagen"
	RequestStatus.Failed -> "Misslyckades"
	RequestStatus.Unknown -> "Status okänd"
}

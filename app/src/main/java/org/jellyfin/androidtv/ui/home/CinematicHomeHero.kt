package org.jellyfin.androidtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale

data class CinematicHeroMetadata(
	val title: String,
	val subtitle: String?,
	val facts: List<String>,
	val overview: String?,
)

fun BaseItemDto.toCinematicHeroMetadata(): CinematicHeroMetadata? {
	val displayTitle = if (type == BaseItemKind.EPISODE) seriesName ?: name else name
	if (displayTitle.isNullOrBlank()) return null
	val runtimeMinutes = runTimeTicks?.takeIf { it > 0 }?.div(600_000_000L)
	return CinematicHeroMetadata(
		title = displayTitle,
		subtitle = if (type == BaseItemKind.EPISODE) name?.takeUnless { it == displayTitle } else null,
		facts = listOfNotNull(
			productionYear?.toString(),
			runtimeMinutes?.let { "${it / 60} h ${it % 60} min".removePrefix("0 h ") },
			officialRating?.takeIf(String::isNotBlank),
			communityRating?.let { String.format(Locale.ROOT, "★ %.1f", it) },
		),
		overview = overview?.trim()?.takeIf(String::isNotBlank),
	)
}

@Composable
fun CinematicHomeHero(
	item: BaseItemDto?,
	modifier: Modifier = Modifier,
	focusRequester: FocusRequester? = null,
	onPlay: (BaseItemDto) -> Unit = {},
) {
	val metadata = item?.toCinematicHeroMetadata()
	Column(
		modifier = modifier.height(130.dp).padding(vertical = 5.dp).widthIn(max = 330.dp),
		verticalArrangement = Arrangement.Bottom,
	) {
		if (metadata != null) {
			Text(metadata.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
			metadata.subtitle?.let { Text(it, color = Color.White.copy(alpha = .9f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
			if (metadata.facts.isNotEmpty()) {
				Spacer(Modifier.height(2.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
					metadata.facts.forEach { Text(it, color = Color.White.copy(alpha = .86f), fontSize = 10.sp) }
				}
			}
			metadata.overview?.let {
				Spacer(Modifier.height(2.dp))
				Text(it, color = Color.White.copy(alpha = .88f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
			}
			Spacer(Modifier.height(3.dp))
			var playFocused by remember { mutableStateOf(false) }
			val label = stringResource(if ((item.userData?.playbackPositionTicks ?: 0) > 0) R.string.jellyfin_tv_resume else R.string.lbl_play)
			Row(
				modifier = Modifier
					.background(if (playFocused) Color(0xDD432453) else Color(0xCC171717), RoundedCornerShape(8.dp))
					.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
					.onFocusChanged { playFocused = it.isFocused }
					.clickable { onPlay(item) }
					.focusable()
					.padding(horizontal = 10.dp, vertical = 5.dp),
			) { Text("▶  $label", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium) }
		}
	}
}

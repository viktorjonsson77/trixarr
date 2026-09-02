package org.jellyfin.androidtv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun CinematicHomeHero(item: BaseItemDto?, modifier: Modifier = Modifier) {
	val metadata = item?.toCinematicHeroMetadata()
	Column(
		modifier = modifier.height(128.dp).padding(horizontal = 32.dp, vertical = 6.dp).widthIn(max = 620.dp),
		verticalArrangement = Arrangement.Bottom,
	) {
		if (metadata != null) {
			Text(metadata.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
			metadata.subtitle?.let { Text(it, color = Color.White.copy(alpha = .9f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
			if (metadata.facts.isNotEmpty()) {
				Spacer(Modifier.height(4.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
					metadata.facts.forEach { Text(it, color = Color.White.copy(alpha = .86f), fontSize = 15.sp) }
				}
			}
			metadata.overview?.let {
				Spacer(Modifier.height(4.dp))
				Text(it, color = Color.White.copy(alpha = .88f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
			}
		}
	}
}

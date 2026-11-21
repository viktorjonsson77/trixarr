package org.jellyfin.androidtv.status

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable data class StatusBatch(val requests: List<RequestDownload> = emptyList(), val limit: Int = 25, val observedAt: String? = null)
@Serializable data class StatusEta(val estimatedAt: String? = null, val confidence: String = "UNKNOWN", val source: String = "ETA_UNKNOWN")
@Serializable data class DownloadStatus(
    val state: String = "UNKNOWN", val progress: Double? = null, val bytesPerSecond: Double? = null,
    val remainingBytes: Double? = null, val downloadEta: StatusEta = StatusEta(), val availabilityEta: StatusEta = StatusEta(),
    val observedAt: String? = null, val stale: Boolean = false, val jellyfinVerified: Boolean = false,
    val transfers: List<DownloadStatus> = emptyList(),
)
@Serializable data class EpisodeDownload(val seasonNumber: Int, val episodeNumber: Int, val status: DownloadStatus)
@Serializable data class SeasonDownload(val seasonNumber: Int? = null, val availableEpisodes: Int = 0, val totalEpisodes: Int = 0, val state: String = "UNKNOWN")
@Serializable data class RequestDownload(
    val requestId: Int, val mediaType: String, val tmdbId: Int? = null, val tvdbId: Int? = null,
    val status: DownloadStatus = DownloadStatus(), val episodes: List<EpisodeDownload> = emptyList(),
    val seasons: List<SeasonDownload> = emptyList(), val issues: List<String> = emptyList(), val observedAt: String? = null,
)

fun DownloadStatus.isOld(now: Instant): Boolean = stale || observedAt?.let {
    runCatching { val time = Instant.parse(it); time.isAfter(now.plusSeconds(5)) || time.isBefore(now.minusSeconds(45)) }.getOrDefault(true)
} ?: true
fun DownloadStatus.percent(): Int? = progress?.takeIf { it.isFinite() && it in 0.0..1.0 }?.let { (it * 100).toInt() }
fun statusLabel(status: DownloadStatus): String = when(status.state) {
    "REQUESTED" -> "Begärd"; "AWAITING_APPROVAL" -> "Väntar på godkännande"; "APPROVED" -> "Godkänd"
    "WAITING_FOR_RELEASE" -> "Väntar på premiär"; "SEARCHING" -> "Söker"; "QUEUED" -> "I kö"
    "DOWNLOADING" -> "Laddar ner"; "PAUSED" -> "Pausad"; "STALLED" -> "Nedladdningen står stilla"
    "DOWNLOAD_COMPLETE" -> "Nedladdad – väntar på import"; "IMPORTING" -> "Importerar"
    "WAITING_FOR_JELLYFIN" -> "Väntar på Jellyfin"; "PARTIALLY_AVAILABLE" -> "Delvis tillgänglig"
    "AVAILABLE" -> if (status.jellyfinVerified) "Tillgänglig i Jellyfin" else "Tillgänglighet ej verifierad"
    "FAILED" -> "Misslyckades"; "NOT_MONITORED" -> "Inte bevakad"; else -> "Status okänd"
}
fun compactStatus(status: DownloadStatus, now: Instant): String {
    if (status.isOld(now)) return "Äldre status – uppdatering behövs"
    val percent = status.percent()?.takeIf { status.state in setOf("DOWNLOADING", "PAUSED", "STALLED") }
    return statusLabel(status) + (percent?.let { " – $it %" } ?: "")
}
fun etaLabel(eta: StatusEta, status: DownloadStatus, now: Instant, zone: ZoneId): String {
    val time = eta.estimatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    if (status.isOld(now) || eta.confidence !in setOf("LOW", "MEDIUM", "HIGH") || eta.source == "ETA_UNKNOWN" || time == null || time.isBefore(now) || time.isAfter(now.plusSeconds(7 * 86400))) return "Beräknad tid okänd"
    val day = time.atZone(zone).toLocalDate(); val today = now.atZone(zone).toLocalDate()
    val prefix = when(day) { today -> "Idag"; today.plusDays(1) -> "Imorgon"; else -> DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("sv")).format(day) }
    return "$prefix cirka " + DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(time)
}
fun byteLabel(bytes: Double?): String? = bytes?.takeIf { it.isFinite() && it >= 0 }?.let {
    if (it >= 1_000_000_000) String.format(Locale.forLanguageTag("sv"), "%.1f GB", it / 1_000_000_000)
    else String.format(Locale.forLanguageTag("sv"), "%.1f MB", it / 1_000_000)
}

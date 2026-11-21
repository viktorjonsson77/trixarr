package org.jellyfin.androidtv.status

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.ZoneId

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadStatusTests : StringSpec({
    val now = Instant.parse("2026-11-21T22:50:00Z")
    fun status() = DownloadStatus(state="DOWNLOADING",progress=.74,observedAt=now.toString())
    "only secure credential-free bridge endpoints are accepted" {
        secureBridgeUrl("http://private/status") shouldBe null
        secureBridgeUrl("https://user:secret@private/status") shouldBe null
        secureBridgeUrl("https://private/status?key=secret") shouldBe null
        secureBridgeUrl("https://private/#fragment") shouldBe null
        secureBridgeUrl("https://private/status/") shouldBe "https://private/status"
        (runCatching { StatusBridgeClient("http://127.0.0.1",OkHttpClient(),{"synthetic"},{null}) }.exceptionOrNull() is IllegalArgumentException) shouldBe true
    }
    "progress invalid numbers are never rendered" {
        status().percent() shouldBe 74
        status().copy(progress=1.2).percent() shouldBe null
        status().copy(progress=Double.NaN).percent() shouldBe null
        status().copy(progress=-.1).percent() shouldBe null
    }
    "unknown availability estimate is separate from download completion" {
        val s=status().copy(downloadEta=StatusEta(now.plusSeconds(2400).toString(),"MEDIUM","QBITTORRENT"))
        etaLabel(s.availabilityEta,s,now,ZoneId.of("Europe/Stockholm")) shouldBe "Beräknad tid okänd"
        etaLabel(s.downloadEta,s,now,ZoneId.of("Europe/Stockholm")) shouldBe "Imorgon cirka 00:30"
        etaLabel(s.downloadEta,s,now,ZoneId.of("UTC")) shouldBe "Idag cirka 23:30"
    }
    "stale invalid or overlong ETA is unknown" {
        for (eta in listOf(StatusEta("bad","MEDIUM","QBITTORRENT"),StatusEta(now.minusSeconds(1).toString(),"MEDIUM","QBITTORRENT"),StatusEta(now.plusSeconds(10*86400).toString(),"MEDIUM","QBITTORRENT"),StatusEta(now.plusSeconds(20).toString(),"UNKNOWN","QBITTORRENT"))) {
            etaLabel(eta,status(),now,ZoneId.of("UTC")) shouldBe "Beräknad tid okänd"
        }
        val stale=status().copy(observedAt=now.minusSeconds(60).toString())
        compactStatus(stale,now) shouldBe "Äldre status – uppdatering behövs"
        etaLabel(StatusEta(now.plusSeconds(60).toString(),"MEDIUM","QBITTORRENT"),stale,now,ZoneId.of("UTC")) shouldBe "Beräknad tid okänd"
    }
    "availability requires Jellyfin verification" {
        statusLabel(status().copy(state="AVAILABLE",jellyfinVerified=false)) shouldBe "Tillgänglighet ej verifierad"
        statusLabel(status().copy(state="WAITING_FOR_JELLYFIN")) shouldBe "Väntar på Jellyfin"
        statusLabel(status().copy(state="PAUSED")) shouldBe "Pausad"
        statusLabel(status().copy(state="STALLED")) shouldBe "Nedladdningen står stilla"
    }
    "batch parsing preserves independent requests and episode states" {
        val json=Json {ignoreUnknownKeys=true}
        val batch=json.decodeFromString<StatusBatch>("""{"requests":[{"requestId":1,"mediaType":"tv","tmdbId":2,"status":{"state":"PARTIALLY_AVAILABLE"},"seasons":[{"seasonNumber":2,"availableEpisodes":6,"totalEpisodes":8}],"episodes":[{"seasonNumber":2,"episodeNumber":7,"status":{"state":"DOWNLOADING","progress":0.68}},{"seasonNumber":2,"episodeNumber":8,"status":{"state":"WAITING_FOR_RELEASE"}}]},{"requestId":3,"mediaType":"tv","tmdbId":2}]}""")
        batch.requests.size shouldBe 2
        batch.requests[0].status.progress shouldBe null
        batch.requests[0].seasons.single().availableEpisodes shouldBe 6
        batch.requests[0].episodes[0].status.percent() shouldBe 68
        batch.requests[1].requestId shouldBe 3
    }
    "polling cancellation prevents new calls after screen deactivation" {
        runTest {
            var calls=0
            val job=launch {pollStatuses(12_000,{calls++;StatusBatch()},{},{})}
            runCurrent();calls shouldBe 1
            advanceTimeBy(12_001);runCurrent();calls shouldBe 2
            job.cancelAndJoin();advanceTimeBy(60_000);runCurrent();calls shouldBe 2
        }
    }
    "temporary failures preserve polling and never publish invented data" {
        runTest {
            var calls=0;var errors=0;var published=0
            val job=launch {pollStatuses(45_000,{calls++;if(calls==1) error("synthetic") else StatusBatch()},{published++},{errors++})}
            runCurrent();errors shouldBe 1;published shouldBe 0
            advanceTimeBy(45_001);runCurrent();published shouldBe 1
            job.cancelAndJoin()
        }
    }
    "Swedish units use decimal comma" {
        byteLabel(32_400_000.0) shouldBe "32,4 MB"
        byteLabel(Double.POSITIVE_INFINITY) shouldBe null
    }
})

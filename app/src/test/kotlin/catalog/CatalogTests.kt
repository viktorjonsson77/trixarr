package org.jellyfin.androidtv.ui.catalog

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CatalogTests :
    StringSpec({
        fun movie(key: String = "a", id: Int? = 12) =
            CatalogTitle(key, TitleKind.Movie, "Arrival", year = 2016, ids = TitleIds(tmdb = id))
        "remote detail has no local playback DTO and does not crash" {
            val repository = CatalogRepository(io.mockk.mockk(), io.mockk.mockk(), "test")
            repository.dto(movie()) shouldBe null
            repository.dto(movie().copy(ids = TitleIds(local = "missing"))) shouldBe null
        }
        "multiple remote copies and local title become one playable item" {
            val local =
                movie("local")
                    .copy(
                        ids = TitleIds(local = "uuid", tmdb = 12),
                        playbackId = "uuid",
                        status = TitleStatus.Available,
                        positionTicks = 10,
                    )
            val external = movie().copy(poster = "poster", status = TitleStatus.Requestable)
            val merged = mergeTitles(listOf(external, external, local, external))
            merged.size shouldBe 1
            merged.single().key shouldBe "a"
            merged.single().playbackId shouldBe "uuid"
            merged.single().poster shouldBe "poster"
            merged.single().primary shouldBe TitleAction.Resume
        }
        "same ID in different media types does not collapse" {
            mergeTitles(listOf(movie(), movie().copy(kind = TitleKind.Series))).size shouldBe 2
        }
        "authoritative conflicting IDs beat name and year" {
            sameTitle(movie(), movie(id = 13)) shouldBe false
        }
        "name year fallback does not merge remakes" {
            sameTitle(movie(id = null), movie(id = null).copy(year = 2024)) shouldBe false
        }
        "episodes with series metadata are separate playback targets" {
            mergeTitles(
                    listOf(
                        movie("ep1").copy(kind = TitleKind.Episode),
                        movie("ep2").copy(kind = TitleKind.Episode),
                    )
                )
                .size shouldBe 2
        }
        "request action requires both permissions and requestable status" {
            TitleStatus.entries.forEach { status ->
                movie().copy(status = status, requestAllowed = true).primary shouldBe
                    if (status == TitleStatus.Requestable) TitleAction.Request
                    else TitleAction.Information
            }
            movie().copy(status = TitleStatus.Requestable).primary shouldBe TitleAction.Information
        }
        "backend available without playback target never produces play" {
            movie().copy(status = TitleStatus.Available).primary shouldBe TitleAction.Information
        }
        "playback resumes the chosen episode using exact saved milliseconds" {
            val episode =
                movie()
                    .copy(
                        kind = TitleKind.Episode,
                        playbackId = "episode-3",
                        positionTicks = 123456789L,
                    )
            playbackStart(episode) shouldBe PlaybackStart("episode-3", 12345L)
            playbackStart(episode.copy(watched = true)) shouldBe PlaybackStart("episode-3", 0L)
            playbackStart(movie()) shouldBe null
        }
        "watched title restarts instead of resuming stale progress" {
            movie().copy(playbackId = "id", watched = true, positionTicks = 30).primary shouldBe
                TitleAction.Play
        }
        "progress uses real duration and never divides by zero" {
            movie()
                .copy(runtimeTicks = 1_200_000_000, positionTicks = 600_000_000)
                .progress shouldBe .5f
            movie()
                .copy(runtimeTicks = 1_200_000_000, positionTicks = 600_000_000)
                .minutesRemaining shouldBe 1L
            movie().copy(runtimeTicks = 0, positionTicks = 1).progress shouldBe 0f
            movie().copy(positionTicks = 1).minutesRemaining shouldBe null
        }
        "empty rows and recommendations without viewing history are hidden" {
            visibleRows(
                    listOf(
                        CatalogRow("resume", "Fortsätt titta", emptyList()),
                        CatalogRow("latest", "Nytt", listOf(movie())),
                    )
                )
                .map { it.key } shouldBe listOf("latest")
            recommendationSeeds(listOf(movie())).size shouldBe 0
            recommendationSeeds(listOf(movie().copy(watched = true))).size shouldBe 1
        }
        "focus follows identity through reload and nearest index after removal" {
            retainedIndex(listOf("b", "a", "c"), "a", 0) shouldBe 1
            retainedIndex(listOf("b", "c"), "a", 1) shouldBe 1
            retainedIndex(listOf("b"), "a", 8) shouldBe 0
            retainedIndex(emptyList(), "a", 8) shouldBe 0
        }
        "season selection prioritizes actual resumable episode then first unseen" {
            val episodes =
                listOf(
                    movie("1").copy(watched = true),
                    movie("2"),
                    movie("3").copy(positionTicks = 5),
                )
            relevantEpisode(episodes)?.key shouldBe "3"
            relevantEpisode(episodes.take(2))?.key shouldBe "2"
            relevantEpisode(emptyList()) shouldBe null
        }
        "fast search typing is debounced into one backend call" {
            runTest {
                val calls = mutableListOf<String>()
                val values = mutableListOf<String>()
                val search =
                    SearchCoordinator(this) { q: String ->
                        calls += q
                        q
                    }
                search.submit("a", {}, { values += it.getOrThrow() })
                advanceTimeBy(200)
                search.submit("åäö", {}, { values += it.getOrThrow() })
                advanceUntilIdle()
                calls shouldBe listOf("åäö")
                values shouldBe listOf("åäö")
            }
        }
        "late transport response cannot replace newer results" {
            runTest {
                val values = mutableListOf<String>()
                val search =
                    SearchCoordinator(this) { q: String ->
                        withContext(NonCancellable) {
                            delay(if (q == "old") 1000 else 10)
                            q
                        }
                    }
                search.submit("old", {}, { values += it.getOrThrow() })
                advanceTimeBy(351)
                runCurrent()
                search.submit("new", {}, { values += it.getOrThrow() })
                advanceUntilIdle()
                values shouldBe listOf("new")
            }
        }
        "clearing a pending search prevents results appearing again" {
            runTest {
                var count = 0
                val search =
                    SearchCoordinator(this) { _: String ->
                        count++
                        emptyList<String>()
                    }
                search.submit("x", {}, {})
                search.cancel()
                advanceUntilIdle()
                count shouldBe 0
            }
        }
        "search failure is delivered without dropping previously displayed data" {
            runTest {
                var shown = listOf("kept")
                var failed = false
                val search = SearchCoordinator<List<String>>(this) { error("network") }
                search.submit("q", {}, { it.onSuccess { shown = it }.onFailure { failed = true } })
                advanceUntilIdle()
                shown shouldBe listOf("kept")
                failed shouldBe true
            }
        }
    })

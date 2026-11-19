package org.jellyfin.androidtv.ui.catalog

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jellyfin.androidtv.seerr.SeerrMediaMapper

class CatalogMetadataTests :
    StringSpec({
        "series detail type is not confused with media kind" {
            val json =
                Json.parseToJsonElement(
                        """{"id":1,"name":"Real series","type":"Scripted","posterPath":"/real.jpg"}"""
                    )
                    .jsonObject
            val media = SeerrMediaMapper.mapMedia(json)
            media.kind shouldBe org.jellyfin.androidtv.seerr.model.MediaKind.Series
            media.title shouldBe "Real series"
            media.posterUrl shouldBe "/real.jpg"
        }

        "search ignores people and preserves real movie and series identities" {
            val json =
                Json.parseToJsonElement(
                        """{"results":[{"id":1,"mediaType":"person","name":"Actor"},{"id":2,"mediaType":"movie","title":"Movie"},{"id":2,"mediaType":"tv","name":"Series"}]}"""
                    )
                    .jsonObject
            SeerrMediaMapper.mapResults(json).map { it.title } shouldBe listOf("Movie", "Series")
        }
        "null metadata and pictures do not become literal null placeholders" {
            val json =
                Json.parseToJsonElement(
                        """{"id":1,"mediaType":"movie","title":"Movie","posterPath":null,"backdropPath":null,"overview":null,"originalLanguage":null}"""
                    )
                    .jsonObject
            val title = SeerrMediaMapper.mapMedia(json)
            title.posterUrl shouldBe null
            title.backdropUrl shouldBe null
            title.overview shouldBe null
            title.originalLanguage shouldBe null
        }
        "cast character portrait creator and director come from real metadata" {
            val json =
                Json.parseToJsonElement(
                        """{"id":1,"mediaType":"movie","title":"Movie","originalLanguage":"sv","credits":{"cast":[{"name":"Actor","character":"Character","profilePath":"/portrait.jpg"}],"crew":[{"name":"Director","job":"Director"}]},"createdBy":[{"name":"Creator"}]}"""
                    )
                    .jsonObject
            val title = SeerrMediaMapper.mapMedia(json) { path, _ -> "image:$path" }
            title.people.first().role shouldBe "Character"
            title.people.first().image shouldBe "image:/portrait.jpg"
            title.people.map { it.job } shouldBe listOf("Actor", "Director", "Creator")
        }
    })

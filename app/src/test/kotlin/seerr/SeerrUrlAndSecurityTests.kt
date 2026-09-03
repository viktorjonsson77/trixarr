package org.jellyfin.androidtv.seerr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class SeerrUrlAndSecurityTests : StringSpec({
	"normalizes https, trailing slash, and path prefix" {
		SeerrUrl.normalize(" https://example.test/seerr/ ").getOrThrow().toString() shouldBe "https://example.test/seerr/api/v1/"
}
	"adds https when the scheme is omitted" {
		SeerrUrl.normalize("example.test").getOrThrow().scheme shouldBe "https"
	}
	"rejects http unless the current profile explicitly allows it" {
		SeerrUrl.normalize("http://192.0.2.1:5055").isFailure shouldBe true
		SeerrUrl.normalize("http://192.0.2.1:5055", allowInsecureHttp = true).isSuccess shouldBe true
	}
	"rejects embedded credentials and query data" {
		SeerrUrl.normalize("https://user:pass@example.test").isFailure shouldBe true
		SeerrUrl.normalize("https://example.test?token=secret").isFailure shouldBe true
	}
	"redacts secrets and complete query strings" {
		val result = SeerrLogSanitizer.redact("password=hunter2 Cookie=connect.sid.secret https://host/path?token=abc")
		result shouldNotContain "hunter2"
		result shouldNotContain "connect.sid.secret"
		result shouldNotContain "abc"
	}
})

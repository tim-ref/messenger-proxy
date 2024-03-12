package de.akquinet.timref.proxy.extensions

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.UserId

class UserIdExtensionTest : ShouldSpec({

    should("convert matrix user id from @ format to uri format") {
        val atFormat = "@somebody:somewhere"
        val uriFormat = "matrix:u/somebody:somewhere"

        UserId(atFormat).toUriFormat().full shouldBe uriFormat
        UserId(uriFormat).toUriFormat().full shouldBe uriFormat
    }

    should("convert matrix user id from uri format to @ format") {
        val uriFormat = "matrix:u/somebody:somewhere"
        val atFormat = "@somebody:somewhere"

        UserId(uriFormat).toAtFormat().full shouldBe atFormat
        UserId(atFormat).toAtFormat().full shouldBe atFormat
    }
})

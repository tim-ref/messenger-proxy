package de.akquinet.timref.proxy.extensions

import net.folivo.trixnity.core.model.UserId

fun UserId.toUriFormat(): UserId =
    if (this.full.contains(UserId.sigilCharacter)) {
        UserId("matrix:u/${this.localpart}:${this.domain}")
    } else {
        this
    }

fun UserId.toAtFormat(): UserId =
    if (this.full.contains(UserId.sigilCharacter)) {
        this
    } else {
        UserId(this.full.replace("matrix:u/", UserId.sigilCharacter.toString()))
    }

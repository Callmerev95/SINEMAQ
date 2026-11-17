package com.nuvio.app.features.trakt

import com.nuvio.app.features.simkl.SimklPkceCrypto

internal data class TraktPkceMaterial(
    val verifier: String,
    val challenge: String,
)

internal fun generateTraktPkceMaterial(): TraktPkceMaterial {
    val verifier = SimklPkceCrypto.secureRandomBytes(32).base64UrlNoPad()
    require(verifier.length in 43..128) { "PKCE verifier must be 43-128 characters" }
    val challenge = SimklPkceCrypto.sha256(verifier.encodeToByteArray()).base64UrlNoPad()
    return TraktPkceMaterial(verifier = verifier, challenge = challenge)
}

internal fun ByteArray.base64UrlNoPad(): String {
    if (isEmpty()) return ""
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    return buildString((size * 4 + 2) / 3) {
        var index = 0
        while (index < size) {
            val first = this@base64UrlNoPad[index].toInt() and 0xff
            val second = this@base64UrlNoPad.getOrNull(index + 1)?.toInt()?.and(0xff)
            val third = this@base64UrlNoPad.getOrNull(index + 2)?.toInt()?.and(0xff)
            append(alphabet[first ushr 2])
            append(alphabet[((first and 0x03) shl 4) or ((second ?: 0) ushr 4)])
            if (second != null) {
                append(alphabet[((second and 0x0f) shl 2) or ((third ?: 0) ushr 6)])
            }
            if (third != null) {
                append(alphabet[third and 0x3f])
            }
            index += 3
        }
    }
}

package br.com.civilconnection.infrastructure.webhook

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookVerifier(
    secret: String,
) {
    private val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM)

    fun isValid(
        payload: ByteArray,
        signatureHeader: String?,
    ): Boolean {
        val provided = signatureHeader?.removePrefix("sha256=")?.hexToBytesOrNull() ?: return false
        val expected = Mac.getInstance(ALGORITHM).run {
            init(key)
            doFinal(payload)
        }
        return MessageDigest.isEqual(expected, provided)
    }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length != 64 || any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}

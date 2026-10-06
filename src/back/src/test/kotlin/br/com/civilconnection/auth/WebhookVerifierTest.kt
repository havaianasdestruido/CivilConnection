package br.com.civilconnection.auth

import br.com.civilconnection.infrastructure.webhook.WebhookVerifier
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookVerifierTest {
    private val secret = "um-segredo-de-teste-comprido"
    private val verifier = WebhookVerifier(secret)

    @Test
    fun `aceita assinatura hmac valida`() {
        val payload = "{\"type\":\"INSERT\"}".toByteArray()
        assertTrue(verifier.isValid(payload, "sha256=${sign(payload)}"))
    }

    @Test
    fun `rejeita assinatura ausente ou adulterada`() {
        assertFalse(verifier.isValid("{}".toByteArray(), null))
        assertFalse(verifier.isValid("{}".toByteArray(), "sha256=${"00".repeat(32)}"))
    }

    private fun sign(payload: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload).joinToString("") { "%02x".format(it) }
    }
}

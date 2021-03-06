package tech.relaycorp.relaynet.ramf

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.relaycorp.relaynet.CERTIFICATE
import tech.relaycorp.relaynet.issueStubCertificate
import tech.relaycorp.relaynet.messages.payloads.StubEncryptedPayload
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import kotlin.test.assertEquals

internal class EncryptedRAMFMessageTest {
    private val recipientAddress = "04334"

    @Nested
    inner class UnwrapPayload {
        @Test
        fun `SessionlessEnvelopedData payload should be decrypted`() {
            val payload = StubEncryptedPayload("the payload")
            val recipientKeyPair = generateRSAKeyPair()
            val recipientCertificate =
                issueStubCertificate(recipientKeyPair.public, recipientKeyPair.private)
            val message = StubEncryptedRAMFMessage(
                recipientAddress,
                payload.encrypt(recipientCertificate),
                CERTIFICATE
            )

            val plaintextDeserialized = message.unwrapPayload(recipientKeyPair.private)

            assertEquals(payload.payload, plaintextDeserialized.payload)
        }
    }
}

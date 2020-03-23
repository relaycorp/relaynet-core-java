package tech.relaycorp.relaynet.wrappers.x509

import java.math.BigInteger
import java.sql.Date
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair

class CertificateTest {
    private val stubCommonName = "The CommonName"
    private val stubKeyPair = generateRSAKeyPair()

    @Test
    fun `Certificate version should be 3`() {
        val certificate = Certificate.issue(
            stubCommonName,
            stubKeyPair.private,
            stubKeyPair.public
        )

        assertEquals(3, certificate.certificateHolder.versionNumber)
    }

    @Test
    fun `Subject public key should be the specified one`() {
        val validityStartDate = LocalDateTime.now().plusMonths(1)
        val validityEndDate = LocalDateTime.now().plusMonths(2)
        val certificate = Certificate.issue(
            stubCommonName,
            stubKeyPair.private,
            stubKeyPair.public,
            validityStartDate,
            validityEndDate
        )

        assertEquals(
            stubKeyPair.public.encoded.asList(),
            certificate.certificateHolder.subjectPublicKeyInfo.encoded.asList()
        )
    }

    @Test
    fun `Certificate should be signed with issuer private key`() {
        val certificate = Certificate.issue(
            stubCommonName,
            stubKeyPair.private,
            stubKeyPair.public
        )

        assert(
            certificate.certificateHolder.isSignatureValid(JcaContentVerifierProviderBuilder().build(stubKeyPair.public))
        )
    }

    @Test
    fun `Serial number should be autogenerated`() {
        val certificate = Certificate.issue(
            stubCommonName,
            stubKeyPair.private,
            stubKeyPair.public
        )

        assert(certificate.certificateHolder.serialNumber > BigInteger.ZERO)
    }

    @Test
    fun `Validity start date should be set to current time by default`() {
        val certificate = Certificate.issue(
            stubCommonName,
            stubKeyPair.private,
            stubKeyPair.public
        )

        assertEquals(
            Date.valueOf(LocalDate.now()),
            certificate.certificateHolder.notBefore
        )
    }

    // TODO: There shouldn't be any default end date. It must be explicit.
    @Test
    fun testShouldHaveAValidDefaultEndDate() {
        val newCertificate = Certificate.issue(
            stubCommonName,
            stubKeyPair.private,
            stubKeyPair.public
        )

        assertTrue(
            newCertificate.certificateHolder.notAfter > Date.valueOf(LocalDate.now()),
            "Should create a certificate end date after now"
        )
    }

    @Test
    fun `The end date should be later than the start date`() {
        val validityStartDate = LocalDateTime.now().plusMonths(1)

        val exception = assertThrows<CertificateException> {
            Certificate.issue(
                stubCommonName,
                stubKeyPair.private,
                stubKeyPair.public,
                validityStartDate,
                validityStartDate // Same as start date
            )
        }
        assertEquals(
            "The end date must be later than the start date",
            exception.message
        )
    }

    @Nested
    inner class CommonName {
        @Test
        fun `Common name should not be an empty string`() {
            val exception = assertThrows<CertificateException> {
                Certificate.issue(
                    "",
                    stubKeyPair.private,
                    stubKeyPair.public
                )
            }
            assertEquals(
                "CommonName should not be empty",
                exception.message
            )
        }

        @Test
        fun `Specified CN should be used`() {
            val commonName = "The CN"
            val certificate = Certificate.issue(
                commonName,
                stubKeyPair.private,
                stubKeyPair.public
            )

            assertEquals(1, certificate.certificateHolder.subject.rdNs.size)
            assertEquals(false, certificate.certificateHolder.subject.rdNs[0].isMultiValued)
            assertEquals(commonName, certificate.certificateHolder.subject.rdNs[0].first.value.toString())
        }
    }
}

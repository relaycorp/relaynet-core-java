package tech.relaycorp.relaynet.wrappers.x509

import java.math.BigInteger
import java.sql.Date
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.relaycorp.relaynet.sha256
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair

class CertificateTest {
    private val stubSubjectCommonName = "The CommonName"
    private val stubSubjectKeyPair = generateRSAKeyPair()
    private val stubValidityEndDate = LocalDateTime.now().plusMonths(2)

    @Nested
    inner class Issue {
        @Test
        fun `Certificate version should be 3`() {
            val certificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            assertEquals(3, certificate.certificateHolder.versionNumber)
        }

        @Test
        fun `Subject public key should be the specified one`() {
            val certificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            assertEquals(
                stubSubjectKeyPair.public.encoded.asList(),
                certificate.certificateHolder.subjectPublicKeyInfo.encoded.asList()
            )
        }

        @Test
        fun `Certificate should be signed with issuer private key`() {
            val certificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            assert(
                certificate.certificateHolder.isSignatureValid(
                    JcaContentVerifierProviderBuilder().build(
                        stubSubjectKeyPair.public
                    )
                )
            )
        }

        @Test
        fun `Serial number should be autogenerated`() {
            val certificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            assert(certificate.certificateHolder.serialNumber > BigInteger.ZERO)
        }

        @Test
        fun `Validity start date should be set to current time by default`() {
            val certificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            assertEquals(
                Date.valueOf(LocalDate.now()),
                certificate.certificateHolder.notBefore
            )
        }

        @Test
        fun `Validity end date should be honored`() {
            val endDate = LocalDateTime.now().plusDays(1)
            val certificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                endDate
            )

            assertEquals(
                Date.valueOf(endDate.toLocalDate()),
                certificate.certificateHolder.notAfter
            )
        }

        @Test
        fun `The end date should be later than the start date`() {
            val validityStartDate = LocalDateTime.now().plusMonths(1)

            val exception = assertThrows<CertificateException> {
                Certificate.issue(
                    stubSubjectCommonName,
                    stubSubjectKeyPair.public,
                    stubSubjectKeyPair.private,
                    validityStartDate,
                    validityStartDate = validityStartDate // Same as start date
                )
            }
            assertEquals(
                "The end date must be later than the start date",
                exception.message
            )
        }

        @Test
        fun `Subject DN should be set to specified CN`() {
            val commonName = "The CN"
            val certificate = Certificate.issue(
                commonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            assertEquals(1, certificate.certificateHolder.subject.rdNs.size)
            assertEquals(false, certificate.certificateHolder.subject.rdNs[0].isMultiValued)
            assertEquals(
                commonName,
                certificate.certificateHolder.subject.rdNs[0].first.value.toString()
            )
        }

        @Test
        fun `Issuer DN should be same as subject when certificate is self-issued`() {
            val commonName = "The CN"
            val certificate = Certificate.issue(
                commonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            assertEquals(1, certificate.certificateHolder.issuer.rdNs.size)
            assertEquals(false, certificate.certificateHolder.issuer.rdNs[0].isMultiValued)
            assertEquals(
                commonName,
                certificate.certificateHolder.issuer.rdNs[0].first.value.toString()
            )
        }

        @Nested
        inner class IssuerCertificate {
            private val issuerKeyPair = generateRSAKeyPair()

            @Test
            fun `Issuer DN should be set to subject of issuer certificate`() {
                val issuerCommonName = "The issuer"
                val issuerCertificate = Certificate.issue(
                    issuerCommonName,
                    issuerKeyPair.public,
                    issuerKeyPair.private,
                    stubValidityEndDate,
                    isCA = true
                )
                val subjectCertificate = Certificate.issue(
                    stubSubjectCommonName,
                    stubSubjectKeyPair.public,
                    issuerKeyPair.private,
                    stubValidityEndDate,
                    issuerCertificate = issuerCertificate
                )

                assertEquals(1, subjectCertificate.certificateHolder.issuer.rdNs.size)
                assertEquals(
                    false,
                    subjectCertificate.certificateHolder.issuer.rdNs[0].isMultiValued
                )
                assertEquals(
                    issuerCommonName,
                    subjectCertificate.certificateHolder.issuer.rdNs[0].first.value.toString()
                )
            }

            @Test
            fun `Issuer certificate should have basicConstraints extension`() {
                val issuerCommonName = "The issuer"
                val issuerDistinguishedNameBuilder = X500NameBuilder(BCStyle.INSTANCE)
                issuerDistinguishedNameBuilder.addRDN(BCStyle.C, issuerCommonName)

                val builder = X509v3CertificateBuilder(
                    issuerDistinguishedNameBuilder.build(),
                    42.toBigInteger(),
                    Date.valueOf(LocalDateTime.now().toLocalDate()),
                    Date.valueOf(LocalDateTime.now().toLocalDate().plusMonths(1)),
                    issuerDistinguishedNameBuilder.build(),
                    SubjectPublicKeyInfo.getInstance(issuerKeyPair.public.encoded)
                )
                val signatureAlgorithm = DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WithRSAEncryption")
                val digestAlgorithm = DefaultDigestAlgorithmIdentifierFinder().find(signatureAlgorithm)
                val privateKeyParam: AsymmetricKeyParameter = PrivateKeyFactory.createKey(issuerKeyPair.private.encoded)
                val contentSignerBuilder = BcRSAContentSignerBuilder(signatureAlgorithm, digestAlgorithm)
                val signerBuilder = contentSignerBuilder.build(privateKeyParam)
                val issuerCertificate = Certificate(builder.build(signerBuilder))

                val exception = assertThrows<CertificateException> {
                    Certificate.issue(
                        stubSubjectCommonName,
                        stubSubjectKeyPair.public,
                        issuerKeyPair.private,
                        stubValidityEndDate,
                        issuerCertificate = issuerCertificate
                    )
                }

                assertEquals("Issuer certificate should have basic constraints extension", exception.message)
            }

            @Test
            fun `Issuer certificate should be marked as CA`() {
                val issuerCommonName = "The issuer"
                val issuerCertificate = Certificate.issue(
                    issuerCommonName,
                    issuerKeyPair.public,
                    issuerKeyPair.private,
                    stubValidityEndDate,
                    isCA = false
                )
                val exception = assertThrows<CertificateException> {
                    Certificate.issue(
                        stubSubjectCommonName,
                        stubSubjectKeyPair.public,
                        issuerKeyPair.private,
                        stubValidityEndDate,
                        issuerCertificate = issuerCertificate
                    )
                }

                assertEquals("Issuer certificate should be marked as CA", exception.message)
            }
        }

        @Nested
        inner class BasicConstraintsExtension {
            private val extensionOid = "2.5.29.19"

            @Test
            fun `Extension should be included and marked as critical`() {
                val certificate = Certificate.issue(
                    stubSubjectCommonName,
                    stubSubjectKeyPair.public,
                    stubSubjectKeyPair.private,
                    stubValidityEndDate
                )

                assert(certificate.certificateHolder.hasExtensions())
                val extension = certificate.certificateHolder.getExtension(ASN1ObjectIdentifier(extensionOid))
                assert(extension is Extension)
                assert(extension.isCritical)
            }

            @Test
            fun `CA flag should be false by default`() {
                val certificate = Certificate.issue(
                    stubSubjectCommonName,
                    stubSubjectKeyPair.public,
                    stubSubjectKeyPair.private,
                    stubValidityEndDate
                )

                val basicConstraints = BasicConstraints.fromExtensions(certificate.certificateHolder.extensions)
                assertFalse(basicConstraints.isCA)
            }

            @Test
            fun `CA flag should be enabled if requested`() {
                val certificate = Certificate.issue(
                    stubSubjectCommonName,
                    stubSubjectKeyPair.public,
                    stubSubjectKeyPair.private,
                    stubValidityEndDate,
                    isCA = true
                )

                assert(
                    BasicConstraints.fromExtensions(certificate.certificateHolder.extensions).isCA
                )
            }

            @Test
            fun `CA flag should be enabled if pathLenConstraint is greater than 0`() {
                val exception = assertThrows<CertificateException> {
                    Certificate.issue(
                        stubSubjectCommonName,
                        stubSubjectKeyPair.public,
                        stubSubjectKeyPair.private,
                        stubValidityEndDate,
                        isCA = false,
                        pathLenConstraint = 1
                    )
                }

                assertEquals(
                    "Subject should be a CA if pathLenConstraint=1",
                    exception.message
                )
            }

            @Test
            fun `pathLenConstraint should be 0 by default`() {
                val certificate = Certificate.issue(
                    stubSubjectCommonName,
                    stubSubjectKeyPair.public,
                    stubSubjectKeyPair.private,
                    stubValidityEndDate
                )

                val basicConstraints = BasicConstraints.fromExtensions(
                    certificate.certificateHolder.extensions
                )
                assertEquals(
                    0,
                    basicConstraints.pathLenConstraint.toInt()
                )
            }

            @Test
            fun `pathLenConstraint can be set to a custom value of up to 2`() {
                val certificate = Certificate.issue(
                    stubSubjectCommonName,
                    stubSubjectKeyPair.public,
                    stubSubjectKeyPair.private,
                    stubValidityEndDate,
                    isCA = true,
                    pathLenConstraint = 2
                )

                val basicConstraints = BasicConstraints.fromExtensions(
                    certificate.certificateHolder.extensions
                )
                assertEquals(
                    2,
                    basicConstraints.pathLenConstraint.toInt()
                )
            }

            @Test
            fun `pathLenConstraint should not be greater than 2`() {
                val exception = assertThrows<CertificateException> {
                    Certificate.issue(
                        stubSubjectCommonName,
                        stubSubjectKeyPair.public,
                        stubSubjectKeyPair.private,
                        stubValidityEndDate,
                        pathLenConstraint = 3
                    )
                }

                assertEquals("pathLenConstraint should be between 0 and 2 (got 3)", exception.message)
            }

            @Test
            fun `pathLenConstraint should not be negative`() {
                val exception = assertThrows<CertificateException> {
                    Certificate.issue(
                        stubSubjectCommonName,
                        stubSubjectKeyPair.public,
                        stubSubjectKeyPair.private,
                        stubValidityEndDate,
                        pathLenConstraint = -1
                    )
                }

                assertEquals("pathLenConstraint should be between 0 and 2 (got -1)", exception.message)
            }
        }

        @Nested
        inner class AuthorityKeyIdentifierTest {
            @Test
            fun `Value should correspond to subject when self-issued`() {
                val certificate = Certificate.issue(
                    stubSubjectCommonName,
                    stubSubjectKeyPair.public,
                    stubSubjectKeyPair.private,
                    stubValidityEndDate
                )

                val aki = AuthorityKeyIdentifier.fromExtensions(
                    certificate.certificateHolder.extensions
                )
                val subjectPublicKeyInfo = certificate.certificateHolder.subjectPublicKeyInfo
                assertEquals(
                    sha256(subjectPublicKeyInfo.parsePublicKey().encoded).asList(),
                    aki.keyIdentifier.asList()
                )
            }

            @Test
            fun `Value should correspond to issuer when issued by a CA`() {
                val issuerKeyPair = generateRSAKeyPair()
                val issuerCertificate = Certificate.issue(
                    stubSubjectCommonName,
                    issuerKeyPair.public,
                    issuerKeyPair.private,
                    stubValidityEndDate,
                    isCA = true
                )
                val subjectCertificate = Certificate.issue(
                    stubSubjectCommonName,
                    stubSubjectKeyPair.public,
                    stubSubjectKeyPair.private,
                    stubValidityEndDate,
                    issuerCertificate = issuerCertificate
                )

                val issuerPublicKeyInfo = issuerCertificate.certificateHolder.subjectPublicKeyInfo
                val aki = AuthorityKeyIdentifier.fromExtensions(
                    subjectCertificate.certificateHolder.extensions
                )
                assertEquals(
                    sha256(issuerPublicKeyInfo.parsePublicKey().encoded).asList(),
                    aki.keyIdentifier.asList()
                )
            }
        }

        @Test
        fun `Subject Key Identifier extension should be SHA-256 digest of subject key`() {
            val certificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            val ski = SubjectKeyIdentifier.fromExtensions(
                certificate.certificateHolder.extensions
            )
            val subjectPublicKeyInfo = certificate.certificateHolder.subjectPublicKeyInfo
            assertEquals(
                sha256(subjectPublicKeyInfo.parsePublicKey().encoded).asList(),
                ski.keyIdentifier.asList()
            )
        }
    }

    @Nested
    inner class Serialize {
        @Test
        fun `Output should be DER-encoded`() {
            val certificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            val certificateSerialized = certificate.serialize()

            val certificateHolderDeserialized = X509CertificateHolder(certificateSerialized)
            assertEquals(certificate.certificateHolder, certificateHolderDeserialized)
        }
    }

    @Nested
    inner class Deserialize {
        @Test
        fun `Valid certificates should be parsed`() {
            val certificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )
            val certificateSerialized = certificate.serialize()

            val certificateDeserialized = Certificate.deserialize(certificateSerialized)

            assertEquals(certificate, certificateDeserialized)
        }

        @Test
        fun `Invalid certificates should result in errors`() {
            val exception = assertThrows<CertificateException> {
                Certificate.deserialize("Not a certificate".toByteArray())
            }

            assertEquals("Value should be a DER-encoded, X.509 v3 certificate", exception.message)
        }
    }

    @Nested
    inner class Equals {
        private val stubCertificate = Certificate.issue(
            stubSubjectCommonName,
            stubSubjectKeyPair.public,
            stubSubjectKeyPair.private,
            stubValidityEndDate
        )

        @Suppress("ReplaceCallWithBinaryOperator")
        @Test
        fun `A non-Certificate object should not equal`() {
            assertFalse(stubCertificate.equals("Hey"))
        }

        @Test
        fun `A different certificate should not equal`() {
            val anotherKeyPair = generateRSAKeyPair()
            val anotherCertificate = Certificate.issue(
                stubSubjectCommonName,
                anotherKeyPair.public,
                anotherKeyPair.private,
                stubValidityEndDate
            )
            assertNotEquals(anotherCertificate, stubCertificate)
        }

        @Test
        fun `An equivalent certificate should equal`() {
            val sameCertificate = Certificate(stubCertificate.certificateHolder)
            assertEquals(stubCertificate, sameCertificate)
        }
    }

    @Nested
    inner class HashCode {
        @Test
        fun `Hashcode should be that of certificate holder`() {
            val stubCertificate = Certificate.issue(
                stubSubjectCommonName,
                stubSubjectKeyPair.public,
                stubSubjectKeyPair.private,
                stubValidityEndDate
            )

            assertEquals(stubCertificate.certificateHolder.hashCode(), stubCertificate.hashCode())
        }
    }
}

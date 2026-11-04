package dev.pluto.template

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource

/** Keys made for the test run only; nothing here is a secret and nothing is written to disk. */
object TestTokens {
    const val ISSUER = "https://issuer.test"
    const val AUDIENCE = "pluto-bff"

    private fun rsa(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun ec(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    val rsaKey: KeyPair = rsa()
    val otherRsaKey: KeyPair = rsa()
    val ecKey: KeyPair = ec()

    fun pem(pair: KeyPair): String =
        "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.public.encoded) +
            "\n-----END PUBLIC KEY-----"

    private fun claims(issuer: String, audience: String, expires: Instant?, subject: String = "demo-user"): JWTClaimsSet =
        JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(subject).issueTime(Date.from(Instant.now().minusSeconds(5)))
            .also { if (expires != null) it.expirationTime(Date.from(expires)) }.build()

    fun valid(expiresInSeconds: Long = 300, key: KeyPair = rsaKey, issuer: String = ISSUER, audience: String = AUDIENCE): String =
        sign(claims(issuer, audience, Instant.now().plusSeconds(expiresInSeconds)), key)

    fun sign(claims: JWTClaimsSet, key: KeyPair): String =
        if (key.private is ECPrivateKey) {
            SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(), claims).also { it.sign(ECDSASigner(key.private as ECPrivateKey)) }.serialize()
        } else {
            SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims).also { it.sign(RSASSASigner(key.private as RSAPrivateKey)) }.serialize()
        }

    fun withoutExpiry(): String = sign(claims(ISSUER, AUDIENCE, null), rsaKey)

    fun unsigned(): String = PlainJWT(claims(ISSUER, AUDIENCE, Instant.now().plusSeconds(300))).serialize()

    fun hs256(): String =
        SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims(ISSUER, AUDIENCE, Instant.now().plusSeconds(300)))
            .also { it.sign(MACSigner(ByteArray(32) { 7 })) }.serialize()

    fun bearer(token: String = valid()): String = "Bearer $token"
}

/** Registered through META-INF/spring.factories: every application context of the tests trusts the test key. */
class TestTokenSettings : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(context: ConfigurableApplicationContext) {
        context.environment.propertySources.addFirst(
            MapPropertySource(
                "testTokens",
                mapOf(
                    "pluto.security.jwt.public-key" to TestTokens.pem(TestTokens.rsaKey),
                    "pluto.security.jwt.issuer" to TestTokens.ISSUER,
                    "pluto.security.jwt.audience" to TestTokens.AUDIENCE,
                ),
            ),
        )
    }
}

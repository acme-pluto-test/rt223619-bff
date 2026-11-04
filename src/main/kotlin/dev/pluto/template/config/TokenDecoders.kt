package dev.pluto.template.config

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.net.URI
import java.security.Key
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.time.Duration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

/** Builds the decoder of door 1: RS256 or ES256 only, no clock skew, a token without `exp` is refused. */
object TokenDecoders {
    private val allowed = setOf(JWSAlgorithm.RS256, JWSAlgorithm.ES256)

    fun build(settings: JwtSettings): JwtDecoder {
        val s = settings.validated()
        val processor = DefaultJWTProcessor<SecurityContext>()
        processor.jwsKeySelector = if (s.publicKey.isNotBlank()) PinnedKey(parse(s.publicKey)) else FromKeySet(keySet(s.jwkSetUri))
        // Nimbus would accept 60 seconds of skew by itself; the validators below decide, with none.
        processor.jwtClaimsSetVerifier = com.nimbusds.jwt.proc.JWTClaimsSetVerifier { _, _ -> }
        val decoder = NimbusJwtDecoder(processor)
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                RequireExpiry,
                JwtTimestampValidator(Duration.ZERO),
                JwtIssuerValidator(s.issuer),
                RequireAudience(s.audience),
            ),
        )
        return decoder
    }

    private fun parse(pem: String): JWK {
        val text = pem.replace("\\n", "\n").trim()
        check(!text.contains("PRIVATE KEY")) { "pluto.security.jwt.public-key must be a public key" }
        val body = text.lineSequence().filterNot { it.startsWith("-----") }.joinToString("").filterNot { it.isWhitespace() }
        val spec = try {
            X509EncodedKeySpec(Base64.getDecoder().decode(body))
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("pluto.security.jwt.public-key is not a PEM public key")
        }
        runCatching { KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey }.getOrNull()?.let { return RSAKey.Builder(it).build() }
        val ecKey = runCatching { KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey }.getOrNull()
        check(ecKey != null) { "pluto.security.jwt.public-key must be an RSA or EC P-256 public key" }
        check(Curve.forECParameterSpec(ecKey.params) == Curve.P_256) { "pluto.security.jwt.public-key must be RSA or EC P-256" }
        return ECKey.Builder(Curve.P_256, ecKey).build()
    }

    private fun keySet(uri: String): JWKSource<SecurityContext> =
        JWKSourceBuilder.create<SecurityContext>(URI(uri).toURL()).build()

    /** The one configured key, whatever `kid` the token carries (a token source with a key set uses `kid`). */
    private class PinnedKey(private val jwk: JWK) : JWSKeySelector<SecurityContext> {
        override fun selectJWSKeys(header: JWSHeader, context: SecurityContext?): List<Key> =
            when {
                header.algorithm == JWSAlgorithm.RS256 && jwk is RSAKey -> listOf(jwk.toRSAPublicKey())
                header.algorithm == JWSAlgorithm.ES256 && jwk is ECKey -> listOf(jwk.toECPublicKey())
                else -> emptyList()
            }
    }

    private class FromKeySet(private val source: JWKSource<SecurityContext>) : JWSKeySelector<SecurityContext> {
        override fun selectJWSKeys(header: JWSHeader, context: SecurityContext?): List<Key> =
            if (header.algorithm in allowed) JWSVerificationKeySelector<SecurityContext>(header.algorithm, source).selectJWSKeys(header, context) else emptyList()
    }

    private object RequireExpiry : OAuth2TokenValidator<Jwt> {
        override fun validate(token: Jwt): OAuth2TokenValidatorResult =
            if (token.expiresAt != null) OAuth2TokenValidatorResult.success()
            else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "no expiry", null))
    }

    private class RequireAudience(private val audience: String) : OAuth2TokenValidator<Jwt> {
        override fun validate(token: Jwt): OAuth2TokenValidatorResult =
            if (token.audience?.contains(audience) == true) OAuth2TokenValidatorResult.success()
            else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "audience", null))
    }
}

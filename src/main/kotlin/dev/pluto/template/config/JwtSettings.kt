package dev.pluto.template.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Door 1 of reference-scenario. The token a caller sends is checked against exactly one key source (a public key or a
 * key-set address), an issuer and an audience; none has a default, so a service started without them stops.
 */
@ConfigurationProperties("pluto.security.jwt")
data class JwtSettings(
    val publicKey: String = "",
    val jwkSetUri: String = "",
    val issuer: String = "",
    val audience: String = "",
) {
    fun validated(): JwtSettings {
        val hasKey = publicKey.isNotBlank()
        val hasUri = jwkSetUri.isNotBlank()
        check(hasKey || hasUri) { "pluto.security.jwt.public-key or pluto.security.jwt.jwk-set-uri is required" }
        check(!(hasKey && hasUri)) { "pluto.security.jwt.public-key and pluto.security.jwt.jwk-set-uri are both set; set exactly one" }
        check(issuer.isNotBlank()) { "pluto.security.jwt.issuer is required" }
        check(audience.isNotBlank()) { "pluto.security.jwt.audience is required" }
        return this
    }
}

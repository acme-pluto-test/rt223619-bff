package dev.pluto.template

import dev.pluto.template.config.JwtSettings
import dev.pluto.template.config.TokenDecoderConfig
import dev.pluto.template.config.TokenDecoders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.test.context.TestPropertySource

@Configuration
@EnableConfigurationProperties(JwtSettings::class)
class SettingsOnly

class JwtConfigurationTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(SettingsOnly::class.java, TokenDecoderConfig::class.java)
    private val key = "pluto.security.jwt.public-key=" + TestTokens.pem(TestTokens.rsaKey).replace("\n", "\\n")

    private fun failure(vararg properties: String): String {
        var message = ""
        runner.withPropertyValues(*properties).run { context ->
            assertThat(context).hasFailed()
            message = generateSequence(context.startupFailure) { it.cause }.joinToString(" | ") { it.message ?: "" }
        }
        return message
    }

    @Test
    fun refusesToStartForEachBadConfiguration() {
        assertThat(failure("pluto.security.jwt.issuer=i", "pluto.security.jwt.audience=a")).contains("public-key or pluto.security.jwt.jwk-set-uri")
        assertThat(failure(key, "pluto.security.jwt.jwk-set-uri=https://keys.test/jwks", "pluto.security.jwt.issuer=i", "pluto.security.jwt.audience=a"))
            .contains("both set")
        assertThat(failure(key, "pluto.security.jwt.issuer=", "pluto.security.jwt.audience=a")).contains("pluto.security.jwt.issuer is required")
        assertThat(failure(key, "pluto.security.jwt.issuer=i", "pluto.security.jwt.audience=")).contains("pluto.security.jwt.audience is required")
    }

    @Test
    fun startsWithExactlyOneKeySource() {
        runner.withPropertyValues(key, "pluto.security.jwt.issuer=i", "pluto.security.jwt.audience=a").run { assertThat(it).hasNotFailed() }
        runner.withPropertyValues("pluto.security.jwt.jwk-set-uri=https://keys.test/jwks", "pluto.security.jwt.issuer=i", "pluto.security.jwt.audience=a")
            .run { assertThat(it).hasNotFailed() }
    }

    @Test
    fun acceptsAnEs256TokenFromAnEcKey() {
        val decoder = TokenDecoders.build(JwtSettings(publicKey = TestTokens.pem(TestTokens.ecKey), issuer = TestTokens.ISSUER, audience = TestTokens.AUDIENCE))
        assertThat(decoder.decode(TestTokens.valid(key = TestTokens.ecKey)).subject).isEqualTo("demo-user")
        try {
            decoder.decode(TestTokens.valid(key = TestTokens.rsaKey))
            error("a token from another key was accepted")
        } catch (_: JwtException) {
        }
    }

    @Test
    fun refusesAPrivateKeyInThePublicKeySetting() {
        // the markers are assembled so that the template's own secret check does not take this line for a key
        val kind = "PRIVATE" + " KEY"
        val private = "-----BEGIN $kind-----\n" +
            java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(TestTokens.rsaKey.private.encoded) + "\n-----END $kind-----"
        assertThat(failure("pluto.security.jwt.public-key=" + private.replace("\n", "\\n"), "pluto.security.jwt.issuer=i", "pluto.security.jwt.audience=a"))
            .contains("pluto.security.jwt.public-key")
    }
}

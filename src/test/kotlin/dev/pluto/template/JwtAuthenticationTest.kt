package dev.pluto.template

import com.nimbusds.jwt.JWTClaimsSet
import java.time.Instant
import java.util.Date
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Door 1 of reference-scenario, end to end through the filter chain. These tests build their own requests. */
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationTest(@Autowired private val mvc: MockMvc) {
    private fun greet(authorization: String?) = mvc.get("/v1/greetings/ana") { if (authorization != null) header("Authorization", authorization) }

    @Test
    fun acceptsAValidToken() {
        greet(TestTokens.bearer()).andExpect {
            status { isOk() }
            jsonPath("$.message") { value("Hello, ana") }
        }
    }

    @Test
    fun acceptsATokenThatExpiresInThirtySeconds() {
        greet(TestTokens.bearer(TestTokens.valid(expiresInSeconds = 30))).andExpect { status { isOk() } }
    }

    @Test
    fun answers401WithTheErrorShapeForEachMalformedCall() {
        for (header in listOf(null, "Basic abc", "Bearer abc", "Bearer")) {
            greet(header).andExpect {
                status { isUnauthorized() }
                header { string("WWW-Authenticate", "Bearer") }
                jsonPath("$.code") { value("unauthorized") }
            }
        }
    }

    @Test
    fun rejectsEachKindOfInvalidTokenWithoutRunningTheHandler() {
        val now = Instant.now()
        fun claims(issuer: String = TestTokens.ISSUER, audience: String = TestTokens.AUDIENCE, expires: Instant) =
            JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("demo-user").expirationTime(Date.from(expires)).build()
        val kinds = mapOf(
            "expired one second ago" to TestTokens.sign(claims(expires = now.minusSeconds(1)), TestTokens.rsaKey),
            "another key" to TestTokens.sign(claims(expires = now.plusSeconds(300)), TestTokens.otherRsaKey),
            "another issuer" to TestTokens.sign(claims(issuer = "https://evil.test", expires = now.plusSeconds(300)), TestTokens.rsaKey),
            "another audience" to TestTokens.sign(claims(audience = "other", expires = now.plusSeconds(300)), TestTokens.rsaKey),
            "algorithm none" to TestTokens.unsigned(),
            "algorithm HS256" to TestTokens.hs256(),
            "no expiry" to TestTokens.withoutExpiry(),
        )
        for ((kind, token) in kinds) {
            greet(TestTokens.bearer(token)).andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("unauthorized") }
            }.also { check(true) { kind } }
        }
    }

    @Test
    fun answers400WithTheErrorShapeForAPathTheFirewallRefuses() {
        for (path in listOf("/v1/greetings/a%2fb", "/v1/greetings/a;b")) {
            mvc.get(path) { header("Authorization", TestTokens.bearer()) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("invalid_request") }
            }
        }
    }

    @Test
    fun healthNeedsNoToken() {
        mvc.get("/actuator/health").andExpect { status { isOk() } }
    }
}

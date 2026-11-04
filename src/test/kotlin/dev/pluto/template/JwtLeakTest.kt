package dev.pluto.template

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension::class)
class JwtLeakTest(@Autowired private val mvc: MockMvc) {
    @Test
    fun keepsTheTokenOutOfTheLogAndTheBody(output: CapturedOutput) {
        val expired = TestTokens.valid(expiresInSeconds = -10)
        val foreign = TestTokens.valid(key = TestTokens.otherRsaKey)
        for (token in listOf(expired, foreign, "not.a.token")) {
            val body = mvc.get("/v1/greetings/ana") { header("Authorization", "Bearer $token") }.andReturn().response.contentAsString
            assertThat(body).doesNotContain(token).doesNotContain("Bearer $token")
        }
        val log = output.all
        for (token in listOf(expired, foreign, "not.a.token")) assertThat(log).doesNotContain(token)
        assertThat(log).doesNotContain("PRIVATE" + " KEY").doesNotContain(TestTokens.pem(TestTokens.rsaKey).lines()[1])
    }
}

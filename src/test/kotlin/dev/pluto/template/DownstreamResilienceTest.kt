package dev.pluto.template

import com.sun.net.httpserver.HttpServer
import dev.pluto.template.config.TranslatorProperties
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.core.io.ClassPathResource
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** A fake translator whose behaviour each test sets; the application talks to it over real HTTP. */
object FakeTranslator {
    val hits = AtomicInteger()

    @Volatile var delayMillis = 0L

    @Volatile var statuses: List<Int> = listOf(200)

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/translate") { exchange ->
            val n = hits.getAndIncrement()
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val status = statuses[minOf(n, statuses.size - 1)]
            val body = if (status == 200) """{"text":"Ola, ana"}""" else """{"error":"down"}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        start()
    }

    fun reset() {
        hits.set(0)
        delayMillis = 0
        statuses = listOf(200)
    }
}

@SpringBootTest(properties = ["downstream.translator.timeout=1000ms", "downstream.translator.retry-wait=5ms"])
@AutoConfigureMockMvc
@Import(AuthenticatedMvc::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DownstreamResilienceTest(@Autowired private val mvc: MockMvc, @Autowired private val circuitBreaker: CircuitBreaker) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun translator(registry: DynamicPropertyRegistry) {
            registry.add("downstream.translator.base-url") { "http://127.0.0.1:${FakeTranslator.server.address.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stop() = FakeTranslator.server.stop(0)
    }

    private fun translate() = mvc.get("/v1/greetings/ana/translations/pt")

    @Test
    fun answers504WhenTheDownstreamIsSlowerThanTwoSeconds() {
        // the configured default is two seconds; the test shortens it to keep the boundary measurable
        val yaml = YamlPropertySourceLoader().load("application", ClassPathResource("application.yaml")).first()
        assertEquals("2000ms", yaml.getProperty("downstream.translator.timeout"))
        assertEquals(Duration.ofMillis(2000), TranslatorProperties(baseUrl = "http://x").timeout)

        FakeTranslator.reset()
        translate().andExpect { status { isOk() } } // warms the context and the connection up before time matters
        FakeTranslator.reset()
        FakeTranslator.delayMillis = 100
        translate().andExpect { status { isOk() }; jsonPath("$.message") { value("Ola, ana") } }

        FakeTranslator.reset()
        FakeTranslator.delayMillis = 1800
        translate().andExpect { status { isGatewayTimeout() }; jsonPath("$.code") { value("downstream_timeout") } }
        assertEquals(1, FakeTranslator.hits.get(), "a timeout is not retried")
    }

    @Test
    fun retriesTwiceOnServiceUnavailableThenAnswers502() {
        FakeTranslator.reset()
        FakeTranslator.statuses = listOf(503)
        translate().andExpect { status { isBadGateway() }; jsonPath("$.code") { value("downstream_unavailable") } }
        assertEquals(3, FakeTranslator.hits.get(), "the call and two retries")

        // a call that exhausts its retries counts once for the circuit: after four of them (twelve attempts, more
        // than the ten the window holds) a fifth still reaches the downstream, which a retry wrapping the breaker would not allow
        circuitBreaker.reset()
        FakeTranslator.reset()
        FakeTranslator.statuses = listOf(503)
        repeat(4) { translate().andExpect { status { isBadGateway() } } }
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        FakeTranslator.hits.set(0)
        translate().andExpect { status { isBadGateway() } }
        assertEquals(3, FakeTranslator.hits.get(), "the fifth call still reached the downstream")

        FakeTranslator.reset()
        FakeTranslator.statuses = listOf(503, 200)
        translate().andExpect { status { isOk() } }
        assertEquals(2, FakeTranslator.hits.get(), "a retry that succeeds ends the call")

        FakeTranslator.reset()
        FakeTranslator.statuses = listOf(500)
        translate().andExpect { status { isBadGateway() } }
        assertEquals(1, FakeTranslator.hits.get(), "only 503 is retried")
    }

    @Test
    fun opensTheCircuitAtFiveFailuresInTen() {
        // 4 failures in the last 10 calls keep the circuit closed
        FakeTranslator.reset()
        FakeTranslator.statuses = List(6) { 200 } + List(4) { 500 }
        repeat(10) { translate() }
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        FakeTranslator.statuses = listOf(200)
        FakeTranslator.hits.set(0)
        translate().andExpect { status { isOk() } }
        assertEquals(1, FakeTranslator.hits.get())

        // 5 failures in the last 10 open it, and an open circuit does not reach the downstream
        circuitBreaker.reset()
        FakeTranslator.reset()
        FakeTranslator.statuses = List(5) { 200 } + List(5) { 500 }
        repeat(10) { translate() }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
        FakeTranslator.statuses = listOf(200)
        FakeTranslator.hits.set(0)
        translate().andExpect { status { isBadGateway() }; jsonPath("$.code") { value("downstream_unavailable") } }
        assertEquals(0, FakeTranslator.hits.get(), "an open circuit does not reach the downstream")
    }
}

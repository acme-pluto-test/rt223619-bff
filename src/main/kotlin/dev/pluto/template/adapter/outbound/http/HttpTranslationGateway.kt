package dev.pluto.template.adapter.outbound.http

import dev.pluto.template.application.TranslationGateway
import dev.pluto.template.application.TranslationResult
import dev.pluto.template.domain.Greeting
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/** What went wrong on a call to the downstream service; the retry and the circuit breaker are keyed on these. */
sealed class DownstreamException(cause: Throwable?) : RuntimeException(cause) {
    class Timeout(cause: Throwable?) : DownstreamException(cause)

    class ServiceUnavailable(cause: Throwable?) : DownstreamException(cause)

    class Failed(cause: Throwable?) : DownstreamException(cause)
}

data class TranslatorResponse(val text: String)

/**
 * The call is bounded three ways: the client's read timeout, a retry only on `503`, and a circuit breaker outside the
 * retry, so a call that exhausts its retries counts once.
 */
class HttpTranslationGateway(
    private val client: RestClient,
    private val retry: Retry,
    private val circuitBreaker: CircuitBreaker,
) : TranslationGateway {
    override fun translate(greeting: Greeting, lang: String): TranslationResult {
        // the circuit breaker wraps the retried call, so a call that exhausts its retries counts once
        val retried = Retry.decorateSupplier(retry) { fetch(greeting, lang) }
        val call = CircuitBreaker.decorateSupplier(circuitBreaker, retried)
        return try {
            TranslationResult.Translated(call.get())
        } catch (e: CallNotPermittedException) {
            TranslationResult.Unavailable
        } catch (e: DownstreamException.Timeout) {
            TranslationResult.TimedOut
        } catch (e: DownstreamException) {
            TranslationResult.Unavailable
        }
    }

    private fun fetch(greeting: Greeting, lang: String): Greeting =
        try {
            val body = client.get()
                .uri { it.path("/translate").queryParam("text", greeting.message).queryParam("lang", lang).build() }
                .retrieve()
                .body(TranslatorResponse::class.java)
            Greeting(body?.text ?: throw DownstreamException.Failed(null))
        } catch (e: HttpServerErrorException.ServiceUnavailable) {
            throw DownstreamException.ServiceUnavailable(e)
        } catch (e: ResourceAccessException) {
            throw if (e.isTimeout()) DownstreamException.Timeout(e) else DownstreamException.Failed(e)
        } catch (e: RestClientException) {
            throw DownstreamException.Failed(e)
        }

    private fun Throwable.isTimeout(): Boolean = generateSequence(this) { it.cause }.any { it is HttpTimeoutException || it is SocketTimeoutException }
}

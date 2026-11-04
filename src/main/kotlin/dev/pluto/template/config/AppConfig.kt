package dev.pluto.template.config

import dev.pluto.template.adapter.outbound.aws.AwsClients
import dev.pluto.template.adapter.outbound.aws.S3GreetingsStore
import dev.pluto.template.adapter.outbound.http.DownstreamException
import dev.pluto.template.adapter.outbound.http.HttpTranslationGateway
import dev.pluto.template.application.GreetingsService
import dev.pluto.template.application.GreetingsStore
import dev.pluto.template.application.TranslationGateway
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.net.http.HttpClient
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import software.amazon.awssdk.services.s3.S3Client

@ConfigurationProperties("downstream.translator")
data class TranslatorProperties(
    val baseUrl: String,
    val timeout: Duration = Duration.ofMillis(2000),
    val retryAttempts: Int = 3,
    val retryWait: Duration = Duration.ofMillis(50),
)

/** The only place that knows every layer: it wires the adapters to the ports. */
@Configuration
class AppConfig {
    @Bean
    fun translatorRetry(props: TranslatorProperties): Retry =
        Retry.of(
            "translator",
            RetryConfig.custom<Any>()
                .maxAttempts(props.retryAttempts)
                .waitDuration(props.retryWait)
                .retryOnException { it is DownstreamException.ServiceUnavailable }
                .build(),
        )

    @Bean
    fun translatorCircuitBreaker(): CircuitBreaker =
        CircuitBreaker.of(
            "translator",
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50f)
                .recordException { it is DownstreamException }
                .build(),
        )

    @Bean
    fun translatorClient(props: TranslatorProperties): RestClient {
        val http = HttpClient.newBuilder().connectTimeout(props.timeout).build()
        val factory = JdkClientHttpRequestFactory(http).apply { setReadTimeout(props.timeout) }
        return RestClient.builder().baseUrl(props.baseUrl).requestFactory(factory).build()
    }

    @Bean
    fun translationGateway(translatorClient: RestClient, translatorRetry: Retry, translatorCircuitBreaker: CircuitBreaker): TranslationGateway =
        HttpTranslationGateway(translatorClient, translatorRetry, translatorCircuitBreaker)

    @Bean
    fun s3Client(): S3Client = AwsClients.s3()

    @Bean
    fun greetingsStore(s3Client: S3Client, @Value("\${aws.greetings-bucket}") bucket: String): GreetingsStore = S3GreetingsStore(s3Client, bucket)

    @Bean
    fun greetingsService(translations: TranslationGateway, store: GreetingsStore): GreetingsService = GreetingsService(translations, store)
}

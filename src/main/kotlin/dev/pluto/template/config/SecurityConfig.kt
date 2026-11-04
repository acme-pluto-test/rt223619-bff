package dev.pluto.template.config

import dev.pluto.template.adapter.inbound.web.BearerAuthenticationEntryPoint
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.firewall.RequestRejectedHandler
import org.springframework.security.web.firewall.StrictHttpFirewall

/** Every route needs a valid token except the health probes the load balancer calls. */
@Configuration
class TokenDecoderConfig {
    @Bean
    fun jwtDecoder(settings: JwtSettings): JwtDecoder = TokenDecoders.build(settings)
}

@Configuration
class SecurityConfig {
    /** A literal percent in a name reaches the validation, which answers `invalid_name`, instead of ending at the firewall. */
    @Bean
    fun httpFirewall(): StrictHttpFirewall = StrictHttpFirewall().apply { setAllowUrlEncodedPercent(true) }

    /** What the firewall refuses (an encoded slash, a semicolon) leaves with the same error shape as every other 400. */
    @Bean
    fun requestRejectedHandler(): RequestRejectedHandler = RequestRejectedHandler { _, response, _ ->
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("""{"code":"invalid_request"}""")
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt { }
                authenticationEntryPoint = BearerAuthenticationEntryPoint()
            }
        }
        return http.build()!!
    }
}

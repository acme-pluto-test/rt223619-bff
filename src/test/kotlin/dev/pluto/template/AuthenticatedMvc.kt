package dev.pluto.template

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder

/** The business tests are about what a caller with a valid token gets; the token tests build their own requests. */
@TestConfiguration(proxyBeanMethods = false)
class AuthenticatedMvc {
    @Bean
    fun withValidToken(): MockMvcBuilderCustomizer =
        MockMvcBuilderCustomizer { builder: ConfigurableMockMvcBuilder<*> ->
            builder.defaultRequest(MockMvcRequestBuilders.get("/").header("Authorization", TestTokens.bearer()))
        }
}

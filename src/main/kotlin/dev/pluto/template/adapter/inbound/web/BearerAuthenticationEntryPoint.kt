package dev.pluto.template.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

/** A call without a valid token leaves as `401 {"code":"unauthorized"}` and says nothing about why. */
class BearerAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("""{"code":"unauthorized"}""")
    }
}

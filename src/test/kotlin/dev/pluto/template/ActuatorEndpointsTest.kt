package dev.pluto.template

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticatedMvc::class)
class ActuatorEndpointsTest(@Autowired private val mvc: MockMvc) {
    @Test
    fun livenessAndReadinessReportUp() {
        for (probe in listOf("liveness", "readiness")) {
            mvc.get("/actuator/health/$probe").andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
        }
    }

    @Test
    fun sensitiveEndpointsAreNotExposed() {
        for (endpoint in listOf("env", "beans", "heapdump")) {
            mvc.get("/actuator/$endpoint").andExpect { status { isNotFound() } }
            mvc.post("/actuator/$endpoint").andExpect { status { isNotFound() } }
        }
    }
}

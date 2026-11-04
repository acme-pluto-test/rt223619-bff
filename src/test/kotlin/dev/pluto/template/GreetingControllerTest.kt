package dev.pluto.template

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticatedMvc::class)
class GreetingControllerTest(@Autowired private val mvc: MockMvc) {
    @Test
    fun greetsByNameThroughTheGeneratedInterface() {
        mvc.get("/v1/greetings/ana").andExpect {
            status { isOk() }
            jsonPath("$.message") { value("Hello, ana") }
        }
    }

    @Test
    fun acceptsNamesOfOneAndSixtyFourCharacters() {
        for (name in listOf("a", "b".repeat(64))) {
            mvc.get("/v1/greetings/$name").andExpect {
                status { isOk() }
                jsonPath("$.message") { value("Hello, $name") }
            }
        }
    }

    @Test
    fun rejectsNamesOfSixtyFiveCharactersAndNamesWithASpace() {
        for (path in listOf("/v1/greetings/${"c".repeat(65)}", "/v1/greetings/ana%20maria")) {
            mvc.get(path).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("invalid_name") }
            }
        }
    }
}

package dev.pluto.template

import dev.pluto.template.application.GreetingsStore
import dev.pluto.template.application.NoteResult
import dev.pluto.template.domain.Note
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

object FakeStore : GreetingsStore {
    @Volatile var result: NoteResult = NoteResult.NotFound

    override fun note(name: String): NoteResult = result
}

@TestConfiguration
class FakeStoreConfig {
    @Bean
    @Primary
    fun fakeStore(): GreetingsStore = FakeStore
}

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticatedMvc::class, FakeStoreConfig::class)
class NoteAndLanguageTest(@Autowired private val mvc: MockMvc) {
    @Test
    fun notesAnswer200And404And502WithTheirBodies() {
        FakeStore.result = NoteResult.Found(Note("a note"))
        mvc.get("/v1/greetings/ana/note").andExpect { status { isOk() }; jsonPath("$.text") { value("a note") } }
        FakeStore.result = NoteResult.NotFound
        mvc.get("/v1/greetings/ana/note").andExpect { status { isNotFound() }; jsonPath("$.code") { value("note_not_found") } }
        FakeStore.result = NoteResult.Unavailable
        mvc.get("/v1/greetings/ana/note").andExpect { status { isBadGateway() }; jsonPath("$.code") { value("storage_unavailable") } }
        mvc.get("/v1/greetings/ana%20maria/note").andExpect { status { isBadRequest() }; jsonPath("$.code") { value("invalid_name") } }
    }

    @Test
    fun rejectsALanguageThatIsNotTwoLowercaseLetters() {
        for (lang in listOf("PT", "p", "por", "p1")) {
            mvc.get("/v1/greetings/ana/translations/$lang").andExpect { status { isBadRequest() }; jsonPath("$.code") { value("invalid_lang") } }
        }
    }
}

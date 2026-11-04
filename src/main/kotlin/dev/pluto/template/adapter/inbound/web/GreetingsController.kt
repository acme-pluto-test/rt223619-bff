package dev.pluto.template.adapter.inbound.web

import dev.pluto.template.adapter.inbound.web.generated.api.GreetingsApi
import dev.pluto.template.application.GreetingsService
import dev.pluto.template.application.NoteResult
import dev.pluto.template.application.TranslationResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import dev.pluto.template.adapter.inbound.web.generated.model.Greeting as GreetingDto
import dev.pluto.template.adapter.inbound.web.generated.model.Note as NoteDto

/** Implements the interface generated from the contract; a new operation in the contract stops the build until it is here. */
@RestController
class GreetingsController(private val service: GreetingsService) : GreetingsApi {
    override fun getGreeting(name: String): ResponseEntity<GreetingDto> = ResponseEntity.ok(GreetingDto(service.greet(name).message))

    override fun getTranslation(name: String, lang: String): ResponseEntity<GreetingDto> =
        when (val result = service.translate(name, lang)) {
            is TranslationResult.Translated -> ResponseEntity.ok(GreetingDto(result.greeting.message))
            TranslationResult.TimedOut -> throw ApiException(504, "downstream_timeout")
            TranslationResult.Unavailable -> throw ApiException(502, "downstream_unavailable")
        }

    override fun getNote(name: String): ResponseEntity<NoteDto> =
        when (val result = service.note(name)) {
            is NoteResult.Found -> ResponseEntity.ok(NoteDto(result.note.text))
            NoteResult.NotFound -> throw ApiException(404, "note_not_found")
            NoteResult.Unavailable -> throw ApiException(502, "storage_unavailable")
        }
}

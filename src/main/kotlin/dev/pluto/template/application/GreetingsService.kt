package dev.pluto.template.application

import dev.pluto.template.domain.Greeting
import dev.pluto.template.domain.Note

/** Outbound port: the service that translates a greeting. */
interface TranslationGateway {
    fun translate(greeting: Greeting, lang: String): TranslationResult
}

sealed interface TranslationResult {
    data class Translated(val greeting: Greeting) : TranslationResult

    data object TimedOut : TranslationResult

    data object Unavailable : TranslationResult
}

/** Outbound port: where the notes are kept. */
interface GreetingsStore {
    fun note(name: String): NoteResult
}

sealed interface NoteResult {
    data class Found(val note: Note) : NoteResult

    data object NotFound : NoteResult

    /** The store could not be reached or refused the call (a missing bucket, a permission, an outage). */
    data object Unavailable : NoteResult
}

/** Plain Kotlin: no framework type crosses into this layer (the layering rule, door 3). */
class GreetingsService(
    private val translations: TranslationGateway,
    private val store: GreetingsStore,
) {
    fun greet(name: String): Greeting = Greeting("Hello, $name")

    fun translate(name: String, lang: String): TranslationResult = translations.translate(greet(name), lang)

    fun note(name: String): NoteResult = store.note(name)
}

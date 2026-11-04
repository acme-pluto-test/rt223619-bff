package dev.pluto.template.domain

/** A BFF holds no business rules: these are the shapes it passes between a channel and the services behind it. */
data class Greeting(val message: String)

data class Note(val text: String)

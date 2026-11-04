package dev.pluto.template

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/** Door 3 of the pack. The same rules run on the template and on fixtures that break each one. */
class LayeringRules(private val base: String) {
    private val language = arrayOf("kotlin..", "java..", "org.jetbrains..")

    fun domainDependsOnNothingButTheLanguage(): ArchRule =
        classes().that().resideInAPackage("$base.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("$base.domain..", *language)
            .`as`("domain depends only on Kotlin, Java and itself")

    fun applicationDependsOnDomainOnly(): ArchRule =
        classes().that().resideInAPackage("$base.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("$base.application..", "$base.domain..", *language)
            .`as`("application depends only on domain, Kotlin and Java")

    fun adaptersDoNotDependOnEachOther(): ArchRule =
        slices().matching("$base.adapter.(*).(*)..").should().notDependOnEachOther()
            .`as`("no adapter depends on another adapter")

    fun all(): List<ArchRule> = listOf(domainDependsOnNothingButTheLanguage(), applicationDependsOnDomainOnly(), adaptersDoNotDependOnEachOther())
}

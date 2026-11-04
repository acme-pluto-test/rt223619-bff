package dev.pluto.template

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArchitectureTest {
    @Test
    fun theTemplateFollowsTheLayeringRule() {
        val classes = ClassFileImporter().withImportOption(ImportOption.DoNotIncludeTests()).importPackages("dev.pluto.template")
        assertTrue(classes.size > 10, "the import found the template classes")
        for (rule in LayeringRules("dev.pluto.template").all()) rule.check(classes)
    }

    @Test
    fun theRulesFailOnEachKindOfViolation() {
        val classes = ClassFileImporter().importPackages("dev.pluto.violations")
        val rules = LayeringRules("dev.pluto.violations")
        val expectations = listOf(
            rules.domainDependsOnNothingButTheLanguage() to listOf("BadDomainImportsSpring", "BadDomainImportsAdapter", "BadDomainImportsLibrary"),
            rules.applicationDependsOnDomainOnly() to listOf("BadApplicationImportsAdapter", "BadApplicationImportsLibrary"),
            rules.adaptersDoNotDependOnEachOther() to listOf("BadInboundImportsOutbound"),
        )
        for ((rule, offenders) in expectations) {
            val failure = assertThrows<AssertionError> { rule.check(classes) }
            val message = failure.message ?: ""
            for (offender in offenders) assertTrue(message.contains(offender), "${rule.description} names $offender, got: $message")
            assertTrue(message.contains(rule.description), "the message names the rule: ${rule.description}")
        }
    }
}

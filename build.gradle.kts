plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
    jacoco
}

group = "dev.pluto.template"
version = "0.0.1"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencyManagement {
    imports { mavenBom(libs.awssdk.bom.get().toString()) }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.awssdk.s3)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(libs.archunit)
    testImplementation(libs.testcontainers)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-java-parameters")
    }
}

// The contract is the source of truth: the controller interface is generated from it at every build, so an
// operation added to the contract without a controller fails to compile.
val generatedDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$projectDir/src/main/openapi/api.yaml")
    outputDir.set(generatedDir.get().asFile.path)
    apiPackage.set("dev.pluto.template.adapter.inbound.web.generated.api")
    modelPackage.set("dev.pluto.template.adapter.inbound.web.generated.model")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useTags" to "true",
            "useBeanValidation" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "skipDefaultInterface" to "true",
            "serializationLibrary" to "jackson",
            "enumPropertyNaming" to "UPPERCASE",
        ),
    )
}

sourceSets.main { kotlin.srcDir(generatedDir.map { it.dir("src/main/kotlin") }) }
tasks.compileKotlin { dependsOn(tasks.openApiGenerate) }

tasks.jar { enabled = false }

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

val coverageExcludes = listOf("**/generated/**", "**/ApplicationKt.class")

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required = true }
    classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }))
}

// The platform's gate: 80 percent of lines (stack-pack-kotlin-spring, criterion 1).
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }))
    violationRules {
        rule { limit { counter = "LINE"; minimum = "0.80".toBigDecimal() } }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

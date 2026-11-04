package dev.pluto.template.adapter

import dev.pluto.template.adapter.outbound.aws.AwsClients
import dev.pluto.template.adapter.outbound.aws.S3GreetingsStore
import dev.pluto.template.application.NoteResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/** The AWS adapter against Floci 2.3.0, the emulator the platform pins (AD-003). */
class S3GreetingsStoreFlociTest {
    companion object {
        private val floci = GenericContainer(DockerImageName.parse("floci/floci:2.3.0")).withExposedPorts(4566).waitingFor(Wait.forListeningPort())

        @JvmStatic
        @BeforeAll
        fun start() {
            floci.start()
            System.setProperty("aws.endpointUrl", "http://${floci.host}:${floci.getMappedPort(4566)}")
            System.setProperty("aws.region", "us-east-1")
            System.setProperty("aws.accessKeyId", "test")
            System.setProperty("aws.secretAccessKey", "test")
            AwsClients.s3().use { s3 ->
                s3.createBucket(CreateBucketRequest.builder().bucket("greetings").build())
                s3.putObject(PutObjectRequest.builder().bucket("greetings").key("greetings/ana.txt").build(), RequestBody.fromString("a note for ana"))
            }
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            listOf("aws.endpointUrl", "aws.region", "aws.accessKeyId", "aws.secretAccessKey").forEach(System::clearProperty)
            floci.stop()
        }
    }

    @Test
    fun readsAnObjectThroughAwsEndpointUrl() {
        AwsClients.s3().use { s3 ->
            val result = S3GreetingsStore(s3, "greetings").note("ana")
            assertTrue(result is NoteResult.Found, "got $result")
            assertEquals("a note for ana", (result as NoteResult.Found).note.text)
        }
    }

    @Test
    fun returnsUnavailableForAMissingBucket() {
        AwsClients.s3().use { s3 ->
            assertEquals(NoteResult.Unavailable, S3GreetingsStore(s3, "no-such-bucket").note("ana"))
        }
    }

    @Test
    fun returnsNotFoundForAMissingObject() {
        AwsClients.s3().use { s3 ->
            assertEquals(NoteResult.NotFound, S3GreetingsStore(s3, "greetings").note("nobody"))
        }
    }
}

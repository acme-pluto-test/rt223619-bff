package dev.pluto.template.adapter.outbound.aws

import dev.pluto.template.application.GreetingsStore
import dev.pluto.template.application.NoteResult
import dev.pluto.template.domain.Note
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

/** Reads `greetings/<name>.txt` from a bucket. The endpoint is not here: the SDK reads `AWS_ENDPOINT_URL` (FR-BE-05). */
class S3GreetingsStore(private val s3: S3Client, private val bucket: String) : GreetingsStore {
    override fun note(name: String): NoteResult =
        try {
            val request = GetObjectRequest.builder().bucket(bucket).key("greetings/$name.txt").build()
            NoteResult.Found(Note(s3.getObjectAsBytes(request).asUtf8String()))
        } catch (e: NoSuchKeyException) {
            NoteResult.NotFound
        } catch (e: SdkException) {
            NoteResult.Unavailable
        }
}

object AwsClients {
    /** Region and credentials come from the SDK's default chains; the endpoint from `AWS_ENDPOINT_URL` or `aws.endpointUrl`. */
    fun s3(): S3Client {
        val customEndpoint = System.getenv("AWS_ENDPOINT_URL") != null || System.getProperty("aws.endpointUrl") != null
        val region = try {
            DefaultAwsRegionProviderChain.builder().build().region
        } catch (e: SdkClientException) {
            Region.US_EAST_1
        }
        // an emulator such as Floci serves buckets by path, not by host name
        return S3Client.builder().region(region).forcePathStyle(customEndpoint).build()
    }
}

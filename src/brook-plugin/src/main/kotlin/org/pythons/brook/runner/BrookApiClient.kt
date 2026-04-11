package org.pythons.brook.runner

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Communicates with the Brook FastAPI backend running on localhost.
 */
object BrookApiClient {

    private val LOG = Logger.getInstance(BrookApiClient::class.java)
    private const val BASE_URL = "http://localhost:8000"

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // ── DTOs matching the FastAPI request/response models ──

    @Serializable
    data class InjectRequest(val repo_path: String, val speciality: String)

    @Serializable
    data class InjectResponse(val status: String, val directory_tree: String, val git_diff: String)

    @Serializable
    data class HintRequest(val repo_path: String, val speciality: String, val message: String = "")

    @Serializable
    data class VerifyRequest(val repo_path: String, val speciality: String)

    @Serializable
    data class VerifyResponse(val solved: Boolean, val feedback: String)

    @Serializable
    data class SseChunk(val chunk: String)

    // Keep FileModification for backward compatibility with FileModifier
    @Serializable
    data class FileModification(val file: String, val content: String)

    // ── API Methods ──

    /**
     * Calls POST /inject to initialize the broken repo.
     */
    fun inject(repoPath: String, specialty: String): Result<InjectResponse> {
        return try {
            val body = json.encodeToString(InjectRequest.serializer(), InjectRequest(repoPath, specialty))
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/inject"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                return Result.failure(RuntimeException("Backend returned ${response.statusCode()}: ${response.body()}"))
            }

            Result.success(json.decodeFromString(InjectResponse.serializer(), response.body()))
        } catch (e: Exception) {
            LOG.error("Brook API inject failed", e)
            Result.failure(e)
        }
    }

    /**
     * Calls POST /hint and streams the SSE response, calling [onChunk] for each token.
     * Returns the full concatenated hint text.
     */
    fun hintStream(
        repoPath: String,
        specialty: String,
        currentFile: String,
        onChunk: (String) -> Unit
    ): Result<String> {
        return try {
            val body = json.encodeToString(
                HintRequest.serializer(),
                HintRequest(repoPath, specialty, currentFile)
            )
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/hint"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            if (response.statusCode() != 200) {
                val errorBody = response.body().bufferedReader().readText()
                return Result.failure(RuntimeException("Backend returned ${response.statusCode()}: $errorBody"))
            }

            val fullText = StringBuilder()
            val reader = BufferedReader(InputStreamReader(response.body()))

            reader.useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("data: ")) {
                        val payload = line.removePrefix("data: ").trim()
                        if (payload == "[DONE]") break

                        try {
                            val chunk = json.decodeFromString(SseChunk.serializer(), payload)
                            fullText.append(chunk.chunk)
                            onChunk(chunk.chunk)
                        } catch (e: Exception) {
                            LOG.warn("Failed to parse SSE chunk: $payload", e)
                        }
                    }
                }
            }

            Result.success(fullText.toString())
        } catch (e: Exception) {
            LOG.error("Brook API hint failed", e)
            Result.failure(e)
        }
    }

    /**
     * Calls POST /verify to grade the student's solution.
     */
    fun verify(repoPath: String, specialty: String): Result<VerifyResponse> {
        return try {
            val body = json.encodeToString(VerifyRequest.serializer(), VerifyRequest(repoPath, specialty))
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/verify"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                return Result.failure(RuntimeException("Backend returned ${response.statusCode()}: ${response.body()}"))
            }

            Result.success(json.decodeFromString(VerifyResponse.serializer(), response.body()))
        } catch (e: Exception) {
            LOG.error("Brook API verify failed", e)
            Result.failure(e)
        }
    }

    /**
     * Quick health check against GET /health.
     */
    fun isBackendReachable(): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/health"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
}

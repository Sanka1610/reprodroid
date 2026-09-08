package com.sanka1610.reprodroid.data.network

import com.sanka1610.reprodroid.BuildConfig
import com.sanka1610.reprodroid.data.connection.FixedRunnerTrustManager
import com.sanka1610.reprodroid.data.connection.RunnerClientTransportMode
import com.sanka1610.reprodroid.data.connection.RunnerConnectionRegistry
import com.sanka1610.reprodroid.data.connection.RunnerTransportContext
import com.sanka1610.reprodroid.data.connection.RunnerTransportPolicy
import com.sanka1610.reprodroid.data.connection.SelfRevokeResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.time.Instant

class RunnerApiException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String,
) : RuntimeException(message)

class RunnerConfigurationException(message: String) : RuntimeException(message)

class RunnerResponseIntegrityException(message: String) : RuntimeException(message)

data class ArtifactDownloadResponse(
    val bytesWritten: Long,
    val contentLength: Long?,
    val etag: String?,
    val contentType: String?,
)

class RunnerApiClient(
    baseUrl: String,
    engine: HttpClientEngine? = null,
    private val allowDevelopmentV2: Boolean = false,
    private val allowDevelopmentHttp: Boolean = BuildConfig.DEBUG,
) : AutoCloseable {
    private val registry = RunnerConnectionRegistry(baseUrl)
    private val injectedEngine = engine
    @Volatile private var activeSession: ClientSession? = null
    private var removeRetirementListener = registry.onSessionRetired(::retireSession)

    init {
        validateBaseUrl(baseUrl)
    }

    constructor(
        registry: RunnerConnectionRegistry,
        allowDevelopmentHttp: Boolean,
        engine: HttpClientEngine? = null,
    ) : this("", engine, allowDevelopmentV2 = allowDevelopmentHttp, allowDevelopmentHttp = allowDevelopmentHttp) {
        this.externalRegistry = registry
        this.externalAllowDevelopmentHttp = allowDevelopmentHttp
        removeRetirementListener()
        removeRetirementListener = registry.onSessionRetired(::retireSession)
    }

    private var externalRegistry: RunnerConnectionRegistry? = null
    private var externalAllowDevelopmentHttp: Boolean? = null
    private val effectiveRegistry: RunnerConnectionRegistry get() = externalRegistry ?: registry
    private val developmentHttpAllowed: Boolean get() = externalAllowDevelopmentHttp ?: allowDevelopmentHttp
    private val client: HttpClient get() = currentSession().client

    suspend fun createJob(request: CreateJobRequest, expectedRunnerId: String? = null): CreateJobResponse =
        client.post(endpoint("/v1/jobs")) {
            bindRunner(expectedRunnerId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.successBody()

    suspend fun getJob(jobId: String, expectedRunnerId: String? = null): JobResponse =
        client.prepareGet(endpoint("/v1/jobs/$jobId")) { bindRunner(expectedRunnerId) }.execute { response ->
            val text = response.boundedUtf8Body(1_048_576, "job")
            try {
                val node = SANDBOX_JSON.parseToJsonElement(text).jsonObject
                validateJobSandboxJson(node)
                JOB_JSON.decodeFromJsonElement<JobResponse>(node).also {
                    require(it.jobId == jobId)
                    validateJobSandbox(it.sandbox, it.executionMode, it.state)
                }
            } catch (_: Exception) {
                throw RunnerResponseIntegrityException("Runner Job sandbox response is invalid.")
            }
        }

    suspend fun getLogs(jobId: String, afterSequence: Long, limit: Int = 200, expectedRunnerId: String? = null): LogResponse =
        client.get(endpoint("/v1/jobs/$jobId/logs")) {
            bindRunner(expectedRunnerId)
            url {
                parameters.append("afterSequence", afterSequence.toString())
                parameters.append("limit", limit.toString())
            }
        }.successBody()

    suspend fun getBuildEnvironmentManifest(jobId: String, expectedRunnerId: String? = null): BuildEnvironmentManifestResponse =
        client.prepareGet(endpoint("/v1/jobs/$jobId/build-environment-manifest")) { bindRunner(expectedRunnerId) }
            .execute { response ->
                val jsonText = response.boundedUtf8Body(
                    maximumBytes = MAX_BUILD_MANIFEST_RESPONSE_BYTES,
                    description = "build manifest",
                )
                try {
                    val root = BUILD_MANIFEST_JSON.parseToJsonElement(jsonText).jsonObject
                    if (root.containsKey("sandbox")) decodeSandboxEvidence(root.getValue("sandbox").toString())
                    BUILD_MANIFEST_JSON.decodeFromString(jsonText)
                } catch (_: SerializationException) {
                    throw RunnerResponseIntegrityException("Runner build manifest response is not valid public schema v1/v2/v3 JSON.")
                } catch (_: IllegalArgumentException) {
                    throw RunnerResponseIntegrityException("Runner build manifest response is not valid public schema v1/v2/v3 JSON.")
                }
            }

    suspend fun getSourceScan(jobId: String, expectedRunnerId: String? = null): SourceScanDetailResponse =
        client.prepareGet(endpoint("/v1/jobs/$jobId/source-scan")) { bindRunner(expectedRunnerId) }
            .execute { response ->
                val jsonText = response.boundedUtf8Body(
                    maximumBytes = MAX_SOURCE_SCAN_RESPONSE_BYTES,
                    description = "source scan",
                )
                try {
                    SOURCE_SCAN_JSON.decodeFromString(jsonText)
                } catch (_: SerializationException) {
                    throw RunnerResponseIntegrityException("Runner source scan response is not valid public schema v1 JSON.")
                } catch (_: IllegalArgumentException) {
                    throw RunnerResponseIntegrityException("Runner source scan response is not valid public schema v1 JSON.")
                }
            }

    suspend fun confirmJob(jobId: String, request: ConfirmJobRequest, expectedRunnerId: String? = null) {
        client.post(endpoint("/v1/jobs/$jobId/confirm")) {
            bindRunner(expectedRunnerId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.ensureSuccess()
    }

    suspend fun continueSourceScan(jobId: String, request: ContinueSourceScanRequest, expectedRunnerId: String? = null) {
        client.post(endpoint("/v1/jobs/$jobId/source-scan/continue")) {
            bindRunner(expectedRunnerId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.ensureSuccess()
    }

    suspend fun cancelJob(jobId: String, expectedRunnerId: String? = null) {
        client.post(endpoint("/v1/jobs/$jobId/cancel")) { bindRunner(expectedRunnerId) }.ensureSuccess()
    }

    suspend fun retryJob(jobId: String, expectedRunnerId: String? = null): CreateJobResponse =
        client.post(endpoint("/v1/jobs/$jobId/retry")) { bindRunner(expectedRunnerId) }.successBody()

    suspend fun createGenericBuild(request: GenericBuildCreateRequest, idempotencyKey: String, expectedRunnerId: String? = null): CreateJobResponse =
        v2Mutation<GenericBuildCreateRequest, CreateJobResponse>(
            "/v2/builds", request, idempotencyKey, V2_GENERIC_BUILD_CONTRACT, expectedRunnerId,
        )

    suspend fun getGenericBuild(jobId: String, expectedRunnerId: String? = null): GenericBuildResponse {
        requireCanonicalUuid(jobId, "jobId")
        return client.prepareGet(endpoint("/v2/builds/$jobId")) { bindRunner(expectedRunnerId) }.execute { response ->
            response.v2Body<GenericBuildResponse>().also { generic ->
                checkV2(generic.job.jobId == jobId && generic.job.genericBuild == generic.genericBuild)
                validateGenericBuild(generic.genericBuild)
                generic.discovery?.let(::validateGenericDiscovery)
                validateJobSandbox(generic.job.sandbox, generic.job.executionMode, generic.job.state)
            }
        }
    }

    suspend fun confirmGenericBuild(jobId: String, request: ConfirmJobRequest, idempotencyKey: String, expectedRunnerId: String? = null) {
        requireCanonicalUuid(jobId, "jobId")
        v2MutationNoResponse("/v2/builds/$jobId:confirm", request, idempotencyKey, V2_GENERIC_BUILD_CONTRACT, expectedRunnerId)
    }

    suspend fun continueGenericSourceScan(jobId: String, request: ContinueSourceScanRequest, idempotencyKey: String, expectedRunnerId: String? = null) {
        requireCanonicalUuid(jobId, "jobId")
        v2MutationNoResponse("/v2/builds/$jobId:scan-continue", request, idempotencyKey, V2_GENERIC_BUILD_CONTRACT, expectedRunnerId)
    }

    suspend fun cancelGenericBuild(jobId: String, idempotencyKey: String, expectedRunnerId: String? = null) {
        requireCanonicalUuid(jobId, "jobId")
        v2MutationNoResponse("/v2/builds/$jobId:cancel", EmptyV2Request(), idempotencyKey, V2_GENERIC_BUILD_CONTRACT, expectedRunnerId)
    }

    suspend fun createGenericComparison(
        request: CreateGenericComparisonRequest,
        idempotencyKey: String,
        expectedRunnerId: String? = null,
    ): GenericComparisonResponse = v2Mutation<CreateGenericComparisonRequest, GenericComparisonResponse>(
        "/v2/comparisons", request, idempotencyKey, V2_APK_COMPARISON_CONTRACT, expectedRunnerId,
    ).also(::validateGenericComparison)

    suspend fun getGenericComparison(comparisonId: String, expectedRunnerId: String? = null): GenericComparisonResponse {
        requireCanonicalUuid(comparisonId, "comparisonId")
        return client.prepareGet(endpoint("/v2/comparisons/$comparisonId")) { bindRunner(expectedRunnerId) }.execute { response ->
            response.v2Body<GenericComparisonResponse>().also(::validateGenericComparison)
        }
    }

    suspend fun retryGenericResource(
        comparisonId: String,
        idempotencyKey: String,
        expectedRunnerId: String? = null,
    ): GenericResourceRetryResponse {
        requireCanonicalUuid(comparisonId, "comparisonId")
        return v2Mutation<EmptyV2Request, GenericResourceRetryResponse>(
            "/v2/comparisons/$comparisonId:retry-resource",
            EmptyV2Request(),
            idempotencyKey,
            V2_APK_COMPARISON_CONTRACT,
            expectedRunnerId,
        ).also(::validateGenericResourceRetry)
    }

    suspend fun getV2Capabilities(): V2CapabilitiesResponse =
        client.prepareGet(endpoint("/v2/capabilities")).execute { response ->
            response.v2Body<V2CapabilitiesResponse>().also(::validateCapabilities)
        }

    internal suspend fun selfRevoke(): SelfRevokeResponse =
        client.post(endpoint("/v2/authentication/self-revoke")) {
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }.v2Body<SelfRevokeResponse>().also { response ->
            if (
                response.schemaVersion != 1 || response.runnerId != activeRunnerId() ||
                response.state != "REVOKED" || runCatching { Instant.parse(response.revokedAt) }.isFailure
            ) throw RunnerResponseIntegrityException("Runner self-revocation response is invalid.")
        }

    suspend fun getV2Operation(operationId: String): V2OperationResponse {
        requireCanonicalUuid(operationId, "operationId")
        return client.prepareGet(endpoint("/v2/operations/$operationId")).execute { response ->
            response.v2Body<V2OperationResponse>().also(::validateOperation)
        }
    }

    suspend fun getV2StorageSummary(): V2StorageSummaryResponse =
        client.prepareGet(endpoint("/v2/storage/summary")).execute { response ->
            response.v2Body<V2StorageSummaryResponse>().also(::validateStorageSummary)
        }

    suspend fun createV2RetentionHold(
        request: V2RetentionHoldRequest,
        idempotencyKey: String,
    ): V2OperationResponse = v2Mutation("/v2/retention/holds", request, idempotencyKey)

    suspend fun releaseV2RetentionHold(
        holdId: String,
        reason: String,
        idempotencyKey: String,
    ): V2OperationResponse {
        requireCanonicalUuid(holdId, "holdId")
        return v2Mutation("/v2/retention/holds/$holdId/release", V2ReasonRequest(reason), idempotencyKey)
    }

    suspend fun createV2CleanupPreview(
        request: V2CleanupPreviewRequest,
        idempotencyKey: String,
    ): V2OperationResponse = v2Mutation("/v2/cleanup/previews", request, idempotencyKey)

    suspend fun getV2CleanupPreview(previewId: String): V2CleanupPreviewResponse {
        requireCanonicalUuid(previewId, "previewId")
        return client.prepareGet(endpoint("/v2/cleanup/previews/$previewId")).execute { response ->
            response.v2Body<V2CleanupPreviewResponse>().also(::validateCleanupPreview)
        }
    }

    suspend fun executeV2Cleanup(
        previewId: String,
        itemIds: List<String>,
        idempotencyKey: String,
    ): V2OperationResponse {
        requireCanonicalUuid(previewId, "previewId")
        require(itemIds.isNotEmpty() && itemIds.size <= 100 && itemIds.distinct().size == itemIds.size)
        itemIds.forEach { requireCanonicalUuid(it, "itemId") }
        return v2Mutation(
            "/v2/cleanup/previews/$previewId/execute",
            V2CleanupExecuteRequest(itemIds.sorted()),
            idempotencyKey,
        )
    }

    suspend fun getV2CleanupRun(cleanupRunId: String): V2CleanupRunResponse {
        requireCanonicalUuid(cleanupRunId, "cleanupRunId")
        return client.prepareGet(endpoint("/v2/cleanup/runs/$cleanupRunId")).execute { response ->
            response.v2Body<V2CleanupRunResponse>().also(::validateCleanupRun)
        }
    }

    suspend fun resolveToolchainPlan(request: ResolveToolchainPlanRequest): ToolchainPlanResponse =
        client.post(endpoint("/v2/toolchains/plans:resolve")) { contentType(ContentType.Application.Json); setBody(request) }
            .v2Body<ToolchainPlanResponse>().also(::validateToolchainPlan)

    suspend fun createToolchainInstallation(request: CreateToolchainInstallationRequest, idempotencyKey: String): ToolchainInstallationResponse =
        v2Mutation<CreateToolchainInstallationRequest, ToolchainInstallationResponse>(
            "/v2/toolchains/installations", request, idempotencyKey, V2_TOOLCHAIN_CONTRACT,
        ).also(::validateToolchainInstallation)

    suspend fun getToolchainInstallation(installationId: String): ToolchainInstallationResponse {
        requireCanonicalUuid(installationId, "installationId")
        return client.prepareGet(endpoint("/v2/toolchains/installations/$installationId")).execute { response ->
            response.v2Body<ToolchainInstallationResponse>().also(::validateToolchainInstallation)
        }
    }

    suspend fun cancelToolchainInstallation(installationId: String, idempotencyKey: String): ToolchainInstallationResponse {
        requireCanonicalUuid(installationId, "installationId")
        return v2MutationWithoutBody<ToolchainInstallationResponse>(
            "/v2/toolchains/installations/$installationId:cancel", idempotencyKey, V2_TOOLCHAIN_CONTRACT,
        ).also(::validateToolchainInstallation)
    }

    suspend fun getToolchainInventory(): ToolchainInventoryResponse =
        client.prepareGet(endpoint("/v2/toolchains/inventory")).execute { response ->
            response.v2Body<ToolchainInventoryResponse>().also(::validateToolchainInventory)
        }

    suspend fun previewToolchainRemoval(artifactIds: List<String>, idempotencyKey: String): ToolchainRemovalPreviewResponse =
        v2Mutation<ToolchainRemovalRequest, ToolchainRemovalPreviewResponse>(
            "/v2/toolchains/removals:preview", ToolchainRemovalRequest(artifactIds), idempotencyKey, V2_TOOLCHAIN_CONTRACT,
        ).also(::validateToolchainRemovalPreview)

    suspend fun executeToolchainRemoval(previewId: String, idempotencyKey: String): V2OperationResponse {
        requireCanonicalUuid(previewId, "previewId")
        return v2Mutation<ExecuteToolchainRemovalRequest, V2OperationResponse>(
            "/v2/toolchains/removals:execute", ExecuteToolchainRemovalRequest(previewId), idempotencyKey, V2_TOOLCHAIN_CONTRACT,
        ).also(::validateOperation)
    }

    suspend fun downloadArtifact(jobId: String, artifactId: String, destination: File, expectedRunnerId: String? = null): ArtifactDownloadResponse =
        client.prepareGet(endpoint("/v1/jobs/$jobId/artifacts/$artifactId/content")) {
            bindRunner(expectedRunnerId)
            timeout {
                requestTimeoutMillis = DOWNLOAD_REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = DOWNLOAD_SOCKET_TIMEOUT_MILLIS
            }
        }.execute { response ->
            response.ensureSuccess()
            val channel = response.bodyAsChannel()
            var bytesWritten = 0L
            FileOutputStream(destination, false).use { output ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        bytesWritten += read
                    }
                }
                output.fd.sync()
            }
            ArtifactDownloadResponse(
                bytesWritten = bytesWritten,
                contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
                etag = response.headers[HttpHeaders.ETag],
                contentType = response.headers[HttpHeaders.ContentType],
            )
        }

    private fun io.ktor.client.HttpClientConfig<*>.configure(context: RunnerTransportContext) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }

    private fun secureClient(client: HttpClient, context: RunnerTransportContext): HttpClient = client.also { created ->
        created.plugin(HttpSend).intercept { request ->
                if (effectiveRegistry.current() !== context) {
                    throw RunnerConfigurationException("Runner connection changed before request dispatch.")
                }
                val expectedBinding = request.headers[INTERNAL_RUNNER_BINDING_HEADER]
                request.headers.remove(INTERNAL_RUNNER_BINDING_HEADER)
                if (
                    expectedBinding != null &&
                    ((expectedBinding == DEVELOPMENT_BINDING && context.mode != RunnerClientTransportMode.DEVELOPMENT_HTTP) ||
                        (expectedBinding != DEVELOPMENT_BINDING && context.runnerId != expectedBinding))
                ) {
                    throw RunnerConfigurationException("Runner request does not match the stored resource binding.")
                }
                val configured = RunnerTransportPolicy.parseEndpoint(context.endpoint)
                val actualPort = request.url.port
                val expectedPort = configured.port
                val firstPathSegment = request.url.encodedPathSegments.firstOrNull(String::isNotEmpty)
                if (
                    request.url.protocol.name.lowercase() != configured.scheme.lowercase() ||
                    !request.url.host.equals(configured.host, ignoreCase = true) ||
                    actualPort != expectedPort ||
                    firstPathSegment !in setOf("v1", "v2")
                ) {
                    throw RunnerConfigurationException("Runner request origin does not match its immutable connection session.")
                }
                if (context.mode == RunnerClientTransportMode.PAIRED_HTTPS) {
                    request.headers.remove(HttpHeaders.Authorization)
                    request.headers.append(HttpHeaders.Authorization, "Bearer ${requireNotNull(context.bearerToken)}")
                }
            execute(request)
        }
    }

    private suspend inline fun <reified T> HttpResponse.successBody(): T {
        ensureSuccess()
        return body()
    }

    private suspend inline fun <reified Request, reified Response> v2Mutation(
        path: String,
        request: Request,
        idempotencyKey: String,
        contract: String = V2_STORAGE_CONTRACT,
        expectedRunnerId: String? = null,
    ): Response {
        requireCanonicalUuid(idempotencyKey, "Idempotency-Key")
        return client.post(endpoint(path)) {
            bindRunner(expectedRunnerId)
            contentType(ContentType.Application.Json)
            header(V2_CONTRACT_HEADER, contract)
            header(V2_IDEMPOTENCY_HEADER, idempotencyKey)
            setBody(request)
        }.v2Body()
    }

    private suspend inline fun <reified Request> v2MutationNoResponse(
        path: String,
        request: Request,
        idempotencyKey: String,
        contract: String,
        expectedRunnerId: String? = null,
    ) {
        requireCanonicalUuid(idempotencyKey, "Idempotency-Key")
        client.post(endpoint(path)) {
            bindRunner(expectedRunnerId)
            contentType(ContentType.Application.Json)
            header(V2_CONTRACT_HEADER, contract)
            header(V2_IDEMPOTENCY_HEADER, idempotencyKey)
            setBody(request)
        }.ensureSuccess()
    }

    private suspend inline fun <reified Response> v2MutationWithoutBody(
        path: String,
        idempotencyKey: String,
        contract: String,
    ): Response {
        requireCanonicalUuid(idempotencyKey, "Idempotency-Key")
        return client.post(endpoint(path)) {
            header(V2_CONTRACT_HEADER, contract)
            header(V2_IDEMPOTENCY_HEADER, idempotencyKey)
        }.v2Body()
    }

    private suspend inline fun <reified T> HttpResponse.v2Body(): T {
        val text = boundedUtf8Body(MAX_V2_RESPONSE_BYTES, "API v2")
        return try {
            StrictJsonAuditor(text).audit()
            V2_JSON.decodeFromString(text)
        } catch (_: Exception) {
            throw RunnerResponseIntegrityException("Runner API v2 response is invalid.")
        }
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (status.value in 200..299) return
        val apiError = runCatching { body<ApiErrorResponse>() }.getOrNull()
        throw RunnerApiException(
            statusCode = status.value,
            errorCode = apiError?.code ?: "HTTP_${status.value}",
            message = apiError?.message ?: status.description,
        )
    }

    private suspend fun HttpResponse.boundedUtf8Body(maximumBytes: Int, description: String): String {
        ensureSuccess()
        val declaredLength = headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > maximumBytes) {
            throw RunnerResponseIntegrityException("Runner $description response exceeds ${maximumBytes / MIB} MiB.")
        }
        val channel = bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BOUNDED_RESPONSE_BUFFER_SIZE)
        var total = 0
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            if (read > 0) {
                total += read
                if (total > maximumBytes) {
                    throw RunnerResponseIntegrityException("Runner $description response exceeds ${maximumBytes / MIB} MiB.")
                }
                output.write(buffer, 0, read)
            }
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
        } catch (_: CharacterCodingException) {
            throw RunnerResponseIntegrityException("Runner $description response is not valid UTF-8.")
        }
    }

    private fun endpoint(path: String): String {
        val context = effectiveRegistry.current()
            ?: throw RunnerConfigurationException("No active Runner connection is configured.")
        if (path.startsWith("/v2/") && context.mode == RunnerClientTransportMode.DEVELOPMENT_HTTP) {
            requireDevelopmentV2Transport(context)
        }
        return context.endpoint + path
    }

    private fun requireDevelopmentV2Transport(context: RunnerTransportContext) {
        if (!allowDevelopmentV2) {
            throw RunnerConfigurationException("Runner API v2 development access is disabled for this build.")
        }
        val host = URI(context.endpoint).host?.lowercase()
        if (host !in setOf("127.0.0.1", "localhost", "::1")) {
            throw RunnerConfigurationException(
                "Runner API v2 is limited to the loopback development transport before pairing is implemented.",
            )
        }
    }

    private fun validateBaseUrl(baseUrl: String) {
        if (baseUrl.isBlank()) return
        val uri = runCatching { URI(baseUrl) }.getOrNull()
            ?: throw RunnerConfigurationException("Runner base URL is not a valid URI.")
        if (
            uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.host == null ||
            uri.userInfo != null ||
            uri.query != null ||
            uri.fragment != null ||
            uri.port == 0 ||
            uri.port > 65_535 ||
            (uri.path.isNotEmpty() && uri.path != "/")
        ) {
            throw RunnerConfigurationException(
                "Runner base URL must contain only an HTTP(S) scheme, host, and optional port.",
            )
        }
    }

    private fun currentSession(): ClientSession {
        val context = effectiveRegistry.current()
            ?: throw RunnerConfigurationException("No active Runner connection is configured.")
        try {
            RunnerTransportPolicy.validate(context)
        } catch (_: IllegalArgumentException) {
            throw RunnerConfigurationException("Runner transport configuration is invalid.")
        }
        if (context.mode == RunnerClientTransportMode.DEVELOPMENT_HTTP && !developmentHttpAllowed) {
            throw RunnerConfigurationException("This build refuses every HTTP Runner endpoint.")
        }
        activeSession?.takeIf { it.context === context }?.let { return it }
        return synchronized(this) {
            activeSession?.takeIf { it.context === context } ?: createSession(context).also { replacement ->
                activeSession?.client?.close()
                activeSession = replacement
            }
        }
    }

    private fun createSession(context: RunnerTransportContext): ClientSession {
        val httpClient = if (injectedEngine != null) {
            HttpClient(injectedEngine) { configure(context) }
        } else {
            HttpClient(CIO) {
                if (context.mode == RunnerClientTransportMode.PAIRED_HTTPS) {
                    val host = RunnerTransportPolicy.parseEndpoint(context.endpoint).host
                    engine {
                        https {
                            trustManager = FixedRunnerTrustManager(
                                endpointHost = host,
                                rootPin = requireNotNull(context.rootSpkiSha256),
                                expectedRootCertificateDer = requireNotNull(context.rootCertificateDer),
                            )
                        }
                    }
                }
                configure(context)
            }
        }
        return ClientSession(context, secureClient(httpClient, context))
    }

    fun activeRunnerId(): String? = effectiveRegistry.current()?.runnerId

    override fun close() {
        removeRetirementListener()
        retireSession()
    }

    private fun retireSession() {
        synchronized(this) {
            activeSession?.client?.close()
            activeSession = null
        }
    }

    private data class ClientSession(val context: RunnerTransportContext, val client: HttpClient)

    private fun HttpRequestBuilder.bindRunner(expectedRunnerId: String?) {
        expectedRunnerId?.let { header(INTERNAL_RUNNER_BINDING_HEADER, it) }
    }

    private fun validateCapabilities(response: V2CapabilitiesResponse) {
        checkV2(response.apiVersion == "v2" && response.foundationContractVersion == 1)
        requireCanonicalUuid(response.runnerId, "runnerId")
        checkV2(response.runnerVersion.isNotBlank() && response.runnerVersion.length <= 128)
        checkV2(response.capabilities.size <= 64)
        checkV2(response.capabilities.map(V2Capability::id).distinct().size == response.capabilities.size)
        response.capabilities.forEach {
            checkV2(CAPABILITY_ID.matches(it.id) && it.contractVersion in 1..Int.MAX_VALUE)
        }
    }

    private fun validateGenericBuild(snapshot: GenericBuildSnapshot) {
        requireCanonicalUuid(snapshot.comparisonId, "comparisonId")
        checkV2(snapshot.configurationRevision > 0 && SHA256.matches(snapshot.configurationSha256))
        checkV2(snapshot.configurationCanonicalJson.toByteArray().size <= 64 * 1024)
        checkV2(snapshot.expectedArtifactFileName.endsWith(".apk", ignoreCase = true) &&
            '/' !in snapshot.expectedArtifactFileName && '\\' !in snapshot.expectedArtifactFileName)
        snapshot.retryOfJobId?.let { requireCanonicalUuid(it, "retryOfJobId") }
        checkV2(snapshot.memoryBytes in setOf(8_589_934_592, 12_884_901_888))
    }

    private fun validateGenericDiscovery(discovery: GenericDiscoveryEvidence) {
        checkV2(discovery.schemaVersion == 1 && SHA256.matches(discovery.configurationSha256) && SHA256.matches(discovery.outputSha256))
        requireCanonicalUuid(discovery.jobId, "jobId")
        checkV2(discovery.outputBytes in 1..8L * 1024 * 1024 && discovery.selectedTasks.isNotEmpty())
        checkV2(runCatching { Instant.parse(discovery.observedAt) }.isSuccess)
    }

    private fun validateGenericComparison(response: GenericComparisonResponse) {
        checkV2(response.schemaVersion == 1 && SHA256.matches(response.configurationSha256))
        requireCanonicalUuid(response.comparisonId, "comparisonId")
        requireCanonicalUuid(response.buildAJobId, "buildAJobId")
        requireCanonicalUuid(response.buildBJobId, "buildBJobId")
        checkV2(SHA256.matches(response.officialIdentity.sha256) && response.officialIdentity.sizeBytes > 0)
        val expected = response.officialVsA == RawComparisonResult.MATCH &&
            response.officialVsB == RawComparisonResult.MATCH && response.buildAVsB == RawComparisonResult.MATCH &&
            response.trustEligible && response.installEligible
        checkV2(response.reproducible == expected && response.resourceRetryCount in 0..1)
        checkV2(runCatching { Instant.parse(response.createdAt) }.isSuccess && runCatching { Instant.parse(response.updatedAt) }.isSuccess)
    }

    private fun validateGenericResourceRetry(response: GenericResourceRetryResponse) {
        checkV2(response.schemaVersion == 1 && response.memoryBytes == 12_884_901_888L)
        requireCanonicalUuid(response.comparisonId, "comparisonId")
        requireCanonicalUuid(response.retryOfComparisonId, "retryOfComparisonId")
        requireCanonicalUuid(response.buildAJobId, "buildAJobId")
        requireCanonicalUuid(response.buildBJobId, "buildBJobId")
        checkV2(response.comparisonId != response.retryOfComparisonId && response.buildAJobId != response.buildBJobId)
    }

    private fun validateOperation(operation: V2OperationResponse) {
        requireCanonicalUuid(operation.operationId, "operationId")
        checkV2(OPERATION_KIND.matches(operation.kind) && SHA256.matches(operation.requestSha256))
        checkV2(runCatching { Instant.parse(operation.createdAt) }.isSuccess)
        checkV2(runCatching { Instant.parse(operation.updatedAt) }.isSuccess)
        when (operation.state) {
            V2OperationState.COMPLETED -> checkV2(operation.result != null && operation.reason == null)
            V2OperationState.REJECTED, V2OperationState.RECONCILIATION_REQUIRED ->
                checkV2(operation.result == null && operation.reason != null)
            V2OperationState.RESERVED, V2OperationState.APPLYING ->
                checkV2(operation.result == null && operation.reason == null)
        }
        operation.result?.let {
            checkV2(it.type in V2_RESULT_TYPES)
            requireCanonicalUuid(it.resourceId, "result.resourceId")
        }
        operation.reason?.let { checkV2(REASON_CODE.matches(it.code) && it.message.length <= 1_024) }
    }

    private fun validateStorageSummary(response: V2StorageSummaryResponse) {
        checkV2(response.schemaVersion == 1)
        requireCanonicalUuid(response.runnerId, "runnerId")
        checkV2(response.areas.map(V2StorageAreaSummary::area).toSet() == setOf("RUNNER_JOB", "RUNNER_TOOLCHAIN"))
        checkV2(response.areas.size == 2)
        response.areas.forEach { area ->
            listOf(
                area.budgetBytes,
                area.usedBytes,
                area.reservedBytes,
                area.unclassifiedBytes,
                area.usableBytes,
            ).forEach(::validateV2Bytes)
            checkV2(area.warningPercent in 0..100)
            checkV2(area.state in V2_STORAGE_STATES)
            checkV2(area.measurementState in V2_MEASUREMENT_STATES)
            checkV2(runCatching { Instant.parse(area.measuredAt) }.isSuccess)
        }
    }

    private fun validateCleanupPreview(response: V2CleanupPreviewResponse) {
        checkV2(response.schemaVersion == 1 && response.state == "PREVIEWED" && response.items.size <= 1_000)
        requireCanonicalUuid(response.previewId, "previewId")
        requireCanonicalUuid(response.runnerId, "runnerId")
        checkV2(runCatching { Instant.parse(response.expiresAt) }.isSuccess)
        val ordered = response.items.sortedWith(compareBy(V2CleanupPreviewItem::resourceKind, V2CleanupPreviewItem::resourceId, V2CleanupPreviewItem::itemId))
        checkV2(ordered == response.items && response.items.map(V2CleanupPreviewItem::itemId).distinct().size == response.items.size)
        response.items.forEach { item ->
            requireCanonicalUuid(item.itemId, "itemId")
            requireCanonicalUuid(item.resourceId, "resourceId")
            validateV2Bytes(item.observedBytes)
            checkV2(item.resourceKind in V2_CLEANUP_RESOURCE_KINDS && SHA256.matches(item.observedToken))
            checkV2(runCatching { Instant.parse(item.eligibleAt) }.isSuccess)
            checkV2(item.protectionReasons.all { it in V2_PROTECTION_REASONS })
        }
    }

    private fun validateCleanupRun(response: V2CleanupRunResponse) {
        checkV2(response.schemaVersion == 1 && response.state in V2_CLEANUP_RUN_STATES && response.items.size <= 100)
        requireCanonicalUuid(response.cleanupRunId, "cleanupRunId")
        requireCanonicalUuid(response.previewId, "previewId")
        validateV2Bytes(response.releasedBytes)
        response.startedAt?.let { checkV2(runCatching { Instant.parse(it) }.isSuccess) }
        response.finishedAt?.let { checkV2(runCatching { Instant.parse(it) }.isSuccess) }
        response.items.forEach { item ->
            requireCanonicalUuid(item.itemId, "itemId")
            checkV2(item.result in V2_CLEANUP_ITEM_RESULTS)
            validateV2Bytes(item.releasedBytes)
        }
    }

    private fun validateToolchainPlan(response: ToolchainPlanResponse) {
        checkV2(response.schemaVersion == 1 && response.platform == "linux-x86_64")
        requireCanonicalUuid(response.runnerId, "runnerId")
        checkV2(SHA256.matches(response.catalogSha256) && SHA256.matches(response.planSha256))
        validateV2Bytes(response.downloadBytes); validateV2Bytes(response.reservedBytes)
        checkV2(response.items.isNotEmpty() && response.items.size <= 16)
        checkV2(response.items.map { it.artifactId }.distinct().size == response.items.size)
        response.items.forEach { checkV2(SHA256.matches(it.archiveSha256)); validateV2Bytes(it.downloadBytes); validateV2Bytes(it.reservedBytes) }
        checkV2(response.requiredLicenses.map { it.licenseId }.distinct().size == response.requiredLicenses.size)
        response.requiredLicenses.forEach { checkV2(SHA256.matches(it.textSha256) && it.text.isNotBlank() && it.sourceUrl.startsWith("https://")) }
    }

    private fun validateToolchainInstallation(response: ToolchainInstallationResponse) {
        checkV2(response.schemaVersion == 1 && response.progressPercent in 0..100)
        requireCanonicalUuid(response.installationId, "installationId"); requireCanonicalUuid(response.operationId, "operationId"); requireCanonicalUuid(response.runnerId, "runnerId")
        checkV2(SHA256.matches(response.planSha256) && SHA256.matches(response.catalogSha256))
        checkV2(response.items.isNotEmpty() && response.items.size <= 16)
        checkV2(response.items.map { it.artifactId }.distinct().size == response.items.size)
        response.items.forEach { validateV2Bytes(it.downloadedBytes) }
        checkV2(runCatching { Instant.parse(response.createdAt) }.isSuccess && runCatching { Instant.parse(response.updatedAt) }.isSuccess)
    }

    private fun validateToolchainInventory(response: ToolchainInventoryResponse) {
        checkV2(response.schemaVersion == 1); requireCanonicalUuid(response.runnerId, "runnerId"); checkV2(SHA256.matches(response.catalogSha256))
        checkV2(response.items.size <= 128 && response.items.map { it.artifactId }.distinct().size == response.items.size)
        response.items.forEach {
            checkV2(SHA256.matches(it.archiveSha256) && SHA256.matches(it.contentManifestSha256))
            validateV2Bytes(it.installedBytes)
            checkV2(it.state in setOf("VERIFIED", "RECONCILIATION_REQUIRED") && runCatching { Instant.parse(it.installedAt) }.isSuccess)
        }
    }

    private fun validateToolchainRemovalPreview(response: ToolchainRemovalPreviewResponse) {
        checkV2(response.schemaVersion == 1)
        requireCanonicalUuid(response.previewId, "previewId")
        checkV2(response.artifactIds.isNotEmpty() && response.artifactIds.size <= 32)
        checkV2(response.artifactIds == response.artifactIds.distinct().sorted())
        validateV2Bytes(response.releasableBytes)
        checkV2(runCatching { Instant.parse(response.expiresAt) }.isSuccess)
    }

    private fun validateV2Bytes(value: String) {
        checkV2(DECIMAL.matches(value) && value.toLongOrNull() in 0..MAX_V2_BYTES)
    }

    private fun requireCanonicalUuid(value: String, field: String) {
        if (!UUID.matches(value)) throw RunnerResponseIntegrityException("Runner API v2 $field is invalid.")
    }

    private fun checkV2(condition: Boolean) {
        if (!condition) throw RunnerResponseIntegrityException("Runner API v2 response violates its contract.")
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000L
        const val REQUEST_TIMEOUT_MILLIS = 10_000L
        const val SOCKET_TIMEOUT_MILLIS = 10_000L
        const val DOWNLOAD_REQUEST_TIMEOUT_MILLIS = 30 * 60 * 1_000L
        const val DOWNLOAD_SOCKET_TIMEOUT_MILLIS = 30_000L
        const val DOWNLOAD_BUFFER_SIZE = 64 * 1_024
        const val BOUNDED_RESPONSE_BUFFER_SIZE = 64 * 1_024
        const val MAX_BUILD_MANIFEST_RESPONSE_BYTES = 8 * 1024 * 1024
        const val MAX_SOURCE_SCAN_RESPONSE_BYTES = 4 * 1024 * 1024
        const val MAX_V2_RESPONSE_BYTES = 1024 * 1024
        const val MAX_V2_BYTES = 4L * 1024 * 1024 * 1024 * 1024
        const val MIB = 1024 * 1024
        const val V2_CONTRACT_HEADER = "X-ReproDroid-Contract"
        const val V2_STORAGE_CONTRACT = "storage-retention@1"
        const val V2_TOOLCHAIN_CONTRACT = "toolchain-install@1"
        const val V2_GENERIC_BUILD_CONTRACT = "generic-build@1"
        const val V2_APK_COMPARISON_CONTRACT = "apk-comparison@1"
        const val V2_IDEMPOTENCY_HEADER = "Idempotency-Key"
        const val INTERNAL_RUNNER_BINDING_HEADER = "X-ReproDroid-Internal-Runner-Binding"
        const val DEVELOPMENT_BINDING = "local-development"
        val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val SHA256 = Regex("[0-9a-f]{64}")
        val DECIMAL = Regex("0|[1-9][0-9]*")
        val CAPABILITY_ID = Regex("[a-z0-9.-]{1,64}")
        val OPERATION_KIND = Regex("[a-z0-9.-]{1,64}")
        val REASON_CODE = Regex("[A-Z0-9_]{1,64}")
        val V2_RESULT_TYPES = setOf("RETENTION_HOLD", "STORAGE_RESERVATION", "CLEANUP_PREVIEW", "CLEANUP_RUN", "TOOLCHAIN_INSTALLATION", "TOOLCHAIN_REMOVAL")
        val V2_STORAGE_STATES = setOf("OK", "WARNING", "OVER_BUDGET", "STORAGE_UNAVAILABLE")
        val V2_MEASUREMENT_STATES = setOf("COMPLETE", "INCOMPLETE", "FAILED")
        val V2_CLEANUP_RESOURCE_KINDS = setOf("JOB_WORKSPACE", "JOB_ARTIFACT", "JOB_MANIFEST", "JOB_LOG", "SANDBOX_IMPORT")
        val V2_PROTECTION_REASONS = setOf("ACTIVE_JOB", "AWAITING_REVIEW", "SANDBOX_CLEANUP_PENDING", "RETENTION_HOLD", "ACTIVE_RESERVATION", "RESOURCE_CHANGED")
        val V2_CLEANUP_RUN_STATES = setOf("PREVIEWED", "APPLYING", "COMPLETE", "PARTIAL", "REJECTED", "RECONCILIATION_REQUIRED")
        val V2_CLEANUP_ITEM_RESULTS = setOf("DELETED", "ALREADY_MISSING", "SKIPPED_PROTECTED", "FAILED")
        // Additive Job fields remain compatible; sandbox itself is strictly checked before decoding.
        val JOB_JSON = Json { ignoreUnknownKeys = true }
        val BUILD_MANIFEST_JSON = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }
        val SOURCE_SCAN_JSON = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }
        val V2_JSON = Json {
            ignoreUnknownKeys = false
            explicitNulls = true
        }
    }
}

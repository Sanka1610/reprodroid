package com.sanka1610.reprodroid.data.provider

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol

interface ProviderRequestAuthenticator {
    fun authenticate(provider: ProviderId, request: HttpRequestBuilder)

    companion object {
        val NONE: ProviderRequestAuthenticator = object : ProviderRequestAuthenticator {
            override fun authenticate(provider: ProviderId, request: HttpRequestBuilder) = Unit
        }
    }
}

internal class StoredProviderRequestAuthenticator(
    private val credentials: ProviderCredentialSource,
) : ProviderRequestAuthenticator {
    override fun authenticate(provider: ProviderId, request: HttpRequestBuilder) {
        val target = request.url.build()
        val exactOrigin = target.protocol == URLProtocol.HTTPS && target.port == HTTPS_PORT && when (provider) {
            ProviderId.GITHUB -> target.host == GITHUB_API_HOST
            ProviderId.CODEBERG -> target.host == CODEBERG_API_HOST &&
                (target.encodedPath == CODEBERG_API_PREFIX || target.encodedPath.startsWith("$CODEBERG_API_PREFIX/"))
        }
        if (!exactOrigin) {
            throw ProviderCredentialException("Refusing to attach provider authentication outside the exact API origin.")
        }
        val token = credentials.readToken(provider) ?: return
        validateProviderToken(token)
        val scheme = when (provider) {
            ProviderId.GITHUB -> "Bearer"
            ProviderId.CODEBERG -> "token"
        }
        request.header(HttpHeaders.Authorization, "$scheme $token")
    }

    private companion object {
        const val HTTPS_PORT = 443
        const val GITHUB_API_HOST = "api.github.com"
        const val CODEBERG_API_HOST = "codeberg.org"
        const val CODEBERG_API_PREFIX = "/api/v1"
    }
}

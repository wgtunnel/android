package com.zaneschepke.wireguardautotunnel.data.network

import com.zaneschepke.wireguardautotunnel.data.entity.GitHubRelease
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class KtorGitHubApi(private val client: HttpClient) : GitHubApi {
    override suspend fun getLatestRelease(owner: String, repo: String): Result<GitHubRelease> {
        return try {
            val response: GitHubRelease =
                client.get("https://api.github.com/repos/$owner/$repo/releases/latest").body()
            Result.success(response)
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.Forbidden -> Result.failure(Exception("Rate limit exceeded"))
                HttpStatusCode.NotFound ->
                    Result.failure(Exception("Repository or release not found"))
                else -> Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getNightlyRelease(owner: String, repo: String): Result<GitHubRelease> {
        return try {
            // Only one nightly exists at a time
            val response: GitHubRelease =
                client.get("https://api.github.com/repos/$owner/$repo/releases/tags/nightly").body()
            Result.success(response)
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.Forbidden -> Result.failure(Exception("Rate limit exceeded"))
                HttpStatusCode.NotFound -> Result.failure(Exception("Nightly release not found"))
                else -> Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

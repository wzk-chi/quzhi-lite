package com.quzhi.lite.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class AppUpdate(
    val versionName: String,
    val githubUrl: String,
)

class UpdateApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
) {
    suspend fun checkForUpdate(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "quzhi-lite-update-checker")
            .get()
            .build()

        client.newCall(request).execute().use { response: Response ->
            val body = response.body?.string().orEmpty()
            if (response.code != 200) {
                throw ApiException("HTTP ${response.code}: ${response.message}")
            }
            if (body.isBlank()) {
                throw ApiException("GitHub 返回为空")
            }

            val release = gson.fromJson(body, GitHubReleaseDto::class.java)
            val latestVersion = release.tagName
                ?.removePrefix("v")
                ?.takeIf { it.isNotBlank() }
                ?: throw ApiException("GitHub 响应缺少版本号")
            val githubUrl = release.htmlUrl
                ?.takeIf { it.isNotBlank() }
                ?: throw ApiException("GitHub 响应缺少地址")

            if (compareVersions(latestVersion, currentVersion) <= 0) {
                null
            } else {
                AppUpdate(
                    versionName = latestVersion,
                    githubUrl = githubUrl,
                )
            }
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        val partCount = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until partCount) {
            val leftPart = leftParts.getOrElse(index) { 0L }
            val rightPart = rightParts.getOrElse(index) { 0L }
            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }
        return 0
    }

    private fun versionParts(version: String): List<Long> {
        return version
            .substringBefore('-')
            .split('.')
            .map { part ->
                part.toLongOrNull() ?: throw ApiException("版本号格式异常：$version")
            }
    }

    private data class GitHubReleaseDto(
        @SerializedName("tag_name")
        val tagName: String? = null,
        @SerializedName("html_url")
        val htmlUrl: String? = null,
    )

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/wzk-chi/quzhi-lite/releases/latest"
    }
}

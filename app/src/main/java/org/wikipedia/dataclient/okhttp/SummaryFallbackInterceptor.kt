package org.wikipedia.dataclient.okhttp

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.wikipedia.dataclient.mwapi.MwQueryResponse
import org.wikipedia.dataclient.page.PageSummary
import org.wikipedia.dataclient.restbase.RbDefinition
import org.wikipedia.json.JsonUtil
import org.wikipedia.util.UriUtil
import org.wikipedia.util.log.L
import java.util.concurrent.TimeUnit

/**
 * Some MediaWiki sites (e.g. Wiktionary) don't expose the RESTBase/PCS `page/summary` endpoint
 * used for link previews, feed cards, and other places that need a short blurb + thumbnail for a
 * page. When that endpoint fails, this synthesizes an equivalent [PageSummary] from the generic
 * action API (title/thumbnail/revision) plus the first dictionary definition, so the many callers
 * of `RestService.getPageSummary()`/`getSummaryResponse()` keep working unchanged.
 */
class SummaryFallbackInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful || !response.request.url.encodedPath.contains("/page/summary/")) {
            return response
        }
        val fallbackBody = buildFallbackSummary(response.request.url) ?: return response
        return response.newBuilder()
            .code(200)
            .message("OK")
            .body(fallbackBody.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private fun buildFallbackSummary(summaryUrl: HttpUrl): String? {
        return try {
            val title = UriUtil.decodeURL(summaryUrl.pathSegments.last())
            val scheme = summaryUrl.scheme
            val host = summaryUrl.host
            val langCode = host.substringBefore('.')

            val queryUrl = "$scheme://$host/w/api.php?action=query&titles=${UriUtil.encodeURL(title)}" +
                "&prop=info%7Cpageimages&piprop=thumbnail&pithumbsize=320&format=json&formatversion=2&redirects=1"
            val queryJson = executeSync(queryUrl) ?: return null
            val page = JsonUtil.decodeFromString<MwQueryResponse>(queryJson)?.query?.firstPage() ?: return null

            val definitionText = fetchFirstDefinition(scheme, host, title)

            val summary = PageSummary(
                namespace = PageSummary.NamespaceContainer(page.ns, ""),
                titles = PageSummary.Titles(page.title, page.title),
                lang = langCode,
                thumbnail = page.thumbUrl()?.let { PageSummary.Thumbnail(it, 0, 0) },
                extract = definitionText,
                description = definitionText,
                pageId = page.pageId,
                revision = page.lastrevid
            )
            JsonUtil.encodeToString(summary)
        } catch (e: Exception) {
            L.w(e)
            null
        }
    }

    private fun fetchFirstDefinition(scheme: String, host: String, title: String): String? {
        return try {
            val defUrl = "$scheme://$host/api/rest_v1/page/definition/${UriUtil.encodeURL(title)}"
            val defJson = executeSync(defUrl) ?: return null
            val usagesByLang = JsonUtil.decodeFromString<Map<String, List<RbDefinition.Usage>>>(defJson) ?: return null
            usagesByLang.values.asSequence()
                .flatten()
                .flatMap { it.definitions.asSequence() }
                .map { it.definition.replace(HTML_TAG_REGEX, "").trim() }
                .firstOrNull { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun executeSync(url: String): String? {
        FALLBACK_CLIENT.newCall(Request.Builder().url(url).build()).execute().use { rsp ->
            if (!rsp.isSuccessful) {
                return null
            }
            return rsp.body.string()
        }
    }

    companion object {
        private val HTML_TAG_REGEX = Regex("<[^>]*>")

        // Deliberately independent of OkHttpConnectionFactory.client: these fallback calls are
        // synchronous and made from inside an interceptor for that same client, so sharing its
        // dispatcher/connection pool could deadlock under concurrent load.
        private val FALLBACK_CLIENT = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

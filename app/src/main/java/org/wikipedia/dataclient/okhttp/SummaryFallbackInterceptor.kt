package org.wikipedia.dataclient.okhttp

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
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
 * page: it unconditionally returns an error there, so there's no point ever sending it. This
 * synthesizes an equivalent [PageSummary] from the generic action API (title/thumbnail/revision)
 * plus the first dictionary definition instead, without touching the network for page/summary at
 * all, so the many callers of `RestService.getPageSummary()`/`getSummaryResponse()` keep working
 * unchanged. Falls back to actually issuing the original request if synthesis fails for any reason.
 */
class SummaryFallbackInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (!path.contains("/page/summary/") && !path.endsWith("/page/random/summary")) {
            return chain.proceed(request)
        }

        val title = if (path.endsWith("/page/random/summary")) {
            resolveRandomTitle(request.url)
        } else {
            UriUtil.decodeURL(request.url.pathSegments.last())
        }

        val fallbackJson = title?.let { buildFallbackSummary(request.url, it) }
        return if (fallbackJson != null) {
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .header("Content-Type", "application/json; charset=utf-8")
                .body(fallbackJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        } else {
            // Synthesis failed (e.g. fallback network calls also unreachable) -- let the original
            // request go out as a last resort so normal error handling takes over.
            chain.proceed(request)
        }
    }

    private fun resolveRandomTitle(randomSummaryUrl: HttpUrl): String? {
        return try {
            val randomTitleUrl = "${randomSummaryUrl.scheme}://${randomSummaryUrl.host}/api/rest_v1/page/random/title"
            val req = Request.Builder().url(randomTitleUrl).build()
            NO_REDIRECT_CLIENT.newCall(req).execute().use { rsp ->
                val location = rsp.header("location") ?: return null
                UriUtil.decodeURL(location.toHttpUrlOrNull()?.pathSegments?.last().orEmpty())
            }
        } catch (e: Exception) {
            L.w(e)
            null
        }
    }

    private fun buildFallbackSummary(originalUrl: HttpUrl, title: String): String? {
        return try {
            val scheme = originalUrl.scheme
            val host = originalUrl.host
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

        private val NO_REDIRECT_CLIENT = FALLBACK_CLIENT.newBuilder()
            .followRedirects(false)
            .build()
    }
}

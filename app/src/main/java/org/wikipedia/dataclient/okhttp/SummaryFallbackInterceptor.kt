package org.wikipedia.dataclient.okhttp

import android.os.Build
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.tls.HandshakeCertificates
import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.dataclient.mwapi.MwQueryResponse
import org.wikipedia.dataclient.page.PageSummary
import org.wikipedia.dataclient.restbase.RbDefinition
import org.wikipedia.json.JsonUtil
import org.wikipedia.util.UriUtil
import org.wikipedia.util.log.L
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

/**
 * Some MediaWiki sites (e.g. Wiktionary) don't expose the RESTBase/PCS `page/summary` endpoint
 * used for link previews, feed cards, and other places that need a short blurb + thumbnail for a
 * page: it unconditionally returns an error there, so there's no point ever sending it. This
 * synthesizes an equivalent [PageSummary] from the generic action API (title/thumbnail/revision)
 * plus the first dictionary definition instead, without touching the network for page/summary at
 * all, so the many callers of `RestService.getPageSummary()`/`getSummaryResponse()` keep working
 * unchanged.
 *
 * Synthesis is layered and best-effort: it always returns at least a title-only summary (derived
 * straight from the URL, no network needed), and opportunistically enriches it with thumbnail/
 * revision/definition data. It never falls through to actually issuing the real page/summary
 * request, since that's guaranteed to fail on wikis where this interceptor is needed at all --
 * doing so would just surface a raw 404 to the user instead of a (possibly plainer) article view.
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

        // A random title lookup failing is the one case with nothing to synthesize from; fall
        // back to the real (broken) request so normal error handling still takes over.
        if (title.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val fallbackJson = buildFallbackSummary(request.url, title)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .header("Content-Type", "application/json; charset=utf-8")
            .body(fallbackJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
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
            L.e(e)
            null
        }
    }

    /** Always returns valid [PageSummary] JSON for [title]; degrades gracefully as sub-fetches fail. */
    private fun buildFallbackSummary(originalUrl: HttpUrl, title: String): String {
        val scheme = originalUrl.scheme
        val host = originalUrl.host
        val langCode = host.substringBefore('.')

        val page = fetchPageInfo(scheme, host, title)
        val definition = fetchFirstDefinition(scheme, host, title)

        val summary = PageSummary(
            namespace = page?.let { PageSummary.NamespaceContainer(it.ns, "") },
            titles = PageSummary.Titles(page?.title ?: title, page?.title ?: title),
            lang = langCode,
            thumbnail = page?.thumbUrl()?.let { PageSummary.Thumbnail(it, 0, 0) },
            extract = definition?.plainText,
            extractHtml = definition?.html,
            description = definition?.plainText,
            pageId = page?.pageId ?: 0,
            revision = page?.lastrevid ?: 0L
        )
        return JsonUtil.encodeToString(summary) ?: "{\"titles\":{\"canonical\":\"$title\",\"display\":\"$title\"},\"lang\":\"$langCode\"}"
    }

    private fun fetchPageInfo(scheme: String, host: String, title: String): org.wikipedia.dataclient.mwapi.MwQueryPage? {
        return try {
            val queryUrl = "$scheme://$host/w/api.php?action=query&titles=${UriUtil.encodeURL(title)}" +
                "&prop=info%7Cpageimages&piprop=thumbnail&pithumbsize=320&format=json&formatversion=2&redirects=1"
            val queryJson = executeSync(queryUrl) ?: return null
            JsonUtil.decodeFromString<MwQueryResponse>(queryJson)?.query?.firstPage()
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }

    private class FirstDefinition(val html: String, val plainText: String)

    /**
     * Picks the section to show a definition from the same way Wiktionary's own
     * [MediaWiki:Gadget-PagePreviews.js](https://en.wiktionary.org/wiki/MediaWiki:Gadget-PagePreviews.js)
     * does when a preview isn't anchored to a specific language section: prefer English, then
     * Chinese, then Translingual, then whichever section comes first on the page (the response
     * map preserves page order, so the first entry is the first section).
     *
     * Returns both an HTML and a plain-text rendering: [PageSummary.extractHtml] (what the link
     * preview dialog and description-edit screens actually render, via `StringUtil.fromHtml()`)
     * needs real markup, while [PageSummary.extract]/[PageSummary.description] (feed cards, search
     * results, etc.) expect plain text.
     */
    private fun fetchFirstDefinition(scheme: String, host: String, title: String): FirstDefinition? {
        return try {
            val defUrl = "$scheme://$host/api/rest_v1/page/definition/${UriUtil.encodeURL(title)}"
            val defJson = executeSync(defUrl) ?: return null
            val usagesByLang = JsonUtil.decodeFromString<Map<String, List<RbDefinition.Usage>>>(defJson) ?: return null
            val usageGroups = usagesByLang.values.filter { it.isNotEmpty() }

            val preferredGroup = PREFERRED_LANGUAGES.asSequence()
                .mapNotNull { preferred -> usageGroups.firstOrNull { group -> group.any { it.language == preferred } } }
                .firstOrNull() ?: usageGroups.firstOrNull()

            val html = preferredGroup?.asSequence()
                ?.flatMap { it.definitions.asSequence() }
                ?.map { it.definition.trim() }
                ?.firstOrNull { it.isNotEmpty() }
                ?: return null

            FirstDefinition(html, html.replace(HTML_TAG_REGEX, "").trim())
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }

    private fun executeSync(url: String): String? {
        FALLBACK_CLIENT.newCall(Request.Builder().url(url).build()).execute().use { rsp ->
            if (!rsp.isSuccessful) {
                L.e("SummaryFallbackInterceptor: $url returned HTTP ${rsp.code}")
                return null
            }
            return rsp.body.string()
        }
    }

    companion object {
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
        private val PREFERRED_LANGUAGES = listOf("English", "Chinese", "Translingual")

        // Deliberately independent of OkHttpConnectionFactory.client: these fallback calls are
        // synchronous and made from inside an interceptor for that same client, so sharing its
        // dispatcher/connection pool could deadlock under concurrent load. Mirrors its TLS setup
        // for pre-Nougat devices so these requests don't fail differently than the main client.
        private val FALLBACK_CLIENT = createFallbackClient()

        private val NO_REDIRECT_CLIENT = FALLBACK_CLIENT.newBuilder()
            .followRedirects(false)
            .build()

        private fun createFallbackClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                try {
                    val certFactory = CertificateFactory.getInstance("X.509")
                    val certificates = HandshakeCertificates.Builder()
                        .addPlatformTrustedCertificates()
                        .addTrustedCertificate(certFactory.generateCertificate(WikipediaApp.instance.resources.openRawResource(R.raw.isrg_root_x1)) as X509Certificate)
                        .addTrustedCertificate(certFactory.generateCertificate(WikipediaApp.instance.resources.openRawResource(R.raw.isrg_root_x2)) as X509Certificate)
                        .build()
                    builder.sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
                } catch (e: Exception) {
                    L.e(e)
                }
            }
            return builder.build()
        }
    }
}

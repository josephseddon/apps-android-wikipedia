package org.wikipedia.edit

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.activity.BaseActivity
import org.wikipedia.analytics.eventplatform.EditAttemptStepEvent
import org.wikipedia.auth.AccountUtil
import org.wikipedia.databinding.ActivityVisualEditorBinding
import org.wikipedia.dataclient.Service
import org.wikipedia.dataclient.SharedPreferenceCookieManager
import org.wikipedia.extensions.parcelableExtra
import org.wikipedia.login.LoginActivity
import org.wikipedia.page.PageTitle
import org.wikipedia.util.UriUtil
import org.wikipedia.util.log.L

class VisualEditorActivity : BaseActivity() {

    private lateinit var binding: ActivityVisualEditorBinding
    private lateinit var pageTitle: PageTitle
    private var sectionId: Int = SECTION_WHOLE_ARTICLE

    // True once we have confirmed the #/editor/ URL is active (editor UI is up).
    private var hasEditorLoaded = false
    // True once we have seen at least one #/editor/ URL (guards against spurious
    // "exit" signals before the editor has ever appeared).
    private var wasInEditorUrl = false
    // Set to true when a save or cancel is being handled, to prevent double-firing.
    private var saveHandled = false
    private var loadStartTime = 0L

    private val loadTimeoutRunnable = Runnable { onLoadTimeout() }
    private val timeoutHandler = Handler(Looper.getMainLooper())

    private val requestLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == LoginActivity.RESULT_LOGIN_SUCCESS) {
            loadVisualEditor()
        } else {
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisualEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pageTitle = intent.parcelableExtra(Constants.ARG_TITLE)!!
        sectionId = intent.getIntExtra(EXTRA_SECTION_ID, SECTION_WHOLE_ARTICLE)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.visual_editor_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        binding.toolbar.setNavigationOnClickListener { onCloseRequested() }

        onBackPressedDispatcher.addCallback(this) { onCloseRequested() }

        binding.errorView.retryClickListener = android.view.View.OnClickListener {
            showErrorView(false)
            loadVisualEditor()
        }

        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false
        }

        // Register the JS→Kotlin bridge before any page loads.
        binding.webView.addJavascriptInterface(VisualEditorJsInterface(), JS_INTERFACE_NAME)
        binding.webView.webViewClient = VisualEditorWebViewClient()
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean {
                MaterialAlertDialogBuilder(this@VisualEditorActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnDismissListener { result?.cancel() }
                    .show()
                return true
            }
        }

        if (AccountUtil.isLoggedIn) {
            loadVisualEditor()
        } else {
            requestLoginLauncher.launch(
                LoginActivity.newIntent(this, LoginActivity.SOURCE_EDIT)
            )
        }
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(loadTimeoutRunnable)
        binding.webView.stopLoading()
        super.onDestroy()
    }

    private fun buildVisualEditorUrl(): String {
        val sb = StringBuilder()
        sb.append("https://")
        sb.append(pageTitle.wikiSite.authority())
        sb.append("/w/index.php?title=")
        sb.append(UriUtil.encodeURL(pageTitle.prefixedText))
        sb.append("&veaction=edit")
        if (sectionId >= 0) {
            sb.append("&section=").append(sectionId)
        }
        return sb.toString()
    }

    private fun loadVisualEditor() {
        val url = buildVisualEditorUrl()
        setCookies(url)
        binding.webView.isVisible = true
        showErrorView(false)
        binding.progressBar.isVisible = true
        loadStartTime = System.currentTimeMillis()
        // Reset state so a retry starts fresh.
        hasEditorLoaded = false
        wasInEditorUrl = false
        saveHandled = false
        timeoutHandler.postDelayed(loadTimeoutRunnable, LOAD_TIMEOUT_MS)
        EditAttemptStepEvent.logInit(pageTitle, EditAttemptStepEvent.INTERFACE_VISUAL)
        binding.webView.loadUrl(url)
    }

    private fun setCookies(url: String) {
        CookieManager.getInstance().let { manager ->
            val cookies = SharedPreferenceCookieManager.instance.loadForRequest(url)
            for (cookie in cookies) {
                manager.setCookie(url, cookie.toString())
            }
        }
    }

    /**
     * Returns true when [url] represents an active Visual Editor session.
     * VE rewrites the URL via history.pushState to use a `#/editor/<section>` fragment,
     * so we must recognise both the initial `veaction=edit` query form and the pushState form.
     */
    private fun isVisualEditorUrl(url: String): Boolean {
        return url.contains("veaction=edit") ||
                url.contains("action=visualeditor") ||
                Uri.parse(url).fragment?.startsWith("/editor/") == true
    }

    private fun onLoadTimeout() {
        if (!saveHandled) {
            showErrorView(true)
        }
    }

    private fun showErrorView(show: Boolean) {
        binding.webView.isVisible = !show
        binding.errorView.isVisible = show
        if (show) {
            binding.progressBar.isVisible = false
        }
    }

    private fun onSaveDetected(revId: Long) {
        if (saveHandled) return
        saveHandled = true
        timeoutHandler.removeCallbacks(loadTimeoutRunnable)
        val latencyMs = if (loadStartTime > 0) System.currentTimeMillis() - loadStartTime else 0L
        L.d("VisualEditor save detected; revId=$revId latencyMs=$latencyMs")
        EditAttemptStepEvent.logSaveSuccess(pageTitle, EditAttemptStepEvent.INTERFACE_VISUAL)
        val data = Intent()
        data.putExtra(EXTRA_SECTION_ID, sectionId)
        data.putExtra(EXTRA_REV_ID, revId)
        setResult(EditHandler.RESULT_REFRESH_PAGE, data)
        finish()
    }

    private fun onExitWithoutSave() {
        if (saveHandled) return
        saveHandled = true
        setResult(RESULT_CANCELED)
        finish()
    }

    /**
     * Called (on the main thread) whenever the WebView URL changes via
     * history.pushState / history.replaceState.  We use the `#/editor/` fragment
     * to detect when the VE is active and when it has exited back to the article.
     */
    private fun handleUrlChange(fullUrl: String) {
        if (saveHandled) return

        val uri = Uri.parse(fullUrl)
        val fragment = uri.fragment

        if (fragment?.startsWith("/editor/") == true) {
            // The VE editor URL is now active.
            wasInEditorUrl = true
            if (!hasEditorLoaded) {
                hasEditorLoaded = true
                timeoutHandler.removeCallbacks(loadTimeoutRunnable)
                val latencyMs = if (loadStartTime > 0) System.currentTimeMillis() - loadStartTime else 0L
                L.d("VisualEditor loaded (pushState); latencyMs=$latencyMs")
            }
            return
        }

        // Fragment is gone (or is something else) — the VE has exited.
        // Ignore this if we have not seen the editor URL yet (could be an intermediate redirect).
        if (!wasInEditorUrl) return

        val veNotify = uri.getQueryParameter("venotify")
        val oldId = uri.getQueryParameter("oldid")?.toLongOrNull() ?: 0L

        if (veNotify == "saved" || oldId > 0) {
            onSaveDetected(oldId)
        } else {
            onExitWithoutSave()
        }
    }

    private fun onCloseRequested() {
        if (hasEditorLoaded && !saveHandled) {
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.edit_abandon_confirm))
                .setPositiveButton(getString(R.string.edit_abandon_confirm_yes)) { dialog, _ ->
                    dialog.dismiss()
                    EditAttemptStepEvent.logSaveFailure(pageTitle, EditAttemptStepEvent.INTERFACE_VISUAL)
                    onExitWithoutSave()
                }
                .setNegativeButton(getString(R.string.edit_abandon_confirm_no)) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    /**
     * JavaScript-to-Kotlin bridge.  The JS shim injected in [injectUrlChangeMonitor] calls
     * [onUrlChanged] via `VisualEditorBridge.onUrlChanged(url)` whenever VE changes the URL
     * through history.pushState, history.replaceState, or a popstate event.
     */
    inner class VisualEditorJsInterface {
        @JavascriptInterface
        fun onUrlChanged(url: String) {
            // The @JavascriptInterface method is called on a background thread;
            // post to main thread before touching any UI or state.
            binding.webView.post { handleUrlChange(url) }
        }
    }

    private inner class VisualEditorWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val uri = request.url
            // Block navigation outside Wikipedia/Wikimedia origins
            if (!Service.isWikimediaAuthority(uri.authority)) {
                UriUtil.visitInExternalBrowser(this@VisualEditorActivity, uri)
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            binding.progressBar.isVisible = true
            url ?: return

            // Guard: real navigations (not pushState) can also carry save/exit signals.
            val uri = Uri.parse(url)
            val veNotify = uri.getQueryParameter("venotify")
            if (veNotify == "saved") {
                val revId = uri.getQueryParameter("oldid")?.toLongOrNull() ?: 0L
                onSaveDetected(revId)
                return
            }

            if (hasEditorLoaded && !isVisualEditorUrl(url) && !saveHandled) {
                // A real navigation away from the VE (not a pushState change).
                onExitWithoutSave()
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            timeoutHandler.removeCallbacks(loadTimeoutRunnable)
            binding.progressBar.isVisible = false
            setCookies(url.orEmpty())

            if (!saveHandled) {
                // Mark the editor as loaded unconditionally: by the time onPageFinished fires,
                // VE may have already rewritten the URL to #/editor/… via pushState, so we
                // cannot rely on isVisualEditorUrl() here.
                if (!hasEditorLoaded) {
                    hasEditorLoaded = true
                    val latencyMs = if (loadStartTime > 0) System.currentTimeMillis() - loadStartTime else 0L
                    L.d("VisualEditor loaded; latencyMs=$latencyMs")
                }
                // Inject the pushState/replaceState monitor so subsequent JS-only URL changes
                // are forwarded to handleUrlChange() via the VisualEditorBridge interface.
                injectUrlChangeMonitor(view)
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame == true && !saveHandled) {
                timeoutHandler.removeCallbacks(loadTimeoutRunnable)
                showErrorView(true)
            }
        }

        private fun injectUrlChangeMonitor(view: WebView?) {
            // Language: plain ES5 to maximize compatibility with older system WebViews.
            // We guard with __veUrlMonitorInstalled so re-injection on retry is idempotent.
            val js = """
                (function() {
                    if (window.__veUrlMonitorInstalled) return;
                    window.__veUrlMonitorInstalled = true;
                    var origPush = history.pushState;
                    var origReplace = history.replaceState;
                    function notify(url) {
                        try { ${JS_INTERFACE_NAME}.onUrlChanged(url || window.location.href); } catch(e) { console.error('VisualEditorBridge error:', e); }
                    }
                    history.pushState = function(s, t, url) {
                        origPush.apply(this, arguments);
                        notify(typeof url === 'string' ? url : window.location.href);
                    };
                    history.replaceState = function(s, t, url) {
                        origReplace.apply(this, arguments);
                        notify(typeof url === 'string' ? url : window.location.href);
                    };
                    window.addEventListener('popstate', function() {
                        notify(window.location.href);
                    });
                })();
            """.trimIndent()
            view?.evaluateJavascript(js, null)
        }
    }

    companion object {
        const val EXTRA_SECTION_ID = "sectionId"
        const val EXTRA_REV_ID = "revId"
        const val SECTION_WHOLE_ARTICLE = -1
        private const val LOAD_TIMEOUT_MS = 30_000L
        private const val JS_INTERFACE_NAME = "VisualEditorBridge"

        fun newIntent(
            context: Context,
            sectionId: Int,
            title: PageTitle,
            invokeSource: Constants.InvokeSource
        ): Intent {
            return Intent(context, VisualEditorActivity::class.java)
                .putExtra(Constants.ARG_TITLE, title)
                .putExtra(EXTRA_SECTION_ID, sectionId)
                .putExtra(Constants.INTENT_EXTRA_INVOKE_SOURCE, invokeSource)
        }
    }
}

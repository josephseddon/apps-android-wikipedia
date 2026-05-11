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

    private var hasEditorLoaded = false
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

    private fun isVisualEditorUrl(url: String): Boolean {
        return url.contains("veaction=edit") || url.contains("action=visualeditor")
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

    private fun onCloseRequested() {
        if (hasEditorLoaded && !saveHandled) {
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.edit_abandon_confirm))
                .setPositiveButton(getString(R.string.edit_abandon_confirm_yes)) { dialog, _ ->
                    dialog.dismiss()
                    EditAttemptStepEvent.logSaveFailure(pageTitle, EditAttemptStepEvent.INTERFACE_VISUAL)
                    setResult(RESULT_CANCELED)
                    finish()
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

            val uri = Uri.parse(url)
            val veNotify = uri.getQueryParameter("venotify")
            if (veNotify == "saved") {
                // VE signals a successful save
                val revId = uri.getQueryParameter("oldid")?.toLongOrNull() ?: 0L
                onSaveDetected(revId)
                return
            }

            if (hasEditorLoaded && !isVisualEditorUrl(url) && !saveHandled) {
                // URL changed away from VE without a save signal: the user cancelled via VE UI
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            timeoutHandler.removeCallbacks(loadTimeoutRunnable)
            binding.progressBar.isVisible = false
            setCookies(url.orEmpty())

            if (!url.isNullOrEmpty() && isVisualEditorUrl(url) && !saveHandled) {
                hasEditorLoaded = true
                val latencyMs = if (loadStartTime > 0) System.currentTimeMillis() - loadStartTime else 0L
                L.d("VisualEditor loaded; latencyMs=$latencyMs")
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
    }

    companion object {
        const val EXTRA_SECTION_ID = "sectionId"
        const val EXTRA_SECTION_ANCHOR = "sectionAnchor"
        const val EXTRA_REV_ID = "revId"
        const val SECTION_WHOLE_ARTICLE = -1
        private const val LOAD_TIMEOUT_MS = 30_000L

        fun newIntent(
            context: Context,
            sectionId: Int,
            sectionAnchor: String?,
            title: PageTitle,
            invokeSource: Constants.InvokeSource
        ): Intent {
            return Intent(context, VisualEditorActivity::class.java)
                .putExtra(Constants.ARG_TITLE, title)
                .putExtra(EXTRA_SECTION_ID, sectionId)
                .putExtra(EXTRA_SECTION_ANCHOR, sectionAnchor)
                .putExtra(Constants.INTENT_EXTRA_INVOKE_SOURCE, invokeSource)
        }
    }
}

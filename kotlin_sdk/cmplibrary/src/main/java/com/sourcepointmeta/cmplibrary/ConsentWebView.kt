package com.sourcepointmeta.cmplibrary

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.*
import java.util.*

abstract class ConsentWebView(context: Activity?, timeoutMillisec: Long, isShowPM: Boolean) : WebView(context) {
    private val TAG = "ConsentWebView"
    private var timeoutMillisec: Long
    private var connectionPool: ConnectionPool
    private var isShowPM = false

    init {
        this.timeoutMillisec = timeoutMillisec
        connectionPool = ConnectionPool()
        this.isShowPM = isShowPM
        setup()
    }

    private inner class MessageInterface {
        // called when message is about to be shown
        @JavascriptInterface
        fun onMessageReady() {
            Log.d("onMessageReady", "called")
            this@ConsentWebView.flushOrSyncCookies()
            this@ConsentWebView.onMessageReady()
        }

        // called when a choice is selected on the message
        @JavascriptInterface
        fun onMessageChoiceSelect(choiceId: Int, choiceType: Int) {
            Log.d("onMessageChoiceSelect", "called")
            if (this@ConsentWebView.hasLostInternetConnection()) {
                this@ConsentWebView.onErrorOccurred(ConsentLibException.NoInternetConnectionException())
            }
            this@ConsentWebView.onMessageChoiceSelect(choiceId, choiceType)
        }

        //called when user takes action on privacy manager
        @JavascriptInterface
        fun onPrivacyManagerAction(pmData: String) {
            Log.d("onPrivacyManagerAction", "called")
        }

        @JavascriptInterface
        fun onMessageChoiceError(errorType: String) {
            onErrorOccurred(errorType)
            Log.d("onMessageChoiceError", "called")
        }

        // called when interaction with message is complete
        @JavascriptInterface
        fun onConsentReady(consentUUID: String, euConsent: String) {
            Log.d("onConsentReady", "called")
            this@ConsentWebView.flushOrSyncCookies()
            this@ConsentWebView.onConsentReady(euConsent, consentUUID)
        }

        //called when privacy manager cancel button is tapped
        @JavascriptInterface
        fun onPMCancel() {
            Log.d("onPMCancel", "called")
        }

        // called when message or pm need to shown
        @JavascriptInterface
        fun onSPPMObjectReady() {
            Log.d("onSPPMObjectReady", "called")
            if (isShowPM) {
                this@ConsentWebView.flushOrSyncCookies()
                this@ConsentWebView.onMessageReady()
            }
        }

        //called when an error is occured while loading web-view
        @JavascriptInterface
        fun onErrorOccurred(errorType: String) {
            val error = if (this@ConsentWebView.hasLostInternetConnection())
                ConsentLibException.NoInternetConnectionException()
            else
                ConsentLibException("Something went wrong in the javascript world.")
            this@ConsentWebView.onErrorOccurred(error)
        }

        // xhr logger
        @JavascriptInterface
        fun xhrLog(response: String) {
            Log.d("xhrLog", response)
        }

    }

    // A simple mechanism to keep track of the urls being loaded by the WebView
    private inner class ConnectionPool internal constructor() {
        private val connections: HashSet<String>
        private val INITIAL_LOAD: String? = "data:text/html,";

        init {
            connections = HashSet()
        }

        internal fun add(url: String) {
            // on API level < 21 the initial load is not recognized by the WebViewClient#onPageStarted callback
            if (url.equals(INITIAL_LOAD, ignoreCase = true)) return
            connections.add(url)
        }

        internal fun remove(url: String) {
            connections.remove(url)
        }

        internal operator fun contains(url: String): Boolean {
            return connections.contains(url)
        }
    }


    private fun doesLinkContainImage(testResult: WebView.HitTestResult): Boolean {
        return testResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
    }

    private fun getLinkUrl(testResult: WebView.HitTestResult): String? {
        if (doesLinkContainImage(testResult)) {
            val handler = Handler()
            val message = handler.obtainMessage()
            requestFocusNodeHref(message)
            return message.data.get("url") as String
        }

        return testResult.extra
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (0 != context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
                setWebContentsDebuggingEnabled(true)
                enableSlowWholeDocumentDraw()
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        settings.setAppCacheEnabled(false)
        settings.setBuiltInZoomControls(false)
        settings.setSupportZoom(false)
        settings.setJavaScriptCanOpenWindowsAutomatically(true)
        settings.setAllowFileAccess(true)
        settings.setJavaScriptEnabled(true)
        settings.setSupportMultipleWindows(true)
        settings.setDomStorageEnabled(true)
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                connectionPool.add(url)
                val run: Runnable = object : Runnable {
                    override fun run() {
                        if (connectionPool.contains(url))
                            onErrorOccurred(ConsentLibException.ApiException("TIMED OUT: $url"))
                    }
                }

                val myHandler = Handler(Looper.myLooper())
                myHandler.postDelayed(run, timeoutMillisec)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                flushOrSyncCookies()
                connectionPool.remove(url)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                Log.d(TAG, "onReceivedError: $error")
                onErrorOccurred(ConsentLibException.ApiException(error.toString()))
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.d(TAG, "onReceivedError: Error $errorCode: $description")
                onErrorOccurred(ConsentLibException.ApiException(description))
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                super.onReceivedSslError(view, handler, error)
                Log.d(TAG, "onReceivedSslError: Error $error")
                onErrorOccurred(ConsentLibException.ApiException(error.toString()))
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val message = "The WebView rendering process crashed!"
                Log.e(TAG, message)
                onErrorOccurred(ConsentLibException(message))
                return false
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                dialog: Boolean,
                userGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(getLinkUrl(view.hitTestResult)))
                view.context.startActivity(browserIntent)
                return false
            }
        }
        setOnKeyListener { view, keyCode, event ->
            val webView = view as WebView
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                KeyEvent.KEYCODE_BACK == keyCode &&
                webView.canGoBack()
            ) {
                webView.goBack()
                return@setOnKeyListener true
            }
            false
        }
        addJavascriptInterface(MessageInterface(), "JSReceiver")
        resumeTimers()
    }

    internal fun hasLostInternetConnection(): Boolean {
        val manager = getContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager ?: return true

        val activeNetwork = manager.activeNetworkInfo
        return activeNetwork == null || !activeNetwork.isConnectedOrConnecting
    }

    private fun flushOrSyncCookies() {
        // forces the cookies sync between RAM and local storage
        // https://developer.android.com/reference/android/webkit/CookieSyncManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            CookieManager.getInstance().flush()
        else
            CookieSyncManager.getInstance().sync()
    }

    protected override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flushOrSyncCookies()
    }

    abstract fun onMessageReady()

    abstract fun onErrorOccurred(error: ConsentLibException)

    abstract fun onConsentReady(euConsent: String, consentUUID: String)

    abstract fun onMessageChoiceSelect(choiceType: Int, choiceId: Int)

    @Throws(ConsentLibException.NoInternetConnectionException::class)
    fun loadMessage(messageUrl: String) {
        if (hasLostInternetConnection())
            throw ConsentLibException.NoInternetConnectionException()

        // On API level >= 21, the JavascriptInterface is not injected on the page until the *second* page load
        // so we need to issue blank load with loadData
        loadData("", "text/html", null)
        Log.d(TAG, "Loading Webview with: $messageUrl")
        Log.d(TAG, "User-Agent: " + getSettings().getUserAgentString())
        loadUrl(messageUrl)
    }

    fun display() {
        layoutParams = ViewGroup.LayoutParams(0, 0)
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        bringToFront()
        requestLayout()
    }
}
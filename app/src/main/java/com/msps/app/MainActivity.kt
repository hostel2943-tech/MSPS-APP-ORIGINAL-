package com.msps.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.view.ViewGroup
import android.widget.Toast
import android.print.PrintManager
import android.print.PrintAttributes

class MainActivity : Activity() {

    private lateinit var webView: WebView

    private var fileChooserCallback:
        ValueCallback<Array<Uri>>? = null

    private var pendingBackupBytes: ByteArray? = null

    companion object {
        private const val FILE_PICKER = 4101
        private const val BACKUP_SAVER = 4102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent content from overlapping system bars
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true)
        }

        window.statusBarColor = Color.rgb(16, 36, 58)
        window.navigationBarColor = Color.rgb(16, 36, 58)

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        webView = WebView(this)

        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.addJavascriptInterface(
            MSPSBridge(),
            "AMUAndroidPrint"
        )

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val uri = request.url

                return if (
                    uri.scheme == "http" ||
                    uri.scheme == "https"
                ) {
                    val host = uri.host ?: ""

                    if (
                        host == "phhwrfqlswvhfsdymnin.supabase.co" ||
                        host == "cdn.jsdelivr.net"
                    ) {
                        false
                    } else {
                        try {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, uri)
                            )
                        } catch (_: Exception) {}
                        true
                    }
                } else {
                    false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: WebChromeClient.FileChooserParams?
            ): Boolean {

                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback

                val intent = Intent(
                    Intent.ACTION_OPEN_DOCUMENT
                ).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf(
                            "application/json",
                            "text/json",
                            "application/octet-stream",
                            "text/plain"
                        )
                    )
                }

                return try {
                    startActivityForResult(intent, FILE_PICKER)
                    true
                } catch (_: Exception) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    false
                }
            }
        }

        setContentView(webView)

        webView.loadUrl(
            "file:///android_asset/index.html"
        )
    }

    inner class MSPSBridge {

        @JavascriptInterface
        fun saveBackup(base64: String, filename: String) {
            runOnUiThread {
                try {
                    pendingBackupBytes = Base64.decode(
                        base64,
                        Base64.DEFAULT
                    )

                    val safeName = filename
                        .substringAfterLast("/")
                        .substringAfterLast("\\")
                        .ifBlank { "MSPS_Backup.json" }

                    val intent = Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                    ).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, safeName)
                    }

                    startActivityForResult(intent, BACKUP_SAVER)

                } catch (_: Exception) {
                    webView.evaluateJavascript(
                        "window.__mspsBackupSaved && window.__mspsBackupSaved(false)",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun print() {
            runOnUiThread {
                try {
                    val manager = getSystemService(
                        PRINT_SERVICE
                    ) as PrintManager

                    manager.print(
                        "MSPS Report",
                        webView.createPrintDocumentAdapter("MSPS Report"),
                        PrintAttributes.Builder().build()
                    )
                } catch (_: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Printing unavailable",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    @Deprecated("Android compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_PICKER) {
            val result =
                if (resultCode == RESULT_OK && data?.data != null) {
                    arrayOf(data.data!!)
                } else null

            fileChooserCallback?.onReceiveValue(result)
            fileChooserCallback = null

        } else if (requestCode == BACKUP_SAVER) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            val bytes = pendingBackupBytes

            if (uri != null && bytes != null) {
                val saved = try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(bytes)
                    }
                    true
                } catch (_: Exception) {
                    false
                }

                webView.evaluateJavascript(
                    "window.__mspsBackupSaved && window.__mspsBackupSaved($saved)",
                    null
                )
            } else {
                webView.evaluateJavascript(
                    "window.__mspsBackupSaved && window.__mspsBackupSaved(false)",
                    null
                )
            }

            pendingBackupBytes = null
        }
    }

    @Deprecated("Android compatibility")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AMUAndroidPrint")
            webView.destroy()
        }
        super.onDestroy()
    }
}

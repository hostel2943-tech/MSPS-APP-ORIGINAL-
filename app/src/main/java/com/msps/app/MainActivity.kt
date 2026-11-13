package com.msps.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import android.print.PrintAttributes
import android.print.PrintManager

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout

    private var fileChooserCallback:
        ValueCallback<Array<Uri>>? = null

    private var pendingBackupBytes: ByteArray? = null

    companion object {
        private const val FILE_PICKER = 4101
        private const val BACKUP_SAVER = 4102

        private const val BLUE = 0xFF10243A.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clear fullscreen and no-limit flags
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Respect Android system bars on modern Android
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        }

        // Keep system bars visible
        window.statusBarColor = BLUE
        window.navigationBarColor = BLUE

        // White status bar icons
        window.decorView.systemUiVisibility = 0

        // Do not draw into the camera cutout
        if (Build.VERSION.SDK_INT >= 28) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            window.attributes = params
        }

        // Create parent layout
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(BLUE)
            clipToPadding = true
            clipChildren = true
        }

        // Create WebView
        webView = WebView(this)

        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Website settings
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        // JavaScript bridge
        webView.addJavascriptInterface(
            MSPSBridge(),
            "AMUAndroidPrint"
        )

        // Website navigation
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val uri = request.url

                if (
                    uri.scheme == "http" ||
                    uri.scheme == "https"
                ) {

                    val host = uri.host ?: ""

                    if (
                        host == "phhwrfqlswvhfsdymnin.supabase.co" ||
                        host == "cdn.jsdelivr.net"
                    ) {
                        return false
                    }

                    try {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, uri)
                        )
                    } catch (_: Exception) {
                    }

                    return true
                }

                return false
            }
        }

        // Native file selection
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

                    startActivityForResult(
                        intent,
                        FILE_PICKER
                    )

                    true

                } catch (_: Exception) {

                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    false
                }
            }
        }

        // Add WebView inside parent layout
        rootLayout.addView(webView)

        // Set the parent layout as the activity content
        setContentView(rootLayout)

        // IMPORTANT:
        // Apply system bar and camera cutout insets
        // to the PARENT layout, not the WebView.

        if (Build.VERSION.SDK_INT >= 30) {

            rootLayout.setOnApplyWindowInsetsListener {
                view, insets ->

                val safeInsets = insets.getInsets(
                    WindowInsets.Type.systemBars() or
                    WindowInsets.Type.displayCutout()
                )

                view.setPadding(
                    safeInsets.left,
                    safeInsets.top,
                    safeInsets.right,
                    safeInsets.bottom
                )

                insets
            }

            rootLayout.requestApplyInsets()
        }

        // Load original MSPS website
        webView.loadUrl(
            "file:///android_asset/index.html"
        )
    }

    // JavaScript bridge
    inner class MSPSBridge {

        @JavascriptInterface
        fun saveBackup(
            base64: String,
            filename: String
        ) {

            runOnUiThread {

                try {

                    pendingBackupBytes = Base64.decode(
                        base64,
                        Base64.DEFAULT
                    )

                    val safeName = filename
                        .substringAfterLast("/")
                        .substringAfterLast("\\")
                        .ifBlank {
                            "MSPS_Backup.json"
                        }

                    val intent = Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                    ).apply {

                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"

                        putExtra(
                            Intent.EXTRA_TITLE,
                            safeName
                        )
                    }

                    startActivityForResult(
                        intent,
                        BACKUP_SAVER
                    )

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
                        webView.createPrintDocumentAdapter(
                            "MSPS Report"
                        ),
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

    // File picker and backup saving
    @Deprecated("Android compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == FILE_PICKER) {

            val result =
                if (
                    resultCode == Activity.RESULT_OK &&
                    data?.data != null
                ) {
                    arrayOf(data.data!!)
                } else {
                    null
                }

            fileChooserCallback?.onReceiveValue(result)
            fileChooserCallback = null

        } else if (requestCode == BACKUP_SAVER) {

            val uri =
                if (resultCode == Activity.RESULT_OK) {
                    data?.data
                } else {
                    null
                }

            val bytes = pendingBackupBytes

            if (uri != null && bytes != null) {

                val saved = try {

                    contentResolver
                        .openOutputStream(uri)
                        ?.use {
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

    // Android back button
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

            webView.removeJavascriptInterface(
                "AMUAndroidPrint"
            )

            webView.destroy()
        }

        super.onDestroy()
    }
}

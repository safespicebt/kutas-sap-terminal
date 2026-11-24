package com.kutas.sapterminal

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutError: LinearLayout
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: MaterialButton
    private lateinit var btnChangeUrl: MaterialButton
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var btnBack: Button
    private lateinit var btnList: Button
    private lateinit var btnLogoff: Button
    private lateinit var btnReload: Button
    private lateinit var btnEnter: Button

    companion object {
        const val PREFS_NAME = "KutasSapPrefs"
        const val KEY_URL = "server_url"
        const val DEFAULT_URL = "http://10.65.12.66:8000/sap/bc/gui/sap/its/zkut2"

        const val DATAWEDGE_INTENT_ACTION = "com.kutas.sapterminal.SCAN"
        const val DATAWEDGE_EXTRA_DATA = "com.symbol.datawedge.data_string"
    }

    private val barcodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DATAWEDGE_INTENT_ACTION) {
                val scannedBarcode = intent.getStringExtra(DATAWEDGE_EXTRA_DATA)
                if (!scannedBarcode.isNullOrEmpty()) {
                    injectBarcodeToSap(scannedBarcode.trim())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableFullScreen()

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initViews()
        setupWebView()
        setupListeners()
        setupKeypadListeners()
        setupBackPressHandler()

        loadSapUrl()
    }

    override fun onResume() {
        super.onResume()
        enableFullScreen()
        registerBarcodeReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterBarcodeReceiver()
    }

    private fun enableFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        layoutError = findViewById(R.id.layoutError)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnRetry = findViewById(R.id.btnRetry)
        btnChangeUrl = findViewById(R.id.btnChangeUrl)

        btnBack = findViewById(R.id.btnBack)
        btnList = findViewById(R.id.btnList)
        btnLogoff = findViewById(R.id.btnLogoff)
        btnReload = findViewById(R.id.btnReload)
        btnEnter = findViewById(R.id.btnEnter)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.textZoom = 130
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                layoutError.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                cookieManager.flush()

                injectSapMobileResponsiveStyles()
                checkAndHandleLogoff(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    layoutError.visibility = View.VISIBLE
                    tvErrorMessage.text = "Sunucuya bağlanılamadı (${getCurrentUrl()}).\nLütfen Wi-Fi bağlantısını kontrol edin."
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress > 60) {
                    injectSapMobileResponsiveStyles()
                }
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun checkAndHandleLogoff(url: String?) {
        if (url != null && (url.contains("logoff", ignoreCase = true) ||
                    url.contains("logout", ignoreCase = true) ||
                    url.contains("exit", ignoreCase = true))) {
            resetSessionAndGoToLogin()
            return
        }

        val checkJs = """
            (function() {
                var text = document.body ? document.body.innerText.toLowerCase() : '';
                return text.includes('logoff') || 
                       text.includes('sistemden çıktınız') || 
                       text.includes('oturum kapatıldı') || 
                       text.includes('oturum sonlandırıldı');
            })();
        """.trimIndent()

        webView.evaluateJavascript(checkJs) { result ->
            if (result == "true") {
                resetSessionAndGoToLogin()
            }
        }
    }

    private fun resetSessionAndGoToLogin() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            webView.clearCache(true)
            webView.clearHistory()
            loadSapUrl()
            Toast.makeText(this, "Giriş ekranına dönüldü", Toast.LENGTH_SHORT).show()
        }
    }

    private fun injectSapMobileResponsiveStyles() {
        val js = """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                    document.head.appendChild(meta);
                }

                var style = document.getElementById('kutas-mobile-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'kutas-mobile-style';
                    style.innerHTML = `
                        html, body {
                            width: 100% !important;
                            max-width: 100vw !important;
                            margin: 0 !important;
                            padding: 4px !important;
                            font-size: 16px !important;
                        }
                        table {
                            width: 100% !important;
                            max-width: 100% !important;
                        }
                        input, select, textarea, button {
                            font-size: 16px !important;
                            box-sizing: border-box !important;
                        }
                        input[type="text"], input[type="password"] {
                            height: 42px !important;
                            line-height: 42px !important;
                            border: 1px solid #78909C !important;
                            border-radius: 4px !important;
                            padding: 4px 8px !important;
                            width: 100% !important;
                        }
                        input[type="submit"], input[type="button"], button {
                            min-height: 42px !important;
                            padding: 6px 14px !important;
                            border-radius: 4px !important;
                            font-weight: bold !important;
                        }
                    `;
                    document.head.appendChild(style);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun setupListeners() {
        swipeRefresh.setOnRefreshListener { webView.reload() }
        btnRetry.setOnClickListener {
            layoutError.visibility = View.GONE
            loadSapUrl()
        }
        btnChangeUrl.setOnClickListener { showUrlConfigDialog() }
    }

    private fun setupKeypadListeners() {
        // 🔴 GERİ (F3)
        btnBack.setOnClickListener {
            sendSapKeyOrOkCode(114, "/3")
        }

        // 🟡 LİSTE (F4)
        btnList.setOnClickListener {
            sendSapKeyOrOkCode(115, "/4")
        }

        // 🔘 ÇIKIŞ
        btnLogoff.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Oturumu Kapat")
                .setMessage("SAP oturumunu kapatıp giriş ekranına dönmek istiyor musunuz?")
                .setPositiveButton("Evet, Çıkış Yap") { _, _ ->
                    sendSapKeyOrOkCode(123, "/nex")
                    resetSessionAndGoToLogin()
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        // 🔵 YENİLE
        btnReload.setOnClickListener {
            webView.reload()
        }
        btnReload.setOnLongClickListener {
            showUrlConfigDialog()
            true
        }

        // 🟢 ONAY (ENTER)
        btnEnter.setOnClickListener {
            sendSapKeyOrOkCode(13, "/0")
        }
    }

    private fun sendSapKeyOrOkCode(keyCode: Int, okCode: String) {
        val js = """
            (function() {
                if (typeof setOkCode === 'function') {
                    setOkCode('$okCode');
                    return;
                }
                var okInput = document.querySelector('input[name="~OkCode"]') || document.querySelector('input[name="okcode"]');
                if (okInput) {
                    okInput.value = '$okCode';
                    if (okInput.form) {
                        okInput.form.submit();
                        return;
                    }
                }
                var target = document.activeElement || document.body;
                var evt = new KeyboardEvent('keydown', {
                    key: '$okCode',
                    keyCode: $keyCode,
                    which: $keyCode,
                    bubbles: true,
                    cancelable: true
                });
                target.dispatchEvent(evt);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectBarcodeToSap(barcode: String) {
        val escapedBarcode = barcode.replace("'", "\\'")
        val js = """
            (function() {
                var active = document.activeElement;
                if (!active || (active.tagName !== 'INPUT' && active.tagName !== 'TEXTAREA')) {
                    var inputs = document.querySelectorAll('input[type="text"], input:not([type])');
                    for (var i = 0; i < inputs.length; i++) {
                        if (inputs[i].offsetParent !== null && !inputs[i].disabled && !inputs[i].readOnly) {
                            active = inputs[i];
                            active.focus();
                            break;
                        }
                    }
                }
                if (active) {
                    active.value = '$escapedBarcode';
                    active.dispatchEvent(new Event('input', { bubbles: true }));
                    active.dispatchEvent(new Event('change', { bubbles: true }));
                    
                    var enterDown = new KeyboardEvent('keydown', {
                        key: 'Enter',
                        code: 'Enter',
                        keyCode: 13,
                        which: 13,
                        bubbles: true,
                        cancelable: true
                    });
                    active.dispatchEvent(enterDown);
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
        Toast.makeText(this, "Okundu: $barcode", Toast.LENGTH_SHORT).show()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sendSapKeyOrOkCode(114, "/3")
            }
        })
    }

    private fun showUrlConfigDialog() {
        val input = EditText(this)
        input.setText(getCurrentUrl())
        input.setSelection(input.text.length)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.server_url))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    sharedPrefs.edit().putString(KEY_URL, newUrl).apply()
                    loadSapUrl()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun getCurrentUrl(): String {
        return sharedPrefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    private fun loadSapUrl() {
        webView.loadUrl(getCurrentUrl())
    }

    private fun registerBarcodeReceiver() {
        val filter = IntentFilter(DATAWEDGE_INTENT_ACTION)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        ContextCompat.registerReceiver(
            this,
            barcodeReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun unregisterBarcodeReceiver() {
        try {
            unregisterReceiver(barcodeReceiver)
        } catch (e: Exception) {
            // Yut
        }
    }
}

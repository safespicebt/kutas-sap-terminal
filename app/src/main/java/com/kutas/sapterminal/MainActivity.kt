package com.kutas.sapterminal

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var btnQuickReload: ImageButton
    private lateinit var sharedPrefs: SharedPreferences

    // SAP Alt Fonksiyon Tuşları (Velocity Tarzı)
    private lateinit var btnF1: Button
    private lateinit var btnF2: Button
    private lateinit var btnF3: Button
    private lateinit var btnF4: Button
    private lateinit var btnEnter: Button

    companion object {
        const val PREFS_NAME = "KutasSapPrefs"
        const val KEY_URL = "server_url"
        const val DEFAULT_URL = "http://10.65.12.66:8000/sap/bc/gui/sap/its/zkut2"

        // Zebra DataWedge Broadcast Intent
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

        // TC21 / TC26'da ekranın uykuya geçmesini engelle
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableFullScreen()

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initViews()
        setupWebView()
        setupListeners()
        setupSapKeypadListeners()
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
        btnQuickReload = findViewById(R.id.btnQuickReload)

        btnF1 = findViewById(R.id.btnF1)
        btnF2 = findViewById(R.id.btnF2)
        btnF3 = findViewById(R.id.btnF3)
        btnF4 = findViewById(R.id.btnF4)
        btnEnter = findViewById(R.id.btnEnter)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
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
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun setupListeners() {
        swipeRefresh.setOnRefreshListener { webView.reload() }
        btnRetry.setOnClickListener {
            layoutError.visibility = View.GONE
            loadSapUrl()
        }
        btnChangeUrl.setOnClickListener { showUrlConfigDialog() }
        btnQuickReload.setOnClickListener { webView.reload() }
        btnQuickReload.setOnLongClickListener {
            showUrlConfigDialog()
            true
        }
    }

    /**
     * Velocity tarzı SAP Alt Fonksiyon Tuşları
     */
    private fun setupSapKeypadListeners() {
        // F1 (Yardım / Help)
        btnF1.setOnClickListener { sendSapKeyOrOkCode(112, "/1") }

        // F2
        btnF2.setOnClickListener { sendSapKeyOrOkCode(113, "/2") }

        // F3 (GERİ - SAP'de bir önceki menüye çıkış)
        btnF3.setOnClickListener { sendSapKeyOrOkCode(114, "/3") }

        // F4 (Liste / Arama Yardımı)
        btnF4.setOnClickListener { sendSapKeyOrOkCode(115, "/4") }

        // ENTER (Onay / Gönder)
        btnEnter.setOnClickListener { sendSapKeyOrOkCode(13, "/0") }
    }

    /**
     * SAP ITSmobile'a Fonksiyon Tuşu veya OkCode Gönderme
     */
    private fun sendSapKeyOrOkCode(keyCode: Int, okCode: String) {
        val js = """
            (function() {
                // 1. Standart ITSmobile OkCode fonksiyonu varsa çağır
                if (typeof setOkCode === 'function') {
                    setOkCode('$okCode');
                    return;
                }
                // 2. OkCode input alanını bul ve gönder
                var okInput = document.querySelector('input[name="~OkCode"]') || document.querySelector('input[name="okcode"]');
                if (okInput) {
                    okInput.value = '$okCode';
                    if (okInput.form) {
                        okInput.form.submit();
                        return;
                    }
                }
                // 3. Klavye Olayını Simüle Et
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
                    
                    if (active.form) {
                        // Eğer ITSmobile'da doğrudan form submit varsa
                    }
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
        Toast.makeText(this, "Okundu: $barcode", Toast.LENGTH_SHORT).show()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Geri tuşuna basıldığında SAP'de F3 (Geri) gönder veya çıkış sor
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    showExitDialog()
                }
            }
        })
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("KUTAŞ SAP Terminal")
            .setMessage(getString(R.string.exit_confirm))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> finish() }
            .setNegativeButton(getString(R.string.no), null)
            .show()
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
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            barcodeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
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

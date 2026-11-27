package com.kutas.sapterminal

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutError: LinearLayout
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: MaterialButton
    private lateinit var btnChangeUrl: MaterialButton
    private lateinit var sharedPrefs: SharedPreferences

    // Üst Bar Butonları ve Sürüm Göstergesi
    private var tvAppVersion: TextView? = null
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnEnvToggle: Button

    // Renkli ve İsimli Alt Bar Butonları
    private lateinit var btnBack: Button
    private lateinit var btnList: Button
    private lateinit var btnLogoff: Button
    private lateinit var btnReload: Button
    private lateinit var btnEnter: Button

    companion object {
        const val PREFS_NAME = "KutasSapPrefs"
        const val KEY_SELECTED_ENV = "selected_env" // "CANLI", "TEST" veya "TGT"
        const val KEY_URL_CANLI = "url_canli"
        const val KEY_URL_TEST = "url_test"
        const val KEY_URL_TGT = "url_tgt"

        const val DEFAULT_URL_CANLI = "http://10.65.12.66:8000/sap/bc/gui/sap/its/zkut2"
        const val DEFAULT_URL_TEST = "http://10.65.12.67:8000/sap/bc/gui/sap/its/zkut2"
        const val DEFAULT_URL_TGT = "http://10.65.12.66:8000/sap/bc/gui/sap/its/zkut/?sap-client=100"

        // Doğrudan GitHub En Son APK İndirme Bağlantısı
        const val UPDATE_APK_URL = "https://github.com/safespicebt/kutas-sap-terminal/releases/latest/download/app-debug.apk"

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
        setupKeypadListeners()
        setupBackPressHandler()

        updateEnvButtonUi()
        loadSapUrl()
    }

    override fun onResume() {
        super.onResume()
        enableFullScreen()
        registerBarcodeReceiver()
        hideKeyboard()
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

    /**
     * TC21 Ekranını Kaplayan Sanal Klavyeyi Anında Kapatır
     */
    private fun hideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val v = currentFocus ?: window?.decorView
            val token = v?.windowToken
            if (imm != null && token != null) {
                imm.hideSoftInputFromWindow(token, 0)
            }
        } catch (e: Exception) {
            // Yut
        }
    }

    /**
     * Uygulamanın Yüklü Olduğu Sürüm Numarasını Getirir (Örn: 1.0.26)
     */
    private fun getAppVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        layoutError = findViewById(R.id.layoutError)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnRetry = findViewById(R.id.btnRetry)
        btnChangeUrl = findViewById(R.id.btnChangeUrl)

        tvAppVersion = findViewById(R.id.tvAppVersion)
        tvAppVersion?.text = "v${getAppVersionName()}"

        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnEnvToggle = findViewById(R.id.btnEnvToggle)

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

        // SAP ITSmobile'ı mobil ekran genişliğine tam oturt
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.textZoom = 100 // Standart dengeli yazı ölçeği
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
                hideKeyboard()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                cookieManager.flush()

                // Sayfayı ekrana tam yay ve butonları/kutuları büyüt
                injectSapMobileResponsiveStyles()

                // SAP Çıkış Kontrolü: Çıkış yapıldıysa otomatik giriş ekranına dön
                checkAndHandleLogoff(url)

                hideKeyboard()
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
                    tvErrorMessage.text = "Sunucuya bağlanılamadı (${getCurrentUrl()}).\nLütfen Wi-Fi ve yerel ağ bağlantısını kontrol edin."
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

    /**
     * SAP Çıkış Ekranını Algılar ve Otomatik Olarak Giriş Sayfasını Yükler
     */
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

    /**
     * Oturumu sıfırlar ve doğrudan temiz kullanıcı giriş ekranını açar
     */
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

    /**
     * SAP ITSmobile ekranını mobil terminal ekranına tam yayar
     */
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
                            padding: 6px 10px !important;
                            box-sizing: border-box !important;
                            font-size: 14px !important;
                        }
                        div, form, table {
                            max-width: 100% !important;
                            box-sizing: border-box !important;
                        }
                        table {
                            width: 100% !important;
                        }
                        input, select, textarea, button {
                            font-size: 14px !important;
                            box-sizing: border-box !important;
                            max-width: 100% !important;
                        }
                        input[type="text"], input[type="password"], input:not([type]), select, textarea {
                            display: block !important;
                            height: 40px !important;
                            line-height: 40px !important;
                            border: 1px solid #78909C !important;
                            border-radius: 4px !important;
                            padding: 4px 10px !important;
                            margin-top: 4px !important;
                            margin-bottom: 6px !important;
                            width: 100% !important;
                        }
                        input[type="submit"], input[type="button"], button {
                            width: auto !important;
                            min-width: 130px !important;
                            max-width: 100% !important;
                            min-height: 42px !important;
                            height: auto !important;
                            padding: 8px 14px !important;
                            white-space: normal !important;
                            overflow: visible !important;
                            border-radius: 6px !important;
                            font-weight: bold !important;
                        }
                    `;
                    document.head.appendChild(style);
                }

                // Etiket (Örn: Terminal No) üstte, doldurulacak kutu hemen altında tam genişlikte olsun
                var rows = document.querySelectorAll('tr');
                for (var r = 0; r < rows.length; r++) {
                    var tr = rows[r];
                    if (tr.dataset && tr.dataset.kutasStacked) continue;
                    var hasEditable = tr.querySelector('input[type="text"], input[type="password"], input:not([type]), select, textarea');
                    if (hasEditable && tr.cells && tr.cells.length >= 2) {
                        tr.style.display = 'block';
                        tr.style.width = '100%';
                        tr.style.marginBottom = '6px';
                        for (var c = 0; c < tr.cells.length; c++) {
                            var cell = tr.cells[c];
                            cell.style.display = 'block';
                            cell.style.width = '100%';
                            cell.style.textAlign = 'left';
                            cell.style.padding = '2px 0';
                            if (!cell.querySelector('input, select, textarea, button')) {
                                cell.style.fontWeight = '600';
                            }
                        }
                        if (tr.dataset) tr.dataset.kutasStacked = '1';
                    }
                }

                // Otomatik Ekle Dinleyicisi (Barkod lazeri Enter bastığında ekranda Ekle varsa ona tıklar)
                if (!window.kutasAutoEkleHook) {
                    window.kutasAutoEkleHook = true;
                    document.addEventListener('keydown', function(e) {
                        if (e.keyCode === 13 || e.key === 'Enter') {
                            var buttons = document.querySelectorAll('input[type="button"], input[type="submit"], button, a.MobileButton, a');
                            for (var i = 0; i < buttons.length; i++) {
                                var b = buttons[i];
                                var text = (b.value || b.innerText || b.getAttribute('title') || '').trim().toLowerCase();
                                if (text === 'ekle' || text.indexOf('ekle') !== -1 || text === 'add') {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    if (document.activeElement && typeof document.activeElement.blur === 'function') {
                                        document.activeElement.blur();
                                    }
                                    setTimeout(function() { b.click(); }, 60);
                                    return false;
                                }
                            }
                        }
                    }, true);
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

        // Ortam Değiştirme Butonu (CANLI / TEST)
        btnEnvToggle.setOnClickListener {
            hideKeyboard()
            showEnvironmentSwitchDialog()
        }

        // Otomatik Güncelleme Butonu (GitHub'dan Çeker)
        btnCheckUpdate.setOnClickListener {
            hideKeyboard()
            confirmAndStartUpdate()
        }
    }

    /**
     * Güncelleme Onay Penceresi ve İndirme Başlatma
     */
    private fun confirmAndStartUpdate() {
        AlertDialog.Builder(this)
            .setTitle("Uygulamayı Güncelle")
            .setMessage("Mevcut Sürüm: v${getAppVersionName()}\n\nGitHub üzerindeki en son sürüm otomatik olarak indirilip kurulacak.\n\nDevam etmek istiyor musunuz?")
            .setPositiveButton("Evet, Güncelle") { _, _ ->
                checkPermissionsAndDownload()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun checkPermissionsAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(this)
                    .setTitle("Yükleme İzni Gerekli")
                    .setMessage("Uygulamanın kendini güncelleyebilmesi için 'Bilinmeyen uygulamaları yükle' iznini açmanız gerekmektedir.")
                    .setPositiveButton("Ayarlara Git") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("İptal", null)
                    .show()
                return
            }
        }
        downloadAndInstallUpdate()
    }

    /**
     * GitHub'dan Doğrudan APK İndirir ve Kurulumu Başlatır
     */
    private fun downloadAndInstallUpdate() {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Güncelleme İndiriliyor")
            setMessage("GitHub'dan en yeni APK indiriliyor, lütfen bekleyin...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            max = 100
            setCancelable(false)
            show()
        }

        thread {
            try {
                var currentUrl = UPDATE_APK_URL
                var connection: HttpURLConnection
                var redirectCount = 0

                // GitHub Releases indirme bağlantısı 302 yönlendirmesi yapar, hedef AWS/CDN URL'ini takip et
                while (true) {
                    val url = URL(currentUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.connect()

                    val responseCode = connection.responseCode
                    if ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                                responseCode == 307 || responseCode == 308) && redirectCount < 6) {
                        currentUrl = connection.getHeaderField("Location")
                        connection.disconnect()
                        redirectCount++
                    } else {
                        break
                    }
                }

                val fileLength = connection.contentLength
                val updateDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val apkFile = File(updateDir, "KutasSapTerminal_update.apk")
                if (apkFile.exists()) apkFile.delete()

                val input = BufferedInputStream(connection.inputStream)
                val output = FileOutputStream(apkFile)

                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        val progress = ((total * 100) / fileLength).toInt()
                        runOnUiThread { progressDialog.progress = progress }
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()
                connection.disconnect()

                runOnUiThread {
                    progressDialog.dismiss()
                    launchApkInstaller(apkFile)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("Güncelleme İndirilemedi")
                        .setMessage("İndirme sırasında bir hata oluştu: ${e.message}\n\nTarayıcı üzerinden indirmek ister misiniz?")
                        .setPositiveButton("Tarayıcıda Aç") { _, _ ->
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_APK_URL))
                            startActivity(browserIntent)
                        }
                        .setNegativeButton("Kapat", null)
                        .show()
                }
            }
        }
    }

    /**
     * İndirilen APK Dosyasını FileProvider Üzerinden Güvenle Yükletir
     */
    private fun launchApkInstaller(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Yükleme başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * CANLI / TEST / TGT Ortam Seçim Dialogu
     */
    private fun showEnvironmentSwitchDialog() {
        val currentEnv = getSelectedEnv()
        val options = arrayOf(
            "🟢 Canlı Ortam (KTP - zkut2)",
            "🟡 Test Ortamı (KTT - 10.65.12.67)",
            "🔵 TGT (Turgutlu - zkut)",
            "⚙️ Sunucu Adreslerini Düzenle"
        )
        val selectedIndex = when (currentEnv) {
            "TEST" -> 1
            "TGT" -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("SAP Ortamı Seçin")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> switchEnvironment("CANLI")
                    1 -> switchEnvironment("TEST")
                    2 -> switchEnvironment("TGT")
                    3 -> showUrlConfigDialog()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun switchEnvironment(env: String) {
        if (getSelectedEnv() == env) {
            Toast.makeText(this, "Zaten $env ortamındasınız.", Toast.LENGTH_SHORT).show()
            return
        }
        sharedPrefs.edit().putString(KEY_SELECTED_ENV, env).apply()
        updateEnvButtonUi()
        resetSessionAndGoToLogin()
        Toast.makeText(this, "$env ortamına geçildi", Toast.LENGTH_SHORT).show()
    }

    private fun getSelectedEnv(): String {
        return sharedPrefs.getString(KEY_SELECTED_ENV, "CANLI") ?: "CANLI"
    }

    private fun updateEnvButtonUi() {
        when (getSelectedEnv()) {
            "TEST" -> {
                btnEnvToggle.text = "🟡 TEST"
                btnEnvToggle.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_orange_dark)
            }
            "TGT" -> {
                btnEnvToggle.text = "🔵 TGT"
                btnEnvToggle.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_blue_dark)
            }
            else -> {
                btnEnvToggle.text = "🟢 CANLI"
                btnEnvToggle.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            }
        }
    }

    /**
     * Renkli ve İsimli Alt Tuşların Dinleyicileri
     */
    private fun setupKeypadListeners() {
        // 🔴 GERİ (SAP F3 - Bir önceki menüye çıkış)
        btnBack.setOnClickListener {
            hideKeyboard()
            sendSapKeyOrOkCode(114, "/3")
        }

        // 🟡 LİSTE (SAP F4 - Arama Listesi / Yardım)
        btnList.setOnClickListener {
            hideKeyboard()
            sendSapKeyOrOkCode(115, "/4")
        }

        // 🚪 ÇIKIŞ (Oturumu Kapat ve Giriş Ekranına Dön)
        btnLogoff.setOnClickListener {
            hideKeyboard()
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

        // 🔵 YENİLE (Sayfayı tazele / Uzun basınca sunucu adresi değiştir)
        btnReload.setOnClickListener {
            hideKeyboard()
            webView.reload()
        }
        btnReload.setOnLongClickListener {
            hideKeyboard()
            showUrlConfigDialog()
            true
        }

        // 🟢 ONAY / ENTER (Sonraki adıma geç / Kaydet)
        btnEnter.setOnClickListener {
            hideKeyboard()
            sendSapKeyOrOkCode(13, "/0")
        }
    }

    /**
     * SAP ITSmobile'a Fonksiyon Tuşu veya OkCode Gönderme
     */
    private fun sendSapKeyOrOkCode(keyCode: Int, okCode: String) {
        val js = """
            (function() {
                if (document.activeElement && typeof document.activeElement.blur === 'function') {
                    document.activeElement.blur();
                }
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

                    var enterUp = new KeyboardEvent('keyup', {
                        key: 'Enter',
                        code: 'Enter',
                        keyCode: 13,
                        which: 13,
                        bubbles: true,
                        cancelable: true
                    });
                    active.dispatchEvent(enterUp);

                    // Depo Palet/Koli Otomasyonu: Ekranda "EKLE" butonu varsa otomatik tıkla
                    setTimeout(function() {
                        var buttons = document.querySelectorAll('input[type="button"], input[type="submit"], button, a.MobileButton, a');
                        var ekleBtn = null;
                        for (var i = 0; i < buttons.length; i++) {
                            var b = buttons[i];
                            var text = (b.value || b.innerText || b.getAttribute('title') || '').trim().toLowerCase();
                            if (text === 'ekle' || text.indexOf('ekle') !== -1 || text === 'add') {
                                ekleBtn = b;
                                break;
                            }
                        }

                        if (ekleBtn) {
                            if (active && typeof active.blur === 'function') active.blur();
                            ekleBtn.click();
                        } else {
                            if (typeof setOkCode === 'function') {
                                setOkCode('/0');
                            } else if (active.form) {
                                active.form.submit();
                            }
                        }
                    }, 120);
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
        hideKeyboard()
        Toast.makeText(this, "Okundu: $barcode", Toast.LENGTH_SHORT).show()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                hideKeyboard()
                sendSapKeyOrOkCode(114, "/3")
            }
        })
    }

    private fun showUrlConfigDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val tvCanli = TextView(this).apply { text = "Canlı Ortam URL (10.65.12.66):" }
        val inputCanli = EditText(this).apply {
            setText(getUrlForEnv("CANLI"))
        }

        val tvTest = TextView(this).apply {
            text = "Test Ortamı URL (10.65.12.67):"
            setPadding(0, 20, 0, 0)
        }
        val inputTest = EditText(this).apply {
            setText(getUrlForEnv("TEST"))
        }

        val tvTgt = TextView(this).apply {
            text = "TGT Ortamı URL (Turgutlu):"
            setPadding(0, 20, 0, 0)
        }
        val inputTgt = EditText(this).apply {
            setText(getUrlForEnv("TGT"))
        }

        layout.addView(tvCanli)
        layout.addView(inputCanli)
        layout.addView(tvTest)
        layout.addView(inputTest)
        layout.addView(tvTgt)
        layout.addView(inputTgt)

        AlertDialog.Builder(this)
            .setTitle("Sunucu Adreslerini Yapılandır")
            .setView(layout)
            .setPositiveButton("Kaydet") { _, _ ->
                val newCanli = inputCanli.text.toString().trim()
                val newTest = inputTest.text.toString().trim()
                val newTgt = inputTgt.text.toString().trim()
                if (newCanli.isNotEmpty()) sharedPrefs.edit().putString(KEY_URL_CANLI, newCanli).apply()
                if (newTest.isNotEmpty()) sharedPrefs.edit().putString(KEY_URL_TEST, newTest).apply()
                if (newTgt.isNotEmpty()) sharedPrefs.edit().putString(KEY_URL_TGT, newTgt).apply()
                loadSapUrl()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun getUrlForEnv(env: String): String {
        return when (env) {
            "TEST" -> sharedPrefs.getString(KEY_URL_TEST, DEFAULT_URL_TEST) ?: DEFAULT_URL_TEST
            "TGT" -> sharedPrefs.getString(KEY_URL_TGT, DEFAULT_URL_TGT) ?: DEFAULT_URL_TGT
            else -> sharedPrefs.getString(KEY_URL_CANLI, DEFAULT_URL_CANLI) ?: DEFAULT_URL_CANLI
        }
    }

    private fun getCurrentUrl(): String {
        return getUrlForEnv(getSelectedEnv())
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

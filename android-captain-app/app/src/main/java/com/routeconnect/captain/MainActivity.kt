package com.routeconnect.captain

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Shows the normal driver.html page inside the app, so manual stop check-in works exactly as before.
 * Only GPS comes from the native background service (works with the screen off).
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private val ui = Handler(Looper.getMainLooper())
    private var wantTracking = false

    // Pretend to be normal Chrome so Google sign-in is not refused inside an app
    private val chromeUa: String by lazy {
        WebSettings.getDefaultUserAgent(this).replace("; wv", "").replace(Regex("Version/[\\d.]+ "), "")
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        root = FrameLayout(this)
        setContentView(root)
        web = newWebView()
        root.addView(web)
        web.loadUrl(BuildConfig.DRIVER_URL)
        askPermissions()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun newWebView(): WebView = WebView(this).apply {
        layoutParams = ViewGroup.LayoutParams(-1, -1)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportMultipleWindows(true)          // Google sign-in popup
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.userAgentString = chromeUa
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(Bridge(), "AndroidBridge")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                if (u.scheme == "tel" || u.scheme == "whatsapp" || u.scheme == "intent" || u.scheme == "geo") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                    return true
                }
                return false
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, m: android.os.Message): Boolean {
                val popup = newWebView()
                root.addView(popup)
                popup.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(w: WebView) { root.removeView(w); w.destroy() }
                }
                (m.obj as WebView.WebViewTransport).webView = popup
                m.sendToTarget()
                return true
            }
            override fun onGeolocationPermissionsShowPrompt(o: String, cb: GeolocationPermissions.Callback) { cb.invoke(o, true, false) }
        }
    }

    /** What driver.html calls (window.AndroidBridge.*) */
    inner class Bridge {
        @JavascriptInterface fun startTracking() { ui.post { startGps() } }
        @JavascriptInterface fun stopTracking() { ui.post { stopGps() } }
    }

    private fun startGps() {
        wantTracking = true
        if (!hasLocation()) { askPermissions(); return }
        LocationService.onFix = { l ->
            ui.post {
                web.evaluateJavascript(
                    "window.__nativeFix&&window.__nativeFix(${l.latitude},${l.longitude},${l.accuracy},${if (l.hasSpeed()) l.speed else -1},${l.time})", null)
            }
        }
        LocationService.onError = { c -> ui.post { web.evaluateJavascript("window.__nativeErr&&window.__nativeErr($c)", null) } }
        ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java))
        askBatteryUnrestricted()
    }

    private fun stopGps() {
        wantTracking = false
        stopService(Intent(this, LocationService::class.java))
        LocationService.onFix = null; LocationService.onError = null
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 10)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (hasLocation()) {
            if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 11)
            if (wantTracking) startGps()
        }
    }

    /** Ask once to exempt the app from battery saver so the phone does not kill tracking. */
    @SuppressLint("BatteryLife")
    private fun askBatteryUnrestricted() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        val sp = getSharedPreferences("rc", MODE_PRIVATE)
        if (sp.getBoolean("asked", false)) return
        sp.edit().putBoolean("asked", true).apply()
        AlertDialog.Builder(this)
            .setTitle("बैटरी सेटिंग")
            .setMessage("स्क्रीन बंद होने पर भी लोकेशन जाती रहे, इसके लिए अगली स्क्रीन में 'अनुमति दें / Allow' दबाएँ।")
            .setPositiveButton("ठीक है") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
            }.show()
    }

    // IMPORTANT: do NOT pause the WebView in the background, otherwise the page stops sending location.
    override fun onPause() { super.onPause(); web.resumeTimers() }
    override fun onResume() { super.onResume(); web.onResume(); web.resumeTimers() }

    @Deprecated("use callback") override fun onBackPressed() {
        if (root.childCount > 1) { val top = root.getChildAt(root.childCount - 1) as WebView; root.removeView(top); top.destroy() }
        else if (web.canGoBack()) web.goBack()
        else moveTaskToBack(true)      // keep tracking alive instead of closing
    }
}

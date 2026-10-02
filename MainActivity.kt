package app.hiddenrule

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.LinearLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.security.MessageDigest

// ====== غيّر القيم دي لما تجهز ======
object Cfg {
    // أرقام اختبار من جوجل. استبدلها بأرقامك من AdMob قبل النشر
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val INTER = "ca-app-pub-3940256099942544/1033173712"
    const val REWARD = "ca-app-pub-3940256099942544/5224354917"
    // رابط الدفع بتاعك (فودافون كاش، PayPal، إلخ)
    const val PAY_URL = "https://example.com/buy"
    // سر أكواد التفعيل. متغيرهوش وإلا أكوادك في codes.txt هتبطل
    const val SECRET = "BC0CB11FA6D2"
}

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var banner: AdView
    private var interAd: InterstitialAd? = null
    private var rewardAd: RewardedAd? = null
    private var pro = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        pro = getSharedPreferences("hr", 0).getBoolean("pro", false)
        MobileAds.initialize(this) {}
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")
        banner = AdView(this)
        banner.setAdSize(AdSize.BANNER)
        banner.adUnitId = Cfg.BANNER
        if (pro) banner.visibility = View.GONE else banner.loadAd(AdRequest.Builder().build())
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(banner, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        setContentView(root)
        loadInter()
        loadReward()
    }

    private fun loadInter() {
        InterstitialAd.load(this, Cfg.INTER, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interAd = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { interAd = null }
        })
    }

    private fun loadReward() {
        RewardedAd.load(this, Cfg.REWARD, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardAd = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { rewardAd = null }
        })
    }

    private fun js(s: String) = runOnUiThread { web.evaluateJavascript(s, null) }

    private fun valid(raw: String): Boolean {
        val p = raw.trim().uppercase().split("-")
        if (p.size != 3 || p[0] != "HR") return false
        val h = MessageDigest.getInstance("SHA-256").digest((Cfg.SECRET + p[1]).toByteArray())
        val hex = h.joinToString("") { "%02x".format(it) }
        return hex.take(4).uppercase() == p[2]
    }

    inner class Bridge {
        @JavascriptInterface
        fun isPro(): Boolean = pro

        @JavascriptInterface
        fun inter() {
            runOnUiThread {
                val a = interAd
                if (!pro && a != null) {
                    interAd = null
                    a.show(this@MainActivity)
                    loadInter()
                }
            }
        }

        @JavascriptInterface
        fun reward() {
            runOnUiThread {
                val r = rewardAd
                if (r == null) {
                    js("onReward(false)")
                    loadReward()
                } else {
                    val earned = BooleanArray(1)
                    r.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardAd = null
                            loadReward()
                            js("onReward(" + earned[0] + ")")
                        }
                    }
                    r.show(this@MainActivity) { earned[0] = true }
                }
            }
        }

        @JavascriptInterface
        fun redeem(code: String): Boolean {
            if (!valid(code)) return false
            pro = true
            getSharedPreferences("hr", 0).edit().putBoolean("pro", true).apply()
            runOnUiThread { banner.visibility = View.GONE }
            return true
        }

        @JavascriptInterface
        fun buy() {
            runOnUiThread { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Cfg.PAY_URL))) }
        }

        @JavascriptInterface
        fun share(t: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t)
                startActivity(Intent.createChooser(i, null))
            }
        }
    }
}

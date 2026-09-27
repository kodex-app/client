package dev.icedtea.kodex.platform

import android.webkit.CookieManager
import com.multiplatform.webview.web.NativeWebView

actual fun prepareChallengeWebView(view: NativeWebView) {
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(view, true)
    }
    view.settings.domStorageEnabled = true
}

actual fun challengeWebViewUserAgent(view: NativeWebView): String? = view.settings.userAgentString

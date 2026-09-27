package dev.icedtea.kodex.platform

import com.multiplatform.webview.web.NativeWebView

/** WKWebView keeps third-party cookies for a frame the user interacts with; nothing to switch on. */
actual fun prepareChallengeWebView(view: NativeWebView) = Unit

/** WKWebView has no public getter for its default UA — the page is asked instead. */
actual fun challengeWebViewUserAgent(view: NativeWebView): String? = view.customUserAgent?.takeIf { it.isNotBlank() }

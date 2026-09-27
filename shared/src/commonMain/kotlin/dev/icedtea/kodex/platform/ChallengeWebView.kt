package dev.icedtea.kodex.platform

import com.multiplatform.webview.web.NativeWebView

/**
 * Readies a WebView for a site's human check (Cloudflare Turnstile and friends): the check runs in a
 * `challenges.cloudflare.com` frame, whose cookies Android's WebView refuses by default as
 * third-party — the LNReader app's WebView accepts them, so this one does too.
 */
expect fun prepareChallengeWebView(view: NativeWebView)

/**
 * The User-Agent [view] sends, when the platform can say so directly; null means ask the page
 * (`navigator.userAgent`). The server's requests must carry this exact UA for the cookies to count.
 */
expect fun challengeWebViewUserAgent(view: NativeWebView): String?

package app.kodex.client.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberUrlOpener(): (String) -> Unit = { url ->
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) }
}

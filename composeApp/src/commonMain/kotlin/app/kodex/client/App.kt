package app.kodex.client

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.kodex.client.di.AppGraph
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.login.LoginScreen
import app.kodex.client.ui.main.MainScaffold
import app.kodex.client.ui.theme.KodexTheme
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade

/**
 * Root of the shared UI, hosted identically by Android, iOS, and desktop entry points. A non-null
 * active server means the user is signed in → show the main app; otherwise → the login/server picker.
 */
@Composable
fun App() {
    val graph = remember { AppGraph() }

    // Route Coil's cover loads through our Ktor engine so images are decoded/cached consistently
    // across platforms; the per-request X-API-Key header is added by CoverImage.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { graph.imageHttpClient })) }
            .crossfade(true)
            .build()
    }

    KodexTheme {
        Surface(Modifier.fillMaxSize()) {
            val activeServer by graph.session.activeServer.collectAsStateSafe()
            if (activeServer == null) {
                LoginScreen(graph.session)
            } else {
                MainScaffold(graph.session, graph.api)
            }
        }
    }
}

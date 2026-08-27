package dev.icedtea.kodex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import dev.icedtea.kodex.di.AppGraph
import dev.icedtea.kodex.ui.LocalEventBus
import dev.icedtea.kodex.ui.LocalSnackbar
import dev.icedtea.kodex.ui.SnackbarController
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.login.LoginScreen
import dev.icedtea.kodex.ui.main.MainScaffold
import dev.icedtea.kodex.ui.theme.KodexTheme
import dev.icedtea.kodex.ui.theme.LocalImmersiveContent
import dev.icedtea.kodex.ui.theme.LocalSystemNavBarColor
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade

/**
 * Root of the shared UI, hosted identically by the Android and iOS entry points. A non-null
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

    // Written by whichever screen owns the bottom bar; read by the strip painted below.
    val systemNavBarColor = remember { mutableStateOf<Color?>(null) }

    // Written by a full-window screen (the readers) through ImmersiveContent; read just below.
    val immersiveContent = remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarController(snackbarHostState, scope) }

    KodexTheme(graph.appSettings) {
        CompositionLocalProvider(
            LocalEventBus provides graph.eventBus,
            LocalSnackbar provides snackbar,
            LocalSystemNavBarColor provides systemNavBarColor,
            LocalImmersiveContent provides immersiveContent,
        ) {
            Surface(Modifier.fillMaxSize()) {
              Box(Modifier.fillMaxSize()) {
                // Reserve the system navigation-bar space app-wide so bottom content (nav bar, FABs,
                // last list rows) is never hidden under it. Bottom-only, so top backdrops stay edge-to-edge.
                // Consuming the inset here also prevents descendant Scaffolds from double-padding it.
                //
                // Skipped for an immersive screen: the readers hide the system bars with their chrome,
                // and a reserved strip that collapses and reappears with the bars resizes the page under
                // them on every toggle. See ImmersiveContent.
                Box(Modifier.fillMaxSize().then(if (immersiveContent.value) Modifier else Modifier.navigationBarsPadding())) {
                    val activeServer by graph.session.activeServer.collectAsStateSafe()
                    if (activeServer == null) {
                        LoginScreen(graph.session)
                    } else {
                        MainScaffold(graph.session, graph.api, graph.appSettings, graph.sourcePrefs)
                    }
                    // App-level snackbar overlay — sits above any screen's own Scaffold.
                    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
                // The strip the system bar sits on. A sibling of the padded content rather than a
                // background under it, so it covers exactly the inset the content stepped aside for —
                // and, being outside that consumption, it still sees the real inset height.
                systemNavBarColor.value?.let { color ->
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(color),
                    )
                }
              }
            }
        }
    }
}

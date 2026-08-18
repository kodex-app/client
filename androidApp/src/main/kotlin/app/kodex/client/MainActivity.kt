package app.kodex.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.kodex.client.platform.enableKodexEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Not a bare enableEdgeToEdge(): that one re-imposes system-derived bar icon colours on
        // every configuration change, overriding the in-app theme. See enableKodexEdgeToEdge.
        enableKodexEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

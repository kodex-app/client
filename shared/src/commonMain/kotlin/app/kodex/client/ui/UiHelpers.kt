package app.kodex.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.StateFlow

/** Small alias so screens read a StateFlow without importing the flow-interop overload each time. */
@Composable
fun <T> StateFlow<T>.collectAsStateSafe(): State<T> = collectAsState()

/** Turns transport/HTTP failures into a short, user-facing line for the login screen. */
fun Throwable.friendlyMessage(): String = when (this) {
    is ClientRequestException -> when (response.status.value) {
        401 -> "Wrong email or password."
        403 -> "This account isn't allowed to sign in."
        404 -> "That doesn't look like a Kodex server."
        else -> message
    }

    else -> message ?: "Couldn't reach the server. Check the address and your connection."
}

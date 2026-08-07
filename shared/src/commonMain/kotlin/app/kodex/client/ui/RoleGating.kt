package app.kodex.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.kodex.client.auth.SessionManager

/**
 * Role gating off the current user (`UserDto.roles`). Screens/sections that are admin- or
 * manager-only observe these so the UI hides what the caller can't use (the server also enforces it).
 */
@Composable
fun rememberIsAdmin(session: SessionManager): Boolean {
    val user by session.currentUser.collectAsStateSafe()
    return user?.isAdmin == true
}

@Composable
fun rememberIsManager(session: SessionManager): Boolean {
    val user by session.currentUser.collectAsStateSafe()
    return user?.let { it.isAdmin || it.isManager } == true
}

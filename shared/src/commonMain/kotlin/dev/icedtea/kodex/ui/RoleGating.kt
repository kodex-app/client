package dev.icedtea.kodex.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.icedtea.kodex.auth.SessionManager

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

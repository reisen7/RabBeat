package com.rabbeat.app.desktop.ui
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/** Browser-style route history: both sidebar nav and detail pushes record back. */
class NavHistory(initial: DesktopRoute) {
    var current by mutableStateOf<DesktopRoute>(initial)
        private set
    private val backStack = mutableStateListOf<DesktopRoute>()
    private val forwardStack = mutableStateListOf<DesktopRoute>()
    val canBack: Boolean get() = backStack.isNotEmpty()
    val canForward: Boolean get() = forwardStack.isNotEmpty()

    fun push(route: DesktopRoute) {
        if (route == current) return
        backStack += current
        forwardStack.clear()
        current = route
    }

    fun topLevel(route: DesktopRoute) = push(route)

    fun back() {
        val prev = backStack.removeLastOrNull() ?: return
        forwardStack += current
        current = prev
    }

    fun forward() {
        val next = forwardStack.removeLastOrNull() ?: return
        backStack += current
        current = next
    }
}

/** Shared keyboard/focus handles between the window key handler and the shell. */
class ShellKeys {
    val searchFocus = FocusRequester()
    var searchFocused by mutableStateOf(false)
}

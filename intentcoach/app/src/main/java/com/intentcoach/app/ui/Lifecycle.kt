package com.intentcoach.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Runs [action] every time the screen comes back to the foreground — including the first
 * time. This is how screens notice a permission the user just granted in system Settings:
 * LaunchedEffect(Unit) only runs once, so it would stay stale.
 */
@Composable
fun OnResume(action: () -> Unit) {
    // rememberUpdatedState: the observer is registered once, but always calls the latest
    // lambda, so it never captures stale state.
    val current by rememberUpdatedState(action)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

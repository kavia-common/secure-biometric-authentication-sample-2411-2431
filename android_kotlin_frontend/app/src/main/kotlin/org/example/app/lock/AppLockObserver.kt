package org.example.app.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.example.app.auth.AuthManager

class AppLockObserver(private val authManager: AuthManager) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        // When app goes to background, lock session if logged in.
        authManager.lockApp()
    }
}

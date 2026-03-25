package org.example.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.example.app.auth.BiometricAuth
import org.example.app.lock.AppLockObserver

class MainActivity : AppCompatActivity() {

    private val app: SecureSampleApp by lazy { application as SecureSampleApp }

    private lateinit var container: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        container = findViewById(R.id.rootContainer)

        // Lifecycle-aware lock when app backgrounds.
        // In preview-safe mode we skip this wiring entirely to reduce chances of startup crashes.
        if (!app.isPreviewSafeMode) {
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.addObserver(AppLockObserver(app.authManager))
            }
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        // If locked and logged in, show locked screen.
        render()
    }

    private fun render() {
        // If Application initialization failed in preview, show an error screen instead of crashing.
        val initFailure = app.initFailureMessage
        if (initFailure != null) {
            showInitFailure(initFailure)
            return
        }

        val auth = app.authManager
        when {
            !auth.isLoggedIn() -> showLogin()
            auth.isLocked -> showLocked()
            else -> showMain()
        }
    }

    private fun showInitFailure(message: String) {
        val root = LayoutInflater.from(this).inflate(
            R.layout.screen_login,
            findViewById(R.id.rootContainer),
            false
        )
        setRoot(root)

        // Reuse login screen as a minimal UI surface; disable actions and show error.
        root.findViewById<EditText>(R.id.usernameInput).isEnabled = false
        root.findViewById<EditText>(R.id.passwordInput).isEnabled = false
        root.findViewById<Button>(R.id.loginButton).apply {
            isEnabled = false
            text = "App failed to initialize"
        }
        root.findViewById<TextView>(R.id.loginStatus).text =
            "Startup error (safe mode):\n$message"
    }

    private fun showLogin() {
        val root = LayoutInflater.from(this).inflate(
            R.layout.screen_login,
            findViewById(R.id.rootContainer),
            false
        )
        setRoot(root)

        val username = root.findViewById<EditText>(R.id.usernameInput)
        val password = root.findViewById<EditText>(R.id.passwordInput)
        val status = root.findViewById<TextView>(R.id.loginStatus)

        root.findViewById<Button>(R.id.loginButton).setOnClickListener {
            status.text = ""
            lifecycleScope.launch {
                val result = app.authManager.login(
                    username = username.text?.toString().orEmpty(),
                    password = password.text?.toString().orEmpty()
                )
                if (result.isSuccess) {
                    render()
                } else {
                    status.text = result.exceptionOrNull()?.message ?: "Login failed"
                }
            }
        }
    }

    private fun showLocked() {
        val root = LayoutInflater.from(this).inflate(
            R.layout.screen_locked,
            findViewById(R.id.rootContainer),
            false
        )
        setRoot(root)

        val status = root.findViewById<TextView>(R.id.lockedStatus)
        val unlock = root.findViewById<Button>(R.id.unlockButton)

        unlock.setOnClickListener {
            status.text = ""
            if (!BiometricAuth.canAuthenticate(this)) {
                status.text = "Biometric/device credential not available on this device."
                return@setOnClickListener
            }

            BiometricAuth.authenticate(
                activity = this,
                title = "Unlock app",
                subtitle = "Authenticate to continue",
                onSuccess = {
                    app.authManager.unlockApp()
                    render()
                },
                onError = { err ->
                    status.text = err
                }
            )
        }
    }

    private fun showMain() {
        val root = LayoutInflater.from(this).inflate(
            R.layout.screen_main,
            findViewById(R.id.rootContainer),
            false
        )
        setRoot(root)

        val info = root.findViewById<TextView>(R.id.sessionInfo)
        val apiOutput = root.findViewById<TextView>(R.id.apiOutput)

        val tokens = app.authManager.currentTokens()
        info.text = buildString {
            append("Session active.\n")
            if (tokens != null) {
                append("Access expires at: ").append(tokens.accessTokenExpiresAtMs).append("\n")
                append("Token will auto-refresh on API call if close to expiry.")
            }
        }

        root.findViewById<Button>(R.id.callApiButton).setOnClickListener {
            val apiClient = app.getOrCreateApiClient()
            if (apiClient == null) {
                apiOutput.text =
                    "API disabled in preview-safe mode (or failed to initialize)."
                return@setOnClickListener
            }

            apiOutput.text = "Calling API..."
            lifecycleScope.launch {
                val result = runCatching { apiClient.service.get() }
                apiOutput.text = result.getOrElse { "API error: ${it.message}" }
            }
        }

        root.findViewById<Button>(R.id.logoutButton).setOnClickListener {
            app.authManager.logout()
            render()
        }
    }

    private fun setRoot(view: View) {
        // Swap screen into the single container.
        val rootContainer = findViewById<android.widget.FrameLayout>(R.id.rootContainer)
        rootContainer.removeAllViews()
        rootContainer.addView(view)
    }
}

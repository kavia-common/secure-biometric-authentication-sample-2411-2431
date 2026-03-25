package org.example.app.diagnostics

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.example.app.MainActivity
import org.example.app.SecureSampleApp

/**
 * A minimal launcher Activity (no XML) used to surface startup diagnostics in preview.
 *
 * This activity is intended to be extremely resilient: it does not inflate layouts and it does not
 * touch fragile initialization paths. It only reads in-app internal files and application state,
 * and then lets the user proceed into MainActivity.
 */
class DiagnosticActivity : AppCompatActivity() {

    private val app: SecureSampleApp by lazy { application as SecureSampleApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Preview/emulator environments can be brittle; this launcher must never crash.
        val result = runCatching {
            super.onCreate(savedInstanceState)

            // Important: the marker indicates the *previous* process crashed.
            // We do not clear it automatically; user can clear explicitly to keep signal visible.
            val crashMarker = runCatching { CrashMarker.readStartupCrashMarker(this) }.getOrNull()
            val initFailure = runCatching { app.initFailureMessage }.getOrNull()

            val paddingPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f,
                resources.displayMetrics
            ).toInt()

            val rootColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val title = TextView(this).apply {
                text = "Startup diagnostics"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            }
            val subtitle = TextView(this).apply {
                text = "If the app previously exited on startup in preview, details may appear below."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }

            val markerHeader = TextView(this).apply {
                text = "Last startup crash marker:"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            val markerBody = TextView(this).apply {
                text = crashMarker ?: "(none)"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            val initHeader = TextView(this).apply {
                text = "Current init failure (in-memory):"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            val initBody = TextView(this).apply {
                text = initFailure ?: "(none)"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            val clearBtn = Button(this).apply {
                text = "Clear crash marker"
                setOnClickListener {
                    runCatching { CrashMarker.clearStartupCrashMarker(this@DiagnosticActivity) }
                    recreate()
                }
            }

            val continueBtn = Button(this).apply {
                text = "Continue to app"
                setOnClickListener {
                    // Proceed to the real app activity.
                    startActivity(Intent(this@DiagnosticActivity, MainActivity::class.java))
                    finish()
                }
            }

            val hint = TextView(this).apply {
                text = "Tip: if MainActivity still fails, return here on next launch to see the marker."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            rootColumn.addView(title)
            rootColumn.addView(spaceDp(8))
            rootColumn.addView(subtitle)
            rootColumn.addView(spaceDp(16))
            rootColumn.addView(markerHeader)
            rootColumn.addView(spaceDp(6))
            rootColumn.addView(markerBody)
            rootColumn.addView(spaceDp(16))
            rootColumn.addView(initHeader)
            rootColumn.addView(spaceDp(6))
            rootColumn.addView(initBody)
            rootColumn.addView(spaceDp(18))
            rootColumn.addView(clearBtn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            rootColumn.addView(spaceDp(10))
            rootColumn.addView(continueBtn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            rootColumn.addView(spaceDp(16))
            rootColumn.addView(hint)

            val scroll = ScrollView(this).apply {
                addView(rootColumn)
            }

            setContentView(scroll)
        }

        if (result.isFailure) {
            // Absolute last resort: a tiny, no-dependency screen so the process stays alive.
            super.onCreate(savedInstanceState)
            val paddingPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f,
                resources.displayMetrics
            ).toInt()
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            }
            val title = TextView(this).apply {
                text = "Diagnostics failed to render"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            }
            val body = TextView(this).apply {
                val t = result.exceptionOrNull()
                text = buildString {
                    append("An exception occurred in DiagnosticActivity.\n\n")
                    append(t?.javaClass?.name ?: "unknown")
                    t?.message?.let { append(": ").append(it) }
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            root.addView(title)
            root.addView(spaceDp(10))
            root.addView(body)
            setContentView(root)
        }
    }

    private fun spaceDp(dp: Int): TextView {
        val h = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
        return TextView(this).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, h)
            text = ""
        }
    }
}

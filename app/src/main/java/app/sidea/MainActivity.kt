package app.sidea

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sidea.data.Settings
import app.sidea.ui.SideRoot
import app.sidea.ui.theme.SideTheme

@UnstableApi
class MainActivity : AppCompatActivity() {
    private var current: Settings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bars(dark = true)
        if (savedInstanceState == null) opened(intent)
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { s ->
                current = s
                val dark = s.dark(isSystemInDarkTheme())
                LaunchedEffect(dark) { bars(dark) }
                // Wait for settings so the welcome screens don't flash for someone already here.
                SideTheme(s.format, dark) { SideRoot(s) }
            }
        }
    }

    private fun bars(dark: Boolean) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opened(intent)
    }

    private fun opened(intent: Intent?) {
        val open = intent?.getStringExtra(EXTRA_OPEN) ?: return
        container.open.value = open to System.currentTimeMillis()
    }

    /**
     * Leaving the app with the screen on means the phone is in your hand: the needle goes up. With
     * the screen off (the phone put down) the music plays on.
     */
    override fun onStop() {
        super.onStop()
        if (container.excused) {
            container.excused = false
            return
        }
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        if (current?.strict == true && interactive && !isChangingConfigurations && container.room.value != null) container.pickedUp()
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_ROOM = "room"
    }
}

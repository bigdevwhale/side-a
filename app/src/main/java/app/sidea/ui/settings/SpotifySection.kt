package app.sidea.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.play.Spotify
import app.sidea.ui.components.Field
import app.sidea.ui.components.SectionTitle
import app.sidea.ui.components.SoftButton
import app.sidea.ui.components.UCard
import app.sidea.ui.ctx
import app.sidea.ui.theme.Mono
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType

private const val DASHBOARD = "https://developer.spotify.com/dashboard"

/**
 * The optional Spotify Client ID: how to get one (an app of your own in Spotify's dashboard, with
 * this build's package name, fingerprint and redirect), where to paste it, and whether it connects.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SpotifySection(saved: String, onSave: (String) -> Unit) {
    val c = ctx()
    val spotify = c.container.spotify
    val link by spotify.link.collectAsStateWithLifecycle()
    val error by spotify.error.collectAsStateWithLifecycle()
    var id by remember { mutableStateOf(saved) }
    val fingerprint = remember { runCatching { Spotify.fingerprint(c) }.getOrDefault("") }
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val no = spotify.refused(res.resultCode, res.data)
        if (no == null) spotify.connect(c, id, ask = false) else spotify.failed(no)
    }

    SectionTitle("Spotify")
    UCard {
        Text(stringResource(R.string.spotify_title), style = UType.Strong)
        Text(stringResource(R.string.spotify_sub), style = UType.Muted)
        Spacer(Modifier.height(14.dp))

        Text(stringResource(R.string.spotify_step1), style = UType.Body)
        Spacer(Modifier.height(6.dp))
        SoftButton(stringResource(R.string.spotify_open), {
            try {
                c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DASHBOARD)))
            } catch (_: ActivityNotFoundException) {
            }
        }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
        Spacer(Modifier.height(14.dp))

        Text(stringResource(R.string.spotify_step2), style = UType.Body)
        Spacer(Modifier.height(6.dp))
        Copyable(stringResource(R.string.spotify_package), c.packageName)
        Copyable(stringResource(R.string.spotify_sha1), fingerprint)
        Copyable(stringResource(R.string.spotify_redirect), Spotify.REDIRECT)
        Text(stringResource(R.string.spotify_api), style = UType.Small, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(14.dp))

        Text(stringResource(R.string.spotify_step3), style = UType.Body)
        Spacer(Modifier.height(8.dp))
        Field(id, { v ->
            id = v.trim()
            onSave(id)
        }, "Client ID", style = UType.Body.copy(fontFamily = Mono, fontSize = 14.sp))
        Spacer(Modifier.height(10.dp))
        SoftButton(stringResource(R.string.spotify_connect), {
            // Ask in Spotify first (from here, in front), then connect quietly.
            try {
                allow.launch(spotify.permission(id))
            } catch (_: ActivityNotFoundException) {
                spotify.connect(c, id, ask = true)
            }
        }, enabled = id.length >= 16, icon = Icons.Rounded.Link)
        Spacer(Modifier.height(8.dp))
        val status = when {
            !spotify.installed() -> stringResource(R.string.spotify_missing)
            link == Spotify.Link.ON -> stringResource(R.string.spotify_on)
            link == Spotify.Link.CONNECTING -> stringResource(R.string.spotify_connecting)
            link == Spotify.Link.FAILED -> stringResource(R.string.spotify_failed, error.orEmpty())
            id.isBlank() -> stringResource(R.string.spotify_none)
            else -> stringResource(R.string.spotify_ready)
        }
        Text(status, style = UType.Small.copy(color = if (link == Spotify.Link.ON) U.c.lamp else U.c.muted))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.spotify_premium), style = UType.Small)
    }
}

/** A value to paste into Spotify's dashboard, with a copy button. */
@Composable
private fun Copyable(label: String, value: String) {
    val c = ctx()
    var copied by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(U.c.surface2, U.RSm)
            .clickable {
                c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
                copied = true
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = UType.Small)
            Text(value.ifBlank { "—" }, fontFamily = Mono, fontSize = 13.sp, color = U.c.ink)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ContentCopy, stringResource(R.string.copy), Modifier.size(18.dp), tint = if (copied) U.c.lamp else U.c.faint)
    }
}

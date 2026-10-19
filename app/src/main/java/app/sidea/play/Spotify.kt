package app.sidea.play

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.ContentApi
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ListItem
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

/**
 * Spotify's own remote control (the App Remote SDK), with a Client ID the person registers
 * themselves: optional, and the only way to start an album in Spotify without opening it. It talks
 * to the Spotify app on the phone; nothing goes over the internet from here.
 */
class Spotify(private val context: Context) {
    enum class Link { OFF, CONNECTING, ON, FAILED }

    private val _link = MutableStateFlow(Link.OFF)
    val link: StateFlow<Link> = _link.asStateFlow()

    /** Why the last connection failed, in Spotify's words. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** The album of what Spotify is playing now: its name and spotify:album: URI. */
    private val _album = MutableStateFlow<Pair<String, String>?>(null)
    val album: StateFlow<Pair<String, String>?> = _album.asStateFlow()

    @Volatile private var remote: SpotifyAppRemote? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var id = ""

    fun installed(): Boolean = SpotifyAppRemote.isSpotifyInstalled(context)

    /**
     * Connects with [clientId], then runs [then]. With [ask] set, Spotify shows its own consent
     * screen the first time (from an activity [from]); without it, a connection that hasn't been
     * allowed yet just fails, and [failed] runs.
     */
    fun connect(from: Context, clientId: String, ask: Boolean, failed: () -> Unit = {}, then: (SpotifyAppRemote) -> Unit = {}) {
        val r = remote
        if (r != null && r.isConnected && clientId == id) return then(r)
        if (clientId.isBlank()) return failed()
        r?.let { SpotifyAppRemote.disconnect(it) }
        id = clientId
        _link.value = Link.CONNECTING
        // Spotify sometimes never answers (a system dialog in the way, Spotify asleep): give up in a while.
        var done = false
        handler.postDelayed({
            if (!done && _link.value == Link.CONNECTING) {
                done = true
                failed("no answer from Spotify")
                failed()
            }
        }, 8_000)
        val params = ConnectionParams.Builder(clientId).setRedirectUri(REDIRECT).showAuthView(ask).build()
        SpotifyAppRemote.connect(from, params, object : Connector.ConnectionListener {
            override fun onConnected(app: SpotifyAppRemote) {
                if (done) return SpotifyAppRemote.disconnect(app)
                done = true
                remote = app
                _link.value = Link.ON
                _error.value = null
                app.playerApi.subscribeToPlayerState().setEventCallback { ps ->
                    val a = ps.track?.album
                    if (a != null && !a.uri.isNullOrBlank()) _album.value = a.name.orEmpty() to a.uri
                }
                then(app)
            }

            override fun onFailure(error: Throwable) {
                if (done) return
                done = true
                remote = null
                _link.value = Link.FAILED
                _error.value = error.message ?: error.javaClass.simpleName
                failed()
            }
        })
    }

    /** Plays [uri] (a spotify:album:…) from its first song, as side A starts; [failed] if Spotify can't be reached. */
    fun play(from: Context, clientId: String, uri: String, failed: () -> Unit) {
        connect(from, clientId, ask = false, failed = failed) { app ->
            app.playerApi.skipToIndex(uri, 0).setErrorCallback {
                // Some accounts can't pick a song; then just play the album.
                app.playerApi.play(uri).setErrorCallback { failed() }
            }
        }
    }

    /**
     * What's on one of Spotify's shelves: the top ones (recently played, your library, picks) with
     * [parent] null, or what's inside [parent]. Null if Spotify can't be reached.
     */
    fun browse(from: Context, clientId: String, parent: ListItem?, done: (List<ListItem>?) -> Unit) {
        connect(from, clientId, ask = false, failed = { done(null) }) { app ->
            val call = if (parent == null) app.contentApi.getRecommendedContentItems(ContentApi.ContentType.DEFAULT) else app.contentApi.getChildrenOfItem(parent, 50, 0)
            call.setResultCallback { done(it.items?.toList().orEmpty()) }.setErrorCallback { e ->
                failed(e.message ?: e.javaClass.simpleName)
                done(null)
            }
        }
    }

    /** An item's picture, from Spotify's own cache. */
    fun image(item: ListItem, done: (Bitmap?) -> Unit) {
        val app = remote
        val uri = item.imageUri
        if (app == null || !app.isConnected || uri == null) return done(null)
        app.imagesApi.getImage(uri, Image.Dimension.LARGE).setResultCallback { done(it) }.setErrorCallback { done(null) }
    }

    /**
     * The one-time permission, asked in the Spotify app. Spotify can't open it by itself from the
     * background on recent Android (the App Remote's own auth view gets blocked and the connection
     * just hangs), so it's started from the settings screen, in front: the same request Spotify's auth
     * library sends, without the library. Launch it for a result, then [connect] without [ask].
     */
    fun permission(clientId: String): Intent = Intent(SSO).setPackage(PACKAGE)
        .putExtra("VERSION", 1)
        .putExtra("CLIENT_ID", clientId)
        .putExtra("REDIRECT_URI", REDIRECT)
        .putExtra("RESPONSE_TYPE", "token")
        .putExtra("SCOPES", arrayOf("app-remote-control"))
        .putExtra("STATE", UUID.randomUUID().toString())

    /** What Spotify said to the permission request, if it refused. */
    fun refused(code: Int, data: Intent?): String? {
        val reply = data?.getBundleExtra("REPLY")
        val error = reply?.getString("ERROR") ?: data?.getStringExtra("ERROR")
        return error ?: if (code == Activity.RESULT_OK) null else "cancelled"
    }

    fun failed(why: String) {
        _link.value = Link.FAILED
        _error.value = why
    }

    fun disconnect() {
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        _link.value = Link.OFF
    }

    companion object {
        const val PACKAGE = "com.spotify.music"
        private const val SSO = "com.spotify.sso.action.START_AUTH_FLOW"

        /** The redirect URI to register in the Spotify dashboard. Nothing is ever sent there. */
        const val REDIRECT = "sidea://spotify"

        fun isAlbum(uri: String?): Boolean = uri != null && uri.startsWith("spotify:album:")

        /** The SHA-1 of this build's signing certificate, as the Spotify dashboard asks for it. */
        fun fingerprint(c: Context): String {
            val info = c.packageManager.getPackageInfo(c.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val cert = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return ""
            return MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString(":") { "%02X".format(it) }
        }
    }
}

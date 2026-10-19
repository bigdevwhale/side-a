package app.sidea.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sidea.R
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.T
import app.sidea.ui.art.Cover
import app.sidea.ui.components.TopBar
import app.sidea.ui.ctx
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import app.sidea.ui.w

/** The second-hand shop: everything let go, each with its farewell line. Any of it can be bought back, as a week's buy. */
@Composable
fun ArchiveScreen(home: Home, go: Go) {
    val c = ctx()
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.archive), go::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(w(R.string.archive_sub_v, R.string.archive_sub_t), style = UType.Sub)
            Spacer(Modifier.height(16.dp))
            if (home.gone.isEmpty()) Text(w(R.string.archive_none_v, R.string.archive_none_t), style = UType.Muted)
            home.gone.forEach { r ->
                Row(Modifier.fillMaxWidth().clip(U.RMd).clickable { go.record(r.id) }.padding(vertical = 10.dp)) {
                    Box(Modifier.width(if (U.tape) 54.dp else 72.dp)) { Cover(r, home.wear(r), Modifier.fillMaxWidth(), lettering = false) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.artist, style = UType.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (r.farewell.isNotBlank()) Text(r.farewell, style = UType.Note.copy(fontSize = UType.Note.fontSize * 0.85f, color = U.c.muted), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.gone_since, T.date(c, r.goneAt)), style = UType.Small)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

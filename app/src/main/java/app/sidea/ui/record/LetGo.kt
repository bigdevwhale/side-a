package app.sidea.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sidea.R
import app.sidea.data.Record
import app.sidea.ui.Home
import app.sidea.ui.art.Cover
import app.sidea.ui.components.Field
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import app.sidea.ui.w

/**
 * Taking a record to the second-hand shop. With [pick] set, the shelf is full and one has to go to
 * make room: choose which first. Then a line to remember it by.
 */
@Composable
fun LetGoDialog(home: Home, record: Record?, pick: Boolean, onDismiss: () -> Unit, onDone: (Record, String) -> Unit) {
    var chosen by remember { mutableStateOf(record) }
    var farewell by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = U.c.surface,
        title = { Text(if (pick && chosen == null) w(R.string.full_title_v, R.string.full_title_t) else stringResource(R.string.let_go_title), style = UType.Title) },
        text = {
            Column {
                val r = chosen
                if (r == null) {
                    Text(w(R.string.full_sub_v, R.string.full_sub_t), style = UType.Muted)
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                        // The least played first: the likeliest to go.
                        home.shelf.sortedBy { home.plays(it) }.forEach { s ->
                            Row(Modifier.fillMaxWidth().clip(U.RSm).clickable { chosen = s }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(40.dp)) { Cover(s, home.wear(s), Modifier.fillMaxWidth(), lettering = false) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, style = UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.artist, style = UType.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(48.dp)) { Cover(r, home.wear(r), Modifier.fillMaxWidth(), lettering = false) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(r.title, style = UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.artist, style = UType.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.let_go_sub), style = UType.Muted)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.background(U.c.paper, U.RSm).border(1.dp, U.c.line, U.RSm)) {
                        Field(farewell, { farewell = it.take(140) }, stringResource(R.string.farewell_hint), singleLine = false, style = UType.Note, minLines = 2)
                    }
                }
            }
        },
        confirmButton = {
            val r = chosen
            if (r != null) TextButton(onClick = { onDone(r, farewell) }) { Text(stringResource(R.string.let_go_do), color = U.c.lamp) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = U.c.muted) } },
    )
}

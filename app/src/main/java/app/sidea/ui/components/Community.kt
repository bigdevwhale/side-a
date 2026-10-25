package app.sidea.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sidea.R
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType

/** The StillCraft bot: deep links file a bug or an idea for this app, or follow its releases. */
object Community {
    private const val BOT = "https://t.me/ArcaneWhisperBot?start="
    const val SLUG = "sidea"
    const val BUG = "${BOT}bug_$SLUG"
    const val IDEA = "${BOT}wish_$SLUG"
    const val FOLLOW = "${BOT}f_$SLUG"
    const val CHAT = "https://t.me/+Q4gFEDk8O842OGVi"
}

/** Report a bug, suggest an idea, follow updates, join the chat; each opens Telegram. */
@Composable
fun CommunityLinks() {
    val context = LocalContext.current
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    SectionTitle(stringResource(R.string.community))
    UCard {
        Text(stringResource(R.string.community_hint), style = UType.Muted)
        Spacer(Modifier.height(4.dp))
        LinkRow("🐞", stringResource(R.string.community_bug), stringResource(R.string.community_bug_hint)) { open(Community.BUG) }
        LinkRow("💡", stringResource(R.string.community_idea), stringResource(R.string.community_idea_hint)) { open(Community.IDEA) }
        LinkRow("🔔", stringResource(R.string.community_follow), stringResource(R.string.community_follow_hint)) { open(Community.FOLLOW) }
        LinkRow("💬", stringResource(R.string.community_chat), stringResource(R.string.community_chat_hint)) { open(Community.CHAT) }
    }
}

@Composable
private fun LinkRow(icon: String, label: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = UType.Strong)
            Spacer(Modifier.height(2.dp))
            Text(sub, style = UType.Muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = U.c.faint)
    }
}

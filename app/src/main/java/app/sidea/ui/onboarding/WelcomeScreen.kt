package app.sidea.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.data.Format
import app.sidea.data.Record
import app.sidea.ui.art.Cassette
import app.sidea.ui.art.SmallDisc
import app.sidea.ui.components.PrimaryButton
import app.sidea.ui.ctx
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import app.sidea.ui.w

private val hero = Record(id = -1, title = "Side A", artist = "Stillcraft", seed = 2)

/**
 * Three pages: why (too much music, none of it heard), which room (records or cassettes, and the
 * whole app follows), and the house rules.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val c = ctx()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val current by c.container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val format = current?.format ?: Format.VINYL

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
        AnimatedContent(page, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page") { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                when (p) {
                    0 -> {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
                            if (U.tape) Cassette(hero, "A", 0.3f, 1, 0.1f, Modifier.fillMaxWidth())
                            else SmallDisc(hero, true, Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                        Spacer(Modifier.height(32.dp))
                        Text(stringResource(R.string.welcome_title), style = UType.Hero)
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.welcome_sub), style = UType.Sub)
                    }
                    1 -> {
                        Text(stringResource(R.string.pick_title), style = UType.Display)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.pick_sub), style = UType.Sub)
                        Spacer(Modifier.height(22.dp))
                        FormatPicker(format, { f -> c.container.write { settings.update { it.copy(format = f) } } })
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.pick_later), style = UType.Small)
                    }
                    else -> {
                        Text(stringResource(R.string.rules_title), style = UType.Display)
                        Spacer(Modifier.height(22.dp))
                        Rule("1", w(R.string.rule_week_v, R.string.rule_week_t), w(R.string.rule_week_sub_v, R.string.rule_week_sub_t))
                        Rule("2", stringResource(R.string.rule_whole), w(R.string.rule_whole_sub_v, R.string.rule_whole_sub_t))
                        Rule("3", w(R.string.rule_mark_v, R.string.rule_mark_t), w(R.string.rule_mark_sub_v, R.string.rule_mark_sub_t))
                        Rule("4", stringResource(R.string.rule_shelf), w(R.string.rule_shelf_sub_v, R.string.rule_shelf_sub_t))
                        Rule("5", stringResource(R.string.rule_gift), w(R.string.rule_gift_sub_v, R.string.rule_gift_sub_t))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i -> Box(Modifier.size(8.dp).background(if (i == page) U.c.lamp else U.c.line, CircleShape)) }
            }
            PrimaryButton(
                stringResource(if (page < 2) R.string.next else R.string.start),
                {
                    if (page < 2) page++
                    else {
                        c.container.write { settings.update { it.copy(onboarded = true) } }
                        onDone()
                    }
                },
                Modifier.width(170.dp),
            )
        }
    }
}

@Composable
private fun Rule(n: String, title: String, sub: String) {
    Row(Modifier.padding(vertical = 9.dp)) {
        Box(Modifier.size(30.dp).background(U.c.lampSoft, CircleShape), contentAlignment = Alignment.Center) {
            Text(n, style = UType.Strong.copy(color = U.c.lamp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = UType.Strong)
            Text(sub, style = UType.Muted)
        }
    }
}

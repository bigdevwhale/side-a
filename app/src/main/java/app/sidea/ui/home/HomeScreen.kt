package app.sidea.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.domain.Allowance
import app.sidea.domain.Shop
import app.sidea.play.Phase
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.T
import app.sidea.ui.art.Cassette
import app.sidea.ui.art.Shelf
import app.sidea.ui.art.SmallDisc
import app.sidea.ui.art.Spine
import app.sidea.ui.ctx
import app.sidea.ui.components.PrimaryButton
import app.sidea.ui.components.SectionTitle
import app.sidea.ui.components.UCard
import app.sidea.ui.theme.Mono
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import app.sidea.ui.theme.Wide
import app.sidea.ui.w

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun HomeScreen(home: Home, go: Go) {
    val c = ctx()
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
        Masthead(go)

        val room by c.container.room.collectAsStateWithLifecycle()
        room?.let { id -> home.record(id)?.let { NowPlaying(it, home, go) } }

        SectionTitle(w(R.string.shelf_title, R.string.shelf_title_t, home.shelf.size, Shop.SHELF))
        if (home.shelf.isEmpty()) {
            UCard {
                Text(w(R.string.shelf_empty_v, R.string.shelf_empty_t), style = UType.Title)
                Spacer(Modifier.height(6.dp))
                Text(w(R.string.shelf_empty_sub_v, R.string.shelf_empty_sub_t), style = UType.Muted)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(w(R.string.go_shop_v, R.string.go_shop_t), go::shop, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
        }
        Shelf(home.shelf.map { Spine(it, home.wear(it), home.fresh(it)) }, onOpen = { go.record(it.id) }, modifier = Modifier.fillMaxWidth().clip(U.RMd))

        Spacer(Modifier.height(16.dp))
        ShopCard(home, go)
        Spacer(Modifier.height(10.dp))
        UCard(onClick = go::archive, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.archive), style = UType.Strong)
                    Text(
                        if (home.gone.isEmpty()) w(R.string.archive_none_v, R.string.archive_none_t) else T.plural(c, if (U.tape) R.plurals.gone_t else R.plurals.gone_v, home.gone.size),
                        style = UType.Muted,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = U.c.faint)
            }
        }
        if (home.listenedMs > 60_000) {
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.listened_total, T.hours(c, home.listenedMs)).uppercase(), style = UType.Eyebrow, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** The name on the front of the hi-fi, its lamp, and the small print beside it. */
@Composable
private fun Masthead(go: Go) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name).uppercase(), fontFamily = Wide, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.12.em, color = U.c.ink)
                Spacer(Modifier.width(10.dp))
                Lamp()
            }
            Text(w(R.string.masthead_v, R.string.masthead_t), fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.2.em, color = U.c.faint)
        }
        IconButton(onClick = go::settings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = U.c.muted) }
    }
}

@Composable
fun Lamp(on: Boolean = true, size: Float = 9f) {
    val lamp = U.c.lamp
    Canvas(Modifier.size(size.dp * 2)) {
        if (on) drawCircle(lamp.copy(alpha = 0.25f), this.size.minDimension / 2)
        drawCircle(if (on) lamp else Color.Gray, this.size.minDimension / 4)
    }
}

/** A record is on: a small turning disc (or cassette) and the way back to the room. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun NowPlaying(record: app.sidea.data.Record, home: Home, go: Go) {
    val c = ctx()
    val deck by c.container.deck.state.collectAsStateWithLifecycle()
    val stream by c.container.stream.state.collectAsStateWithLifecycle()
    val playing = (deck.record?.id == record.id && deck.playing) || (stream.record?.id == record.id && stream.matched && stream.now?.playing == true)
    Spacer(Modifier.height(10.dp))
    UCard(onClick = { go.room(record.id) }, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (U.tape) {
                val at = if (deck.sideMs > 0) deck.sideAtMs.toFloat() / deck.sideMs else 0.3f
                Cassette(record, if (deck.side == 0) "A" else "B", at, if (playing) 1 else 0, home.wear(record), Modifier.width(76.dp))
            } else SmallDisc(record, playing, Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        deck.phase == Phase.FLIP -> w(R.string.now_flip_v, R.string.now_flip_t)
                        playing -> w(R.string.now_playing_v, R.string.now_playing_t)
                        else -> w(R.string.now_waiting_v, R.string.now_waiting_t)
                    }.uppercase(),
                    style = UType.Eyebrow.copy(color = U.c.lamp),
                )
                Text(record.title, style = UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(record.artist, style = UType.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = U.c.faint)
        }
    }
}

/** The shop on the corner: whether today's a day to buy, and what's waiting on the counter. */
@Composable
private fun ShopCard(home: Home, go: Go) {
    val c = ctx()
    UCard(onClick = go::shop, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Awning(Modifier.fillMaxWidth().height(18.dp))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w(R.string.shop_v, R.string.shop_t), style = UType.Title)
                    Spacer(Modifier.height(4.dp))
                    Text(allowanceText(home), style = UType.Muted.copy(color = if (home.allowance.canBuy) U.c.lamp else U.c.muted))
                    if (home.held.isNotEmpty()) Text(T.plural(c, R.plurals.held, home.held.size), style = UType.Small)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = U.c.faint)
            }
        }
    }
}

@Composable
fun allowanceText(home: Home): String {
    val c = ctx()
    return when (val a = home.allowance) {
        is Allowance.Gift -> T.plural(c, if (U.tape) R.plurals.gifts_t else R.plurals.gifts_v, a.left)
        Allowance.Open -> w(R.string.shop_open_v, R.string.shop_open_t)
        is Allowance.Closed -> stringResource(R.string.shop_closed, T.weekday(c, a.next))
    }
}

/** The shop's striped awning. */
@Composable
fun Awning(modifier: Modifier = Modifier) {
    val a = U.c.lamp
    val b = U.c.paper
    Canvas(modifier) {
        val n = 14
        val sw = size.width / n
        for (i in 0 until n) {
            drawRect(if (i % 2 == 0) a else b, Offset(i * sw, 0f), Size(sw, size.height * 0.7f))
            drawArc(if (i % 2 == 0) a else b, 0f, 180f, true, Offset(i * sw, size.height * 0.4f), Size(sw, size.height * 0.6f))
        }
    }
}

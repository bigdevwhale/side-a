package app.sidea.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sidea.R
import app.sidea.data.Format
import app.sidea.data.Record
import app.sidea.ui.art.Cassette
import app.sidea.ui.art.SmallDisc
import app.sidea.ui.theme.LocalFormat
import app.sidea.ui.theme.LocalPalette
import app.sidea.ui.theme.SilverDeck
import app.sidea.ui.theme.U
import app.sidea.ui.theme.UType
import app.sidea.ui.theme.Walnut

/** A record to show off each look with. */
private val sample = Record(id = -1, title = "Side A", artist = "Stillcraft", seed = 5)

/** Records or cassettes: each card is drawn in its own room, whichever is on now. */
@Composable
fun FormatPicker(selected: Format, onSelect: (Format) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Format.VINYL, selected == Format.VINYL, Modifier.weight(1f)) { onSelect(Format.VINYL) }
        Card(Format.TAPE, selected == Format.TAPE, Modifier.weight(1f)) { onSelect(Format.TAPE) }
    }
}

@Composable
private fun Card(format: Format, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ring = U.c.lamp
    val pal = if (format == Format.TAPE) SilverDeck else Walnut
    CompositionLocalProvider(LocalPalette provides pal, LocalFormat provides format) {
        Column(
            modifier
                .clip(U.RLg)
                .background(pal.surface)
                .border(if (on) 3.dp else 1.dp, if (on) ring else pal.line, U.RLg)
                .clickable(onClick = onClick)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                if (format == Format.TAPE) Cassette(sample, "A", 0.35f, if (on) 1 else 0, 0f, Modifier.fillMaxWidth())
                else SmallDisc(sample, on, Modifier.fillMaxWidth(0.92f).aspectRatio(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(if (format == Format.TAPE) R.string.format_tape else R.string.format_vinyl), style = UType.Title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(if (format == Format.TAPE) R.string.format_tape_sub else R.string.format_vinyl_sub), style = UType.Muted, textAlign = TextAlign.Center)
        }
    }
}

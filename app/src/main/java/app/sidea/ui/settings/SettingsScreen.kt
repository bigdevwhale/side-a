package app.sidea.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import app.sidea.R
import app.sidea.container
import app.sidea.data.Crackle
import app.sidea.data.Settings
import app.sidea.data.Shade
import app.sidea.ui.Go
import app.sidea.ui.Home
import app.sidea.ui.Language
import app.sidea.ui.T
import app.sidea.ui.components.Chip
import app.sidea.ui.components.InfoRow
import app.sidea.ui.components.SectionTitle
import app.sidea.ui.components.Segmented
import app.sidea.ui.components.Toggle
import app.sidea.ui.components.TopBar
import app.sidea.ui.components.UCard
import app.sidea.ui.ctx
import app.sidea.ui.onboarding.FormatPicker
import app.sidea.ui.shop.openListenerSettings
import app.sidea.ui.theme.UType
import app.sidea.ui.theme.U
import app.sidea.ui.w
import java.time.DayOfWeek

val SILENCES = listOf(0, 15, 30, 60)

@OptIn(ExperimentalLayoutApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsScreen(home: Home, go: Go) {
    val c = ctx()
    val s = home.settings
    fun set(change: (Settings) -> Settings) = c.container.write { settings.update(change) }
    // Coming back from the system's access page: look again.
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    val granted = remember(resumed) { c.container.stream.granted() }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.settings), go::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            SectionTitle(stringResource(R.string.set_look))
            FormatPicker(s.format, { f -> set { it.copy(format = f) } })
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.set_look_sub), style = UType.Small)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.set_shade), style = UType.Strong)
            Spacer(Modifier.height(8.dp))
            Segmented(Shade.entries, s.shade, {
                stringResource(
                    when (it) {
                        Shade.NATIVE -> R.string.shade_native
                        Shade.LIGHT -> R.string.shade_light
                        Shade.DARK -> R.string.shade_dark
                        Shade.SYSTEM -> R.string.shade_system
                    },
                )
            }, { v -> set { it.copy(shade = v) } })
            Spacer(Modifier.height(6.dp))
            Text(w(R.string.shade_sub_v, R.string.shade_sub_t), style = UType.Small)

            SectionTitle(w(R.string.shop_v, R.string.shop_t))
            UCard {
                Text(stringResource(R.string.set_delivery), style = UType.Strong)
                Text(stringResource(R.string.set_delivery_sub), style = UType.Muted)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d -> Chip(T.dayShort(c, d), s.day == d, { set { it.copy(storeDay = d.value) } }) }
                }
            }

            SectionTitle(stringResource(R.string.room))
            UCard {
                Text(w(R.string.set_silence_v, R.string.set_silence_t), style = UType.Strong)
                Text(stringResource(R.string.set_silence_sub), style = UType.Muted)
                Spacer(Modifier.height(10.dp))
                Segmented(SILENCES, s.silence, { if (it == 0) stringResource(R.string.off) else stringResource(R.string.seconds, it) }, { n -> set { it.copy(silence = n) } })
                Spacer(Modifier.height(16.dp))
                Toggle(w(R.string.set_strict_v, R.string.set_strict_t), stringResource(R.string.set_strict_sub), s.strict) { v -> set { it.copy(strict = v) } }
                Spacer(Modifier.height(12.dp))
                Text(w(R.string.set_crackle_v, R.string.set_crackle_t), style = UType.Strong)
                Text(w(R.string.set_crackle_sub_v, R.string.set_crackle_sub_t), style = UType.Muted)
                Spacer(Modifier.height(10.dp))
                Segmented(Crackle.entries, s.crackle, {
                    stringResource(
                        when (it) {
                            Crackle.OFF -> R.string.crackle_off
                            Crackle.SOFT -> R.string.crackle_soft
                            Crackle.REAL -> R.string.crackle_real
                        },
                    )
                }, { v -> set { it.copy(crackle = v) } })
                Spacer(Modifier.height(14.dp))
                Toggle(w(R.string.set_recorded_v, R.string.set_recorded_t), w(R.string.set_recorded_sub_v, R.string.set_recorded_sub_t), s.recorded) { v -> set { it.copy(recorded = v) } }
            }

            SectionTitle(stringResource(R.string.set_players))
            UCard {
                InfoRow(Icons.Rounded.GraphicEq, stringResource(R.string.set_players_sub), stringResource(if (granted) R.string.set_access_on else R.string.set_access_off)) { openListenerSettings(c) }
                InfoRow(Icons.Rounded.BatteryAlert, stringResource(R.string.set_battery_sub), stringResource(R.string.set_battery)) {
                    try {
                        c.startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        c.startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null)))
                    }
                }
            }

            SectionTitle(stringResource(R.string.set_language))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cur = Language.current()
                Chip(stringResource(R.string.set_lang_system), cur == Language.SYSTEM, { Language.set(Language.SYSTEM) })
                Chip("English", cur == Language.EN, { Language.set(Language.EN) })
                Chip("Русский", cur == Language.RU, { Language.set(Language.RU) })
            }

            SectionTitle(stringResource(R.string.set_about))
            Text(stringResource(R.string.set_privacy), style = UType.Sub)
            Spacer(Modifier.height(28.dp))
        }
    }
}

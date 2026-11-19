package app.katib.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.katib.R
import app.katib.asr.Models
import app.katib.data.Person
import app.katib.data.SearchHit
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

// ------------------------------------------------------------------ Search
@Composable
fun SearchScreen(vm: AppVm, onOpen: (Long, Float) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>?>(null) }
    LaunchedEffect(q) {
        if (q.isBlank()) { hits = null; return@LaunchedEffect }
        delay(200); hits = vm.search(q)
    }
    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.tab_search))
        OutlinedTextField(q, { q = it }, placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.card),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = E.c.ink, unfocusedBorderColor = E.c.line, cursorColor = E.c.signal),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter))
        Spacer(Modifier.height(8.dp))
        when {
            hits == null -> Text(stringResource(R.string.search_empty), color = E.c.muted, modifier = Modifier.padding(20.dp))
            hits!!.isEmpty() -> Text(stringResource(R.string.no_results), color = E.c.muted, modifier = Modifier.padding(20.dp))
        }
        LazyColumn {
            items(hits.orEmpty(), key = { it.segmentId }) { h ->
                Column(Modifier.fillMaxWidth().clickable { onOpen(h.meetingId, h.startSec) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("${h.title} · ${fmtClock(h.startSec)}".uppercase(), style = labelStyle, maxLines = 1)
                    Text(highlight(h.text, q), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider(color = E.c.line, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun highlight(text: String, q: String) = buildAnnotatedString {
    val terms = q.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    var i = 0
    val accent = E.c.signal
    while (i < text.length) {
        val hit = terms.mapNotNull { t -> text.indexOf(t, i, ignoreCase = true).takeIf { it >= 0 }?.let { it to t.length } }.minByOrNull { it.first }
        if (hit == null) { append(text.substring(i)); break }
        append(text.substring(i, hit.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(text.substring(hit.first, hit.first + hit.second)) }
        i = hit.first + hit.second
    }
}

// ------------------------------------------------------------------ People
@Composable
fun PeopleScreen(vm: AppVm) {
    val people by vm.people.collectAsState()
    val owner = people.firstOrNull { it.isOwner && it.embedding != null }
    var enrolling by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Person?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) enrolling = true }

    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.tab_people))
        Text(stringResource(R.string.people_body), style = MaterialTheme.typography.bodyMedium, color = E.c.muted,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = Space.gutter).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
            .border(Stroke.hairline, if (owner != null) E.c.verified else E.c.ink, RoundedCornerShape(Radius.card)).padding(Space.m),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (owner != null) Icons.Outlined.RecordVoiceOver else Icons.Outlined.Person, null, tint = if (owner != null) E.c.verified else E.c.ink)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.me), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton({ perm.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(stringResource(if (owner != null) R.string.enroll_again else R.string.enroll_me))
            }
        }
        Spacer(Modifier.height(8.dp))
        val others = people.filterNot { it.isOwner }
        if (others.isEmpty()) Text(stringResource(R.string.people_empty), color = E.c.muted, modifier = Modifier.padding(20.dp))
        LazyColumn {
            items(others, key = { it.id }) { p ->
                var count by remember { mutableIntStateOf(0) }
                LaunchedEffect(p.id) { count = vm.meetingsWith(p.id) }
                Row(Modifier.fillMaxWidth().clickable { editing = p }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).border(Stroke.hairline, E.c.line, CircleShape), contentAlignment = Alignment.Center) {
                        Text(p.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.person_meetings, count), style = MaterialTheme.typography.bodySmall, color = E.c.muted)
                    }
                    if (p.embedding != null) Icon(Icons.Outlined.GraphicEq, null, tint = E.c.muted)
                }
                HorizontalDivider(color = E.c.line, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
    if (enrolling) EnrollDialog(vm) { enrolling = false; vm.resetEnroll() }
    editing?.let { p -> PersonDialog(p, { editing = null }, { vm.renamePerson(p, it); editing = null }, { vm.deletePerson(p); editing = null }) }
}

@Composable
private fun PersonDialog(p: Person, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = E.c.paper,
        title = { Text(p.name) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name_hint)) }, singleLine = true)
                if (confirm) { Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.delete_person_q), color = E.c.signal) }
            }
        },
        confirmButton = { KButton({ onRename(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            TextButton({ if (confirm) onDelete() else confirm = true }) { Text(stringResource(R.string.delete), color = E.c.signal) }
        },
    )
}

@Composable
private fun EnrollDialog(vm: AppVm, onClose: () -> Unit) {
    val st by vm.enroll.collectAsState()
    AlertDialog(
        onDismissRequest = {}, containerColor = E.c.paper,
        title = { Text(stringResource(R.string.enroll_title)) },
        text = {
            Column {
                when (val s = st) {
                    EnrollState.Idle, is EnrollState.Recording -> {
                        Text(stringResource(R.string.enroll_body), style = MaterialTheme.typography.bodyMedium, color = E.c.muted)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.enroll_prompt), style = MaterialTheme.typography.bodyLarge)
                    }
                    else -> {}
                }
                Spacer(Modifier.height(16.dp))
                when (val s = st) {
                    is EnrollState.Recording -> {
                        // Live input level so it's obvious the mic is hearing you.
                        val lvl = (1f + kotlin.math.log10(s.level.coerceAtLeast(0.001f)) / 3f).coerceIn(0f, 1f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(E.c.signal))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.enroll_listening), style = MaterialTheme.typography.labelLarge, color = E.c.signal)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator({ lvl }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(Radius.tag)),
                            color = E.c.signal, trackColor = E.c.line, gapSize = 0.dp, drawStopIndicator = {})
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator({ (s.seconds / 30f).coerceAtMost(1f) }, Modifier.fillMaxWidth().height(2.dp),
                            color = E.c.ink, trackColor = E.c.line, gapSize = 0.dp, drawStopIndicator = {})
                        Text(stringResource(if (s.seconds < 10f) R.string.enroll_keep_going else R.string.enroll_can_finish, s.seconds.toInt()),
                            style = MaterialTheme.typography.bodySmall, color = E.c.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                    is EnrollState.Processing -> {
                        listOf(R.string.enroll_step_speech, R.string.enroll_step_build, R.string.enroll_step_check).forEachIndexed { i, r ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                    when {
                                        i < s.step -> Icon(Icons.Outlined.CheckCircle, null, tint = E.c.verified)
                                        i == s.step -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = E.c.primary)
                                        else -> Box(Modifier.size(10.dp).clip(CircleShape).background(E.c.line))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(r), style = MaterialTheme.typography.bodyLarge,
                                    color = if (i <= s.step) E.c.ink else E.c.muted)
                            }
                        }
                    }
                    is EnrollState.Done -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = E.c.verified, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.enroll_saved), style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.enroll_speech_used, s.speechSec.toInt()), style = MaterialTheme.typography.bodyMedium)
                        s.consistency?.let { c ->
                            val (label, color) = when {
                                c >= 0.7f -> R.string.quality_good to E.c.verified
                                c >= 0.5f -> R.string.quality_ok to E.c.ink
                                else -> R.string.quality_weak to E.c.signal
                            }
                            Text(stringResource(R.string.enroll_consistency, "%.2f".format(c), stringResource(label)),
                                style = MaterialTheme.typography.bodyMedium, color = color)
                            if (c < 0.5f) Text(stringResource(R.string.enroll_redo_hint), style = MaterialTheme.typography.bodySmall, color = E.c.muted)
                        }
                    }
                    is EnrollState.Failed -> Text(stringResource(when (s.reason) {
                        "models" -> R.string.enroll_needs_models
                        "noisy" -> R.string.enroll_noisy
                        else -> R.string.enroll_failed
                    }, s.speechSec.toInt()), color = E.c.signal)
                    EnrollState.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (val s = st) {
                EnrollState.Idle -> KButton({ vm.startEnroll() }) { Text(stringResource(R.string.enroll_start)) }
                is EnrollState.Failed -> KButton({ vm.resetEnroll(); vm.startEnroll() }) { Text(stringResource(R.string.retry)) }
                is EnrollState.Recording -> KButton({ vm.finishEnroll() }, enabled = s.seconds >= 10f) { Text(stringResource(R.string.enroll_finish)) }
                is EnrollState.Processing -> {}
                is EnrollState.Done -> KButton(onClose) { Text(stringResource(R.string.enroll_close)) }
            }
        },
        dismissButton = {
            if (st !is EnrollState.Done && st !is EnrollState.Processing) TextButton(onClose) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// ------------------------------------------------------------------ Settings
@Composable
fun SettingsScreen(vm: AppVm, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val downloads by vm.downloads.collectAsState()
    val tick by vm.modelTick.collectAsState()
    val err by vm.modelError.collectAsState()
    var asr by remember { mutableStateOf(vm.prefs.asrModel) }
    var lang by remember { mutableStateOf(vm.prefs.language) }
    var days by remember { mutableFloatStateOf(vm.prefs.retentionDays.toFloat()) }
    var diar by remember { mutableStateOf(vm.prefs.diarize) }
    var lock by remember { mutableStateOf(vm.prefs.lockEnabled) }
    var loc by remember { mutableStateOf(vm.prefs.locationDefault) }
    var wipe by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(stringResource(R.string.settings), onBack = onBack)

        Section(stringResource(R.string.models))
        key(tick) {
            Models.ALL.forEach { m ->
                val installed = vm.installed(m)
                val prog = downloads[m.id]
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = MaterialTheme.typography.titleSmall)
                        Text("${m.sizeMb} MB", style = monoStyle)
                        if (prog != null) LinearProgressIndicator({ prog }, Modifier.fillMaxWidth().padding(top = Space.xs).height(2.dp),
                            color = E.c.ink, trackColor = E.c.line, gapSize = 0.dp, drawStopIndicator = {})
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        prog != null -> {}
                        !installed -> KOutlinedButton({ vm.install(m) }) { Text(stringResource(R.string.install)) }
                        m in Models.ASR && asr == m.id -> Chip(stringResource(R.string.in_use), color = E.c.ink, dot = E.c.ink)
                        m in Models.ASR -> TextButton({ asr = m.id; vm.prefs.asrModel = m.id }) { Text(stringResource(R.string.use)) }
                        else -> Chip(stringResource(R.string.installed), dot = E.c.verified)
                    }
                }
            }
        }
        err?.let { Text(it, color = E.c.signal, modifier = Modifier.padding(horizontal = 20.dp)) }

        Section(stringResource(R.string.language))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to R.string.lang_auto, "ar" to R.string.lang_ar, "en" to R.string.lang_en).forEach { (k, r) ->
                FilterChip(lang == k, { lang = k; vm.prefs.language = k }, { Text(stringResource(r)) })
            }
        }

        Section(stringResource(R.string.retention))
        Text(if (days.toInt() == 0) stringResource(R.string.retention_zero) else stringResource(R.string.retention_days, days.toInt()),
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Slider(days, { days = it }, valueRange = 0f..30f, steps = 29,
            onValueChangeFinished = { vm.prefs.retentionDays = days.toInt() },
            modifier = Modifier.padding(horizontal = 20.dp),
            colors = kSliderColors())

        Section("")
        ToggleRow(stringResource(R.string.diarize), diar) { diar = it; vm.prefs.diarize = it }
        ToggleRow(stringResource(R.string.app_lock), lock) { lock = it; vm.prefs.lockEnabled = it }
        ToggleRow(stringResource(R.string.location_default), loc) { loc = it; vm.prefs.locationDefault = it; vm.withLocation.value = it }

        Spacer(Modifier.height(24.dp))
        KOutlinedButton({ wipe = true }, Modifier.padding(horizontal = 20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = E.c.signal)) { Text(stringResource(R.string.wipe_all)) }
        Text(stringResource(R.string.privacy_footer), style = MaterialTheme.typography.bodySmall, color = E.c.muted,
            modifier = Modifier.padding(20.dp))
    }

    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false }, containerColor = E.c.paper,
        title = { Text(stringResource(R.string.wipe_all)) },
        text = { Text(stringResource(R.string.wipe_q)) },
        confirmButton = {
            TextButton({
                wipe = false
                vm.wipeEverything {
                    val act = ctx as Activity
                    act.startActivity(Intent(act, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                    act.finishAffinity(); exitProcess(0)
                }
            }) { Text(stringResource(R.string.wipe_all), color = E.c.signal) }
        },
        dismissButton = { TextButton({ wipe = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(Space.l))
    if (title.isNotEmpty()) { SectionLabel(title); Spacer(Modifier.height(Space.s)) }
    else HorizontalDivider(Modifier.padding(horizontal = Space.gutter), thickness = Stroke.hairline, color = E.c.line)
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = kSwitchColors())
    }
}

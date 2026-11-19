package app.katib.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import app.katib.R
import app.katib.audio.Player
import app.katib.data.*
import java.io.File
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(vm: AppVm, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val list by vm.meetings.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.tab_library), onSettings = onSettings,
            eyebrow = if (list.isNotEmpty()) "${list.size} · ${stringResource(R.string.on_device_only)}" else null)
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = Space.xl * 2), horizontalAlignment = Alignment.CenterHorizontally) {
                EngravedLogo(Modifier.size(132.dp).alpha(0.35f))
                Spacer(Modifier.height(Space.m))
                Text(stringResource(R.string.library_empty), color = E.c.muted, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Space.xl))
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = Space.l)) {
            items(list, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(m.id) }.padding(horizontal = Space.gutter, vertical = Space.m)) {
                    // Ledger entry number
                    Text("№%03d".format(m.id), style = monoStyle.copy(fontSize = 11.sp), modifier = Modifier.width(44.dp).padding(top = 6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${fmtDate(m.createdAt)} · ${fmtClock(m.durationSec)}".uppercase(), style = labelStyle)
                        Spacer(Modifier.height(2.dp))
                        Text(m.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(Space.s))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (m.kind == "note") Chip(stringResource(R.string.mode_note), color = E.c.ink)
                            StatusChip(m); RetentionChip(m)
                            if (m.otherVoices) Chip(stringResource(R.string.other_voices_short), color = E.c.signal)
                        }
                    }
                }
                HorizontalDivider(color = E.c.line, thickness = Stroke.hairline, modifier = Modifier.padding(horizontal = Space.gutter))
            }
        }
    }
}

@Composable
fun StatusChip(m: Meeting) {
    when (m.status) {
        Status.RECORDING -> Chip(stringResource(R.string.status_recording), color = E.c.signal, dot = E.c.signal)
        Status.QUEUED -> Chip(stringResource(R.string.status_queued))
        Status.TRANSCRIBING -> Chip(stringResource(R.string.status_transcribing, (m.progress * 100).toInt()), color = E.c.ink, dot = E.c.ink)
        Status.FAILED -> Chip(stringResource(R.string.status_failed), color = E.c.signal)
        else -> {}
    }
}

@Composable
fun RetentionChip(m: Meeting) {
    if (m.audioDeleted) { Chip(stringResource(R.string.audio_deleted)); return }
    val at = m.audioDeleteAt ?: return
    val left = at - System.currentTimeMillis()
    val days = TimeUnit.MILLISECONDS.toDays(left).toInt()
    if (days >= 1) Chip(stringResource(R.string.audio_days_left, days))
    else Chip(stringResource(R.string.audio_hours_left, TimeUnit.MILLISECONDS.toHours(left).toInt().coerceAtLeast(0)))
}

@Composable
fun stageLabel(stage: String?) = when (stage) {
    "vad" -> stringResource(R.string.stage_vad)
    "asr" -> stringResource(R.string.stage_asr)
    "diarize" -> stringResource(R.string.stage_diarize)
    "voices" -> stringResource(R.string.stage_voices)
    else -> ""
}

/** Display name for a speaker cluster. */
@Composable
fun speakerName(sp: MeetingSpeaker?, idx: Int, people: List<Person>): String {
    val p = sp?.personId?.let { id -> people.firstOrNull { it.id == id } }
    return when {
        p?.isOwner == true -> stringResource(R.string.me)
        p != null -> p.name
        idx < 0 -> stringResource(R.string.unknown)
        else -> stringResource(R.string.speaker_n, idx + 1)
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MeetingScreen(vm: AppVm, id: Long, seekTo: Float, onBack: () -> Unit) {
    val m by vm.meeting(id).collectAsState(null)
    val segs by vm.segments(id).collectAsState(emptyList())
    val speakers by vm.speakers(id).collectAsState(emptyList())
    val people by vm.people.collectAsState()
    val prov by vm.provenance(id).collectAsState(emptyList())
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var consent by remember { mutableStateOf<List<ConsentLog>>(emptyList()) }
    LaunchedEffect(id) { consent = vm.consentFor(id) }

    val meeting = m ?: return
    val audio = meeting.audioPath?.let(::File)?.takeIf { it.exists() }
    val player = remember(audio) { audio?.let(::Player) }
    DisposableEffect(player) { onDispose { player?.release() } }
    val pos by (player?.position ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0f) }).collectAsState()
    val playing by (player?.playing ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()

    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Segment?>(null) }
    var labeling by remember { mutableStateOf<MeetingSpeaker?>(null) }
    val byIdx = speakers.associateBy { it.idx }
    val threshold = vm.prefs.matchThreshold

    val listState = rememberLazyListState()
    LaunchedEffect(seekTo, segs.size) {
        if (seekTo >= 0 && segs.isNotEmpty()) {
            val i = segs.indexOfFirst { it.endSec >= seekTo }.coerceAtLeast(0)
            listState.scrollToItem(i + 1)
        }
    }
    val activeIdx = if (playing) segs.indexOfLast { it.startSec <= pos + 0.05f } else -1

    Column(Modifier.fillMaxSize()) {
        Header(meeting.title, onBack = onBack) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "menu") }
                DropdownMenu(menu, { menu = false }, containerColor = E.c.paper) {
                    DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menu = false; renaming = true })
                    DropdownMenuItem({ Text(stringResource(R.string.export_text)) }, {
                        menu = false
                        val body = buildString {
                            appendLine(meeting.title); appendLine(fmtDate(meeting.createdAt)); appendLine()
                            segs.forEach { s ->
                                val sp = byIdx[s.speakerIdx]
                                val p = sp?.personId?.let { pid -> people.firstOrNull { it.id == pid } }
                                val name = p?.name ?: if (s.speakerIdx >= 0) "Speaker ${s.speakerIdx + 1}" else ""
                                appendLine("[${fmtClock(s.startSec)}] ${if (name.isNotEmpty()) "$name: " else ""}${s.text}")
                            }
                        }
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, body).putExtra(Intent.EXTRA_SUBJECT, meeting.title), null))
                    })
                    if (audio != null) {
                        DropdownMenuItem({ Text(stringResource(R.string.retranscribe)) }, { menu = false; vm.retranscribe(id) })
                        DropdownMenuItem({ Text(stringResource(R.string.delete_audio_now)) }, { menu = false; player?.stop(); vm.deleteAudioNow(id) })
                    }
                    if (prov.isNotEmpty()) DropdownMenuItem({ Text(stringResource(R.string.export_evidence)) }, {
                        menu = false
                        scope.launch {
                            val f = vm.evidenceFile(id)
                            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json")
                                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                        }
                    })
                    DropdownMenuItem({ Text(stringResource(R.string.delete_meeting), color = E.c.signal) }, { menu = false; confirmDelete = true })
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("${fmtDate(meeting.createdAt)} · ${fmtClock(meeting.durationSec)}", style = monoStyle)
                    if (meeting.kind == "note") Text(stringResource(R.string.mode_note), style = MaterialTheme.typography.bodySmall, color = E.c.muted)
                    else consent.firstOrNull()?.let {
                        Text(stringResource(R.string.consent_log, it.participants, it.method),
                            style = MaterialTheme.typography.bodySmall, color = E.c.muted)
                    }
                    if (meeting.lat != null && meeting.lon != null) Text(
                        "⌖ %.5f, %.5f".format(meeting.lat, meeting.lon) + (meeting.locAccuracyM?.let { " ±%.0f m".format(it) } ?: ""),
                        style = monoStyle)
                    ProvenancePanel(meeting, prov) { app.katib.audio.RecorderService.sealRequests.tryEmit(-id) }
                    if (meeting.otherVoices) Text(stringResource(R.string.other_voices_warning), color = E.c.signal,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    if (meeting.model != null && meeting.rtf != null)
                        Text(stringResource(R.string.processed_with, meeting.model, meeting.rtf), style = MaterialTheme.typography.bodySmall, color = E.c.muted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusChip(meeting); RetentionChip(meeting) }

                    if (meeting.status == Status.TRANSCRIBING || meeting.status == Status.QUEUED) {
                        Spacer(Modifier.height(12.dp))
                        Text(stageLabel(meeting.stage).uppercase(), style = labelStyle.copy(color = E.c.ink))
                        LinearProgressIndicator({ meeting.progress }, Modifier.fillMaxWidth().padding(vertical = 6.dp).height(2.dp),
                            color = E.c.ink, trackColor = E.c.line, gapSize = 0.dp, drawStopIndicator = {})
                    }
                    if (meeting.status == Status.FAILED) {
                        Spacer(Modifier.height(12.dp))
                        Text(meeting.error ?: "", color = E.c.signal, style = MaterialTheme.typography.bodyMedium)
                        if (audio != null) TextButton(onClick = { vm.retranscribe(id) }) { Text(stringResource(R.string.retry)) }
                    }

                    if (speakers.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.speakers).uppercase(), style = labelStyle)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            speakers.forEach { sp ->
                                val weak = sp.personId != null && sp.score < 1f && sp.score < threshold + 0.1f
                                Row(Modifier.clip(RoundedCornerShape(Radius.card)).border(Stroke.hairline, E.c.line, RoundedCornerShape(Radius.card))
                                    .clickable { labeling = sp }.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(speakerColor(sp.idx)))
                                    Spacer(Modifier.width(Space.s))
                                    Text(speakerName(sp, sp.idx, people), style = MaterialTheme.typography.labelLarge)
                                    if (weak) Text(" · " + stringResource(R.string.low_confidence), style = MaterialTheme.typography.labelMedium, color = E.c.muted)
                                }
                            }
                        }
                    }
                    if (meeting.status == Status.DONE && segs.isEmpty()) {
                        Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.no_speech), color = E.c.muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = E.c.line)
                }
            }
            itemsIndexed(segs, key = { _, s -> s.id }) { i, s ->
                val prev = segs.getOrNull(i - 1)
                val newTurn = prev == null || prev.speakerIdx != s.speakerIdx
                val active = i == activeIdx || (seekTo >= 0 && !playing && s.startSec <= seekTo && s.endSec >= seekTo)
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (active) E.c.surface else E.c.paper)
                        .combinedClickable(onClick = { player?.play(s.startSec) }, onLongClick = { editing = s })
                        .padding(start = Space.gutter, end = Space.gutter, top = if (newTurn) Space.m + 2.dp else 2.dp, bottom = 2.dp),
                ) {
                    Box(Modifier.width(Stroke.rule).heightIn(min = 24.dp).fillMaxHeight().background(speakerColor(s.speakerIdx)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (newTurn) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(speakerName(byIdx[s.speakerIdx], s.speakerIdx, people).uppercase(),
                                style = labelStyle.copy(color = speakerColor(s.speakerIdx), fontSize = 12.sp))
                            Spacer(Modifier.width(8.dp))
                            Text(fmtClock(s.startSec), style = monoStyle.copy(fontSize = 11.sp))
                        }
                        Text(s.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        if (audio != null) {
            HorizontalDivider(color = E.c.line)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (playing) player?.stop() else player?.play(pos) }) {
                    Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                }
                Text(fmtClock(pos), style = monoStyle)
                var drag by remember { mutableStateOf<Float?>(null) }
                Slider(
                    value = (drag ?: pos).coerceIn(0f, meeting.durationSec.coerceAtLeast(1f)),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { player?.play(it) }; drag = null }, valueRange = 0f..meeting.durationSec.coerceAtLeast(1f),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = kSliderColors(),
                )
                Text(fmtClock(meeting.durationSec), style = monoStyle)
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = E.c.paper,
        text = { Text(stringResource(R.string.delete_meeting_q)) },
        confirmButton = { TextButton({ confirmDelete = false; player?.stop(); vm.deleteMeeting(id); onBack() }) {
            Text(stringResource(R.string.delete), color = E.c.signal) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (renaming) TextDialog(stringResource(R.string.rename), meeting.title, { renaming = false }) { vm.rename(id, it); renaming = false }
    editing?.let { s -> TextDialog(stringResource(R.string.edit_segment), s.text, { editing = null }, multiline = true) { vm.editSegment(s.id, it); editing = null } }
    labeling?.let { sp -> LabelSpeakerDialog(sp, people, { labeling = null }) { p, n, r -> vm.labelSpeaker(sp, p, n, r); labeling = null } }
}

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, multiline: Boolean = false, onSave: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = E.c.paper,
        title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = !multiline, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { KButton({ onSave(t.trim()) }, enabled = t.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LabelSpeakerDialog(sp: MeetingSpeaker, people: List<Person>, onDismiss: () -> Unit,
                               onPick: (Person?, String?, Boolean) -> Unit) {
    var newName by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(false) }
    val canRemember = sp.embedding != null
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = E.c.paper,
        title = { Text(stringResource(R.string.name_speaker)) },
        text = {
            Column {
                people.forEach { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(p, null, remember && canRemember) }
                        .padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sp.personId == p.id, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (p.isOwner) stringResource(R.string.me) else p.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.new_person)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (canRemember) Row(Modifier.clickable { remember = !remember }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(remember, { remember = it })
                    Text(stringResource(R.string.remember_voice), style = MaterialTheme.typography.bodySmall)
                }
                if (sp.personId != null) TextButton({ onPick(null, null, false) }) { Text(stringResource(R.string.not_this_person)) }
            }
        },
        confirmButton = {
            KButton({ onPick(null, newName, remember && canRemember) }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ProvenancePanel(m: Meeting, prov: List<ProvenanceRecord>, onSeal: () -> Unit) {
    val capture = prov.firstOrNull { it.kind == "capture" }
    val versions = prov.count { it.kind == "transcript" }
    Spacer(Modifier.height(Space.m))
    // Styled as a seal stamp: verified = moss border; unsealed = dashed-feel hairline.
    Panel(Modifier.fillMaxWidth(), border = if (capture != null) E.c.verified else E.c.line, pad = Space.m - 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (capture != null) Icons.Outlined.VerifiedUser else Icons.Outlined.GppMaybe, null,
                tint = if (capture != null) E.c.verified else E.c.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
            Text(stringResource(when {
                    capture != null -> R.string.prov_sealed
                    m.kind == "note" -> R.string.prov_note_unsigned
                    else -> R.string.prov_unsealed
                }),
                style = MaterialTheme.typography.titleSmall, color = if (capture != null) E.c.verified else E.c.ink,
                modifier = Modifier.weight(1f))
            if (capture == null && m.audioSha256 != null) TextButton(onSeal) {
                Text(stringResource(if (m.kind == "note") R.string.sign_note else R.string.seal_now)) }
        }
        Spacer(Modifier.height(Space.xs))
        m.audioSha256?.let { KV("audio", it.take(16) + "…") }
        capture?.let { KV("seal", it.payloadSha256.take(16) + "…") ; KV("at", fmtDate(it.createdAt)) }
        if (versions > 0) KV("ver", stringResource(R.string.prov_versions, versions))
        if (m.audioSha256 == null) Text(stringResource(R.string.prov_legacy), style = MaterialTheme.typography.bodySmall, color = E.c.muted)
    }
}

/** Ledger key/value line in mono. */
@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(k.uppercase(), style = labelStyle, modifier = Modifier.width(56.dp))
        Text(v, style = monoStyle.copy(fontSize = 12.sp, color = E.c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

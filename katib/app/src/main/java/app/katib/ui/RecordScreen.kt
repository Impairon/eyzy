package app.katib.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.katib.R
import app.katib.asr.Models

@Composable
fun RecordScreen(vm: AppVm, onSettings: () -> Unit, onOpen: (Long) -> Unit) {
    val rec by vm.rec.collectAsState()
    val consent by vm.consent.collectAsState()
    val title by vm.title.collectAsState()
    val personal by vm.personal.collectAsState()
    val withLoc by vm.withLocation.collectAsState()
    val ctx = LocalContext.current
    var showConsent by remember { mutableStateOf(false) }
    var permDenied by remember { mutableStateOf(false) }
    val tick by vm.modelTick.collectAsState()
    val hasAsr = remember(tick) { Models.ASR.any { Models.installed(ctx, it) } }
    val defaultTitle = stringResource(R.string.untitled_meeting) + " · " + fmtDate(System.currentTimeMillis())

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) {
            permDenied = false
            val loc = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            vm.startRecording(defaultTitle, loc)
        }
        else permDenied = true
    }
    fun start() {
        val list = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (withLoc) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
        }
        perms.launch(list.toTypedArray())
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(stringResource(R.string.app_name), onSettings = onSettings, mark = true)
        // Persistent privacy cues
        Row(Modifier.padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Chip(stringResource(R.string.on_device_only), color = E.c.ink, dot = E.c.verified)
            Chip(stringResource(R.string.sync_off))
            Chip(stringResource(R.string.audio_kept_days, vm.prefs.retentionDays))
        }
        Spacer(Modifier.height(Space.l))

        if (!rec.recording) {
            // Mode: consent-gated meeting vs personal note (owner only). Ledger tabs, not a pill toggle.
            ModeTabs(personal) { vm.personal.value = it }
            Spacer(Modifier.height(Space.l))
            TitleInput(title, stringResource(if (personal) R.string.mode_note else R.string.meeting_title_hint)) { vm.title.value = it }
            Spacer(Modifier.height(Space.m))
            if (personal) {
                Panel(Modifier.padding(horizontal = Space.gutter).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.EditNote, null, tint = E.c.ink)
                        Spacer(Modifier.width(Space.m))
                        Text(stringResource(R.string.note_explainer), style = MaterialTheme.typography.bodyMedium, color = E.c.muted)
                    }
                }
            } else ConsentCard(consent) { showConsent = true }
            Row(Modifier.fillMaxWidth().clickable { vm.withLocation.value = !withLoc }.padding(horizontal = Space.gutter, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = if (withLoc) E.c.ink else E.c.muted)
                Spacer(Modifier.width(Space.m))
                Text(stringResource(R.string.attach_location), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(withLoc, { vm.withLocation.value = it }, colors = kSwitchColors())
            }
        }

        Spacer(Modifier.height(if (rec.recording) Space.l else Space.xl))
        val live = rec.recording && !rec.paused
        val stateText = when {
            rec.recording && rec.paused -> stringResource(R.string.paused)
            rec.recording -> stringResource(R.string.recording)
            else -> stringResource(R.string.ready_to_record)
        }
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            if (live) { LiveDot(); Spacer(Modifier.width(Space.s)) }
            Text(stateText.uppercase(), style = labelStyle.copy(color = if (live) E.c.signal else E.c.muted, fontSize = 12.sp, letterSpacing = 2.sp))
        }
        Text(fmtClock(rec.elapsedMs / 1000f),
            style = TextStyle(fontFamily = PlexMono, fontSize = 64.sp, color = if (rec.recording) E.c.ink else E.c.muted, letterSpacing = (-1).sp),
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Space.s))
        LevelMeter(rec.levels, active = live,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.xl))
        Spacer(Modifier.height(Space.l))

        val enabled = rec.recording || consent != null || personal
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            if (rec.recording) {
                KOutlinedButton(onClick = { if (rec.paused) vm.resume() else vm.pause() }, modifier = Modifier.size(56.dp)) {
                    Icon(if (rec.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        stringResource(if (rec.paused) R.string.resume else R.string.pause))
                }
            }
            val desc = stringResource(if (rec.recording) R.string.stop_recording else R.string.start_recording)
            RecordButton(recording = rec.recording, live = live, enabled = enabled, description = desc) {
                if (rec.recording) vm.stopRecording() else start()
            }
            if (rec.recording) Spacer(Modifier.size(56.dp))
        }
        Spacer(Modifier.height(Space.m))
        if (!rec.recording && !enabled) Text(stringResource(R.string.consent_required), style = labelStyle,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        if (permDenied) Text(stringResource(R.string.mic_permission), color = E.c.signal,
            modifier = Modifier.padding(horizontal = Space.gutter).align(Alignment.CenterHorizontally))
        if (!hasAsr && !rec.recording) {
            Panel(Modifier.padding(Space.gutter).fillMaxWidth(), fill = E.c.surface) {
                Text(stringResource(R.string.model_missing), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onSettings, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.open_models)) }
            }
        }
        Spacer(Modifier.height(Space.l))
    }

    if (showConsent) ConsentDialog(withLoc, onDismiss = { showConsent = false }) { p, m -> vm.consent.value = ConsentDraft(p, m); showConsent = false }
}

@Composable
private fun ConsentCard(consent: ConsentDraft?, onClick: () -> Unit) {
    val ok = consent != null
    Panel(Modifier.padding(horizontal = Space.gutter).fillMaxWidth().clip(RoundedCornerShape(Radius.card)).clickable(onClick = onClick),
        border = if (ok) E.c.verified else E.c.ink) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Outlined.VerifiedUser else Icons.Outlined.Groups, null, tint = if (ok) E.c.verified else E.c.ink)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(if (ok) stringResource(R.string.consent_confirmed, consent!!.method) else stringResource(R.string.consent_required),
                    style = MaterialTheme.typography.titleSmall)
                if (ok) Text(consent!!.participants, style = MaterialTheme.typography.bodyMedium, color = E.c.muted, maxLines = 2)
            }
            if (!ok) Text(stringResource(R.string.confirm_consent).uppercase(), style = labelStyle.copy(color = E.c.ink))
        }
    }
}

/** Two-option ledger tabs: label text with an ink rule under the selected one. */
@Composable
private fun ModeTabs(personal: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter)) {
        listOf(false to R.string.mode_meeting, true to R.string.mode_note).forEach { (v, r) ->
            val sel = personal == v
            val rule by animateColorAsState(if (sel) E.c.ink else E.c.line, tween(Motion.base, easing = Motion.standard), label = "tab")
            Column(Modifier.weight(1f).clickable { onChange(v) }) {
                Text(stringResource(r), style = MaterialTheme.typography.titleMedium, color = if (sel) E.c.ink else E.c.muted,
                    modifier = Modifier.padding(vertical = Space.s))
                Box(Modifier.fillMaxWidth().height(if (sel) 2.dp else Stroke.hairline).background(rule))
            }
        }
    }
}

/** Title input set in the display serif, underlined like a form field on paper. */
@Composable
private fun TitleInput(value: String, placeholder: String, onChange: (String) -> Unit) {
    val style = MaterialTheme.typography.headlineSmall.copy(color = E.c.ink)
    Column(Modifier.padding(horizontal = Space.gutter)) {
        androidx.compose.foundation.text.BasicTextField(
            value, onChange, singleLine = true, textStyle = style,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(E.c.signal),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(placeholder, style = style.copy(color = E.c.muted.copy(alpha = 0.6f))); inner() }
            },
        )
        Spacer(Modifier.height(Space.xs))
        HorizontalDivider(thickness = Stroke.hairline, color = E.c.line)
    }
}

@Composable
private fun LiveDot() {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(8.dp).clip(CircleShape).background(E.c.signal.copy(alpha = a)))
}

/**
 * The record control, drawn like the engraved logo: a solid core inside concentric hairline rings.
 * Idle = ink. Live = vermilion, rings breathe outward. Disabled = rings only.
 */
@Composable
private fun RecordButton(recording: Boolean, live: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    val core by animateColorAsState(when { !enabled -> E.c.line; recording -> E.c.signal; else -> E.c.ink },
        tween(Motion.base, easing = Motion.standard), label = "core")
    val ringColor = if (recording) E.c.signal else E.c.ink
    val t = rememberInfiniteTransition(label = "rings")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "phase")
    val onCore = if (recording) E.c.onSignal else E.c.paper
    Box(
        Modifier.size(132.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r0 = size.minDimension * 0.30f
            val rings = 7
            for (i in 0 until rings) {
                val f = (i + if (live) phase else 0f) / rings
                val r = r0 + (size.minDimension / 2 - r0) * f
                val alpha = (if (enabled) 0.55f else 0.25f) * (1f - f)
                drawCircle(ringColor, r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f), alpha = alpha)
            }
            drawCircle(core, r0)
        }
        if (recording) Box(Modifier.size(24.dp).clip(RoundedCornerShape(Radius.tag)).background(onCore))
        else Icon(Icons.Outlined.Mic, null, tint = onCore, modifier = Modifier.size(30.dp))
    }
}

@Composable
fun ConsentDialog(withLocation: Boolean, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var participants by remember { mutableStateOf("") }
    val methods = listOf(stringResource(R.string.method_verbal), stringResource(R.string.method_written), stringResource(R.string.method_notice))
    var method by remember { mutableStateOf(methods[0]) }
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = E.c.paper,
        title = { Text(stringResource(R.string.consent_title)) },
        text = {
            Column {
                Text(stringResource(R.string.consent_body), style = MaterialTheme.typography.bodyMedium, color = E.c.muted)
                if (withLocation) Text(stringResource(R.string.consent_location), style = MaterialTheme.typography.bodyMedium,
                    color = E.c.ink, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(participants, { participants = it }, label = { Text(stringResource(R.string.participants_hint)) },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    methods.forEach { m -> FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) }) }
                }
                Row(Modifier.clickable { checked = !checked }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, { checked = it })
                    Text(stringResource(R.string.consent_check), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            KButton(onClick = { onConfirm(participants.trim(), method) }, enabled = checked && participants.isNotBlank()) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LevelMeter(levels: List<Float>, active: Boolean, modifier: Modifier) {
    val ink = if (active) E.c.ink else E.c.muted.copy(alpha = 0.35f)
    val line = E.c.line
    Canvas(modifier) {
        val bars = 60
        val w = size.width / bars
        drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        val data = levels.takeLast(bars)
        data.forEachIndexed { i, db ->
            val norm = ((db + 60f) / 60f).coerceIn(0.03f, 1f)
            val h = norm * size.height
            drawRoundRect(ink, Offset(i * w + w * 0.3f, (size.height - h) / 2), Size(w * 0.4f, h), CornerRadius(w))
        }
    }
}

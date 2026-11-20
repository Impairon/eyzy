# Swizy + Katib: Integration, Arabic UX, and Implementation Plan

**Prepared:** 19 November 2026  
**Repository reviewed:** `Impairon/eyzy`, branch `pushy`  
**Purpose:** A practical plan for bringing selected Katib capabilities into Swizy without turning Swizy into a meeting recorder.

## 1. Product direction

Keep **Swizy** as the main product: Lowery privacy masking, manual masking profiles, Sibha, prayer times, and Qibla. Add a focused **Personal Notes** area for private voice notes and on-device transcription.

The requested scope is:
1. An optional biometric app lock controlled by a Settings toggle.
2. A cleaner, consistent Arabic-first interface with correct RTL, with English as a secondary language.
3. Personal voice-note recording and transcription.
4. In-app management of speech models: download, verify, select, and remove.
5. A user-entered note title. If the title is blank, generate `ملاحظة_yyyy-MM-dd_HH-mm` using local time. Never label the item as a meeting or call it `اجتماع`.

**Core product rule:** this is a personal note-taking flow. Do not import meeting consent screens, participant management, speaker identity enrollment, location capture, or legal-evidence language into the default flow.

## 2. What the repository currently contains

### Swizy (the main app)

The main app is a compact Android utility with Lowery avatar/profile-picture masking, manual screen-region masking, per-app/per-orientation profiles, Sibha, prayer-time display and caching, Qibla compass, and optional headset-button time announcements. The main screen implementation is a large programmatic Android `Activity` (`MainActivity.kt`, around 1,437 lines) that builds views directly. Navigation is a side menu with five pages. Many screen labels and descriptions are hard-coded in English. The current manifest does not declare `supportsRtl`, and the app has no Arabic `values-ar/strings.xml` resource directory in the reviewed tree.

The root app currently uses AGP 8.5.2, Kotlin Android plugin 1.9.24, compile/target SDK 34, and min SDK 24. Preserve the Lowery accessibility service and overlay behavior while adding features; it is the core privacy function and should not be destabilized by a UI migration.

### Katib (the source project)

Katib is a second Android app nested under `katib/`. It uses Kotlin and Jetpack Compose, a `ViewModel`/`StateFlow` pattern, Room, SQLCipher, WorkManager, a foreground microphone service, an on-device sherpa-onnx speech pipeline, and a Settings screen for model installation. It already distinguishes personal notes from meetings in its data (`Meeting.kind == "note"`) and gives personal notes a consent-free capture path.

Useful source areas:
- `katib/app/src/main/java/app/katib/ui/RecordScreen.kt` — personal note versus meeting choice, note title, recording state, timer and level meter.
- `katib/app/src/main/java/app/katib/ui/OtherScreens.kt` — model-management settings, language selection, app-lock toggle, searchable library-related settings.
- `katib/app/src/main/java/app/katib/ui/AppVm.kt` — ViewModel state, recording actions, database work, transcription queue and note/meeting distinction.
- `katib/app/src/main/java/app/katib/audio/RecorderService.kt` — foreground microphone capture, pause/resume/stop and persistent notification.
- `katib/app/src/main/java/app/katib/asr/Models.kt` and `Pipeline.kt` — model catalogue, checksum verification, language selection and speech pipeline.
- `katib/app/src/main/java/app/katib/data/Db.kt` — Room data model, transcript segments and full-text search.
- `katib/app/src/main/java/app/katib/ui/Theme.kt`, `Tokens.kt`, and `katib/design/tokens.json` — fonts, themes, spacing, semantic colors and component tokens.
- `katib/docs/ARCHITECTURE.md` and `THREAT_MODEL.md` — useful architecture and security reasoning.

Katib is explicitly described by its README as **alpha** and says it was tested on one Galaxy S23 Ultra. Treat it as a valuable reference implementation, not a production-certified library or a security audit.

## 3. What to take from Katib

| Take / adapt | Why it is useful for Swizy | How to adapt it |
|---|---|---|
| Personal-note capture flow | It separates a private voice note from a meeting. | Keep only the note path. Remove meeting terminology and participant consent from Swizy's note UX. |
| Foreground recorder service | Recording continues with the screen off and has visible system controls. | Adapt service lifecycle, pause/resume/stop actions and ongoing notification. Start it only from a visible user action after microphone permission. |
| Background transcription worker | Long inference should not block the UI. | Queue transcription after a recording stops; expose stage/progress/retry in the note screen. |
| Model catalogue and checksum checks | Users can install models without sideloading files. | Keep model IDs, download URL, expected size, required files and pinned SHA-256. Add retry/cancel, free-space checks and clear storage information. |
| Room data layer and transcript segments | Better than storing a large history as JSON preferences. | Use a dedicated `Note` entity and `TranscriptSegment` records; do not inherit `Meeting` naming. |
| Full-text search | Makes recorded notes useful after the first day. | Search note titles and transcript text locally; show matching text and jump to its timestamp. |
| ViewModel + StateFlow | Separates UI state from recorder/database implementation. | Make Notes screens observe a `NotesViewModel`; do not put recording/database logic in `MainActivity`. |
| Design tokens and Arabic typography | Gives the app a coherent style and a real Arabic type system. | Reuse the token approach and font pairing, while adjusting corner radii and colors to Swizy's brand. |
| Biometric lock behavior | Provides a known app-lock flow and settings toggle. | Reuse the lifecycle pattern, but define explicit behavior for disabled lock, failed auth and device fallback. |

### Do not copy into the first version

- Meeting consent dialog, participant list, consent ledger, and legal evidence claims.
- Speaker diarization, enrolled voice profiles, voice embeddings, and speaker-label editing.
- GPS/location attachment to notes.
- Hardware-attested evidence bundles, signing every edit, external verifier and legal-chain-of-custody UI.
- Katib's five-day audio deletion default. Swizy should not silently delete a user's note audio; make retention an explicit preference.
- Katib's build files wholesale. Its build requires newer toolchain/API settings than the root app and its min SDK is 29 while Swizy currently supports API 24.

## 4. The four requested features

### 4.1 Optional app lock

**Settings label:** `قفل التطبيق بالبصمة` (or `قفل التطبيق`). It is one visible on/off toggle.

Expected behavior:
- Off: Swizy opens normally and never prompts for biometrics.
- On: prompt when the app is opened and after it has been backgrounded beyond the chosen lock delay. Start with a 60-second delay (Katib's current behavior); make the delay a later optional setting if needed.
- Use Android's system biometric prompt. Prefer strong biometrics and allow device credential as a fallback unless the product explicitly chooses fingerprint-only behavior. The prompt may therefore offer PIN, pattern or password depending on the device configuration.
- Cancel/failed authentication keeps private screens locked; do not silently unlock.
- Do not stop an active recording when the user backgrounds the app. The foreground service and its notification continue; the next time the user opens Swizy, the app lock is applied.
- If biometrics are unavailable or not enrolled, explain the device requirement and offer a supported device-credential fallback. Do not leave the user in an unbreakable lockout state.

**Important security distinction:** an app lock is a UI privacy barrier, not encryption. It does not by itself encrypt note audio or transcript files. If Swizy promises local privacy for notes, encrypt private content at rest in a separately designed phase and test key lifecycle carefully.

### 4.2 Personal voice notes

Use only the concepts `ملاحظة`, `ملاحظاتي`, and `تسجيل ملاحظة`. Do not show `اجتماع`, `اجتماعات`, `مشارك`, or meeting-consent UI.

Recommended flow:
1. User opens **ملاحظاتي** and taps **+ ملاحظة صوتية**.
2. Show a simple title field labeled **اسم الملاحظة**. The field is optional; helper text says that leaving it empty creates an automatic name.
3. User taps **بدء التسجيل**. Request microphone permission only here, with a clear explanation.
4. Show a focused recording screen: title, elapsed timer, live audio level, pause/resume, and a large stop button. Show a clear recording indicator and keep the Android foreground notification visible.
5. On stop, save the audio immediately and create a transcription job. Never lose the recording just because a model is missing or transcription fails.
6. Show status: waiting for transcription, transcribing with progress/stage, ready, or failed with retry.
7. Open the note to play audio, read/edit the transcript, rename it, share/export it explicitly, or delete it.

**Title rule:** trim whitespace. If non-empty, use the user's title. Otherwise generate:

```text
ملاحظة_yyyy-MM-dd_HH-mm
Example: ملاحظة_2026-11-19_21-15
```

Use the device's local time. Keep this fallback generation deterministic and unique (add seconds or a short suffix if two notes are created in the same minute). The display can be localized, but the saved title should remain stable. A note must be named once at creation, but it must remain renameable later.

**Note data model (recommended):**
- `Note`: stable ID, title, created timestamp, duration, local audio path, selected language, model ID/version, transcription status, progress/stage, error, and optional retention setting.
- `TranscriptSegment`: note ID, start/end time, text, and stable order. Store as segments so tapping a line can jump playback to that point. For the first release, speaker IDs are unnecessary.
- `NoteSettings`: selected ASR model, language preference, whether to keep original audio, and app-lock preference.

Keep raw audio and transcript local by default. Sharing happens only after the user explicitly selects Share/Export. If audio retention is configurable, default to keeping it until the user deletes it; offer “delete audio after transcription” as an opt-in, not a surprise.

### 4.3 In-app model installation

Katib's current model catalogue lists approximately:
- Qwen3-ASR 0.6B int8: **879 MB**, described in the app as Arabic + English.
- Whisper base: **208 MB**, described as multilingual.
- Silero VAD: **1 MB**.
- Speaker segmentation: **7 MB**; speaker embedding: **27 MB**.

For Swizy's personal-note feature, start with one ASR model and optionally VAD. Do **not** download the speaker segmentation or speaker embedding models in the first release; they solve a meeting/diarization problem that personal notes do not need.

Recommended Settings UI for each model:
- Name, supported languages, download size, and a one-sentence quality/performance description.
- State: Not installed / Downloading / Verifying / Installed / In use / Failed.
- Progress bar and downloaded size; Cancel and Retry actions.
- “Use this model” selection; do not auto-switch silently after a failure.
- Delete model action with confirmation and a warning that existing transcripts are not deleted.
- Wi-Fi-only preference, storage estimate, free-space check, and a clear “models stay on this device” explanation.

Downloader requirements:
- Run on an I/O dispatcher; never block the main thread.
- Download to a temporary file, verify pinned SHA-256, then atomically move/extract to the final model directory.
- Validate the required model files after extraction.
- Handle interrupted downloads, insufficient disk space, checksum mismatch, HTTP errors, cancellation and retry.
- Keep one model download active at a time initially to avoid memory/storage pressure.
- A missing model must not prevent saving a recording. Keep the audio and mark the note as waiting for a model/transcription.

**Arabic accuracy warning:** Qwen3-ASR lists Arabic as a supported language, but the current published dialect table does not list Egyptian Arabic. Katib's README specifically calls out Gulf Arabic and Modern Standard Arabic and says the project is alpha. Do not advertise reliable Egyptian colloquial transcription until you test it with real Egyptian speech. Build a small local evaluation set (quiet room, noisy room, fast speech, MSA, Egyptian colloquial, Arabic-English code-switching) and compare transcript error rate and latency on the actual target phones.

### 4.4 Arabic-first UI and UX

Arabic should be the default interface, not a translated afterthought. Add complete `values-ar/strings.xml` resources, enable RTL in the manifest, and use Android's per-app language support so the user can switch to English without changing the phone language.

Typography guidance from Katib is a good starting point:
- IBM Plex Sans Arabic for body text, labels, buttons and settings.
- Amiri for large Arabic display headings only; it is beautiful but too calligraphic for dense small UI text.
- A mono font for timers, model sizes and technical metadata; keep Latin tokens, file names and hashes LTR.

RTL test cases:
- Navigation order and icon direction are mirrored correctly.
- Back/forward arrows are semantic and use auto-mirrored icons.
- Row order, sliders, progress bars and swipe navigation behave intentionally.
- Numbers, dates, file paths, model names, URLs, latitude/longitude and hashes stay legible and do not reorder punctuation.
- Long Arabic titles wrap without clipping; mixed Arabic/English transcripts remain readable.
- Test at larger font scale and with TalkBack; all icons/actions need accessible descriptions.

Starter Arabic copy:

| Purpose | Arabic label |
|---|---|
| Notes section | ملاحظاتي |
| New voice note | + ملاحظة صوتية |
| Note title | اسم الملاحظة |
| Auto-title hint | اتركه فارغًا لإنشاء اسم تلقائي |
| Start / stop | بدء التسجيل / إيقاف التسجيل |
| Pause / resume | إيقاف مؤقت / متابعة |
| Transcribing | جارٍ تحويل الصوت إلى نص… |
| Retry | إعادة المحاولة |
| Models | نماذج التفريغ |
| Download model | تنزيل النموذج |
| Installed / in use | تم التثبيت / قيد الاستخدام |
| App lock | قفل التطبيق بالبصمة |
| Search notes | ابحث في ملاحظاتك |

## 5. Recommended Swizy UI structure

Keep the product simple. A practical top-level navigation is:

| Main destination | Contains |
|---|---|
| Lowery | Main masking toggle, accessibility-service status, manual masking, profiles shortcut. |
| Notes | New voice note, recent notes, search, transcription status. |
| Sibha | Large counter, count, reset/undo and optional haptic/sound settings. |
| Prayers | Today's times, location/method settings, next-prayer emphasis; Qibla is a clear action within this area. |
| Settings | App lock, language, model manager, Lowery controls, Sibha preferences, prayer preferences, backup/about. |

Put Settings behind a clear top-bar gear/action rather than mixing it into the main destinations. Qibla does not need a separate top-level destination if it naturally belongs with prayer tools.

### Screen-level improvements

**Lowery home:** one clear status card at the top (enabled/disabled), one primary action to enable the accessibility service if needed, then two simple entry cards: manual hide and app profiles. Replace long explanatory paragraphs with concise descriptions and “Learn more” details. Make the difference between the dynamic mask and manual boxes obvious.

**Notes home:** prominent new-note action, search at the top, and a clean recent-note list. Each row should show title, date/time, duration, transcription status and a small audio/transcript indicator. Do not overload every row with technical model data.

**Recording screen:** one task only. Title field, elapsed timer, clear recording state, audio level, pause/resume and stop. Confirm before discarding an active recording. On completion, explain that audio is saved and transcription can continue in the background.

**Note detail:** audio player near the top; transcript below; tap a segment to seek; edit/rename/share/delete in an overflow menu. Put retry near a failed transcription, not hidden inside Settings.

**Settings:** organize into short groups with plain descriptions. Keep the app-lock toggle near the top. Put model downloads in their own section and show storage use. Use confirmation dialogs for deleting models, wiping data or deleting a note.

**Sibha:** preserve the large touch target and count-first layout. Add a small undo-last-tap action because accidental taps are common. Keep reset secondary and require confirmation; retain haptic/sound settings but make them understandable in Arabic.

**Prayer/Qibla:** make the next prayer or today's schedule the visual focus. Keep location and calculation method in Settings or a secondary panel. In Qibla, show calibration/compass accuracy in plain language and avoid presenting an unreliable sensor reading as precise.

### Visual system

Katib's strongest design idea is its token system: primitive colors and sizes feed semantic tokens, and screens use semantic tokens rather than hard-coded values. Adopt that pattern. You can borrow its warm paper/ink approach and restrained red live-state color, but keep Swizy's visual identity and logo. For Swizy, use more generous 12–16 dp card corners than Katib's very tight 4 dp card radius, clear 48 dp or larger touch targets, consistent spacing, and strong Arabic text hierarchy. Avoid excessive gradients, borders and decorative cards.

## 5.1 Qibla finder: source scan and correctness fix

**Observed user symptom:** the Qibla screen appears to indicate the opposite direction. The user has to turn their back toward the indicated direction instead of facing it. Treat this as a compass correctness defect, not a visual polish request.

### What the current source does

The reviewed implementation is in `app/src/main/java/com/abo/avatarblackout/MainActivity.kt`:
- `registerSensors()` prefers `TYPE_ROTATION_VECTOR`, falls back to `TYPE_GEOMAGNETIC_ROTATION_VECTOR`, then to accelerometer + magnetic-field sensors.
- `updateHeadingFromMatrix()` manually chooses matrix elements according to `displayRotation`, derives azimuth with `atan2`, and smooths the result.
- In the default portrait branch (`ROTATION_0`), it currently uses `fwdX = -R[1]` and `fwdY = -R[4]`. These are both negated relative to the device-top-axis projection normally used for portrait heading, which is a strong code-level explanation for a 180° reversal. The other three branches also manually remap axes and need verification rather than assuming only the portrait branch is wrong.
- `qiblaBearing()` uses the standard initial-bearing great-circle formula from the saved latitude/longitude to the Kaaba coordinates (21.4225°, 39.8262°). The formula appears structurally correct; verify with independent known-location calculations before changing it.
- `CompassView` rotates the compass rose by `-azimuth` and draws the Qibla pointer at `qiblaBearing - azimuth`. That relative-angle convention is internally consistent if `azimuth` is the actual heading of the phone's top edge.
- The screen currently promises automatic tilt compensation, but heading extraction is hand-written. Sensor accuracy is shown only as a small colored dot and a limited “Figure-8 to calibrate” message; the app does not clearly explain unreliable/uncalibrated readings.

### Recommended implementation

1. Replace the hand-written four-branch matrix-axis calculation with Android's documented coordinate-remapping approach (`SensorManager.remapCoordinateSystem`) for the current display rotation, then obtain azimuth using `SensorManager.getOrientation()`. Keep the device's physical top edge as the forward direction in portrait and correctly remap it for landscape/upside-down rotations.
2. Normalize the azimuth to 0–360°, smooth across the 0°/360° boundary, and avoid treating an uninitialized `0°` as a real previous reading. Recompute display rotation when configuration changes; do not capture it only once and assume it remains fixed.
3. Keep the Qibla bearing calculation separate from sensor heading. Verify the bearing independently for known locations (including Cairo and at least one location in another quadrant) and validate latitude/longitude ranges before calculating.
4. Make the compass explain the result: “Point the top edge of your phone toward the arrow” (localized), show the numeric bearing and current heading, and show a clear calibration warning when sensor accuracy is low/unreliable. Do not display “Aligned” while the sensor reading is unreliable.
5. Confirm that magnetic north is being used. If the product ever chooses true north, apply magnetic declination using location and time; do not mix true-north Qibla bearing with magnetic-north sensor heading.

### Required test matrix

- With the phone held normally in portrait and screen facing the user, compare the indicated heading with a trusted compass at North, East, South and West.
- Confirm that rotating the phone clockwise makes the heading change in the same direction and that the Qibla arrow moves toward the top exactly when the phone faces the calculated bearing.
- Repeat in landscape-left, landscape-right and upside-down portrait where supported.
- Repeat flat, upright and at moderate tilt; verify no 180° flip and no false “Aligned” state.
- Test with low/unreliable magnetic accuracy, near metal/magnets, and after figure-eight calibration. The UI must warn rather than imply precision.
- Independently verify Qibla bearings for saved coordinates and check that changing location updates the bearing.

**Diagnosis confidence:** high that the current portrait axis sign is a likely cause of the reported reversal; not yet device-verified. No source change or physical compass test has been performed as part of this document update. Do not ship a blind `+180°` patch: fix the coordinate transform and verify every orientation.

## 6. Architecture and integration approach

Do not paste Katib into the root app as a second application. Extract and adapt the capabilities into Swizy's existing application ID and package. Suggested feature boundaries:

```text
com.abo.avatarblackout/
  app/                 # app shell, navigation, app lifecycle
  core/ui/             # theme, typography, color/spacing tokens, shared components
  core/security/       # optional biometric lock controller
  feature/lowery/      # existing masking screens and service integration
  feature/sibha/       # counter UI and logic
  feature/prayers/     # prayer times and Qibla
  feature/notes/
    data/              # Note, TranscriptSegment, DAO, database, repository
    audio/             # foreground recorder service, audio files/player
    transcription/     # ASR pipeline and WorkManager worker
    models/            # model catalogue, downloader, checksum validation
    ui/                # Notes list, recording, detail, model manager
```

Suggested components:
- `Note` and `TranscriptSegment` Room entities, with database migrations from the first released schema.
- `NotesRepository` to own data operations.
- `NotesViewModel` to expose screen state as `StateFlow`.
- `NoteRecorderService` for microphone capture and an ongoing notification.
- `TranscriptionWorker` for post-recording inference, progress and retry.
- `SpeechModelManager` for model catalog, installation, integrity checks and deletion.
- `AppLockController` for lifecycle-aware biometric prompts and the Settings preference.

### Important build compatibility issue

The root Swizy project currently uses AGP 8.5.2 / Kotlin 1.9.24, compile/target SDK 34 and min SDK 24. Katib uses AGP 8.7.3, Kotlin/Compose 2.1.0, compile SDK 35 and min SDK 29, plus Compose, Room, WorkManager, SQLCipher and a bundled sherpa-onnx AAR. Do not copy its Gradle files blindly. First decide the Android version range Swizy must continue supporting. Either keep min SDK 24 and implement/test compatible paths, or intentionally raise it to 29 with a clear product decision. Upgrade the toolchain in a separate change and build the existing app before adding recording. Add the Katib feature dependencies individually and verify native ABI support for your target devices.

## 7. Android platform requirements and privacy

- Biometric authentication should use Android's system prompt. `BIOMETRIC_STRONG` and `DEVICE_CREDENTIAL` can be combined so the device PIN/pattern/password remains a recovery path.
- For microphone recording, declare the microphone foreground-service type and its permission requirements for the target SDK. Start the microphone service while the activity is visible and after `RECORD_AUDIO` permission is granted. Android 14+ enforces the microphone foreground-service prerequisites and while-in-use restrictions.
- Keep an ongoing, honest recording notification with pause/resume/stop controls. Do not hide microphone activity.
- Keep audio local. State exactly what network requests are used for: model downloads only. Do not add analytics or uploads to the first version.
- Consider SQLCipher/encrypted audio after the basic notes lifecycle works. Katib's threat model correctly distinguishes an app lock from encryption. Its audio encryption, key wrapping and crypto-erase design are advanced; do not copy them without reviewing threat assumptions, failure behavior and key recovery.
- Provide explicit export/share actions and explain that once a user shares content, the destination app controls the shared copy.

## 8. Implementation phases

| Phase | Scope | Exit criteria |
|---|---|---|
| 0. Stabilize | Branch/backup, baseline build, test Lowery service, clean build setup. | Existing features still work; baseline APK builds. |
| 1. Arabic + UI foundation | Resource localization, RTL, app language, theme tokens, navigation and shared components. | All existing screens readable in Arabic and English; no behavior regressions. |
| 2. App lock | Settings toggle, lifecycle timer, system biometric prompt, device credential fallback. | On/off behavior, cold start, background/resume and unavailable biometric cases pass. |
| 3. Note data + screens | Note schema, title prompt/default name, library/detail UI. | Notes can be created, renamed, listed, deleted and survive process death. |
| 4. Recording | Foreground recorder, notification, pause/resume/stop, audio playback. | Screen-off recording works on supported OS versions; no lost audio after stop. |
| 5. Model manager | In-app catalog/download/checksum/installation/selection/removal. | Interrupted, failed, low-storage and successful downloads all have recoverable states. |
| 6. Transcription + search | ASR worker, progress/retry, Arabic language setting, transcript editor, FTS search. | A saved note transcribes offline, can be edited and found by search. |
| 7. Hardening | Privacy review, database/audio encryption decision, QA on different devices and Egyptian Arabic samples. | Release checklist passes; model accuracy limitations are documented. |

Do not refactor Lowery's AccessibilityService at the same time as implementing audio recording. Isolate changes so a notes bug cannot break the app's existing masking function.

## 9. Acceptance checklist

**App lock:** off means no prompt; on means lock on app open/resume according to delay; cancel leaves app locked; PIN/pattern/password fallback works; active recording continues while the UI is backgrounded.

**Naming:** title is saved exactly when non-empty after trimming; blank title creates `ملاحظة_yyyy-MM-dd_HH-mm`; duplicate-minute notes remain unique; note can be renamed later; there is no user-facing meeting/اجتماع terminology.

**Recording:** permission denial is explained; recording notification is visible; timer and pause/resume work; stop persists the file before transcription begins; process death and screen-off behavior are tested; delete requires confirmation.

**Models:** model download shows accurate state and progress; checksum failure is surfaced; retry/cancel works; low storage does not corrupt installed models; model removal does not delete existing transcripts; app can record a note without a model and transcribe later.

**Arabic:** all labels are translated; RTL layout is correct; Arabic-English mixed text is readable; dates/numbers/timers/file names remain correctly ordered; dynamic type and TalkBack are tested.

**Regression:** Lowery dynamic masking, manual boxes, profile switching, Quick Settings tile, Sibha count, prayer-time cache, Qibla compass and headset time announcement still work.

## 10. What to learn from the source code, in order

1. **Compose + state:** learn how `RecordScreen` reads state from `AppVm`, and how `AppVm` exposes `StateFlow`. This keeps UI changes from mixing with audio/database internals.
2. **Foreground microphone service:** study `RecorderService` start/stop/pause/resume and notification actions. Understand Android permissions, service lifetime and what happens when the Activity is destroyed.
3. **Room + search:** study `Db.kt` entities, DAOs, migrations and FTS query. Implement Swizy's own `Note` schema rather than reusing Katib's `Meeting` entity.
4. **Model lifecycle:** study `Models.kt` and the Settings screen for catalogue, sizes, checksums and installation states. Add missing UX states such as cancel, retry and storage failure.
5. **Transcription pipeline:** read `Pipeline.kt` after the recorder and model manager. For the first release, understand VAD → ASR only; defer diarization.
6. **Arabic presentation:** study `Theme.kt`, `Tokens.kt` and `values-ar/strings.xml`. Learn locale-aware typography, RTL, and mixed-direction text behavior.
7. **Security:** read `THREAT_MODEL.md` before touching encryption. Understand the difference between app lock, data-at-rest encryption and cryptographic evidence signing.

## 11. Recommended product decisions

- Keep Swizy's name and main purpose; add a clearly separate **ملاحظاتي** feature.
- Arabic is the default language; offer English in the app's language setting.
- Use one title field before every recording; blank title generates `ملاحظة_yyyy-MM-dd_HH-mm`.
- Start with Qwen3-ASR 0.6B int8 as the Arabic-capable option and expose Whisper base as a smaller alternative only after device testing. Let the user choose; do not silently switch.
- Do not add diarization, voice profiles, meeting consent, GPS note tagging or evidence signing to the first release.
- Keep audio until the user deletes it by default; make any auto-delete policy explicit and configurable.
- Preserve API 24 support unless a deliberate decision is made to raise min SDK; Katib's min SDK 29 is not a reason to raise Swizy automatically.

## 12. Source references

### Swizy source reviewed
- [MainActivity.kt](https://github.com/Impairon/eyzy/blob/pushy/app/src/main/java/com/abo/avatarblackout/MainActivity.kt)
- [Prefs.kt](https://github.com/Impairon/eyzy/blob/pushy/app/src/main/java/com/abo/avatarblackout/Prefs.kt)
- [AndroidManifest.xml](https://github.com/Impairon/eyzy/blob/pushy/app/src/main/AndroidManifest.xml)
- [app/build.gradle.kts](https://github.com/Impairon/eyzy/blob/pushy/app/build.gradle.kts)

### Katib source reviewed
- [README](https://github.com/Impairon/eyzy/blob/pushy/katib/README.md)
- [Architecture](https://github.com/Impairon/eyzy/blob/pushy/katib/docs/ARCHITECTURE.md)
- [Threat model](https://github.com/Impairon/eyzy/blob/pushy/katib/docs/THREAT_MODEL.md)
- [RecordScreen.kt](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/java/app/katib/ui/RecordScreen.kt)
- [OtherScreens.kt](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/java/app/katib/ui/OtherScreens.kt)
- [AppVm.kt](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/java/app/katib/ui/AppVm.kt)
- [RecorderService.kt](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/java/app/katib/audio/RecorderService.kt)
- [Models.kt](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/java/app/katib/asr/Models.kt)
- [Pipeline.kt](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/java/app/katib/asr/Pipeline.kt)
- [Db.kt](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/java/app/katib/data/Db.kt)
- [Theme.kt](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/java/app/katib/ui/Theme.kt)
- [Tokens.kt](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/java/app/katib/ui/Tokens.kt)
- [Arabic strings](https://github.com/Impairon/eyzy/blob/pushy/katib/app/src/main/res/values-ar/strings.xml)

### Platform and model references
- [Android biometric authentication](https://developer.android.com/identity/sign-in/biometric-auth)
- [Android per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages)
- [Foreground service changes](https://developer.android.com/develop/background-work/services/fgs/changes)
- [Microphone foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Qwen3-ASR model and language information](https://github.com/QwenLM/Qwen3-ASR)

---

**Review limitation:** this document is based on the repository tree, README, architecture/threat-model documents, selected Kotlin source files, manifests and Gradle configuration on the reviewed `pushy` branch. The Qibla section includes a targeted source-level diagnosis of the heading calculation, but this is not a full source audit, build verification, physical-device compass test, model benchmark or independent security review.

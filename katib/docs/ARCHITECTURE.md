# Katib architecture

Katib is a single Android app (Kotlin, Jetpack Compose) with no backend. Everything in this
document runs on the phone. The only network calls are speech-model downloads from the
sherpa-onnx GitHub releases.

- [1. System context](#1-system-context)
- [2. Components](#2-components)
- [3. Recording → transcript lifecycle](#3-recording--transcript-lifecycle)
- [4. Storage and encryption](#4-storage-and-encryption)
- [5. Speech pipeline](#5-speech-pipeline)
- [6. Provenance: signing and evidence](#6-provenance-signing-and-evidence)
- [7. Retention and deletion](#7-retention-and-deletion)
- [8. Data inventory](#8-data-inventory)
- [9. Build and platform](#9-build-and-platform)

---

## 1. System context

```mermaid
flowchart LR
    subgraph Phone["📱 Phone (trust boundary)"]
        K[Katib app]
        KS[(Android Keystore<br/>StrongBox / TEE)]
        K <--> KS
    end
    U((Owner)) -- speaks, consents,<br/>biometric --> K
    P((Other participants)) -- speak --> K
    GH[GitHub releases<br/>sherpa-onnx models] -- "model files only<br/>(HTTPS, SHA-256)" --> K
    K -- "evidence bundle / text<br/>(only when the owner shares)" --> X[Recipient / court / auditor]
    X -. verifies offline .-> V[tools/verify_evidence.py]
    G[Google hardware<br/>attestation root] -. trust anchor .-> V
```

Nothing flows out of the phone unless the owner presses **Share** or **Export evidence**.
Audio is never uploaded.

---

## 2. Components

```mermaid
flowchart TB
    subgraph UI["ui/ (Compose)"]
        MA[MainActivity<br/>app lock · FLAG_SECURE · seal prompts]
        VM[AppVm<br/>state + actions]
        RS[RecordScreen] & LS[Library / Meeting] & SS[Search] & PS[People] & ST[Settings]
    end
    subgraph AUDIO["audio/"]
        REC[RecorderService<br/>foreground · microphone]
        EAW[EncryptedAudioWriter / Reader<br/>ECA1 format]
        PL[Player]
        LOC[Locator]
    end
    subgraph WORK["work/ (WorkManager)"]
        TW[TranscribeWorker<br/>expedited · foreground]
        RW[RetentionWorker<br/>every 6 h]
    end
    subgraph ASR["asr/"]
        PIPE[Pipeline<br/>VAD → ASR → diarization]
        MOD[Models<br/>download · verify · locate]
    end
    subgraph SEC["security/"]
        CR[Crypto<br/>envelope encryption]
        PR[Provenance<br/>signing · attestation · bundles]
    end
    subgraph DATA["data/"]
        DB[(KatibDb<br/>Room + SQLCipher + FTS4)]
        PF[Prefs]
    end
    UI --> VM
    VM --> REC & DB & PR & MOD & LOC
    REC --> EAW --> CR
    REC -- "stop → enqueue" --> TW
    TW --> EAW & PIPE & DB & PR
    PIPE --> MOD
    RW --> DB
    PL --> EAW
    DB --> CR
    MA -- "BiometricPrompt + CryptoObject" --> PR
```

| Package | Responsibility |
|---|---|
| `audio/RecorderService` | Foreground service (`microphone` type). Reads 16 kHz mono PCM from `AudioRecord` (`VOICE_RECOGNITION` source), streams it to an encrypted file, computes the plaintext SHA-256 as it goes, publishes level/duration as a `StateFlow`. |
| `audio/EncryptedAudio` | `ECA1` chunked AES-GCM container. Random access by 1-second chunk for playback and seeking. |
| `work/TranscribeWorker` | Expedited, foreground `CoroutineWorker`. Decrypts audio into memory, runs the pipeline, writes segments, matches speakers, seals the transcript. Survives app kill; orphaned jobs are re-queued at startup. |
| `work/RetentionWorker` | Periodic. Shreds audio past its delete-at time. |
| `asr/Pipeline` | sherpa-onnx inference: Silero VAD, Qwen3-ASR or Whisper, pyannote + WeSpeaker diarization, voice enrollment. |
| `asr/Models` | Model catalogue, HTTPS download, SHA-256 check (pinned for every model), tar.bz2 extraction. |
| `security/Crypto` | Keystore master key; wrap/unwrap data keys; DB passphrase; crypto-wipe. |
| `security/Provenance` | Signing keys, Key Attestation chains, canonical payloads, hash-chained manifests, evidence bundles. |
| `data/KatibDb` | SQLCipher-encrypted Room database: meetings, segments (+FTS), speakers, people, consent log, provenance records. |
| `ui/MainActivity` | Biometric app lock (after 60 s in background), `FLAG_SECURE`, and the biometric signing prompt for capture seals. |

---

## 3. Recording → transcript lifecycle

```mermaid
sequenceDiagram
    autonumber
    actor O as Owner
    participant UI as RecordScreen / AppVm
    participant R as RecorderService
    participant F as Encrypted audio file
    participant DB as KatibDb
    participant BP as BiometricPrompt
    participant W as TranscribeWorker
    participant PR as Provenance

    alt Meeting mode
        O->>UI: list participants, confirm consent
        UI->>DB: ConsentLog(names, method, time)
    else Personal note
        UI->>DB: ConsentLog("Owner only")
    end
    O->>UI: Record
    UI->>R: start (meeting id, uuid, kind)
    opt location attached
        UI->>UI: Locator.current() → lat/lon/accuracy
    end
    loop every buffer
        R->>F: AES-GCM chunk (index as AAD)
        R->>R: SHA-256(plaintext PCM)
    end
    O->>UI: Stop
    R->>F: close
    R->>DB: status=QUEUED, audioSha256
    R->>W: enqueue "transcribe-<id>" (expedited)
    alt Meeting mode (always) or note + "Sign note"
        R-->>BP: seal request
        BP->>O: fingerprint / face / PIN
        O->>BP: authenticate
        BP->>PR: unlocked Signature (user key)
        PR->>DB: capture manifest #0 (signed by owner)
    end
    W->>F: decrypt to memory
    W->>W: VAD → ASR → diarization
    W->>DB: segments, speakers, rtf, retention date
    W->>PR: sealTranscript("transcribed")
    PR->>DB: transcript manifest #1 (device key, prev = #0)
    Note over O,DB: Later edits → sealTranscript("user_edit") → manifest #n (prev = #n-1)
```

Meeting status moves through `RECORDING → QUEUED → PROCESSING → DONE` (or `FAILED`, with a
retry action). Progress and stage (`vad`, `asr`, `diarize`) are written to the DB, and the UI
observes them.

---

## 4. Storage and encryption

```mermaid
flowchart TB
    subgraph TEE["Android Keystore (hardware, non-exportable)"]
        MK["katib_master_v1<br/>AES-256-GCM"]
        UK["katib_user_sign_v1<br/>EC P-256 · biometric per use"]
        DK["katib_device_sign_v1<br/>EC P-256"]
    end
    subgraph NB["noBackupFilesDir/ (app-private, excluded from backup)"]
        DBK["db.key = wrap(32 random bytes)"]
        AUD["audio/m&lt;id&gt;.eca<br/>header: wrap(per-file AES key)"]
        IID["install.id"]
    end
    DBF[("databases/katib.db<br/>SQLCipher")]
    MK -- wraps --> DBK
    MK -- wraps --> AUD
    DBK -- passphrase --> DBF
```

**Envelope encryption.** One AES-256-GCM master key in the Keystore never leaves hardware. It
only wraps small random data keys:

- each audio file has its own 256-bit key, stored wrapped in the file header;
- the SQLCipher passphrase is a random 32 bytes, stored wrapped in `db.key`.

**ECA1 audio format.**

```
"ECA1" | sampleRate:i32 | wrappedKeyLen:i32 | wrappedKey | record*
record = iv(12) | AES-GCM(ciphertext of 1 s of s16le PCM) + tag(16)     AAD = chunk index
```

Chunk indices are bound as AAD, so chunks can't be reordered or swapped undetected. Truncation
of trailing chunks is detectable by comparing to the signed `audio_sha256` and duration.

**Wipe everything** deletes the master key and `db.key`. All ciphertext on the device becomes
unreadable, regardless of flash wear-levelling.

**Platform hardening.** `allowBackup=false`, data-extraction rules exclude everything,
`FLAG_SECURE` on the window (no screenshots, recents thumbnails or casting), notifications use
`VISIBILITY_PRIVATE`, and the app requires biometric/device credential after 60 s in the
background.

---

## 5. Speech pipeline

```mermaid
flowchart LR
    A[16 kHz mono PCM<br/>decrypted in memory] --> V[Silero VAD<br/>512-sample window<br/>max 20 s / segment]
    V --> S{{speech segments}}
    S --> ASR[ASR per segment<br/>Qwen3-ASR 0.6B int8<br/>or Whisper base]
    A --> D[Diarization<br/>pyannote seg 3.0 +<br/>WeSpeaker ResNet34<br/>fast clustering, τ = 0.6]
    ASR --> M[Assign speaker<br/>by max time overlap]
    D --> M
    M --> E[Per-speaker embedding<br/>≤ 30 s of speech]
    E --> X[Cosine match vs. People<br/>threshold 0.55]
    X --> OUT[(Segments + speakers)]
```

- **Language.** Auto-detect, or pinned to Arabic or English (`language` option for Qwen3).
- **Voice enrollment.** About 30 s of reading → VAD → embedding. A consistency check compares
  embeddings from each half; under 0.35 is rejected as noisy. At least 6 s of speech is required.
- **Personal notes.** If a cluster doesn't match the enrolled owner, the note is flagged
  "other voices", since a note was supposed to contain only the owner.
- **Performance.** Galaxy S23 Ultra (Snapdragon 8 Gen 2): about 0.73× real time with Qwen3-ASR.
- Speaker **embeddings never leave the phone** and are never written into signed manifests or
  exports.

---

## 6. Provenance: signing and evidence

Two hardware keys, both with an Android Key Attestation chain whose challenge is this install's
random `install_id`:

| Key | Auth | Signs | Proves |
|---|---|---|---|
| `katib_user_sign_v1` | biometric or device credential, **every use** | capture manifests | a person who could unlock this phone approved this recording |
| `katib_device_sign_v1` | none | transcript manifests | this app on this device produced this transcript |

### Manifest chain

```mermaid
flowchart LR
    C["katib.capture.v1<br/>meeting_uuid · audio_sha256<br/>duration · consent · location?<br/>signed: USER key"]
    T1["katib.transcript.v1 #1<br/>reason: transcribed<br/>transcript_sha256 · model hash<br/>speaker match scores<br/>prev_manifest_sha256<br/>signed: DEVICE key"]
    T2["katib.transcript.v1 #2<br/>reason: user_edit<br/>transcript_sha256'<br/>prev_manifest_sha256<br/>signed: DEVICE key"]
    C --> T1 --> T2
```

**Canonical transcript** (what `transcript_sha256` covers), sorted by start time then id,
`Locale.ROOT`:

```
<start %.2f>\t<end %.2f>\t<speaker index>\t<text>\n
```

### Evidence bundle (`katib.evidence.v1`)

An exported JSON file that contains every manifest (payload, SHA-256, signature, PEM cert
chain), the current transcript text and the device info. **Not included:** audio, voice
embeddings.

### Verification (`tools/verify_evidence.py`)

```mermaid
flowchart TB
    B[evidence.json] --> H[payload hash ✓]
    H --> S[ECDSA signature vs leaf cert ✓]
    S --> CH[cert chain internally valid ✓]
    CH --> R{root = Google<br/>attestation root?}
    R -->|--google-roots| OK1[✓]
    CH --> AT[attestation extension:<br/>StrongBox/TEE · challenge = install_id<br/>verified boot · locked bootloader<br/>package + signer digest<br/>user-auth on capture key]
    AT --> L[hash chain links ✓<br/>uuid / audio hash agree ✓]
    L --> TX[latest transcript_sha256<br/>= bundled text ✓]
    TX --> AU{--audio file?}
    AU -->|yes| AH[SHA-256 PCM = audio_sha256 ✓]
```

---

## 7. Retention and deletion

| Item | Default lifetime | Mechanism |
|---|---|---|
| Audio | 5 days after transcription (configurable) | `RetentionWorker` every 6 h → overwrite + delete |
| Transcript, segments | until the owner deletes the meeting | DB delete (SQLCipher) |
| Provenance records | deleted with the meeting | DB delete |
| Voice profiles | until deleted in People | DB delete |
| Exported bundles | cache, cleared on next export | `cacheDir/exports` |
| Everything | Settings → Wipe | master key destroyed (crypto-erase) |

Overwriting a file on flash isn't a guaranteed physical erase (wear-levelling,
file-based encryption). Each file's own key goes with it, and the master-key wipe is the real
guarantee. See [THREAT_MODEL.md](THREAT_MODEL.md).

---

## 8. Data inventory

| Data | Personal? | Where | Encrypted at rest | Leaves device |
|---|---|---|---|---|
| Raw audio | yes (voice) | `noBackupFilesDir/audio` | AES-GCM per file | never |
| Transcript text | yes | SQLCipher DB | yes | only if owner shares/exports |
| Speaker embeddings | **biometric** | SQLCipher DB | yes | never |
| Participant names / consent | yes | SQLCipher DB | yes | inside evidence bundle |
| Location | yes | SQLCipher DB | yes | inside evidence bundle (if attached) |
| install_id, device model | pseudonymous | file + DB | install.id: no (random, non-secret) | inside evidence bundle |
| Models | no | `filesDir/models` | no | downloaded from GitHub |

---

## 9. Build and platform

- Kotlin 2.1, AGP 8.7, Compose BOM 2024.12, Room 2.6 + KSP, WorkManager 2.10,
  SQLCipher 4.6, sherpa-onnx 2.0.1 (static AAR, arm64-v8a).
- `minSdk 29` (Android 10), `targetSdk 35`. Tested on Android 17 (Galaxy S23 Ultra).
- Debug builds skip `FLAG_SECURE` only if `files/test/allow_screens` exists (for UI screenshots via adb).
- Debug builds include an exported `DebugImport` receiver for scripted testing (import WAV,
  export bundle). It is in `src/debug` only and **not present in release builds**.

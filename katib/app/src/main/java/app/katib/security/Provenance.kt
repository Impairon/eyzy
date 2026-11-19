package app.katib.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import app.katib.BuildConfig
import app.katib.asr.Models
import app.katib.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant

/**
 * Forensic provenance: tamper-evident, hardware-signed records ("manifests") for each recording.
 *
 *  - USER key  (katib_user_sign_v1): EC P-256 in StrongBox/TEE, every use requires fingerprint/face/PIN.
 *    Signs the CAPTURE manifest at stop: audio SHA-256 (computed while recording), consent, location, device.
 *  - DEVICE key (katib_device_sign_v1): EC P-256, no user auth. Signs TRANSCRIPT manifests automatically
 *    (after transcription and after each edit), each chained to the previous manifest by hash.
 *
 * Both keys carry an Android Key Attestation certificate chain (rooted at Google) proving the key was
 * generated in this phone's secure hardware for this app. Verify with tools/verify_evidence.py.
 * Voice embeddings are NEVER included: only the owner-match score.
 */
object Provenance {
    const val USER_KEY = "katib_user_sign_v1"
    const val DEVICE_KEY = "katib_device_sign_v1"
    private const val KS = "AndroidKeyStore"

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).hex()
    fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    private fun ks() = KeyStore.getInstance(KS).apply { load(null) }

    private fun ensureKey(ctx: Context, alias: String, userAuth: Boolean) {
        if (ks().containsAlias(alias)) return
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(installId(ctx).toByteArray())
            .apply {
                if (strongBox) setIsStrongBoxBacked(true)
                if (userAuth) {
                    setUserAuthenticationRequired(true)
                    setInvalidatedByBiometricEnrollment(true)
                    if (Build.VERSION.SDK_INT >= 30) setUserAuthenticationParameters(0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                }
            }.build()
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KS)
        try { gen.initialize(spec(true)); gen.generateKeyPair() }
        catch (e: StrongBoxUnavailableException) { gen.initialize(spec(false)); gen.generateKeyPair() }
        catch (e: java.security.ProviderException) { gen.initialize(spec(false)); gen.generateKeyPair() }
    }

    /** Random per-install id; used as the attestation challenge and in every manifest. */
    fun installId(ctx: Context): String {
        val f = File(ctx.noBackupFilesDir, "install.id")
        if (!f.exists()) f.writeText(Crypto.randomBytes(16).hex())
        return f.readText()
    }

    fun securityLevel(alias: String): String = runCatching {
        val key = ks().getKey(alias, null) as PrivateKey
        val info = KeyFactory.getInstance(key.algorithm, KS).getKeySpec(key, KeyInfo::class.java)
        if (Build.VERSION.SDK_INT >= 31) when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "strongbox"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "tee"
            else -> "software"
        } else if (@Suppress("DEPRECATION") info.isInsideSecureHardware) "hardware" else "software"
    }.getOrDefault("unknown")

    fun certChainPem(alias: String): String = ks().getCertificateChain(alias).joinToString("") {
        "-----BEGIN CERTIFICATE-----\n" + Base64.encodeToString(it.encoded, Base64.NO_WRAP).chunked(64).joinToString("\n") +
            "\n-----END CERTIFICATE-----\n"
    }

    /** Signature object for the biometric prompt (CryptoObject). Regenerates the key if biometrics changed. */
    fun userSignature(ctx: Context): Signature {
        ensureKey(ctx, USER_KEY, userAuth = true)
        return try {
            Signature.getInstance("SHA256withECDSA").apply { initSign(ks().getKey(USER_KEY, null) as PrivateKey) }
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            ks().deleteEntry(USER_KEY); ensureKey(ctx, USER_KEY, true)
            Signature.getInstance("SHA256withECDSA").apply { initSign(ks().getKey(USER_KEY, null) as PrivateKey) }
        }
    }

    private fun deviceSign(ctx: Context, data: ByteArray): ByteArray {
        ensureKey(ctx, DEVICE_KEY, userAuth = false)
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(ks().getKey(DEVICE_KEY, null) as PrivateKey); update(data); sign()
        }
    }

    private fun device() = JSONObject()
        .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
        .put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
        .put("security_patch", Build.VERSION.SECURITY_PATCH)
        .put("app_package", BuildConfig.APPLICATION_ID).put("app_version", BuildConfig.VERSION_NAME)
        .put("app_build", if (BuildConfig.DEBUG) "debug" else "release")

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    fun capturePayload(ctx: Context, m: Meeting, consent: ConsentLog?, ownerEnrolled: Boolean): String = JSONObject()
        .put("type", "katib.capture.v1")
        .put("meeting_uuid", m.uuid)
        .put("install_id", installId(ctx))
        .put("mode", m.kind)
        .put("started_at", iso(m.createdAt))
        .put("duration_sec", m.durationSec.toDouble())
        .put("audio", JSONObject().put("format", "pcm_s16le_16000_mono").put("sha256", m.audioSha256))
        .put("consent", consent?.let { JSONObject().put("participants", it.participants).put("method", it.method)
            .put("confirmed_at", iso(it.confirmedAt)) } ?: JSONObject.NULL)
        .put("location", if (m.lat != null) JSONObject().put("lat", m.lat).put("lon", m.lon)
            .put("accuracy_m", m.locAccuracyM?.toDouble() ?: JSONObject.NULL).put("fix_at", m.locAt?.let(::iso) ?: JSONObject.NULL)
            else JSONObject.NULL)
        .put("owner_voice_enrolled", ownerEnrolled)
        .put("device", device())
        .put("key", JSONObject().put("alias", USER_KEY).put("security_level", securityLevel(USER_KEY))
            .put("user_auth", "biometric_strong|device_credential, per use"))
        .put("signed_at_device_clock", iso(System.currentTimeMillis()))
        .put("clock_note", "device clock, not a trusted timestamp")
        .toString(2)

    /** Canonical transcript text; its SHA-256 is what gets signed. */
    fun canonicalTranscript(segs: List<Segment>): String = buildString {
        segs.sortedWith(compareBy({ it.startSec }, { it.id })).forEach {
            append("%.2f\t%.2f\t%d\t".format(java.util.Locale.ROOT, it.startSec, it.endSec, it.speakerIdx))
            append(it.text.replace('\n', ' ')); append('\n')
        }
    }

    /** Hash of the ASR model files, cached by path+size+mtime (hashing ~1 GB takes a couple of seconds). */
    private fun modelHash(ctx: Context, modelTitle: String?): String? {
        val spec = Models.ASR.firstOrNull { it.title == modelTitle } ?: return null
        val files = spec.required.mapNotNull { Models.path(ctx, it) }.map(::File)
            .flatMap { if (it.isDirectory) it.walkTopDown().filter(File::isFile).toList() else listOf(it) }.sortedBy { it.name }
        val key = files.joinToString("|") { "${it.path}:${it.length()}:${it.lastModified()}" }.hashCode().toString()
        val prefs = ctx.getSharedPreferences("model_hash", Context.MODE_PRIVATE)
        prefs.getString(key, null)?.let { return it }
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 20)
        files.forEach { f -> f.inputStream().use { i -> var n: Int; while (i.read(buf).also { n = it } > 0) md.update(buf, 0, n) } }
        return md.digest().hex().also { prefs.edit().putString(key, it).apply() }
    }

    /** Device-signs a new transcript version, chained to the previous manifest. */
    suspend fun sealTranscript(ctx: Context, meetingId: Long, reason: String) {
        val dao = KatibDb.get(ctx).dao()
        val m = dao.meeting(meetingId) ?: return
        val uuid = m.uuid ?: return
        val segs = dao.segmentsNow(meetingId)
        val speakers = dao.speakersNow(meetingId)
        val people = dao.peopleNow()
        val prev = dao.provenance(meetingId).lastOrNull()
        val payload = JSONObject()
            .put("type", "katib.transcript.v1")
            .put("meeting_uuid", uuid)
            .put("install_id", installId(ctx))
            .put("version", (dao.provenance(meetingId).count { it.kind == "transcript" } + 1))
            .put("reason", reason)
            .put("prev_manifest_sha256", prev?.payloadSha256 ?: JSONObject.NULL)
            .put("audio_sha256", m.audioSha256 ?: JSONObject.NULL)
            .put("transcript_sha256", sha256(canonicalTranscript(segs).toByteArray()))
            .put("segments", segs.size)
            .put("asr", JSONObject().put("model", m.model ?: JSONObject.NULL)
                .put("model_files_sha256", modelHash(ctx, m.model) ?: JSONObject.NULL)
                .put("runtime", "sherpa-onnx 2.0.1").put("language", m.language))
            .put("speakers", JSONArray().apply {
                speakers.forEach { sp ->
                    val p = people.firstOrNull { it.id == sp.personId }
                    put(JSONObject().put("idx", sp.idx).put("speech_sec", sp.speechSec.toDouble())
                        .put("is_owner", p?.isOwner == true)
                        .put("match_score", if (sp.personId != null) sp.score.toDouble() else JSONObject.NULL))
                }
            })
            .put("device", device())
            .put("key", JSONObject().put("alias", DEVICE_KEY).put("security_level", securityLevel(DEVICE_KEY)).put("user_auth", "none"))
            .put("signed_at_device_clock", iso(System.currentTimeMillis()))
            .toString(2)
        val bytes = payload.toByteArray()
        val sig = deviceSign(ctx, bytes)
        dao.insertProvenance(ProvenanceRecord(meetingId = meetingId, kind = "transcript", payload = payload,
            payloadSha256 = sha256(bytes), prevSha256 = prev?.payloadSha256,
            signature = Base64.encodeToString(sig, Base64.NO_WRAP), keyAlias = DEVICE_KEY,
            certChain = certChainPem(DEVICE_KEY), createdAt = System.currentTimeMillis()))
    }

    /** Evidence bundle: manifests + signatures + attestation chains + transcript text. */
    suspend fun evidenceBundle(ctx: Context, meetingId: Long): String {
        val dao = KatibDb.get(ctx).dao()
        val m = dao.meeting(meetingId)!!
        val recs = dao.provenance(meetingId)
        return JSONObject()
            .put("format", "katib.evidence.v1")
            .put("meeting_uuid", m.uuid).put("title", m.title)
            .put("manifests", JSONArray().apply {
                recs.forEach { r -> put(JSONObject().put("kind", r.kind).put("payload", r.payload)
                    .put("payload_sha256", r.payloadSha256).put("signature_b64", r.signature)
                    .put("algorithm", "SHA256withECDSA").put("cert_chain_pem", r.certChain)) }
            })
            .put("transcript_canonical", canonicalTranscript(dao.segmentsNow(meetingId)))
            .put("verify", "python3 verify_evidence.py <this file> [--audio <decrypted 16k mono s16le .raw/.wav>]")
            .toString(2)
    }
}

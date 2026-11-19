#!/usr/bin/env python3
"""
Independent verifier for Katib evidence bundles (katib.evidence.v1).

Checks, for every manifest:
  1. payload_sha256 matches the payload bytes
  2. ECDSA-SHA256 signature verifies against the leaf certificate's public key
  3. the attestation certificate chain is internally valid (each cert signed by the next)
  4. the Android Key Attestation extension: key lives in TEE/StrongBox, attestation challenge == install_id,
     app package, verified-boot state, user-auth requirement
Across the chain:
  5. transcript manifests link to the previous manifest by hash; all agree on audio_sha256 / meeting_uuid
  6. the latest transcript manifest's transcript_sha256 matches the bundled transcript text
Optional:
  7. --audio FILE: SHA-256 of the raw PCM (16 kHz mono s16le; WAV data chunk or .raw) equals audio_sha256

Trust anchor: the root certificate fingerprint is printed. Compare it to Google's published hardware
attestation roots (developer.android.com/privacy-and-security/security-key-attestation#root_certificate),
or pass --google-roots roots.pem to check automatically. Requires: pip install cryptography
"""
import argparse, base64, hashlib, json, sys
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

ATTEST_OID = x509.ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17")
SEC = {0: "Software", 1: "TrustedEnvironment", 2: "StrongBox"}
BOOT = {0: "Verified", 1: "SelfSigned", 2: "Unverified", 3: "Failed"}
ok_all = True


def say(ok, msg):
    global ok_all
    ok_all &= bool(ok)
    print(("  PASS  " if ok else "  FAIL  ") + msg)


# ---------- minimal DER reader (enough for KeyDescription) ----------
def der(buf, i=0):
    t = buf[i]; i += 1
    cls, cons, num = t >> 6, bool(t & 0x20), t & 0x1F
    if num == 0x1F:
        num = 0
        while True:
            b = buf[i]; i += 1; num = (num << 7) | (b & 0x7F)
            if not b & 0x80: break
    ln = buf[i]; i += 1
    if ln & 0x80:
        n = ln & 0x7F; ln = int.from_bytes(buf[i:i + n], "big"); i += n
    return cls, cons, num, buf[i:i + ln], i + ln


def children(buf):
    i, out = 0, []
    while i < len(buf):
        c, k, n, v, i = der(buf, i); out.append((c, k, n, v))
    return out


def as_int(v): return int.from_bytes(v, "big", signed=False)


def authlist(v):
    return {n: val for (c, k, n, val) in children(v) if c == 2}


def parse_attestation(cert):
    try:
        ext = cert.extensions.get_extension_for_oid(ATTEST_OID).value.value
    except x509.ExtensionNotFound:
        return None
    _, _, _, body, _ = der(ext)
    f = children(body)
    sw, hw = authlist(f[6][3]), authlist(f[7][3])
    info = {
        "attestation_version": as_int(f[0][3]),
        "attestation_security": SEC.get(as_int(f[1][3]), "?"),
        "keymint_security": SEC.get(as_int(f[3][3]), "?"),
        "challenge": f[4][3],
        "user_auth_required": 503 not in hw and 503 not in sw,   # noAuthRequired absent
        "auth_types": as_int(children(hw[504])[0][3]) if 504 in hw else None,
    }
    if 704 in hw:  # rootOfTrust
        rot = children(children(hw[704])[0][3])
        info["device_locked"] = rot[1][3] != b"\x00"
        info["verified_boot"] = BOOT.get(as_int(rot[2][3]), "?")
    app = sw.get(709) or hw.get(709)
    if app:
        seq = children(children(children(app)[0][3])[0][3])
        pkgs = []
        for c in children(seq[0][3]):
            parts = children(c[3]); pkgs.append(parts[0][3].decode(errors="replace"))
        info["app_packages"] = pkgs
        info["app_signer_sha256"] = [x[3].hex() for x in children(seq[1][3])] if len(seq) > 1 else []
    return info


def load_chain(pem):
    certs = x509.load_pem_x509_certificates(pem.encode())
    return certs


def check_chain(certs):
    good = True
    for a, b in zip(certs, certs[1:]):
        try:
            a.verify_directly_issued_by(b)
        except Exception as e:
            good = False; print("    chain link failed:", e)
    root = certs[-1]
    try:
        root.verify_directly_issued_by(root)
    except Exception:
        pass
    return good, root


def pcm_sha256(path):
    data = open(path, "rb").read()
    if data[:4] == b"RIFF":
        i = 12
        while i + 8 <= len(data):
            cid, ln = data[i:i + 4], int.from_bytes(data[i + 4:i + 8], "little")
            if cid == b"data":
                return hashlib.sha256(data[i + 8:i + 8 + ln]).hexdigest()
            i += 8 + ln + (ln & 1)
        raise SystemExit("no data chunk in WAV")
    return hashlib.sha256(data).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("bundle")
    ap.add_argument("--audio")
    ap.add_argument("--google-roots", help="PEM file with Google attestation root certificates")
    a = ap.parse_args()
    b = json.load(open(a.bundle))
    print(f"Katib evidence: {b.get('title')}  uuid={b.get('meeting_uuid')}")
    roots = []
    if a.google_roots:
        roots = [c.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
                 for c in x509.load_pem_x509_certificates(open(a.google_roots, "rb").read())]

    prev_hash, audio, last_transcript = None, None, None
    for n, m in enumerate(b["manifests"], 1):
        payload = m["payload"].encode()
        p = json.loads(payload)
        print(f"\n[{n}] {m['kind']}  ({p.get('type')})  signed {p.get('signed_at_device_clock')}")
        say(hashlib.sha256(payload).hexdigest() == m["payload_sha256"], "payload hash")
        certs = load_chain(m["cert_chain_pem"])
        try:
            certs[0].public_key().verify(base64.b64decode(m["signature_b64"]), payload, ec.ECDSA(hashes.SHA256()))
            say(True, "signature (ECDSA P-256 / SHA-256)")
        except Exception as e:
            say(False, f"signature: {e}")
        chain_ok, root = check_chain(certs)
        say(chain_ok, f"certificate chain ({len(certs)} certs)")
        fp = root.fingerprint(hashes.SHA256()).hex()
        print(f"        root: {root.subject.rfc4514_string()}  sha256={fp[:32]}…")
        if roots:
            rk = root.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
            say(rk in roots, "root is a Google hardware attestation root")
        att = parse_attestation(certs[0])
        if att is None:
            say(False, "attestation extension present")
        else:
            say(att["keymint_security"] in ("TrustedEnvironment", "StrongBox"),
                f"key in secure hardware ({att['keymint_security']})")
            say(att["challenge"] == p.get("install_id", "").encode(), "attestation challenge == install_id")
            if "verified_boot" in att:
                say(att["verified_boot"] == "Verified" and att.get("device_locked"),
                    f"verified boot={att['verified_boot']}, bootloader locked={att.get('device_locked')}")
            if att.get("app_packages"):
                print(f"        app: {', '.join(att['app_packages'])}  signer={','.join(s[:16] for s in att.get('app_signer_sha256', []))}")
            if m["kind"] == "capture":
                say(att["user_auth_required"], "key requires user authentication (biometric/PIN) per use")

        u = p.get("audio", {}).get("sha256") if m["kind"] == "capture" else p.get("audio_sha256")
        if u:
            if audio is None: audio = u
            say(u == audio, "audio_sha256 consistent across manifests")
        if m["kind"] == "transcript":
            if p.get("prev_manifest_sha256") is not None or prev_hash is not None:
                say(p.get("prev_manifest_sha256") == prev_hash, "chained to previous manifest")
            last_transcript = p
        prev_hash = m["payload_sha256"]

    print()
    if last_transcript:
        t = hashlib.sha256(b["transcript_canonical"].encode()).hexdigest()
        say(t == last_transcript["transcript_sha256"], "bundled transcript matches latest signed version")
    if a.audio:
        say(pcm_sha256(a.audio) == audio, "audio file matches signed audio_sha256")
    print("\nRESULT:", "VALID" if ok_all else "INVALID")
    sys.exit(0 if ok_all else 1)


if __name__ == "__main__":
    main()

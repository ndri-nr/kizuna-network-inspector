# 30_APP_UNDER_TEST_SETUP.md

> Project: Kizuna Network Inspector
>
> Document Type: Integration guide for the **app being debugged** (the "app under test")
>
> Audience: Hand this file to the *other* project (your app's repo). It lists the
> changes that app needs so KNI can capture and decrypt all of its HTTPS traffic.

---

## 1. Why any change is needed

KNI decrypts HTTPS by acting as a man-in-the-middle: it presents a leaf certificate
signed by the **Kizuna Root CA** (a CA generated on-device and installed by you as a
*user* certificate).

On Android **7.0+ (API 24+)**, apps trust **only the system CA store** by default and
**ignore user-installed CAs**. So an unmodified app rejects KNI's certificate with:

```
java.security.cert.CertPathValidatorException: Trust anchor for certification path not found
```

A **debug build** can safely opt in to trusting user CAs. This doc makes that change
**debug-only** so your release build is never weakened.

> Do NOT ship any of this in a release build. Trusting user CAs in production is a
> security hole.

---

## 2. What the app under test must do

### Step A — Trust user CAs in debug via Network Security Config

Create the config **only for the debug build** so release is untouched.

`src/debug/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <!-- Keep trusting the real system CAs... -->
            <certificates src="system" />
            <!-- ...and additionally trust user-installed CAs (KNI's root). -->
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

Reference it from a **debug-only manifest** so it never leaks into release.

`src/debug/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:networkSecurityConfig="@xml/network_security_config"
        tools:replace="android:networkSecurityConfig"
        xmlns:tools="http://schemas.android.com/tools" />
</manifest>
```

(If your main manifest doesn't already set `networkSecurityConfig`, you can drop the
`tools:replace` attribute.)

Result: the **debug** APK trusts KNI's user-installed CA; **release** is unchanged.

### Step B — Disable certificate pinning in debug

If the app pins certificates, it will reject KNI's leaf even with Step A. Turn pinning
off for debug builds:

- **OkHttp `CertificatePinner`**: only add the pinner when `!BuildConfig.DEBUG`.

  ```kotlin
  val builder = OkHttpClient.Builder()
  if (!BuildConfig.DEBUG) {
      builder.certificatePinner(
          CertificatePinner.Builder()
              .add("api.example.com", "sha256/AAAA…")
              .build()
      )
  }
  ```

- **Network Security Config `<pin-set>`**: don't include it in the debug config above.
- **Third-party SDKs / Flutter / React Native / Cronet** with their own pinning: gate
  or disable it for debug (see Step D for Cronet/QUIC).

### Step C — Keep HTTPS over TCP (avoid QUIC / HTTP-3)

KNI intercepts **TCP** ports 80 and 443. It does **not** decrypt **QUIC / HTTP-3
(UDP 443)**. If the app talks HTTP/3, that traffic is invisible to KNI.

- Plain **OkHttp / Retrofit / HttpURLConnection**: already TCP (HTTP/1.1 or h2 over
  TCP) — nothing to do. KNI forces **ALPN `http/1.1`**, and these clients fall back to
  HTTP/1.1 automatically.
- **Cronet / `google-http-client` with QUIC**, or explicit HTTP/3: **disable QUIC** in
  debug (e.g. don't call `enableQuic(true)` on the Cronet engine), so requests go over
  TCP where KNI can see them.

HTTPS itself is fine and expected — you do **not** need cleartext. KNI decrypts real
HTTPS; keep your API on `https://`.

### Step D — One VPN at a time

Android allows only **one** active `VpnService`. While KNI's VPN is running, no other
VPN (WARP/1.1.1.1, corporate VPN, etc.) can run, and vice-versa. Turn other VPNs
**off** before capturing.

---

## 3. What to do in the KNI app (the inspector side)

1. **Save & install the CA**: KNI → Settings → Root Certificate → **Save to File** →
   then Android Settings → Security → *Encryption & credentials* → **Install a
   certificate → CA certificate** → pick `kni_root_ca.crt`. (The in-app "Install"
   button is unreliable on Android 11+; use the file route.)
2. **Enable decryption**: KNI → Settings → **Decrypt HTTPS (MITM)** → ON.
   *(Off = HTTPS is relayed opaquely and you only see a `CONNECT https://host`
   metadata row at connection close — no method/body.)*
3. **Scope capture to your app**: KNI → Settings → **Apps to Capture** → select your
   debug app's package. This routes only your app through the VPN, so KNI's MITM can't
   break other apps, and you avoid noise. (Empty selection = all apps.)
4. Start capture, exercise your app, open a request in the feed → **Request /
   Response** tabs show decrypted headers + body; **TLS** tab shows version/cipher.

---

## 4. Verification checklist

- [ ] Device is **not** running another VPN.
- [ ] KNI Root CA installed under Settings → Trusted credentials → **User**.
- [ ] KNI: **Decrypt HTTPS** is ON and capture is (re)started after enabling it.
- [ ] App under test is a **debug** build with the Step A network security config.
- [ ] Certificate pinning is **off** in debug (Step B).
- [ ] App uses HTTPS over **TCP** (no QUIC/HTTP-3) in debug (Step C).
- [ ] A request from the app appears in KNI's feed with a real method (GET/POST/…),
      decrypted headers, and a readable body.
- [ ] TLS tab shows a version (e.g. `TLSv1_3`) and cipher — confirms decryption.

If a request still errors with `Trust anchor … not found` → Step A didn't take
(wrong build variant, manifest not merged, or CA not installed as *user*). If it
errors with a pinning message → Step B. If it simply never appears → Step C (QUIC) or
another VPN is active (Step D).

---

## 5. Alternatives without touching the app

- **Emulator**: run an AVD with a writable system image (`emulator -writable-system`),
  then install the KNI CA into the **system** store — every app trusts it, no per-app
  config. Good for testing apps you don't own.
- **Rooted device**: install the CA into `/system/etc/security/cacerts` (e.g. Magisk
  `MagiskTrustUserCerts` module). Same effect system-wide.

For normal development of your own app, **Step A (debug network security config) is the
recommended path** — no root, release build untouched.

---

## 6. Known limitations (cannot be worked around from KNI)

- Apps that **pin** and that you cannot modify.
- **QUIC / HTTP-3 (UDP 443)** traffic.
- Other apps' traffic while decryption is on but they don't trust the user CA — scope
  capture to your app (Step 3.3) to avoid breaking them.
- Non-rooted devices cannot decrypt apps that trust only the system store.

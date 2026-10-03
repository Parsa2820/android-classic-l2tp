# Classic L2TP

A stock Android client for classic **L2TP/IPsec** (IKEv1 with a pre-shared key, NAT-T, PPP MS-CHAPv2). It uses `VpnService` and a TUN interface. It does not need root, and it is not an IKEv2 client.

Android 12 removed L2TP from Settings. The network stack here is the TunnelForge engine (GPL-3.0-only), wrapped in a small Kotlin app. See [ATTRIBUTION.md](ATTRIBUTION.md).

## What it speaks

| Layer | Spec | What the client does |
| --- | --- | --- |
| IKEv1 | RFC 2409 | Main Mode with PSK. Phase 1 proposes AES-128-CBC and 3DES-CBC, HMAC-SHA1, MODP 2048 (RFC 3526). |
| NAT-T | RFC 3947, RFC 3948 | UDP 500, then ESP-in-UDP on 4500. NAT-D is forced so the peer treats the path as translated. A normal app cannot send IP protocol 50. |
| ESP | RFC 4303, transport mode | AES-128-CBC or 3DES-CBC with HMAC-SHA1-96, selected by the server. |
| L2TP | RFC 2661, RFC 3193 | Control connection, then a data session. Inner UDP port 1701 rides inside ESP. |
| PPP | RFC 1661, RFC 1332 | LCP, MS-CHAPv2 (RFC 2759), IPCP for an IPv4 address and DNS. |
| Android | VpnService | `protect()` on the tunnel sockets, then `establish()` with the IPCP address. |

MS-CHAPv2 is weak (MD4 and DES). It is what classic L2TP servers still ask for, including the Docker lab.

## Build in Android Studio

Requirements:

- Android Studio with SDK 36, build-tools 36, CMake 3.22.1, and NDK `28.2.13676358`
- JDK 17
- minSdk 31, targetSdk 36, compileSdk 36

Open this directory. Android Studio writes `local.properties` with `sdk.dir`. Then **Run** the `app` configuration, or from a shell:

```sh
export ANDROID_HOME="$HOME/Android/Sdk"   # or wherever the SDK lives
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. ABIs are `arm64-v8a` (phones) and `x86_64` (the emulator).

The first native build compiles vendored mbedTLS. Later builds are incremental.

## App

Enter the server, IPsec pre-shared key, and PPP username and password, then **Connect**. Android shows the VPN consent dialog the first time (`VpnService.prepare`). A foreground notification stays up for the session. **Disconnect** stops IKEv1/L2TP and removes the TUN interface.

The PSK and password are kept in memory for the session. The hostname and username are the only values stored in app preferences.

The status line follows the handshake: IKEv1, L2TP, PPP, then the IPv4 address from IPCP. If IPCP does not offer DNS, the TUN uses `1.1.1.1` and `9.9.9.9`.

## Docker lab

The lab is `hwdsl2/ipsec-vpn-server`: Libreswan for IKEv1 and xl2tpd for L2TP/PPP. L2TP/IPsec with a PSK is the default on that image (it also offers IKEv2; this client does not use that).

```sh
cd lab
cp .env.example .env
# Phone on your LAN: set VPN_PUBLIC_IP to this machine's LAN address.
docker compose up -d
docker logs -f classic-l2tp-lab
```

Host networking is required. Mapping UDP 500/4500 through Docker's userland proxy changes the address IKE sees and NAT-T falls over. The container is privileged so Libreswan can install kernel XFRM state.

Default lab credentials in `.env.example`:

| Field | Value |
| --- | --- |
| PSK | `lab-ipsec-psk` |
| PPP user | `labuser` |
| PPP password | `lab-password` |

### Same Linux host, no phone

Build the host hook. It links the same C engine and runs the handshake with socket protection turned off:

```sh
cmake -S native -B native/build
cmake --build native/build -j
ctest --test-dir native/build --output-on-failure
./native/build/l2tp-lab 127.0.0.1 labuser 'lab-password' 'lab-ipsec-psk'
```

`result=0` and a `client_ip=` line mean IKEv1, L2TP, and PPP all completed. The process then sends an L2TP teardown. Exit codes match the engine: `1` IKE, `2` L2TP, `3` PPP, `12` cancelled.

`native/tests/mschap_rfc2759` checks the RFC 2759 section 9.2 NT-Response vector and an MS-CHAPv2 failure message. That test does not need the lab.

### Phone or tablet

1. Put the device on the same LAN as the Docker host.
2. Set `VPN_PUBLIC_IP` in `lab/.env` to that host's LAN address, and recreate the container.
3. Install the debug APK. Use that LAN address as the server.
4. Watch `docker logs -f classic-l2tp-lab` and `adb logcat -s tunnel_engine:V TunnelVpnService:V`.

UDP 500 and 4500 must reach the host. Guest Wi-Fi that blocks UDP, or a firewall that only allows 4500, will fail in IKE.

### Emulator

The standard Android emulator NAT (`10.0.2.2`) is a poor fit for IKE and NAT-T. Datagrams to the host are rewritten, and Libreswan often never completes Quick Mode. Prefer a device on the LAN.

If you still try the emulator:

- The server address from inside the emulator is `10.0.2.2`.
- `VPN_PUBLIC_IP` must be the address Libreswan should advertise, which often does not match what the emulator uses.
- Cold-boot the AVD after changing host firewall rules.

A failure here is usually the emulator network, not proof that the stack only speaks IKEv2.

## Layout

| Path | Role |
| --- | --- |
| `app/src/main/kotlin/` | Compose UI, VPN consent, foreground `TunnelVpnService` |
| `app/src/main/cpp/` | Vendored TunnelForge engine plus the JNI bridge |
| `third_party/mbedtls/` | mbedTLS 2.28.9 |
| `native/` | Host build of the engine, `l2tp-lab`, RFC 2759 test |
| `lab/` | Docker Compose for the L2TP/IPsec server |
| `app/src/test/` | JVM tests for profile checks, IPCP address formatting, DNS fallback, exit codes |

## Known limitations

- IPv4 only. IPv6 is not captured and is not negotiated.
- IKEv1 Main Mode with a pre-shared key. No certificates, no EAP, no IKEv2.
- PPP authentication is whatever the server picks from the engine's LCP offer. The lab uses MS-CHAPv2. PAP and CHAP-MD5 exist in the engine if a server asks for them.
- ESP is AES-128-CBC or 3DES-CBC with HMAC-SHA1-96, transport mode, NAT-T. There is no AES-GCM.
- NAT-T is forced. A path that refuses UDP 4500 will not work.
- One profile, full-device IPv4 route (`0.0.0.0/0`), MTU 1400. The server's own IPv4 is excluded from the TUN on API 33+ so tunnel packets are not routed back into the tunnel. Older API 31–32 devices rely on `protect()` alone.
- Proxy mode and the gVisor userspace stack from TunnelForge are not wired up. The JNI symbols remain so the native library still loads.
- MS-CHAPv2 can be brute-forced offline. Use this for networks that still require L2TP/IPsec, not as a modern VPN design.
- The engine log redacts secrets before they reach the screen. Logcat can still contain protocol detail. Do not post a full `tunnel_engine` capture publicly.

## Next protocol work

The handshake path is the TunnelForge engine, not a stub. If a particular server still fails:

1. Run `l2tp-lab` against it and keep the `result=` code (`1`, `2`, or `3`).
2. Compare `adb logcat -s tunnel_engine:V` with Libreswan `auth.log` inside the container.
3. Keying mismatches show up as Quick Mode decrypt errors. `TUNNEL_FORGE_KEYMAT_VARIANT` in `app/src/main/cpp/CMakeLists.txt` selects the KEYMAT layout (1 is the default).
4. Inner L2TP UDP checksums are left at 0 (`TUNNEL_FORGE_ESP_INNER_UDP_NO_CHECKSUM`), which is legal in RFC 768 and matches the servers this engine was written for.

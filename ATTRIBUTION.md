# Attribution

Classic L2TP is GPL-3.0-only because its tunnel engine is derived from TunnelForge.

## TunnelForge

- Project: [evokelektrique/tunnel-forge](https://github.com/evokelektrique/tunnel-forge)
- License: GPL-3.0-only
- Commit imported: `a71ef7cd9441a34924bb2ea416af75080694de2d` (2026-08-31)
- Files: `app/src/main/cpp/` (IKEv1, ESP-in-UDP, L2TP, PPP, MS-CHAPv2)
- Local changes are listed in `app/src/main/cpp/UPSTREAM.txt`

The Kotlin VpnService, Compose UI, host lab client, and tests in this repository are original code written against that engine. The combined work is distributed under GPL-3.0-only. See `LICENSE`.

## mbedTLS

- Project: [Mbed-TLS/mbedtls](https://github.com/Mbed-TLS/mbedtls) 2.28.9
- License: Apache-2.0 (`third_party/mbedtls/LICENSE`)
- Vendored so the Android and host builds do not download it during configure
- `MBEDTLS_MD4_C` is enabled at compile time because RFC 2759 MS-CHAPv2 uses MD4

Apache-2.0 is compatible with GPL-3.0 when the combination is distributed under GPL-3.0.

## Lab server

`lab/docker-compose.yml` runs the third-party image `hwdsl2/ipsec-vpn-server` (Libreswan + xl2tpd). That image is not part of this source tree.

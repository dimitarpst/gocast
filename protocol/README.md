# gocast protocol

Kotlin Multiplatform code shared by the Android sender and desktop receiver:

- WebSocket hello and successful-handshake messages.
- Join parameters and the existing QR join-link format, plus the optional remote
  room (`r`). Links without `r`, or with a malformed one, still join over the LAN.
- Control messages and ICE server lists for the signaling Worker (`cloud/signal`),
  and its address (`RemoteSignaling.BASE_URL`).
- Host/port formatting and parsing, including bracketed IPv6 addresses.

Both `android/` and `desktop/` include this directory as `:protocol`. Open either
app project as before; each compiles the shared sources with its own existing
Kotlin plugin and dependency catalog. Build output is separated by app under
`protocol/build/` so the two Gradle builds do not overwrite each other's classes.

All production code lives in `commonMain` without Android, desktop, or UI APIs.
The JVM target produces the library consumed by both current apps; an Android
library target is unnecessary because the protocol has no Android-specific code.
No iOS or web app target is configured.

Android URI query decoding stays in the Android adapter. Screen/audio capture,
WebRTC integration, desktop networking, identity storage, and UI stay in their
existing app projects.

Compatibility tests pin the existing JSON messages and join-link behavior,
including optional trust fields and permissive legacy parsing. Run from either
the `android/` or `desktop/` directory:

```powershell
.\gradlew.bat :protocol:jvmTest
```

# gocast desktop

Compose Desktop app for Windows (primary), with optional Linux/macOS packages later.

## Run (dev)

```bash
./gradlew :desktopApp:run
```

Gradle/Java runs do **not** reliably show the Windows Firewall Allow popup. That is expected.

## Package Windows MSI

Requires JDK 17+ with `jpackage`, and [WiX Toolset](https://wixtoolset.org/) on `PATH`.

```bash
./gradlew :desktopApp:packageMsi
```

Output (typical):

`desktopApp/build/compose/binaries/main/msi/gocast-1.0.0.msi`

### LAN firewall (port 8765)

Phones connect inbound to the PC on TCP **8765**. The packaged app:

1. Tries `netsh` on first launch to Allow `gocast.exe` + TCP 8765 on **private** networks (no PowerShell)
2. If that cannot run elevated, Windows should still show the normal **Allow gocast on private networks?** dialog when the app first listens

Install the MSI, launch gocast once, and Allow private networks if prompted.

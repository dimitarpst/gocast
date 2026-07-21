# gocast desktop

Compose Desktop app for Windows (primary), with optional Linux/macOS packages later.

## Run (dev)

```bash
./gradlew :desktopApp:run
```

Gradle/Java runs do **not** reliably show the Windows Firewall Allow popup. That is expected.

## Package Windows MSI

Requires JDK 17+ with `jpackage`, and **WiX Toolset 3.x** (`candle`/`light` on `PATH`).

Install (admin PowerShell / elevated winget; needs .NET Framework 3.5):

```bash
winget install --id WiXToolset.WiXToolset -e
```

Then:

```bash
./gradlew :desktopApp:packageMsi
```

Output (typical):

`desktopApp/build/compose/binaries/main/msi/gocast-1.0.0.msi`

### LAN firewall (port 8765)

Phones connect inbound to the PC on TCP **8765**. The packaged app elevates once
(UAC) and adds `netsh` Allow rules for `gocast.exe` + TCP 8765 on **all**
profiles (`profile=any`) — home Wi‑Fi is often marked Public on laptops.

No PowerShell. Gradle/Java runs do not set these rules.

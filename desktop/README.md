# gocast desktop

Compose Desktop app for Windows (primary), with optional Linux/macOS packages later.

## Run (dev)

```bash
./gradlew :desktopApp:run
```

Gradle/Java runs do **not** show the LAN setup gate or write firewall rules. That is expected.

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

The MSI is branded as **gocast** (publisher, Start Menu group, shortcut, description). Packaging also injects an uninstall custom action that runs `gocast-cleanup.cmd`.

### First-run LAN setup (port 8765)

After a fresh install, the app always shows the network permission screen once before the QR. Windows needs an Allow rule so phones can reach TCP **8765**. The app elevates once (UAC) and adds `netsh` rules for `gocast.exe` + TCP 8765 on **all** profiles (`profile=any`) — home Wi‑Fi is often marked Public.

No PowerShell for end users. Gradle/Java runs do not set these rules.

### Uninstall wipe

Uninstalling the MSI runs `resources\gocast-cleanup.cmd`, which removes:

- Firewall rules named `gocast` and `gocast TCP 8765`
- `%LOCALAPPDATA%\gocast` (viewer engine, identity, prefs) for each Windows user profile
- Legacy `%USERPROFILE%\.gocast` if present

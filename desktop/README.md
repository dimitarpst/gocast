# gocast desktop

Compose Desktop app for Windows (primary), with optional Linux/macOS packages later.

## Run (dev)

```bash
./gradlew :desktopApp:run
```

Gradle/Java runs do **not** show the LAN setup gate or write firewall rules. That is expected.

### Viewer

The desktop viewer uses **WebView2** (system Edge via Wry / `kdroidfilter:composewebview`).

Optional autoplay for WebView2 (Gradle `run` sets this; for a packaged exe set it
yourself before launch if needed):

```bash
set WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS=--autoplay-policy=no-user-gesture-required
```

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

`desktopApp/build/compose/binaries/main/msi/gocast-<version>.msi`

The version comes from the root `release.properties`. Packaging configures upgrade/downgrade handling and explicit-uninstall network cleanup. Required authoring failures stop the package task.

For the branded setup wrapper, double-click **Build Windows Setup.cmd** at the repository root. This produces the normal setup EXE plus the underlying MSI and hashes; it never installs them. See [release packaging](../docs/releases.md) for prerequisites, optional signing, and pending validation.

### First-run LAN setup (port 8765)

After a fresh install, the app always shows the network permission screen once before the QR. Windows needs an Allow rule so phones can reach TCP **8765**. The app elevates once (UAC) and adds `netsh` rules for `gocast.exe` + TCP 8765 on **all** profiles (`profile=any`) — home Wi‑Fi is often marked Public.

No PowerShell for end users. Gradle/Java runs do not set these rules.

### Uninstall and repair

New packages run `resources\gocast-cleanup.cmd` before its payload is removed, only during explicit uninstall. It removes the firewall rules named `gocast` and `gocast TCP 8765`.

Personal data under `%LOCALAPPDATA%\gocast` and legacy `%USERPROFILE%\.gocast` is preserved. Repair restores installed components. The new setup wrapper owns maintenance for wrapper installations. Old published installers have different cleanup behaviour and require migration testing; this code does not rewrite their cached installers.

# gocast

**Your phone screen, on your PC. Same Wi‑Fi. Nothing else.**

No account. No cloud. No cables. Open gocast on the computer, connect from the phone, and you’re casting — picture and sound.

gocast is **a govrse app** — built for the moments that should feel obvious, not set up.

## How it works

1. Run **gocast** on your Windows PC  
2. Open **gocast** on your Android phone  
3. Connect with a **QR scan**, or **find the PC on Wi‑Fi and enter the code** shown on the desktop  
4. Share your screen — what you see and hear on the phone plays on the PC  

Phone and PC need to be on the **same Wi‑Fi**. The desktop app accepts connections on your network (port **8765**).

## Downloads

| Platform | File |
|----------|------|
| **Windows** | GoCast setup `.exe` for new releases; existing releases use `.msi` |
| **Android** | `.apk` |

Get the latest builds from [Releases](https://github.com/dimitarpst/gocast/releases).

The desktop **Updates** control checks GitHub for newer stable versions. The new
setup/update flow is under development; existing published packages are unchanged.
Work is tracked in [TASKS.md](TASKS.md). See [release packaging](docs/releases.md)
for building and validating the new Windows setup.

## Why it exists

Screen casting shouldn’t mean signing into someone else’s ecosystem, fighting a browser tab, or settling for muddy audio.

gocast does one job, cleanly.

## Repository

| Path | What |
|------|------|
| `android/` | Phone app |
| `desktop/` | Windows app |
| `protocol/` | Kotlin Multiplatform connection messages and pairing rules shared by both apps |
| `cast-site/` | Join links for `cast.govrse.app` |
| `branding/` | Logos and marks |

## Develop

For Windows + USB-connected phone testing, double-click one of these files:

- **Refresh Phone.cmd** — update and reopen the phone app.
- **Refresh PC.cmd** — rebuild and reopen the PC app.
- **Refresh Both.cmd** — refresh both apps.

The command-line equivalent is:

```powershell
.\scripts\dev.ps1
```

This updates **gocast Dev** on the phone without uninstalling, restarts the desktop
development window, and records the build label in the refresh output. See
[local development testing](docs/development.md) for setup, logs and focused refreshes.

Open `android/` or `desktop/` as before. Both builds include `protocol/` automatically;
each uses its existing Kotlin version. Run `./gradlew :protocol:jvmTest` from either
app directory to check handshake and join-link compatibility.

**Desktop**

```bash
cd desktop
./gradlew :desktopApp:run
```

**Android**

Open `android/` in Android Studio, or:

```bash
cd android
./gradlew :app:installDebug
```

Release APK (needs local `keystore.properties` — see `keystore.properties.example`):

```bash
cd android
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## License

[MIT](LICENSE)

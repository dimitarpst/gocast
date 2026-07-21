# gocast

**Your phone screen, on your PC. Same Wi‑Fi. Nothing else.**

No account. No cloud. No cables. Open gocast on the computer, scan from the phone, and you’re casting — with sound.

That’s the whole product.

---

gocast is **a govrse app** — built for the moments that should feel obvious, not “set up.”

## How it works

1. Run **gocast** on your Windows PC  
2. Open **gocast** on your Android phone  
3. Scan the QR (or enter the short code)  
4. Share your screen — video and system audio go to the PC over your LAN  

Pause, resume, mute the phone speakers while you listen on the PC. Quality settings if you care. Otherwise ignore them.

## Downloads

Releases will ship as:

| Platform | What you get |
|----------|----------------|
| **Windows** | Installer (`.msi`) |
| **Android** | Sideload APK |

See [Releases](https://github.com/dimitarpst/gocast/releases) when they’re published.

> Phone and PC must be on the **same Wi‑Fi**. The desktop app listens on your LAN (TCP **8765**) so the phone can find it.

## Why it exists

Screen casting shouldn’t mean signing into someone else’s ecosystem, fighting a browser tab, or accepting muddy audio.

gocast is deliberately small: one job, done cleanly.

## Repo layout

| Path | What |
|------|------|
| `android/` | Phone app |
| `desktop/` | Windows (Compose) desktop app + viewer |
| `cast-site/` | `cast.govrse.app` — join links / light landing |
| `branding/` | Logos and marks |

Yes, the site lives here too. One place for the product is fine.

## Develop

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

## License

[MIT](LICENSE) — a govrse creation, open to build on.

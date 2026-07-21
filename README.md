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
| **Windows** | `.msi` installer |
| **Android** | `.apk` |

Get the latest builds from [Releases](https://github.com/dimitarpst/gocast/releases).

## Why it exists

Screen casting shouldn’t mean signing into someone else’s ecosystem, fighting a browser tab, or settling for muddy audio.

gocast does one job, cleanly.

## Repository

| Path | What |
|------|------|
| `android/` | Phone app |
| `desktop/` | Windows app |
| `cast-site/` | Join links for `cast.govrse.app` |
| `branding/` | Logos and marks |

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

[MIT](LICENSE)

# Local phone + PC testing

Double-click **Refresh Phone.cmd**, **Refresh PC.cmd**, or **Refresh Both.cmd** in
the repository root. No terminal command is required. These use the same refresh
script with the corresponding option.

The optional command-line equivalent is:

```powershell
.\scripts\dev.ps1
```

It builds the current Android and desktop sources, closes the previous development
window, updates the phone app in place, and opens both apps. The desktop title and
refresh output identify the build (for example `20261025-013000-123`); no development
banner appears in the phone UI. Build output and an Android startup log are saved in the
printed `.local-run/<build-label>/` folder. The desktop PID and its log folder are
also recorded in `.local-run/desktop.json`.

You do not need an APK or MSI installer for each code change. Run the command
again after editing, and wait for `Refresh complete`. A failed build does not
restart either app. This is a full restart; it does not promise to retain an
active casting session.

## First run

- Install Android SDK 36 and SDK Platform-Tools through Android Studio.
- Have JDK 21 available. The script checks `JAVA_HOME`, Gradle's downloaded JDKs,
  Android Studio's bundled JDK, and `PATH`. You can specify
  `-JavaHome 'C:\path\to\jdk-21'` explicitly.
- Connect the phone with USB debugging enabled, unlock it, and accept its USB
  debugging authorization prompt. USB is used to update and launch the app.
- Close any separately installed GoCast window or older `gradlew :desktopApp:run`
  before the first refresh. Only one desktop receiver can use TCP port 8765.
  Subsequent refreshes close the development window launched by this script.
- The first build may download Gradle and dependencies.

The SDK is located using `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or
`%LOCALAPPDATA%\Android\Sdk`. Use `-SdkRoot 'C:\path\to\Android\Sdk'` to override it.
With multiple connected devices, add `-Serial <id>` (from `adb devices`).

The Android development package is always **`app.gocast.debug`**, named
**gocast Dev**. It stays beside the signed release (`app.gocast`), and `adb install
-r` updates it without deleting its settings, trusted PCs or saved profiles.
Android Studio's `debug` variant uses this same package and debug signing key.
Open **gocast Dev** on the phone to test development changes. Reopen the walkthrough
using **How it works** on the home screen, without resetting any saved preferences.

The desktop launches from compiled sources with a **gocast Dev** window title.
It uses the same desktop settings and identity storage as the existing app.
Each desktop run uses its own copies of the workspace JARs so rebuilding cannot
alter classes that are still loaded by the previous window. The script restarts
only its recorded process, validating both PID creation time and executable. It
requests a normal close, then stops that same development process if it hangs.
If another process owns port 8765, the command
stops and identifies it instead of terminating it.

## Focused refreshes

```powershell
# Update only the phone, then show the introduction again.
.\scripts\dev.ps1 -AndroidOnly -Onboarding

# Update only the phone, then open quality settings.
.\scripts\dev.ps1 -AndroidOnly -Screen Quality

# Update only the desktop.
.\scripts\dev.ps1 -DesktopOnly
```

`-Onboarding` requests the walkthrough for this launch without resetting its
saved completion status or any other preferences. `-Screen Home` is the default;
`-Screen Quality` opens the quality sheet. These launch extras are development-only.

This workflow preserves the app's existing connection behavior. It does not
change firewall rules, Wi-Fi settings, receiver ports or ADB network forwarding.
Successful startup confirms that the latest apps launch; phone-to-PC casting is
a separate test.

## When a refresh fails

- **Port 8765 already used:** close the app named in the message and rerun. The
  script does not kill untracked processes.
- **No authorized phone:** check the cable, USB debugging and phone authorization.
- **Android refuses an update because of signing:** use the same debug keystore
  as the existing development app. The script never uninstalls either app to
  work around a signing mismatch.
- **Desktop exits or has no window:** read the printed `desktop.stderr.log` and
  `desktop.stdout.log`. If it remains open, close its window before rerunning.
- **Android exits:** check `android-startup.log` and select `app.gocast.debug` in
  Android Studio's Logcat. The script captures recent logs from that process; if
  it exits before its PID can be read, it filters by the development app's UID
  instead so early crashes can still be recorded.

To test an actual installer or release upgrade later, use a separate release
testing pass. This loop intentionally builds and runs the development variants.

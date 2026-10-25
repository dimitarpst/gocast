# Windows setup and releases

Status: implementation ready for user-run build and validation. No package from this work has been built, installed, or published yet.

## One release version

[`release.properties`](../release.properties) is the version source for Android, the desktop MSI, setup wrapper, and desktop update UI. The working version is **1.0.3**, Android code **8**. It is unreleased; GitHub's existing releases remain unchanged.

Increase the three-part version for each release, and increase Android's version code for every new APK intended to update the release package. Keep the release APK's existing signing identity. Never replace the contents of an already-published version to distribute a different build.

## Build without installing

Double-click **Build Windows Setup.cmd** in the project root. It requires the existing x64 JDK 21 / WiX 3.x packaging tools. The script finds Gradle-downloaded JDKs and common WiX locations, or accepts `-JavaHome` for another location.

It builds the MSI, authors maintenance rules, bundles a Microsoft-signed WebView2 prerequisite, and produces:

- `gocast-<version>-windows-x64-setup.exe` — the normal install/update/repair/uninstall entry point.
- `gocast-<version>-windows-x64.msi` — the underlying package for managed deployment; requires its own prerequisite handling. Do not use this to bypass the wrapper's maintenance entry.
- `SHA256SUMS.txt` — hashes of the final artifacts.

Output is under `.local-run/packages/<version>-<timestamp>/`. Each run has its own folder. Building does not install, launch, change firewall rules, or publish a release. `-Release` selects Compose's release/proguard package task; both variants still require validation before distribution.

The default WebView2 payload is Microsoft's small online bootstrapper. On a device that lacks the runtime, setup needs internet access to install it. To build an offline-capable setup, provide the official x64 Evergreen Standalone Installer with `-WebView2InstallerPath`. Packaging checks the Microsoft signature before embedding either payload. Because GoCast installs per machine, detection checks the machine runtime; a runtime installed only for the builder's current user does not satisfy all-user deployment. Uninstalling GoCast leaves this shared Microsoft runtime installed.

## Signing

For signing, put the existing code-signing certificate in the Windows user certificate store, put `signtool.exe` on PATH, and pass `-CertificateThumbprint`. The build signs the authored MSI, embeds it, signs the extracted Burn maintenance engine, reattaches that engine, signs the full setup, and verifies those signatures. No private key is copied into the project or package.

Without that parameter the artifacts are unsigned development packages. The updater checks release metadata over HTTPS and verifies the GitHub asset's SHA-256 digest and size; it does not claim independent Authenticode publisher verification. Confirm the production signing identity before publishing to general users. WiX 3 is inherited from the existing jpackage toolchain; evaluate a maintained toolchain migration before declaring the distribution production-ready.

## Maintenance rules

The MSI retains upgrade family `7E3A9C2B-5F14-4D8A-9B6E-1C0D4A8F2E75`; the setup wrapper has its own stable family. Burn caches the package, manages maintenance and failure reporting, and presents one normal Windows maintenance entry for wrapper installations.

The authored MSI detects newer related products and rejects downgrade installation. Old-product removal is moved into the Windows Installer transaction. Explicit uninstall runs network cleanup before its file is removed, and skips it during upgrades. Personal data is preserved; repair restores installed components and is not an application-data reset.

This does **not** retroactively change cleanup embedded in old cached installers. Upgrade from each actual existing release, including failure/rollback, must be checked before shipping. The underlying phone-to-PC connection bug is outside this change.

## Update contract

The desktop Updates control is reachable from pairing and initial LAN setup, including setup failure. It checks `dimitarpst/gocast` only when opened. It compares numeric versions and never offers equal/older versions as updates. A development build newer than GitHub's latest release says so explicitly.

The existing v1.0.0–v1.0.2 binaries cannot gain this control remotely. Their first upgrade to a build containing the updater is a manual setup download; later versions can use the in-app flow.

Publish the stable tag as `v<version>`. Upload the normal setup with the exact name `gocast-<version>-windows-x64-setup.exe`. The API asset must be fully uploaded, have the expected HTTPS release URL, a positive bounded size, and a SHA-256 digest. Ambiguous/unversioned legacy MSI names are not guessed, and the normal updater does not select a standalone MSI.

Download progress, cancellation, unavailable GitHub, rate limits, malformed metadata, absent architecture support, and integrity failure have distinct handling. An installer is rechecked before opening. Setup does not open while the phone is connected or casting. Opening setup is a handoff, not a claim that installation succeeded; the app is never killed silently.

A completed download is retained for later. Reopening Updates checks fresh release metadata and verifies the cached file before offering setup again, including after an app restart. Failed/cancelled attempts remove only their own partial download and preserve any previously staged installer. Partial downloads are restarted rather than resumed.

Validate the candidate before publishing. Check the source commit and version, create the matching release tag, then upload the artifacts and concise release notes as a **draft release**. Publish only after the maintenance checks below pass. No automatic publishing is configured by this change.

## User-run validation

1. Build/compile desktop and Android from this version. Run desktop update tests (`:shared:jvmTest`) and shared protocol tests (`:protocol:jvmTest`). These were authored/reviewed but not run by the agent in this pass.
2. On a clean Windows test environment, check setup appearance, install, missing WebView2, Cancel, and permission refusal. Test offline setup with a standalone prerequisite if needed.
3. Upgrade from the actual v1.0.0, v1.0.1, and v1.0.2 packages. Preserve identity/trust/settings; deliberately fail or cancel replacement and verify recovery. Include standard and administrator user contexts.
4. Run the same-version wrapper again. Check Repair, locked files/running app, restart prompts, Uninstall, and Windows Installed Apps. Check future-version downgrade rejection.
5. Exercise update checking against controlled release fixtures: newer, equal, older, prerelease, missing package, wrong architecture, missing/wrong digest, cancelled download, and file modified after download. No update test should publish a production release just to trigger the UI.

Record results in [TASKS.md](../TASKS.md). Keep package/runtime validation pending until actually performed.

## References

[Windows Installer upgrade removal](https://learn.microsoft.com/en-us/windows/win32/msi/removeexistingproducts-action), [downgrade prevention](https://learn.microsoft.com/en-us/windows/win32/msi/preventing-an-old-package-from-installing-over-a-newer-version), [WebView2 distribution](https://learn.microsoft.com/microsoft-edge/webview2/concepts/distribution), [Burn behaviour and themes](https://docs.firegiant.com/wix3/bundle/wixstdba/), [signing the maintenance engine](https://docs.firegiant.com/wix3/overview/insignia/).

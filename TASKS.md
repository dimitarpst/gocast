# GoCast tasks

Updated: 29 October 2026. Owner: Codex unless marked user input or user validation.

This is the persistent working list. Update it when work starts, scope changes, or a result is ready. Source changes and user-validated behaviour have separate statuses. Keep individual changes small enough to review and commit.

## In progress

- [ ] **DEL-01 — Audit installation and releases.** Local source and GitHub metadata audited; see [findings](research/installation-and-updates.md). Confirmed `dimitarpst/gocast`, default branch `master`, and published versions 1.0.0–1.0.2 with APK/MSI assets. Actual MSI tables/behaviour, installation scope, architecture, and signing remain to verify.
- [ ] **GIT-02 — Release artifacts.** Source checkpoints are saved locally in logical groups. Prepare validated packages and a matching release tag/draft only after DEL-07. Do not publish unvalidated packages.
- [ ] **DEL-07 — Validate release and maintenance scenarios.** User-run build/compile/tests, clean install, upgrade from actual GitHub releases, same-version repair, downgrade rejection, uninstall, locked files, cancellation, and rollback. This is the current release gate; no builds or installer/device tests have been run by the agent in this pass.

## Ready / next

- [ ] **DEL-08 — Confirm production signing and supported packaging tools.** Signing hooks exist, but no GoCast publisher certificate has been verified. Current setup uses WiX 3 inherited from jpackage; evaluate a maintained toolchain before production readiness. No automatic publication.

## Product backlog

- [ ] **UX-01 — Replace onboarding demonstrations with real contextual guidance.** Use the researched smooth overlay and failure-aware journey; no fake success or filler pages. See [research](research/onboarding/README.md). Fake demos removed in UI-05; contextual guidance not started.
- [ ] **DES-01 — Establish the Go experience.** Consistent visual foundations across GoCast phone/PC and future Go apps. See [design direction](research/go-design-language.md).
- [ ] **DES-02 — Explore a custom Go font and shared logo construction rules.** Record the two-colour vector direction as an input, not a finished specification.
- [ ] **NET-01 — Discuss and diagnose phone-to-PC connection failure.** Discussed 29 Oct: the user suspects dorm Wi-Fi client isolation (unconfirmed). Decision: connect across networks through Cloudflare signaling + STUN/TURN; see [plan](research/remote-connection.md). Do not change connection behaviour as part of UI or installer work.
- [ ] **NET-04b — Cloudflare deployment (user-run), cross-network test matrix, resilience/budget guard.** Plan steps 6–7. Needs NET-02/03/04 validated first. Setup steps in [cloud/signal/README.md](cloud/signal/README.md).

## Implemented; user validation pending

- [ ] **NET-02 — Signaling Worker + Durable Object.** `cloud/signal/`: Worker + SQLite Durable Object (hibernation, auto ping/pong, hashed host key, one phone per room, STUN/TURN credentials only for allowlisted rooms, relay kill switch). 26 local Vitest tests pass in the Workers runtime. Not deployed; no account id or secret in the repository.
- [ ] **NET-03 — Room id + remote signaling in protocol, desktop and Android.** Join link gains `r=`; desktop keeps a room id/host key in `identity.json`, dials out to the Worker and passes ICE servers to the viewer; Android stores the room, tries the Worker first with LAN fallback, uses the ICE servers and reconnects without mDNS. Protocol tests (30) pass from the desktop build; desktop server/trust code and a loopback fake-Worker test pass in a scratch JVM build. The Compose module and the Android app were not compiled (Google Maven unreachable in the agent sandbox). Needs Refresh PC / Refresh Phone builds and device tests.
- [ ] **NET-04 — Connection-type indicator.** Viewer and phone read the selected ICE candidate pair from `getStats` and show/log Direct (LAN), Direct (internet) or Relayed. Classifier tested in `protocol/`; the UI and stats fields are unverified on devices.
- [ ] **DEL-02 — Branded Windows setup.** Added a GoCast Burn theme with installation, maintenance, progress, files-in-use, success and failure pages. Existing Windows Installer remains underneath. WebView2 is a detected/shared prerequisite; setup appearance and behaviour are unverified. Build entry: **Build Windows Setup.cmd**.
- [ ] **DEL-03 — MSI maintenance rules.** New package authoring verifies identity/version, rejects downgrades, puts old-product removal inside the transaction, schedules cleanup before file removal, skips upgrade cleanup, and fails packaging on authoring errors. Cleanup no longer wipes any profile data. Legacy cached-MSI cleanup remains a separate migration test; do not call this fixed for older installs yet.
- [ ] **DEL-04 — One release version source.** Root `release.properties` feeds Android, desktop runtime/package, and setup. Development version is 1.0.3 / Android code 8; existing published releases remain unchanged. Release tag/asset contract documented in [docs/releases.md](docs/releases.md); candidate must be compiled and validated.
- [ ] **DEL-05 — Desktop update checking.** Updates control is reachable from pairing and initial LAN setup (including failure), reads stable releases from the confirmed repository, compares numeric versions, selects an explicit setup asset for the running architecture, and handles offline/rate-limit/unavailable metadata. Actual UI/network validation pending.
- [ ] **DEL-06 — Download and setup handoff.** Progress/cancel, bounded HTTPS redirects, size/SHA-256 verification, cached completed downloads across restarts, recheck before opening, and active-connection/cast guard implemented. Tests added for version/asset/redirect boundaries and changed downloads, but not run. SHA-256/TLS verifies delivery from the repository; independent publisher-signature validation is not implemented or claimed. Production signing belongs to DEL-08.
- [ ] **DEV-01 — Refresh Phone / PC / Both launchers.** Update installed development apps without repetitive uninstalling; save session logs. Some earlier runs were checked, but this does not validate Windows installer maintenance.
- [ ] **UI-02 — Reuse the approved QR symbol inside the scanner.** One vector resource shared with the action button; no build/device check performed for this change.
- [ ] **UI-05 — Phone UI polish.** Every screen now keeps clear of the status bar, 3-button/gesture nav bar, cutouts and keyboard. Quality screen redesigned after [research](research/quality-settings-ui.md):
  - borderless rounded groups;
  - Profile rows with a check mark;
  - pill buttons;
  - one "Detailed settings" row that opens five headed groups (Picture, Bitrate, Streaming, Video format, Compatibility);
  - each setting is a tap-to-pick row showing its value, with "Recommended" on the Balanced default and help (ⓘ) inside the picker.

  Setting values are unchanged; names are plain (e.g. "Keep it sharp", "Video priority"). "How it works" is one page; fake sharing/quality demos removed. Built and installed with Refresh Phone (build 20261030-034643-028). User looked at the Quality screen on the Poco and approved it (30 Oct). The nav-bar spacing and "How it works" were not separately confirmed, and the Xiaomi 15T Pro was not checked. User check on the phone pending.
- [ ] **UI-06 — New QR scanner.** Replaced the quickie library with our own CameraX + ML Kit screen (same library versions, nothing new downloaded). The frame springs onto a QR as soon as the camera locates it, even before it is readable (white), reading at 1280×960 instead of the default 640×480. GoCast QR → primary colour and connects; other QR → red frame and "Not a GoCast QR code"; it never connects. Bottom panel in the Quality screen's style has the approved QR symbol, Flashlight and Find on Wi‑Fi. Denied camera → "Allow camera access to scan" + Open settings, per [journey notes](research/onboarding/journey-and-recovery.md). Built and installed with Refresh Phone (build 20261030-040643-634). The user tried the first version and liked it; the early lock-on is not tried yet.
- [ ] **UI-04 — PC identity header.** Removed the pill and permanent Rename text after user feedback. Name is a compact transparent clickable control; hover/keyboard focus reveals a faded edit icon and subtle surface, with space reserved to prevent movement. Opens the approved modal. Specific pattern research saved in [research/pc-name-editing.md](research/pc-name-editing.md). Refresh PC.cmd rebuilt and relaunched successfully (build 20261025-045230-571). Visual review pending.

## Completed research / recorded requirements

- [x] Research contextual onboarding, motion, cancellation, and recovery; save sources and limitations under `research/onboarding/`.
- [x] Record the shared Go design direction in `research/go-design-language.md`.
- [x] Establish this persistent task list.
- [x] **UI-01 — Clean Wi-Fi action icon.** Concentric rounded bands; user described the result as beautiful after refreshing.
- [x] **GIT-01 — Reconnect the existing folder to GitHub history.** Attached `origin` to `https://github.com/dimitarpst/gocast.git`, fetched `master` and tags v1.0.0–v1.0.2, and set the baseline to the original history. No clone or checkout; hash verification confirmed all 204 inspected existing source/assets/docs files remained unchanged during attachment. Existing application changes remain local.
- [x] Saved the shared protocol, development interface/workflow, central version metadata, and MSI maintenance checkpoint in `fa63b66`. This is source preservation, not a validation result.
- [x] Static checks: final packaging PowerShell syntax, setup XML parsing, maintenance JavaScript syntax (`node --check`), and Git whitespace checks passed. These checks do not compile Kotlin, build packages, execute installer database edits, or validate runtime behaviour.

## Working agreements

- The agent may run the existing CMD launchers after completing changes (permission updated by the user). Keep build/refresh operations behind those launchers. Installer installation, publishing and device interaction are separate actions.
- Keep the real GoCast logo and the approved QR action symbol.
- Distinguish edits made from behaviour tested. Report limits plainly.
- Keep the task list current and commit at meaningful checkpoints once Git history is available.

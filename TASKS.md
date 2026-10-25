# GoCast tasks

Updated: 25 October 2026. Owner: Codex unless marked user input or user validation.

This is the persistent working list. Update it when work starts, scope changes, or a result is ready. Source changes and user-validated behaviour have separate statuses. Keep individual changes small enough to review and commit.

## In progress

- [ ] **DEL-01 — Audit installation and releases.** Local source and GitHub metadata audited; see [findings](research/installation-and-updates.md). Confirmed `dimitarpst/gocast`, default branch `master`, and published versions 1.0.0–1.0.2 with APK/MSI assets. Actual MSI tables/behaviour, installation scope, architecture, and signing remain to verify.
- [ ] **GIT-02 — Release artifacts.** Source checkpoints are saved locally in logical groups. Prepare validated packages and a matching release tag/draft only after DEL-07. Do not publish unvalidated packages.
- [ ] **DEL-07 — Validate release and maintenance scenarios.** User-run build/compile/tests, clean install, upgrade from actual GitHub releases, same-version repair, downgrade rejection, uninstall, locked files, cancellation, and rollback. This is the current release gate; no builds or installer/device tests have been run by the agent in this pass.

## Ready / next

- [ ] **DEL-08 — Confirm production signing and supported packaging tools.** Signing hooks exist, but no GoCast publisher certificate has been verified. Current setup uses WiX 3 inherited from jpackage; evaluate a maintained toolchain before production readiness. No automatic publication.

## Product backlog

- [ ] **UX-01 — Replace onboarding demonstrations with real contextual guidance.** Use the researched smooth overlay and failure-aware journey; no fake success or filler pages. See [research](research/onboarding/README.md).
- [ ] **DES-01 — Establish the Go experience.** Consistent visual foundations across GoCast phone/PC and future Go apps. See [design direction](research/go-design-language.md).
- [ ] **DES-02 — Explore a custom Go font and shared logo construction rules.** Record the two-colour vector direction as an input, not a finished specification.
- [ ] **NET-01 — Discuss and diagnose phone-to-PC connection failure.** Explicitly deferred by the user. Do not change connection behaviour as part of UI or installer work without that discussion.

## Implemented; user validation pending

- [ ] **DEL-02 — Branded Windows setup.** Added a GoCast Burn theme with installation, maintenance, progress, files-in-use, success and failure pages. Existing Windows Installer remains underneath. WebView2 is a detected/shared prerequisite; setup appearance and behaviour are unverified. Build entry: **Build Windows Setup.cmd**.
- [ ] **DEL-03 — MSI maintenance rules.** New package authoring verifies identity/version, rejects downgrades, puts old-product removal inside the transaction, schedules cleanup before file removal, skips upgrade cleanup, and fails packaging on authoring errors. Cleanup no longer wipes any profile data. Legacy cached-MSI cleanup remains a separate migration test; do not call this fixed for older installs yet.
- [ ] **DEL-04 — One release version source.** Root `release.properties` feeds Android, desktop runtime/package, and setup. Development version is 1.0.3 / Android code 8; existing published releases remain unchanged. Release tag/asset contract documented in [docs/releases.md](docs/releases.md); candidate must be compiled and validated.
- [ ] **DEL-05 — Desktop update checking.** Updates control is reachable from pairing and initial LAN setup (including failure), reads stable releases from the confirmed repository, compares numeric versions, selects an explicit setup asset for the running architecture, and handles offline/rate-limit/unavailable metadata. Actual UI/network validation pending.
- [ ] **DEL-06 — Download and setup handoff.** Progress/cancel, bounded HTTPS redirects, size/SHA-256 verification, cached completed downloads across restarts, recheck before opening, and active-connection/cast guard implemented. Tests added for version/asset/redirect boundaries and changed downloads, but not run. SHA-256/TLS verifies delivery from the repository; independent publisher-signature validation is not implemented or claimed. Production signing belongs to DEL-08.
- [ ] **DEV-01 — Refresh Phone / PC / Both launchers.** Update installed development apps without repetitive uninstalling; save session logs. Some earlier runs were checked, but this does not validate Windows installer maintenance.
- [ ] **UI-02 — Reuse the approved QR symbol inside the scanner.** One vector resource shared with the action button; no build/device check performed for this change.

## Completed research / recorded requirements

- [x] Research contextual onboarding, motion, cancellation, and recovery; save sources and limitations under `research/onboarding/`.
- [x] Record the shared Go design direction in `research/go-design-language.md`.
- [x] Establish this persistent task list.
- [x] **UI-01 — Clean Wi-Fi action icon.** Concentric rounded bands; user described the result as beautiful after refreshing.
- [x] **GIT-01 — Reconnect the existing folder to GitHub history.** Attached `origin` to `https://github.com/dimitarpst/gocast.git`, fetched `master` and tags v1.0.0–v1.0.2, and set the baseline to the original history. No clone or checkout; hash verification confirmed all 204 inspected existing source/assets/docs files remained unchanged during attachment. Existing application changes remain local.
- [x] Saved the shared protocol, development interface/workflow, central version metadata, and MSI maintenance checkpoint in `fa63b66`. This is source preservation, not a validation result.
- [x] Static checks: final packaging PowerShell syntax, setup XML parsing, maintenance JavaScript syntax (`node --check`), and Git whitespace checks passed. These checks do not compile Kotlin, build packages, execute installer database edits, or validate runtime behaviour.

## Working agreements

- The user runs builds, refreshes, installation, and device checks. Do not launch them automatically.
- Keep the real GoCast logo and the approved QR action symbol.
- Distinguish edits made from behaviour tested. Report limits plainly.
- Keep the task list current and commit at meaningful checkpoints once Git history is available.

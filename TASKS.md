# GoCast tasks

Updated: 25 October 2026. Owner: Codex unless marked user input or user validation.

This is the persistent working list. Update it when work starts, scope changes, or a result is ready. Source changes and user-validated behaviour have separate statuses. Keep individual changes small enough to review and commit.

## In progress

- [ ] **DEL-01 — Audit installation and releases.** Local source and GitHub metadata audited; see [findings](research/installation-and-updates.md). Confirmed `dimitarpst/gocast`, default branch `master`, and published versions 1.0.0–1.0.2 with APK/MSI assets. Actual MSI tables/behaviour, installation scope, architecture, and signing remain to verify.
- [ ] **GIT-02 — Save coherent commits and release artifacts.** Git is reconnected and the research/task-list checkpoint is saved locally. Next, review the existing application changes in logical groups before committing them. Do not publish unvalidated packages.

## Ready / next

- [ ] **DEL-02 — Define the GoCast setup experience.** Branded, familiar Install, Update, Repair, and Uninstall flows; clear progress, cancellation, failure, and completion. Windows first. Use a supported installer engine underneath.
- [ ] **DEL-03 — Fix installer lifecycle.** Cleanup must execute while its payload exists, distinguish explicit uninstall from upgrade, preserve settings during upgrades/repair, and report packaging failures. Replace the legacy all-profile data wipe policy before enabling cleanup reliably. Account for cleanup embedded in older cached MSIs; a new installer alone cannot retroactively fix it.
- [ ] **DEL-04 — Establish one release version source.** Feed Android display/build versions, desktop package/runtime versions, release tags, and installer metadata consistently. Never overwrite an existing published release or reuse an Android version code.
- [ ] **DEL-05 — Add desktop update checking.** Manual check against the confirmed GitHub repository; compare versions numerically; select the correct Windows asset; handle offline/rate-limit/missing-asset cases. No downgrade path or silent interruption of casting.
- [ ] **DEL-06 — Implement verified update download and installer handoff.** Show release notes/progress, verify integrity and publisher authenticity, request installation intentionally, and retain a usable installed version if updating fails.
- [ ] **DEL-07 — Validate release and maintenance scenarios.** User-run clean install, upgrade from an actual existing GitHub release, same-version repair, downgrade rejection, uninstall, locked files, cancellation, interrupted download, and data preservation. Do not mark the installer stable before this passes.

## Product backlog

- [ ] **UX-01 — Replace onboarding demonstrations with real contextual guidance.** Use the researched smooth overlay and failure-aware journey; no fake success or filler pages. See [research](research/onboarding/README.md).
- [ ] **DES-01 — Establish the Go experience.** Consistent visual foundations across GoCast phone/PC and future Go apps. See [design direction](research/go-design-language.md).
- [ ] **DES-02 — Explore a custom Go font and shared logo construction rules.** Record the two-colour vector direction as an input, not a finished specification.
- [ ] **NET-01 — Discuss and diagnose phone-to-PC connection failure.** Explicitly deferred by the user. Do not change connection behaviour as part of UI or installer work without that discussion.

## Implemented; user validation pending

- [ ] **DEV-01 — Refresh Phone / PC / Both launchers.** Update installed development apps without repetitive uninstalling; save session logs. Some earlier runs were checked, but this does not validate Windows installer maintenance.
- [ ] **UI-02 — Reuse the approved QR symbol inside the scanner.** One vector resource shared with the action button; no build/device check performed for this change.

## Completed research / recorded requirements

- [x] Research contextual onboarding, motion, cancellation, and recovery; save sources and limitations under `research/onboarding/`.
- [x] Record the shared Go design direction in `research/go-design-language.md`.
- [x] Establish this persistent task list.
- [x] **UI-01 — Clean Wi-Fi action icon.** Concentric rounded bands; user described the result as beautiful after refreshing.
- [x] **GIT-01 — Reconnect the existing folder to GitHub history.** Attached `origin` to `https://github.com/dimitarpst/gocast.git`, fetched `master` and tags v1.0.0–v1.0.2, and set the baseline to the original history. No clone or checkout; hash verification confirmed all 204 inspected existing source/assets/docs files remained unchanged during attachment. Existing application changes remain local.

## Working agreements

- The user runs builds, refreshes, installation, and device checks. Do not launch them automatically.
- Keep the real GoCast logo and the approved QR action symbol.
- Distinguish edits made from behaviour tested. Report limits plainly.
- Keep the task list current and commit at meaningful checkpoints once Git history is available.

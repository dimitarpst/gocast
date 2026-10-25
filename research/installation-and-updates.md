# Installation, maintenance, and updates

Audited: 25 October 2026. Windows first. Status: local source and GitHub release metadata audited; published package internals and runtime behaviour are unverified.

## Confirmed repository and releases

GitHub access is now connected to the verified `dimitarpst` account. The repository is [dimitarpst/gocast](https://github.com/dimitarpst/gocast), with default branch `master`. Published releases [v1.0.0](https://github.com/dimitarpst/gocast/releases/tag/v1.0.0), [v1.0.1](https://github.com/dimitarpst/gocast/releases/tag/v1.0.1), and [v1.0.2](https://github.com/dimitarpst/gocast/releases/tag/v1.0.2) each contain matching APK and MSI assets. The latest listed release is v1.0.2, with `gocast-1.0.2.apk` and `gocast-1.0.2.msi`. Asset metadata includes SHA-256 digests; this alone does not establish the package publisher's signing identity.

The local folder has been attached to the original `master` history without cloning or checking out files. Verification of 204 source/assets/docs files found no file-content changes during attachment. The pre-existing application differences are retained as local changes. No code was pushed and no release was published by this reconnection.

## Product requirement

GoCast should have a branded setup and maintenance experience that feels familiar: Install, Update, Repair, and Uninstall. Update is intentional, uses GitHub Releases, and offers newer compatible versions. A downgrade browser is not required.

Use an established Windows installation engine for file ownership, registration, maintenance, and rollback. GoCast supplies the visual language and clear steps. Custom branding should not mean implementing a separate file-copy/uninstall system or treating deletion of the application folder as uninstallation.

## Findings from the current source

| Area | Current evidence | Consequence |
| --- | --- | --- |
| Installer engine | Compose/jpackage generates an MSI using WiX. | There is already a Windows-native base to assess before replacing packaging. |
| Upgrade identity | `desktop/desktopApp/build.gradle.kts` sets stable upgrade UUID `7E3A9C2B-5F14-4D8A-9B6E-1C0D4A8F2E75`. | Keep the existing upgrade family unless a deliberate migration requires otherwise. Actual published MSI identities still need inspection. |
| Versioning | Android declares version name 1.0.2 and code 7; desktop repeats 1.0.2 in two package settings. | Versions exist, but are duplicated and do not yet constitute a reliable update lifecycle. Do not choose the next release number before checking existing releases. |
| Cleanup scheduling | `inject-uninstall-cleanup.js` schedules a file-backed cleanup action at sequence 3700, described as after RemoveFiles. | The installed script can be gone before execution. Confirm the generated MSI tables, then fix ordering or use a cleanup mechanism that does not depend on a removed payload. |
| Upgrade condition | Cleanup is conditioned only on `REMOVE="ALL"`. | Windows upgrades can remove the previous product with REMOVE=ALL too. An upgrade must not trigger destructive uninstall-data cleanup. |
| Data removal | `gocast-cleanup.cmd` attempts to wipe GoCast data under every accessible Windows user profile and deletes registry keys. | Do not make this run more reliably without first replacing its data-removal policy. Ordinary maintenance must not silently wipe other users' data. |
| Packaging errors | MSI post-processing failures log a warning and let packaging continue. | A successfully produced installer file is not proof that its maintenance behaviour is configured correctly. Required package checks should fail the release build. |
| Update controls | No dedicated update implementation found in the inspected desktop source. | Need release discovery, version comparison, failure states, download validation, and intentional installer handoff. |
| Git working copy | Initially lacked `.git`; now attached to the original remote history in this same folder. | Existing changes are visible against the real baseline, with no replacement checkout. Review and commit them in logical groups. |

The cleanup findings are source-level risks, not a reproduced diagnosis of the user's uninstall failure. No MSI was built, installed, modified, or uninstalled during this audit.

## Legacy upgrade is a separate case

The old product's uninstall actions come from its cached installer. Fixing the next package's cleanup condition does not retroactively fix those older actions. Before shipping an update, inspect an actual existing release and design a migration that preserves identity, trusted devices, and settings through its removal path. Validate upgrade failure and rollback as well as success.

Do not promise that repairs restore personal settings: repair restores installed program components. Keep application-data reset distinct from repair, and make any remove-data option explicit and scoped to the intended user.

## Intended maintenance behaviour

| Entry / situation | Expected result |
| --- | --- |
| Fresh setup | Explain location and permission needs, install prerequisites if required, show actual progress, then offer launch. |
| Newer setup with older GoCast installed | Offer Update in the same installation family, preserve data, and handle a running cast explicitly. |
| Same-version setup or Windows maintenance entry | Offer Repair and Uninstall; expose useful logs if an action fails. |
| Older setup opened against a newer installation | Refuse an unintended downgrade and explain the installed version. |
| User cancels or installation fails | Leave a known usable version or clearly explain recovery. Do not show success early. |
| Uninstall | Remove the registered application and its owned integration. Clearly separate optional personal-data removal from uninstalling the program. |

These are acceptance criteria, not implemented UI. Select the branded wrapper only after confirming legacy package migration. A WiX Burn bootstrapper can sit over MSI maintenance and dependencies; compare its implementation burden with the existing tooling before committing to a new stack.

## Intended update flow

1. Show installed version and a Check for updates action in the app.
2. Read published stable release metadata from the confirmed repository. Compare numeric release versions rather than strings or release dates. Ignore drafts/prereleases for the stable channel; never offer equal or older versions as an update.
3. Match an explicitly named asset for the correct platform/architecture. Missing packages, an inaccessible/private repository, rate limits, and being offline have distinct outcomes; none means “You're up to date.”
4. Show the new version, release notes, and download/install action. Do not interrupt a cast or close the application automatically.
5. Download to a staging location and verify the expected artifact before installation. Design integrity checks and publisher authentication together; a hash fetched alongside a file alone is not independent publisher authentication. Never ship a personal GitHub access token in the app.
6. Hand off intentionally to the supported installer. Respect UAC cancellation, preserve the current working install on failure, and require real completion before claiming success.

Android updating needs a separate distribution decision: signed APK updates must retain the application's signing identity and increase versionCode. Do not treat the separately installed development package as an update to the release package. Windows is the immediate installation/maintenance focus.

## Required evidence before a release

- Repository access, branch, tags, and APK/MSI asset names are confirmed. Verify package internals, architecture, and publishing workflow next.
- Inspect an actual previous MSI's ProductCode, UpgradeCode, installation scope, action sequencing, and data-cleanup behaviour.
- Confirm the signing and publisher-authentication setup without exposing credentials.
- Establish one version source and unique release artifacts, then implement the updater against that contract.
- Have the user run clean install, legacy upgrade, same-version repair, rollback/cancellation, and uninstall checks. Include standard/admin user contexts and a running app. Save results in the task list.

## Sources

- [Microsoft: RemoveExistingProducts](https://learn.microsoft.com/en-us/windows/win32/msi/removeexistingproducts-action) — upgrade removal can use REMOVE=ALL; Windows sets UPGRADINGPRODUCTCODE.
- [Microsoft: UPGRADINGPRODUCTCODE](https://learn.microsoft.com/en-us/windows/win32/msi/upgradingproductcode) — distinguishes removal during upgrade from explicit removal.
- [Microsoft: RemoveFiles](https://learn.microsoft.com/en-us/windows/win32/msi/removefiles-action) — removes installed files according to component state.
- [Kotlin: Native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html) — Compose packaging, version fields, and native distribution options.
- [GitHub: Releases API](https://docs.github.com/en/rest/releases/releases) — published release metadata and downloadable assets; tags alone are not releases.
- [WiX: Burn bundles](https://docs.firegiant.com/wix/tools/burn/) — a supported bootstrapper engine to evaluate for branded installation over package maintenance.

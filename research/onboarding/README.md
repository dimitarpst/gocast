# Onboarding: animated guidance and connection recovery

Researched: 25 October 2026. Status: design proposal; no application changes or runtime validation in this research pass.

## Direction

Introduce GoCast through its real interface. A softly dimmed overlay reveals one actual control, accompanied by a brief, smoothly fading hint. The user performs the real action. Connection attempts can succeed, fail, be cancelled, or be interrupted; the introduction must remain coherent in every case.

The immediate goal is a first successful cast between phone and PC. Quality settings and profiles are introduced when relevant, without becoming prerequisites for connecting.

GoCast also establishes the shared look and interaction language for future Go apps. The overlay, controls, and recovery surfaces should follow those common foundations rather than becoming a separate visual style. See [the Go design direction](../go-design-language.md).

## Research pack

- [Motion and interaction](motion.md): appearance, animation, target positioning, accessibility, and interruption rules.
- [Journey and recovery](journey-and-recovery.md): both connection methods, failure branches, progress, and current code limitations.
- [Sources](sources.md): primary references, findings, and evidence limits.

## Evidence and proposed decisions

| Evidence | Proposed application to GoCast |
| --- | --- |
| Revolut's published redesign and a former lead designer's case study emphasise simpler decisions and access to real tasks. [R1, R2](sources.md#revolut) | Preserve GoCast branding and establish a clear primary action. Introduce features through actual use. |
| Android recommends contextual education, recovery, and resumable progress. [A1](sources.md#android) | Give each hint an eligibility condition and remember dismissal separately from completed setup. |
| Research on introductory slide decks found no task-success benefit in the apps tested. Coach-mark guidance favours focused, timely hints. [U1, U2](sources.md#usability-and-patterns) | Replace the disconnected demonstrations with short guidance on real controls; avoid a compulsory tour of every button. |
| Material distinguishes motion of objects from opacity and colour effects, which should not overshoot. [M1](sources.md#motion-and-accessibility) | Use calm fades and restrained movement. No bouncing spotlight, flashing ring, or repeating attention animation. |
| Recovery guidance distinguishes input mistakes from service failures. [E1, E2](sources.md#feedback-and-recovery) | A rejected code and an unreachable PC need different explanations and actions. Preserve useful context. |

These are GoCast design proposals, not claims that Revolut uses this exact overlay or animation. Its current first-run tour has not been independently verified. The user-provided screenshots inform visual hierarchy and surfaces, not the mechanics of onboarding; private banking screenshots are not copied here.

## Boundary before implementation

This is enough research to develop a small reviewable design covering the overlay, a connection failure, and a successful cast. Exact motion still needs human visual judgement. The first design must include those three states together; a polished success-only sequence is insufficient.

Before wiring the design into the app, resolve the state and recovery gaps identified in [journey-and-recovery.md](journey-and-recovery.md#current-code-observations). Do not fix the underlying connectivity bug as part of this research. Builds, installation, restarts, and device checks remain user-operated.

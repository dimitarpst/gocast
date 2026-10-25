# Sources and evidence limits

Reviewed 25 October 2026. Summaries are paraphrases. Proposed GoCast copy, timings, state rules, and recovery actions are design decisions to evaluate, not quotations from these references.

## Revolut

### R1 — Official redesign account, 10 October 2023

[Revolut 10: the era of ease](https://www.revolut.com/blog/post/revolut-10/).

Primary product account: customer research informed simpler navigation and quicker access to frequent tasks. Useful for hierarchy and reducing friction. Historical redesign material, not documentation of the current first-run overlay.

### R2 — Former lead designer's onboarding case study

[Ben Brouckaert: Revolut Lifecycle](https://www.benbrk.com/revolut-lifecycle).

First-person account of research into activation, confusing decisions, combined steps, and returning to unfinished setup from Home. It includes an NDA caveat and modified/omitted details. Retrieved through indexed page content; direct retrieval returned 404 during this review. Treat as supporting historical context, not the sole basis for an implementation or proof of today's product.

## Usability and patterns

### U1 — Experimental evidence, 8 March 2020

[NN/G: Mobile Tutorials: Wasted Effort or Efficiency Boost?](https://www.nngroup.com/articles/mobile-tutorials/).

A study of 70 people and four iPhone apps found no statistically significant task-success advantage from the introductory slide tutorials tested. Tutorial readers rated tasks harder. Limited to the sampled apps and tutorial type; this does not establish that every guided introduction is ineffective.

### U2 — Coach-mark observations, 16 February 2014

[NN/G: Instructional Overlays and Coach Marks for Mobile Apps](https://www.nngroup.com/articles/mobile-instructional-overlay/).

Supports one focused hint at a relevant moment and warns about lengthy sequences and confusing imitations of real UI. Examples are historical; use the interaction principles rather than copying dated visual styling.

### U3 — A banking design system, last updated 12 February 2021

[SEB Design Library: Onboarding](https://designlibrary.sebgroup.com/patterns/pattern-onboarding).

Distinguishes coach marks, walkthroughs, and interactive empty-state guidance. Recommends short, sparing introductions with later access to help. Evidence that contextual guidance is an established financial-product pattern, not evidence of Revolut's implementation or measured conversion gains.

## Android

### A1 — Official onboarding guidance

[Android Developers: Authentication & Onboarding](https://developer.android.com/design/ui/mobile/guides/patterns/onboarding).

Recommends separating upfront needs from contextual education, explaining permissions when needed, providing recovery, and allowing setup to be skipped and resumed. Authentication examples need adaptation: GoCast's task is connecting two devices, without account creation.

### A2 — Permission denial

[Android Developers: Request runtime permissions](https://developer.android.com/training/permissions/requesting).

Explains permission rationale, denial, and retaining the functionality that remains possible. Relevant to camera-free connection and avoiding repeated requests after denial. Camera permission and media-projection consent are different mechanisms.

### A3 — Screen-capture consent and lifecycle

[Android Developers: Media projection](https://developer.android.com/media/grow/media-projection).

New projection sessions require consent; Android 14+ restricts token reuse. The platform can terminate capture, including lock behaviour on applicable Android versions. Saved educational progress never substitutes for live permission or capture state.

## Motion and accessibility

### M1 — Official Material motion model

[Material 3: How motion works](https://m3.material.io/styles/motion/overview/how-it-works).

Distinguishes spatial movement from effects such as opacity, which should not overshoot. The public page is JavaScript-rendered; relevant content was available in indexed excerpts. Supports a motion vocabulary, not our proposed numeric timings or a dependency upgrade.

### M2 — Compose interruption handling

[Android Developers: Value-based animations](https://developer.android.com/develop/ui/compose/animation/value-based), [Animatable reference](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/Animatable).

Documents retargeting and interruption from current animation values. Useful for reacting to user input, navigation, and failures without restarting stale sequences. Does not supply onboarding-specific state management.

### M3 — Accessibility precedent

[USWDS: Tooltip](https://designsystem.digital.gov/components/tooltip/).

Documents keyboard accessibility, dismissal, and removal of nonessential transitions under reduced motion. This is a web component, not a drop-in Compose spotlight; its behaviour informs accessibility requirements only.

## Feedback and recovery

### E1 — Input errors versus application problems

[GOV.UK Design System: Error message](https://design-system.service.gov.uk/components/error-message/).

Recommends specific explanations and retaining input. Explicitly distinguishes incorrect user input from service problems that the person cannot fix by editing a field. Apply the distinction to code rejection versus connection failure; do not transplant government form layouts into GoCast.

### E2 — Feedback proportional to the situation

[Apple Human Interface Guidelines: Feedback](https://developer.apple.com/design/human-interface-guidelines/feedback).

Supports feedback near the relevant context, clear action outcomes, and interruption proportional to importance. Design guidance, not research proving a particular overlay treatment or an Android requirement.

## Still unverified

- Revolut's current first-run tour, precise overlay animation, timing, and dismissal behaviour.
- Which receiver-side event, if any, currently confirms a displayed frame to the phone.
- Which network failures GoCast can reliably distinguish without misleading diagnosis.
- The appropriate connection timeout and retry budget.
- Final scrim opacity, hint placement, and motion quality on the user's phone and PC.

Answer these through a focused source/state audit and later user-run visual checks. Further broad searches are unlikely to resolve app-specific gaps.

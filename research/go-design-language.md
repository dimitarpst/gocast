# Shared design direction for the Go apps

Recorded: 25 October 2026. Status: product requirement; visual specifications are still to be developed and reviewed.

## Intent

GoCast is an opportunity to establish a recognisable, consistent design language for the broader Go series / Goverse. Future apps should feel related through their visual and interaction choices, while remaining clear about their individual purpose.

The user wants the shared direction established here. This does not make GoCast's current appearance an approved design system: existing inconsistencies and rejected onboarding treatments remain work to resolve.

## Shared foundations

Define and review these as reusable decisions rather than choosing them independently for each screen or app:

- Typography: font family, hierarchy, weights, line heights, and consistent roles.
- Colour: background and surface hierarchy, text contrast, primary actions, and semantic status colours.
- Shape and spacing: a deliberate radius scale, padding, grouping, control sizes, and density.
- Controls: recognisable primary and secondary actions, inputs, cards, settings rows, and dialogs.
- Motion: calm entrances and exits, restrained feedback, interruption behaviour, and reduced-motion support.
- Language: short, direct labels; clear status; specific recovery instructions.
- Brand: a recognisable relationship between the Go name and each app's identity, including consistent logo placement and treatment.

The exact palette, type scale, shapes, accent strategy, and brand relationship are not yet approved. Revolut is an inspiration for polish and hierarchy, not a specification to copy or a replacement for Go's own identity.

## Room for each app and platform

Each app can have its own content, workflows, and necessary controls. Whether individual apps should have distinct accent colours is an open design decision.

Phone and desktop should share the same visual vocabulary and meaning of actions. Layout, density, navigation, touch targets, keyboard behaviour, and window handling should suit the device. Consistency does not require identical layouts or ignoring native system permission dialogs.

## Applying this to GoCast

Use real GoCast screens to establish the first coherent set of foundations and component states. Review the phone and desktop together, including Home/pairing, quality settings, the contextual overlay, and recovery from connection failure.

The onboarding overlay should inherit shared typography, surfaces, shapes, and motion. Its guidance content and eligibility remain specific to the app's tasks. Failure, loading, disabled, and successful states need the same care as the initial appearance.

After those decisions hold up in the actual screens, document their names and values alongside examples. Extract shared implementation only where it helps the current apps; a separate framework or cross-repository package is not required to establish the design direction.

## Scope now

This records the Goverse direction and extends the onboarding research brief. It does not authorise a full visual redesign, rename, logo replacement, or changes to connection behaviour. No application code is changed by this note.

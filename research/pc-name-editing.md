# PC name editing

Reviewed 25 October 2026 for the specific desktop rename affordance.

[PatternFly's inline-edit guidance](https://pf3.patternfly.org/v3/pattern-library/forms-and-controls/inline-edit/) explicitly describes an edit icon appearing on hover next to editable text, with clicking the text or icon starting editing. This is an archived design reference, not evidence of current user testing.

[Atlassian's inline-edit guidance](https://developer.atlassian.com/platform/forge/ui-kit/components/inline-edit/) requires sufficient visual affordance and keyboard access. Its inline-edit component also stresses alignment between read and edit states and clear confirmation/cancellation.

GoCast application: remove the permanent Rename label and pill. Keep the PC identity readable; add a hand cursor, restrained hover surface, and a short fade for the adjacent edit icon. Expose the same cue on keyboard focus. Reserve the icon's space to avoid text movement. Clicking the name opens the already-approved modal, retaining its explicit Save/Cancel behaviour. Inline editing is a possible later alternative, not necessary to fix the current control.

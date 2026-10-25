# Overlay motion and interaction proposal

Status: proposed values to judge visually; not measured Revolut specifications.

## Appearance

Keep the real screen in place. Fade a neutral dark scrim over it, leaving a rounded opening around the actual target. Match the target's shape, with approximately 8 dp of surrounding breathing room. Start by evaluating a scrim opacity around 0.48; tune for contrast on GoCast's dark surfaces.

Place one compact hint beside the opening. Use the existing typography and rounded surface language on both platforms. Prefer a short title and one sentence; include an arrow only if the relationship is otherwise unclear. Retain an obvious dismissal action. No decorative glow, fake pointer, replacement button, or duplicated screenshot.

## Proposed choreography

| Transition | Starting point for review |
| --- | --- |
| First appearance | Scrim fades in over 260 ms; hint fades in over 200 ms, starting roughly 60 ms later. |
| Target changes within a stable screen | Hint crossfades; the opening moves with a restrained, non-overshooting transition around 240 ms. |
| Navigation or a substantially different layout | Fade out, wait for the real target to be laid out, then fade in. Never sweep a spotlight across unrelated screens. |
| Dismissal | Fade away over roughly 160 ms; the action takes effect immediately. |
| Error, permission sheet, or missing target | Yield immediately to the real UI. No minimum display time and no delayed navigation to finish an animation. |
| Reduced motion | Remove movement and staging delays; use an immediate change or minimal fade consistent with the system preference. |

Material's separation of spatial and effects animation supports the distinction above; it does not prescribe these numbers. Opacity must settle without overshoot. Compose supports interrupted animations; changing a target mid-animation must continue from the current visual state rather than replaying the entrance. [Material motion](https://m3.material.io/styles/motion/overview/how-it-works), [Compose animations](https://developer.android.com/develop/ui/compose/animation/value-based).

## Position and input

- Measure the actual target after layout. Update its bounds for scrolling, keyboard appearance, rotation, desktop resizing, and font scaling. Never hardcode phone coordinates.
- Keep the opening, target hit area, and hint placement in the same coordinate system. A visual cutout does not automatically provide correct touch or mouse routing.
- The revealed control remains the real clickable control and performs its normal action once. Do not simulate the action from a copied tutorial button.
- Keep dismissal and necessary recovery controls available. Covered unrelated controls must not accidentally receive a click. During transition, never let the moving opening activate an unrelated control.
- If the target disappears or is disabled, suspend that hint. Do not render an orphan opening or force navigation to recover a tutorial step.
- Keep hints inside available window bounds and clear of navigation bars, system controls, and the keyboard. On small windows or large text, use a compact sheet or inline explanation if there is no usable space.

## Accessibility and interruptions

The spotlight is app UI. It must not require permission to draw over other apps or cover Android's camera/capture consent dialogs.

Use real semantics for the target and hint. Ensure a logical screen-reader order, keyboard operation on desktop, visible focus, and Back/Escape dismissal. Covered controls must not remain reachable as invisible distractions. Dismissal returns focus sensibly. A tutorial dismissal ends guidance only; stopping capture remains an explicit separate action.

Readability cannot depend on the scrim, colour, or animation alone. Do not auto-dismiss before someone finishes reading, auto-advance on a timer, or announce every animation frame. Errors take announcement priority over educational hints.

Respect the platform motion preference. USWDS demonstrates disabling nonessential tooltip transitions for reduced motion; this is a cross-platform principle, not an Android implementation recipe. Android and desktop behaviour must be checked separately when implemented. [USWDS tooltip guidance](https://designsystem.digital.gov/components/tooltip/).

## Human review when implemented

Check a normal fade, immediate dismissal, a rapid double tap, interruption during the fade, missing targets, a keyboard covering the target, large text, a resized desktop window, and disabled animations. The introduction should stay quiet enough to let someone use the app at their own pace.

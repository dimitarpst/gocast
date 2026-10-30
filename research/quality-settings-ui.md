# Quality settings screen — UI research (30 Oct 2026)

Short notes behind the UI-05 redesign of the phone's Quality screen.

## What the sources say

- **Android settings guidelines** ([source.android.com](https://source.android.com/docs/core/settings/settings-guidelines)):
  - Under a setting's title, show its current value, not a description.
  - Choose values from a radio list in a dialog, not a drop-down.
  - Group related settings under one heading.
  - Hide rarely used settings, and use "Advanced" only for 3+ items.
  - More than 10–15 items on one screen overwhelms.
  - Put explanations in a footer, not between rows.
- **Nielsen Norman Group, progressive disclosure** ([nngroup.com](https://www.nngroup.com/articles/progressive-disclosure/)): hiding advanced settings keeps beginners from mistakes and from weighing features they don't need.
- **Revolut visual language** (third-party reference, [design.hagicode.com](https://design.hagicode.com/designs/revolut/DESIGN.md)):
  - No shadows or outlines; colour and whitespace do the separating.
  - Cards have about 20 px corners; buttons are pills.
  - The layout uses an 8 px grid, with neutral grey for secondary text.
  - It is not an official Revolut source.
- **Grouped rounded lists** (Material 3 Expressive and WhatsApp's Android beta, [wabetainfo.com](https://wabetainfo.com/whatsapp-is-testing-a-new-settings-design-on-android/)): rows sit together in one rounded block instead of separate cards.
- **Defaults** (NN/g via [GitLab design issue](https://gitlab.com/gitlab-org/design.gitlab.com/issues/142)): a pre-selected or recommended option reads as an endorsement. That's why "Recommended" marks the Balanced default.

## Applied

- Profile is one rounded group, and the chosen profile gets a check mark.
- Auto-detect and Save as profile are pill buttons.
- All detailed settings sit behind a single "Detailed settings" row with an animated chevron.
- Inside, they fall into five headed groups: Picture, Bitrate, Streaming, Video format and Compatibility.
- Rows show name, then value, then ›, and open a radio-list dialog.
- The cost chips and ⓘ moved off the list and into each picker's dialog. Switch rows keep a small ⓘ.

Limits: none of this has been tested with users. The Revolut notes are second-hand.

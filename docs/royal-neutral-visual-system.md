# NeaRer Royal Neutral visual refresh

## Product direction
Blue remains NeaRer's signature accent, not a universal background. Use a calm graphite foundation, warm ivory content surfaces, clean white cards, and a restrained blue/gold accent system. Add teal for positive/connected states and terracotta for secondary game/community accents. Avoid full-screen saturated blue except for a purposeful brand moment.

## Palette tokens
- Graphite canvas: #111827
- Warm ivory surface: #F8F7F3
- Clean white: #FFFFFF
- Brand blue: #3478C8 (actions, selected states, links, discovery pulse)
- Deep blue: #285F9E (brand depth)
- Gold: #D9AE4B (premium highlight, not body text)
- Teal: #16877A (connected/success)
- Terracotta: #C17A52 (secondary accent)
- Primary ink: #202B3C
- Muted text: #667085
- Soft surface: #ECEAE4

## Screen identities
- Home: warm ivory and white surfaces; blue only on key actions and active navigation.
- Transfer: bright neutral file workspace; file-type accents and clear progress states.
- Nearby: graphite radar canvas with blue/teal live nodes, readable peer cards, and explicit radio readiness.
- Chats: light conversation canvas with strong message contrast and clear delivery labels.
- Communities: warm neutral cards with distinct group identity colours.
- Games: dedicated dark game-table treatment with rich board colours; never use the global blue page background as the board.
- Flash: neutral communication console with distinct cellular-signal and nearby-message paths.

## Typography and hierarchy
Use a consistent type scale: page title 26-30sp bold, section title 16-18sp semibold, body 14-16sp regular, metadata 11-12sp medium. Avoid all-caps for long explanatory copy. Keep muted text readable and reserve gold for short labels or key highlights.

## Motion and live states
Use short, purposeful transitions (150-250ms), shimmer/pulse only for actual active work, and never imply a peer is connected unless transport confirms it. Empty states should explain the next useful action; queued/offline work should remain visible and clearly labelled.

## Logo and launch
The approved NeaRer mark must be the designed N with four distinct luminous points around it. Do not substitute a generic glyph or dice mark. Animate the four lights in sequence around the N, then settle into a steady low-intensity glow. Keep the system splash animation short (around 1 second) and follow Android's SplashScreen API constraints.

## Implementation references
- Material 3 brand theming and semantic color roles: https://developer.android.com/codelabs/m3-design-theming
- Android color roles: https://developer.android.google.cn/design/ui/mobile/guides/styles/color
- Android SplashScreen animation requirements: https://developer.android.com/develop/ui/views/launch/splash-screen
- Apple HIG foundations for color, layout and typography: https://developer.apple.com/design/human-interface-guidelines/
- Nielsen Norman Group visual hierarchy: https://www.nngroup.com/articles/visual-hierarchy-ux-definition/

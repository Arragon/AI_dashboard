# Subscription Tracker Design System

## Direction

**Quiet Ledger** is a practical financial utility language: calm, compact, and precise without looking institutional. It uses warm paper-like neutrals, a single juniper accent, thin separators, and restrained elevation. The interface should feel maintained by a product team, not assembled from generic dashboard cards.

## Principles

1. Put records and actions before decoration.
2. Use whitespace and dividers for grouping; use a raised surface only when it communicates hierarchy.
3. Keep one primary action per context and subordinate secondary or destructive actions.
4. Preserve amounts, dates, and status information as the strongest scanning anchors.
5. Support light and dark themes as separate calibrated palettes, never as simple inversion.

## Color roles

All application UI must consume semantic Material color roles rather than hard-coded screen colors.

| Role | Light | Dark | Purpose |
|---|---|---|---|
| Background | `#F4F2EC` | `#101512` | Main canvas |
| Surface | `#FCFAF6` | `#171D19` | Navigation, dialogs, raised groups |
| Surface variant | `#ECEAE3` | `#202822` | Selected/empty/pressed states |
| Primary | `#356B59` | `#8AC4AA` | Primary action and active navigation |
| Primary container | `#D8E9E0` | `#244A3C` | Selected navigation and quiet emphasis |
| Primary text | `#1B201D` | `#E7ECE8` | Main content |
| Secondary text | `#626A65` | `#AEB8B1` | Supporting content |
| Outline | `#D9DDD8` | `#354039` | Structural separators |
| Error | `#A44F43` | `#FFB4A8` | Destructive and error states |

Do not introduce purple/blue AI gradients, neon glows, pure black, or multiple competing accents.

## Typography

Use the Android system sans-serif for native rendering, language coverage, dynamic text sizing, and zero font-loading cost. The scale is 12 / 14 / 16 / 17 / 21 / 28sp. Headings use semibold weight with slightly tightened tracking; body content uses regular weight and generous line height. Amounts and dates use monospace/tabular presentation where alignment improves scanning.

## Spacing and shape

Use a 4dp base rhythm. Preferred spacing values are 4, 8, 12, 16, 20, 24, and 32dp. Page gutters are 20dp on phones; content remains centered and constrained on larger widths. Touch targets are at least 48dp for primary controls and 44dp for tertiary text actions.

Shape roles:

- 6dp: menus and compact inner elements
- 9dp: rows and small controls
- 14dp: buttons, fields, selected navigation
- 20dp: dialogs and primary surfaces
- 28dp: reserved for large sheets

## Components

- **Top bar:** quiet context header on the canvas, separated by a thin outline.
- **Bottom navigation:** four equally reachable destinations; active state uses a filled primary container and text label.
- **Buttons:** 48dp minimum height, one filled primary action per group, outlined secondary actions, text tertiary/destructive actions.
- **Rows:** normally unboxed and divider-separated; tappable rows use ripple plus a brief scale/background response.
- **Metrics:** grouped in one raised surface rather than separate cards; values use monospace type.
- **Empty states:** one low-contrast surface with direct explanation, not illustration-heavy placeholders.
- **Forms:** labels remain visible, fields are 56dp minimum height, and action rows preserve a clear save/cancel/destructive hierarchy.
- **Dialogs:** 20dp radius, strong surface isolation, bounded scrolling, and stable actions.

## Motion

Motion exists only for feedback, selection, and state continuity. Use 120ms press feedback and approximately 180ms selection transitions. Animate only color, opacity, and transform; never animate layout dimensions or position properties. Do not use perpetual decorative animation, blur-heavy effects, particles, parallax, or scroll listeners. Respect Android's animator duration scale and reduced-motion settings through Compose animation APIs.

## Responsive and accessibility

- Keep fixed navigation clear of system safe areas through Scaffold and platform bars.
- Allow action groups to wrap instead of shrinking controls below usable widths.
- Avoid horizontal page scrolling; horizontal filter strips are the only intentional exception.
- Preserve readable contrast at WCAG AA for text pairs in both themes.
- Never rely on color alone for status or selection; retain labels and semantic state.
- Keep long text wrappable and localizable; use ellipsis only for secondary one-line metadata.
- Use semantic controls and explicit descriptions for icon-only or document actions.

## Anti-patterns

Avoid card grids, oversized headings, decorative gradients, generic glassmorphism, excessive shadows, tiny controls, mixed icon families, unlabelled navigation, continuous motion, and one-off hard-coded colors inside screens.

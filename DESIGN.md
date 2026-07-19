---
name: ShopMate
description: A collaborative real-time shopping list that feels like a household object, not a dev tool.
colors:
  marigold: "oklch(0.79 0.155 75)"
  marigold-deep: "oklch(0.68 0.145 68)"
  marigold-tint: "oklch(0.93 0.07 85)"
  marigold-faint: "oklch(0.96 0.04 87)"
  honey-deep: "oklch(0.40 0.08 68)"
  ground: "oklch(0.98 0.005 85)"
  panel: "oklch(1 0 0)"
  line: "oklch(0.90 0.015 80)"
  ink: "oklch(0.27 0.025 65)"
  ink-soft: "oklch(0.47 0.03 65)"
  ink-mute: "oklch(0.55 0.035 70)"
  danger: "oklch(0.55 0.19 27)"
  danger-tint: "oklch(0.96 0.025 27)"
typography:
  display:
    fontFamily: "Nunito Sans Variable, Nunito Sans, Seravek, Avenir Next, system-ui, sans-serif"
    fontSize: "1.875rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  title:
    fontFamily: "Nunito Sans Variable, Nunito Sans, Seravek, Avenir Next, system-ui, sans-serif"
    fontSize: "1.1875rem"
    fontWeight: 600
    lineHeight: 1.37
  item:
    fontFamily: "Nunito Sans Variable, Nunito Sans, Seravek, Avenir Next, system-ui, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.41
  body:
    fontFamily: "Nunito Sans Variable, Nunito Sans, Seravek, Avenir Next, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.47
  label:
    fontFamily: "Nunito Sans Variable, Nunito Sans, Seravek, Avenir Next, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 600
    lineHeight: 1.54
rounded:
  xl: "0.75rem"
  2xl: "1rem"
  full: "9999px"
spacing:
  gap: "0.5rem"
  gutter: "1.25rem"
  section: "1rem"
  touch: "2.75rem"
components:
  button-primary:
    backgroundColor: "{colors.marigold}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
    padding: "10px 24px"
    height: "{spacing.touch}"
  button-primary-hover:
    backgroundColor: "{colors.marigold-deep}"
    textColor: "{colors.ink}"
  button-on-brand:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.panel}"
    rounded: "{rounded.full}"
    padding: "8px 20px"
    height: "{spacing.touch}"
  button-ghost:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink-soft}"
    rounded: "{rounded.full}"
    padding: "10px 16px"
    height: "{spacing.touch}"
  input-field:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.xl}"
    padding: "12px 16px"
  chip-quantity:
    backgroundColor: "{colors.marigold-faint}"
    textColor: "{colors.honey-deep}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: "2px 10px"
---

# Design System: ShopMate

## 1. Overview

**Creative North Star: "The Sunlit Fridge Door"**

ShopMate's visual system is the family grocery pad reborn as software: the marigold warmth of a morning kitchen, the immediacy of a pencil on paper, and the quiet dependability of a system app. The interface is friendly, warm, and familial — a household object that happens to be world-class engineering underneath. Color is committed, not decorative: one saturated marigold carries the login screen, every header band, the add-item dock, and the check affordances, giving the app an unmistakable identity at a glance, even one-handed under supermarket fluorescents.

The system explicitly rejects both of PRODUCT.md's anti-references: **dev-tool darkness** (no dark-terminal aesthetic — this app lives in bright kitchens and brighter stores) and **todo-app minimalism** (this is a *shopping* list with quantities, check-off rhythm, and a pad-like surface — not a generic gray task list). The previous default-Tailwind white+indigo system is retired wholesale.

Layout is a single centered column (`max-w-lg`, 32rem) on every screen with a 1.25rem gutter; responsive behavior is structural, not fluid — the column simply centers in wider viewports. Motion is responsive, never choreographed: a drawn checkmark (220ms), a strike that fades in, sheets that slide up 1rem — all easing on `cubic-bezier(0.22, 1, 0.36, 1)` (ease-out-quint family), all disabled under `prefers-reduced-motion`.

**Key Characteristics:**
- Committed color: marigold owns headers, the add dock, checks, and the entire login screen
- Light, bright, warm — designed for daylight and store lighting; no dark mode
- One warm humanist sans (Nunito Sans) across the whole UI, fixed ~1.125-ratio scale
- The list is a "pad": one continuous white panel with hairline ruled dividers
- Pill-shaped controls, circular checkboxes, ≥44px touch targets everywhere
- Responsive motion: tactile feedback and calm transitions, zero page choreography

## 2. Colors

A committed marigold palette on bright, chroma-free light surfaces — warmth lives in the brand color, never in tinted-cream backgrounds.

### Primary
- **Marigold** (`oklch(0.79 0.155 75)`): the brand. A genuinely saturated golden yellow-orange that fills header bands, the add-item dock, checked checkbox circles, and the whole login surface. Always carries dark ink text (≈7.9:1), never white.
- **Marigold Deep** (`oklch(0.68 0.145 68)`): hover/pressed state of marigold fills, the focus-ring color on light surfaces, and accent strokes inside empty-state glyphs.
- **Marigold Tint** (`oklch(0.93 0.07 85)`): selection washes and member-avatar dots. Visibly golden — a state color, never a body background.
- **Marigold Faint** (`oklch(0.96 0.04 87)`): quantity-chip fill only.
- **Honey Deep** (`oklch(0.40 0.08 68)`): secondary text on marigold surfaces (login tagline, footnotes) and chip text. A darker shade of the brand hue — never gray on marigold (4.5:1+ on marigold, 8:1+ on faint).

### Neutral
- **Ground** (`oklch(0.98 0.005 85)`): the app background. Effectively white with imperceptible chroma; lets marigold read as the brand.
- **Panel** (`oklch(1 0 0)`): pure white. Cards, the list pad, sheets, input fields.
- **Line** (`oklch(0.90 0.015 80)`): hairline borders, the pad's ruled dividers, skeleton bones. Decorative only — never a control's only affordance.
- **Ink** (`oklch(0.27 0.025 65)`): primary text, on-marigold text, and the fill of "on-brand" buttons. ≥12:1 on panel.
- **Ink Soft** (`oklch(0.47 0.03 65)`): secondary text (member counts, empty-state prose, dialog subtitles). ≥6:1 on panel.
- **Ink Mute** (`oklch(0.55 0.035 70)`): placeholders, checked-off item text, unchecked checkbox rings, idle delete icons. The floor: 4.5:1 on panel.
- **Danger** (`oklch(0.55 0.19 27)`) on **Danger Tint** (`oklch(0.96 0.025 27)`): error panels and delete-hover states. The only non-brand hue in the system.

### Named Rules
**The Committed Rule.** Marigold is not an accent — it owns real surface area on every screen (header + dock + checks; the login screen is fully drenched). If a grayscale screenshot looks like a generic white app, the color commitment has failed.

**The Actually-Marigold Rule.** Warmth is expressed through the saturated brand color, never by tinting neutrals toward cream/beige/sand. Ground stays at chroma ≤0.005; a warm-tinted near-white body background is the failure mode, not the goal.

**The Daylight Rule.** Every screen must hold up in a bright supermarket: WCAG AA text contrast everywhere, 3:1 minimum on control outlines (checkbox rings are ink-mute, not line), no low-contrast "elegance."

**The Dark-Honey Rule.** Text on marigold is ink or honey-deep — a darker shade of the same hue. Gray or white text on marigold is forbidden.

## 3. Typography

**UI Font:** Nunito Sans Variable (self-hosted via Fontsource; fallbacks Seravek, Avenir Next, system-ui)

**Character:** A warm, rounded humanist sans — friendly without being cute, legible at arm's length while walking. One family everywhere; hierarchy comes from weight (400/600/700) and a tight ~1.125 product scale, never from a second face.

### Hierarchy
- **Display** (700, 1.875rem/2.25rem, -0.025em): the login wordmark only.
- **Title** (600–700, 1.1875rem/1.625rem): screen headers ("ShopMate", list names) and dialog titles.
- **Item** (400, 1.0625rem/1.5rem): list-item names — the workhorse, sized for glanceability; also the add-item input.
- **Body** (400–600, 0.9375rem/1.375rem): buttons (600), empty-state prose, dialog copy. Prose blocks cap at ~32ch centered.
- **Label** (600, 0.8125rem/1.25rem): quantity chips, member counts, section labels ("In the cart · 2"), dialog subtitles.

### Named Rules
**The One Voice Rule.** One type family everywhere — headings, items, buttons, labels. No display font, ever.

## 4. Elevation

Flat by default. Depth is conveyed by color commitment (marigold bands vs. bright ground) and hairline `line` borders, not by shadows. Shadows appear only as a response to state or as genuine physical lift: the modal sheet (`shadow-xl`), the login pad-preview card (a soft honey-tinted `0 12px 32px -16px oklch(0.40 0.08 68 / 0.45)`), and a whisper of `shadow-sm` on the add-dock's white input pill and FAB. Panels at rest carry a 1px `line` border and no shadow.

### Named Rules
**The Flat-By-Default Rule.** Surfaces are flat at rest. If a resting card has a shadow, it's wrong; if a floating sheet lacks one, it's wrong.

## 5. Components

Everything interactive is a pill or a circle; everything content-bearing is a soft rectangle (0.75–1rem radius). Tactile and dependable: controls compress slightly when pressed (`scale(0.97)`, 120ms).

### Buttons
- **Shape:** fully rounded pills (9999px), min-height 44px, weight-600 body-size text.
- **Primary (on light):** marigold fill, ink text, 10px 24px padding. Hover: marigold-deep. Disabled: 50% opacity.
- **On-brand (on marigold):** ink fill, white text — the inverted twin used in header bands ("New list", "Share") and the login CTA. Hover: ink at 90%.
- **Ghost:** white fill, 1px line border, ink-soft text. The "Cancel" role. Hover: ground fill.
- **Focus:** 2px marigold-deep outline, offset 2px (ink outline on marigold surfaces).
- **Press:** `scale(0.97)` at 120ms ease-out-quint, all buttons.

### Chips
- **Quantity chip:** marigold-faint fill, honey-deep 600-weight label text, full-round, 2px 10px padding. On checked rows it desaturates to ground fill + ink-mute text.
- **Member dots:** 20px marigold-tint circles with honey-deep initials, overlapping -6px, 1px white ring.

### Cards / Containers
- **The Pad (signature):** the item list is one continuous white panel — 1rem radius, 1px line border, rows divided by hairline `line` rules like a paper pad. Never one-card-per-item. The checked-off section repeats the pad at 60% panel opacity under an "In the cart · n" label.
- **List cards (overview):** white, 1rem radius, 1px line border, 1.25rem padding. Hover: marigold-deep/50 border + marigold-faint/40 wash.
- **Sheets/dialogs:** white, 1rem radius, `shadow-xl`, 1.5rem padding; bottom sheet on mobile (slides up 1rem, 220ms), centered card ≥640px; backdrop ink/40 with 150ms fade; Escape and backdrop-click close.

### Inputs / Fields
- **Standard:** white fill, 1px line border, 0.75rem radius, 12px 16px padding, body size; placeholder ink-mute. Focus: 2px marigold-deep ring.
- **Add-item pill:** the dock variant — full-round, borderless white on marigold, item-size text, `shadow-sm`. Focus: 2px ink ring.
- **Inline edit:** borderless, transparent, 2px marigold-deep bottom border only — the pencil line.

### Checkbox (signature)
A 26px circle inside a 44px hit area. Unchecked: 2px ink-mute ring on white (hover: marigold-deep). Checked: marigold fill, ink checkmark that draws itself via stroke-dashoffset (220ms ease-out-quint); the item label's strike-through fades in via `text-decoration-color` (250ms) as the text drops to ink-mute.

### Navigation
- **Header band:** full-width marigold, sticky, content in the centered column; title-size 700 ink text; a 44px round back-chevron button on detail screens (hover: marigold-deep/25 wash); one on-brand action pill on the right.
- **Add dock:** full-width marigold bar fixed to the bottom with `env(safe-area-inset-bottom)` padding; white input pill + 44px ink circular FAB (marigold plus icon). Disabled FAB: ink/20 fill.

### States
- **Loading:** skeleton pads with pulsing line-colored bones — never content-area spinners (spinners only for full-screen auth waits, marigold-colored).
- **Empty:** a marigold-tint pad glyph, item-weight headline, ink-soft teaching copy, and (on the lists screen) an inline primary CTA.
- **Error:** danger text on danger-tint panel, 1rem radius, danger/25 border.

## 6. Do's and Don'ts

### Do:
- **Do** let marigold carry real surface area (The Committed Rule) — header bands, the add dock, check states, and the fully drenched login.
- **Do** make check-off feel like the pencil stroke on a paper grocery pad: drawn checkmark, fading strike, instant optimistic state.
- **Do** keep the calm of Apple Reminders: nothing to learn, nothing to distrust, remote edits appearing in place without ceremony.
- **Do** honor `prefers-reduced-motion` with instant alternatives for every animation, including the checkmark draw and sheet slide.
- **Do** size every touch target ≥44px (`min-h-touch`/`min-w-touch`) for one-handed, in-motion use.
- **Do** keep control outlines at ≥3:1 (checkbox rings are ink-mute, never line).

### Don't:
- **Don't** ship "dev-tool darkness" — no dark-terminal aesthetic, no dense engineer-chic panels (PRODUCT.md anti-reference).
- **Don't** ship "todo-app minimalism" — no generic gray task-list look; the pad, chips, and cart section are the shopping-list identity (PRODUCT.md anti-reference).
- **Don't** tint backgrounds toward cream/beige to fake warmth (The Actually-Marigold Rule) — ground stays at chroma ≤0.005.
- **Don't** put gray or white text on marigold (The Dark-Honey Rule) — use ink or honey-deep.
- **Don't** reintroduce the retired indigo (#4F46E5) or default-Tailwind grays.
- **Don't** render items as individual cards — the pad is one panel with ruled dividers; nested cards are always wrong.
- **Don't** use mascots, vegetable illustrations, or sticker-book decoration — warmth comes from color and interaction, not kitsch.
- **Don't** drop below WCAG AA text contrast anywhere (The Daylight Rule).
- **Don't** define a Tailwind color token without the `/ <alpha-value>` suffix — opacity modifiers silently break without it.

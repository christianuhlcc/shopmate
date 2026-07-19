<!-- SEED: re-run /impeccable document once the redesign is implemented, to capture the actual tokens and components. -->

---
name: ShopMate
description: A collaborative real-time shopping list that feels like a household object, not a dev tool.
---

# Design System: ShopMate

## 1. Overview

**Creative North Star: "The Sunlit Fridge Door"**

ShopMate's visual system is the family grocery pad reborn as software: the marigold warmth of a morning kitchen, the immediacy of a pencil on paper, and the quiet dependability of a system app. The interface is friendly, warm, and familial — a household object that happens to be world-class engineering underneath. Color is committed, not decorative: one saturated marigold/honey brand color carries a substantial share of the surface and gives the app an unmistakable identity at a glance, even one-handed under supermarket fluorescents.

This system explicitly rejects both of PRODUCT.md's anti-references: **dev-tool darkness** (no dark-terminal aesthetic — this app lives in bright kitchens and brighter stores) and **todo-app minimalism** (this is a *shopping* list with quantities, rhythm, and check-off satisfaction — not a generic gray task list). It equally rejects the previous visual system (default-Tailwind white + indigo), which is retired wholesale.

**Key Characteristics:**
- Committed color: marigold/honey carries 30–60% of the surface, not a 10% accent
- Light, bright, and warm — designed for daylight and store lighting
- One warm humanist sans across the whole UI
- Responsive motion: tactile feedback and calm transitions, zero choreography
- Reference feel: Apple Reminders' calm system-app trustworthiness × the physical satisfaction of a paper grocery pad

## 2. Colors

A committed marigold/honey palette on light surfaces — warmth lives in the brand color, not in tinted-cream backgrounds. Exact values `[to be resolved during implementation, in OKLCH]`.

### Primary
- **Marigold / Honey** `[to be resolved]`: the brand color. Golden yellow-orange, genuinely saturated. Carries headers, the add-item surface, check affordances, and selection — the identity of the app.

### Neutral
- **Bright ground** `[to be resolved]`: light, clean base surface (pure or near-pure, *not* warm cream); lets the marigold read as the brand.
- **Ink** `[to be resolved]`: near-black body text, ≥7:1 against the ground. On marigold fills, text color is decided by perceptual contrast at implementation (dark ink on pale marigold; white only if the fill lands mid-luminance).

### Named Rules
**The Committed Rule.** Marigold is not an accent — it carries 30–60% of key screens. If a screenshot in grayscale looks like a generic white app, the color commitment has failed.

**The Actually-Marigold Rule.** Warmth is expressed through the saturated brand color, never by tinting neutrals toward cream/beige/sand. A warm-tinted near-white background is the failure mode, not the goal.

**The Daylight Rule.** Every screen must hold up in a bright supermarket: WCAG AA minimum everywhere, no low-contrast "elegance."

## 3. Typography

**UI Font:** Warm humanist sans `[font to be chosen at implementation]`, single family in multiple weights (regular / medium / semibold).

**Character:** Friendly without being cute; legible at arm's length while walking. The pencil-on-paper immediacy comes from weight contrast and generous sizing, not from novelty fonts.

### Hierarchy
- Fixed rem scale with a tight product-register ratio (≈1.125–1.2 between steps) `[exact scale at implementation]`
- **Title** (semibold): list names, screen headers
- **Body** (regular/medium): item names — the workhorse; sized generously for glanceability
- **Label** (medium, smaller): quantities, metadata, member names

### Named Rules
**The One Voice Rule.** One type family everywhere — headings, items, buttons, labels. No display font, ever.

## 4. Elevation

Flat by default. Depth is conveyed by the second neutral layer (panels vs. ground) and by color commitment, not by shadows; a shadow may appear only as a response to state (a lifted dragged item, an open sheet). `[Exact shadow vocabulary, if any, at implementation.]`

## 6. Do's and Don'ts

### Do:
- **Do** let marigold carry real surface area (The Committed Rule) — headers, add-item bar, check states.
- **Do** make check-off feel like the pencil stroke on a paper grocery pad: instant, tactile, satisfying.
- **Do** keep the calm of Apple Reminders: nothing to learn, nothing to distrust, remote edits appearing without ceremony.
- **Do** honor `prefers-reduced-motion` with instant/crossfade alternatives for every animation.
- **Do** size touch targets for one-handed, in-motion use (≥44px).

### Don't:
- **Don't** ship "dev-tool darkness" — no dark-terminal aesthetic, no dense engineer-chic panels (PRODUCT.md anti-reference).
- **Don't** ship "todo-app minimalism" — no generic gray task-list look that erases the shopping-list identity (PRODUCT.md anti-reference).
- **Don't** tint backgrounds toward cream/beige to fake warmth (The Actually-Marigold Rule).
- **Don't** reintroduce the retired indigo (#4F46E5) or default-Tailwind grays.
- **Don't** use mascots, vegetable illustrations, or sticker-book decoration — warmth through color and interaction, not kitsch.
- **Don't** drop below WCAG AA contrast anywhere (The Daylight Rule).

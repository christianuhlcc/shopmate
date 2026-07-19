# Product

## Register

product

## Platform

web

## Users

A household — partner and family sharing grocery lists. The primary context is a phone in one hand in the supermarket: quick check-offs, quick additions, often on flaky store Wi-Fi. The secondary context is planning at home, sometimes with two people editing the same list at the same time. A secondary audience is people the author shows the project to: the app doubles as an engineering showcase, so it must hold up under inspection as well as daily use.

## Product Purpose

ShopMate is a collaborative, real-time shopping list. Several people edit the same list simultaneously — online or offline, on multiple devices — and every edit converges without conflicts or lost changes (per-field LWW CRDT, server-assigned timestamps, live SSE fan-out). Success is twofold and equal: the household genuinely prefers it over paper and other apps week after week, and the project reads as a craft showcase when someone inspects it.

## Positioning

Everyone edits at once — online or offline — and it always converges. No locks, no "someone else is editing", no lost edits. The CRDT guarantee is the product promise, and every screen should make concurrent editing feel unremarkable.

## Brand Personality

Friendly, warm, familial. ShopMate is a household object, not a tool — it belongs on the fridge door, not in a dev toolbox. Warmth comes through tone, detail, and reliability rather than decoration. The interface stays calm and trustworthy; the engineering craft is felt as polish, never surfaced as jargon.

## Anti-references

- **Dev-tool darkness**: no dark-terminal aesthetic. This is a household app used one-handed in bright supermarkets.
- **Todo-app minimalism**: not a bare generic task list. It should feel like a *shopping* list — quantities, aisles-worth of items, check-off rhythm — not a Todoist clone.

## Design Principles

- **Merge without ceremony.** Concurrent edits are normal, not exceptional. Never show conflict dialogs, locks, or "refresh to see changes" — remote edits appear calmly in place, and the user's own edits are never blocked or lost.
- **One hand, one glance.** The store is the primary context: the next action (add, check off) is always reachable with a thumb, and the state of the list is readable at a glance while walking.
- **A household object, not a tool.** Warmth through tone, micro-detail, and dependability — not through mascots, illustrations, or decorative color.
- **Craft is felt, not shown.** The engineering quality expresses itself as UI polish, speed, and correctness under concurrency. No technical vocabulary ever reaches the interface.

## Accessibility & Inclusion

WCAG AA contrast throughout. `prefers-reduced-motion` respected for all animation. Fully keyboard-navigable. Generous touch targets sized for one-handed, in-motion use in a store.

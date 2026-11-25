---
name: "RYDA Design System"
description: "Premium motorcycle-tech design system engineered for Android-first ride tracking, telemetry, garage management, and rider community."
---

## Overview

RYDA is an Android-first platform exclusively built for motorcycle riders. The design language fuses high-performance motorsport telemetry aesthetics with modern, tactile mobile interfaces. The experience is unapologetically dark-first, reflecting nighttime asphalt, carbon composite textures, and the luminescent glow of digital instrument clusters.

Every screen embodies precision, clarity in outdoor riding conditions, rapid scannability, and high-satisfaction tactile micro-interactions. Social, club, and event features are thoughtfully integrated to enhance the ride rather than clutter the journey.

## Colors

The RYDA palette is dark-first, high-contrast, and focused. The core ground is Asphalt Black `#101114`, paired with mechanical cockpit tones `#191B20` and `#22252B`. The soul of the interface is Moto Orange `#FF6B35`, an energetic, high-visibility accent reminiscent of motorcycle tachometer redlines and hazard markers, paired with electric telemetry cyan `#00E5FF` for GPS tracking and live telemetry.

```yaml
colors:
  accent: "#FF6B35"
  telemetry: "#00E5FF"
  success: "#10B981"
  warning: "#F59E0B"
  error: "#EF4444"
  border: "#30333A"
  subtle-border: "#252830"
  light:
    primary: "#FF6B35"
    on-primary: "#323232"
    surface: "#F4F5F8"
    on-surface: "#101114"
    surface-container: "#EAECEF"
    on-surface-container: "#22252B"
  dark:
    primary: "#FF6B35"
    on-primary: "#323232"
    surface: "#101114"
    on-surface: "#F5F5F5"
    surface-container: "#191B20"
    on-surface-container: "#9A9DA5"
    surface-container-high: "#22252B"
    on-surface-container-high: "#F5F5F5"
```

## Typography

RYDA uses **Plus Jakarta Sans** for crisp, geometric headers and technical readouts, paired with **Instrument Sans** for neutral, legible UI body text. Telemetry statistics, speedometers, coordinates, and countdowns use **Source Code Pro** for rock-solid tabular alignment.

```yaml
typography:
  display-lg:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 40px
    fontWeight: 800
    lineHeight: 48px
    letterSpacing: -0.5px
  display-lg-mobile:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 32px
    fontWeight: 800
    lineHeight: 38px
    letterSpacing: -0.3px
  headline-xl:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 36px
    fontWeight: 700
    lineHeight: 44px
    letterSpacing: -0.4px
  headline-xl-mobile:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 28px
    fontWeight: 700
    lineHeight: 34px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 28px
    fontWeight: 700
    lineHeight: 36px
    letterSpacing: -0.2px
  headline-lg-mobile:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 24px
    fontWeight: 700
    lineHeight: 32px
    letterSpacing: -0.2px
  headline-md:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 20px
    fontWeight: 600
    lineHeight: 28px
    letterSpacing: -0.1px
  headline-sm:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 16px
    fontWeight: 600
    lineHeight: 24px
    letterSpacing: 0px
  body-lg:
    fontFamily: "Instrument Sans"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 24px
    letterSpacing: 0px
  body-md:
    fontFamily: "Instrument Sans"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
    letterSpacing: 0px
  body-sm:
    fontFamily: "Instrument Sans"
    fontSize: 12px
    fontWeight: 400
    lineHeight: 16px
    letterSpacing: 0.1px
  label-lg:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 14px
    fontWeight: 600
    lineHeight: 20px
    letterSpacing: 0.1px
  label-md:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 12px
    fontWeight: 600
    lineHeight: 16px
    letterSpacing: 0.2px
  label-sm:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 11px
    fontWeight: 600
    lineHeight: 14px
    letterSpacing: 0.4px
  stat-metric:
    fontFamily: "Source Code Pro"
    fontSize: 32px
    fontWeight: 700
    lineHeight: 36px
    letterSpacing: -0.5px
  stat-metric-mobile:
    fontFamily: "Source Code Pro"
    fontSize: 28px
    fontWeight: 700
    lineHeight: 32px
    letterSpacing: -0.4px
  stat-unit:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 12px
    fontWeight: 700
    lineHeight: 16px
    letterSpacing: 0.5px
```

## Layout & Spacing

RYDA follows an 8pt mobile grid (with 4pt half-steps for compact telemetry badges) tailored for Android devices (standard 390px viewport width). The primary bottom navigation uses an ergonomic 64px floating docked bar with a raised 60px circular central action for "Record Ride".

```yaml
spacing:
  gutter-xs: 4px
  gutter-sm: 8px
  gutter-md: 12px
  gutter-base: 16px
  gutter-lg: 20px
  gutter-xl: 24px
  section: 32px
  page-margin: 16px
  dock-height: 64px
```

## Elevation & Depth

Surfaces rely on subtle tonal step-ups (`#101114` -> `#191B20` -> `#22252B`) and precise 1px borders (`#30333A`). Floating control chips and bottom sheets use deep, diffuse obsidian drop-shadows with subtle orange back-glows on primary active states. Glassmorphism and blurry backdrops are strictly restrained to preserve outdoor legibility.

## Shapes

Shapes feature technical chamfers and modern, tight-radius rounded corners reminiscent of milled aluminum motorcycle parts.

```yaml
rounded:
  xs: 4px
  sm: 6px
  DEFAULT: 8px
  md: 12px
  lg: 16px
  xl: 20px
  pill: 9999px
  full: 9999px
```

## Motion & Micro-interaction

Motion in RYDA feels tight, mechanical, and athletic. Rapid touch responses (120ms spring compressions on tap) provide tangible confirmation when riders interact with buttons while stopped. Screen transitions follow Android predictive back and shared-element expansions (280ms cubic-bezier).

```yaml
motion:
  tap-press:
    duration: 120ms
    delay: 0ms
    timingFunction: cubic-bezier(0.2, 0.8, 0.4, 1)
  control-expand:
    duration: 200ms
    delay: 0ms
    timingFunction: cubic-bezier(0.0, 0.0, 0.2, 1)
  sheet-slide:
    duration: 300ms
    delay: 0ms
    timingFunction: cubic-bezier(0.2, 0.9, 0.3, 1)
  badge-unlock:
    duration: 600ms
    delay: 50ms
    timingFunction: cubic-bezier(0.34, 1.56, 0.64, 1)
```

## Voice & Content Strategy

The voice is authentic, knowledgeable, and respectful of the road. It speaks rider-to-rider without condescension, corporate jargon, or reckless encouragement.

- **Tone:** Focused, athletic, technical, community-grounded.
- **Capitalization:** Title case for screen headers, sentence case for descriptions and alerts.
- **Terminology:** "Ride", "Motorcycle", "Garage", "Pillion", "Ride Leader", "Odometer", "GPX". Never generic "Trip", "Vehicle", or "Car".
- **Action Labels:** Concrete action verbs: "Start Ride", "Publish Ride", "Join Club", "RSVP Going", "Add to Garage".

## Components

Component implementations live as individual HTML sticker sheet files in
`components/`. Each file contains the canonical, styled HTML components with
`<!-- COMPONENT: name -->` comment markers. Read each file before use.

- **Badge** → `@components/badge.html`: `badge-numeric`, `badge-dot`
- **Button** → `@components/button.html`: `button-primary`, `button-icon`, `button-with-icon`
- **Card** → `@components/card.html`: `card-elevated`, `card-action`, `card-interactive`, `card-interactive-2`
- **Searchbar** → `@components/searchbar.html`: `searchbar-standard`, `searchbar-with-suggestions`
- **Tabs** → `@components/tabs.html`: `tabs-horizontal`, `tabs-with-icon`

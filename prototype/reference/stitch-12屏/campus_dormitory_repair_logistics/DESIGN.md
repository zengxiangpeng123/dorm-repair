---
name: Campus Dormitory Repair & Logistics
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#434655'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#737686'
  outline-variant: '#c3c6d7'
  surface-tint: '#0053db'
  primary: '#004ac6'
  on-primary: '#ffffff'
  primary-container: '#2563eb'
  on-primary-container: '#eeefff'
  inverse-primary: '#b4c5ff'
  secondary: '#00668a'
  on-secondary: '#ffffff'
  secondary-container: '#40c2fd'
  on-secondary-container: '#004d6a'
  tertiary: '#006242'
  on-tertiary: '#ffffff'
  tertiary-container: '#007d55'
  on-tertiary-container: '#bdffdb'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dbe1ff'
  primary-fixed-dim: '#b4c5ff'
  on-primary-fixed: '#00174b'
  on-primary-fixed-variant: '#003ea8'
  secondary-fixed: '#c4e7ff'
  secondary-fixed-dim: '#7bd0ff'
  on-secondary-fixed: '#001e2c'
  on-secondary-fixed-variant: '#004c69'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
  tabular-stat:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: '0'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-compact: 0.75rem
  margin: 1rem
  margin-compact: 0.75rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system establishes a clean, reassuring, and responsive mobile-first experience for campus residents and maintenance staff. The core design language merges modern utility-driven minimalism with tactile warmth, transforming bureaucratic dormitory repairs into a frictionless, dependable service.

### Personality & Tone
- **Reassuring & Transparent:** Provides clear real-time visibility into repair lifecycles, dispelling uncertainty for anxious students dealing with water leaks, electrical faults, or lockouts.
- **Approachable & Modern:** Balances collegiate technological efficiency with friendly, supportive visual cues.
- **Action-Oriented:** Eliminates cognitive overhead through direct, multimodal issue reporting (voice descriptions, photo captures, quick-select issue tags).

### Visual Movement
The interface employs a hybrid of **Clean Software Minimalism** and **Soft Tonal Elevation**. High-contrast crisp typography pairs with generous whitespace, pristine white card surfaces, soft pill badges, and delicately diffused elevation to create an orderly, calming environment optimized for hand-held usage.

## Colors

The color system centers around trust and service clarity, anchored by deep university tech blue, balanced with airy accents, and complemented by a clear semantic feedback matrix.

### Core Palette
- **Primary (`#2563EB`):** High-clarity collegiate blue. Applied to primary calls-to-action, active bottom navigation states, key step indicators, and primary visual anchors.
- **Secondary (`#38BDF8`):** Sky blue accent. Used for interactive highlights, secondary button backgrounds, contextual informational callouts, and supportive illustrations.
- **Tertiary (`#10B981`):** Jade green. Designated for completed service tickets, validated inputs, normal appliance operations, and positive confirmation states.
- **Neutral (`#64748B`):** Cool slate gray. Governs supportive text, subtle icons, inactive borders, and structural framing.

### Semantic & Feedback Colors
- **Urgent / Emergency (`#EF4444`):** Soft coral-rose for critical hazards (pipe bursts, sparks, lockouts), overdue repairs, and destructive actions.
- **In-Progress / Attention (`#F59E0B`):** Warm amber for tickets awaiting dispatch, technician en-route markers, and pending approvals.

### Surfaces & Backgrounds
- **Canvas Base:** `#F8FAFC` to `#F1F5F9` creates a soft, low-glare backdrop that enhances card readability.
- **Elevated Surfaces:** Pure `#FFFFFF` surfaces with subtle `#E2E8F0` micro-borders isolate ticket information cleanly against the soft canvas.

## Typography

The typography uses Inter paired with system font fallbacks (such as PingFang SC) for Chinese characters. The hierarchy emphasizes fast scanning during urgent situations.

### Numeric & Data Legibility
Ticket numbers, room identifiers, appointment time slots, and queue positions must use tabular figures (`font-variant-numeric: tabular-nums;`) to prevent shifting layouts in countdowns and list views.

### Hierarchy & Style Rules
- **Display & Headlines:** Used for dashboard greetings, issue category titles, and modal headers. Bold, condensed letter spacing maintains compactness on narrow 360px–412px viewports.
- **Body:** Calibrated line heights prevent visual clutter in long issue descriptions, maintenance notes, and inspection remarks.
- **Labels & Badges:** Uppercase or slightly tracked medium-weight text reserved for status pills, floor/room tags, and action buttons.

## Layout & Spacing

The layout is built for mobile portrait viewports (375px to 414px width, baseline 390px). It adheres to a strict 4-column fluid mobile grid anchored by safe margins and an 8pt vertical spacing cadence.

### Layout Mechanics
- **Canvas Margins:** Fixed `1rem` (16px) outer gutter on standard phones, tightening to `0.75rem` (12px) on devices narrower than 360px.
- **Columns:** 4-column layout with a standard `1rem` column gutter, ideal for split-action items (e.g., "Take Photo" vs "Record Audio").
- **Vertical Flow:** Stacked layout containers rely on `space-md` (16px) separation between related cards and `space-xl` (32px) between major functional zones.
- **Bottom Navigation Clearance:** All views reserve an unblocked bottom padding clearance of `5.5rem` (88px) to accommodate the floating navigation dock and safe-area insets.

## Elevation & Depth

Visual hierarchy uses delicate tonal differentiation and multi-stop ambient shadows rather than stark heavy dropshadows.

### Layering Model
1. **Level 0 (Canvas Base):** Flat `#F8FAFC`, non-elevated background surface.
2. **Level 1 (Cards & Modules):** Pure white `#FFFFFF` surface with a crisp `1px solid #E2E8F0` border and an ambient shadow:
   `box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04), 0 6px 16px -4px rgba(15, 23, 42, 0.06);`
3. **Level 2 (Interactive Floating Actions & Menus):** Modal dialogs, bottom sheets, and floating action prompts:
   `box-shadow: 0 8px 24px -4px rgba(37, 99, 235, 0.12), 0 4px 12px rgba(15, 23, 42, 0.08);`
4. **Level 3 (Floating Navigation Dock):** Detached navigation bar elevated above view content with a frosted backdrop filter (`backdrop-filter: blur(16px); background: rgba(255, 255, 255, 0.88);`) combined with:
   `box-shadow: 0 12px 32px -4px rgba(15, 23, 42, 0.12), 0 2px 8px rgba(15, 23, 42, 0.04);`

## Shapes

The interface embraces a friendly, organic rounded geometry that softens mechanical maintenance tasks.

### Curvature Tokens
- **Core Cards & Containers:** Standard cards use `1rem` (16px) to `1.25rem` (20px) radius (`rounded-lg`). Hero action cards use `1.5rem` (24px) (`rounded-xl`).
- **Input Fields & Media Previews:** Medium radius of `0.75rem` (12px) for form textareas, photo capture previews, and technician profiles.
- **Status Pills & Action Chips:** Full pill radius (`9999px`) for category filters, status chips, urgency markers, and audio recording triggers.
- **Buttons:** Primary interactive targets leverage pill or `0.75rem` rounded corners to ensure generous, thumb-friendly touch targets.

## Components

### 1. Primary Action Triggers (Quick Repair Entry)
- **Voice & Camera Buttons:** Dual prominent action cards at the top of the repair reporting screen.
  - Audio trigger features a pulsing soundwave visual in `#38BDF8` with a soft-tint background (`#F0F9FF`).
  - Camera trigger uses a `#2563EB` solid or soft outlined block to open instant photo/video capture with room number watermark overlays.
- **Urgent Emergency Hotline Strip:** A high-visibility rounded strip at the top of the dashboard colored in soft rose (`#FEF2F2`) with red text (`#EF4444`) for electrical sparks, active pipe bursts, or room lockouts.

### 2. Repair Timeline & Progress Tracker
- **Vertical Step Sequence:** Connected by a continuous 2px track.
  - **Completed Stages:** Solid `#10B981` node with a check icon and matching connecting line.
  - **Active Stage:** Pulsing double-ring node in `#2563EB` with bold timestamp and technician assignment card.
  - **Pending Stages:** Hollow `#CBD5E1` node with muted label text.
- Integrated technician card nested in the active step includes an avatar, call trigger, and real-time arrival estimation.

### 3. Status Badges & Category Chips
- Badges use a light tinted background combined with an opaque border and high-contrast text:
  - *Completed / Checked:* Background `#ECFDF5`, text `#047857`, border `#A7F3D0`.
  - *In Progress / Dispatched:* Background `#FFFBEB`, text `#B45309`, border `#FDE68A`.
  - *Urgent / Pending Review:* Background `#FEF2F2`, text `#B91C1C`, border `#FECACA`.
  - *Filter Category Chips:* Inactive chips use `#F1F5F9` background with `#475569` text; selected state transitions to `#2563EB` with pure white text.

### 4. Interactive Cards
- **Ticket Summary Card:** Pure white surface with a 16px corner radius, containing room badge (e.g., `Building 7 - 402`), issue summary, thumbnail photo, creation timestamp, and state pill badge.
- Active tap states produce a micro-scale transition (`transform: scale(0.985);`) with soft border highlight.

### 5. Input Fields & Form Controls
- **Text Inputs & Textareas:** `#FFFFFF` background with `1px solid #E2E8F0` border, `12px` roundedness. Focus state introduces a 2px outer glow (`box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15)`) and `#2563EB` border.
- **Checkboxes & Radios:** Circle-rounded selection controls, transitioning to solid `#2563EB` with crisp white inner glyphs when selected.

### 6. Floating Navigation Bar
- Positioned `16px` above the bottom screen edge, spanning full width minus 32px padding margin.
- Contains 3 to 4 key sections: "Home/Status", "Submit Repair" (accentuated center elevated button), "My Tickets", and "Service Profile".
- Features frosted glass rendering (`rgba(255, 255, 255, 0.92)`) with a clean horizontal separation line and icon-plus-label layout.
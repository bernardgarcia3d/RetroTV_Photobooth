# Design System: Fotoautomat 1994 CRT

## 1. Visual Theme & Atmosphere

A tactile 1990s photo-booth control console inspired by the reference storefront: cream plaster, painted green booth panels, a deep red curtain, mint accessories, printed signage, and slightly weathered consumer electronics. VHS packaging, arcade controls, disposable cameras, cassette labels, and thermal receipts shape the component language. The density is balanced and operational, with clear zones for the camera, branded title, payment activation, and operator access.

## 2. Color Palette & Roles

- **Booth Cream** (`#F4ECE2`) — primary background sampled from the plaster and booth casing.
- **Thermal Paper** (`#FFFBF4`) — cards, QR surfaces, receipts, and high-contrast content.
- **Hardware Sand** (`#DED4C6`) — bezels, borders, recessed controls, and printed-label backing.
- **Painted Booth Green** (`#485A49`) — primary controls, logo marks, machine chrome, and confirmed states.
- **Muted Panel Green** (`#6B7C6B`) — secondary labels and quieter machine details.
- **Curtain Red** (`#913A32`) — primary CTA and attention state, derived from the reference curtain.
- **Mint Accessory** (`#A8D8AE`) — success, accessibility-enabled state, and gentle highlight.
- **Electric Cyan** (`#31BFC2`) — pose/camera accent used sparingly.
- **Tape Magenta** (`#C33E73`) — styling/edit accent and subtle VHS channel bleed.
- **Cabinet Purple** (`#71518F`) — collectible/sticker accents only.
- **Arcade Yellow** (`#D6A62F`) — warning badges, paper status, and machine indicators.
- **Lime Signal** (`#A7C94B`) — ready/charged status accents only.
- **CRT Charcoal** (`#20231F`) — text and deep hardware contrast; never use pure black for UI surfaces.

Cream, green, and curtain red carry the interface. Cyan, magenta, purple, yellow, and lime behave like small stickers or LEDs—never large background fills. Avoid cool gray drift and neon glow around body text.

## 3. Typography Rules

- **Display:** Space Grotesk, bold, uppercase, controlled tracking for booth titles and action labels.
- **Body:** Work Sans, relaxed leading, short readable lines for instructions.
- **Machine labels:** Space Mono, uppercase, letter-spaced, used for station metadata, status, and timestamps.
- **Hierarchy:** Prefer weight, color, and recessed surfaces over oversized text. Keep titles customizable through operator settings.
- **Minimum interaction text:** 14sp-equivalent on tablet controls; metadata may use 11–13sp when non-interactive.

## 4. Component Stylings

- **Primary buttons:** Oxblood or forest fill, white text, minimum 52dp height, tactile pressed translation, no glow.
- **Secondary buttons:** Thermal-paper fill with hardware-sand border and a clear label.
- **Cards:** Warm white/thermal-paper fill, 1dp sand border, restrained elevation. Use elevation to separate a stage or QR surface, not as decoration.
- **Control sizing:** Back and utility controls are at least 44dp; primary and form-completion actions are 52dp. QR tiles, camera windows, and printed-strip stages use fixed dimensions when their role is visual rather than flexible.
- **Focus and selection:** Use Electric Cyan for the keyboard focus ring and the current border/template selection. Mint denotes an enabled or completed state; it does not replace the primary action color.
- **Status pills:** Compact recessed labels with a colored dot or short machine prefix (`SYS OK`, `CRT: ON`, `WAITING FOR PAYMENT`).
- **Logo mark:** Operator-configurable, rendered as a compact forest enamel badge while the venue and title remain editable configuration values.
- **QR surface:** High-contrast thermal-paper card with generous quiet space, price, and instructions adjacent to it.
- **Camera layer:** Full-screen camera remains the landing surface. Keep branding and activation controls in clear, non-overlapping zones above the preview.

## 5. Layout Principles

- Use a full-height, edge-to-edge kiosk canvas with safe touch padding.
- Landscape tablets use an adaptive two-zone composition for title/payment content; portrait tablets collapse into a vertically scrollable single column.
- Operator pages use a centered, bounded reading column (760–1120dp depending on complexity) rather than stretching controls across the entire tablet.
- Long catalog rows may scroll horizontally inside their own rail; no guest or operator page may rely on horizontal page scrolling.
- Keep all controls at least 44dp touch size and avoid horizontal scrolling.
- Keep the title and payment layouts visually asymmetric when space allows: content on the left, QR/status surface on the right.
- Preserve the camera-first attract experience and route activation through the branded title page before QR payment.
- Do not restore the removed post-strip rail to the landing page.

## 6. Motion & Interaction

- Keep motion restrained and hardware-like: LED/status pulse, tactile button press, and camera preview texture only. Never animate more than two decorative elements in one view.
- Animate opacity and transforms rather than layout bounds.
- **Landing intensity (operator default 82%)**: scanlines, subtle curvature/vignette, sparse grain, gentle flicker, and restrained cyan/magenta channel separation.
- **Payment and capture (15–25%)**: static scanline/printed texture only; never distort QR codes, faces, totals, countdowns, or controls.
- **Editing and confirmation (0–15%)**: cream paper texture and machine labels; no flicker and no channel shift.
- **Reduced motion:** disables CRT flicker and any perpetual decorative motion while preserving state information.
- **Reduced glitch:** minimizes grain and RGB separation while preserving contrast, color hierarchy, and tactile styling.

## 7. Responsive Rules

- Landscape tablet: use the available width for side-by-side content and QR card, with bounded content widths.
- Portrait tablet: stack content, QR, and actions; allow vertical scrolling.
- Keep operator-configured title, venue, and logo visible at every stage without truncating the primary action.
- Never allow the QR card or CTA to be clipped by system bars or camera cutouts.

## 8. Anti-Patterns (Banned)

- No generic Inter typography, generic serif fonts, or pure black UI surfaces.
- No neon gradients, purple glows, excessive decorative effects, or fake statistics.
- No overlapping text and controls, tiny touch targets, or horizontal overflow.
- No three equal generic cards when an asymmetric or compact feature treatment is clearer.
- No invented branding that overrides operator settings.
- No production claims for the demo QR; until a provider/backend is connected, label it as test/demo mode.
## Implemented full-screen booth direction

- The landing screen remains an edge-to-edge live camera experience.
- Session, payment, layout, capture, review, delivery, completion, and operator pages use full-screen compositions rather than a simulated television cabinet.
- Permanent decorative top and bottom rails are not part of the interface. Structure comes from spacing, typography, color blocks, and bounded content panels.
- Landscape is the primary tablet layout. Narrow and portrait displays stack the same content without changing the underlying workflow.
- The visual system uses the existing cream, sand, charcoal, forest, oxblood, mint, and cyan tokens. Existing components and all camera, QR, printing, and settings behavior remain authoritative.

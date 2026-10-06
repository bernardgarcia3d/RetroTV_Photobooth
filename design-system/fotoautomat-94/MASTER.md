# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** Fotoautomat 94
**Generated:** 2026-10-03 19:06:37
**Category:** Gaming

---

## Global Rules

### Color Palette

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Primary | `#485A49` | `--color-primary` |
| Secondary | `#913A32` | `--color-secondary` |
| CTA/Accent | `#913A32` | `--color-cta` |
| Background | `#F4ECE2` | `--color-background` |
| Text | `#20231F` | `--color-text` |

**Color Notes:** Reference-derived cream plaster, painted booth green, red curtain, mint success, and sparse cyan/magenta/yellow/lime arcade accents.

### Typography

- **Heading Font:** Space Grotesk
- **Body Font:** Work Sans
- **Machine Label Font:** Space Mono
- **Mood:** tactile 1990s consumer electronics, photo booth, arcade labels, readable CRT
- **Google Fonts:** Space Grotesk + Work Sans + Space Mono

**CSS Import:**
```css
@import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@600;700&family=Space+Mono:wght@400;700&family=Work+Sans:wght@400;500;600;700&display=swap');
```

### Spacing Variables

| Token | Value | Usage |
|-------|-------|-------|
| `--space-xs` | `4px` / `0.25rem` | Tight gaps |
| `--space-sm` | `8px` / `0.5rem` | Icon gaps, inline spacing |
| `--space-md` | `16px` / `1rem` | Standard padding |
| `--space-lg` | `24px` / `1.5rem` | Section padding |
| `--space-xl` | `32px` / `2rem` | Large gaps |
| `--space-2xl` | `48px` / `3rem` | Section margins |
| `--space-3xl` | `64px` / `4rem` | Hero padding |

### Shadow Depths

| Level | Value | Usage |
|-------|-------|-------|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | Subtle lift |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.1)` | Cards, buttons |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1)` | Modals, dropdowns |
| `--shadow-xl` | `0 20px 25px rgba(0,0,0,0.15)` | Hero images, featured cards |

---

## Component Specs

### Buttons

```css
/* Primary Button */
.btn-primary {
  background: #913A32;
  color: white;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}

.btn-primary:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

/* Secondary Button */
.btn-secondary {
  background: transparent;
  color: #485A49;
  border: 2px solid #485A49;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}
```

### Cards

```css
.card {
  background: #FFFBF4;
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-md);
  transition: all 200ms ease;
  cursor: pointer;
}

.card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}
```

### Inputs

```css
.input {
  padding: 12px 16px;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 200ms ease;
}

.input:focus {
  border-color: #485A49;
  outline: none;
  box-shadow: 0 0 0 3px #485A4930;
}
```

### Modals

```css
.modal-overlay {
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.modal {
  background: white;
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow-xl);
  max-width: 500px;
  width: 90%;
}
```

---

## Style Guidelines

**Style:** Retro-Futurism

**Keywords:** 1990s photo booth, CRT television, VHS packaging, arcade controls, disposable camera, thermal receipt, painted plastic, collectible labels

**Best For:** Gaming, entertainment, music platforms, tech brands, artistic projects, nostalgic, cyberpunk

**Key Effects:** Restrained CRT scanlines, subtle phosphor bloom, gentle vignette, sparse VHS noise, tactile bevels, printed labels, and accessibility-controlled flicker/glitch intensity

### Page Pattern

**Pattern Name:** Event/Conference Landing

- **Conversion Strategy:** Early bird pricing with deadline. Social proof (past attendees). Speaker credibility. Multi-ticket discounts.
- **CTA Placement:** Register CTA sticky + After speakers + Bottom
- **Section Order:** 1. Hero (date/location/countdown), 2. Speakers grid, 3. Agenda/schedule, 4. Sponsors, 5. Register CTA

---

## Anti-Patterns (Do NOT Use)

- ❌ Minimalist design
- ❌ Static assets

### Additional Forbidden Patterns

- ❌ **Emojis as icons** — Use SVG icons (Heroicons, Lucide, Simple Icons)
- ❌ **Missing cursor:pointer** — All clickable elements must have cursor:pointer
- ❌ **Layout-shifting hovers** — Avoid scale transforms that shift layout
- ❌ **Low contrast text** — Maintain 4.5:1 minimum contrast ratio
- ❌ **Instant state changes** — Always use transitions (150-300ms)
- ❌ **Invisible focus states** — Focus states must be visible for a11y

---

## Pre-Delivery Checklist

Before delivering any UI code, verify:

- [ ] No emojis used as icons (use SVG instead)
- [ ] All icons from consistent icon set (Heroicons/Lucide)
- [ ] `cursor-pointer` on all clickable elements
- [ ] Hover states with smooth transitions (150-300ms)
- [ ] Light mode: text contrast 4.5:1 minimum
- [ ] Focus states visible for keyboard navigation
- [ ] `prefers-reduced-motion` respected
- [ ] Responsive: 375px, 768px, 1024px, 1440px
- [ ] No content hidden behind fixed navbars
- [ ] No horizontal scroll on mobile

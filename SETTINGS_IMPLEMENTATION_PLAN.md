# Operator Settings Implementation Plan

## Purpose

This plan adapts the useful settings patterns observed in the Ambooth reference recording for Event Photobooth. It is a functional reference only: the app will use its own visual system, wording, and Android architecture.

The goal is an operator-only control area that makes a cafe, restaurant, rental crew, or event operator confident that the booth, printer, and guest flow are ready before guests use the tablet.

## Reference patterns worth adapting

The recording groups controls into five practical areas:

1. A setup checklist for printer, countdown, and receipt setup.
2. Guest-experience controls for the home screen and available layouts.
3. Capture-and-print controls for camera, timers, sounds, receipt design, printer width, and connectivity.
4. Security and display controls, including a PIN and keeping the tablet awake.
5. A QR delivery control that is only useful when a connected service has uploaded the media.

These categories match the product's local-first workflow. They must not let the app say that a printer or QR download is ready until it is actually ready.

## Proposed information architecture

The guest screen remains simple. All controls below are behind the operator PIN.

### 1. Readiness checklist

Show this first whenever an operator opens Settings.

- Camera permission: granted or needs attention.
- Camera test: front camera preview available.
- Printer: disconnected, permission needed, ready, or error.
- Paper/test print: not tested, passed, or failed.
- Booth layout: configured.
- Digital delivery: disabled, offline/pending, or ready.

Each row opens its correct control or recovery action. This is more useful than a generic settings list during setup at a cafe or event.

### 2. Booth and guest experience

- Event/venue name and start-screen message.
- Start-screen logo and background image.
- One selected strip layout in Release 1.
- A small selection of approved layouts later, not fifteen layouts at launch.
- Optional subtle start-screen animation later; no animation by default on lower-powered or battery-powered deployments.
- Guest-facing settings icon hidden by default after the operator PIN is created.

The existing visible gear can remain during development. For a live installation, use one deliberate operator entry gesture, such as a five-second press on an unobtrusive corner, followed by the PIN.

### 3. Capture experience

- Camera choice: front camera by default, with rear camera available only after it is tested on the reference tablet.
- Photo count: three photos for the first release; make the stored setting extensible to four photos later.
- Countdown: three seconds by default, configurable from three to five seconds.
- Photo progress: always display `Photo 1 of 3`, taken-photo markers, and the next-shot message during capture.
- Retake flow: restart the whole three-photo sequence for MVP. Per-photo replacement can wait until post-pilot evidence shows it is needed.
- Sound: an optional countdown beep and shutter sound, with a master mute setting.
- Guest idle timeout: return to the home screen after the result screen has been idle for a configurable period, initially 60 seconds.

## 4. Print and receipt controls

- Reference printer profile: XP-80T, USB, 80 mm paper, 576-dot printable width.
- Printer connection state, USB permission state, and last printer error.
- Operator-only `Test print` button.
- Print workflow: auto-print enabled by default; manual reprint is an operator action, not a guest action.
- Print queue: total queued, printing, failed, and successfully printed jobs; failed jobs must expose a retry action and the recorded error.
- One branded portrait strip template: logo/header, three photos, footer, and print-safe monochrome rendering.
- Do not use the XP-D4601B to tune the receipt layout. It is only a USB-discovery device because it is a 104 mm label printer.

The `57 mm / 80 mm` selector shown in the reference should not be a normal production setting. Launch with an 80 mm-only profile. A width selector becomes appropriate only after a second printer and layout are explicitly validated.

## 5. Security, display, and operations

- PIN-protected settings: already present in the current build.
- Keep screen awake: enabled whenever the booth is in guest mode; do not change the tablet's global system setting.
- Battery, charging, storage, network, and printer state displayed in an operator diagnostics panel where Android exposes the data.
- Event-level session count and successful/failed print count.
- `End event` and media cleanup are later protected operator actions, governed by the retention policy.

The reference's app-rating, social-follow, and generic personal usage-profile items are not product features for this app. Replace them with deployment-relevant diagnostics and event controls.

## 6. QR delivery and gallery

The reference shows a simple QR-download switch. Our behavior needs a stricter state model because the product promises private, temporary GIF delivery.

- `Disabled`: no upload attempted and no QR shown.
- `Pending`: source photos, print strip, and GIF are held locally for background upload; no QR shown as ready.
- `Ready`: private media upload and token creation succeeded; show one QR code for the matching animated GIF strip.
- `Unavailable`: expired, revoked, deleted, or a failed upload; show clear operator recovery guidance.

Do not add a guest-facing QR toggle until Release 2. The first QR must lead only to the correct GIF strip through a temporary signed URL; it must never reveal permanent object-storage URLs or source photos.

## Implementation architecture

Do not keep the expanding settings logic inside `EventPhotoboothApp.kt`. Split it before adding configuration screens.

```text
data/
  BoothSettings.kt              Persistent booth configuration
  BoothSettingsRepository.kt    Read/write and validation
  OperatorReadiness.kt          Derived camera/printer/delivery checks

settings/
  OperatorSettingsScreen.kt     Section list and navigation
  ReadinessChecklistScreen.kt   Actionable setup checks
  BoothSettingsScreen.kt        Home, capture, sound, timeout controls
  PrintSettingsScreen.kt        USB state, test print, queue and retry

printer/
  UsbPrinterManager.kt          USB discovery, permission and connection state
  Xp80tPrinterAdapter.kt        XP-80T raster/ESC-POS output only after hardware validation
```

Use one immutable `BoothSettings` model, for example:

```text
eventName, homeTitle, logoPath, backgroundPath,
photoCount, countdownSeconds, idleTimeoutSeconds, soundEnabled,
keepScreenAwake, autoPrint, printerProfile, activeLayoutId,
digitalDeliveryEnabled
```

Persist local booth settings separately from event branding snapshots. Editing today's operator settings must not silently change the historical configuration of a completed event.

## Delivery order

### Phase A — complete the local printing foundation

1. Implement USB discovery and permission handling with the XP-D4601B for discovery only.
2. Add the readiness checklist and printer-status panel.
3. Validate a test print, auto-print, error state, and retry loop on the XP-80T with a powered USB-C OTG hub.
4. Lock the production printer profile to 80 mm / 576 dots.

### Phase B — configurable, safe booth experience

1. Extract settings UI and repository from the current single UI file.
2. Add configurable countdown, sound, idle reset, and keep-awake controls.
3. Add start-screen branding inputs and one fixed receipt-template configuration.
4. Hide operator access from normal guest use once the operator PIN is configured.

### Phase C — secure digital delivery

1. Build the animated GIF renderer as a background task.
2. Add private upload, idempotent retry, session token generation, and signed GIF downloads.
3. Show QR only when the server confirms delivery readiness.
4. Add event-gallery settings, retention, deletion, and download revocation.

### Phase D — commercial operating modes

1. Add reusable organizer branding profiles and frozen event snapshots.
2. Add venue/rental/package deployment labels and print counters for reconciliation with the venue's own POS.
3. Consider voucher or paid-print controls only after a deliberate payment-flow decision; in-app payments remain outside the current MVP.

## Release 1 acceptance checks for settings

- An operator can enter settings with a PIN and see camera, printer, paper-test, and queue status.
- The app correctly reports when the printer is absent, awaiting USB permission, ready, or failed.
- A setting change is persisted across an app restart and affects the next guest session only where intended.
- The guest cannot accidentally open settings during normal use.
- A three-photo session with auto-print disabled creates one durable queued job; enabling auto-print sends one job through the validated printer adapter without duplication.
- The app does not present a QR as ready while offline or before the matching GIF upload is confirmed.

## Recommendations

- Copy the reference app's setup checklist, grouping, and plain-language status messages; these directly reduce operator mistakes.
- Keep the guest UI much simpler than the settings UI. A guest should only see Start, progress, review/retake, and completion.
- Build the XP-80T path before broadening printer support or building elaborate template controls.
- Treat the QR workflow as a privacy-sensitive delivery system, not just a checkbox.
- Use original branding and Compose components instead of copying the reference product's assets, layout, or copy.

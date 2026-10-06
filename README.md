# Event Photobooth Android App

Native Android tablet application for the Event Photobooth MVP described in [PRD.txt](PRD.txt).

The working hardware assumptions and the difference between the development label printer and the production receipt printer are recorded in [HARDWARE_PROFILE.md](HARDWARE_PROFILE.md).

## Implemented local MVP

- Compose guest-mode shell with an operator entry point
- Camera permission handling and CameraX front-camera preview
- Portrait-oriented 4:3 source capture with JPEG orientation normalization, so all layouts receive upright source images
- Local test-payment gate for the fixed configured price; it is clearly marked as a test and cannot be used as QRPH payment verification
- Layout selection for one-photo, three-photo strip, four-photo grid, and six-photo grid sessions
- Layout-driven countdown capture sequence with taken/current/next progress markers
- A complete branded output preview before print; guests edit the assembled strip/grid rather than individual source photos
- Operator-customizable layout names and visual accent presets while keeping validated one/three/four/six-photo geometry
- Approved stickers and emojis on the full output: horizontally scroll the tray, tap to add, tap to select, drag to move, pinch to resize, double-tap to duplicate, and Delete to remove; placements and scale persist with each session
- Guest photo-brightness slider with a print-oriented black-and-white preview; the selected brightness is applied to the printed strip and local GIF output
- Local app-private image storage, configuration snapshots, and persisted session metadata
- Local animated GIF-strip rendering and final digital-strip persistence for each completed session
- Completed digital strips and animated GIFs are also published to the tablet Gallery under `Pictures/RetroTV PhotoBooth`; Gallery copies remain until the operator removes them
- Configurable venue name, strip header/footer, fixed PHP price, and enabled layouts behind the operator PIN
- Branded receipt-strip bitmap rendering for all four layouts
- Durable local print-job queue and status model
- Printer-style animated processing screen that shows the assembled strip moving toward the printer while the print job is rendered/submitted; queue status remains authoritative
- USB device discovery, Android USB permission request, XP-D4601B diagnostic labels, scaled latest-strip proof prints, generic thermal print brightness and dither tuning, XP-80TS bottom-feed tuning, manual XP-80TS receipt/strip test printing, automatic XP-80TS print attempt after strip creation, visible print queue/retry/errors, and an operator printer-readiness screen
- Local/demo QR delivery screen after successful print submission; signed cloud access remains a Release 2/3 backend feature
- Clear boundary for Release 2: verified QRPH confirmation, private upload, and signed access are not enabled until a backend and payment provider are selected

## Verification status

The debug APK builds after the local-MVP flow update on 2026-10-02. The earlier Pixel Tablet emulator smoke test confirmed camera permission, three-photo capture, local source JPEG storage, strip rendering, and durable print queuing. The expanded layout, sticker/emoji tray, layout-design settings, brightness preview, animated processing, USB discovery, and USB permission paths compile successfully but still need a full device walkthrough on the Samsung tablet.

The Samsung tablet has detected one connected USB device with the XP-D4601B development printer path. Android USB permission and a TSPL diagnostic label print have been validated on the XP-D4601B. The XP-80TS ESC/POS receipt test has also printed successfully. The current build attempts XP-80TS strip printing automatically after strip creation when exactly one permitted USB printer is connected; otherwise the durable print job remains queued for operator retry. Operator settings now show queued, printing, printed, and failed jobs with retry and last-error guidance. The latest debug build on 2026-09-30 compiles with local GIF generation, local/demo QR delivery, generic brightness/dither controls, and bottom-feed tuning.

## Run locally

1. Install the current Android Studio and Android SDK, including API 37.
2. Open this folder in Android Studio and allow Gradle to sync.
3. Run on the selected Android 10 or newer tablet.
4. Grant camera permission.
5. Use the XP-D4601B only for USB discovery, permission, diagnostic label, and scaled strip-proof testing. Complete receipt-strip printing against a USB-equipped XP-80T.

## Fallback release

The current baseline is preserved as RetroTV PhotoBooth `v1.0.0`.

- Source fallback: local Git tag `v1.0.0` and branch `fallback/v1.0.0`
- Reinstallable APK: `releases/v1.0.0/RetroTV-PhotoBooth-v1.0.0.apk`
- Android version name: `1.0.0`
- Android version code: `1`

Future work should continue on `master` (or a feature branch) without moving the fallback tag. To reinstall the fallback over a newer APK, Android may require the installer's downgrade option because the fallback has the older version code.

## Production boundary

The app deliberately does not create a live payment request, verify a QRPH payment, upload media, issue signed URLs, or claim cloud delivery is available. The displayed payment and delivery QR tiles are local MVP test aids only. Live payment requires a provider-neutral backend webhook/status flow; production digital delivery requires the private storage and signed-token backend defined in the PRD.

# Hardware Profile

## Development equipment

### Tablet

- Intended reference device: Samsung Galaxy Tab S9
- Status: model number and Android version must be confirmed from Settings, About tablet
- Expected connection: USB-C

The Galaxy Tab S9 specification lists USB 3.2 Gen 1 and a 12 MP front camera. It is a suitable candidate for the tablet reference device once USB host behavior is verified with the selected hub.

### Development printer

- Model: Xprinter XP-D4601B
- Role: development and USB discovery testing only
- Print type: 4-inch direct-thermal label printer
- Resolution: 203 DPI
- Maximum print width: 104 mm
- Standard interface: USB
- Command languages: TSPL, ZPL, EPL, and DPL

The XP-D4601B is not a receipt printer and must not be treated as the production receipt-strip reference. Its 104 mm label format and label-printer command languages differ from the intended 80 mm receipt workflow.

## Production target

### Receipt printer

- Intended model: Xprinter XP-80T / XP-80TS
- Required variant: USB included; USB plus LAN is preferred for fallback operations
- Paper: 79.5 mm roll, 80 mm class
- Printable width: 72 mm
- Resolution: 203 DPI
- Target strip width: 576 dots
- Cutter: partial cutter

The XP-80T / XP-80TS product family has several interface variants. Confirm the purchased unit includes USB before integrating it with the Android tablet. The product must use the actual interface set printed on the device label; do not assume Bluetooth, Wi-Fi, or LAN is present.

## Connection plan

1. Connect the Galaxy Tab S9 to a powered USB-C OTG hub with power delivery.
2. Connect the printer to the hub with its own supplied power adapter.
3. Keep the tablet charger connected to the hub during events.
4. Verify Android detects the USB printer before app-level printing work begins.
5. For the XP-D4601B, validate USB detection only. Do not use it to tune the 80 mm receipt-strip layout.
6. For the XP-80T / XP-80TS, validate ESC/POS receipt diagnostics first, then 576-dot receipt rendering, one/three/four/six-photo strip output, brightness, dither threshold, paper feed, cutter behavior, retry behavior, and sustained printing.

## Required physical checks

- Tablet model number and Android version
- USB-C OTG hub supports both data and charging
- XP-80T / XP-80TS exact interface SKU
- Receipt paper quality and 80 mm roll fit
- Brightness, dither threshold, and bottom-feed settings for the chosen paper roll
- Printer power adapter remains separate from tablet power
- Camera framing, tablet mounting, and soft event lighting

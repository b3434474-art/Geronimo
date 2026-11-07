# Geronimo architecture

## Components

1. **Windows desktop assistant** — preserves the original full-screen navy-and-blue assistant and adds a phone connection strip, call popup, outgoing dial panel and trusted-contact manager.
2. **Android companion** — registers an Android `InCallService` when the user grants the default-phone-app role. A foreground connected-device service keeps the BLE phone link available while the app is backgrounded.
3. **Bluetooth LE transport** — a GATT service carries JSON call commands from Windows to Android and fragmented JSON state notifications from Android to Windows. It carries no live voice audio.

## Call policy

| Event | Behavior |
| --- | --- |
| Incoming call | Show a call prompt. Never auto-answer. |
| Answer | Only after a deliberate user action on the phone or Windows popup. |
| Decline | Only after a deliberate user action. |
| Hang up | Only after a deliberate user action. |
| Outgoing number in phone-side allowlist | May call directly only if Android's CALL_PHONE permission is also granted. |
| Other outgoing number | Open Android's dialer so the user confirms and places the call. |
| Lost connection | Windows cannot send further call commands; reconnect before controls are used. |

The allowlist is stored locally on Android, synced from the user's Windows contact manager, and applies only to outgoing calls. It cannot change incoming-call behavior.

## Current state

- [x] Preserve the supplied original desktop assistant source and design.
- [x] Add Windows phone connection UI and call popup.
- [x] Implement bidirectional BLE GATT commands and notifications.
- [x] Add Android call-state integration and caller-name lookup (permission-gated).
- [x] Add Android foreground BLE phone-link service.
- [x] Add phone-side trusted outgoing-number list.
- [x] Add custom incoming/outgoing MP3 ringtones.
- [x] Configure GitHub Actions for Android APK and Windows executable builds.
- [ ] Test the full phone/PC call flow on the actual devices.
- [ ] Verify call controls, background reconnects and call events on the user's Android version.
- [ ] Establish a supported phone-to-PC two-way hands-free audio route.

## Audio limitation

BLE pairing and notification delivery are not a voice channel. Windows 10's built-in Bluetooth HFP role is not the required phone-facing hands-free endpoint for routing cellular audio to the laptop. Call audio remains unavailable in this build.

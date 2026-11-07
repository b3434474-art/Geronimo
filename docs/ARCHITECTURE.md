# Geronimo architecture

## Components

1. **Windows desktop assistant** — preserves the original full-screen navy-and-blue assistant and adds a phone connection strip, call popup, outgoing dial panel and trusted-contact manager.
2. **Android companion** — registers an Android `InCallService` when the user grants the default-phone-app role. A foreground connected-device service keeps the BLE phone link available while the app is backgrounded.
3. **Bluetooth LE transport** — a GATT service accepts only bonded Bluetooth devices and carries JSON call commands from Windows to Android plus fragmented JSON state notifications from Android to Windows. It carries no live voice audio.

## Call policy

| Event | Behavior |
| --- | --- |
| Incoming call | Show a call prompt. Never auto-answer. |
| Answer | Only after a deliberate user action on the phone or Windows popup. |
| Decline | Only after a deliberate user action. |
| Hang up | Only after a deliberate user action. |
| Outgoing number in phone-side allowlist | May call directly only if Android's CALL_PHONE permission is also granted. |
| Other outgoing number | Open Android's dialer so the user confirms and places the call. |
| Lost or untrusted connection | The phone rejects commands from unbonded devices. |

The allowlist is stored locally on Android, synced from the user's Windows contact manager, and applies only to outgoing calls. It cannot change incoming-call behavior.

## Current state

- [x] Preserve the supplied original desktop assistant source and design.
- [x] Add Windows phone connection UI and call popup.
- [x] Implement bidirectional BLE GATT commands and notifications.
- [x] Require Bluetooth bonding before the phone accepts call commands.
- [x] Add Android call-state integration and caller-name lookup (permission-gated).
- [x] Add Android foreground BLE phone-link service.
- [x] Add phone-side trusted outgoing-number list.
- [x] Add custom incoming/outgoing MP3 ringtones.
- [x] Configure GitHub Actions for Android APK and Windows executable builds.
- [ ] Test the full phone/PC call flow on the actual devices.
- [ ] Verify call controls, background reconnects and call events on the user's Android version.
- [ ] Establish a supported phone-to-PC two-way hands-free audio route.

## Audio limitation

BLE pairing and notification delivery are not a voice channel. Microsoft's Windows accessory guidance describes the PC as the Audio Gateway (AG) for a Bluetooth headset-type accessory while the accessory acts as the Hands-Free unit (HF). That is not the phone-facing HF role this cellular-audio route requires.【turn484453search0】

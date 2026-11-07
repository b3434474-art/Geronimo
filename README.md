# Geronimo

Geronimo is a local-first Windows voice assistant with an Android phone companion.

## Project layout

- `windows-app/` — the original Python desktop assistant plus the phone controls.
- `android-companion/` — Kotlin Android companion for call events and call controls.
- `docs/` — architecture and Bluetooth audio notes.

## Original desktop features

The original navy-and-blue interface and pulsing orb are retained. The app includes local Ollama AI, optional push-to-listen offline Vosk recognition, spoken replies, calculator, timer, jokes, facts, and optional Night Watch camera motion indication.

Ollama and the Vosk speech model are separate free downloads; neither is bundled in the executable. Night Watch is an opt-in local camera feature, not a certified security system.

## Phone companion features

- Local Bluetooth LE connection with call-state notifications in both directions.
- Incoming call popup on Windows, with manual Answer and Decline.
- Hang up, mute, and speaker commands.
- Outgoing dialing from Windows; unknown numbers open the Android dialer for manual confirmation.
- Phone-side trusted-number allowlist for outgoing calls only.
- Caller-name lookup from carrier caller ID, local allowlist, or contacts when permission is granted.
- Custom incoming and outgoing MP3 ringtones on Android.
- Android foreground service to maintain the phone-link connection while in the background.

**Incoming calls are never answered automatically.** The user must explicitly choose Answer on the phone or Windows call popup.

## Audio limitation

The BLE channel transfers call metadata and commands, not voice. The Windows 10 Bluetooth stack used by this project does not provide the phone-to-PC Hands-Free Profile endpoint required for cellular call audio through the laptop speakers and microphone. Two-way PC call audio is therefore not included or verified.

## Build status

GitHub Actions builds the Android debug APK and Windows executable when `main` changes. Check the latest Actions run for the result and download its artifacts. Artifacts are retained for 14 days.

## Development

Windows source run (PowerShell):

```powershell
cd windows-app
py -m pip install -r requirements.txt
py -m geronimo.main
```

Android Studio: open `android-companion/`, sync Gradle, and use JDK 17 / Android SDK 36.

## Privacy

Call details and trusted numbers remain on the phone/PC and are transferred only over the local paired BLE connection. The contacts permission is optional. Do not commit phone numbers, contacts, call logs, credentials, or pairing secrets.

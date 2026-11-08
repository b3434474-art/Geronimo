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

- Bluetooth LE call-state notifications in both directions; the phone rejects unpaired PC connections.
- ADB-over-USB is preferred for call commands and status; paired BLE remains the fallback.
- Incoming call popup and a locally generated repeating ringtone on Windows.
- The Windows ringtone starts on incoming ring and stops when the call is answered, changes to another call state, ends, the phone link disconnects, or Geronimo shuts down.
- Answer, decline, hang up, mute, speaker and outgoing dial controls.
- Outgoing dialing from Windows; unknown numbers open the Android dialer for manual confirmation.
- Phone-side trusted-number allowlist for outgoing calls only.
- Caller-name lookup from Android caller ID, local allowlist, or contacts when permission is granted.
- Custom incoming and outgoing MP3 ringtones on Android.
- Android foreground service to maintain the phone-link connection while in the background.

**Incoming calls are never answered automatically.** The user must explicitly choose Answer on the phone or Windows call popup.

## Call audio

The Windows Calls panel shows the real call-audio endpoints reported by Android during a call and can request a route change through Android Telecom on Android 14 or newer. This changes the phone's call-audio route; it does not capture or transmit cellular voice to Windows.

The laptop will receive caller audio only if Android itself lists the laptop as a compatible call-audio endpoint. Geronimo does not invent an endpoint or bypass Android's call-audio access rules. USB and BLE carry call metadata and commands, not voice. See [Android's InCallService API](https://developer.android.com/reference/android/telecom/InCallService) and [Android audio input sharing](https://developer.android.com/media/platform/sharing-audio-input).

## Build status

GitHub Actions builds the Android debug APK and Windows executable on pushes to `main`. It also runs the Windows ringtone unit tests. Check the latest Actions run for the result and download its artifacts. Artifacts are retained for 14 days.

## Development

Windows source run (PowerShell):

```powershell
cd windows-app
py -m pip install -r requirements.txt
py -m geronimo.main
```

Android Studio: open `android-companion/`, sync Gradle, and use JDK 17 / Android SDK 36.

## Privacy

Call details and trusted numbers remain on the phone/PC and are transferred only over the local USB or paired BLE connection. Contact permission is optional. Do not commit phone numbers, contacts, call logs, credentials, or pairing secrets.

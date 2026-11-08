# Geronimo Windows companion

This is the original Geronimo assistant UI with an added phone-control strip.

## Phone connection

- **USB (preferred):** Install Android SDK Platform-Tools, add `adb.exe` to PATH, enable USB debugging on the phone, connect with a data-capable cable, unlock the phone, and approve the USB debugging prompt for this PC. The Connect button checks USB first.
- **Bluetooth fallback:** Pair the phone and PC in Windows Bluetooth settings, enable Bluetooth on both, open Geronimo on Android, and press Connect in Windows.
- USB and Bluetooth carry call commands and call-state notifications. They do not carry cellular call audio.

## Calls

The phone app must be installed and selected as the default phone app for Android to deliver InCallService call events. Incoming calls are never auto-answered. They require a manual Answer action on the phone or the Windows call popup. Outgoing numbers not on the phone-side trusted list open the Android dialer for confirmation.

## Audio limitation

The laptop microphone and speakers cannot be used as the cellular-call headset through this companion. Android restricts cellular call-audio capture by ordinary third-party apps; the supported InCallService interface provides call controls and audio-route selection, not raw call audio. Call audio stays on the phone's selected audio route.

## Original assistant

The original full-screen Geronimo interface, blue orb, calculator, timer, facts, jokes, Ollama integration, optional Vosk speech recognition, optional spoken replies, and opt-in Night Watch feature are retained. See the root README for the project overview.

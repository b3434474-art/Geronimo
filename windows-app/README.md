# Geronimo Windows companion

This is the original Geronimo assistant UI with an added phone-control strip. The original navy-and-blue full-screen assistant and pulsing orb remain intact.

## Phone connection

- **USB (preferred):** Install Android SDK Platform-Tools, add `adb.exe` to PATH, enable USB debugging on the phone, connect with a data-capable cable, unlock the phone, and approve the USB debugging prompt for this PC. The Connect button checks USB first.
- **Bluetooth fallback:** Pair the phone and PC in Windows Bluetooth settings, enable Bluetooth on both, open Geronimo on Android, and press Connect in Windows.
- USB and Bluetooth carry call commands and call-state notifications. They do not carry cellular call audio.

## Calls

The phone app must be installed and selected as the default phone app for Android to deliver InCallService call events. Incoming calls are never auto-answered. They require a manual Answer action on the phone or the Windows call popup.

For an incoming call, Windows displays the caller and plays a generated repeating ringtone locally. It stops when the call is answered, moves to another state, ends, the phone link is disconnected, or Geronimo closes.

## Call audio output

The Calls panel displays the endpoints reported by Android during a call and can ask Android Telecom to change the phone's active route (Android 14+). The Windows PC's speakers will play the caller only if Android lists that PC as a supported call-audio endpoint. The route selector cannot create a missing Bluetooth hands-free endpoint or stream audio over the Geronimo control link.

## Original assistant

The original full-screen Geronimo interface, blue orb, calculator, timer, facts, jokes, Ollama integration, optional Vosk speech recognition, optional spoken replies, and opt-in Night Watch feature are retained. See the root README for the project overview.

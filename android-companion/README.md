# Geronimo Android companion

The phone-side Kotlin app works with the original Geronimo Windows assistant. Use JDK 17 and Android SDK 36 to build it.

## Features

- User-confirmed request to become Android's default phone app.
- Android `InCallService` call-state events for ringing, dialing, active, held and ended calls.
- Incoming call screen and notifications with manual **Answer** and **Decline**, plus **Hang up** for active calls.
- Your custom incoming and outgoing MP3 sounds, repeated until the call state changes.
- USB command/event transport over an authorized ADB port forward, with Bluetooth LE as an optional fallback.
- Answer, decline, hang up, mute, speaker and outgoing dial commands from the Windows companion over either transport.
- Caller-name lookup from Android caller ID, the local trusted-number list, or phone contacts after permission is granted.
- Outgoing trusted-number list stored on the phone. Only a matching number on that phone-side list may use direct dialing, and only after Android's CALL_PHONE permission is granted. Otherwise the Android dialer opens for manual confirmation.
- BLE mode accepts commands only from a Bluetooth-bonded PC.
- On Android 14+, actual available call-audio endpoints are reported to Windows. The Windows panel can request an endpoint change using the identifier Android supplied.

Incoming calls are never answered automatically. A user must tap Answer on the phone or the Windows call popup.

## USB setup

1. Install the Windows **Android SDK Platform-Tools** package and make sure `adb.exe` is on the Windows PATH.
2. On the Galaxy, enable Developer options and USB debugging. Connect the phone to the Dell with a data-capable USB cable.
3. Unlock the phone and approve the **Allow USB debugging** prompt for this Dell. Only authorize a computer you trust.
4. Open Geronimo on Android and keep its phone-link notification running. The USB server listens on the phone's loopback interface only; Windows reaches it through ADB port forwarding.
5. In Windows Geronimo, press **Connect**. It checks authorized USB devices first and automatically falls back to Bluetooth when USB is not available.
6. On Android 14+, Android requires a qualifying Nearby devices permission before Geronimo can start its persistent connected-device foreground service. BLE itself needs both Bluetooth permissions; the USB server still uses ADB and does not pair over Bluetooth.
7. To stop using USB debugging, turn USB debugging off in Android Developer options. You can also revoke USB debugging authorizations there.

## Bluetooth fallback

1. Pair the Galaxy and Dell in normal Bluetooth settings.
2. Allow **Nearby devices / Bluetooth** and **Notifications** in Geronimo.
3. Keep the foreground phone-link notification enabled. Windows will use BLE when no authorized USB connection is available.

## Call audio

The Windows Calls panel can list and request only the call-audio endpoints Android Telecom actually reports, on Android 14 or newer. Choosing an endpoint routes the call on the phone. It does **not** stream raw call audio to the Windows app.

The laptop microphone and speakers work as the call audio device only if Android exposes the laptop itself as a compatible call endpoint. Geronimo cannot create that endpoint or capture cellular-call audio directly. USB and BLE remain control/status transports, not audio transports. See [Android InCallService](https://developer.android.com/reference/android/telecom/InCallService) and [Android audio input sharing](https://developer.android.com/media/platform/sharing-audio-input).

## Privacy and safety

The USB TCP server binds only to `127.0.0.1` on the phone and is reachable through an ADB port forward after the user authorizes the connected PC. BLE mode accepts only a bonded device. Call state and caller lookup stay on-device and are sent across the local USB or paired BLE link. Contact permission is optional. Never commit phone numbers, contacts, or pairing secrets to the repository.

# Geronimo Android companion

The phone-side Kotlin app works with the original Geronimo Windows assistant. Use JDK 17 and Android SDK 36 to build it.

## Features

- User-confirmed request to become Android's default phone app.
- Android `InCallService` call-state events for ringing, dialing, active and ended calls.
- Incoming call screen and notifications with manual **Answer** and **Decline**, plus **Hang up** for active calls.
- Your custom incoming and outgoing MP3 sounds, repeated until the call state changes.
- USB command/event transport over an authorized ADB port forward, with Bluetooth LE as a fallback.
- Answer, decline, hang up, mute, speaker and outgoing dial commands from the Windows companion over either transport.
- Caller-name lookup from Android caller ID, the local trusted-number list, or phone contacts after permission is granted.
- Outgoing trusted-number list stored on the phone. Only a matching number on that phone-side list may use direct dialing, and only after Android's CALL_PHONE permission is granted. Otherwise the Android dialer opens for manual confirmation.
- BLE mode accepts commands only from a Bluetooth-bonded PC.

Incoming calls are never answered automatically. A user must tap Answer on the phone or the Windows call popup.

## USB setup

1. Install the Windows **Android SDK Platform-Tools** package and make sure `adb.exe` is on the Windows PATH.
2. On the Galaxy, enable Developer options and USB debugging. Connect the phone to the Dell with a data-capable USB cable.
3. Unlock the phone and approve the **Allow USB debugging** prompt for this Dell. Only authorize a computer you trust.
4. Open Geronimo on Android and keep its phone-link notification running. The USB server listens on the phone's loopback interface only; Windows reaches it through ADB port forwarding.
5. In Windows Geronimo, press **Connect**. It checks authorized USB devices first and automatically falls back to Bluetooth when USB is not available.
6. To stop using USB debugging, turn USB debugging off in Android Developer options. You can also revoke USB debugging authorizations there.

## Bluetooth fallback

1. Pair the Galaxy and Dell in normal Bluetooth settings.
2. Allow **Nearby devices / Bluetooth** and **Notifications** in Geronimo.
3. Keep the foreground phone-link notification enabled. Windows will use BLE when no authorized USB connection is available.

## Audio limitation

USB carries command and call-state data only. It does **not** route cellular-call audio to the Windows microphone and speakers. Android restricts ordinary third-party apps from capturing cellular-call uplink/downlink audio; that capability requires a privileged, preinstalled app. The laptop's audio devices therefore cannot replace the phone's call microphone and earpiece/speaker through this USB bridge. Call audio remains on the phone's selected audio route.

## Privacy and safety

The USB TCP server binds only to `127.0.0.1` on the phone and is reachable through an ADB port forward after the user authorizes the connected PC. BLE mode accepts only a bonded device. Call state and caller lookup stay on-device and are sent across the local USB or paired BLE link. Contact permission is optional. Never commit phone numbers, contacts, or pairing secrets to the repository.

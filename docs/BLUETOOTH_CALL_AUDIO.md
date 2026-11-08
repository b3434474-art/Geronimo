# Geronimo call transport and audio

## Phone connection transports

Geronimo supports two local transports for call controls and status events:

- **USB:** Windows uses Android Debug Bridge (ADB) port forwarding to connect to a loopback-only TCP server in the Android companion. USB is preferred when an authorized device is detected.
- **Bluetooth LE:** retained as an optional fallback when USB is unavailable. The BLE connection must be paired/bonded in the device settings.

Both transports carry dial, answer, reject, hang-up, mute, speaker, trusted-contact sync, caller/call-state messages, and reported audio-endpoint metadata. Neither transports live cellular call audio. Incoming calls require a manual answer action.

## Windows incoming ringtone

When Android reports `CALL_RINGING`, the Windows companion plays a small, original two-tone WAV ringtone locally using the Windows sound API. It repeats until the call is answered, moves into another state, ends, the PC disconnects the phone link, or Geronimo shuts down. No internet or extra audio package is needed for this feature.

## Android call audio endpoint routing

On Android 14 or newer, Geronimo listens for Telecom's available call-audio endpoints and sends their exact identifiers and names to the Windows Calls panel. The panel can ask Android to switch to one of those currently available endpoints. The app does not fabricate endpoints: it only requests an endpoint supplied by Android.

This can route a call to a device that Android already recognizes as a supported call endpoint. It cannot turn a Windows PC into a Bluetooth hands-free endpoint, and it does not copy cellular voice into the Geronimo app.

## Why laptop caller audio is not guaranteed

Android's documented audio-input sharing rules say a normal third-party app cannot capture cellular-call audio; call capture requires system/privileged capabilities. See [Android audio input sharing](https://developer.android.com/media/platform/sharing-audio-input).

The Android `InCallService` API allows a default dialer to request supported call audio endpoints; it does not expose raw cellular-call microphone and speaker streams to ordinary apps. See [Android InCallService](https://developer.android.com/reference/android/telecom/InCallService).

Bluetooth role direction matters too: Microsoft's Windows Bluetooth accessory documentation describes the PC as the Hands-Free Profile Audio Gateway and an accessory such as a headset as the Hands-Free unit. That standard Windows PC role is not automatically the phone-facing headset role required for the phone to route a call to the laptop. See [Microsoft Bluetooth accessory guidance](https://learn.microsoft.com/en-us/windows-hardware/design/accessory-guidelines/bluetooth-accessory-guidelines/bluetooth-accessory-guidelines-classic-audio).

Therefore, the supported Geronimo feature now rings the Windows laptop and exposes Android's real route choices. Caller voice through laptop speakers remains conditional on Android listing the laptop as a compatible endpoint and must be verified on the actual phone and PC.

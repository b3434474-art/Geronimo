# Geronimo call transport and audio

## Phone connection transports

Geronimo supports two local transports for call controls and status events:

- **USB:** Windows uses Android Debug Bridge (ADB) port forwarding to connect to a loopback-only TCP server in the Android companion. USB is preferred when an authorized device is detected.
- **Bluetooth LE:** retained as an automatic fallback when USB is unavailable. The BLE connection must be paired/bonded in the device settings.

Both transports can carry dial, answer, reject, hang-up, mute, speaker, trusted-contact sync, and caller/call-state messages. Incoming calls still require a manual answer action.

## Cellular call audio

Neither the USB ADB bridge nor the BLE bridge carries live cellular-call audio. Android's documented audio-input sharing rules say a normal third-party app cannot capture cellular-call audio; call capture requires an accessibility service or a privileged preinstalled app with the protected `CAPTURE_AUDIO_OUTPUT` permission. See the [Android audio input sharing documentation](https://developer.android.com/media/platform/sharing-audio-input).

The Android `InCallService` API allows a default dialer to request supported call audio endpoints (for example, earpiece, speaker, Bluetooth, or a wired headset), but it does not grant ordinary apps raw access to the cellular call's microphone and speaker streams. See the [Android InCallService API](https://developer.android.com/reference/android/telecom/InCallService).

Therefore, the supported Geronimo build keeps actual call audio on the phone's currently selected audio route. The Windows microphone and speakers are not presented as a working cellular headset. This is an Android platform restriction, not a missing USB cable feature.

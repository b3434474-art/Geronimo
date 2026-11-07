# Bluetooth and cellular-call audio

## Current implementation

Geronimo uses BLE GATT for small call-control and call-state messages:

- Windows to Android: dial, answer, decline, hang up, mute and speaker commands.
- Android to Windows: phone-ready, incoming/ringing, dialing, active, ended and error events, including caller number/name when available.

BLE notifications are not used for call voice audio.

## Why laptop call audio is not included

Windows 10's built-in Bluetooth Hands-Free Profile support is designed around the PC as an Audio Gateway and an accessory (such as a headset) as the Hands-Free unit. The required phone-to-PC connection needs the computer to provide the Hands-Free endpoint to the phone. That role is not provided by the target Windows 10 setup, so this app cannot route the phone's cellular call audio through the laptop microphone and speakers using the current built-in stack.

## Acceptance test for any future audio route

Do not mark call audio as supported until a real test on the target hardware demonstrates:

- Remote caller audio plays through the PC speakers.
- The PC microphone is transmitted to the call.
- The route remains stable for incoming and outgoing calls, screen-off and reconnects.
- Call controls work and incoming calls still always require explicit approval.

Until these tests pass, show call-audio support as unavailable / not verified.

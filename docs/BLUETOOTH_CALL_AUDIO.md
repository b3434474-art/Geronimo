# Bluetooth and cellular-call audio

## Current implementation

Geronimo uses BLE GATT for small call-control and call-state messages:

- Windows to Android: dial, answer, decline, hang up, mute and speaker commands.
- Android to Windows: phone-ready, incoming/ringing, dialing, active, ended and error events, including caller number/name when available.

The phone accepts call commands only from a Bluetooth-bonded PC. BLE notifications are not used for call voice audio.

## Why laptop call audio is not included

Microsoft's Windows Bluetooth accessory guidance describes the PC as the Audio Gateway (AG) and a headset-style accessory as the Hands-Free unit (HF). HFP uses the HF unit as the remote microphone/speaker for the AG.【turn484453search0】【turn484453search1】 This documented Windows accessory arrangement does not provide the phone-facing HF endpoint required to route the phone's cellular call audio through the laptop.

## Acceptance test for any future audio route

Do not mark call audio as supported until a real test on the target hardware demonstrates:

- Remote caller audio plays through the PC speakers.
- The PC microphone is transmitted to the call.
- The route remains stable for incoming and outgoing calls, screen-off and reconnects.
- Call controls work and incoming calls still always require explicit approval.

Until these tests pass, show call-audio support as unavailable / not verified.

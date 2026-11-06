# Bluetooth and cellular-call audio investigation

## Do not assume pairing is enough

A normal Bluetooth pairing does not automatically expose the phone's cellular call controls or call audio to a Windows application. A2DP media audio and HFP hands-free call audio are separate profiles and roles.

Before implementing an audio bridge, establish all of the following on the target hardware:

1. Which Bluetooth profile roles the Android phone and Windows Bluetooth adapter actually support.
2. Whether Windows can act as the required hands-free endpoint for the phone, rather than merely acting as an audio gateway for a Bluetooth headset.
3. Whether Android allows the intended call-audio route for a third-party companion app under the selected phone-app/telecom role.
4. Whether microphone and speaker audio can travel both ways with acceptable latency and without feedback.
5. Whether the route survives screen-off, incoming calls, reconnects, and Bluetooth interruptions.

## Acceptance test

Call audio is not considered implemented until a real test demonstrates:
- audio from the remote caller is heard through the computer speakers;
- the computer microphone is sent to the call;
- answer, decline, and hang-up actions work only after the corresponding user action;
- an incoming call is never answered automatically.

Until then, the app must display call-audio support as **unavailable / not verified**, not as connected.

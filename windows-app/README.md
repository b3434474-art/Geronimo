# Geronimo Windows app

Geronimo keeps the original full-screen navy-and-blue voice-assistant interface and adds the phone companion in a separate strip. The original orb, Ollama assistant, Vosk offline speech recognition, spoken responses, calculator, timer, jokes, facts and opt-in Night Watch camera panel are preserved.

## Run from source

Requires Python 3.10+ on Windows. The first run restores the original assistant module from its compressed source file.

```powershell
cd windows-app
py -m pip install -r requirements.txt
py -m geronimo.main
```

Pair the Dell PC and Galaxy A15 in normal Windows Bluetooth settings before using the phone link. The Android phone accepts commands only from a bonded PC. Open Geronimo on Android and leave its phone-link foreground notification enabled. Press **Connect** in the Windows app. BLE carries call-state messages and call-control commands, not live call audio.

## Original offline-assistant features

- **Local AI:** Install Ollama separately and download the default `llama3.2:1b` model. It runs on this PC; the model is not bundled in the executable.
- **Offline speech recognition:** Download the small English Vosk model and extract it to `windows-app/geronimo/models/vosk-model-small-en-us-0.15`. Listening begins only after you press Voice recognition.
- **Spoken responses:** Uses Windows speech output through pyttsx3.
- **Night Watch:** Optional camera motion indicator, enabled only after an explicit confirmation. It does not record or upload frames and is not a certified security system.

## Phone companion

The phone link supports call notifications, caller names when Android permissions permit, answer/decline/hang-up controls, mute/speaker commands, outgoing dialing, and a locally saved trusted-outgoing-number list. A trusted number can skip the dialer confirmation only if the phone-side allowlist contains the exact number and the user has granted Android's CALL_PHONE permission. Otherwise unknown numbers open the Android dialer for manual confirmation.

Incoming calls are never auto-answered. Answer requires a deliberate tap on the phone or Windows call popup.

## Audio limitation

BLE transports small command and event messages, not voice. Microsoft's Windows accessory guidance describes the PC as the HFP Audio Gateway while an audio accessory such as a headset acts as the Hands-Free unit. That built-in arrangement does not provide the phone-facing Hands-Free endpoint needed for this phone-to-laptop audio route. This build does not claim laptop call audio.

## Build artifacts

GitHub Actions builds the Android debug APK and Windows executable on pushes to `main`. Build artifacts are retained for 14 days. Ollama and the Vosk speech model remain separate downloads.

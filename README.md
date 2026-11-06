# Geronimo

Geronimo is a local-first phone companion project for a Windows PC and an Android phone.

## Project layout

- `windows-app/` — Python desktop app for the computer.
- `android-companion/` — Kotlin Android companion app.
- `docs/` — architecture notes and Bluetooth research.

## Intended behavior

- The Android phone and Windows computer communicate over a local Bluetooth connection.
- **Incoming calls always require an explicit accept/decline action.** Geronimo must never auto-answer.
- A locally stored contact allowlist may reduce confirmation prompts for **outgoing** calls only. It must never change incoming-call behavior.
- Call controls should remain unavailable until a real, authenticated phone-to-PC connection is established.
- No cloud account or paid backend is required for the planned local connection.

## Important status

This repository currently contains the initial app scaffolding and call-approval policy, not a completed phone integration. In particular, pairing two devices does not by itself make Windows a cellular-call audio endpoint. Two-way call audio and reliable call-control routing are research/implementation tasks and must be verified on the actual phone and PC before being described as working.

## Development

### Windows app

Requires Python 3.10 or newer. From `windows-app`:

```powershell
python -m geronimo.main
```

Run the policy tests:

```powershell
python -m unittest discover -s tests -v
```

### Android companion

Open `android-companion/` in Android Studio and let Gradle sync. Use JDK 17 and install Android SDK 36. The Android module is an informational starter screen; it does not yet request call permissions or act as the default phone app.

## Privacy and safety

- Do not commit real phone numbers, contact lists, call logs, credentials, pairing secrets, or local settings.
- Store any future allowlist on the user's device, not in Git.
- Ask for permission before enabling call-related Android capabilities.
- Keep incoming-call approval mandatory.

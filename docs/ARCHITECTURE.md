# Geronimo architecture

## Components

1. **Android companion** — future phone-side component. Android's call-management APIs are permission- and role-sensitive. The app must clearly explain any role or permission request before asking the user to approve it.
2. **Windows desktop app** — future user interface for connection status and call prompts.
3. **Bluetooth transport** — a local protocol for pairing, authenticating, exchanging capability/status messages, and sending user-approved commands.

## Required call policy

| Event | Required behavior |
| --- | --- |
| Incoming call | Always show a prompt. Never auto-answer. |
| User taps Decline | Decline only after an explicit action. |
| User taps Answer | Answer only after an explicit action and only if Android grants the required capability. |
| Outgoing call to allowlisted contact | May skip the extra Geronimo confirmation, but only after the user initiates it. |
| Outgoing call to other contact | Ask for confirmation before placing it. |
| Lost or untrusted connection | Disable remote call controls. |

The outgoing allowlist and incoming-call approval are separate policies. A contact being allowlisted must never cause an incoming call to be answered automatically.

## Milestones

- [x] Create repository structure and project documentation.
- [x] Add a testable call-approval policy to the Windows package.
- [x] Add initial Windows and Android app shells.
- [ ] Implement and test authenticated Bluetooth messaging.
- [ ] Implement Android call-state integration with the required Android role/permissions.
- [ ] Implement Windows call prompt and user-triggered controls.
- [ ] Verify whether a supported hands-free audio route can be established for this exact PC/phone combination.
- [ ] Add end-to-end tests and device setup instructions.

## Audio limitation

Bluetooth pairing, A2DP media playback, and Hands-Free Profile (HFP) cellular-call routing are different capabilities. The existence of a paired device or an audio device node is not proof that the PC can act as the phone's hands-free endpoint. Do not report call audio as supported until an actual two-way test succeeds.

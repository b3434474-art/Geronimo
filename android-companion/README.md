# Geronimo Android companion

This is the phone-side Kotlin starter project.

Open this directory in Android Studio, use JDK 17, and install Android SDK 36. The current app is an informational shell only; it does not yet read call state, answer calls, place calls, or route cellular-call audio.

## Android call integration

A production call companion may need the Android default phone-app role and Telecom APIs such as `InCallService`. These are not granted merely by pairing over Bluetooth. Before adding them, the app must explain why the role is needed and request it through Android's supported user-facing flow.

Incoming calls must always be presented for explicit approval. A local outgoing-contact allowlist must not influence incoming calls.

## Privacy

Request only the permissions needed for a feature when that feature is implemented. Do not upload contacts or call information to a server. Never add personal contacts or phone numbers to this repository.

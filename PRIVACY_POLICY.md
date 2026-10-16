# Apocalyptic AI — Privacy Policy

**Effective date: October 16, 2026**

Apocalyptic AI is an Android survival and emergency-preparedness assistant. This policy describes data handling in the app build in this repository. The publisher should review and host this policy at a stable public URL before submitting an app listing.

## Information processed on your device

- **Chat messages and conversation history:** stored in app-private local storage so the conversation can be restored. Messages are processed by the model selected and downloaded by the user, on the device. The app does not send prompts or generated answers to an AI inference server.
- **Voice input:** only after the user taps Mic and grants microphone permission, audio is passed to Android's on-device speech recognizer to produce draft text. Voice input is available on Android 12 or newer when an on-device speech model is installed. The app does not send the audio or recognized text to its own server. Recognition model availability and processing are provided by the device's Android speech service.
- **Spoken replies:** optional and off by default. When enabled, completed AI replies are read aloud using the installed Android text-to-speech engine. The engine and voice are provided by the device; their offline behavior and data handling depend on the installed system voice.
- **Go-bag checklist and preferences:** checklist state, appearance preference, selected model, and download status are stored locally in Android app preferences.
- **Model files:** downloaded model weights are stored in app-specific storage. The app does not bundle or upload model files.

Android backup is disabled for this app. Clearing conversations, resetting the checklist, removing a model, or uninstalling the app removes the corresponding app data from this device. Android system-level forensic recovery is outside the app's control.

## Network connections

The app requests internet access for two user-initiated purposes:

1. **Model downloads:** when you choose a model, Android DownloadManager retrieves the model file from the model host identified in the Models screen. The hosting provider and any content-delivery service may receive ordinary request and connection metadata, such as your IP address, requested file, and download time. Their own privacy and terms apply.
2. **Donation link:** choosing Donate opens `https://raihan.sites.bd` in an external browser. The website and browser may process data under their own policies; the app does not receive payment information.

The app does not provide accounts, remote chat inference, advertising, or an analytics service. This statement does not cover the independent practices of Android speech and text-to-speech services, the model host, the external browser, or the donation website.

## Permissions

The app requests Android's internet permission for model downloads and the donation link. It requests microphone permission only when the user starts voice input. It does not request location, contacts, camera, or phone-state permissions.

## Children's privacy

The app is a general-audience preparedness tool and is not designed to collect information from children. It does not request personal information or create accounts.

## Security and retention

Data is stored in app-private device storage, but no mobile application can guarantee absolute security. Anyone with access to an unlocked device or its app data may be able to view information. Keep the device secured and delete conversations that you no longer need.

## Changes and contact

This policy should be updated if the app's data practices change. For privacy questions, contact the publisher through [raihan.sites.bd](https://raihan.sites.bd).

## Publisher checklist

Before release, the publisher should verify that this policy still matches the shipped binary, host it at a stable public URL, add that URL to the Play Store listing, and submit an accurate Google Play Data safety declaration. This draft is informational and is not legal advice.

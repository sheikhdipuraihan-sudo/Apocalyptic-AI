# Apocalyptic AI — Privacy Policy

**Effective date: October 16, 2026**

## About this policy

This policy describes Apocalyptic AI for Android and the data handling implemented in the app. Apocalyptic AI is a local-model preparedness assistant. **Publisher/privacy contact:** [raihan.sites.bd](https://raihan.sites.bd). The app does not create user accounts or operate a cloud AI inference, advertising, or analytics service.

## Information stored on your device

- **Chat and saved conversations:** Stored in the app's private storage so you can reopen them. The selected downloaded model processes prompts and generates responses on the device.
- **Preferences and checklist:** Appearance and generation preferences, selected model, download status, and checklist state are stored in app preferences.
- **Model files:** Downloaded model weights are stored in app-specific storage.
- The app does not intentionally collect account details, contacts, location, camera data, or payment-card details. Android backup is disabled.

## Optional Host AI — local browser chat

Host AI is off until you start it. When enabled, the phone runs an HTTP server bound to its private Wi-Fi or phone-hotspot IPv4 address. A friend needs to be connected to that local network and have the temporary access-code share link. Messages are sent to this phone and processed by its downloaded model; they are not forwarded to a cloud AI or internet relay. Host AI does not expose a public URL or make an outbound internet connection for chat.

Browser messages are processed temporarily in memory and are not added to the app's saved conversation history. The temporary host code and status are stored in app-private preferences while hosting and removed when hosting stops. The browser page keeps its conversation only for that page session. Stop hosting to revoke the access code and close the local server.

**Network security:** Hosted browser chat uses local HTTP, not encrypted HTTPS. Other people or devices with access to the network may be able to observe traffic; the access code is not encryption. Share the link only with people you trust on a network you trust, and do not send sensitive information through the hosted page. Some phone hotspots block communication between connected devices. The app cannot control recipients' or local-network operators' practices.

## Voice features

Voice input is optional and starts only after you tap the microphone, review the in-app disclosure, and grant microphone permission. On supported Android versions, audio is passed to Android's on-device speech recognizer to produce draft text. The app does not send the recording to its own server. Recognition availability and the speech service/model's handling depend on Android and what is installed on your device. Spoken replies are optional and use the Android text-to-speech engine and voice installed on your device; the engine's offline behavior and data practices are controlled by the device/provider.

## Network connections and third parties

Android DownloadManager downloads a model only after you choose one. The selected model host and content-delivery provider may receive ordinary connection information such as your IP address, requested file, and request time; their terms and privacy policies apply. Choosing Donate opens raihan.sites.bd in an external browser, which is subject to that website's and browser's own practices. Local Host AI does not use an internet relay. Model files and libraries are identified in the app's Open-source notices and project documentation; review their respective terms.

## Permissions

Internet access is used for user-requested model downloads, opening the donation website, and local socket operation when Host AI is started. Microphone permission is requested only for voice input. Host AI uses a foreground service and visible notification; Android 13 or newer may request notification permission before hosting starts. Wi-Fi state, wake-lock, and foreground-service permissions support this user-started host. The app does not request location, contacts, camera, or phone-state permissions.

## Retention and deletion

Saved conversations and preferences remain on the device until you remove them, clear app data, or uninstall the app. Downloaded model files remain until removed in the app or until app data is cleared/uninstalled. Host AI browser messages are not written to the app's saved chat history. The host access code is revoked and removed from app preferences when hosting stops; the active browser page may retain its temporary tab state until it is closed or refreshed. A browser or network may retain normal cache or connection records. Android backup is disabled.

The app has no user accounts, so there is no account deletion process. Available local controls are clearing conversations, removing downloads, clearing app data, or uninstalling.

## AI and safety notice

AI-generated responses can be inaccurate, incomplete, outdated, biased, or unsafe. This app is an educational preparedness aid, not an emergency service and not medical, legal, financial, or other professional advice. Do not rely on it for urgent decisions. Follow local law, official emergency instructions, and qualified professional guidance; contact emergency services when appropriate. Users are responsible for deciding whether and how to use or share generated output. Nothing in this policy limits rights that cannot legally be limited.

## Children

The app is not designed as a children's service. The publisher should accurately complete any applicable Play Console audience and Families declarations for the actual distribution.

## Contact and changes

For privacy questions or requests, contact the publisher through [raihan.sites.bd](https://raihan.sites.bd). This policy should be updated whenever the app's data practices change. Keep any public privacy-policy URL and Google Play Data safety declarations consistent with the released binary. The policy and disclosures do not guarantee legal compliance or store approval; the publisher should obtain jurisdiction-specific legal review where appropriate.

## Publisher release checklist

Before distributing on Google Play, host this policy at an active, publicly accessible, non-geofenced URL and add that URL in Play Console. Complete and maintain an accurate Data safety form for the final AAB, taking account of app permissions, model-download providers, Android speech/TTS services, and the opt-in local HTTP host. For Android 14+ targets, complete the Play Console foreground-service declaration for the declared service type, including the use-case description, user impact, and a demonstration video. Verify model and library licenses, store-listing disclosures, target-audience declarations, and all applicable policies. An in-app policy is useful but does not replace the public privacy-policy URL, Play Console declarations, or qualified legal advice.

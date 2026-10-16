# Apocalyptic AI

A clean, privacy-first Android field guide for offline survival planning. The app is designed around a **LLaMA 3.2 1B GGUF** model and a compact, dependency-free native shell so the release APK stays small. The app uses Internet only when the user taps **Download model**. The GGUF download happens through Android’s in-app DownloadManager and is saved locally; chat and the bundled guide remain offline after download.

## What is included

- White, modern AI-chat interface with conversation cards, quick prompts, and a model picker.
- Offline safety-first knowledge pack covering water, shelter, first aid, food safety, tools, and repairs.
- Guardrail for weapon, explosive, and injury-trap requests; the app redirects to lawful, non-violent preparedness.
- Donation link on the main screen: https://raihan.sites.bd
- GitHub Actions workflow that builds both a debug APK and a signed-ready release AAB.

## Local model note

This repository does not redistribute Meta’s LLaMA weights. **Download model** now starts an in-app download of the ARM-friendly Q4_0_4_4 GGUF quantization (about 770 MB) from a public Hugging Face quantization repository. Internet is used only for that explicit download; the AI remains local/offline afterward. The download is stored in the app’s private external Downloads directory and tracked by Android DownloadManager. The current release includes the compact UI and official download handoff plus an offline fallback guide. For production inference, connect a llama.cpp Android/JNI runner to the downloaded GGUF file (kept intentionally separate from the UI so the optional native runtime does not inflate the base APK).

## Build locally

```bash
cd gradle
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

## GitHub Actions

Every push to `main` builds `app-debug.apk` and `app-release.aab`. The AAB is uploaded as an artifact for Play Console publishing. To sign a production AAB, add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` repository secrets; otherwise the workflow creates an unsigned release bundle and clearly labels it.

## Branding and model attribution

**Apocalyptic AI** is the application name, primary identity, interface brand, and offline survival assistant persona. The app uses the **Meta LLaMA 3.2 1B** open-weight model when the user downloads it. The app should identify itself as Apocalyptic AI first and credit Meta and LLaMA 3.2 when model attribution is relevant. “Open-weight” is used intentionally: Meta’s Llama 3.2 Community License is not an OSI-approved open-source license, so the app does not mislabel the model or imply that Apocalyptic AI owns the model weights.

The runtime system prompt is: “You are Apocalyptic AI, a privacy-first offline survival assistant. Apocalyptic AI is the app and primary brand. You run locally using the open-weight Meta LLaMA 3.2 1B model under the Llama 3.2 Community License. Identify yourself as Apocalyptic AI first and credit Meta LLaMA 3.2 1B when model attribution is relevant. Give cautious, lawful, non-violent preparedness guidance.”

## Model licensing

LLaMA 3.2 1B is open-weight, but Meta distributes it under the Llama 3.2 Community License rather than an OSI-approved open-source license. The app downloads a compatible quantization from a public repository; users are responsible for reviewing the applicable terms before using or redistributing weights.

## Content and safety

The bundled summaries are original, concise notes, not full books. The references point to public-domain or openly available preparedness sources. Always follow local law, official emergency guidance, and trained medical advice.

## Native local inference

The app now links the official MIT-licensed llama.cpp Android binding as a pinned source submodule. The Download model action saves a GGUF file into app-private storage; once present, prompts are sent to llama.cpp locally with the Apocalyptic AI system prompt and generated tokens are returned to the chat UI. The Release page publishes only `app-release.apk` and `app-release.aab`.

## Release signing

This repository is private and contains the Apocalyptic AI release keystore and `gradle/signing.properties` because the owner explicitly requested repository-stored signing. Keep the repository private and do not share the keystore or signing properties. The GitHub Actions workflow signs both the release APK and release AAB from these files.

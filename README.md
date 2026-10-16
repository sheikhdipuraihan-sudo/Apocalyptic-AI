# Apocalyptic AI

A privacy-first Android preparedness assistant with a bundled offline guide and optional local AI. Chat runs on the selected GGUF model on the device; the app does not send chat messages to a server. Internet is used only when the user chooses to download a model.

## What is included

- Responsive sidebar navigation, light/dark/system themes, conversation history, and a model picker for 360M, 0.5B, and 1B models.
- Offline safety-first knowledge pack covering water, shelter, first aid, food safety, tools, and repairs.
- Guardrail for weapon, explosive, and injury-trap requests; the app redirects to lawful, non-violent preparedness.
- Donate is available from the sidebar and Settings: https://raihan.sites.bd
- GitHub Actions workflow that builds both a debug APK and a signed-ready release AAB.

## Local model note

Model weights are not bundled or redistributed by this repository. The in-app model picker offers:

- Meta LLaMA 3.2 1B Instruct, Q4_0_4_4 (about 735 MiB)
- Qwen2.5 0.5B Instruct, Q4_K_M (about 379 MiB)
- SmolLM2 360M Instruct, Q4_K_M (about 258 MiB)

Each download is stored in the app’s private Downloads directory and tracked by Android DownloadManager. The app checks available storage, reports transfer progress and failure reasons, and verifies the downloaded GGUF before enabling chat. Wi-Fi is recommended. The library remains available offline whether or not a model is installed.

## Build locally

```bash
cd gradle
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

## GitHub Actions

Every push to `main` builds `app-debug.apk` and `app-release.aab`. The AAB is uploaded as an artifact for Play Console publishing. To sign a production AAB, add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` repository secrets; otherwise the workflow creates an unsigned release bundle and clearly labels it.

## Branding and model attribution

**Apocalyptic AI** is the application name, primary identity, interface brand, and preparedness assistant persona. The selected model can be Meta LLaMA 3.2 1B, Qwen2.5 0.5B, or SmolLM2 360M. The app identifies as Apocalyptic AI and credits the selected model’s creators when attribution is relevant. Review each model’s own license and terms before use; Apocalyptic AI does not own or redistribute the model weights.

The runtime system prompt is tailored to the selected model and asks it to give cautious, lawful, non-violent preparedness guidance. Messages are processed locally on the device.

## Model licensing

Model licenses vary. Consult the original model card and applicable license before using or redistributing downloaded weights. The app downloads community-provided GGUF quantizations and does not bundle their weights.

## Content and safety

The bundled summaries are original, concise notes, not full books. The references point to public-domain or openly available preparedness sources. Always follow local law, official emergency guidance, and trained medical advice.

## Native local inference

The app links the MIT-licensed llama.cpp runtime as a pinned source submodule. Once a selected GGUF is downloaded and verified, prompts are sent to llama.cpp locally and generated tokens are returned to the chat UI. If a model cannot load, the app reports the error rather than presenting a bundled guide answer as AI chat.

## Release signing

Signing keys are **not** stored in this repository. Add these repository secrets (Settings → Secrets and variables → Actions) so every build uses the same key and can update in place:

`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`

Use an **RSA-2048** key. Without secrets, the workflow generates a throw-away RSA-2048 key for that build.

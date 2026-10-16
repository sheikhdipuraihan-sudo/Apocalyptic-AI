# Apocalyptic AI

**A private, on-device survival and emergency-preparedness assistant.** After a user downloads a small model, chat inference runs on the Android device; chat messages are not sent to an AI server. The app does not bundle model weights. Internet access is used for a user-requested model download and for the optional donation link.

## Product features

- Full-width Android chat layout with a simple sidebar; saved conversations can be reopened or deleted from the sidebar.
- Optional on-device voice dictation (Android 12+ with an installed offline speech model) and optional spoken replies through the system voice engine.
- A visible **Stop** control while the model is generating, with a cancellable native inference loop.
- On-device chat persistence, model download progress and validation, dark/light/system appearance, and a private go-bag checklist.
- Working inference controls: response length, context window, sampling temperature, custom persona/style instructions, restored-chat context, and optional background model unloading.
- Subtle screen transitions with a reduced-motion switch. GGUF chat templates are read from model metadata; the Android native build currently uses CPU backends only.
- Safety-oriented survival guidance for water, shelter, first aid, food safety, navigation, communications, and essential supplies. It rejects explicit requests for weapons, explosives, traps, and harming people.
- Donate action opens [raihan.sites.bd](https://raihan.sites.bd).

## Local model choices

The picker defaults to **TituLM 1B MPT Q4_K_M**, a compact Bangla/English model. Other choices include Qwen3 0.6B, Qwen2.5 0.5B, SmolLM2 360M, and SmolLM2 1.7B. The 1.7B model is intended for newer phones; 6 GB or more total RAM is recommended, and the app checks available memory before starting it. There are no 7B/13B-class downloads.

| Model | Quantization / approximate download | Upstream model and license | GGUF download repository |
| --- | --- | --- | --- |
| TituLM 1B MPT v2 | Q4_K_M · 870 MiB | [Hishab TituLM MPT 1B v2](https://huggingface.co/hishab/titulm-mpt-1b-v2.0) · Apache-2.0 | [mradermacher GGUF](https://huggingface.co/mradermacher/titulm-mpt-1b-v2.0-i1-GGUF) |
| Qwen3 0.6B | Q4_K_M · 462 MiB | [Qwen3 0.6B](https://huggingface.co/Qwen/Qwen3-0.6B) · Apache-2.0 | [bartowski GGUF](https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF) |
| Qwen2.5 0.5B Instruct | Q4_K_M · 379 MiB | [Qwen2.5 0.5B Instruct](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct) · Apache-2.0 | [bartowski GGUF](https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF) |
| SmolLM2 360M Instruct | Q4_K_M · 258 MiB | [SmolLM2 360M Instruct](https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct) · Apache-2.0 | [bartowski GGUF](https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF) |
| SmolLM2 1.7B Instruct | Q4_K_M · 1.0 GiB | [SmolLM2 1.7B Instruct](https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct) · Apache-2.0 | [Hugging Face Smol Models GGUF](https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF) |

The model cards list the upstream model terms. Quantized files are downloaded from the linked GGUF repositories, which may be maintained by a different publisher; review **both** links, the repository license, and any applicable terms before commercial distribution. TituLM MPT 1B is distinct from the TituLLM Llama 3.2 1B variant. This project does not redistribute the downloaded weights and does not make a legal-clearance guarantee. Model licenses do not establish rights to all training data, trademarks, or generated output.

Weights are kept in the app’s private Downloads directory and tracked with Android DownloadManager. The app checks available storage and validates the downloaded GGUF before enabling chat. Wi-Fi is recommended. The app does not send chat content to a server.

## Safety and privacy

- AI output can be inaccurate, incomplete, or unsafe. Do not rely on the model as an emergency service, doctor, or substitute for official local guidance. Call local emergency services when appropriate.
- The app is a general preparedness aid, not medical, legal, or professional advice. Follow local law, official emergency instructions, and trained professional guidance.
- Chat history and model files are stored on the device in app-private storage. Users can clear the active chat or remove downloaded models from the app. Android backup is disabled in the manifest.
- The optional internet permission is used for model downloads and opening the donation website; it is not used to send prompts to a remote inference service.
- Microphone access is requested only after the user taps Mic. Voice recognition uses the device's on-device speech service; spoken replies use the installed system TTS engine, whose offline behavior depends on the selected voice.

## Model attribution and third-party licenses

- Qwen3 and Qwen2.5 model family/source licensing is published by [Qwen](https://huggingface.co/Qwen); the linked source model cards specify Apache-2.0.
- SmolLM2 source model cards and the official 1.7B GGUF repository are published by [Hugging Face Smol Models](https://huggingface.co/HuggingFaceTB); the linked pages specify Apache-2.0.
- The mobile runtime uses the pinned [`llama.cpp`](https://github.com/ggml-org/llama.cpp) submodule; its project is MIT-licensed. Other dependencies retain their own licenses—review them before distribution.
- Model weights are downloaded by user choice and are not included in the APK/AAB.

## Play Store release checklist

Before publishing, verify the exact model and GGUF repository licenses and required notices, include appropriate third-party attribution in the app/store listing, prepare an accurate public privacy policy and Data safety declaration, disclose that responses are generated by an on-device AI and may be wrong, and review current Google Play policy requirements. App changes alone cannot guarantee legal clearance or Play Store approval.

## Build locally

```bash
cd gradle
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

## GitHub Actions

Every push to `main` builds `app-debug.apk` and `app-release.aab`. The AAB is uploaded as an artifact for Play Console publishing. To sign a production AAB, add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` repository secrets; otherwise the workflow creates an unsigned release bundle and clearly labels it.

Release signing keys are **not** stored in this repository. Use an RSA-2048 key and keep a secure backup of the production signing key.

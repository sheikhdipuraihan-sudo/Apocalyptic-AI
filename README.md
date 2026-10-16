# Apocalyptic AI

**A private, on-device survival and emergency-preparedness assistant.** After a user downloads a small model, inference runs on the Android device. The app does not bundle model weights. Outbound internet access is used for a user-requested model download and for the optional donation link.

## Product features

- Android chat with saved conversations that can be reopened or deleted from the sidebar.
- Optional on-device voice dictation (Android 12+ with an installed offline speech model) and optional spoken replies through the system voice engine.
- A visible **Stop** control while the model is generating, with a cancellable native inference loop.
- On-device chat persistence, model download progress and validation, dark/light/system appearance, and a private go-bag checklist.
- Working inference controls: response length, context window, sampling temperature, custom persona/style instructions, restored-chat context, and optional background model unloading.
- A full **Privacy Policy** and AI/safety notice available inside Settings. Voice input shows a clear first-use disclosure before microphone access.
- Subtle screen transitions with a reduced-motion switch. GGUF chat templates are read from model metadata; the Android native build currently uses CPU backends only.
- Safety-oriented survival guidance for water, shelter, first aid, food safety, navigation, communications, and essential supplies. The app rejects explicit requests for weapons, explosives, traps, and harming people.
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

Weights are kept in the app’s private Downloads directory and tracked with Android DownloadManager. The app checks available storage and validates the downloaded GGUF before enabling chat. Wi-Fi is recommended. The app does not send chat content to a cloud inference server.

## Safety and privacy

- AI output can be inaccurate, incomplete, or unsafe. Do not rely on the model as an emergency service, doctor, or substitute for official local guidance. Contact local emergency services when appropriate.
- The app is a general preparedness aid, not medical, legal, or professional advice. Follow local law, official emergency instructions, and trained professional guidance.
- Chat history and model files are stored on the device in app-private storage. Users can clear the active chat or remove downloaded models from the app. Android backup is disabled in the manifest.
- The app uses outbound internet for model downloads and opening the donation website; it does not send prompts to a remote inference service.
- Microphone access is requested only after the user taps Mic and accepts the in-app disclosure. Voice recognition uses the device's on-device speech service; spoken replies use the installed system TTS engine, whose offline behavior depends on the selected voice.

## Model attribution and third-party licenses

- Qwen3 and Qwen2.5 model family/source licensing is published by [Qwen](https://huggingface.co/Qwen); the linked source model cards specify Apache-2.0.
- SmolLM2 source model cards and the official 1.7B GGUF repository are published by [Hugging Face Smol Models](https://huggingface.co/HuggingFaceTB); the linked pages specify Apache-2.0.
- The mobile runtime uses the pinned [`llama.cpp`](https://github.com/ggml-org/llama.cpp) submodule; its project is MIT-licensed. Other dependencies retain their own licenses—review them before distribution.
- Model weights are downloaded by user choice and are not included in the APK/AAB.

## Play Store release checklist

Before submitting a new app or update, target **Android 16 (API level 36) or higher**; Google Play's deadline began August 31, 2026. Test the app on supported devices and review the Android 16 behavior changes. Publish this policy at an active public URL and enter it in Play Console. Complete accurate Data safety, content rating, target-audience, ads, and app-access declarations. The app's central feature is a text chatbot, so review Google's [AI-Generated Content policy](https://support.google.com/googleplay/android-developer/answer/14094294?hl=en) and ensure prohibited output is prevented. Verify every model/GGUF/library license and the listing's AI disclosures. Submit an AAB signed with a persistent Play upload key; retain a secure backup. If your personal Play Console account was created after November 13, 2023, Google requires a closed test with at least 12 testers opted in continuously for 14 days, then a production-access request. Store approval remains Google's decision.

## Build locally

```bash
cd gradle
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

## GitHub Actions

Every push to `main` triggers the release workflow, which builds an APK and AAB and publishes them as a GitHub prerelease. Configure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` repository secrets with a persistent upload key. The workflow now fails before building or publishing if any signing secret is missing or the keystore cannot be decoded; it never generates a temporary key.

Release signing keys are **not** stored in this repository. Keep a secure backup of the long-lived upload key.

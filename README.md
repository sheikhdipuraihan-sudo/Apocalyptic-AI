# Apocalyptic AI

**A private, on-device survival and emergency-preparedness assistant.** After a user downloads a small model, inference runs on the Android device. The app does not bundle model weights. Outbound internet access is used for a user-requested model download and for the optional donation link.

## Product features

- Android chat with saved conversations that can be reopened or deleted from the sidebar.
- Optional on-device voice dictation (Android 12+ with an installed offline speech model) and optional spoken replies through the system voice engine.
- A visible **Stop** control while the model is generating, with a cancellable native inference loop.
- On-device chat persistence, model download progress and validation, dark/light/system appearance, and a private go-bag checklist.
- A searchable model picker with low-/mid-/high-end hardware tiers, download-size filters and sorting, and installed/not-installed filters.
- Working inference controls: response length, context window, sampling temperature, custom persona/style instructions, restored-chat context, and optional background model unloading.
- A full **Privacy Policy** and AI/safety notice available inside Settings. Voice input shows a clear first-use disclosure before microphone access.
- Subtle screen transitions with a reduced-motion switch. GGUF chat templates are read from model metadata; the Android native build currently uses CPU backends only.
- Safety-oriented survival guidance for water, shelter, first aid, food safety, navigation, communications, and essential supplies. The app rejects explicit requests for weapons, explosives, traps, and harming people.
- Donate action opens [raihan.sites.bd](https://raihan.sites.bd).

## Local model choices

The fast default is **Qwen2.5 0.5B Instruct Q4_K_M** (about 379 MiB). The picker contains 16 on-demand models, from 360M/0.5B low-download choices through 4B models and a very-high-end 7B option: Qwen2.5, Qwen3, Gemma 3 and Gemma 4, Llama 3.2, SmolLM2, DeepSeek R1 Distill, Granite, Phi-4 mini, and Mistral. A model's displayed hardware tier is a rough file-size-based estimate—not a guarantee. The app checks available memory before generation; larger models can still be slow or unavailable on a phone.

Use the picker search and filters for low-/mid-/high-end hardware, small/medium/large downloads, installed/not-installed models, and ascending/descending file size. Exact model cards, GGUF publishers, pinned files, byte sizes, and license caveats are listed in [MODEL_CATALOG.md](MODEL_CATALOG.md). TituLM was removed from the selectable catalog; an upgrade also cancels its old download and removes its app-private cached model file when possible.

The app downloads weights only after the user chooses; it does not redistribute them. Review **both** the upstream model card and the GGUF publisher's terms before use or distribution. Some model cards use custom or gated terms; a public quantized file does not waive them. The Qwen2.5 3B metadata currently reports `other`, so the app does not claim an Apache-2.0 license for that model. This catalog is attribution and engineering documentation, not legal advice or a guarantee of commercial rights.

Weights are kept in the app’s private Downloads directory and tracked with Android DownloadManager. The app checks available storage and validates the downloaded GGUF before enabling chat. Wi-Fi is recommended. The app does not send chat content to a cloud inference server.

## Safety and privacy

- AI output can be inaccurate, incomplete, or unsafe. Do not rely on the model as an emergency service, doctor, or substitute for official local guidance. Contact local emergency services when appropriate.
- The app is a general preparedness aid, not medical, legal, or professional advice. Follow local law, official emergency instructions, and trained professional guidance.
- Chat history and model files are stored on the device in app-private storage. Users can clear the active chat or remove downloaded models from the app. Android backup is disabled in the manifest.
- The app uses outbound internet for model downloads and opening the donation website; it does not send prompts to a remote inference service.
- Microphone access is requested only after the user taps Mic and accepts the in-app disclosure. Voice recognition uses the device's on-device speech service; spoken replies use the installed system TTS engine, whose offline behavior depends on the selected voice.

## Model attribution and third-party licenses

- Optional model sources and individual terms are tracked in [MODEL_CATALOG.md](MODEL_CATALOG.md); model card and GGUF links are also shown in the app's model picker.
- Qwen3, Qwen2.5, SmolLM2, Granite, Phi, Mistral, Gemma, Meta Llama, and DeepSeek retain their respective upstream model licenses/terms. Review each source card; Gemma 3 and Meta Llama cards have special terms, and some are gated.
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

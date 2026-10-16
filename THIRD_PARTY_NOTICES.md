# Third-party notices

This project includes or may download the following third-party components. The app's own source code does not receive a license from the model/runtime licenses below; no app-code license is declared here.

## Runtime included in the app

- **llama.cpp** (pinned git submodule): MIT License. Source and license: <https://github.com/ggml-org/llama.cpp>.
- **AndroidSVG** (`com.caverock:androidsvg-aar:1.4`): Apache License 2.0. Source: <https://github.com/BigBadaboom/androidsvg>.
- **AndroidX Core KTX** (`androidx.core:core-ktx:1.15.0`): Apache License 2.0. Source: <https://developer.android.com/jetpack/androidx>.
- **AndroidX DataStore Preferences** (`androidx.datastore:datastore-preferences:1.1.1`): Apache License 2.0. Source: <https://developer.android.com/jetpack/androidx>.
- **Kotlinx Coroutines Android** (`org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`): Apache License 2.0. Source: <https://github.com/Kotlin/kotlinx.coroutines>.

Review transitive dependencies and their current license/notice files from the resolved release build before publishing. This inventory is not a substitute for preserving required notices from each dependency.

## Optional model downloads (not included in the APK/AAB)

- Qwen3 0.6B and Qwen2.5 0.5B Instruct: upstream Qwen model cards specify Apache-2.0. The app links both the upstream card and the GGUF repository used for the download.
- SmolLM2 360M and 1.7B Instruct: upstream Hugging Face Smol Models cards specify Apache-2.0. The app links both the upstream card and the GGUF repository used for the download.

Model weights are fetched only after a user chooses a download. Check the exact model card, quantization repository, required notices, dataset/trademark terms, and current terms before commercial use or redistribution. The app publisher makes no legal-clearance guarantee.

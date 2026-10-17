package bd.sites.raihan.apocalypticai;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.DownloadManager;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import com.arm.aichat.LocalModelBridge;
import com.caverock.androidsvg.SVG;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {
    // Theme colours (monochrome, black/white). Set by applyTheme().
    private int INK, MUTED, GREEN, GREEN_DARK, GREEN_SOFT, PAPER, CARD, BORDER, ORANGE, ON_ACCENT;
    private boolean darkMode;

    private static final String PREFS = "apocalyptic_ai_prefs";
    private static final String CHAT_KEY = "chat_messages_v2";
    private static final String HISTORY_KEY = "chat_history_v1";
    private static final String ACTIVE_SESSION_KEY = "active_chat_session_id";
    private static final String SPEAK_REPLIES_KEY = "speak_replies_enabled";
    private static final String VOICE_LANGUAGE_KEY = "voice_language";
    private static final String VOICE_DISCLOSURE_MODE_KEY = "voice_disclosure_mode_v2";
    private static final String RESPONSE_LENGTH_KEY = "response_length_mode";
    private static final String RESTORED_CONTEXT_KEY = "restored_chat_context_enabled";
    private static final String CONTEXT_SIZE_KEY = "context_size";
    private static final String TEMPERATURE_KEY = "temperature_mode";
    private static final String AUTO_UNLOAD_KEY = "auto_unload_model";
    private static final String CUSTOM_INSTRUCTIONS_KEY = "custom_assistant_instructions";
    private static final String ANIMATIONS_KEY = "animations_enabled";
    private static final String DOWNLOAD_KEY = "model_download_id";
    private static final String SELECTED_MODEL_KEY = "selected_model_id";
    private static final String RETIRED_TITULM_CLEANUP_KEY = "retired_titulm_model_cleanup_v1";
    private static final String RETIRED_TITULM_MODEL_ID = "titulm-mpt-1b";
    private static final String RETIRED_TITULM_FILE = "titulm-mpt-1b-v2.0.i1-Q4_K_M.gguf";
    private static final String SYSTEM_PROMPT = "You are Apocalyptic AI, a concise on-device preparedness assistant. Give direct, practical safety guidance. Never help with weapons, explosives, traps, or harming people. For emergencies, recommend local emergency services or qualified professionals. Return only the answer; do not reveal private reasoning.";
    private static final String[] KIT_ITEMS = {"Safe drinking water", "Water treatment method", "Shelf-stable food", "First-aid kit", "Essential medicines", "Warm and rain layers", "Light and spare power", "Paper map and compass", "Whistle or signal", "Repair kit and cordage", "Hygiene supplies", "Emergency contacts and plan"};

    private static final class ModelSpec {
        final String id, name, details, fileName, url, attribution, sourceUrl, quantizationUrl, license;
        final long expectedBytes;
        ModelSpec(String id, String name, String details, String fileName, String url, long expectedBytes,
        String attribution, String sourceUrl, String quantizationUrl, String license) {
            this.id = id;
            this.name = name;
            this.details = details;
            this.fileName = fileName;
            this.url = url;
            this.expectedBytes = expectedBytes;
            this.attribution = attribution;
            this.sourceUrl = sourceUrl;
            this.quantizationUrl = quantizationUrl;
            this.license = license;
        }
        int recommendedRamGb() {
            long gib = 1024L * 1024L * 1024L;
            if (expectedBytes <= 768L * 1024L * 1024L) return 4;
            if (expectedBytes <= 1536L * 1024L * 1024L) return 6;
            if (expectedBytes <= 3L * gib) return 8;
            if (expectedBytes <= 5L * gib) return 12;
            return 16;
        }
        String hardwareTier() {
            int ram = recommendedRamGb();
            return ram <= 4 ? "Low-end" : ram <= 6 ? "Mid-range" : ram <= 8 ? "High-end" : "Very high-end";
        }
    }

    private static final String DEFAULT_MODEL_ID = "qwen-05b";
    // Curated mobile GGUF catalog; exact download sizes are checked against immutable Hugging Face revisions.
    private static final ModelSpec[] MODELS = {
            new ModelSpec("qwen-05b", "Qwen2.5 · 0.5B", "Fast multilingual instruct · Q4_K_M",
                    "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/41ba88dbac95fed2528c92514c131d73eb5a174b/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
                    397_808_192L, "Qwen Team", "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct", "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF", "Apache-2.0"),
            new ModelSpec("smollm2-360m", "SmolLM2 · 360M", "Smallest download · compact instruct · Q4_K_M",
                    "SmolLM2-360M-Instruct-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/7be6f65f1db715fe5dc5a4634c0d459b4eed42ec/SmolLM2-360M-Instruct-Q4_K_M.gguf",
                    270_590_880L, "Hugging Face Smol Models", "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct", "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF", "Apache-2.0"),
            new ModelSpec("qwen3-06b", "Qwen3 · 0.6B", "Compact multilingual general-purpose instruct · Q4_K_M",
                    "Qwen_Qwen3-0.6B-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF/resolve/60b85c0e3d8fe0f6474f406922a26d12aca4550d/Qwen_Qwen3-0.6B-Q4_K_M.gguf",
                    484_220_320L, "Qwen Team", "https://huggingface.co/Qwen/Qwen3-0.6B", "https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF", "Apache-2.0"),
            new ModelSpec("gemma3-1b", "Gemma 3 · 1B", "Small multilingual text instruct · Q4_K_M",
                    "gemma-3-1b-it-Q4_K_M.gguf",
                    "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/f9c28bcd85737ffc5aef028638d3341d49869c27/gemma-3-1b-it-Q4_K_M.gguf",
                    806_058_240L, "Google Gemma", "https://huggingface.co/google/gemma-3-1b-it", "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF", "Gemma terms"),
            new ModelSpec("llama32-1b", "Llama 3.2 · 1B", "Compact text instruct · multilingual · Q4_K_M",
                    "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/067b946cf014b7c697f3654f621d577a3e3afd1c/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                    807_694_464L, "Meta Llama", "https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct", "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF", "Llama 3.2 Community License"),
            new ModelSpec("qwen3-17b", "Qwen3 · 1.7B", "Multilingual instruct with optional reasoning · Q4_K_M",
                    "Qwen_Qwen3-1.7B-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/Qwen_Qwen3-1.7B-GGUF/resolve/dcb19155b962dbb6389f4691a982043a8e651022/Qwen_Qwen3-1.7B-Q4_K_M.gguf",
                    1_282_439_584L, "Qwen Team", "https://huggingface.co/Qwen/Qwen3-1.7B", "https://huggingface.co/bartowski/Qwen_Qwen3-1.7B-GGUF", "Apache-2.0"),
            new ModelSpec("deepseek-r1-1p5b", "DeepSeek R1 Distill · 1.5B", "Reasoning-focused · may respond more slowly · Q4_K_M",
                    "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/9cc28b17e86fa2415fcb070f8ee5ec27c965aa61/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                    1_117_320_800L, "DeepSeek", "https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B", "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF", "MIT"),
            new ModelSpec("smollm2-17b", "SmolLM2 · 1.7B", "On-device-oriented instruct · Q4_K_M",
                    "smollm2-1.7b-instruct-q4_k_m.gguf",
                    "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/2d4a76a30b4af41ecd395c35725ac11688d4cfe4/smollm2-1.7b-instruct-q4_k_m.gguf",
                    1_055_609_536L, "Hugging Face Smol Models", "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct", "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF", "Apache-2.0"),
            new ModelSpec("granite31-2b", "Granite 3.1 · 2B", "Compact enterprise instruct · multilingual · Q4_K_M",
                    "granite-3.1-2b-instruct-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/granite-3.1-2b-instruct-GGUF/resolve/e47b8b46c04cede00f9e19d5a846551b14b2efce/granite-3.1-2b-instruct-Q4_K_M.gguf",
                    1_545_295_424L, "IBM Granite", "https://huggingface.co/ibm-granite/granite-3.1-2b-instruct", "https://huggingface.co/bartowski/granite-3.1-2b-instruct-GGUF", "Apache-2.0"),
            new ModelSpec("qwen25-3b", "Qwen2.5 · 3B", "Multilingual instruction, coding and math · Q4_K_M",
                    "qwen2.5-3b-instruct-q4_k_m.gguf",
                    "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/7dabda4d13d513e3e842b20f0d435c732f172cbe/qwen2.5-3b-instruct-q4_k_m.gguf",
                    2_104_932_768L, "Qwen Team", "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct", "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF", "Model-specific terms · see card"),
            new ModelSpec("llama32-3b", "Llama 3.2 · 3B", "Higher-quality text instruct · multilingual · Q4_K_M",
                    "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/5ab33fa94d1d04e903623ae72c95d1696f09f9e8/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                    2_019_377_696L, "Meta Llama", "https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct", "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF", "Llama 3.2 Community License"),
            new ModelSpec("gemma3-4b", "Gemma 3 · 4B", "Multilingual text instruct · Q4_K_M · high-end phone",
                    "gemma-3-4b-it-Q4_K_M.gguf",
                    "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF/resolve/5a3566e716d80f709ed7b79817eaf7733d2a1fce/gemma-3-4b-it-Q4_K_M.gguf",
                    2_489_894_016L, "Google Gemma", "https://huggingface.co/google/gemma-3-4b-it", "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF", "Gemma terms"),
            new ModelSpec("phi4-mini", "Phi-4 mini · 3.8B", "Compact reasoning and multilingual instruct · Q4_K_M",
                    "Phi-4-mini-instruct-Q4_K_M.gguf",
                    "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/78eb92a46fc37e6b524df991ed9aca9bc6aa7b80/Phi-4-mini-instruct-Q4_K_M.gguf",
                    2_491_874_272L, "Microsoft Phi", "https://huggingface.co/microsoft/Phi-4-mini-instruct", "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF", "MIT"),
            new ModelSpec("qwen3-4b", "Qwen3 · 4B", "Multilingual reasoning and general chat · Q4_K_M",
                    "Qwen3-4B-Q4_K_M.gguf",
                    "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/bc640142c66e1fdd12af0bd68f40445458f3869b/Qwen3-4B-Q4_K_M.gguf",
                    2_497_280_256L, "Qwen Team", "https://huggingface.co/Qwen/Qwen3-4B", "https://huggingface.co/Qwen/Qwen3-4B-GGUF", "Apache-2.0"),
            new ModelSpec("gemma4-e2b", "Gemma 4 · E2B", "New mobile-oriented Gemma generation · Q4_K_M · higher-end phone",
                    "gemma-4-E2B-it-Q4_K_M.gguf",
                    "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/0314792d7f1f7e229411f620751375812bb9faf2/gemma-4-E2B-it-Q4_K_M.gguf",
                    3_106_738_272L, "Google Gemma", "https://huggingface.co/google/gemma-4-E2B-it", "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF", "Apache-2.0"),
            new ModelSpec("mistral7b-v03", "Mistral · 7B Instruct v0.3", "Flagship-only · very large download · CPU inference is slow · Q4_K_M",
                    "Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/61fd4167fff3ab01ee1cfe0da183fa27a944db48/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
                    4_372_812_000L, "Mistral AI", "https://huggingface.co/mistralai/Mistral-7B-Instruct-v0.3", "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF", "Apache-2.0")
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<ChatMessage> chat = new ArrayList<>();
    private LinearLayout appRoot;
    private LinearLayout pageBody;
    private LinearLayout messageColumn;
    private ScrollView chatScroll;
    private EditText composer;
    private TextView microphoneButton;
    private ProgressBar modelProgress;
    private TextView modelProgressText;
    private TextView modelPageStatus;
    private TextView modelAction;
    private TextView checklistCountLabel;
    private ProgressBar checklistProgress;
    private FrameLayout navigationOverlay;
    private View navigationScrim;
    private View navigationDrawer;
    private String activeTab = "chat";
    private String selectedModelId = DEFAULT_MODEL_ID;
    private String modelSearchQuery = "";
    private String modelFilter = "all";
    private boolean modelSortDescending;
    private String activeConversationId = "";
    private boolean checklistOpen;
    private long downloadId = -1L;
    private boolean isGenerating;
    private boolean isListening;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private TextView transientNotice;
    private final Runnable dismissTransientNotice = () -> removeTransientNotice();
    private boolean textToSpeechReady;
    private String pendingSpeech = "";
    private String voiceInputBaseText = "";
    private boolean generationStoppedByUser;
    private boolean restoreHistoryContext;
    private int onboardingPage;

    private final Runnable downloadPoller = new Runnable() {
        @Override public void run() {
            refreshDownloadState();
            if (downloadId > 0L && modelPageStatus != null) handler.postDelayed(this, 900L);
        }
    };

    private static final class ChatMessage {
        final String role;
        String text;
        ChatMessage(String role, String text) { this.role = role; this.text = text; }
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private ModelSpec findModel(String id) {
        for (ModelSpec model : MODELS) if (model.id.equals(id)) return model;
        for (ModelSpec model : MODELS) if (DEFAULT_MODEL_ID.equals(model.id)) return model;
        return MODELS[0];
    }

    private ModelSpec selectedModel() { return findModel(selectedModelId); }

    private String modelDownloadKey(String modelId) { return "model_download_id_" + modelId; }

    private long savedDownloadId(String modelId) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String key = modelDownloadKey(modelId);
        if (prefs.contains(key)) return prefs.getLong(key, -1L);
        if ("qwen3-06b".equals(modelId) && prefs.contains(DOWNLOAD_KEY)) {
            long legacyId = prefs.getLong(DOWNLOAD_KEY, -1L);
            prefs.edit().putLong(key, legacyId).remove(DOWNLOAD_KEY).apply();
            return legacyId;
        }
        return -1L;
    }

    private void cleanupRetiredTituModel() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (prefs.getBoolean(RETIRED_TITULM_CLEANUP_KEY, false)) return;
        long retiredDownload = prefs.getLong(modelDownloadKey(RETIRED_TITULM_MODEL_ID), -1L);
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (retiredDownload > 0L && manager != null) manager.remove(retiredDownload);
        File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) directory = getFilesDir();
        File retiredFile = new File(directory, RETIRED_TITULM_FILE);
        boolean removed = !retiredFile.exists() || retiredFile.delete();
        android.content.SharedPreferences.Editor editor = prefs.edit().remove(modelDownloadKey(RETIRED_TITULM_MODEL_ID));
        if (removed) editor.putBoolean(RETIRED_TITULM_CLEANUP_KEY, true);
        editor.apply();
    }

    private void saveDownloadId(String modelId, long id) {
        getPreferences(MODE_PRIVATE).edit().putLong(modelDownloadKey(modelId), id).apply();
    }

    private void clearDownloadId(String modelId) {
        getPreferences(MODE_PRIVATE).edit().remove(modelDownloadKey(modelId)).apply();
        if ("qwen3-06b".equals(modelId)) getPreferences(MODE_PRIVATE).edit().remove(DOWNLOAD_KEY).apply();
    }

    private File getModelFile(ModelSpec model) {
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = getFilesDir();
        return new File(dir, model.fileName);
    }

    private File getModelFile() { return getModelFile(selectedModel()); }

    private boolean validModelFile(ModelSpec model) {
        File file = getModelFile(model);
        if (!file.isFile() || file.length() < model.expectedBytes) return false;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] magic = new byte[4];
            raf.readFully(magic);
            return magic[0] == 'G' && magic[1] == 'G' && magic[2] == 'U' && magic[3] == 'F';
        } catch (Exception ignored) { return false; }
    }

    private boolean validModelFile() { return validModelFile(selectedModel()); }

    private boolean selectedModelReady() {
        return validModelFile() && (downloadId <= 0L || !isDownloadActive());
    }

    private String selectedSystemPrompt() {
        String prompt = SYSTEM_PROMPT + " The selected model is " + selectedModel().attribution + ". Identify it accurately if asked.";
        String custom = getPreferences(MODE_PRIVATE).getString(CUSTOM_INSTRUCTIONS_KEY, "").trim();
        if (!custom.isEmpty()) {
            if (custom.length() > 1200) custom = custom.substring(0, 1200);
            prompt += "\n\nAdditional user style/persona preferences (follow when consistent with the app's safety rules):\n" + custom;
        }
        return prompt;
    }

    private void selectModel(String id) {
        if (isGenerating) {
            toast("Wait for the current response before switching models.");
            return;
        }
        selectedModelId = findModel(id).id;
        getPreferences(MODE_PRIVATE).edit().putString(SELECTED_MODEL_KEY, selectedModelId).apply();
        downloadId = savedDownloadId(selectedModelId);
        activeTab = "model";
        checklistOpen = false;
        showMain();
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTheme();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadChat();
        activeConversationId = getPreferences(MODE_PRIVATE).getString(ACTIVE_SESSION_KEY, "");
        selectedModelId = getPreferences(MODE_PRIVATE).getString(SELECTED_MODEL_KEY, DEFAULT_MODEL_ID);
        selectedModelId = findModel(selectedModelId).id;
        getPreferences(MODE_PRIVATE).edit().putString(SELECTED_MODEL_KEY, selectedModelId).apply();
        cleanupRetiredTituModel();
        downloadId = savedDownloadId(selectedModelId);
        if (!getPreferences(MODE_PRIVATE).getBoolean("onboarding_complete", false)) showOnboarding(0);
        else showMain();
    }

    @Override protected void onResume() {
        super.onResume();
        if (downloadId > 0L) handler.post(downloadPoller);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(downloadPoller);
        if (isListening) stopVoiceInput(true);
        super.onPause();
    }

    @Override protected void onStop() {
        if (getPreferences(MODE_PRIVATE).getBoolean(AUTO_UNLOAD_KEY, false)) LocalModelBridge.unloadModel();
        super.onStop();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(downloadPoller);
        handler.removeCallbacks(dismissTransientNotice);
        removeTransientNotice();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 3107) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startVoiceInput();
            else toast("Microphone permission is needed for voice input.");
        }
    }

    private static final String THEME_KEY = "theme_mode"; // system | light | dark

    private String themeMode() { return getPreferences(MODE_PRIVATE).getString(THEME_KEY, "system"); }

    private void applyTheme() {
        String mode = themeMode();
        boolean systemDark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        darkMode = "dark".equals(mode) || ("system".equals(mode) && systemDark);
        if (darkMode) {
            INK = Color.rgb(242, 242, 240);
            MUTED = Color.rgb(154, 154, 150);
            GREEN = Color.rgb(242, 242, 240);
            GREEN_DARK = Color.WHITE;
            GREEN_SOFT = Color.rgb(42, 42, 42);
            PAPER = Color.rgb(22, 22, 22);
            CARD = Color.rgb(33, 33, 33);
            BORDER = Color.rgb(52, 52, 52);
            ORANGE = Color.rgb(138, 138, 134);
            ON_ACCENT = Color.rgb(22, 22, 22);
        } else {
            INK = Color.rgb(52, 50, 45);
            MUTED = Color.rgb(131, 130, 126);
            GREEN = Color.rgb(26, 26, 26);
            GREEN_DARK = Color.rgb(26, 26, 26);
            GREEN_SOFT = Color.rgb(237, 237, 235);
            PAPER = Color.rgb(248, 248, 247);
            CARD = Color.WHITE;
            BORDER = Color.rgb(229, 229, 227);
            ORANGE = Color.rgb(138, 138, 134);
            ON_ACCENT = Color.WHITE;
        }
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(PAPER));
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(CARD);
        int flags = View.SYSTEM_UI_FLAG_VISIBLE;
        if (!darkMode) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private String themeLabel() {
        String m = themeMode();
        return "dark".equals(m) ? "Dark" : "light".equals(m) ? "Light" : "System default";
    }

    private int choiceIndex(String[] values, String selected) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) return i;
        return 0;
    }

    private void cycleTheme() {
        String[] values = {"system", "light", "dark"};
        showChoiceDialog("Appearance", "Choose the app theme.", new String[]{"Follow device", "Light", "Dark"},
                choiceIndex(values, themeMode()), index -> {
                    getPreferences(MODE_PRIVATE).edit().putString(THEME_KEY, values[index]).apply();
                    applyTheme();
                    showMain();
                });
    }

    private int contextSize() { return getPreferences(MODE_PRIVATE).getInt(CONTEXT_SIZE_KEY, 4096); }
    private String contextSizeLabel() { return String.format(Locale.US, "%,d requested tokens · model may cap it", contextSize()); }
    private void cycleContextSize() {
        int current = contextSize();
        int selected = current == 2048 ? 0 : current == 4096 ? 1 : 2;
        showChoiceDialog("Context window", "Larger windows use more RAM. The model may enforce a lower limit.",
                new String[]{"2,048 tokens · lightest", "4,096 tokens · balanced", "8,192 tokens · highest memory"}, selected, index -> {
                    int next = index == 0 ? 2048 : index == 1 ? 4096 : 8192;
                    getPreferences(MODE_PRIVATE).edit().putInt(CONTEXT_SIZE_KEY, next).apply();
                    toast("Context window: " + String.format(Locale.US, "%,d", next) + ". It applies when the model next loads.");
                    showMain();
                });
    }

    private String temperatureMode() { return getPreferences(MODE_PRIVATE).getString(TEMPERATURE_KEY, "balanced"); }
    private float temperatureValue() {
        String mode = temperatureMode();
        return "precise".equals(mode) ? 0.2f : "creative".equals(mode) ? 0.8f : 0.3f;
    }
    private String temperatureLabel() {
        String mode = temperatureMode();
        String name = "precise".equals(mode) ? "Precise" : "creative".equals(mode) ? "Creative" : "Balanced";
        return name + " · " + String.format(Locale.US, "%.1f", temperatureValue());
    }
    private void cycleTemperature() {
        String[] values = {"precise", "balanced", "creative"};
        showChoiceDialog("Temperature", "Lower values are more focused; higher values vary more.",
                new String[]{"Precise · 0.2", "Balanced · 0.3", "Creative · 0.8"}, choiceIndex(values, temperatureMode()), index -> {
                    getPreferences(MODE_PRIVATE).edit().putString(TEMPERATURE_KEY, values[index]).apply();
                    toast("Sampling: " + values[index]);
                    showMain();
                });
    }

    private boolean animationsEnabled() { return getPreferences(MODE_PRIVATE).getBoolean(ANIMATIONS_KEY, true); }
    private void toggleAnimations() {
        int selected = animationsEnabled() ? 0 : 1;
        showChoiceDialog("Motion", "This setting controls page and sidebar transitions.", new String[]{"On · subtle motion", "Reduced · no transitions"}, selected, index -> {
            boolean enabled = index == 0;
            getPreferences(MODE_PRIVATE).edit().putBoolean(ANIMATIONS_KEY, enabled).apply();
            toast(enabled ? "Subtle screen animations enabled." : "Animations reduced.");
            showMain();
        });
    }
    private void toggleAutoUnload() {
        int selected = getPreferences(MODE_PRIVATE).getBoolean(AUTO_UNLOAD_KEY, false) ? 0 : 1;
        showChoiceDialog("Auto-unload model", "Free model memory while Apocalyptic AI is in the background, then load again for the next chat.", new String[]{"On · unload in background", "Off · keep loaded"}, selected, index -> {
            boolean enabled = index == 0;
            getPreferences(MODE_PRIVATE).edit().putBoolean(AUTO_UNLOAD_KEY, enabled).apply();
            toast(enabled ? "The model will unload in the background." : "The model will stay loaded in the background.");
            showMain();
        });
    }

    private void showAssistantInstructions() {
        Dialog dialog = new Dialog(this);
        LinearLayout shell = brandedDialogShell("Assistant persona & instructions", "Set a name, role, or tone. Core safety rules remain in effect.");
        EditText editor = new EditText(this);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setMinLines(4);
        editor.setMaxLines(8);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setTextColor(INK);
        editor.setHintTextColor(MUTED);
        editor.setBackground(round(PAPER, 12));
        editor.setHint("Example: Use short bullet points; write in a calm, practical tone.");
        editor.setText(getPreferences(MODE_PRIVATE).getString(CUSTOM_INSTRUCTIONS_KEY, ""));
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, dp(150));
        editorParams.topMargin = dp(14);
        shell.addView(editor, editorParams);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView reset = action("Reset", false);
        TextView cancel = action("Cancel", false);
        TextView save = action("Save", true);
        reset.setOnClickListener(v -> {
            getPreferences(MODE_PRIVATE).edit().remove(CUSTOM_INSTRUCTIONS_KEY).apply();
            dialog.dismiss(); toast("Assistant instructions reset."); showMain();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String value = editor.getText().toString().trim();
            if (value.length() > 1200) { toast("Keep assistant instructions under 1,200 characters."); return; }
            getPreferences(MODE_PRIVATE).edit().putString(CUSTOM_INSTRUCTIONS_KEY, value).apply();
            dialog.dismiss(); toast("Instructions saved; applied to your next reply."); showMain();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        buttons.addView(reset, resetParams);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        cancelParams.leftMargin = dp(6); buttons.addView(cancel, cancelParams);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        saveParams.leftMargin = dp(6); buttons.addView(save, saveParams);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, -2);
        buttonsParams.topMargin = dp(12); shell.addView(buttons, buttonsParams);
        dialog.setContentView(shell);
        dialog.setCancelable(true);
        showBrandedDialog(dialog);
    }

    private void showOnboarding(int page) {
        onboardingPage = page;
        appRoot = new LinearLayout(this);
        appRoot.setOrientation(LinearLayout.VERTICAL);
        appRoot.setBackgroundColor(PAPER);
        appRoot.setPadding(dp(24), dp(18), dp(24), dp(22));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = label("APOCALYPTIC AI", 12, GREEN_DARK);
        mark.setTypeface(null, Typeface.BOLD);
        top.addView(mark, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView skip = label(page < 2 ? "Skip" : "", 14, MUTED);
        skip.setGravity(Gravity.CENTER);
        skip.setOnClickListener(v -> finishOnboarding());
        top.addView(skip, new LinearLayout.LayoutParams(dp(56), dp(42)));
        appRoot.addView(top);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(0, dp(26), 0, dp(22));
        String[] titles = {"Prepared for the uncertain.", "A guide that works offline.", "Choose your local AI."};
        String[] descriptions = {
                "A private, local AI for survival planning and everyday emergency readiness.",
                "Your chat runs on this phone after a model download. No chat messages are sent to a server.",
                "Choose a small Apache-2.0 model. A larger 1.7B option is available for newer phones."
        };
        TextView title = label(titles[page], 29, INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setLineSpacing(dp(2), 1.0f);
        content.addView(title);
        TextView description = label(descriptions[page], 16, MUTED);
        description.setLineSpacing(dp(5), 1.0f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = dp(14);
        content.addView(description, descParams);
        if (page == 2) {
            LinearLayout privacy = card();
            TextView note = label("Model weights download only after you choose. Chat stays on this phone; the model publisher’s license is shown in Models.", 14, GREEN_DARK);
            note.setLineSpacing(dp(3), 1.0f);
            privacy.addView(note);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
            noteParams.topMargin = dp(22);
            content.addView(privacy, noteParams);
        }
        scroll.addView(content);
        appRoot.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            dot.setBackground(round(i == page ? GREEN : BORDER, 8));
            LinearLayout.LayoutParams dpms = new LinearLayout.LayoutParams(dp(i == page ? 24 : 8), dp(8));
            dpms.setMargins(dp(4), 0, dp(4), dp(18));
            dots.addView(dot, dpms);
        }
        appRoot.addView(dots);
        TextView next = action(page == 2 ? "Open Apocalyptic AI" : "Continue", true);
        next.setOnClickListener(v -> { if (onboardingPage == 2) finishOnboarding(); else showOnboarding(onboardingPage + 1); });
        appRoot.addView(next, new LinearLayout.LayoutParams(-1, dp(54)));
        setContentView(appRoot);
    }

    private void finishOnboarding() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("onboarding_complete", true).apply();
        showMain();
    }

    private void showMain() {
        if (navigationScrim != null) navigationScrim.animate().cancel();
        if (navigationDrawer != null) navigationDrawer.animate().cancel();
        appRoot = new LinearLayout(this);
        appRoot.setOrientation(LinearLayout.VERTICAL);
        appRoot.setBackgroundColor(PAPER);
        appRoot.setPadding(0, 0, 0, 0);
        navigationOverlay = null;
        navigationScrim = null;
        navigationDrawer = null;

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(14), dp(2), dp(14), dp(2));
        boolean wideLayout = getResources().getConfiguration().screenWidthDp >= 640;
        if (!wideLayout) {
            ImageView menu = roundImageIcon(R.drawable.ic_menu, "Open navigation");
            menu.setOnClickListener(v -> toggleNavigationDrawer());
            toolbar.addView(menu);
        }
        String pageTitle = "chat".equals(activeTab) ? "Chat" : "model".equals(activeTab) ? "Models" : "Settings";
        TextView title = label(pageTitle, 17, INK);
        title.setTypeface(null, Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        if ("chat".equals(activeTab)) {
            TextView newChat = roundIcon("＋", "Start a new chat");
            newChat.setOnClickListener(v -> newChat());
            toolbar.addView(newChat);
        }
        appRoot.addView(toolbar);

        LinearLayout contentShell = new LinearLayout(this);
        contentShell.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout pageFrame = new FrameLayout(this);
        pageBody = new LinearLayout(this);
        pageBody.setOrientation(LinearLayout.VERTICAL);
        pageBody.setPadding(dp(14), dp(8), dp(14), dp(4));
        int sideWidthDp = wideLayout ? 220 : 0;
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        int bodyWidthDp = wideLayout
                ? Math.min(920, Math.max(360, screenWidthDp - sideWidthDp - 24))
                : Math.max(1, screenWidthDp);
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(dp(bodyWidthDp), -1, Gravity.CENTER_HORIZONTAL);
        pageFrame.addView(pageBody, bodyParams);
        if (wideLayout) {
            contentShell.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(sideWidthDp), -1));
            contentShell.addView(pageFrame, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            contentShell.addView(pageFrame, new LinearLayout.LayoutParams(-1, -1));
        }
        appRoot.addView(contentShell, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout screen = new FrameLayout(this);
        screen.addView(appRoot, new FrameLayout.LayoutParams(-1, -1));
        if (!wideLayout) {
            navigationOverlay = new FrameLayout(this);
            navigationOverlay.setVisibility(View.GONE);
            navigationScrim = new View(this);
            navigationScrim.setAlpha(0f);
            navigationScrim.setBackgroundColor(Color.argb(145, 0, 0, 0));
            navigationScrim.setOnClickListener(v -> closeNavigationDrawer());
            navigationOverlay.addView(navigationScrim, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout drawer = buildSidebar();
            navigationDrawer = drawer;
            drawer.setTranslationX(-dp(300));
            FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(300), -1, Gravity.START);
            navigationOverlay.addView(drawer, drawerParams);
            screen.addView(navigationOverlay, new FrameLayout.LayoutParams(-1, -1));
        }
        setContentView(screen);
        renderPage();
    }

    private void toggleNavigationDrawer() {
        if (navigationOverlay == null) return;
        if (navigationOverlay.getVisibility() == View.VISIBLE) closeNavigationDrawer();
        else openNavigationDrawer();
    }

    private void openNavigationDrawer() {
        if (navigationOverlay == null || navigationDrawer == null || navigationScrim == null) return;
        navigationDrawer.animate().cancel();
        navigationScrim.animate().cancel();
        navigationOverlay.setVisibility(View.VISIBLE);
        if (!animationsEnabled()) {
            navigationDrawer.setTranslationX(0f);
            navigationScrim.setAlpha(1f);
            return;
        }
        navigationDrawer.setTranslationX(-dp(300));
        navigationScrim.setAlpha(0f);
        navigationScrim.animate().alpha(1f).setDuration(180).start();
        navigationDrawer.animate().translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void closeNavigationDrawer() {
        if (navigationOverlay == null || navigationDrawer == null || navigationScrim == null) return;
        navigationDrawer.animate().cancel();
        navigationScrim.animate().cancel();
        if (!animationsEnabled()) {
            navigationDrawer.setTranslationX(-dp(300));
            navigationScrim.setAlpha(0f);
            navigationOverlay.setVisibility(View.GONE);
            return;
        }
        View closingDrawer = navigationDrawer;
        FrameLayout closingOverlay = navigationOverlay;
        navigationScrim.animate().alpha(0f).setDuration(150).start();
        navigationDrawer.animate().translationX(-dp(300)).setDuration(180)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    if (navigationOverlay == closingOverlay && navigationDrawer == closingDrawer)
                        closingOverlay.setVisibility(View.GONE);
                }).start();
    }

    private LinearLayout buildSidebar() {
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(10), dp(12), dp(10), dp(12));
        sidebar.setBackgroundColor(CARD);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(dp(8), dp(5), dp(8), dp(13));
        TextView brand = label("Apocalyptic AI", 15, INK);
        brand.setTypeface(null, Typeface.BOLD);
        brandRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        sidebar.addView(brandRow);

        ScrollView scroll = new ScrollView(this);
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        addSidebarItem(items, "new", "New chat", "chat.svg", R.drawable.ic_nav_chat);
        addSidebarItem(items, "chat", "Chat", "chat.svg", R.drawable.ic_nav_chat);
        addSidebarItem(items, "model", "Models", "model.svg", R.drawable.ic_nav_model);
        addSidebarItem(items, "settings", "Settings", "settings.svg", R.drawable.ic_nav_settings);

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(dp(10), dp(10), dp(10), dp(10));
        items.addView(divider, dividerParams);
        TextView historyHeading = label("YOUR CHATS", 10, MUTED);
        historyHeading.setTypeface(null, Typeface.BOLD);
        historyHeading.setLetterSpacing(0.08f);
        historyHeading.setPadding(dp(10), dp(5), dp(10), dp(8));
        items.addView(historyHeading);
        try {
            JSONArray history = new JSONArray(getPreferences(MODE_PRIVATE).getString(HISTORY_KEY, "[]"));
            if (history.length() == 0) {
                TextView empty = label("Saved conversations will appear here.", 12, MUTED);
                empty.setPadding(dp(10), dp(4), dp(10), dp(10));
                items.addView(empty);
            }
            for (int i = 0; i < history.length(); i++) {
                JSONObject entry = history.getJSONObject(i);
                TextView row = label(entry.optString("title", "Preparedness chat"), 13, INK);
                row.setSingleLine(true);
                row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setPadding(dp(10), dp(11), dp(10), dp(11));
                row.setBackground(round(CARD, 10));
                row.setContentDescription("Open chat: " + entry.optString("title", "Preparedness chat") + ". Long press to delete.");
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = dp(2);
                items.addView(row, rp);
                row.setOnClickListener(v -> {
                    closeNavigationDrawer();
                    loadHistoryConversation(entry);
                });
                row.setOnLongClickListener(v -> { confirmDeleteHistoryConversation(entry); return true; });
            }
        } catch (Exception ignored) {
            TextView empty = label("Chat history could not be loaded.", 12, MUTED);
            empty.setPadding(dp(10), dp(4), dp(10), dp(10));
            items.addView(empty);
        }
        scroll.addView(items);
        sidebar.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addSidebarItem(sidebar, "donate", "Donate", null, R.drawable.ic_donate);
        return sidebar;
    }

    private void addSidebarItem(LinearLayout sidebar, String id, String title, String svgAsset, int iconResource) {
        boolean selected = activeTab.equals(id) && !checklistOpen;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(round(selected ? GREEN_SOFT : CARD, 12));
        ImageView icon = new ImageView(this);
        if ("donate".equals(id)) {
            icon.setImageResource(iconResource);
            icon.setColorFilter(MUTED);
        } else {
            setSvgIcon(icon, svgAsset, iconResource, selected ? GREEN : MUTED);
        }
        icon.setContentDescription(title);
        row.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));
        TextView name = label(title, 14, selected ? GREEN_DARK : INK);
        if (selected) name.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.leftMargin = dp(12);
        row.addView(name, nameParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(3);
        sidebar.addView(row, rowParams);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            closeNavigationDrawer();
            if ("donate".equals(id)) openDonate();
            else if ("new".equals(id)) newChat();
            else {
                checklistOpen = false;
                activeTab = id;
                showMain();
            }
        });
    }

    private void renderPage() {
        pageBody.removeAllViews();
        modelProgress = null;
        modelProgressText = null;
        modelPageStatus = null;
        modelAction = null;
        if (checklistOpen) renderChecklist();
        else if ("chat".equals(activeTab)) renderChat();
        else if ("model".equals(activeTab)) renderModel();
        else renderSettings();
        if (animationsEnabled()) {
            pageBody.setAlpha(0f);
            pageBody.setTranslationY(dp(6));
            pageBody.animate().alpha(1f).translationY(0f).setDuration(170).start();
        } else {
            pageBody.animate().cancel();
            pageBody.setAlpha(1f);
            pageBody.setTranslationY(0f);
        }
    }

    private void setSvgIcon(ImageView target, String assetName, int fallbackResource, int tint) {
        try {
            SVG svg = SVG.getFromAsset(getAssets(), "icons/" + assetName);
            svg.setDocumentWidth(dp(24));
            svg.setDocumentHeight(dp(24));
            PictureDrawable drawable = new PictureDrawable(svg.renderToPicture());
            drawable.setBounds(0, 0, dp(24), dp(24));
            target.setImageDrawable(drawable);
        } catch (Exception ignored) {
            target.setImageResource(fallbackResource);
        }
        target.setColorFilter(tint);
    }

    private void renderChat() {
        TextView title = label("Survival, with local AI.", 23, INK);
        title.setTypeface(null, Typeface.BOLD);
        pageBody.addView(title);
        TextView subtitle = label("Ask about water, shelter, first aid, supplies, and safer plans.", 13, MUTED);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(4);
        subtitleParams.bottomMargin = dp(12);
        pageBody.addView(subtitle, subtitleParams);

        LinearLayout state = new LinearLayout(this);
        state.setGravity(Gravity.CENTER_VERTICAL);
        state.setPadding(dp(12), dp(10), dp(12), dp(10));
        state.setBackground(round(CARD, 14));
        boolean ready = selectedModelReady();
        TextView stateIcon = label(ready ? "●" : "○", 13, ready ? GREEN : ORANGE);
        state.addView(stateIcon);
        TextView stateText = label(ready ? selectedModel().name + " ready · on this phone" : "Choose a local model to start", 12, INK);
        LinearLayout.LayoutParams stateTextParams = new LinearLayout.LayoutParams(0, -2, 1);
        stateTextParams.leftMargin = dp(8);
        state.addView(stateText, stateTextParams);
        TextView openModel = label(ready ? "Models" : "Set up", 12, GREEN);
        openModel.setTypeface(null, Typeface.BOLD);
        openModel.setOnClickListener(v -> { activeTab = "model"; showMain(); });
        state.addView(openModel);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.bottomMargin = dp(12);
        pageBody.addView(state, stateParams);

        if (!ready) {
            LinearLayout setup = card();
            setup.setGravity(Gravity.CENTER);
            TextView setupTitle = label("Your assistant runs on this device", 18, INK);
            setupTitle.setTypeface(null, Typeface.BOLD);
            setupTitle.setGravity(Gravity.CENTER);
            setup.addView(setupTitle);
            TextView setupCopy = label("Download a compact model once. Your chat stays local; an internet connection is only used for downloads and the donation link.", 13, MUTED);
            setupCopy.setGravity(Gravity.CENTER);
            setupCopy.setLineSpacing(dp(3), 1.0f);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
            copyParams.topMargin = dp(7);
            setup.addView(setupCopy, copyParams);
            TextView choose = action("Choose a local model", true);
            LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, dp(50));
            chooseParams.topMargin = dp(15);
            setup.addView(choose, chooseParams);
            choose.setOnClickListener(v -> { activeTab = "model"; showMain(); });
            TextView checklist = action("Open my go-bag checklist", false);
            LinearLayout.LayoutParams checklistParams = new LinearLayout.LayoutParams(-1, dp(48));
            checklistParams.topMargin = dp(8);
            setup.addView(checklist, checklistParams);
            checklist.setOnClickListener(v -> openChecklist());
            pageBody.addView(setup, new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        messageColumn = new LinearLayout(this);
        messageColumn.setOrientation(LinearLayout.VERTICAL);
        messageColumn.setPadding(0, dp(4), 0, dp(14));
        chatScroll.addView(messageColumn);
        pageBody.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (chat.isEmpty()) addMessage("assistant", "Hi, I’m Apocalyptic AI. I can help you prepare calmly and safely. Ask about water, shelter, first aid, or a practical supplies plan.");
        else for (ChatMessage message : chat) addMessageView(message);

        if (chat.size() <= 1 && !isGenerating) {
            LinearLayout prompts = new LinearLayout(this);
            prompts.setOrientation(LinearLayout.VERTICAL);
            String[][] choices = {{"Water", "How can I make uncertain water safer?"}, {"First aid", "What should I do for severe bleeding?"}, {"Go-bag", "What belongs in a basic go-bag?"}};
            for (String[] choice : choices) {
                TextView prompt = label(choice[0] + "     ›", 13, GREEN_DARK);
                prompt.setPadding(dp(12), dp(8), dp(12), dp(8));
                prompt.setBackground(round(CARD, 12));
                LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
                pp.bottomMargin = dp(6);
                prompts.addView(prompt, pp);
                prompt.setOnClickListener(v -> { composer.setText(choice[1]); sendMessage(); });
            }
            LinearLayout.LayoutParams promptsParams = new LinearLayout.LayoutParams(-1, -2);
            promptsParams.bottomMargin = dp(7);
            pageBody.addView(prompts, promptsParams);
        }
        LinearLayout composeRow = new LinearLayout(this);
        composeRow.setGravity(Gravity.CENTER_VERTICAL);
        composeRow.setPadding(0, dp(7), 0, dp(8));
        composer = new EditText(this);
        composer.setTextSize(15);
        composer.setTextColor(INK);
        composer.setHintTextColor(MUTED);
        composer.setHint(isGenerating ? "Response in progress…" : "Ask your local survival guide…");
        composer.setSingleLine(false);
        composer.setMaxLines(4);
        composer.setPadding(dp(15), dp(10), dp(12), dp(10));
        composer.setBackground(round(CARD, 20));
        composeRow.addView(composer, new LinearLayout.LayoutParams(0, dp(52), 1));
        microphoneButton = label(isListening ? "Stop" : "Mic", 12, GREEN_DARK);
        microphoneButton.setTypeface(null, Typeface.BOLD);
        microphoneButton.setGravity(Gravity.CENTER);
        microphoneButton.setContentDescription(isListening ? "Stop voice input" : "Start on-device voice input");
        microphoneButton.setBackground(round(GREEN_SOFT, 18));
        microphoneButton.setEnabled(!isGenerating);
        LinearLayout.LayoutParams microphoneParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        microphoneParams.leftMargin = dp(6);
        composeRow.addView(microphoneButton, microphoneParams);
        microphoneButton.setOnClickListener(v -> { if (isListening) stopVoiceInput(false); else requestVoiceInput(); });
        TextView send = label(isGenerating ? "Stop" : "Send", 13, ON_ACCENT);
        send.setTypeface(null, Typeface.BOLD);
        send.setGravity(Gravity.CENTER);
        send.setContentDescription(isGenerating ? "Stop response" : "Send message");
        send.setBackground(round(GREEN, 20));
        send.setClickable(true);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(58), dp(50));
        sendParams.leftMargin = dp(8);
        composeRow.addView(send, sendParams);
        send.setOnClickListener(v -> { if (isGenerating) stopGeneration(); else sendMessage(); });
        pageBody.addView(composeRow);
    }

    private void renderChecklist() {
        TextView back = label("‹   Chat", 14, GREEN_DARK);
        back.setPadding(0, dp(5), 0, dp(12));
        back.setOnClickListener(v -> { checklistOpen = false; activeTab = "chat"; renderPage(); });
        pageBody.addView(back);
        TextView title = label("My go-bag", 25, INK);
        title.setTypeface(null, Typeface.BOLD);
        pageBody.addView(title);
        TextView subtitle = label("A personal checklist, saved only on this device.", 13, MUTED);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(4);
        subtitleParams.bottomMargin = dp(12);
        pageBody.addView(subtitle, subtitleParams);

        LinearLayout summary = card();
        checklistCountLabel = label(checklistSummary(), 14, GREEN_DARK);
        checklistCountLabel.setTypeface(null, Typeface.BOLD);
        summary.addView(checklistCountLabel);
        checklistProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        checklistProgress.setMax(KIT_ITEMS.length);
        checklistProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        checklistProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BORDER));
        checklistProgress.setProgress(checklistCompletedCount());
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(7));
        progressParams.topMargin = dp(10);
        summary.addView(checklistProgress, progressParams);
        pageBody.addView(summary);

        ScrollView scroll = new ScrollView(this);
        LinearLayout checklist = new LinearLayout(this);
        checklist.setOrientation(LinearLayout.VERTICAL);
        checklist.setPadding(0, dp(3), 0, dp(10));
        for (int i = 0; i < KIT_ITEMS.length; i++) {
            final int itemIndex = i;
            final boolean[] checked = {getPreferences(MODE_PRIVATE).getBoolean("kit_checked_" + i, false)};
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setMinimumHeight(dp(52));
            item.setBackground(round(CARD, 12));
            item.setClickable(true);
            item.setFocusable(true);
            TextView checkmark = label(checked[0] ? "✓" : "", 15, Color.WHITE);
            checkmark.setGravity(Gravity.CENTER);
            checkmark.setBackground(round(checked[0] ? GREEN : BORDER, 7));
            item.addView(checkmark, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView itemText = label(KIT_ITEMS[i], 14, INK);
            LinearLayout.LayoutParams itemTextParams = new LinearLayout.LayoutParams(0, -2, 1);
            itemTextParams.leftMargin = dp(12);
            item.addView(itemText, itemTextParams);
            item.setContentDescription((checked[0] ? "Packed: " : "Not packed: ") + KIT_ITEMS[i]);
            item.setOnClickListener(v -> {
                checked[0] = !checked[0];
                checkmark.setText(checked[0] ? "✓" : "");
                checkmark.setBackground(round(checked[0] ? GREEN : BORDER, 7));
                item.setContentDescription((checked[0] ? "Packed: " : "Not packed: ") + KIT_ITEMS[itemIndex]);
                getPreferences(MODE_PRIVATE).edit().putBoolean("kit_checked_" + itemIndex, checked[0]).apply();
                updateChecklistProgress();
            });
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
            itemParams.bottomMargin = dp(7);
            checklist.addView(item, itemParams);
        }
        scroll.addView(checklist);
        pageBody.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView reset = action("Reset checklist", false);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, dp(48));
        resetParams.topMargin = dp(6);
        resetParams.bottomMargin = dp(8);
        pageBody.addView(reset, resetParams);
        reset.setOnClickListener(v -> showBrandedConfirmation("Reset your checklist?",
                "All check marks will be cleared on this device.", "Reset", () -> {
                    android.content.SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
                    for (int i = 0; i < KIT_ITEMS.length; i++) editor.remove("kit_checked_" + i);
                    editor.apply();
                    renderPage();
                }));
    }

    private int checklistCompletedCount() {
        int count = 0;
        for (int i = 0; i < KIT_ITEMS.length; i++) if (getPreferences(MODE_PRIVATE).getBoolean("kit_checked_" + i, false)) count++;
        return count;
    }

    private String checklistSummary() { return checklistCompletedCount() + " of " + KIT_ITEMS.length + " items packed"; }

    private void updateChecklistProgress() {
        int count = checklistCompletedCount();
        if (checklistCountLabel != null) checklistCountLabel.setText(count + " of " + KIT_ITEMS.length + " items packed");
        if (checklistProgress != null) checklistProgress.setProgress(count);
    }

    private void openChecklist() {
        activeTab = "chat";
        checklistOpen = true;
        showMain();
    }

    private void renderModel() {
        TextView title = label("Choose a local model", 23, INK);
        title.setTypeface(null, Typeface.BOLD);
        pageBody.addView(title);
        TextView subtitle = label("Search models and find one that fits your phone", 13, MUTED);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1, -2);
        sub.topMargin = dp(4); sub.bottomMargin = dp(14);
        pageBody.addView(subtitle, sub);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.setTextColor(INK);
        searchInput.setHintTextColor(MUTED);
        searchInput.setHint("Search name, family, license…");
        searchInput.setPadding(dp(12), 0, dp(12), 0);
        searchInput.setBackground(round(CARD, 13));
        searchInput.setText(modelSearchQuery);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { modelSearchQuery = s.toString(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        inputParams.rightMargin = dp(8);
        searchRow.addView(searchInput, inputParams);
        TextView searchButton = action("Search", true);
        searchRow.addView(searchButton, new LinearLayout.LayoutParams(dp(82), dp(48)));
        searchButton.setOnClickListener(v -> {
            modelSearchQuery = searchInput.getText().toString().trim();
            Object inputMethod = getSystemService(INPUT_METHOD_SERVICE);
            if (inputMethod instanceof InputMethodManager) ((InputMethodManager) inputMethod).hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            showMain();
        });
        pageBody.addView(searchRow);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams filterRowParams = new LinearLayout.LayoutParams(-1, -2);
        filterRowParams.topMargin = dp(8);
        filterRowParams.bottomMargin = dp(8);
        TextView filterButton = action(modelFilterButtonLabel(), false);
        LinearLayout.LayoutParams filterButtonParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        filterButtonParams.rightMargin = dp(8);
        filterRow.addView(filterButton, filterButtonParams);
        filterButton.setOnClickListener(v -> openModelFilterDialog());
        TextView sortButton = action(modelSortDescending ? "Largest first" : "Smallest first", false);
        filterRow.addView(sortButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        sortButton.setOnClickListener(v -> openModelSortDialog());
        pageBody.addView(filterRow, filterRowParams);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ArrayList<ModelSpec> visibleModels = modelsMatchingPicker();
        int installedCount = 0;
        for (ModelSpec model : MODELS) if (validModelFile(model)) installedCount++;
        TextView available = label(visibleModels.size() + " of " + MODELS.length + " models match · " + installedCount + " installed", 11, MUTED);
        available.setTypeface(null, Typeface.BOLD);
        available.setLetterSpacing(0.08f);
        content.addView(available);
        if (visibleModels.isEmpty()) {
            TextView empty = label("No models match. Try a different search or filter.", 14, MUTED);
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            content.addView(empty);
        } else {
            for (ModelSpec model : visibleModels) content.addView(modelOption(model));
        }

        LinearLayout hero = card();
        TextView modelName = label(selectedModel().name, 18, INK);
        modelName.setTypeface(null, Typeface.BOLD);
        hero.addView(modelName);
        TextView details = label(selectedModel().details, 13, MUTED);
        LinearLayout.LayoutParams dpms = new LinearLayout.LayoutParams(-1, -2);
        dpms.topMargin = dp(7);
        hero.addView(details, dpms);
        TextView privacy = label("The selected model runs locally after download. Model files stay in this app’s private storage; chat messages are not sent to a server.", 13, INK);
        privacy.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = dp(15);
        hero.addView(privacy, pp);
        TextView modelCardLink = label("Open model card · " + selectedModel().license, 12, GREEN_DARK);
        LinearLayout.LayoutParams licenseParams = new LinearLayout.LayoutParams(-1, -2);
        licenseParams.topMargin = dp(10);
        hero.addView(modelCardLink, licenseParams);
        modelCardLink.setOnClickListener(v -> openExternal(selectedModel().sourceUrl));
        TextView quantizationLink = label("Open GGUF download repository", 12, GREEN_DARK);
        LinearLayout.LayoutParams quantizationParams = new LinearLayout.LayoutParams(-1, -2);
        quantizationParams.topMargin = dp(5);
        hero.addView(quantizationLink, quantizationParams);
        quantizationLink.setOnClickListener(v -> openExternal(selectedModel().quantizationUrl));
        content.addView(hero);

        LinearLayout stateCard = card();
        TextView statusTitle = label("Download status", 15, INK);
        statusTitle.setTypeface(null, Typeface.BOLD);
        stateCard.addView(statusTitle);
        modelPageStatus = label(modelStatusSummary(), 13, MUTED);
        LinearLayout.LayoutParams msp = new LinearLayout.LayoutParams(-1, -2);
        msp.topMargin = dp(8);
        stateCard.addView(modelPageStatus, msp);
        modelProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        modelProgress.setMax(100);
        modelProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        modelProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BORDER));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(7));
        progressParams.topMargin = dp(13);
        stateCard.addView(modelProgress, progressParams);
        modelProgressText = label("", 12, MUTED);
        LinearLayout.LayoutParams mpt = new LinearLayout.LayoutParams(-1, -2);
        mpt.topMargin = dp(6);
        stateCard.addView(modelProgressText, mpt);
        modelAction = action(selectedModelReady() ? "Model ready" : "Download model", true);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(50));
        actionParams.topMargin = dp(14);
        stateCard.addView(modelAction, actionParams);
        modelAction.setOnClickListener(v -> onModelAction());
        content.addView(stateCard);

        TextView note = label("RAM tiers are estimates, not guarantees. Larger models may be slow or need more free memory; the app checks available memory before generation. Weights download only after you choose. Review both the upstream model card and the GGUF publisher’s license before distribution.", 12, MUTED);
        note.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(13);
        content.addView(note, np);
        scroll.addView(content);
        pageBody.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        refreshDownloadState();
        if (downloadId > 0L) {
            handler.removeCallbacks(downloadPoller);
            handler.post(downloadPoller);
        }
    }

    private String modelFilterButtonLabel() {
        if ("low".equals(modelFilter)) return "Filter · Low-end";
        if ("mid".equals(modelFilter)) return "Filter · Mid-range";
        if ("high".equals(modelFilter)) return "Filter · High-end";
        if ("small".equals(modelFilter)) return "Filter · Small files";
        if ("medium".equals(modelFilter)) return "Filter · Medium files";
        if ("large".equals(modelFilter)) return "Filter · Large files";
        if ("installed".equals(modelFilter)) return "Filter · Installed";
        if ("not_installed".equals(modelFilter)) return "Filter · Not installed";
        return "Filter · All models";
    }

    private void openModelFilterDialog() {
        String[] keys = {"all", "low", "mid", "high", "small", "medium", "large", "installed", "not_installed"};
        String[] labels = {"All models", "Low-end phones · up to 4 GB RAM", "Mid-range phones · about 6 GB RAM", "High-end phones · 8 GB+ RAM",
                "Small downloads · up to 512 MiB", "Medium downloads · 512 MiB–2 GiB", "Large downloads · over 2 GiB", "Installed on this device", "Not installed"};
        int checked = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(modelFilter)) checked = i;
        showChoiceDialog("Filter model list", "Narrow models by hardware tier, file size, or download status.", labels, checked, which -> {
            modelFilter = keys[which];
            showMain();
        });
    }

    private void openModelSortDialog() {
        String[] labels = {"Smallest download first", "Largest download first"};
        showChoiceDialog("Sort models by file size", "Choose the file-size order for the catalog.", labels,
                modelSortDescending ? 1 : 0, which -> {
                    modelSortDescending = which == 1;
                    showMain();
                });
    }

    private ArrayList<ModelSpec> modelsMatchingPicker() {
        ArrayList<ModelSpec> matches = new ArrayList<>();
        String query = modelSearchQuery.trim().toLowerCase(Locale.US);
        long mib = 1024L * 1024L;
        long gib = 1024L * mib;
        for (ModelSpec model : MODELS) {
            String searchable = (model.id + " " + model.name + " " + model.details + " " + model.fileName + " " + model.attribution + " " + model.license + " " + model.sourceUrl + " " + model.quantizationUrl + " "
                    + model.hardwareTier() + " " + model.recommendedRamGb() + " GB").toLowerCase(Locale.US);
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            long size = model.expectedBytes;
            boolean installed = validModelFile(model);
            if ("low".equals(modelFilter) && model.recommendedRamGb() > 4) continue;
            if ("mid".equals(modelFilter) && (model.recommendedRamGb() < 5 || model.recommendedRamGb() > 6)) continue;
            if ("high".equals(modelFilter) && model.recommendedRamGb() < 8) continue;
            if ("small".equals(modelFilter) && size > 512L * mib) continue;
            if ("medium".equals(modelFilter) && (size <= 512L * mib || size > 2L * gib)) continue;
            if ("large".equals(modelFilter) && size <= 2L * gib) continue;
            if ("installed".equals(modelFilter) && !installed) continue;
            if ("not_installed".equals(modelFilter) && installed) continue;
            matches.add(model);
        }
        Collections.sort(matches, (left, right) -> modelSortDescending
                ? Long.compare(right.expectedBytes, left.expectedBytes)
                : Long.compare(left.expectedBytes, right.expectedBytes));
        return matches;
    }

    private LinearLayout modelOption(ModelSpec model) {
        boolean selected = model.id.equals(selectedModelId);
        LinearLayout option = card();
        option.setOrientation(LinearLayout.HORIZONTAL);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setBackground(round(selected ? GREEN_SOFT : CARD, 14));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(model.name, 15, INK);
        name.setTypeface(null, Typeface.BOLD);
        text.addView(name);
        TextView details = label(model.details + " · " + formatBytes(model.expectedBytes) + " · " + model.hardwareTier()
                + " · ~" + model.recommendedRamGb() + " GB RAM · " + model.license, 12, MUTED);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(3);
        text.addView(details, detailParams);
        TextView state = label(selected ? "Selected" : validModelFile(model) ? "Installed · select" : "Not installed · select", 11, selected ? GREEN_DARK : MUTED);
        state.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
        option.addView(text, textParams);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
        stateParams.leftMargin = dp(8);
        option.addView(state, stateParams);
        option.setClickable(true);
        option.setOnClickListener(v -> {
            if (!model.id.equals(selectedModelId)) selectModel(model.id);
        });
        return option;
    }

    private void onModelAction() {
        ModelSpec model = selectedModel();
        if (selectedModelReady()) {
            showBrandedConfirmation("Model is ready", model.name + " is saved on this device. Chat responses now run locally.",
                    "Open chat", () -> { activeTab = "chat"; showMain(); });
        } else if (downloadId > 0L && isDownloadActive()) {
            showBrandedConfirmation("Cancel model download?", "The partial model file will be removed.",
                    "Cancel download", () -> cancelDownload());
        } else {
            showBrandedConfirmation("Download " + model.name + "?",
                    "This download is about " + formatBytes(model.expectedBytes) + ". Wi-Fi is recommended. Chat runs locally after download.\n\nModel terms: " + model.license + ". Review the upstream card and GGUF repository terms on the model page before use.",
                    "Download", () -> startModelDownload());
        }
    }

    private void startModelDownload() {
        try {
            ModelSpec model = selectedModel();
            File target = getModelFile();
            if (target.exists() && !validModelFile()) target.delete();
            File directory = target.getParentFile();
            long freeBytes = directory == null ? Long.MAX_VALUE : new StatFs(directory.getAbsolutePath()).getAvailableBytes();
            if (freeBytes < model.expectedBytes + 32L * 1024L * 1024L) {
                toast("Not enough free space. Free at least " + formatBytes(model.expectedBytes + 32L * 1024L * 1024L) + " and retry.");
                return;
            }
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) { toast("Android download service is unavailable."); return; }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(model.url));
            request.setTitle("Apocalyptic AI · " + model.name);
            request.setDescription("Downloading " + model.name + " (" + formatBytes(model.expectedBytes) + ")");
            request.setMimeType("application/octet-stream");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, model.fileName);
            downloadId = manager.enqueue(request);
            saveDownloadId(model.id, downloadId);
            if (modelPageStatus != null) modelPageStatus.setText("Connecting to the model host…");
            if (modelAction != null) modelAction.setText("Cancel download");
            handler.removeCallbacks(downloadPoller);
            handler.post(downloadPoller);
            toast("Model download started. Progress is shown here and in notifications.");
        } catch (Exception e) {
            toast("Could not start download: " + safeMessage(e));
        }
    }

    private void refreshDownloadState() {
        if (modelPageStatus == null) return;
        ModelSpec model = selectedModel();
        if (validModelFile() && (downloadId <= 0L || !isDownloadActive())) {
            modelPageStatus.setText("Ready · file verified as GGUF");
            modelProgress.setProgress(100);
            modelProgressText.setText(formatBytes(getModelFile().length()) + " saved on this device");
            modelAction.setText("Model ready");
            if (downloadId > 0L) clearSelectedDownloadTracking();
            return;
        }
        if (downloadId <= 0L) {
            modelPageStatus.setText("Not downloaded · choose a model to begin local chat");
            modelProgress.setProgress(0);
            modelProgressText.setText("");
            modelAction.setText("Download model");
            return;
        }
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) return;
        Cursor cursor = null;
        try {
            cursor = manager.query(new DownloadManager.Query().setFilterById(downloadId));
            if (cursor == null || !cursor.moveToFirst()) {
                modelPageStatus.setText("Download not found. You can try again.");
                modelProgress.setProgress(0);
                modelProgressText.setText("Start a fresh download to retry.");
                modelAction.setText("Retry download");
                getModelFile().delete();
                clearSelectedDownloadTracking();
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) {
                int percent = total > 0L ? (int) Math.min(100L, downloaded * 100L / total) : 0;
                modelPageStatus.setText(status == DownloadManager.STATUS_PAUSED ? "Paused by Android" : "Downloading…");
                modelProgress.setProgress(percent);
                modelProgressText.setText(total > 0L ? formatBytes(downloaded) + " of " + formatBytes(total) + " · " + percent + "%" : formatBytes(downloaded) + " downloaded");
                modelAction.setText("Cancel download");
            } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                if (validModelFile()) {
                    modelPageStatus.setText("Ready · file verified as GGUF");
                    modelProgress.setProgress(100);
                    modelProgressText.setText(formatBytes(getModelFile().length()) + " saved on this device");
                    modelAction.setText("Model ready");
                } else {
                    modelPageStatus.setText("Download finished, but the file is incomplete or not a GGUF model.");
                    modelProgress.setProgress(0);
                    modelProgressText.setText("Remove the partial file and retry.");
                    modelAction.setText("Retry download");
                    getModelFile().delete();
                }
                clearSelectedDownloadTracking();
            } else if (status == DownloadManager.STATUS_FAILED) {
                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                modelPageStatus.setText("Download failed · " + downloadFailure(reason));
                modelProgress.setProgress(0);
                modelProgressText.setText("Check the connection and free storage, then retry.");
                modelAction.setText("Retry download");
                getModelFile().delete();
                clearSelectedDownloadTracking();
            }
        } catch (Exception e) {
            modelPageStatus.setText("Could not read download status: " + safeMessage(e));
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void clearSelectedDownloadTracking() {
        clearDownloadId(selectedModelId);
        downloadId = -1L;
        handler.removeCallbacks(downloadPoller);
    }

    private boolean isDownloadActive() {
        if (downloadId <= 0L) return false;
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) return false;
        Cursor c = null;
        try {
            c = manager.query(new DownloadManager.Query().setFilterById(downloadId));
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                return status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED;
            }
        } catch (Exception ignored) { }
        finally { if (c != null) c.close(); }
        return false;
    }

    private void cancelDownload() {
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager != null && downloadId > 0L) manager.remove(downloadId);
        getModelFile().delete();
        clearSelectedDownloadTracking();
        refreshDownloadState();
        toast("Download cancelled.");
    }

    private void removeModel() {
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager != null && downloadId > 0L) manager.remove(downloadId);
        getModelFile().delete();
        clearSelectedDownloadTracking();
        refreshDownloadState();
        toast("Model removed from this device.");
    }

    private String modelStatusSummary() {
        if (selectedModelReady()) return "Ready on this device · local inference available";
        if (downloadId > 0L) return "Checking download status…";
        return "Not downloaded · choose a model to begin local chat";
    }

    private boolean onDeviceSpeechAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        try { return SpeechRecognizer.isOnDeviceRecognitionAvailable(this); }
        catch (Exception ignored) { return false; }
    }

    private void requestVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Voice input is unavailable. Install or enable a speech recognition service on this device.");
            return;
        }
        boolean onDevice = onDeviceSpeechAvailable();
        String mode = onDevice ? "on_device" : "system_service";
        if (!mode.equals(getPreferences(MODE_PRIVATE).getString(VOICE_DISCLOSURE_MODE_KEY, ""))) {
            String disclosure = onDevice
                    ? "Android reports that on-device recognition is available. Audio is passed to that system recognizer to create draft text; Apocalyptic AI does not upload recordings to its own server. Availability and handling are controlled by Android and the installed speech component."
                    : "This device does not provide on-device recognition to the app. Dictation will use the installed Android speech service, which may send audio to its provider or require internet; its privacy practices apply. Apocalyptic AI does not upload audio to its own server. Continue only if you are comfortable with the installed provider handling the recording. Tap Mic again to use this service."
                    ;
            showBrandedConfirmation("Voice input & privacy", disclosure, "Continue", () -> {
                getPreferences(MODE_PRIVATE).edit().putString(VOICE_DISCLOSURE_MODE_KEY, mode).apply();
                requestMicrophonePermissionOrStart();
            });
            return;
        }
        requestMicrophonePermissionOrStart();
    }

    private void requestMicrophonePermissionOrStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3107);
            return;
        }
        startVoiceInput();
    }

    private void startVoiceInput() {
        if (isGenerating || isListening) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("No speech recognition service is available on this device.");
            return;
        }
        boolean onDevice = onDeviceSpeechAvailable();
        try {
            voiceInputBaseText = composer == null ? "" : composer.getText().toString().trim();
            speechRecognizer = onDevice
                    ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                    : SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onError(int error) {
                    boolean wasListening = isListening;
                    finishVoiceInput();
                    if (!wasListening) return;
                    toast(error == SpeechRecognizer.ERROR_NO_MATCH ? "No speech was recognized. Try again." : "The speech service could not complete dictation. Try again.");
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        updateVoiceInput(matches.get(0));
                        toast("Voice input added. Review it before sending.");
                    } else toast("No speech was recognized. Try again.");
                    finishVoiceInput();
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) updateVoiceInput(matches.get(0));
                }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguageTag());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            isListening = true;
            if (microphoneButton != null) {
                microphoneButton.setText("Stop");
                microphoneButton.setContentDescription("Stop voice input");
            }
            speechRecognizer.startListening(intent);
            toast(onDevice ? "Listening with on-device recognition… tap Stop when finished."
                    : "Listening with the installed Android speech service… tap Stop when finished.");
        } catch (Exception e) {
            finishVoiceInput();
            toast("Could not start voice input. Check that your speech service is enabled.");
        }
    }

    private void updateVoiceInput(String recognizedText) {
        if (composer == null || recognizedText == null || recognizedText.trim().isEmpty()) return;
        String text = voiceInputBaseText.isEmpty() ? recognizedText.trim() : voiceInputBaseText + " " + recognizedText.trim();
        composer.setText(text);
        composer.setSelection(text.length());
    }

    private void stopVoiceInput(boolean cancel) {
        if (speechRecognizer != null) {
            if (cancel) {
                speechRecognizer.cancel();
                finishVoiceInput();
            } else {
                speechRecognizer.stopListening();
            }
        }
    }

    private void finishVoiceInput() {
        isListening = false;
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (microphoneButton != null) {
            microphoneButton.setText("Mic");
            microphoneButton.setContentDescription("Start voice input");
        }
    }

    private void toggleSpokenReplies() {
        boolean currentlyEnabled = getPreferences(MODE_PRIVATE).getBoolean(SPEAK_REPLIES_KEY, false);
        showChoiceDialog("Spoken replies", "Choose whether completed assistant answers are read aloud using the voice installed on the phone.",
                new String[]{"Off", "On · use installed voice"}, currentlyEnabled ? 1 : 0, index -> {
                    boolean enabled = index == 1;
                    getPreferences(MODE_PRIVATE).edit().putBoolean(SPEAK_REPLIES_KEY, enabled).apply();
                    if (!enabled && textToSpeech != null) textToSpeech.stop();
                    toast(enabled ? "Spoken replies enabled." : "Spoken replies disabled.");
                    showMain();
                });
    }

    private String voiceLanguageTag() {
        String setting = getPreferences(MODE_PRIVATE).getString(VOICE_LANGUAGE_KEY, "system");
        return "system".equals(setting) ? Locale.getDefault().toLanguageTag() : setting;
    }

    private String voiceLanguageLabel() {
        String setting = getPreferences(MODE_PRIVATE).getString(VOICE_LANGUAGE_KEY, "system");
        return "bn-BD".equals(setting) ? "Bangla" : "en-US".equals(setting) ? "English" : "System language";
    }

    private void cycleVoiceLanguage() {
        String[] values = {"system", "bn-BD", "en-US"};
        showChoiceDialog("Voice language", "This selection applies to dictation and spoken replies when supported by installed speech engines.",
                new String[]{"System language", "Bangla · Bangladesh", "English · United States"},
                choiceIndex(values, getPreferences(MODE_PRIVATE).getString(VOICE_LANGUAGE_KEY, "system")), index -> {
                    String next = values[index];
                    getPreferences(MODE_PRIVATE).edit().putString(VOICE_LANGUAGE_KEY, next).apply();
                    if (textToSpeech != null) textToSpeech.setLanguage(Locale.forLanguageTag("system".equals(next) ? Locale.getDefault().toLanguageTag() : next));
                    toast("Voice language: " + voiceLanguageLabel());
                    showMain();
                });
    }

    private void speakAssistantReply(String text) {
        if (!getPreferences(MODE_PRIVATE).getBoolean(SPEAK_REPLIES_KEY, false) || text == null || text.trim().isEmpty()) return;
        pendingSpeech = text.trim();
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                    textToSpeechReady = true;
                    int languageStatus = textToSpeech.setLanguage(Locale.forLanguageTag(voiceLanguageTag()));
                    if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                        toast("No installed speech voice supports your device language.");
                        return;
                    }
                    speakPendingReply();
                } else toast("The installed system speech engine could not start.");
            });
        } else if (textToSpeechReady) speakPendingReply();
    }

    private void speakPendingReply() {
        if (textToSpeech == null || !textToSpeechReady || pendingSpeech.isEmpty()
                || !getPreferences(MODE_PRIVATE).getBoolean(SPEAK_REPLIES_KEY, false)) return;
        textToSpeech.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, "reply-" + System.currentTimeMillis());
        pendingSpeech = "";
    }

    private void renderSettings() {
        TextView title = label("Settings", 23, INK);
        title.setTypeface(null, Typeface.BOLD);
        pageBody.addView(title);
        TextView subtitle = label("Private local chat and survival tools for this device.", 13, MUTED);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1, -2);
        sub.topMargin = dp(4); sub.bottomMargin = dp(16);
        pageBody.addView(subtitle, sub);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        LinearLayout privacy = card();
        TextView privacyTitle = label("Privacy policy", 16, INK); privacyTitle.setTypeface(null, Typeface.BOLD); privacy.addView(privacyTitle);
        TextView privacyText = label("Read how this app handles on-device chats, voice features, model downloads, and your privacy.", 13, MUTED);
        privacyText.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(-1, -2); privacyParams.topMargin = dp(7); privacy.addView(privacyText, privacyParams);
        privacy.setClickable(true);
        privacy.setOnClickListener(v -> showPrivacyDetails());
        list.addView(privacy);

        list.addView(sectionLabel("CHAT PREFERENCES"));
        list.addView(settingRow("Appearance", "Theme: " + themeLabel() + " · tap to change", v -> cycleTheme()));
        list.addView(settingRow("Response length", responseLengthLabel() + " · tap to change", v -> cycleResponseLength()));
        list.addView(settingRow("Context window", contextSizeLabel() + " · higher values use more memory", v -> cycleContextSize()));
        list.addView(settingRow("Temperature", temperatureLabel() + " · tap to change creativity", v -> cycleTemperature()));
        String customInstructions = getPreferences(MODE_PRIVATE).getString(CUSTOM_INSTRUCTIONS_KEY, "").trim();
        list.addView(settingRow("Assistant persona", customInstructions.isEmpty() ? "Set a name, tone, or role" : "Custom instructions saved · tap to edit", v -> showAssistantInstructions()));
        boolean restoredContext = getPreferences(MODE_PRIVATE).getBoolean(RESTORED_CONTEXT_KEY, true);
        list.addView(settingRow("Restored-chat context", restoredContext ? "On · include recent messages after reopening a chat" : "Off · each restored-chat reply starts fresh", v -> toggleRestoredContext()));
        boolean autoUnload = getPreferences(MODE_PRIVATE).getBoolean(AUTO_UNLOAD_KEY, false);
        list.addView(settingRow("Auto-unload model", autoUnload ? "On · unload in background; load for next chat" : "Off · keep model loaded while app is backgrounded", v -> toggleAutoUnload()));
        list.addView(settingRow("Motion", animationsEnabled() ? "On · subtle screen transitions" : "Reduced · no screen transitions", v -> toggleAnimations()));
        boolean speechEnabled = getPreferences(MODE_PRIVATE).getBoolean(SPEAK_REPLIES_KEY, false);
        list.addView(settingRow("Spoken replies", speechEnabled ? "On · uses your installed system voice" : "Off · tap to enable system voice output", v -> toggleSpokenReplies()));
        list.addView(settingRow("Voice language", voiceLanguageLabel() + " · tap to change", v -> cycleVoiceLanguage()));
        list.addView(sectionLabel("DATA & APP"));
        list.addView(settingRow("Recent chats", "Open conversations saved on this device", v -> openHistory()));
        list.addView(settingRow("My go-bag checklist", checklistSummary(), v -> openChecklist()));
        list.addView(settingRow("Clear this conversation", "Remove locally saved chat messages", v -> confirmClearChat()));
        list.addView(sectionLabel("ABOUT & SAFETY"));
        list.addView(settingRow("AI limits & safety notice", "Important model and emergency-use limitations", v -> showSafety()));
        list.addView(settingRow("Open-source notices", "Runtime components and model attribution", v -> showThirdPartyNotices()));
        list.addView(settingRow("Donate", "Support the project", v -> openDonate()));
        list.addView(settingRow("About", "Version " + BuildConfig.VERSION_NAME + " · Apocalyptic AI", v -> showAbout()));
        scroll.addView(list);
        pageBody.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private View settingRow(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL);
        TextView t = label(title, 15, INK); t.setTypeface(null, Typeface.BOLD); text.addView(t);
        TextView s = label(subtitle, 12, MUTED); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.topMargin = dp(4); text.addView(s, sp);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = label("›", 22, MUTED); row.addView(arrow);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title + ". " + subtitle + ". Open setting.");
        row.setOnClickListener(listener);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(8); row.setLayoutParams(rp);
        return row;
    }

    private TextView sectionLabel(String text) {
        TextView heading = label(text, 11, MUTED);
        heading.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(18);
        params.bottomMargin = dp(2);
        heading.setLayoutParams(params);
        return heading;
    }

    private String responseLengthMode() {
        return getPreferences(MODE_PRIVATE).getString(RESPONSE_LENGTH_KEY, "fast");
    }

    private int responseTokenBudget() {
        String mode = responseLengthMode();
        return "detailed".equals(mode) ? 320 : "balanced".equals(mode) ? 160 : 128;
    }

    private String responseLengthLabel() {
        String mode = responseLengthMode();
        String label = "detailed".equals(mode) ? "Detailed" : "balanced".equals(mode) ? "Balanced" : "Fast";
        return label + " · up to " + responseTokenBudget() + " tokens";
    }

    private void cycleResponseLength() {
        String[] values = {"fast", "balanced", "detailed"};
        showChoiceDialog("Response length", "Choose a shorter/faster reply or allow more room for detail.",
                new String[]{"Fast · up to 128 tokens", "Balanced · up to 160 tokens", "Detailed · up to 320 tokens"},
                choiceIndex(values, responseLengthMode()), index -> {
                    String next = values[index];
                    getPreferences(MODE_PRIVATE).edit().putString(RESPONSE_LENGTH_KEY, next).apply();
                    toast("Response length: " + next);
                    showMain();
                });
    }

    private void toggleRestoredContext() {
        boolean currentlyEnabled = getPreferences(MODE_PRIVATE).getBoolean(RESTORED_CONTEXT_KEY, true);
        showChoiceDialog("Restored-chat context", "Choose whether a reopened conversation sends recent saved messages as context to the local model.",
                new String[]{"On · include recent messages", "Off · start each reply fresh"}, currentlyEnabled ? 0 : 1, index -> {
                    boolean enabled = index == 0;
                    getPreferences(MODE_PRIVATE).edit().putBoolean(RESTORED_CONTEXT_KEY, enabled).apply();
                    toast(enabled ? "Recent restored-chat context enabled." : "Restored chats will start replies without prior messages.");
                    showMain();
                });
    }

    private void sendMessage() {
        if (composer == null || isGenerating) return;
        String question = composer.getText().toString().trim();
        if (question.isEmpty()) return;
        if (!selectedModelReady()) {
            activeTab = "model";
            showMain();
            toast("Download a local model before starting AI chat.");
            return;
        }
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
            long modelWorkingSet = Math.max(768L * 1024L * 1024L, selectedModel().expectedBytes * 3L / 2L);
            long contextReserve = (long) contextSize() * 128L * 1024L;
            long minimumFreeMemory = modelWorkingSet + contextReserve;
            if (memoryInfo.availMem < minimumFreeMemory) {
                toast("This model needs about " + formatBytes(minimumFreeMemory) + " free memory. Close other apps or choose a smaller model.");
                return;
            }
        }
        composer.setText("");
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composer.getWindowToken(), 0);
        addMessage("user", question);
        if (isUnsafeRequest(question)) {
            addMessage("assistant", "I can’t help with weapons, explosives, traps or harming people. I can help with safer alternatives such as shelter, water storage, signaling, protective equipment, repairs and de-escalation.");
            renderPage();
            return;
        }
        isGenerating = true;
        generationStoppedByUser = false;
        ChatMessage reply = new ChatMessage("assistant", "");
        chat.add(reply);
        saveChat();
        renderPage();
        final TextView replyView = lastAssistantBody();
        final StringBuilder generated = new StringBuilder();
        final boolean seedingHistory = restoreHistoryContext;
        final boolean includeRestoredContext = getPreferences(MODE_PRIVATE).getBoolean(RESTORED_CONTEXT_KEY, true);
        final String inferencePrompt = buildInferencePrompt(question, seedingHistory && includeRestoredContext);
        restoreHistoryContext = false;
        LocalModelBridge.generate(this, getModelFile().getAbsolutePath(), selectedSystemPrompt(), inferencePrompt,
                responseTokenBudget(), contextSize(), temperatureValue(), new LocalModelBridge.Callback() {
            @Override public void onToken(String token, boolean done) {
                if (token != null) generated.append(token);
                reply.text = visibleAssistantText(generated.toString());
                if (replyView != null) {
                    boolean reasoningOnly = reply.text.trim().isEmpty() && generated.length() > 0;
                    replyView.setText(reasoningOnly || reply.text.isEmpty() ? "Thinking…" : reply.text);
                    replyView.setTextColor(reasoningOnly || reply.text.isEmpty() ? MUTED : INK);
                }
                if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                if (done) {
                    isGenerating = false;
                    boolean stopped = generationStoppedByUser;
                    if (reply.text.trim().isEmpty()) reply.text = stopped ? "Response stopped." : "I couldn’t produce a response. Please try a shorter question.";
                    saveChat();
                    archiveCurrentChat();
                    speakAssistantReply(reply.text);
                    generationStoppedByUser = false;
                    showMain();
                    if (stopped) toast("Response stopped. Your conversation is saved.");
                }
            }
            @Override public void onError(String message) {
                isGenerating = false;
                generationStoppedByUser = false;
                restoreHistoryContext = seedingHistory;
                reply.text = "The local model could not start: " + message + "\n\nTry selecting a smaller model or check the downloaded file.";
                saveChat();
                archiveCurrentChat();
                showMain();
            }
        });
    }

    private void stopGeneration() {
        if (!isGenerating || generationStoppedByUser) return;
        generationStoppedByUser = true;
        LocalModelBridge.cancelGeneration();
        toast("Stopping response…");
    }

    private String buildInferencePrompt(String question, boolean includeHistory) {
        if (!includeHistory || chat.size() < 2) return question;
        StringBuilder transcript = new StringBuilder();
        int end = chat.size() - 1;
        int start = Math.max(0, end - 8);
        for (int i = start; i < end; i++) {
            ChatMessage message = chat.get(i);
            if (message.text == null || message.text.trim().isEmpty()) continue;
            String text = message.text.trim();
            if (text.length() > 350) text = text.substring(0, 347) + "…";
            transcript.append("user".equals(message.role) ? "User: " : "Assistant: ")
                    .append(text).append('\n');
        }
        if (transcript.length() == 0) return question;
        return "Recent conversation for context only:\n" + transcript
                + "\nRespond to the current user message:\n" + question;
    }

    private TextView lastAssistantBody() {
        if (messageColumn == null || messageColumn.getChildCount() == 0) return null;
        View last = messageColumn.getChildAt(messageColumn.getChildCount() - 1);
        if (last instanceof LinearLayout) {
            LinearLayout bubble = (LinearLayout) last;
            if (bubble.getChildCount() > 1 && bubble.getChildAt(1) instanceof TextView) return (TextView) bubble.getChildAt(1);
        }
        return null;
    }

    /** Hide internal reasoning tags, including partial tags split across streamed tokens. */
    public static String visibleAssistantText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String visible = raw.replaceAll("(?is)<think(?:ing)?\\b[^>]*>.*?(?:</think(?:ing)?\\s*>|$)", "")
                .replaceAll("(?is)<analysis\\b[^>]*>.*?(?:</analysis\\s*>|$)", "")
                .replaceAll("(?is)</?(?:think(?:ing)?|analysis)\\s*>", "");
        String lower = visible.toLowerCase(Locale.US);
        String[] markers = {"<think", "</think", "<analysis", "</analysis"};
        for (String marker : markers) {
            int max = Math.min(marker.length() - 1, lower.length());
            for (int n = max; n > 0; n--) {
                if (lower.endsWith(marker.substring(0, n))) {
                    visible = visible.substring(0, visible.length() - n);
                    lower = lower.substring(0, lower.length() - n);
                    break;
                }
            }
        }
        return visible.trim();
    }

    private void addMessage(String role, String text) {
        ChatMessage message = new ChatMessage(role, text);
        chat.add(message);
        saveChat();
        if (messageColumn != null) addMessageView(message);
    }

    private void addMessageView(ChatMessage message) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
        boolean user = "user".equals(message.role);
        bubble.setBackground(round(user ? GREEN_SOFT : CARD, 16));
        TextView who = label(user ? "YOU" : "APOCALYPTIC AI", 10, user ? GREEN : MUTED);
        who.setTypeface(null, Typeface.BOLD);
        bubble.addView(who);
        String displayText = "user".equals(message.role) ? message.text : visibleAssistantText(message.text);
        TextView body = label(displayText.isEmpty() ? "Thinking…" : displayText, 14, INK);
        body.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.topMargin = dp(5); bubble.addView(body, bp);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        if (user) p.leftMargin = dp(22); else p.rightMargin = dp(12);
        messageColumn.addView(bubble, p);
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    public static boolean isUnsafeRequest(String q) {
        String s = q.toLowerCase(Locale.US);
        return hasAny(s, "make a weapon", "build a weapon", "build a gun", "make a bomb", "build a bomb", "explosive", "booby trap", "injury trap", "hurt someone", "kill someone");
    }

    private static boolean hasAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private void loadChat() {
        String raw = getPreferences(MODE_PRIVATE).getString(CHAT_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String role = item.optString("role", "assistant");
                String text = item.optString("text", "");
                if ("assistant".equals(role)) text = visibleAssistantText(text);
                if ("user".equals(role) || "assistant".equals(role)) chat.add(new ChatMessage(role, text));
            }
        } catch (Exception ignored) { chat.clear(); }
        for (ChatMessage message : chat) {
            if ("user".equals(message.role)) { restoreHistoryContext = true; break; }
        }
        if (restoreHistoryContext) LocalModelBridge.startNewConversation();
    }

    private void saveChat() {
        JSONArray array = new JSONArray();
        try {
            int start = Math.max(0, chat.size() - 80);
            for (int i = start; i < chat.size(); i++) {
                JSONObject obj = new JSONObject();
                obj.put("role", chat.get(i).role);
                obj.put("text", chat.get(i).text);
                array.put(obj);
            }
            getPreferences(MODE_PRIVATE).edit().putString(CHAT_KEY, array.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void archiveCurrentChat() {
        boolean hasUserMessage = false;
        String title = "Preparedness chat";
        for (ChatMessage message : chat) {
            if ("user".equals(message.role)) {
                hasUserMessage = true;
                title = message.text.replace('\n', ' ').trim();
                if (title.length() > 42) title = title.substring(0, 39) + "…";
                break;
            }
        }
        if (!hasUserMessage) return;
        try {
            JSONArray messages = new JSONArray();
            int start = Math.max(0, chat.size() - 80);
            for (int i = start; i < chat.size(); i++) {
                JSONObject item = new JSONObject();
                item.put("role", chat.get(i).role);
                item.put("text", chat.get(i).text);
                messages.put(item);
            }
            JSONArray previous = new JSONArray(getPreferences(MODE_PRIVATE).getString(HISTORY_KEY, "[]"));
            for (int i = 0; i < previous.length(); i++) {
                JSONObject oldSession = previous.getJSONObject(i);
                JSONArray oldMessages = oldSession.optJSONArray("messages");
                if (oldMessages != null && oldMessages.toString().equals(messages.toString())) {
                    activeConversationId = oldSession.optString("id", "");
                    getPreferences(MODE_PRIVATE).edit().putString(ACTIVE_SESSION_KEY, activeConversationId).apply();
                    return;
                }
            }
            if (activeConversationId.isEmpty()) activeConversationId = Long.toString(System.currentTimeMillis());
            JSONObject session = new JSONObject();
            session.put("id", activeConversationId);
            session.put("title", title.isEmpty() ? "Preparedness chat" : title);
            session.put("savedAt", System.currentTimeMillis());
            session.put("messages", messages);
            JSONArray history = new JSONArray();
            history.put(session);
            int kept = 0;
            for (int i = 0; i < previous.length() && kept < 19; i++) {
                JSONObject oldSession = previous.getJSONObject(i);
                if (activeConversationId.equals(oldSession.optString("id", ""))) continue;
                history.put(oldSession);
                kept++;
            }
            getPreferences(MODE_PRIVATE).edit()
                    .putString(HISTORY_KEY, history.toString())
                    .putString(ACTIVE_SESSION_KEY, activeConversationId)
                    .apply();
        } catch (Exception ignored) { }
    }

    private void openHistory() {
        if (isGenerating) { toast("Wait for the current answer to finish first."); return; }
        try {
            JSONArray history = new JSONArray(getPreferences(MODE_PRIVATE).getString(HISTORY_KEY, "[]"));
            if (history.length() == 0) { toast("No saved conversations yet. Start a chat and it will appear in the sidebar."); return; }
            String[] titles = new String[history.length()];
            for (int i = 0; i < history.length(); i++) titles[i] = history.getJSONObject(i).optString("title", "Preparedness chat");
            showChoiceDialog("Your chats", "Saved on this device. Choose a conversation to reopen.", titles, -1, selectedIndex -> {
                try { loadHistoryConversation(history.getJSONObject(selectedIndex)); }
                catch (Exception e) { toast("Could not open that saved conversation."); }
            });
        } catch (Exception e) { toast("Could not read saved chat history."); }
    }

    private void loadHistoryConversation(JSONObject selected) {
        if (isGenerating) { toast("Wait for the current answer to finish first."); return; }
        try {
            archiveCurrentChat();
            JSONArray messages = selected.optJSONArray("messages");
            chat.clear();
            if (messages != null) {
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject item = messages.getJSONObject(i);
                    String role = item.optString("role", "assistant");
                    if ("user".equals(role) || "assistant".equals(role)) {
                        String text = item.optString("text", "");
                        if ("assistant".equals(role)) text = visibleAssistantText(text);
                        ChatMessage previous = chat.isEmpty() ? null : chat.get(chat.size() - 1);
                        if (previous == null || !previous.role.equals(role) || !previous.text.equals(text)) {
                            chat.add(new ChatMessage(role, text));
                        }
                    }
                }
            }
            saveChat();
            LocalModelBridge.startNewConversation();
            activeConversationId = selected.optString("id", "");
            getPreferences(MODE_PRIVATE).edit().putString(ACTIVE_SESSION_KEY, activeConversationId).apply();
            restoreHistoryContext = true;
            activeTab = "chat";
            checklistOpen = false;
            showMain();
        } catch (Exception e) { toast("Could not open that saved conversation."); }
    }

    private void confirmDeleteHistoryConversation(JSONObject target) {
        String title = target.optString("title", "Preparedness chat");
        showBrandedConfirmation("Delete saved chat?", "“" + title + "” will be removed from this device’s chat history.",
                "Keep chat", "Delete", true, () -> deleteHistoryConversation(target));
    }

    private void deleteHistoryConversation(JSONObject target) {
        try {
            JSONArray old = new JSONArray(getPreferences(MODE_PRIVATE).getString(HISTORY_KEY, "[]"));
            JSONArray kept = new JSONArray();
            long savedAt = target.optLong("savedAt", -1L);
            String id = target.optString("id", "");
            for (int i = 0; i < old.length(); i++) {
                JSONObject entry = old.getJSONObject(i);
                boolean match = !id.isEmpty() ? id.equals(entry.optString("id", ""))
                        : savedAt == entry.optLong("savedAt", -2L)
                        && target.optString("title", "").equals(entry.optString("title", ""));
                if (!match) kept.put(entry);
            }
            getPreferences(MODE_PRIVATE).edit().putString(HISTORY_KEY, kept.toString()).apply();
            showMain();
            toast("Saved chat deleted.");
        } catch (Exception e) { toast("Could not delete that saved conversation."); }
    }

    private void newChat() {
        if (isGenerating) { toast("Wait for the current answer to finish first."); return; }
        archiveCurrentChat();
        chat.clear();
        saveChat();
        LocalModelBridge.startNewConversation();
        activeConversationId = "";
        getPreferences(MODE_PRIVATE).edit().remove(ACTIVE_SESSION_KEY).apply();
        restoreHistoryContext = false;
        checklistOpen = false;
        activeTab = "chat";
        showMain();
        toast("New chat started.");
    }

    private void confirmClearChat() {
        showBrandedConfirmation("Clear saved chat?", "This removes the locally saved conversation from this device.",
                "Keep it", "Clear", true, () -> {
                    chat.clear(); saveChat(); LocalModelBridge.startNewConversation(); activeConversationId = "";
                    getPreferences(MODE_PRIVATE).edit().remove(ACTIVE_SESSION_KEY).apply();
                    restoreHistoryContext = false; activeTab = "chat"; checklistOpen = false; showMain();
                });
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.2f GiB", bytes / 1024.0 / 1024.0 / 1024.0);
        if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.0f MiB", bytes / 1024.0 / 1024.0);
        if (bytes >= 1024L) return String.format(Locale.US, "%.0f KiB", bytes / 1024.0);
        return bytes + " B";
    }

    private String downloadFailure(int reason) {
        if (reason == DownloadManager.ERROR_CANNOT_RESUME) return "Android could not resume it";
        if (reason == DownloadManager.ERROR_DEVICE_NOT_FOUND) return "storage unavailable";
        if (reason == DownloadManager.ERROR_FILE_ERROR) return "file/storage error";
        if (reason == DownloadManager.ERROR_HTTP_DATA_ERROR) return "network data error";
        if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) return "not enough free storage";
        if (reason == DownloadManager.ERROR_TOO_MANY_REDIRECTS) return "too many redirects";
        if (reason == DownloadManager.ERROR_UNHANDLED_HTTP_CODE) return "the model host returned an HTTP error";
        if (reason == DownloadManager.ERROR_UNKNOWN) return "unknown download error";
        return "download error " + reason;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackground(round(CARD, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(10);
        box.setLayoutParams(p);
        return box;
    }

    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    private TextView action(String text, boolean primary) {
        TextView button = label(text, 14, primary ? ON_ACCENT : GREEN_DARK);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackground(round(primary ? GREEN : GREEN_SOFT, 15));
        button.setClickable(true);
        return button;
    }

    private interface ChoiceHandler { void onChoice(int index); }

    private LinearLayout brandedDialogShell(String title, String message) {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(18), dp(18), dp(16));
        shell.setBackground(round(CARD, 20));
        TextView heading = label(title, 18, INK);
        heading.setTypeface(null, Typeface.BOLD);
        shell.addView(heading);
        if (message != null && !message.trim().isEmpty()) {
            TextView description = label(message, 13, MUTED);
            description.setGravity(Gravity.TOP | Gravity.START);
            description.setLineSpacing(dp(3), 1.0f);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
            messageParams.topMargin = dp(7);
            shell.addView(description, messageParams);
        }
        return shell;
    }

    private void showBrandedDialog(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(32);
        window.setLayout(Math.max(dp(220), Math.min(dp(440), availableWidth)), WindowManager.LayoutParams.WRAP_CONTENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void showChoiceDialog(String title, String description, String[] options, int selectedIndex, ChoiceHandler handler) {
        Dialog dialog = new Dialog(this);
        LinearLayout shell = brandedDialogShell(title, description);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            boolean selected = i == selectedIndex;
            LinearLayout choice = new LinearLayout(this);
            choice.setOrientation(LinearLayout.HORIZONTAL);
            choice.setGravity(Gravity.CENTER_VERTICAL);
            choice.setPadding(dp(12), dp(11), dp(12), dp(11));
            choice.setMinimumHeight(dp(48));
            choice.setBackground(round(selected ? GREEN_SOFT : PAPER, 13));
            TextView optionText = label(options[i], 14, INK);
            if (selected) optionText.setTypeface(null, Typeface.BOLD);
            choice.addView(optionText, new LinearLayout.LayoutParams(0, -2, 1));
            TextView marker = label(selected ? "Selected" : "›", selected ? 11 : 20, selected ? GREEN_DARK : MUTED);
            if (selected) marker.setTypeface(null, Typeface.BOLD);
            choice.addView(marker);
            choice.setClickable(true);
            choice.setFocusable(true);
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(-1, -2);
            choiceParams.topMargin = dp(7);
            choices.addView(choice, choiceParams);
            choice.setOnClickListener(v -> {
                dialog.dismiss();
                handler.onChoice(index);
            });
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(choices, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(Math.min(360, options.length * 58 + 4)));
        scrollParams.topMargin = dp(7);
        shell.addView(scroll, scrollParams);
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END);
        TextView close = action("Close", false);
        close.setPadding(dp(18), 0, dp(18), 0);
        close.setMinHeight(dp(44));
        close.setOnClickListener(v -> dialog.dismiss());
        footer.addView(close, new LinearLayout.LayoutParams(-2, dp(44)));
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(12);
        shell.addView(footer, footerParams);
        dialog.setContentView(shell);
        dialog.setCancelable(true);
        showBrandedDialog(dialog);
    }

    private void showBrandedConfirmation(String title, String message, String positiveLabel, Runnable onPositive) {
        showBrandedConfirmation(title, message, "Cancel", positiveLabel, false, onPositive);
    }

    private void showBrandedConfirmation(String title, String message, String cancelLabel, String positiveLabel,
            boolean destructive, Runnable onPositive) {
        Dialog dialog = new Dialog(this);
        LinearLayout shell = brandedDialogShell(title, message);
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = action(cancelLabel, false);
        TextView confirm = action(positiveLabel, true);
        if (destructive) {
            confirm.setBackground(round(Color.rgb(178, 54, 54), 15));
            confirm.setTextColor(Color.WHITE);
        }
        cancel.setPadding(dp(12), 0, dp(12), 0);
        confirm.setPadding(dp(12), 0, dp(12), 0);
        cancel.setMinHeight(dp(44));
        confirm.setMinHeight(dp(44));
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            onPositive.run();
        });
        footer.addView(cancel, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        confirmParams.leftMargin = dp(8);
        footer.addView(confirm, confirmParams);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(14);
        shell.addView(footer, footerParams);
        dialog.setContentView(shell);
        dialog.setCancelable(true);
        showBrandedDialog(dialog);
    }

    private void showBrandedMessageDialog(String title, String message, String buttonLabel) {
        Dialog dialog = new Dialog(this);
        LinearLayout shell = brandedDialogShell(title, message);
        TextView close = action(buttonLabel, true);
        close.setMinHeight(dp(46));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(46));
        closeParams.topMargin = dp(14);
        shell.addView(close, closeParams);
        dialog.setContentView(shell);
        dialog.setCancelable(true);
        showBrandedDialog(dialog);
    }

    private void showBrandedDocumentDialog(String title, String content, boolean linkify) {
        Dialog dialog = new Dialog(this);
        LinearLayout shell = brandedDialogShell(title, "");
        TextView text = label(content, 13, INK);
        text.setGravity(Gravity.TOP | Gravity.START);
        text.setLineSpacing(dp(3), 1.0f);
        text.setPadding(dp(12), dp(12), dp(12), dp(12));
        text.setTextIsSelectable(true);
        if (linkify) android.text.util.Linkify.addLinks(text, android.text.util.Linkify.WEB_URLS);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(round(PAPER, 12));
        scroll.addView(text);
        int maxHeight = Math.max(dp(180), Math.min(dp(440), getResources().getDisplayMetrics().heightPixels - dp(220)));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, maxHeight);
        scrollParams.topMargin = dp(12);
        shell.addView(scroll, scrollParams);
        TextView close = action("Close", true);
        close.setMinHeight(dp(46));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(46));
        closeParams.topMargin = dp(12);
        shell.addView(close, closeParams);
        dialog.setContentView(shell);
        dialog.setCancelable(true);
        showBrandedDialog(dialog);
    }

    private TextView roundIcon(String glyph, String description) {
        TextView button = label(glyph, 23, GREEN_DARK);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(round(CARD, 22));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return button;
    }

    private ImageView roundImageIcon(int resource, String description) {
        ImageView button = new ImageView(this);
        button.setImageResource(resource);
        button.setColorFilter(GREEN_DARK);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setContentDescription(description);
        button.setBackground(round(CARD, 22));
        button.setClickable(true);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return button;
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void showPrivacyDetails() {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("PRIVACY_POLICY.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append('\n');
        } catch (Exception e) {
            content.append("The bundled privacy policy could not be opened. Please try again or contact the publisher through raihan.sites.bd.");
        }
        showBrandedDocumentDialog("Privacy policy", content.toString(), true);
    }

    private void showThirdPartyNotices() {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("THIRD_PARTY_NOTICES.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append('\n');
        } catch (Exception e) {
            content.append("Could not load the bundled notices. See THIRD_PARTY_NOTICES.md in the source repository.");
        }
        showBrandedDocumentDialog("Open-source notices", content.toString(), true);
    }

    private void showSafety() {
        showBrandedMessageDialog("AI limits & safety",
                "Apocalyptic AI generates responses with an on-device language model. Output can be inaccurate, incomplete, outdated, biased, or unsafe, and safety filters may not catch every problem. Do not rely on the app as an emergency service or as medical, legal, financial, or other professional advice. For urgent situations, follow official local instructions and contact emergency services or qualified professionals.\n\nUse the app lawfully and do not treat its output as a substitute for your own judgment. Model licenses and third-party terms are listed under Open-source notices and in the project documentation. No statement here guarantees accuracy, fitness for a purpose, legal clearance, or Play Store approval, or limits rights that cannot legally be limited.",
                "Got it");
    }

    private void showAbout() {
        showBrandedMessageDialog("About Apocalyptic AI",
                "Version " + BuildConfig.VERSION_NAME + "\n\nApocalyptic AI is a privacy-first, local survival assistant. Chat uses " + selectedModel().attribution + " on this device. Model files are downloaded from their publishers and are not bundled. Check the linked model card and applicable license before commercial release; this app does not provide legal advice or a guarantee of rights.",
                "Close");
    }

    private void openDonate() {
        openExternal("https://raihan.sites.bd");
    }

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) { toast("Could not open this link."); }
    }

    private void removeTransientNotice() {
        if (transientNotice == null) return;
        transientNotice.animate().cancel();
        if (transientNotice.getParent() instanceof ViewGroup)
            ((ViewGroup) transientNotice.getParent()).removeView(transientNotice);
        transientNotice = null;
    }

    private void toast(String message) {
        try {
            removeTransientNotice();
            View decor = getWindow().getDecorView();
            if (!(decor instanceof FrameLayout)) return;
            TextView notice = label(message, 14, ON_ACCENT);
            notice.setGravity(Gravity.CENTER_VERTICAL);
            notice.setPadding(dp(16), dp(13), dp(16), dp(13));
            notice.setMaxLines(3);
            notice.setEllipsize(android.text.TextUtils.TruncateAt.END);
            notice.setBackground(round(GREEN, 16));
            notice.setElevation(dp(8));
            notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            notice.setAlpha(0f);
            notice.setTranslationY(dp(10));
            transientNotice = notice;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            params.setMargins(dp(16), 0, dp(16), dp(20));
            ((FrameLayout) decor).addView(notice, params);
            notice.animate().alpha(1f).translationY(0f).setDuration(170).start();
            notice.announceForAccessibility(message);
            handler.removeCallbacks(dismissTransientNotice);
            handler.postDelayed(dismissTransientNotice, 2800L);
        } catch (Exception ignored) { }
    }
    private String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }

    @Override public void onBackPressed() {
        if (navigationOverlay != null && navigationOverlay.getVisibility() == View.VISIBLE) {
            navigationOverlay.setVisibility(View.GONE);
            return;
        }
        if (checklistOpen) { checklistOpen = false; renderPage(); return; }
        if (!"chat".equals(activeTab)) { activeTab = "chat"; showMain(); return; }
        super.onBackPressed();
    }
}

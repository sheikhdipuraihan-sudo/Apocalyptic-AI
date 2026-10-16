package bd.sites.raihan.apocalypticai;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
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
    private static final String RESPONSE_LENGTH_KEY = "response_length_mode";
    private static final String RESTORED_CONTEXT_KEY = "restored_chat_context_enabled";
    private static final String CONTEXT_SIZE_KEY = "context_size";
    private static final String TEMPERATURE_KEY = "temperature_mode";
    private static final String AUTO_UNLOAD_KEY = "auto_unload_model";
    private static final String CUSTOM_INSTRUCTIONS_KEY = "custom_assistant_instructions";
    private static final String ANIMATIONS_KEY = "animations_enabled";
    private static final String DOWNLOAD_KEY = "model_download_id";
    private static final String SELECTED_MODEL_KEY = "selected_model_id";
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
    }

    private static final String DEFAULT_MODEL_ID = "titulm-mpt-1b";
    // Keep downloads modest for phones; the first-run default is the requested compact TituLM 1B.
    private static final ModelSpec[] MODELS = {
            new ModelSpec("titulm-mpt-1b", "TituLM · 1B", "Bangla + English · Q4_K_M · about 870 MiB · fast default",
                    "titulm-mpt-1b-v2.0.i1-Q4_K_M.gguf",
                    "https://huggingface.co/mradermacher/titulm-mpt-1b-v2.0-i1-GGUF/resolve/main/titulm-mpt-1b-v2.0.i1-Q4_K_M.gguf?download=true",
                    911_355_520L, "Hishab Technologies", "https://huggingface.co/hishab/titulm-mpt-1b-v2.0", "https://huggingface.co/mradermacher/titulm-mpt-1b-v2.0-i1-GGUF", "Apache-2.0"),
            new ModelSpec("qwen3-06b", "Qwen3 · 0.6B", "Newer general-purpose model · Q4_K_M · about 462 MiB",
                    "Qwen_Qwen3-0.6B-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF/resolve/main/Qwen_Qwen3-0.6B-Q4_K_M.gguf?download=true",
                    484_220_320L, "Qwen Team", "https://huggingface.co/Qwen/Qwen3-0.6B", "https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF", "Apache-2.0"),
            new ModelSpec("qwen-05b", "Qwen2.5 · 0.5B", "Compact chat model · Q4_K_M · about 379 MiB",
                    "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf?download=true",
                    397_808_192L, "Qwen Team", "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct", "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF", "Apache-2.0"),
            new ModelSpec("smollm2-360m", "SmolLM2 · 360M", "Smallest download · Q4_K_M · about 258 MiB",
                    "SmolLM2-360M-Instruct-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf?download=true",
                    270_590_880L, "Hugging Face Smol Models", "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct", "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF", "Apache-2.0"),
            new ModelSpec("smollm2-17b", "SmolLM2 · 1.7B", "Most capable option · Q4_K_M · about 1.0 GiB · newer phones (6 GB+ RAM recommended)",
                    "smollm2-1.7b-instruct-q4_k_m.gguf",
                    "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf?download=true",
                    1_055_609_536L, "Hugging Face Smol Models", "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct", "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF", "Apache-2.0")
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
    private TextView hostStatusLabel;
    private TextView hostShareLinkLabel;
    private TextView hostToggleAction;
    private TextView checklistCountLabel;
    private ProgressBar checklistProgress;
    private FrameLayout navigationOverlay;
    private String activeTab = "chat";
    private String selectedModelId = DEFAULT_MODEL_ID;
    private String activeConversationId = "";
    private boolean checklistOpen;
    private boolean pendingHostStart;
    private long downloadId = -1L;
    private boolean isGenerating;
    private boolean isListening;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
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

    private final Runnable hostStatusPoller = new Runnable() {
        @Override public void run() {
            if (!"host".equals(activeTab)) return;
            refreshHostStatus();
            handler.postDelayed(this, 900L);
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
        downloadId = savedDownloadId(selectedModelId);
        if (!getPreferences(MODE_PRIVATE).getBoolean("onboarding_complete", false)) showOnboarding(0);
        else showMain();
    }

    @Override protected void onResume() {
        super.onResume();
        if (downloadId > 0L) handler.post(downloadPoller);
        if ("host".equals(activeTab)) handler.post(hostStatusPoller);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(downloadPoller);
        handler.removeCallbacks(hostStatusPoller);
        if (isListening) stopVoiceInput(true);
        super.onPause();
    }

    @Override protected void onStop() {
        if (getPreferences(MODE_PRIVATE).getBoolean(AUTO_UNLOAD_KEY, false) && !HostAiService.isActive()) LocalModelBridge.unloadModel();
        super.onStop();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(downloadPoller);
        handler.removeCallbacks(hostStatusPoller);
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
        } else if (requestCode == 3110) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingHostStart) launchHostService();
            else toast("Allow notifications to keep the local host visible while the app is in the background.");
            pendingHostStart = false;
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

    private void cycleTheme() {
        String m = themeMode();
        String next = "system".equals(m) ? "light" : "light".equals(m) ? "dark" : "system";
        getPreferences(MODE_PRIVATE).edit().putString(THEME_KEY, next).apply();
        applyTheme();
        showMain();
    }

    private int contextSize() { return getPreferences(MODE_PRIVATE).getInt(CONTEXT_SIZE_KEY, 4096); }
    private String contextSizeLabel() { return String.format(Locale.US, "%,d requested tokens · model may cap it", contextSize()); }
    private void cycleContextSize() {
        int size = contextSize();
        int next = size == 2048 ? 4096 : size == 4096 ? 8192 : 2048;
        getPreferences(MODE_PRIVATE).edit().putInt(CONTEXT_SIZE_KEY, next).apply();
        toast("Context window set to " + String.format(Locale.US, "%,d", next) + ". It applies when the model next loads.");
        showMain();
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
        String mode = temperatureMode();
        String next = "precise".equals(mode) ? "balanced" : "balanced".equals(mode) ? "creative" : "precise";
        getPreferences(MODE_PRIVATE).edit().putString(TEMPERATURE_KEY, next).apply();
        toast("Sampling: " + ("precise".equals(next) ? "Precise" : "balanced".equals(next) ? "Balanced" : "Creative"));
        showMain();
    }

    private boolean animationsEnabled() { return getPreferences(MODE_PRIVATE).getBoolean(ANIMATIONS_KEY, true); }
    private void toggleAnimations() {
        boolean enabled = !animationsEnabled();
        getPreferences(MODE_PRIVATE).edit().putBoolean(ANIMATIONS_KEY, enabled).apply();
        toast(enabled ? "Subtle screen animations enabled." : "Animations reduced.");
        showMain();
    }
    private void toggleAutoUnload() {
        boolean enabled = !getPreferences(MODE_PRIVATE).getBoolean(AUTO_UNLOAD_KEY, false);
        getPreferences(MODE_PRIVATE).edit().putBoolean(AUTO_UNLOAD_KEY, enabled).apply();
        toast(enabled ? "The model will unload when the app leaves the screen and reload for your next chat." : "Model stays loaded while the app is in the background.");
        showMain();
    }

    private void showAssistantInstructions() {
        EditText editor = new EditText(this);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setMinLines(4);
        editor.setMaxLines(8);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setHint("Example: Use short bullet points; write in a calm, practical tone.");
        editor.setText(getPreferences(MODE_PRIVATE).getString(CUSTOM_INSTRUCTIONS_KEY, ""));
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Assistant persona & instructions")
                .setMessage("Add a name, role, tone, or response preference. The app's core safety rules remain in effect.")
                .setView(editor)
                .setNeutralButton("Reset", (dialog, which) -> {
                    getPreferences(MODE_PRIVATE).edit().remove(CUSTOM_INSTRUCTIONS_KEY).apply();
                    toast("Assistant instructions reset.");
                    showMain();
                })
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    getPreferences(MODE_PRIVATE).edit().putString(CUSTOM_INSTRUCTIONS_KEY, editor.getText().toString().trim()).apply();
                    toast("Instructions saved; applied to your next reply.");
                    showMain();
                })
                .show();
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
        appRoot = new LinearLayout(this);
        appRoot.setOrientation(LinearLayout.VERTICAL);
        appRoot.setBackgroundColor(PAPER);
        appRoot.setPadding(0, 0, 0, 0);
        navigationOverlay = null;

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
            View scrim = new View(this);
            scrim.setBackgroundColor(Color.argb(145, 0, 0, 0));
            scrim.setOnClickListener(v -> navigationOverlay.setVisibility(View.GONE));
            navigationOverlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout drawer = buildSidebar();
            FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(300), -1, Gravity.START);
            navigationOverlay.addView(drawer, drawerParams);
            screen.addView(navigationOverlay, new FrameLayout.LayoutParams(-1, -1));
        }
        setContentView(screen);
        renderPage();
    }

    private void toggleNavigationDrawer() {
        if (navigationOverlay == null) return;
        navigationOverlay.setVisibility(navigationOverlay.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private LinearLayout buildSidebar() {
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(10), dp(12), dp(10), dp(12));
        sidebar.setBackgroundColor(CARD);

        TextView brand = label("Apocalyptic AI", 15, INK);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setPadding(dp(10), dp(8), dp(10), dp(16));
        sidebar.addView(brand);

        ScrollView scroll = new ScrollView(this);
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        addSidebarItem(items, "new", "New chat", "chat.svg", R.drawable.ic_nav_chat);
        addSidebarItem(items, "chat", "Chat", "chat.svg", R.drawable.ic_nav_chat);
        addSidebarItem(items, "model", "Models", "model.svg", R.drawable.ic_nav_model);
        addSidebarItem(items, "host", "Host AI", "host.svg", R.drawable.ic_nav_chat);
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
                    if (navigationOverlay != null) navigationOverlay.setVisibility(View.GONE);
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
            if (navigationOverlay != null) navigationOverlay.setVisibility(View.GONE);
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
        handler.removeCallbacks(hostStatusPoller);
        pageBody.removeAllViews();
        modelProgress = null;
        modelProgressText = null;
        modelPageStatus = null;
        modelAction = null;
        if (checklistOpen) renderChecklist();
        else if ("chat".equals(activeTab)) renderChat();
        else if ("model".equals(activeTab)) renderModel();
        else if ("host".equals(activeTab)) renderHost();
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
            CheckBox item = new CheckBox(this);
            item.setText(KIT_ITEMS[i]);
            item.setTextColor(INK);
            item.setTextSize(14);
            item.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
            item.setPadding(dp(12), dp(6), dp(12), dp(6));
            item.setBackground(round(CARD, 12));
            item.setChecked(getPreferences(MODE_PRIVATE).getBoolean("kit_checked_" + i, false));
            item.setOnCheckedChangeListener((button, checked) -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean("kit_checked_" + itemIndex, checked).apply();
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
        reset.setOnClickListener(v -> new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Reset your checklist?")
                .setMessage("All check marks will be cleared on this device.")
                .setNegativeButton("Keep it", null)
                .setPositiveButton("Reset", (d, w) -> {
                    android.content.SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
                    for (int i = 0; i < KIT_ITEMS.length; i++) editor.remove("kit_checked_" + i);
                    editor.apply();
                    renderPage();
                }).show());
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
        TextView subtitle = label("Compact Apache-2.0 models · runs on this phone", 13, MUTED);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1, -2);
        sub.topMargin = dp(4); sub.bottomMargin = dp(14);
        pageBody.addView(subtitle, sub);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView available = label("AVAILABLE MODELS", 11, MUTED);
        available.setTypeface(null, Typeface.BOLD);
        available.setLetterSpacing(0.08f);
        content.addView(available);
        for (ModelSpec model : MODELS) content.addView(modelOption(model));

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

        TextView note = label("Weights are downloaded only when you choose. Upstream model cards list Apache-2.0; check both the linked model card and the exact GGUF repository before publishing. This is not legal advice. The 1.7B option is intended for newer phones (6 GB+ RAM recommended).", 12, MUTED);
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

    private void renderHost() {
        LinearLayout hostContent = new LinearLayout(this);
        hostContent.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("Host your local AI", 23, INK);
        title.setTypeface(null, Typeface.BOLD);
        hostContent.addView(title);
        TextView subtitle = label("Let friends chat with the model running on this phone.", 13, MUTED);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(4);
        subtitleParams.bottomMargin = dp(13);
        hostContent.addView(subtitle, subtitleParams);

        LinearLayout hostCard = card();
        TextView statusTitle = label("HOST STATUS", 10, MUTED);
        statusTitle.setTypeface(null, Typeface.BOLD);
        statusTitle.setLetterSpacing(0.08f);
        hostCard.addView(statusTitle);
        hostStatusLabel = label("Not hosting", 15, INK);
        hostStatusLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(7);
        hostCard.addView(hostStatusLabel, statusParams);
        TextView linkCaption = label("LOCAL SHARE LINK", 10, MUTED);
        linkCaption.setTypeface(null, Typeface.BOLD);
        linkCaption.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2);
        captionParams.topMargin = dp(16);
        hostCard.addView(linkCaption, captionParams);
        hostShareLinkLabel = label("Start hosting to create a link.", 12, INK);
        hostShareLinkLabel.setTextIsSelectable(true);
        hostShareLinkLabel.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(-1, -2);
        linkParams.topMargin = dp(5);
        hostCard.addView(hostShareLinkLabel, linkParams);

        hostToggleAction = action(HostAiService.isActive() ? "Stop hosting" : "Start hosting", !HostAiService.isActive());
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(48));
        toggleParams.topMargin = dp(15);
        hostCard.addView(hostToggleAction, toggleParams);
        hostToggleAction.setOnClickListener(v -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(65)
                .withEndAction(() -> { v.animate().scaleX(1f).scaleY(1f).setDuration(130).start(); toggleHost(); }).start());

        LinearLayout shareActions = new LinearLayout(this);
        shareActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView copy = action("Copy link", false);
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        shareParams.topMargin = dp(8);
        shareParams.rightMargin = dp(5);
        shareActions.addView(copy, shareParams);
        copy.setOnClickListener(v -> copyLocalHostLink());
        TextView share = action("Share link…", false);
        LinearLayout.LayoutParams shareIntentParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        shareIntentParams.topMargin = dp(8);
        shareIntentParams.leftMargin = dp(5);
        shareActions.addView(share, shareIntentParams);
        share.setOnClickListener(v -> shareLocalHostLink());
        hostCard.addView(shareActions);
        hostContent.addView(hostCard);

        LinearLayout info = card();
        TextView infoTitle = label("Wi‑Fi / hotspot only", 15, INK);
        infoTitle.setTypeface(null, Typeface.BOLD);
        info.addView(infoTitle);
        TextView infoBody = label("Friends must join the same Wi‑Fi network or this phone’s hotspot. The host listens only on the phone’s private Wi‑Fi address and rejects off-network connections. There is no public URL, cloud relay, or cloud AI.", 13, MUTED);
        infoBody.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams infoBodyParams = new LinearLayout.LayoutParams(-1, -2);
        infoBodyParams.topMargin = dp(7);
        info.addView(infoBody, infoBodyParams);
        TextView note = label("The model must already be downloaded on this phone. Hosting uses a temporary access code in the share link; stop hosting to revoke it. Some phone hotspots prevent connected devices from talking to one another.", 12, MUTED);
        note.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(10);
        info.addView(note, noteParams);
        hostContent.addView(info);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(hostContent);
        pageBody.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        refreshHostStatus();
        handler.removeCallbacks(hostStatusPoller);
        handler.post(hostStatusPoller);
    }

    private void refreshHostStatus() {
        if (hostStatusLabel == null || hostShareLinkLabel == null) return;
        android.content.SharedPreferences hostPrefs = getSharedPreferences(HostAiService.PREFS, MODE_PRIVATE);
        if (HostAiService.isActive()) {
            String address = HostAiService.getLocalAddress();
            hostStatusLabel.setText("Active · " + address + ":" + HostAiService.PORT + " · local network only");
            hostStatusLabel.setTextColor(GREEN_DARK);
            hostShareLinkLabel.setText(localHostShareLink());
            if (hostToggleAction != null) {
                hostToggleAction.setText("Stop hosting");
                hostToggleAction.setTextColor(GREEN_DARK);
                hostToggleAction.setBackground(round(GREEN_SOFT, 15));
            }
        } else {
            String state = hostPrefs.getString(HostAiService.KEY_STATE, "stopped");
            String error = hostPrefs.getString(HostAiService.KEY_ERROR, "");
            String text = "starting".equals(state) ? "Starting local host…" : "error".equals(state) ? "Could not start: " + error : "Not hosting";
            hostStatusLabel.setText(text);
            hostStatusLabel.setTextColor("error".equals(state) ? 0xffa43c32 : MUTED);
            hostShareLinkLabel.setText("Start hosting to create a link.");
            if (hostToggleAction != null) {
                hostToggleAction.setText("Start hosting");
                hostToggleAction.setTextColor(ON_ACCENT);
                hostToggleAction.setBackground(round(GREEN, 15));
            }
        }
    }

    private void toggleHost() {
        if (HostAiService.isActive()) {
            stopHosting();
            return;
        }
        if (isGenerating) {
            toast("Wait for the current local reply to finish before starting Host AI.");
            return;
        }
        if (!selectedModelReady()) {
            toast("Download the selected model before hosting.");
            activeTab = "model";
            showMain();
            return;
        }
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Start Host AI on this network?")
                .setMessage("Continuing starts a visible background service on this phone. Browser chat messages from people with your link are sent over local, unencrypted HTTP to this phone's model; they are not sent to a cloud AI or internet relay. Only share the access-code link with people you trust on a trusted Wi‑Fi or hotspot network. Stop hosting to revoke the code.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> requestHostStartPermission())
                .show();
    }

    private void requestHostStartPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            pendingHostStart = true;
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3110);
        } else {
            launchHostService();
        }
    }

    private void launchHostService() {
        android.content.SharedPreferences hostPrefs = getSharedPreferences(HostAiService.PREFS, MODE_PRIVATE);
        hostPrefs.edit().putString(HostAiService.KEY_STATE, "starting").putString(HostAiService.KEY_ERROR, "").apply();
        Intent service = new Intent(this, HostAiService.class).setAction(HostAiService.ACTION_START);
        service.putExtra(HostAiService.EXTRA_MODEL_PATH, getModelFile().getAbsolutePath());
        service.putExtra(HostAiService.EXTRA_SYSTEM_PROMPT, selectedSystemPrompt());
        service.putExtra(HostAiService.EXTRA_MAX_TOKENS, responseTokenBudget());
        service.putExtra(HostAiService.EXTRA_CONTEXT_SIZE, contextSize());
        service.putExtra(HostAiService.EXTRA_TEMPERATURE, temperatureValue());
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            refreshHostStatus();
            toast("Starting the local host…");
        } catch (Exception e) {
            hostPrefs.edit().putString(HostAiService.KEY_STATE, "error")
                    .putString(HostAiService.KEY_ERROR, "Android could not start the background host. Try again while the app is open.").apply();
            refreshHostStatus();
        }
    }

    private void stopHosting() {
        try {
            Intent service = new Intent(this, HostAiService.class).setAction(HostAiService.ACTION_STOP);
            startService(service);
            toast("Stopping the local host…");
        } catch (Exception e) {
            toast("Could not stop hosting. Use the Stop action in the notification.");
        }
        refreshHostStatus();
    }

    private String localHostShareLink() {
        if (!HostAiService.isActive()) return "";
        String address = HostAiService.getLocalAddress();
        String token = HostAiService.getShareToken();
        if (address == null || address.isEmpty() || token == null || token.isEmpty()) return "";
        return "http://" + address + ":" + HostAiService.PORT + "/#key=" + token;
    }

    private void copyLocalHostLink() {
        String link = localHostShareLink();
        if (link.isEmpty()) { toast("Start hosting first to create a local link."); return; }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Local AI host link", link));
            toast("Local link copied. Friends must be on the same Wi‑Fi or hotspot.");
        }
    }

    private void shareLocalHostLink() {
        String link = localHostShareLink();
        if (link.isEmpty()) { toast("Start hosting first to create a local link."); return; }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "Join my local AI chat. Connect to the same Wi‑Fi or phone hotspot, then open this link: " + link);
        startActivity(Intent.createChooser(share, "Share local AI link"));
    }

    private void modelOption(ModelSpec model) {
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
        TextView details = label(model.details + " · " + model.license, 12, MUTED);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(3);
        text.addView(details, detailParams);
        TextView state = label(selected ? "Selected" : validModelFile(model) ? "On device · select" : "Select", 11, selected ? GREEN_DARK : MUTED);
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
            new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Model is ready")
                    .setMessage(model.name + " is saved on this device. Chat responses now run locally.")
                    .setPositiveButton("Open chat", (d, w) -> { activeTab = "chat"; showMain(); })
                    .setNegativeButton("Remove model", (d, w) -> removeModel()).show();
        } else if (downloadId > 0L && isDownloadActive()) {
            new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Cancel model download?")
                    .setMessage("The partial model file will be removed.")
                    .setNegativeButton("Keep downloading", null)
                    .setPositiveButton("Cancel download", (d, w) -> cancelDownload()).show();
        } else {
            new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Download " + model.name + "?")
                    .setMessage("This download is about " + formatBytes(model.expectedBytes) + ". Wi-Fi is recommended. Chat runs locally after the download.")
                    .setNegativeButton("Not now", null)
                    .setPositiveButton("Download", (d, w) -> startModelDownload()).show();
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

    private void requestVoiceInput() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            toast("Private voice input requires Android 12 or newer with an on-device speech model.");
            return;
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            toast("No on-device speech model is installed. Add an offline speech language in your device settings.");
            return;
        }
        if (!getPreferences(MODE_PRIVATE).getBoolean("voice_disclosure_accepted", false)) {
            new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                    .setTitle("Voice input privacy")
                    .setMessage("If you continue, microphone audio will be captured only for dictation and passed to Android's on-device speech recognizer to produce draft text. The app does not upload the recording to a cloud AI. Recognition depends on the speech service/model installed on your device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue", (dialog, which) -> {
                        getPreferences(MODE_PRIVATE).edit().putBoolean("voice_disclosure_accepted", true).apply();
                        requestMicrophonePermissionOrStart();
                    })
                    .show();
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            toast("On-device speech recognition is unavailable on this device.");
            return;
        }
        try {
            voiceInputBaseText = composer == null ? "" : composer.getText().toString().trim();
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
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
                    toast(error == SpeechRecognizer.ERROR_NO_MATCH ? "No speech was recognized. Try again." : "Voice input stopped. Try again.");
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
            toast("Listening on this device… tap Stop when finished.");
        } catch (Exception e) {
            finishVoiceInput();
            toast("Could not start on-device voice input.");
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
            microphoneButton.setContentDescription("Start on-device voice input");
        }
    }

    private void toggleSpokenReplies() {
        boolean enabled = !getPreferences(MODE_PRIVATE).getBoolean(SPEAK_REPLIES_KEY, false);
        getPreferences(MODE_PRIVATE).edit().putBoolean(SPEAK_REPLIES_KEY, enabled).apply();
        if (!enabled && textToSpeech != null) textToSpeech.stop();
        toast(enabled ? "Spoken replies enabled. Uses the system voice installed on your phone." : "Spoken replies disabled.");
        showMain();
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
        String setting = getPreferences(MODE_PRIVATE).getString(VOICE_LANGUAGE_KEY, "system");
        String next = "system".equals(setting) ? "bn-BD" : "bn-BD".equals(setting) ? "en-US" : "system";
        getPreferences(MODE_PRIVATE).edit().putString(VOICE_LANGUAGE_KEY, next).apply();
        if (textToSpeech != null) textToSpeech.setLanguage(Locale.forLanguageTag("system".equals(next) ? Locale.getDefault().toLanguageTag() : next));
        toast("Voice language: " + ("bn-BD".equals(next) ? "Bangla" : "en-US".equals(next) ? "English" : "system language"));
        showMain();
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
        TextView privacyText = label("Read what stays on your phone, what Host AI shares over Wi‑Fi, and how to contact the publisher.", 13, MUTED);
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
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL);
        TextView t = label(title, 15, INK); t.setTypeface(null, Typeface.BOLD); text.addView(t);
        TextView s = label(subtitle, 12, MUTED); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.topMargin = dp(4); text.addView(s, sp);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = label("›", 22, MUTED); row.addView(arrow);
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
        String mode = responseLengthMode();
        String next = "fast".equals(mode) ? "balanced" : "balanced".equals(mode) ? "detailed" : "fast";
        getPreferences(MODE_PRIVATE).edit().putString(RESPONSE_LENGTH_KEY, next).apply();
        toast("Response length: " + ("fast".equals(next) ? "Fast" : "balanced".equals(next) ? "Balanced" : "Detailed"));
        showMain();
    }

    private void toggleRestoredContext() {
        boolean enabled = !getPreferences(MODE_PRIVATE).getBoolean(RESTORED_CONTEXT_KEY, true);
        getPreferences(MODE_PRIVATE).edit().putBoolean(RESTORED_CONTEXT_KEY, enabled).apply();
        toast(enabled ? "Recent restored-chat context enabled." : "Restored chats will start each new reply without previous messages.");
        showMain();
    }

    private void sendMessage() {
        if (composer == null || isGenerating) return;
        if (HostAiService.isActive()) {
            toast("Stop Host AI before using local chat on this phone.");
            return;
        }
        String question = composer.getText().toString().trim();
        if (question.isEmpty()) return;
        if (!selectedModelReady()) {
            activeTab = "model";
            showMain();
            toast("Download a local model before starting AI chat.");
            return;
        }
        if ("titulm-mpt-1b".equals(selectedModelId) || "smollm2-17b".equals(selectedModelId) || contextSize() == 8192) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (activityManager != null) activityManager.getMemoryInfo(memoryInfo);
            long minimumFreeMemory = "smollm2-17b".equals(selectedModelId) ? 1_800L * 1024L * 1024L
                    : "titulm-mpt-1b".equals(selectedModelId) ? 1_200L * 1024L * 1024L
                    : 768L * 1024L * 1024L;
            if (contextSize() == 8192) minimumFreeMemory += 512L * 1024L * 1024L;
            if (activityManager != null && memoryInfo.availMem < minimumFreeMemory) {
                toast("Low free memory for this model. Close other apps or choose a smaller model.");
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
                responseTokenBudget(), contextSize(), temperatureValue(), false, new LocalModelBridge.Callback() {
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
            new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                    .setTitle("Your chats · saved on this device")
                    .setItems(titles, (dialog, selectedIndex) -> {
                        try { loadHistoryConversation(history.getJSONObject(selectedIndex)); }
                        catch (Exception e) { toast("Could not open that saved conversation."); }
                    })
                    .setNegativeButton("Close", null)
                    .show();
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
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Delete saved chat?")
                .setMessage("“" + title + "” will be removed from this device’s chat history.")
                .setNegativeButton("Keep chat", null)
                .setPositiveButton("Delete", (d, w) -> deleteHistoryConversation(target))
                .show();
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
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Clear saved chat?")
                .setMessage("This removes the locally saved conversation from this device.")
                .setNegativeButton("Keep it", null)
                .setPositiveButton("Clear", (d, w) -> { chat.clear(); saveChat(); LocalModelBridge.startNewConversation(); activeConversationId = ""; getPreferences(MODE_PRIVATE).edit().remove(ACTIVE_SESSION_KEY).apply(); restoreHistoryContext = false; activeTab = "chat"; checklistOpen = false; showMain(); })
                .show();
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.1f MiB", bytes / 1024.0 / 1024.0);
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
        TextView text = label(content.toString(), 13, INK);
        text.setGravity(Gravity.TOP | Gravity.START);
        text.setLineSpacing(dp(3), 1.0f);
        text.setPadding(dp(16), dp(12), dp(16), dp(12));
        text.setTextIsSelectable(true);
        android.text.util.Linkify.addLinks(text, android.text.util.Linkify.WEB_URLS);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        int maxHeight = Math.min(dp(520), getResources().getDisplayMetrics().heightPixels - dp(180));
        scroll.setLayoutParams(new ViewGroup.LayoutParams(-1, Math.max(dp(220), maxHeight)));
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Privacy policy")
                .setView(scroll)
                .setPositiveButton("Close", null).show();
    }

    private void showThirdPartyNotices() {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("THIRD_PARTY_NOTICES.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append('\n');
        } catch (Exception e) {
            content.append("Could not load the bundled notices. See THIRD_PARTY_NOTICES.md in the source repository.");
        }
        TextView text = label(content.toString(), 13, INK);
        text.setGravity(Gravity.TOP | Gravity.START);
        text.setPadding(dp(16), dp(12), dp(16), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Open-source notices")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSafety() {
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("AI limits & safety")
                .setMessage("Apocalyptic AI generates responses with an on-device language model. Output can be inaccurate, incomplete, outdated, biased, or unsafe, and safety filters may not catch every problem. Do not rely on the app as an emergency service or as medical, legal, financial, or other professional advice. For urgent situations, follow official local instructions and contact emergency services or qualified professionals.\n\nUse the app lawfully and do not treat its output as a substitute for your own judgment. Model licenses and third-party terms are listed under Open-source notices and in the project documentation. No statement here guarantees accuracy, fitness for a purpose, legal clearance, or Play Store approval, or limits rights that cannot legally be limited.")
                .setPositiveButton("Got it", null).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("About Apocalyptic AI")
                .setMessage("Version " + BuildConfig.VERSION_NAME + "\n\nApocalyptic AI is a privacy-first, local survival assistant. Chat uses " + selectedModel().attribution + " on this device. Model files are downloaded from their publishers and are not bundled. Check the linked model card and applicable license before commercial release; this app does not provide legal advice or a guarantee of rights.")
                .setPositiveButton("Close", null).show();
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

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
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

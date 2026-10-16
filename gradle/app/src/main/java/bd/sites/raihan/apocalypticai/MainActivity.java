package bd.sites.raihan.apocalypticai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.arm.aichat.LocalModelBridge;
import com.caverock.androidsvg.SVG;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    // Theme colours (monochrome, black/white). Set by applyTheme().
    private int INK, MUTED, GREEN, GREEN_DARK, GREEN_SOFT, PAPER, CARD, BORDER, ORANGE, ON_ACCENT;
    private boolean darkMode;

    private static final String PREFS = "apocalyptic_ai_prefs";
    private static final String CHAT_KEY = "chat_messages_v2";
    private static final String HISTORY_KEY = "chat_history_v1";
    private static final String DOWNLOAD_KEY = "model_download_id";
    private static final String SELECTED_MODEL_KEY = "selected_model_id";
    private static final String SYSTEM_PROMPT = "You are Apocalyptic AI, a privacy-first local preparedness assistant. Apocalyptic AI is the app and primary identity. Give concise, practical, cautious, lawful, non-violent guidance. Never provide instructions for weapons, explosives, traps, or harming people. Encourage local emergency services and trained medical care when appropriate. If uncertain, say so and prefer official local guidance.";
    private static final String[] KIT_ITEMS = {"Safe drinking water", "Water treatment method", "Shelf-stable food", "First-aid kit", "Essential medicines", "Warm and rain layers", "Light and spare power", "Paper map and compass", "Whistle or signal", "Repair kit and cordage", "Hygiene supplies", "Emergency contacts and plan"};

    private static final class ModelSpec {
        final String id, name, details, fileName, url, attribution;
        final long expectedBytes;
        ModelSpec(String id, String name, String details, String fileName, String url, long expectedBytes, String attribution) {
            this.id = id;
            this.name = name;
            this.details = details;
            this.fileName = fileName;
            this.url = url;
            this.expectedBytes = expectedBytes;
            this.attribution = attribution;
        }
    }

    private static final ModelSpec[] MODELS = {
            new ModelSpec("llama-1b", "LLaMA 3.2 · 1B", "Best general-purpose option · Q4 · about 735 MiB",
                    "Llama-3.2-1B-Instruct-Q4_0_4_4.gguf",
                    "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_0_4_4.gguf?download=true",
                    770_928_768L, "Meta LLaMA 3.2 1B"),
            new ModelSpec("qwen-05b", "Qwen 2.5 · 0.5B", "Small model · Q4_K_M · about 379 MiB",
                    "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf?download=true",
                    397_808_192L, "Qwen2.5 0.5B Instruct"),
            new ModelSpec("smollm2-360m", "SmolLM2 · 360M", "Micro model · Q4_K_M · about 258 MiB",
                    "SmolLM2-360M-Instruct-Q4_K_M.gguf",
                    "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf?download=true",
                    270_590_880L, "Hugging Face SmolLM2 360M Instruct")
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<ChatMessage> chat = new ArrayList<>();
    private final ArrayList<GuideTopic> topics = new ArrayList<>();
    private LinearLayout appRoot;
    private LinearLayout pageBody;
    private LinearLayout messageColumn;
    private ScrollView chatScroll;
    private EditText composer;
    private EditText librarySearch;
    private ProgressBar modelProgress;
    private TextView modelProgressText;
    private TextView modelPageStatus;
    private TextView modelAction;
    private TextView checklistCountLabel;
    private ProgressBar checklistProgress;
    private FrameLayout navigationOverlay;
    private String activeTab = "chat";
    private String activeTopicId;
    private String selectedModelId = MODELS[0].id;
    private boolean checklistOpen;
    private long downloadId = -1L;
    private boolean isGenerating;
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

    private static final class GuideTopic {
        final String id, title, subtitle, icon, body;
        GuideTopic(String id, String title, String subtitle, String icon, String body) {
            this.id = id; this.title = title; this.subtitle = subtitle; this.icon = icon; this.body = body;
        }
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private ModelSpec findModel(String id) {
        for (ModelSpec model : MODELS) if (model.id.equals(id)) return model;
        return MODELS[0];
    }

    private ModelSpec selectedModel() { return findModel(selectedModelId); }

    private String modelDownloadKey(String modelId) { return "model_download_id_" + modelId; }

    private long savedDownloadId(String modelId) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String key = modelDownloadKey(modelId);
        if (prefs.contains(key)) return prefs.getLong(key, -1L);
        if (MODELS[0].id.equals(modelId) && prefs.contains(DOWNLOAD_KEY)) {
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
        if (MODELS[0].id.equals(modelId)) getPreferences(MODE_PRIVATE).edit().remove(DOWNLOAD_KEY).apply();
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
        return SYSTEM_PROMPT + " The selected open-weight model is " + selectedModel().attribution + ". Credit its creators when model attribution is relevant.";
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
        activeTopicId = null;
        checklistOpen = false;
        showMain();
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTheme();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        seedTopics();
        loadChat();
        selectedModelId = getPreferences(MODE_PRIVATE).getString(SELECTED_MODEL_KEY, MODELS[0].id);
        selectedModelId = findModel(selectedModelId).id;
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
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(downloadPoller);
        super.onDestroy();
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

    private void seedTopics() {
        topics.add(new GuideTopic("water", "Safe water", "Collect, treat and store", "drop",
                "Prefer sealed water or a source known to be safe. Avoid floodwater and water near fuel, industrial chemicals, sewage or dead animals. A clear-looking source may still contain pathogens.\n\nPre-filter muddy water through clean cloth if needed. Use a validated filter according to its instructions and add an appropriate treatment when needed. Boil water according to current local public-health guidance, then cool it in a clean, covered container. Never mix treatment chemicals.\n\nKeep treated and untreated water separate. Use clean containers and plan a reserve for drinking, cooking and first aid."));
        topics.add(new GuideTopic("shelter", "Shelter & warmth", "Reduce exposure safely", "home",
                "Choose a dry, stable location away from flood paths, damaged buildings, dead trees, cliffs and falling debris. Insulate yourself from the ground first, block wind and keep clothing dry.\n\nIn cold conditions, use adjustable layers and avoid sweating. In heat, rest in shade during the hottest period and cover exposed skin. Confusion, unusual sleepiness, clumsiness or collapse require urgent medical help.\n\nNever use a stove, generator or combustion heater in an enclosed space. Carbon monoxide has no smell. Keep a clear exit and contact local responders if a structure is unsafe."));
        topics.add(new GuideTopic("firstaid", "First aid", "Stabilize and get help", "cross",
                "Call local emergency services when possible. Use gloves or another barrier. Check responsiveness, breathing and major bleeding. For life-threatening bleeding, apply firm, continuous direct pressure with clean material and seek urgent care. Do not remove an embedded object.\n\nFor a burn, stop the burning process and cool with clean, cool running water when available. Avoid ice directly, grease and unknown substances; cover loosely with a clean dressing.\n\nFor suspected fractures, support the injured area and do not force it straight. This guide is not a substitute for trained medical care."));
        topics.add(new GuideTopic("food", "Food safety", "Avoid illness and poisoning", "food",
                "Use sealed, shelf-stable food first. Wash hands and tools. Separate raw food from ready-to-eat food. Discard food that is leaking, swollen, moldy, slimy, foul-smelling or has been kept at unsafe temperatures. Never taste food to test it.\n\nDo not eat unknown plants, mushrooms, berries or seeds. Cooking does not neutralize every toxin. Use reliable local identification and health guidance. Preserve food only with a tested method; improvised canning can create serious botulism risk.\n\nKeep food away from rodents, insects, pets and chemicals."));
        topics.add(new GuideTopic("navigation", "Navigation", "Plan routes and reduce risk", "compass",
                "Before travel, record your route, destination, companions, departure time and expected return. Carry a paper map, compass, light, water, insulation, whistle and a way to signal.\n\nIf lost, stop once you realize you are uncertain. Shelter, treat injuries, assess water and mark your location before deciding whether to move. Follow a safe route rather than the shortest route. Do not enter floodwater or descend unknown cliffs.\n\nUse landmarks and a written direction log. Do not rely on moss, stars or animal behavior as your sole navigation method."));
        topics.add(new GuideTopic("repairs", "Tools & repairs", "A compact, useful kit", "tools",
                "A practical repair kit can include cordage, repair tape, work gloves, pliers, a multi-bit driver, sewing supplies, patches, containers, a headlamp and spare batteries. Protect sharp edges and use eye protection.\n\nPractice simple repairs before an emergency. Inspect gear regularly, replace worn parts and keep tools organized. Use tools only for lawful, safe repairs—not to hurt people."));
        topics.add(new GuideTopic("sanitation", "Sanitation", "Reduce disease risk", "shield",
                "Separate drinking water, washing water and waste areas. Keep latrines downhill and away from water sources. Wash hands after using the toilet and before cooking or eating. Control flies and rodents.\n\nAvoid breathing smoke. Never burn plastic, batteries, treated wood or unknown chemicals. Seek medical advice for persistent vomiting, diarrhea, fever or breathing problems."));
        topics.add(new GuideTopic("checklist", "Go-bag checklist", "Prepare before you need it", "check",
                "Water container and treatment method; shelf-stable food; first-aid kit and essential medicines; insulation and rain protection; light and spare power; paper map and compass; whistle; cordage and repair tools; gloves and hygiene supplies; copies of essential documents; emergency contacts; and a written family plan.\n\nAdapt the list for local hazards, climate, health needs, children, older adults and pets. Rotate stored supplies and practice the plan."));
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
        ImageView emblem = new ImageView(this);
        emblem.setImageResource(R.drawable.ic_apocalyptic_mark);
        emblem.setContentDescription("Apocalyptic AI symbol");
        emblem.setScaleType(ImageView.ScaleType.FIT_CENTER);
        emblem.setBackground(round(GREEN_SOFT, 34));
        emblem.setPadding(dp(7), dp(7), dp(7), dp(7));
        LinearLayout.LayoutParams emblemParams = new LinearLayout.LayoutParams(dp(104), dp(104));
        emblemParams.bottomMargin = dp(28);
        content.addView(emblem, emblemParams);

        String[] titles = {"Prepared for the uncertain.", "A guide that works offline.", "Choose your local AI."};
        String[] descriptions = {
                "A calm, practical field guide for water, shelter, first aid and everyday readiness.",
                "Explore the bundled preparedness library and offline guidance any time. Follow local emergency advice first.",
                "Pick a 360M, 0.5B or 1B model. Chat starts after download; the library does not need a model."
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
            TextView note = label("Nothing is downloaded until you choose a model. Local AI chat starts after its download; the preparedness library works without one.", 14, GREEN_DARK);
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
        appRoot.setPadding(dp(18), dp(8), dp(18), 0);
        navigationOverlay = null;

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        boolean wideLayout = getResources().getConfiguration().screenWidthDp >= 640;
        if (!wideLayout) {
            ImageView menu = roundImageIcon(R.drawable.ic_menu, "Open navigation");
            menu.setOnClickListener(v -> toggleNavigationDrawer());
            toolbar.addView(menu);
        }
        ImageView brandMark = new ImageView(this);
        brandMark.setImageResource(R.drawable.ic_apocalyptic_mark);
        brandMark.setContentDescription("Apocalyptic AI");
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        markParams.leftMargin = wideLayout ? 0 : dp(8);
        markParams.rightMargin = dp(8);
        toolbar.addView(brandMark, markParams);
        TextView brand = label("APOCALYPTIC AI", 14, GREEN_DARK);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setLetterSpacing(0.07f);
        toolbar.addView(brand, new LinearLayout.LayoutParams(0, dp(50), 1));
        if ("chat".equals(activeTab) && activeTopicId == null) {
            TextView newChat = roundIcon("＋", "Start a new chat");
            newChat.setOnClickListener(v -> newChat());
            toolbar.addView(newChat);
        }
        TextView more = roundIcon("⋮", "More options");
        more.setOnClickListener(v -> showOverflow(more));
        toolbar.addView(more);
        appRoot.addView(toolbar);

        LinearLayout contentShell = new LinearLayout(this);
        contentShell.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout pageFrame = new FrameLayout(this);
        pageBody = new LinearLayout(this);
        pageBody.setOrientation(LinearLayout.VERTICAL);
        int sideWidthDp = wideLayout ? 190 : 0;
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        int bodyWidthDp = Math.min(780, Math.max(280, screenWidthDp - 36 - sideWidthDp));
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(dp(bodyWidthDp), -1, Gravity.CENTER_HORIZONTAL);
        pageFrame.addView(pageBody, bodyParams);
        if (wideLayout) {
            contentShell.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(sideWidthDp), -1));
            contentShell.addView(pageFrame, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            FrameLayout phoneShell = new FrameLayout(this);
            phoneShell.addView(pageFrame, new FrameLayout.LayoutParams(-1, -1));
            navigationOverlay = new FrameLayout(this);
            navigationOverlay.setVisibility(View.GONE);
            View scrim = new View(this);
            scrim.setBackgroundColor(Color.argb(145, 0, 0, 0));
            scrim.setOnClickListener(v -> navigationOverlay.setVisibility(View.GONE));
            navigationOverlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout drawer = buildSidebar();
            FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(280), -1, Gravity.START);
            navigationOverlay.addView(drawer, drawerParams);
            phoneShell.addView(navigationOverlay, new FrameLayout.LayoutParams(-1, -1));
            contentShell.addView(phoneShell, new LinearLayout.LayoutParams(-1, -1));
        }
        appRoot.addView(contentShell, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(appRoot);
        renderPage();
    }

    private void toggleNavigationDrawer() {
        if (navigationOverlay == null) return;
        navigationOverlay.setVisibility(navigationOverlay.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private LinearLayout buildSidebar() {
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(8), dp(10), dp(8), dp(12));
        sidebar.setBackgroundColor(CARD);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(8), dp(6), dp(6), dp(14));
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_apocalyptic_mark);
        mark.setContentDescription("Apocalyptic AI");
        heading.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView brand = label("Apocalyptic AI", 14, INK);
        brand.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0, -2, 1);
        brandParams.leftMargin = dp(9);
        heading.addView(brand, brandParams);
        sidebar.addView(heading);

        addSidebarItem(sidebar, "chat", "Chat", "chat.svg", R.drawable.ic_nav_chat);
        addSidebarItem(sidebar, "library", "Library", "library.svg", R.drawable.ic_nav_library);
        addSidebarItem(sidebar, "model", "Models", "model.svg", R.drawable.ic_nav_model);
        addSidebarItem(sidebar, "settings", "Settings", "settings.svg", R.drawable.ic_nav_settings);
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(dp(10), dp(10), dp(10), dp(10));
        sidebar.addView(divider, dividerParams);
        addSidebarItem(sidebar, "donate", "Donate", null, R.drawable.ic_donate);
        return sidebar;
    }

    private void addSidebarItem(LinearLayout sidebar, String id, String title, String svgAsset, int iconResource) {
        boolean selected = activeTopicId == null && activeTab.equals(id);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(round(selected ? GREEN_SOFT : CARD, 12));
        ImageView icon = new ImageView(this);
        if ("donate".equals(id)) {
            icon.setImageResource(iconResource);
            icon.setColorFilter(selected ? GREEN : MUTED);
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
            else {
                activeTopicId = null;
                checklistOpen = false;
                activeTab = id;
                showMain();
            }
        });
    }

    private void showOverflow(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "New chat");
        menu.getMenu().add(0, 2, 1, "Preparedness library");
        menu.getMenu().add(0, 3, 2, "Model download");
        menu.getMenu().add(0, 4, 3, "Settings");
        menu.getMenu().add(0, 5, 4, "About Apocalyptic AI");
        menu.getMenu().add(0, 6, 5, "Recent chats");
        menu.getMenu().add(0, 7, 6, "My go-bag checklist");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) newChat();
            else if (item.getItemId() == 2) { activeTab = "library"; activeTopicId = null; checklistOpen = false; showMain(); }
            else if (item.getItemId() == 3) { activeTab = "model"; activeTopicId = null; checklistOpen = false; showMain(); }
            else if (item.getItemId() == 4) { activeTab = "settings"; activeTopicId = null; checklistOpen = false; showMain(); }
            else if (item.getItemId() == 5) showAbout();
            else if (item.getItemId() == 6) openHistory();
            else if (item.getItemId() == 7) openChecklist();
            return true;
        });
        menu.show();
    }

    private void renderPage() {
        pageBody.removeAllViews();
        modelProgress = null;
        modelProgressText = null;
        modelPageStatus = null;
        modelAction = null;
        if (checklistOpen) renderChecklist();
        else if (activeTopicId != null) renderTopicDetail();
        else if ("chat".equals(activeTab)) renderChat();
        else if ("library".equals(activeTab)) renderLibrary();
        else if ("model".equals(activeTab)) renderModel();
        else renderSettings();
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
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("What do you need help with?", 23, INK);
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title);
        TextView subtitle = label("A practical guide for safer decisions.", 13, MUTED);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(4);
        heading.addView(subtitle, subtitleParams);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.bottomMargin = dp(12);
        pageBody.addView(heading, headingParams);

        LinearLayout state = new LinearLayout(this);
        state.setGravity(Gravity.CENTER_VERTICAL);
        state.setPadding(dp(12), dp(10), dp(12), dp(10));
        state.setBackground(round(CARD, 14));
        boolean ready = selectedModelReady();
        TextView stateIcon = label(ready ? "●" : "○", 13, ready ? GREEN : ORANGE);
        state.addView(stateIcon);
        TextView stateText = label(ready ? selectedModel().name + " ready · on this device" : "Download a local model to start AI chat", 12, INK);
        LinearLayout.LayoutParams stateTextParams = new LinearLayout.LayoutParams(0, -2, 1);
        stateTextParams.leftMargin = dp(8);
        state.addView(stateText, stateTextParams);
        TextView openModel = label(ready ? "Ready" : "Set up", 12, GREEN);
        openModel.setTypeface(null, Typeface.BOLD);
        openModel.setOnClickListener(v -> { activeTab = "model"; showMain(); });
        state.addView(openModel);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.bottomMargin = dp(12);
        pageBody.addView(state, stateParams);

        if (!ready) {
            LinearLayout setup = card();
            setup.setGravity(Gravity.CENTER);
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.ic_apocalyptic_mark);
            setup.addView(mark, new LinearLayout.LayoutParams(dp(68), dp(68)));
            TextView setupTitle = label("Choose a local model to chat", 18, INK);
            setupTitle.setTypeface(null, Typeface.BOLD);
            setupTitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
            titleParams.topMargin = dp(12);
            setup.addView(setupTitle, titleParams);
            TextView setupCopy = label("Your messages are answered on this device after the model is downloaded. The preparedness library stays available offline.", 13, MUTED);
            setupCopy.setGravity(Gravity.CENTER);
            setupCopy.setLineSpacing(dp(3), 1.0f);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
            copyParams.topMargin = dp(7);
            setup.addView(setupCopy, copyParams);
            TextView choose = action("Choose a model", true);
            LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, dp(50));
            chooseParams.topMargin = dp(15);
            setup.addView(choose, chooseParams);
            choose.setOnClickListener(v -> { activeTab = "model"; showMain(); });
            TextView library = action("Open preparedness library", false);
            LinearLayout.LayoutParams libraryParams = new LinearLayout.LayoutParams(-1, dp(48));
            libraryParams.topMargin = dp(8);
            setup.addView(library, libraryParams);
            library.setOnClickListener(v -> { activeTab = "library"; showMain(); });
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
        if (chat.isEmpty()) addMessage("assistant", "Hi, I’m Apocalyptic AI. I can help you prepare calmly and safely. Try asking about water, shelter, first aid or supplies.");
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
        composer.setHint("Ask your field guide…");
        composer.setSingleLine(false);
        composer.setMaxLines(4);
        composer.setPadding(dp(15), dp(10), dp(12), dp(10));
        composer.setBackground(round(CARD, 20));
        composeRow.addView(composer, new LinearLayout.LayoutParams(0, dp(52), 1));
        ImageView send = new ImageView(this);
        send.setImageResource(R.drawable.ic_send);
        send.setColorFilter(ON_ACCENT);
        send.setContentDescription("Send message");
        send.setScaleType(ImageView.ScaleType.CENTER);
        send.setBackground(round(GREEN, 20));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(50), dp(50));
        sendParams.leftMargin = dp(8);
        composeRow.addView(send, sendParams);
        send.setOnClickListener(v -> sendMessage());
        pageBody.addView(composeRow);
    }

    private void renderLibrary() {
        TextView title = label("Preparedness library", 23, INK);
        title.setTypeface(null, Typeface.BOLD);
        pageBody.addView(title);
        TextView subtitle = label("Short, offline-first guides for practical needs.", 13, MUTED);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(4);
        subParams.bottomMargin = dp(14);
        pageBody.addView(subtitle, subParams);
        LinearLayout kitCard = card();
        kitCard.setOrientation(LinearLayout.HORIZONTAL);
        kitCard.setGravity(Gravity.CENTER_VERTICAL);
        TextView kitMark = label("✓", 20, GREEN);
        kitMark.setGravity(Gravity.CENTER);
        kitMark.setBackground(round(GREEN_SOFT, 13));
        kitCard.addView(kitMark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout kitText = new LinearLayout(this);
        kitText.setOrientation(LinearLayout.VERTICAL);
        TextView kitTitle = label("My go-bag checklist", 14, INK);
        kitTitle.setTypeface(null, Typeface.BOLD);
        kitText.addView(kitTitle);
        TextView kitCount = label(checklistSummary(), 12, MUTED);
        LinearLayout.LayoutParams kitCountParams = new LinearLayout.LayoutParams(-1, -2);
        kitCountParams.topMargin = dp(3);
        kitText.addView(kitCount, kitCountParams);
        LinearLayout.LayoutParams kitTextParams = new LinearLayout.LayoutParams(0, -2, 1);
        kitTextParams.leftMargin = dp(11);
        kitCard.addView(kitText, kitTextParams);
        kitCard.addView(label("›", 22, MUTED));
        kitCard.setOnClickListener(v -> openChecklist());
        pageBody.addView(kitCard);
        librarySearch = new EditText(this);
        librarySearch.setSingleLine(true);
        librarySearch.setTextSize(14);
        librarySearch.setHint("Search water, shelter, first aid…");
        librarySearch.setPadding(dp(14), 0, dp(14), 0);
        librarySearch.setBackground(round(CARD, 14));
        pageBody.addView(librarySearch, new LinearLayout.LayoutParams(-1, dp(48)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, dp(14));
        for (GuideTopic topic : topics) list.addView(topicCard(topic));
        scroll.addView(list);
        pageBody.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        librarySearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase(Locale.US).trim();
                for (int i = 0; i < list.getChildCount(); i++) {
                    View item = list.getChildAt(i);
                    Object tag = item.getTag();
                    item.setVisibility(tag instanceof String && ((String) tag).contains(query) ? View.VISIBLE : View.GONE);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void renderChecklist() {
        TextView back = label("‹   Library", 14, GREEN_DARK);
        back.setPadding(0, dp(5), 0, dp(12));
        back.setOnClickListener(v -> { checklistOpen = false; renderPage(); });
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
        activeTab = "library";
        activeTopicId = null;
        checklistOpen = true;
        showMain();
    }

    private View topicCard(GuideTopic topic) {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = label(topic.icon, 20, GREEN);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(GREEN_SOFT, 13));
        box.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(topic.title, 15, INK);
        name.setTypeface(null, Typeface.BOLD);
        words.addView(name);
        TextView desc = label(topic.subtitle, 12, MUTED);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = dp(3);
        words.addView(desc, descParams);
        LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(0, -2, 1);
        wordParams.leftMargin = dp(12);
        box.addView(words, wordParams);
        TextView arrow = label("›", 22, MUTED);
        box.addView(arrow);
        box.setTag((topic.title + " " + topic.subtitle + " " + topic.body).toLowerCase(Locale.US));
        box.setOnClickListener(v -> { activeTopicId = topic.id; renderPage(); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(9);
        box.setLayoutParams(p);
        return box;
    }

    private void renderTopicDetail() {
        GuideTopic topic = findTopic(activeTopicId);
        if (topic == null) { activeTopicId = null; renderLibrary(); return; }
        TextView back = label("‹   All guides", 14, GREEN_DARK);
        back.setPadding(0, dp(5), 0, dp(12));
        back.setOnClickListener(v -> { activeTopicId = null; renderPage(); });
        pageBody.addView(back);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView icon = label(topic.icon, 23, GREEN);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(GREEN_SOFT, 14));
        content.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView title = label(topic.title, 26, INK);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(15);
        content.addView(title, tp);
        TextView subtitle = label(topic.subtitle, 14, MUTED);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(5);
        content.addView(subtitle, sp);
        LinearLayout bodyCard = card();
        TextView body = label(topic.body, 15, INK);
        body.setLineSpacing(dp(5), 1.0f);
        bodyCard.addView(body);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(18);
        content.addView(bodyCard, bp);
        TextView ask = action("Ask local AI about this", false);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(50));
        ap.topMargin = dp(12);
        content.addView(ask, ap);
        ask.setOnClickListener(v -> {
            if (!selectedModelReady()) {
                activeTab = "model";
                activeTopicId = null;
                showMain();
                toast("Download a local model before starting AI chat.");
                return;
            }
            activeTopicId = null; activeTab = "chat"; showMain();
            if (composer != null) { composer.setText("Give me a practical checklist for " + topic.title.toLowerCase(Locale.US) + "."); sendMessage(); }
        });
        scroll.addView(content);
        pageBody.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void renderModel() {
        TextView title = label("Choose a local model", 23, INK);
        title.setTypeface(null, Typeface.BOLD);
        pageBody.addView(title);
        TextView subtitle = label("Three sizes · private, on-device inference", 13, MUTED);
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
        TextView privacy = label("The selected model runs locally after download. Model files stay in this app’s private storage; no chat is sent to a server.", 13, INK);
        privacy.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = dp(15);
        hero.addView(privacy, pp);
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

        TextView note = label("The app installer does not include model weights. Choose a smaller model if storage or download data is limited. The preparedness library remains available offline.", 12, MUTED);
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

    private View modelOption(ModelSpec model) {
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
        TextView details = label(model.details, 12, MUTED);
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
                    .setMessage("This download is about " + formatBytes(model.expectedBytes) + ". Wi-Fi is recommended. The local library stays available while it downloads.")
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
            modelPageStatus.setText("Not downloaded · the library is available offline");
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
        return "Not downloaded · the library remains available offline";
    }

    private void renderSettings() {
        TextView title = label("Settings", 23, INK);
        title.setTypeface(null, Typeface.BOLD);
        pageBody.addView(title);
        TextView subtitle = label("Control how Apocalyptic AI works on this device.", 13, MUTED);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1, -2);
        sub.topMargin = dp(4); sub.bottomMargin = dp(16);
        pageBody.addView(subtitle, sub);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        LinearLayout privacy = card();
        TextView privacyTitle = label("Privacy", 16, INK); privacyTitle.setTypeface(null, Typeface.BOLD); privacy.addView(privacyTitle);
        TextView privacyText = label("Chats and the downloaded model stay in this app’s private storage. The optional AI runs locally after the model is downloaded.", 13, MUTED);
        privacyText.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(-1, -2); privacyParams.topMargin = dp(7); privacy.addView(privacyText, privacyParams);
        list.addView(privacy);

        list.addView(settingRow("Appearance", "Theme: " + themeLabel() + " (tap to change)", v -> cycleTheme()));
        list.addView(settingRow("Recent chats", "Open conversations saved on this device", v -> openHistory()));
        list.addView(settingRow("My go-bag checklist", checklistSummary(), v -> openChecklist()));
        list.addView(settingRow("Preparedness safety", "Guidance is cautious and non-violent", v -> showSafety()));
        list.addView(settingRow("Clear this conversation", "Remove locally saved chat messages", v -> confirmClearChat()));
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
        composer.setText("");
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composer.getWindowToken(), 0);
        addMessage("user", question);
        if (isUnsafeRequest(question)) {
            addMessage("assistant", "I can’t help with weapons, explosives, traps or harming people. I can help with safer alternatives such as shelter, water storage, signaling, protective equipment, repairs and de-escalation.");
            renderPage();
            return;
        }
        isGenerating = true;
        ChatMessage reply = new ChatMessage("assistant", "");
        chat.add(reply);
        saveChat();
        renderPage();
        final TextView replyView = lastAssistantBody();
        final StringBuilder generated = new StringBuilder();
        final boolean seedingHistory = restoreHistoryContext;
        final String inferencePrompt = buildInferencePrompt(question, seedingHistory);
        restoreHistoryContext = false;
        LocalModelBridge.generate(this, getModelFile().getAbsolutePath(), selectedSystemPrompt(), inferencePrompt, new LocalModelBridge.Callback() {
            @Override public void onToken(String token, boolean done) {
                if (token != null) generated.append(token);
                reply.text = generated.toString();
                if (replyView != null) replyView.setText(reply.text.isEmpty() ? "Thinking…" : reply.text);
                if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                if (done) {
                    isGenerating = false;
                    if (reply.text.trim().isEmpty()) reply.text = "I couldn’t produce a response. Please try a shorter question.";
                    saveChat();
                    renderPage();
                }
            }
            @Override public void onError(String message) {
                isGenerating = false;
                restoreHistoryContext = seedingHistory;
                reply.text = "The local model could not start: " + message + "\n\nTry selecting a smaller model or check the downloaded file. The offline preparedness library is still available from navigation.";
                saveChat();
                renderPage();
            }
        });
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
        TextView body = label(message.text.isEmpty() ? "Thinking…" : message.text, 14, INK);
        body.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.topMargin = dp(5); bubble.addView(body, bp);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        if (user) p.leftMargin = dp(22); else p.rightMargin = dp(12);
        messageColumn.addView(bubble, p);
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private boolean isUnsafeRequest(String q) {
        String s = q.toLowerCase(Locale.US);
        return hasAny(s, "make a weapon", "build a weapon", "build a gun", "make a bomb", "build a bomb", "explosive", "booby trap", "injury trap", "hurt someone", "kill someone");
    }

    private boolean hasAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private void loadChat() {
        String raw = getPreferences(MODE_PRIVATE).getString(CHAT_KEY, "[]");
        boolean removedOfflineReplies = false;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String role = item.optString("role", "assistant");
                String text = item.optString("text", "");
                if ("assistant".equals(role) && isLegacyOfflineReply(text)) removedOfflineReplies = true;
                else chat.add(new ChatMessage(role, text));
            }
        } catch (Exception ignored) { chat.clear(); }
        if (removedOfflineReplies) saveChat();
        for (ChatMessage message : chat) {
            if ("user".equals(message.role)) { restoreHistoryContext = true; break; }
        }
        if (restoreHistoryContext) LocalModelBridge.startNewConversation();
    }

    private boolean isLegacyOfflineReply(String text) {
        if (text.startsWith("The offline guide is ready for common topics")) return true;
        for (GuideTopic topic : topics) if (topic.body.equals(text)) return true;
        return false;
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
            if (previous.length() > 0) {
                JSONArray oldMessages = previous.getJSONObject(0).optJSONArray("messages");
                if (oldMessages != null && oldMessages.toString().equals(messages.toString())) return;
            }
            JSONObject session = new JSONObject();
            session.put("title", title.isEmpty() ? "Preparedness chat" : title);
            session.put("savedAt", System.currentTimeMillis());
            session.put("messages", messages);
            JSONArray history = new JSONArray();
            history.put(session);
            for (int i = 0; i < Math.min(19, previous.length()); i++) history.put(previous.getJSONObject(i));
            getPreferences(MODE_PRIVATE).edit().putString(HISTORY_KEY, history.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void openHistory() {
        if (isGenerating) { toast("Wait for the current answer to finish first."); return; }
        try {
            JSONArray history = new JSONArray(getPreferences(MODE_PRIVATE).getString(HISTORY_KEY, "[]"));
            if (history.length() == 0) { toast("No saved conversations yet. Start a chat, then tap New chat to save it."); return; }
            String[] titles = new String[history.length()];
            for (int i = 0; i < history.length(); i++) titles[i] = history.getJSONObject(i).optString("title", "Preparedness chat");
            new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Recent chats · saved on this device")
                    .setItems(titles, (dialog, selectedIndex) -> {
                        archiveCurrentChat();
                        try {
                            JSONArray messages = history.getJSONObject(selectedIndex).optJSONArray("messages");
                            chat.clear();
                            if (messages != null) {
                                for (int i = 0; i < messages.length(); i++) {
                                    JSONObject item = messages.getJSONObject(i);
                                    chat.add(new ChatMessage(item.optString("role", "assistant"), item.optString("text", "")));
                                }
                            }
                            saveChat();
                            LocalModelBridge.startNewConversation();
                            restoreHistoryContext = true;
                            activeTab = "chat";
                            activeTopicId = null;
                            checklistOpen = false;
                            showMain();
                        } catch (Exception e) { toast("Could not open that saved conversation."); }
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Exception e) { toast("Could not read saved chat history."); }
    }

    private void newChat() {
        if (isGenerating) { toast("Wait for the current answer to finish first."); return; }
        archiveCurrentChat();
        chat.clear();
        saveChat();
        LocalModelBridge.startNewConversation();
        restoreHistoryContext = false;
        activeTopicId = null;
        checklistOpen = false;
        activeTab = "chat";
        showMain();
        toast("New chat started.");
    }

    private void confirmClearChat() {
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Clear saved chat?")
                .setMessage("This removes the locally saved conversation from this device.")
                .setNegativeButton("Keep it", null)
                .setPositiveButton("Clear", (d, w) -> { chat.clear(); saveChat(); LocalModelBridge.startNewConversation(); restoreHistoryContext = false; activeTab = "chat"; activeTopicId = null; checklistOpen = false; showMain(); })
                .show();
    }

    private GuideTopic findTopic(String id) {
        for (GuideTopic topic : topics) if (topic.id.equals(id)) return topic;
        return topics.get(0);
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

    private void showSafety() {
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Preparedness safety")
                .setMessage("This app supports lawful, non-violent preparedness. It is educational and does not replace local emergency instructions, trained medical care or professional advice. Avoid risky improvisation and contact emergency services when possible.")
                .setPositiveButton("Got it", null).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this, darkMode ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("About Apocalyptic AI")
                .setMessage("Version " + BuildConfig.VERSION_NAME + "\n\nApocalyptic AI is a privacy-first preparedness assistant. Chat uses " + selectedModel().attribution + " locally on this device. Model weights are downloaded separately; review the model’s license before use. The preparedness library works offline without a model.")
                .setPositiveButton("Close", null).show();
    }

    private void openDonate() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://raihan.sites.bd"))); }
        catch (Exception e) { toast("No browser is available to open the donation page."); }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }

    @Override public void onBackPressed() {
        if (navigationOverlay != null && navigationOverlay.getVisibility() == View.VISIBLE) {
            navigationOverlay.setVisibility(View.GONE);
            return;
        }
        if (checklistOpen) { checklistOpen = false; renderPage(); return; }
        if (activeTopicId != null) { activeTopicId = null; renderPage(); return; }
        if (!"chat".equals(activeTab)) { activeTab = "chat"; showMain(); return; }
        super.onBackPressed();
    }
}

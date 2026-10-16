package bd.sites.raihan.apocalypticai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Base64;

import com.arm.aichat.LocalModelBridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started browser chat server, deliberately restricted to a private Wi-Fi/hotspot interface. */
public final class HostAiService extends Service {
    public static final String ACTION_START = "bd.sites.raihan.apocalypticai.HOST_START";
    public static final String ACTION_STOP = "bd.sites.raihan.apocalypticai.HOST_STOP";
    public static final String EXTRA_MODEL_PATH = "model_path";
    public static final String EXTRA_SYSTEM_PROMPT = "system_prompt";
    public static final String EXTRA_MAX_TOKENS = "max_tokens";
    public static final String EXTRA_CONTEXT_SIZE = "context_size";
    public static final String EXTRA_TEMPERATURE = "temperature";

    public static final String PREFS = "host_ai_state";
    public static final String KEY_STATE = "state";
    public static final String KEY_ERROR = "error";
    public static final int PORT = 8765;

    private static final String CHANNEL_ID = "local_ai_host";
    private static final int NOTIFICATION_ID = 8765;
    private static final int MAX_HEADER_BYTES = 8192;
    private static final int MAX_BODY_BYTES = 48000;
    private static final int MAX_MESSAGE_CHARS = 3000;
    private static final int MAX_HISTORY_MESSAGES = 8;
    private static final int MAX_HISTORY_CHARS = 6500;

    private static volatile boolean running;
    private static volatile boolean inferenceRunning;
    private static volatile String activeToken = "";
    private static volatile String activeAddress = "";

    private final AtomicBoolean generationActive = new AtomicBoolean(false);
    private final Object rateLock = new Object();
    private final Map<String, ArrayDeque<Long>> requestTimes = new HashMap<>();
    private final ExecutorService clients = Executors.newFixedThreadPool(3);
    private volatile boolean stopping;
    private volatile ServerSocket serverSocket;
    private volatile Thread listenerThread;
    private String modelPath;
    private String systemPrompt;
    private int maxTokens = 192;
    private int contextSize = 2048;
    private float temperature = 0.3f;

    public static boolean isActive() { return running; }
    public static boolean isGenerating() { return inferenceRunning; }
    public static String getLocalAddress() { return activeAddress; }
    public static String getShareToken() { return activeToken; }

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;

        modelPath = intent.getStringExtra(EXTRA_MODEL_PATH);
        systemPrompt = intent.getStringExtra(EXTRA_SYSTEM_PROMPT);
        maxTokens = clamp(intent.getIntExtra(EXTRA_MAX_TOKENS, 192), 32, 320);
        contextSize = clamp(intent.getIntExtra(EXTRA_CONTEXT_SIZE, 2048), 1024, 8192);
        temperature = Math.max(0.1f, Math.min(1.2f, intent.getFloatExtra(EXTRA_TEMPERATURE, 0.3f)));
        if (modelPath == null || systemPrompt == null) {
            saveState("error", "The selected model settings could not be loaded.");
            stopSelf();
            return START_NOT_STICKY;
        }

        saveState("starting", "");
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        startListenerIfNeeded();
        return START_NOT_STICKY;
    }

    private synchronized void startListenerIfNeeded() {
        if (listenerThread != null && listenerThread.isAlive()) return;
        stopping = false;
        listenerThread = new Thread(this::serve, "ApocalypticAI-LanHost");
        listenerThread.start();
    }

    private void serve() {
        ServerSocket listener = null;
        try {
            if (stopping) return;
            Inet4Address wifiAddress = findWifiAddress();
            listener = new ServerSocket();
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(wifiAddress, PORT), 8);
            if (stopping) { closeQuietly(listener); return; }
            serverSocket = listener;
            activeAddress = wifiAddress.getHostAddress();
            activeToken = newToken();
            running = true;
            saveState("running", "");

            while (!stopping && !listener.isClosed()) {
                Socket client = listener.accept();
                if (!isPrivatePeer(client.getInetAddress())) {
                    try { writeSimple(client.getOutputStream(), 403, "Only devices on this Wi-Fi or phone hotspot can connect."); }
                    catch (Exception ignored) { }
                    closeQuietly(client);
                    continue;
                }
                client.setSoTimeout(15000);
                clients.execute(() -> handleClient(client));
            }
        } catch (Exception e) {
            if (!stopping) {
                String message = e instanceof FileNotFoundException ? e.getMessage() : friendlyError(e);
                saveState("error", message);
                running = false;
                activeToken = "";
                activeAddress = "";
                stopSelf();
            }
        } finally {
            if (listener != null) closeQuietly(listener);
            serverSocket = null;
        }
    }

    private Inet4Address findWifiAddress() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) throw new IOException("Connect this phone to Wi-Fi or start its hotspot first.");
        while (interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            String name = network.getName().toLowerCase(Locale.US);
            boolean wifi = name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("wifi");
            if (!wifi || !network.isUp() || network.isLoopback()) continue;
            Enumeration<InetAddress> addresses = network.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) return (Inet4Address) address;
            }
        }
        throw new IOException("No private Wi-Fi/hotspot address found. Connect to Wi-Fi or start the phone hotspot, then try again.");
    }

    private boolean isPrivatePeer(InetAddress address) {
        return address != null && address.isSiteLocalAddress();
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket;
             BufferedInputStream input = new BufferedInputStream(client.getInputStream());
             BufferedOutputStream output = new BufferedOutputStream(client.getOutputStream())) {
            HttpRequest request = readRequest(input);
            if (request == null) return;
            String path;
            try { path = Uri.parse(request.target).getPath(); }
            catch (Exception ignored) { path = request.target; }

            if ("GET".equals(request.method) && ("/".equals(path) || path == null || path.isEmpty())) {
                byte[] html;
                try (java.io.InputStream asset = getAssets().open("host/index.html")) {
                    html = readAll(asset, 64000);
                }
                writeResponse(output, 200, "text/html; charset=utf-8", html, true);
                return;
            }
            if (!"POST".equals(request.method) || !"/api/chat".equals(path)) {
                writeJson(output, 404, errorJson("Not found."));
                return;
            }
            if (!authorized(request.headers.get("authorization"))) {
                writeJson(output, 401, errorJson("This local host link is not authorized. Open the full link shared by the phone owner."));
                return;
            }
            if (!allowRequest(client.getInetAddress().getHostAddress())) {
                writeJson(output, 429, errorJson("Too many messages from this device. Wait a minute and try again."));
                return;
            }
            if (!generationActive.compareAndSet(false, true)) {
                writeJson(output, 429, errorJson("The phone is answering another message. Please wait and retry."));
                return;
            }
            try {
                JSONObject payload = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
                String message = payload.optString("message", "").trim();
                if (message.isEmpty() || message.length() > MAX_MESSAGE_CHARS) {
                    writeJson(output, 400, errorJson("Enter a message up to 3,000 characters."));
                    return;
                }
                if (MainActivity.isUnsafeRequest(message)) {
                    writeJson(output, 400, errorJson("I can’t help with instructions to harm someone or build a weapon. I can help with safety, prevention, or emergency response."));
                    return;
                }
                String prompt = makePrompt(payload.optJSONArray("history"), message);
                streamAnswer(output, prompt);
            } catch (JSONException e) {
                writeJson(output, 400, errorJson("The browser sent an invalid chat message."));
            } finally {
                generationActive.set(false);
            }
        } catch (Exception ignored) {
            closeQuietly(socket);
        }
    }

    private String makePrompt(JSONArray history, String currentMessage) throws JSONException {
        StringBuilder prompt = new StringBuilder();
        int total = 0;
        if (history != null) {
            int from = Math.max(0, history.length() - MAX_HISTORY_MESSAGES);
            for (int i = from; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;
                String role = item.optString("role", "");
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                String content = item.optString("content", "").trim();
                if (content.isEmpty()) continue;
                if ("assistant".equals(role)) content = MainActivity.visibleAssistantText(content);
                int remaining = MAX_HISTORY_CHARS - total;
                if (remaining <= 0) break;
                if (content.length() > remaining) content = content.substring(content.length() - remaining);
                prompt.append("user".equals(role) ? "User: " : "Assistant: ").append(content).append('\n');
                total += content.length();
            }
        }
        if (prompt.length() > 0) prompt.append("\nRespond to the current message:\n");
        prompt.append(currentMessage);
        return prompt.toString();
    }

    private void streamAnswer(BufferedOutputStream output, String prompt) throws IOException {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = power == null ? null : power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ApocalypticAI:LocalHostInference");
        if (wakeLock != null) wakeLock.acquire(120000L);
        ArrayBlockingQueue<StreamEvent> events = new ArrayBlockingQueue<>(640);
        StringBuilder raw = new StringBuilder();
        StringBuilder sent = new StringBuilder();
        inferenceRunning = true;
        try {
            writeChunkedHeaders(output);
            LocalModelBridge.generate(this, modelPath, systemPrompt, prompt, maxTokens, contextSize, temperature, true,
                    new LocalModelBridge.Callback() {
                        @Override public void onToken(String token, boolean done) {
                            if (token != null && !token.isEmpty()) {
                                synchronized (raw) {
                                    raw.append(token);
                                    String safe = MainActivity.visibleAssistantText(raw.toString());
                                    String previous = sent.toString();
                                    if (safe.startsWith(previous) && safe.length() > previous.length()) {
                                        String delta = safe.substring(previous.length());
                                        sent.setLength(0);
                                        sent.append(safe);
                                        events.offer(StreamEvent.token(delta));
                                    }
                                }
                            }
                            if (done) events.offer(StreamEvent.done());
                        }
                        @Override public void onError(String message) { events.offer(StreamEvent.error(message)); }
                    });

            boolean finished = false;
            while (!finished) {
                StreamEvent event;
                try { event = events.poll(15, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                if (event == null) {
                    writeChunk(output, "{\"ping\":true}\n".getBytes(StandardCharsets.UTF_8));
                    continue;
                }
                JSONObject json = new JSONObject();
                if (event.error != null) {
                    json.put("error", event.error);
                    finished = true;
                } else if (event.done) {
                    json.put("done", true);
                    finished = true;
                } else {
                    json.put("token", event.token);
                }
                writeChunk(output, (json.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            writeChunk(output, new byte[0]);
        } catch (IOException e) {
            LocalModelBridge.cancelGeneration();
            throw e;
        } catch (JSONException e) {
            throw new IOException("Unable to encode model response.", e);
        } finally {
            inferenceRunning = false;
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private boolean authorized(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return false;
        String received = authorization.substring(7).trim();
        String expected = activeToken;
        return !expected.isEmpty() && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }

    private boolean allowRequest(String ip) {
        long now = SystemClock.elapsedRealtime();
        synchronized (rateLock) {
            ArrayDeque<Long> times = requestTimes.computeIfAbsent(ip, key -> new ArrayDeque<>());
            while (!times.isEmpty() && now - times.peekFirst() > 60000L) times.removeFirst();
            if (times.size() >= 6) return false;
            times.addLast(now);
            if (requestTimes.size() > 48) {
                requestTimes.entrySet().removeIf(entry -> entry.getValue().isEmpty() || now - entry.getValue().peekLast() > 60000L);
            }
            return true;
        }
    }

    private HttpRequest readRequest(BufferedInputStream input) throws IOException {
        String line = readLine(input, 2048);
        if (line == null || line.isEmpty()) return null;
        String[] pieces = line.split(" ");
        if (pieces.length < 2) throw new IOException("Malformed HTTP request.");
        HttpRequest request = new HttpRequest(pieces[0], pieces[1]);
        int headerBytes = line.length();
        while (true) {
            String header = readLine(input, MAX_HEADER_BYTES);
            if (header == null) throw new IOException("Incomplete HTTP headers.");
            headerBytes += header.length();
            if (headerBytes > MAX_HEADER_BYTES) throw new IOException("HTTP headers too large.");
            if (header.isEmpty()) break;
            int separator = header.indexOf(':');
            if (separator > 0) request.headers.put(header.substring(0, separator).trim().toLowerCase(Locale.US), header.substring(separator + 1).trim());
        }
        if ("chunked".equalsIgnoreCase(request.headers.get("transfer-encoding"))) throw new IOException("Chunked request bodies are not supported.");
        int length = 0;
        try { length = Integer.parseInt(request.headers.getOrDefault("content-length", "0")); }
        catch (NumberFormatException e) { throw new IOException("Invalid Content-Length."); }
        if (length < 0 || length > MAX_BODY_BYTES) throw new IOException("Request body too large.");
        request.body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(request.body, offset, length - offset);
            if (read < 0) throw new IOException("Incomplete request body.");
            offset += read;
        }
        return request;
    }

    private String readLine(BufferedInputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') return line.toString("UTF-8").replaceFirst("\\r$", "");
            if (line.size() >= maxBytes) throw new IOException("HTTP line too long.");
            line.write(value);
        }
        return line.size() == 0 ? null : line.toString("UTF-8");
    }

    private byte[] readAll(java.io.InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximum) throw new IOException("Local host page is too large.");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private void writeJson(BufferedOutputStream output, int status, JSONObject body) throws IOException {
        writeResponse(output, status, "application/json; charset=utf-8", body.toString().getBytes(StandardCharsets.UTF_8), false);
    }

    private void writeResponse(BufferedOutputStream output, int status, String contentType, byte[] body, boolean html) throws IOException {
        StringBuilder headers = new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
                .append("Content-Type: ").append(contentType).append("\r\n")
                .append("Content-Length: ").append(body.length).append("\r\n")
                .append("Connection: close\r\n")
                .append("Cache-Control: no-store\r\n")
                .append("X-Content-Type-Options: nosniff\r\n")
                .append("Referrer-Policy: no-referrer\r\n");
        if (html) {
            headers.append("X-Frame-Options: DENY\r\n")
                    .append("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:\r\n");
        }
        headers.append("\r\n");
        output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    private void writeChunkedHeaders(BufferedOutputStream output) throws IOException {
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: application/x-ndjson; charset=utf-8\r\nTransfer-Encoding: chunked\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private void writeChunk(BufferedOutputStream output, byte[] data) throws IOException {
        output.write((Integer.toHexString(data.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
        if (data.length > 0) output.write(data);
        output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private void writeSimple(java.io.OutputStream output, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        output.write(("HTTP/1.1 " + status + " " + reason(status) + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    private JSONObject errorJson(String message) {
        JSONObject json = new JSONObject();
        try { json.put("error", message); }
        catch (JSONException ignored) { }
        return json;
    }

    private String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 413: return "Payload Too Large";
            case 429: return "Too Many Requests";
            default: return "Error";
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Local AI host", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shown while this phone is hosting local browser chat.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, HostAiService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Local AI host is on")
                .setContentText("Only devices on this Wi-Fi/hotspot can connect.")
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
                .build();
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private void saveState(String state, String error) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().putString(KEY_STATE, state).putString(KEY_ERROR, error == null ? "" : error);
        if ("running".equals(state)) {
            editor.putString("address", activeAddress).putString("token", activeToken).putInt("port", PORT);
        } else if ("error".equals(state) || "stopped".equals(state)) {
            editor.remove("address").remove("token");
        }
        editor.apply();
    }

    private String friendlyError(Exception error) {
        if (error instanceof SocketException && error.getMessage() != null && error.getMessage().toLowerCase(Locale.US).contains("address already in use")) {
            return "Port " + PORT + " is busy. Stop the other host and try again.";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "Could not start the local host. Reconnect to Wi-Fi and try again." : message;
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    @Override public void onDestroy() {
        stopping = true;
        running = false;
        activeToken = "";
        activeAddress = "";
        ServerSocket listener = serverSocket;
        if (listener != null) closeQuietly(listener);
        Thread thread = listenerThread;
        if (thread != null) thread.interrupt();
        LocalModelBridge.cancelGeneration();
        clients.shutdownNow();
        String savedState = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STATE, "");
        if (!"error".equals(savedState)) saveState("stopped", "");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void closeQuietly(Socket socket) { try { socket.close(); } catch (Exception ignored) { } }
    private void closeQuietly(ServerSocket socket) { try { socket.close(); } catch (Exception ignored) { } }

    private static final class HttpRequest {
        final String method;
        final String target;
        final Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];
        HttpRequest(String method, String target) { this.method = method; this.target = target; }
    }

    private static final class StreamEvent {
        final String token;
        final String error;
        final boolean done;
        private StreamEvent(String token, String error, boolean done) { this.token = token; this.error = error; this.done = done; }
        static StreamEvent token(String value) { return new StreamEvent(value, null, false); }
        static StreamEvent error(String value) { return new StreamEvent(null, value, false); }
        static StreamEvent done() { return new StreamEvent(null, null, true); }
    }
}

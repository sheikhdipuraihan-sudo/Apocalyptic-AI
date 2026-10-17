package bd.sites.raihan.apocalypticai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.StatFs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns the model transfer and its single Apocalyptic AI-branded progress notification. */
public final class ModelDownloadNotificationService extends Service {
    static final String ACTION_START = "bd.sites.raihan.apocalypticai.DOWNLOAD_START";
    static final String ACTION_CANCEL = "bd.sites.raihan.apocalypticai.DOWNLOAD_CANCEL";
    static final String EXTRA_DOWNLOAD_ID = "download_id";
    static final String EXTRA_MODEL_ID = "model_id";
    static final String EXTRA_MODEL_NAME = "model_name";
    static final String EXTRA_MODEL_URL = "model_url";
    static final String EXTRA_FILE_NAME = "file_name";
    static final String EXTRA_EXPECTED_BYTES = "expected_bytes";

    private static final String CHANNEL_ID = "apocalyptic_ai_model_downloads";
    private static final int NOTIFICATION_ID = 7310;
    private static final String STATE_PREFS = "apocalyptic_model_download_state";
    private static final int BUFFER_SIZE = 128 * 1024;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private volatile boolean cancelled;
    private volatile HttpURLConnection activeConnection;
    private long downloadId;
    private long expectedBytes;
    private String modelId = "";
    private String modelName = "local AI model";
    private String fileName = "";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            modelId = intent.getStringExtra(EXTRA_MODEL_ID);
            fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            expectedBytes = intent.getLongExtra(EXTRA_EXPECTED_BYTES, 0L);
            cancelTransfer(modelId);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L);
        expectedBytes = intent.getLongExtra(EXTRA_EXPECTED_BYTES, 0L);
        modelId = intent.getStringExtra(EXTRA_MODEL_ID);
        modelName = intent.getStringExtra(EXTRA_MODEL_NAME);
        fileName = intent.getStringExtra(EXTRA_FILE_NAME);
        String sourceUrl = intent.getStringExtra(EXTRA_MODEL_URL);
        if (modelId == null || modelId.trim().isEmpty() || fileName == null || fileName.trim().isEmpty()
                || sourceUrl == null || downloadId <= 0L) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (modelName == null || modelName.trim().isEmpty()) modelName = "local AI model";

        cancelled = false;
        persistState("running", 0L, expectedBytes, "");
        startForeground(NOTIFICATION_ID, buildProgressNotification(0L, expectedBytes, true));
        if (executor != null) executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();
        final String requestedUrl = sourceUrl;
        executor.execute(() -> download(requestedUrl));
        return START_REDELIVER_INTENT;
    }

    private void download(String sourceUrl) {
        File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) directory = getFilesDir();
        if (!directory.exists() && !directory.mkdirs()) {
            fail("Could not create the app download folder.");
            return;
        }
        File target = new File(directory, fileName);
        File partial = new File(directory, fileName + ".part");
        HttpURLConnection connection = null;
        long downloaded = 0L;
        long lastNotificationAt = 0L;
        try {
            long available = new StatFs(directory.getAbsolutePath()).getAvailableBytes();
            if (expectedBytes > 0L && available < expectedBytes + 32L * 1024L * 1024L) {
                fail("Not enough free storage for this model.");
                return;
            }
            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo network = connectivity == null ? null : connectivity.getActiveNetworkInfo();
            if (network != null && network.isRoaming()) {
                fail("Downloads are disabled while roaming.");
                return;
            }

            URL url = new URL(sourceUrl);
            connection = (HttpURLConnection) url.openConnection();
            activeConnection = connection;
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "ApocalypticAI/1.0 Android model downloader");
            connection.setRequestProperty("Accept", "application/octet-stream, */*");
            connection.connect();
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) throw new java.io.IOException("The model host returned HTTP " + response + ".");

            long responseLength = connection.getContentLengthLong();
            long total = responseLength > 0L ? responseLength : expectedBytes;
            persistState("running", 0L, total, "");
            if (partial.exists() && !partial.delete()) throw new java.io.IOException("Could not clear an incomplete previous download.");
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial, false)) {
                int count;
                while (!cancelled && (count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    downloaded += count;
                    long now = System.currentTimeMillis();
                    if (now - lastNotificationAt >= 700L) {
                        persistState("running", downloaded, total, "");
                        updateProgressNotification(downloaded, total, total <= 0L);
                        lastNotificationAt = now;
                    }
                }
                output.getFD().sync();
            }
            if (cancelled) {
                partial.delete();
                persistState("cancelled", downloaded, total, "");
                stopForegroundCompat(true);
                stopSelf();
                return;
            }
            if (expectedBytes > 0L && downloaded < expectedBytes) {
                throw new java.io.IOException("The downloaded file is incomplete (" + downloaded + " of " + expectedBytes + " bytes).");
            }
            if (target.exists() && !target.delete()) throw new java.io.IOException("Could not replace the incomplete model file.");
            if (!partial.renameTo(target)) throw new java.io.IOException("Could not save the downloaded model file.");
            persistState("complete", downloaded, total > 0L ? total : downloaded, "");
            postCompleteNotification("Download complete", modelName + " was downloaded.");
        } catch (Exception exception) {
            if (partial.exists()) partial.delete();
            if (!cancelled) fail(safeMessage(exception));
            else {
                persistState("cancelled", downloaded, expectedBytes, "");
                stopForegroundCompat(true);
            }
        } finally {
            activeConnection = null;
            if (connection != null) connection.disconnect();
            if (executor != null) executor.shutdown();
            mainHandler.post(this::stopSelf);
        }
    }

    private void cancelTransfer(String requestedModelId) {
        if (requestedModelId != null && !requestedModelId.isEmpty() && !requestedModelId.equals(modelId)) return;
        cancelled = true;
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        if (fileName != null && !fileName.isEmpty()) {
            File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (directory == null) directory = getFilesDir();
            new File(directory, fileName + ".part").delete();
        }
        if (modelId != null && !modelId.isEmpty()) persistState("cancelled", 0L, expectedBytes, "");
        stopForegroundCompat(true);
        if (executor != null) executor.shutdownNow();
    }

    private void fail(String message) {
        persistState("failed", 0L, expectedBytes, message);
        postCompleteNotification("Download failed", modelName + ": " + message);
        mainHandler.post(this::stopSelf);
    }

    private void updateProgressNotification(long downloaded, long total, boolean indeterminate) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildProgressNotification(downloaded, total, indeterminate));
    }

    private Notification buildProgressNotification(long downloaded, long total, boolean indeterminate) {
        int progress = total > 0L ? (int) Math.min(100L, downloaded * 100L / total) : 0;
        String detail = total > 0L ? progress + "% downloaded" : "Downloading…";
        PendingIntent cancelIntent = servicePendingIntent(ACTION_CANCEL, 7312);
        return notificationBuilder(detail)
                .setContentTitle(modelName)
                .setSubText("Model download")
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, indeterminate)
                .addAction(R.drawable.ic_apocalyptic_mark, "Cancel", cancelIntent)
                .build();
    }

    private void postCompleteNotification(String title, String detail) {
        Notification notification = notificationBuilder(detail)
                .setContentTitle(title)
                .setSubText("Model download")
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification);
        stopForegroundCompat(false);
    }

    private Notification.Builder notificationBuilder(String detail) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 7310, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_apocalyptic_mark)
                .setContentText(detail)
                .setContentIntent(content)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
    }

    private PendingIntent servicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, ModelDownloadNotificationService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_MODEL_ID, modelId);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        intent.putExtra(EXTRA_EXPECTED_BYTES, expectedBytes);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Apocalyptic AI downloads", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Branded progress and completion updates for model downloads.");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }

    private void persistState(String status, long downloaded, long total, String error) {
        if (modelId == null || modelId.isEmpty()) return;
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                .putLong("download_id_" + modelId, downloadId)
                .putString("download_status_" + modelId, status)
                .putLong("download_bytes_" + modelId, downloaded)
                .putLong("download_total_" + modelId, total)
                .putString("download_error_" + modelId, error == null ? "" : error)
                .apply();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "Network or storage error." : message;
    }

    private void stopForegroundCompat(boolean removeNotification) {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(removeNotification ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
        else stopForeground(removeNotification);
    }

    @Override public void onDestroy() {
        cancelled = true;
        if (activeConnection != null) activeConnection.disconnect();
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

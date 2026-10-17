package bd.sites.raihan.apocalypticai;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Keeps the model download visible under Apocalyptic AI branding while DownloadManager transfers it. */
public final class ModelDownloadNotificationService extends Service {
    static final String EXTRA_DOWNLOAD_ID = "download_id";
    static final String EXTRA_MODEL_NAME = "model_name";
    static final String EXTRA_EXPECTED_BYTES = "expected_bytes";
    private static final String CHANNEL_ID = "apocalyptic_ai_model_downloads";
    private static final int PROGRESS_NOTIFICATION_ID = 7310;
    private static final int COMPLETE_NOTIFICATION_ID = 7311;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long downloadId;
    private long expectedBytes;
    private String modelName = "local AI model";

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (downloadId <= 0L) { stopMonitoring(); return; }
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Cursor cursor = null;
            try {
                cursor = manager == null ? null : manager.query(new DownloadManager.Query().setFilterById(downloadId));
                if (cursor == null || !cursor.moveToFirst()) {
                    postFinal("Download status unavailable", "Open Apocalyptic AI to check the model download.");
                    stopMonitoring();
                    return;
                }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                long done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                if (total <= 0L) total = expectedBytes;
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    postFinal("Download complete", modelName + " is ready to verify in Apocalyptic AI.");
                    stopMonitoring();
                } else if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    postFinal("Download failed", modelName + " could not be downloaded (error " + reason + "). Open Apocalyptic AI to retry.");
                    stopMonitoring();
                } else {
                    boolean indeterminate = total <= 0L;
                    int progress = !indeterminate ? (int) Math.min(100L, done * 100L / total) : 0;
                    String progressText = indeterminate ? "Downloading " + modelName : "Downloading " + modelName + " · " + progress + "%";
                    showProgress(progressText, progress, indeterminate);
                    handler.postDelayed(this, 1000L);
                }
            } catch (Exception exception) {
                showProgress("Downloading " + modelName, 0, true);
                handler.postDelayed(this, 2500L);
            } finally {
                if (cursor != null) cursor.close();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L);
        modelName = intent.getStringExtra(EXTRA_MODEL_NAME);
        if (modelName == null || modelName.trim().isEmpty()) modelName = "local AI model";
        expectedBytes = intent.getLongExtra(EXTRA_EXPECTED_BYTES, 0L);
        showProgress("Starting " + modelName + " download", 0, true);
        handler.removeCallbacks(poll);
        handler.post(poll);
        return START_NOT_STICKY;
    }

    private void showProgress(String text, int progress, boolean indeterminate) {
        Notification notification = baseNotification(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, indeterminate)
                .build();
        startForeground(PROGRESS_NOTIFICATION_ID, notification);
    }

    private void postFinal(String title, String text) {
        Notification notification = baseNotification(text)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(COMPLETE_NOTIFICATION_ID, notification);
    }

    private Notification.Builder baseNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 7310, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Apocalyptic AI")
                .setContentText(text)
                .setSubText("Apocalyptic AI")
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Apocalyptic AI downloads", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progress and completion updates for model downloads in Apocalyptic AI.");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }

    private void stopMonitoring() {
        handler.removeCallbacks(poll);
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(poll);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

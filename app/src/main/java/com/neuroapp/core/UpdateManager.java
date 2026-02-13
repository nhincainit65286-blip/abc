package com.neuroapp.core;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateManager {

    public interface UpdateCallback {
        void onUpdateAvailable(String version, String changelog);

        void onNoUpdate();

        void onDownloadProgress(int percent);

        void onDownloadComplete(String filePath);

        void onError(String error);
    }

    private static final String CHANNEL_ID = "neuroapp_updates";
    private static final String UPDATE_CHECK_URL = "https://api.github.com/repos/neuroapp/releases/latest";

    private final Context context;
    private final OkHttpClient client;
    private final Gson gson;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final SharedPreferences prefs;

    private String latestVersion = null;
    private String downloadUrl = null;
    private String changelog = null;

    public UpdateManager(Context context) {
        this.context = context;
        this.client = new OkHttpClient();
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences("neuroapp_settings", Context.MODE_PRIVATE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications for app updates");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public boolean isAutoUpdateEnabled() {
        return prefs.getBoolean("auto_update", false);
    }

    public void setAutoUpdate(boolean enabled) {
        prefs.edit().putBoolean("auto_update", enabled).apply();
    }

    public String getCurrentVersion() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    public void checkForUpdateSilent() {
        checkForUpdate(new UpdateCallback() {
            @Override
            public void onUpdateAvailable(String version, String log) {
                if (isAutoUpdateEnabled()) {
                    downloadUpdate(null);
                } else {
                    showUpdateNotification(version, log);
                }
            }

            @Override
            public void onNoUpdate() {
            }

            @Override
            public void onDownloadProgress(int percent) {
            }

            @Override
            public void onDownloadComplete(String filePath) {
                installUpdate(filePath);
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    public void checkForUpdate(UpdateCallback callback) {
        String updateUrl = prefs.getString("update_url", UPDATE_CHECK_URL);

        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(updateUrl)
                        .addHeader("Accept", "application/vnd.github.v3+json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("Failed to check updates: " + response.code()));
                        return;
                    }

                    String body = response.body().string();
                    JsonObject json = gson.fromJson(body, JsonObject.class);

                    latestVersion = json.get("tag_name").getAsString().replace("v", "");
                    changelog = json.has("body") ? json.get("body").getAsString() : "No changelog provided.";

                    // Find APK asset
                    if (json.has("assets") && json.getAsJsonArray("assets").size() > 0) {
                        JsonObject asset = json.getAsJsonArray("assets").get(0).getAsJsonObject();
                        downloadUrl = asset.get("browser_download_url").getAsString();
                    }

                    String currentVersion = getCurrentVersion();
                    if (compareVersions(latestVersion, currentVersion) > 0) {
                        mainHandler.post(() -> callback.onUpdateAvailable(latestVersion, changelog));
                    } else {
                        mainHandler.post(() -> callback.onNoUpdate());
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public void downloadUpdate(UpdateCallback callback) {
        if (downloadUrl == null) {
            if (callback != null)
                callback.onError("No download URL available");
            return;
        }

        executor.execute(() -> {
            try {
                Request request = new Request.Builder().url(downloadUrl).build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        if (callback != null)
                            mainHandler.post(() -> callback.onError("Download failed: " + response.code()));
                        return;
                    }

                    File updateDir = new File(context.getExternalFilesDir(null), "updates");
                    if (!updateDir.exists())
                        updateDir.mkdirs();
                    File apkFile = new File(updateDir, "neuroapp-update.apk");

                    long totalBytes = response.body().contentLength();
                    long downloadedBytes = 0;

                    try (InputStream is = response.body().byteStream();
                            FileOutputStream fos = new FileOutputStream(apkFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                            downloadedBytes += read;
                            if (totalBytes > 0 && callback != null) {
                                int percent = (int) (downloadedBytes * 100 / totalBytes);
                                mainHandler.post(() -> callback.onDownloadProgress(percent));
                            }
                        }
                    }

                    String filePath = apkFile.getAbsolutePath();
                    if (callback != null) {
                        mainHandler.post(() -> callback.onDownloadComplete(filePath));
                    }

                    if (isAutoUpdateEnabled()) {
                        installUpdate(filePath);
                    }
                }
            } catch (Exception e) {
                if (callback != null)
                    mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public void installUpdate(String filePath) {
        File apkFile = new File(filePath);
        if (!apkFile.exists())
            return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apkFile);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = Uri.fromFile(apkFile);
        }

        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void showUpdateNotification(String version, String log) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("NeuroApp Update Available")
                .setContentText("Version " + version + " is ready to download")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(log))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1001, builder.build());
        }
    }

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i].replaceAll("[^0-9]", "")) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i].replaceAll("[^0-9]", "")) : 0;
            if (p1 != p2)
                return p1 - p2;
        }
        return 0;
    }

    public String getUpdateInfo() {
        JsonObject info = new JsonObject();
        info.addProperty("currentVersion", getCurrentVersion());
        info.addProperty("latestVersion", latestVersion != null ? latestVersion : getCurrentVersion());
        info.addProperty("changelog", changelog != null ? changelog : "");
        info.addProperty("autoUpdate", isAutoUpdateEnabled());
        info.addProperty("updateAvailable",
                latestVersion != null && compareVersions(latestVersion, getCurrentVersion()) > 0);
        return gson.toJson(info);
    }
}

package com.neuroapp.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.neuroapp.core.AIEngine;
import com.neuroapp.core.PluginManager;
import com.neuroapp.core.ProxyServer;
import com.neuroapp.core.UpdateManager;
import com.neuroapp.main.MainActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class AppBridge {

    private final MainActivity activity;
    private final AIEngine aiEngine;
    private final UpdateManager updateManager;
    private final PluginManager pluginManager;
    private final Gson gson;
    private final SharedPreferences prefs;
    private final File projectsDir;
    private ProxyServer proxyServer;

    public AppBridge(MainActivity activity, AIEngine aiEngine,
            UpdateManager updateManager, PluginManager pluginManager) {
        this.activity = activity;
        this.aiEngine = aiEngine;
        this.updateManager = updateManager;
        this.pluginManager = pluginManager;
        this.gson = new Gson();
        this.prefs = activity.getSharedPreferences("neuroapp_settings", Context.MODE_PRIVATE);
        this.projectsDir = new File(activity.getFilesDir(), "projects");
        if (!projectsDir.exists())
            projectsDir.mkdirs();
    }

    // ==================== AI Engine ====================

    @JavascriptInterface
    public void generateCode(String prompt, String callbackId) {
        aiEngine.generateCode(prompt, new AIEngine.AICallback() {
            @Override
            public void onSuccess(String result) {
                callJS("onAIResponse", callbackId, escapeJS(result), "null");
            }

            @Override
            public void onError(String error) {
                callJS("onAIResponse", callbackId, "null", "'" + escapeJS(error) + "'");
            }
        });
    }

    @JavascriptInterface
    public void analyzeCode(String code, String callbackId) {
        aiEngine.analyzeCode(code, new AIEngine.AICallback() {
            @Override
            public void onSuccess(String result) {
                callJS("onAIResponse", callbackId, escapeJS(result), "null");
            }

            @Override
            public void onError(String error) {
                callJS("onAIResponse", callbackId, "null", "'" + escapeJS(error) + "'");
            }
        });
    }

    @JavascriptInterface
    public void autoComplete(String code, int cursorPos, String callbackId) {
        aiEngine.autoComplete(code, cursorPos, new AIEngine.AICallback() {
            @Override
            public void onSuccess(String result) {
                callJS("onAIResponse", callbackId, escapeJS(result), "null");
            }

            @Override
            public void onError(String error) {
                callJS("onAIResponse", callbackId, "null", "'" + escapeJS(error) + "'");
            }
        });
    }

    @JavascriptInterface
    public void generateFromDescription(String desc, String language, String callbackId) {
        aiEngine.generateFromDescription(desc, language, new AIEngine.AICallback() {
            @Override
            public void onSuccess(String result) {
                callJS("onAIResponse", callbackId, escapeJS(result), "null");
            }

            @Override
            public void onError(String error) {
                callJS("onAIResponse", callbackId, "null", "'" + escapeJS(error) + "'");
            }
        });
    }

    @JavascriptInterface
    public void fixCode(String code, String error, String callbackId) {
        aiEngine.fixCode(code, error, new AIEngine.AICallback() {
            @Override
            public void onSuccess(String result) {
                callJS("onAIResponse", callbackId, escapeJS(result), "null");
            }

            @Override
            public void onError(String err) {
                callJS("onAIResponse", callbackId, "null", "'" + escapeJS(err) + "'");
            }
        });
    }

    @JavascriptInterface
    public void selfEvolve(String code, String goals, String callbackId) {
        aiEngine.selfEvolve(code, goals, new AIEngine.AICallback() {
            @Override
            public void onSuccess(String result) {
                callJS("onAIResponse", callbackId, escapeJS(result), "null");
            }

            @Override
            public void onError(String error) {
                callJS("onAIResponse", callbackId, "null", "'" + escapeJS(error) + "'");
            }
        });

    }

    @JavascriptInterface
    public void fetchProxyPalModels(String callbackId) {
        aiEngine.fetchProxyPalModels(new AIEngine.AICallback() {
            @Override
            public void onSuccess(String result) {
                callJS("onProxyPalModelsFetched", callbackId, escapeJS(result), "null");
            }

            @Override
            public void onError(String error) {
                callJS("onProxyPalModelsFetched", callbackId, "null", "'" + escapeJS(error) + "'");
            }
        });
    }

    @JavascriptInterface
    public boolean startProxyServer() {
        if (proxyServer != null && proxyServer.isAlive()) {
            return true;
        }
        try {
            proxyServer = new ProxyServer(aiEngine, 8317);
            proxyServer.start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @JavascriptInterface
    public void stopProxyServer() {
        if (proxyServer != null) {
            proxyServer.stop();
            proxyServer = null;
        }
    }

    @JavascriptInterface
    public boolean isProxyServerRunning() {
        return proxyServer != null && proxyServer.isAlive();
    }

    @JavascriptInterface
    public String getProxyLogs() {
        if (proxyServer != null) {
            return gson.toJson(proxyServer.getLogs());
        }
        return "[]";
    }

    @JavascriptInterface
    public void clearProxyLogs() {
        if (proxyServer != null) {
            proxyServer.clearLogs();
        }
    }

    @JavascriptInterface
    public String getProxyConfig() {
        if (proxyServer != null) {
            return gson.toJson(proxyServer.getConfig());
        }
        return "{}";
    }

    @JavascriptInterface
    public void saveProxyConfig(String jsonConfig) {
        if (proxyServer != null) {
            try {
                ProxyConfig config = gson.fromJson(jsonConfig, ProxyConfig.class);
                proxyServer.updateConfig(config);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== Settings ====================

    @JavascriptInterface
    public String getSettings() {
        JsonObject settings = new JsonObject();
        settings.addProperty("aiProvider", aiEngine.getProvider());
        settings.addProperty("apiKeyOpenai", maskApiKey(prefs.getString("api_key_openai", "")));
        settings.addProperty("apiKeyGemini", maskApiKey(prefs.getString("api_key_gemini", "")));
        settings.addProperty("apiKeyAnthropic", maskApiKey(prefs.getString("api_key_anthropic", "")));
        settings.addProperty("proxyPalUrl", aiEngine.getProxyPalUrl());
        settings.addProperty("proxyPalModel", aiEngine.getProxyPalModel());
        settings.addProperty("customHeaders", aiEngine.getCustomHeaders());
        settings.addProperty("autoUpdate", updateManager.isAutoUpdateEnabled());
        settings.addProperty("currentVersion", updateManager.getCurrentVersion());
        settings.addProperty("theme", prefs.getString("theme", "dark"));
        settings.addProperty("fontSize", prefs.getInt("font_size", 14));
        settings.addProperty("autoSave", prefs.getBoolean("auto_save", true));
        settings.addProperty("updateUrl", prefs.getString("update_url", ""));
        return settings.toString();
    }

    @JavascriptInterface
    public void saveSetting(String key, String value) {
        SharedPreferences.Editor editor = prefs.edit();
        switch (key) {
            case "aiProvider":
                aiEngine.setProvider(value);
                break;
            case "apiKeyOpenai":
                aiEngine.setApiKey("openai", value);
                break;
            case "apiKeyGemini":
                aiEngine.setApiKey("gemini", value);
                break;
            case "autoUpdate":
                updateManager.setAutoUpdate(Boolean.parseBoolean(value));
                break;
            case "theme":
                editor.putString("theme", value);
                break;
            case "fontSize":
                editor.putInt("font_size", Integer.parseInt(value));
                break;
            case "autoSave":
                editor.putBoolean("auto_save", Boolean.parseBoolean(value));
                break;
            case "updateUrl":
                editor.putString("update_url", value);
                break;
            case "proxyPalUrl":
                aiEngine.setProxyPalUrl(value);
                break;
            case "proxyPalModel":
                aiEngine.setProxyPalModel(value);
                break;
            case "customHeaders":
                aiEngine.setCustomHeaders(value);
                break;
        }
        editor.apply();
    }

    // ==================== File System ====================

    @JavascriptInterface
    public String listProjects() {
        File[] files = projectsDir.listFiles();
        if (files == null)
            return "[]";

        JsonObject[] projects = Arrays.stream(files)
                .filter(File::isDirectory)
                .map(f -> {
                    JsonObject p = new JsonObject();
                    p.addProperty("name", f.getName());
                    p.addProperty("path", f.getAbsolutePath());
                    p.addProperty("files", countFiles(f));
                    p.addProperty("lastModified", f.lastModified());
                    return p;
                })
                .toArray(JsonObject[]::new);

        return gson.toJson(projects);
    }

    @JavascriptInterface
    public String createProject(String name) {
        File project = new File(projectsDir, name);
        if (project.exists()) {
            return errorJson("Project already exists");
        }
        project.mkdirs();
        // Create default file
        try (FileWriter writer = new FileWriter(new File(project, "main.js"))) {
            writer.write("// " + name + " - Created by NeuroApp\n// Start coding here!\n\n");
        } catch (IOException e) {
            return errorJson(e.getMessage());
        }
        return successJson("Project created: " + name);
    }

    @JavascriptInterface
    public String listFiles(String projectName) {
        File project = new File(projectsDir, projectName);
        if (!project.exists())
            return "[]";
        return gson.toJson(listFilesRecursive(project, ""));
    }

    @JavascriptInterface
    public String readFile(String projectName, String filePath) {
        File file = new File(new File(projectsDir, projectName), filePath);
        if (!file.exists())
            return "";

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    @JavascriptInterface
    public String writeFile(String projectName, String filePath, String content) {
        File project = new File(projectsDir, projectName);
        if (!project.exists())
            project.mkdirs();

        File file = new File(project, filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
            return successJson("File saved");
        } catch (IOException e) {
            return errorJson(e.getMessage());
        }
    }

    @JavascriptInterface
    public String deleteFile(String projectName, String filePath) {
        File file = new File(new File(projectsDir, projectName), filePath);
        if (file.exists() && file.delete()) {
            return successJson("File deleted");
        }
        return errorJson("Failed to delete file");
    }

    @JavascriptInterface
    public String createFile(String projectName, String filePath) {
        File file = new File(new File(projectsDir, projectName), filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();

        try {
            if (file.createNewFile()) {
                return successJson("File created");
            }
            return errorJson("File already exists");
        } catch (IOException e) {
            return errorJson(e.getMessage());
        }
    }

    // ==================== Updates ====================

    @JavascriptInterface
    public String getUpdateInfo() {
        return updateManager.getUpdateInfo();
    }

    @JavascriptInterface
    public void checkForUpdate(String callbackId) {
        updateManager.checkForUpdate(new UpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(String version, String changelog) {
                callJS("onUpdateCheck", callbackId,
                        "'" + version + "'", "'" + escapeJS(changelog) + "'", "'available'");
            }

            @Override
            public void onNoUpdate() {
                callJS("onUpdateCheck", callbackId, "'current'", "''", "'up_to_date'");
            }

            @Override
            public void onDownloadProgress(int percent) {
            }

            @Override
            public void onDownloadComplete(String filePath) {
            }

            @Override
            public void onError(String error) {
                callJS("onUpdateCheck", callbackId, "'error'", "'" + escapeJS(error) + "'", "'error'");
            }
        });
    }

    @JavascriptInterface
    public void downloadUpdate(String callbackId) {
        updateManager.downloadUpdate(new UpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(String v, String c) {
            }

            @Override
            public void onNoUpdate() {
            }

            @Override
            public void onDownloadProgress(int percent) {
                callJS("onDownloadProgress", callbackId, String.valueOf(percent));
            }

            @Override
            public void onDownloadComplete(String filePath) {
                callJS("onDownloadComplete", callbackId, "'" + escapeJS(filePath) + "'");
            }

            @Override
            public void onError(String error) {
                callJS("onDownloadError", callbackId, "'" + escapeJS(error) + "'");
            }
        });
    }

    @JavascriptInterface
    public void installUpdate(String filePath) {
        updateManager.installUpdate(filePath);
    }

    // ==================== Plugins ====================

    @JavascriptInterface
    public String getInstalledPlugins() {
        return pluginManager.getInstalledPlugins();
    }

    @JavascriptInterface
    public String getAvailablePlugins() {
        return pluginManager.getAvailablePlugins();
    }

    @JavascriptInterface
    public String installPlugin(String id, String name, String version, String code, String desc) {
        return pluginManager.installPlugin(id, name, version, code, desc);
    }

    @JavascriptInterface
    public String uninstallPlugin(String id) {
        return pluginManager.uninstallPlugin(id);
    }

    @JavascriptInterface
    public String togglePlugin(String id, boolean enabled) {
        return pluginManager.togglePlugin(id, enabled);
    }

    // ==================== Dashboard Stats ====================

    @JavascriptInterface
    public String getDashboardStats() {
        JsonObject stats = new JsonObject();
        stats.addProperty("totalProjects", countDirectories(projectsDir));
        stats.addProperty("totalFiles", countFiles(projectsDir));
        stats.addProperty("linesGenerated", prefs.getInt("lines_generated", 0));
        stats.addProperty("aiRequests", prefs.getInt("ai_requests", 0));
        stats.addProperty("pluginsInstalled", pluginManager.getInstalledPlugins().length());
        stats.addProperty("appVersion", updateManager.getCurrentVersion());
        stats.addProperty("uptime",
                System.currentTimeMillis() - prefs.getLong("first_launch", System.currentTimeMillis()));

        // Track first launch
        if (!prefs.contains("first_launch")) {
            prefs.edit().putLong("first_launch", System.currentTimeMillis()).apply();
        }

        return stats.toString();
    }

    @JavascriptInterface
    public void incrementStat(String key, int amount) {
        int current = prefs.getInt(key, 0);
        prefs.edit().putInt(key, current + amount).apply();
    }

    // ==================== Helpers ====================

    private void callJS(String function, String... args) {
        StringBuilder js = new StringBuilder();
        js.append("if(typeof ").append(function).append(" === 'function') ");
        js.append(function).append("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0)
                js.append(", ");
            js.append(args[i]);
        }
        js.append(");");
        activity.runOnWebView(js.toString());
    }

    private String escapeJS(String text) {
        if (text == null)
            return "";
        return text
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 8)
            return key;
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private String successJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.addProperty("message", message);
        return obj.toString();
    }

    private String errorJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", false);
        obj.addProperty("message", message);
        return obj.toString();
    }

    private int countFiles(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile())
                    count++;
                else if (f.isDirectory())
                    count += countFiles(f);
            }
        }
        return count;
    }

    private int countDirectories(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory())
                    count++;
            }
        }
        return count;
    }

    private java.util.List<JsonObject> listFilesRecursive(File dir, String prefix) {
        java.util.List<JsonObject> result = new java.util.ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null)
            return result;

        java.util.Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory())
                return -1;
            if (!a.isDirectory() && b.isDirectory())
                return 1;
            return a.getName().compareTo(b.getName());
        });

        for (File f : files) {
            String path = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
            JsonObject item = new JsonObject();
            item.addProperty("name", f.getName());
            item.addProperty("path", path);
            item.addProperty("isDir", f.isDirectory());
            item.addProperty("size", f.length());
            item.addProperty("lastModified", f.lastModified());
            result.add(item);

            if (f.isDirectory()) {
                result.addAll(listFilesRecursive(f, path));
            }
        }
        return result;
    }
}

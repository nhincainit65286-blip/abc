package com.neuroapp.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginManager {

    private final Context context;
    private final Gson gson;
    private final SharedPreferences prefs;
    private final File pluginsDir;
    private final Map<String, JsonObject> installedPlugins;

    public PluginManager(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.prefs = context.getSharedPreferences("neuroapp_plugins", Context.MODE_PRIVATE);
        this.pluginsDir = new File(context.getFilesDir(), "plugins");
        if (!pluginsDir.exists())
            pluginsDir.mkdirs();
        this.installedPlugins = loadInstalledPlugins();
    }

    private Map<String, JsonObject> loadInstalledPlugins() {
        Map<String, JsonObject> plugins = new HashMap<>();
        String json = prefs.getString("installed_plugins", "{}");
        try {
            Type type = new TypeToken<Map<String, JsonObject>>() {
            }.getType();
            plugins = gson.fromJson(json, type);
        } catch (Exception e) {
            plugins = new HashMap<>();
        }
        return plugins != null ? plugins : new HashMap<>();
    }

    private void saveInstalledPlugins() {
        prefs.edit().putString("installed_plugins", gson.toJson(installedPlugins)).apply();
    }

    public String installPlugin(String id, String name, String version, String code, String description) {
        try {
            // Save plugin code
            File pluginFile = new File(pluginsDir, id + ".js");
            try (FileWriter writer = new FileWriter(pluginFile)) {
                writer.write(code);
            }

            // Save plugin metadata
            JsonObject meta = new JsonObject();
            meta.addProperty("id", id);
            meta.addProperty("name", name);
            meta.addProperty("version", version);
            meta.addProperty("description", description);
            meta.addProperty("enabled", true);
            meta.addProperty("installedAt", System.currentTimeMillis());

            installedPlugins.put(id, meta);
            saveInstalledPlugins();

            return gson.toJson(createResponse(true, "Plugin installed: " + name));
        } catch (Exception e) {
            return gson.toJson(createResponse(false, "Install failed: " + e.getMessage()));
        }
    }

    public String uninstallPlugin(String id) {
        try {
            File pluginFile = new File(pluginsDir, id + ".js");
            if (pluginFile.exists())
                pluginFile.delete();
            installedPlugins.remove(id);
            saveInstalledPlugins();
            return gson.toJson(createResponse(true, "Plugin uninstalled"));
        } catch (Exception e) {
            return gson.toJson(createResponse(false, "Uninstall failed: " + e.getMessage()));
        }
    }

    public String togglePlugin(String id, boolean enabled) {
        JsonObject meta = installedPlugins.get(id);
        if (meta != null) {
            meta.addProperty("enabled", enabled);
            saveInstalledPlugins();
            return gson.toJson(createResponse(true, enabled ? "Plugin enabled" : "Plugin disabled"));
        }
        return gson.toJson(createResponse(false, "Plugin not found"));
    }

    public String getPluginCode(String id) {
        File pluginFile = new File(pluginsDir, id + ".js");
        if (!pluginFile.exists())
            return "";

        try (BufferedReader reader = new BufferedReader(new FileReader(pluginFile))) {
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

    public String getInstalledPlugins() {
        JsonArray array = new JsonArray();
        for (JsonObject meta : installedPlugins.values()) {
            array.add(meta);
        }
        return array.toString();
    }

    public String getAvailablePlugins() {
        // Built-in plugin catalog
        JsonArray catalog = new JsonArray();

        addCatalogPlugin(catalog, "syntax-themes", "Syntax Themes", "1.0.0",
                "Additional syntax highlighting themes", "editor");
        addCatalogPlugin(catalog, "git-integration", "Git Integration", "1.0.0",
                "Basic Git operations (commit, push, pull)", "tools");
        addCatalogPlugin(catalog, "code-snippets", "Code Snippets", "1.0.0",
                "Library of reusable code snippets", "productivity");
        addCatalogPlugin(catalog, "markdown-preview", "Markdown Preview", "1.0.0",
                "Live preview for Markdown files", "editor");
        addCatalogPlugin(catalog, "terminal-emulator", "Terminal Emulator", "1.0.0",
                "Built-in terminal for running commands", "tools");
        addCatalogPlugin(catalog, "auto-formatter", "Auto Formatter", "1.0.0",
                "Auto-format code on save", "productivity");
        addCatalogPlugin(catalog, "ai-docs", "AI Documentation", "1.0.0",
                "Auto-generate documentation from code", "ai");
        addCatalogPlugin(catalog, "project-templates", "Project Templates", "1.0.0",
                "Quick-start project templates", "productivity");

        return catalog.toString();
    }

    private void addCatalogPlugin(JsonArray catalog, String id, String name, String version,
            String description, String category) {
        JsonObject plugin = new JsonObject();
        plugin.addProperty("id", id);
        plugin.addProperty("name", name);
        plugin.addProperty("version", version);
        plugin.addProperty("description", description);
        plugin.addProperty("category", category);
        plugin.addProperty("installed", installedPlugins.containsKey(id));
        catalog.add(plugin);
    }

    private JsonObject createResponse(boolean success, String message) {
        JsonObject resp = new JsonObject();
        resp.addProperty("success", success);
        resp.addProperty("message", message);
        return resp;
    }
}

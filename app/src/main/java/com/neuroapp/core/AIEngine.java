package com.neuroapp.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AIEngine {

    public interface AICallback {
        void onSuccess(String result);

        void onError(String error);
    }

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final String PROXYPAL_DEFAULT_URL = "http://localhost:8317/v1/chat/completions";

    private final Context context;
    private final OkHttpClient client;
    private final Gson gson;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final SharedPreferences prefs;

    public AIEngine(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.executor = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences("neuroapp_settings", Context.MODE_PRIVATE);
    }

    public String getProvider() {
        return prefs.getString("ai_provider", "gemini");
    }

    public void setProvider(String provider) {
        prefs.edit().putString("ai_provider", provider).apply();
    }

    public String getApiKey() {
        String provider = getProvider();
        return prefs.getString("api_key_" + provider, "");
    }

    public void setApiKey(String provider, String key) {
        prefs.edit().putString("api_key_" + provider, key).apply();
    }

    public String getProxyPalUrl() {
        return prefs.getString("proxypal_url", PROXYPAL_DEFAULT_URL);
    }

    public void setProxyPalUrl(String url) {
        prefs.edit().putString("proxypal_url", url).apply();
    }

    public String getProxyPalModel() {
        return prefs.getString("proxypal_model", "claude-sonnet-4-20250514");
    }

    public void setProxyPalModel(String model) {
        prefs.edit().putString("proxypal_model", model).apply();
    }

    public void generateCode(String prompt, AICallback callback) {
        String provider = getProvider();
        String apiKey = getApiKey();

        // ProxyPal doesn't require an API key (proxy handles auth)
        if (!"proxypal".equals(provider) && (apiKey == null || apiKey.isEmpty())) {
            callback.onError("API key not set. Please configure in Settings.");
            return;
        }

        executor.execute(() -> {
            try {
                String result;
                if ("openai".equals(provider)) {
                    result = callOpenAI(apiKey, prompt);
                } else if ("proxypal".equals(provider)) {
                    result = callProxyPal(prompt);
                } else {
                    result = callGemini(apiKey, prompt);
                }
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public void analyzeCode(String code, AICallback callback) {
        String prompt = "Analyze this code and suggest improvements, optimizations, and potential bugs. "
                + "Format your response as:\n"
                + "## Analysis\n[analysis]\n"
                + "## Suggestions\n[numbered list]\n"
                + "## Optimized Code\n```\n[improved code]\n```\n\n"
                + "Code to analyze:\n```\n" + code + "\n```";
        generateCode(prompt, callback);
    }

    public void autoComplete(String code, int cursorPosition, AICallback callback) {
        String prompt = "Complete this code at the cursor position (marked with |CURSOR|). "
                + "Only return the completion text, nothing else:\n\n"
                + code.substring(0, cursorPosition) + "|CURSOR|" + code.substring(cursorPosition);
        generateCode(prompt, callback);
    }

    public void generateFromDescription(String description, String language, AICallback callback) {
        String prompt = "Generate " + language + " code for the following description. "
                + "Return only the code, with clear comments:\n\n" + description;
        generateCode(prompt, callback);
    }

    public void fixCode(String code, String error, AICallback callback) {
        String prompt = "Fix this code that has the following error:\n"
                + "Error: " + error + "\n\n"
                + "Code:\n```\n" + code + "\n```\n\n"
                + "Return the fixed code with comments explaining the fix.";
        generateCode(prompt, callback);
    }

    public void selfEvolve(String currentCode, String goals, AICallback callback) {
        String prompt = "You are a self-evolving AI app. Improve this code to better achieve these goals:\n"
                + "Goals: " + goals + "\n\n"
                + "Current code:\n```\n" + currentCode + "\n```\n\n"
                + "Return the evolved version with:\n"
                + "1. New features added\n"
                + "2. Performance improvements\n"
                + "3. Better error handling\n"
                + "4. Documentation\n"
                + "Explain each change.";
        generateCode(prompt, callback);
    }

    private String callOpenAI(String apiKey, String prompt) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", "You are NeuroApp AI, a powerful code generation and analysis engine. "
                + "You generate clean, efficient, well-documented code. "
                + "You always follow best practices and modern patterns.");
        messages.add(systemMsg);
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", "gpt-4o-mini");
        body.add("messages", messages);
        body.addProperty("max_tokens", 4096);
        body.addProperty("temperature", 0.7);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("API Error (" + response.code() + "): " + responseBody);
            }
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        }
    }

    private String callGemini(String apiKey, String prompt) throws IOException {
        JsonObject part = new JsonObject();
        part.addProperty("text", "You are NeuroApp AI, a powerful code generation and analysis engine. "
                + "Generate clean, efficient, well-documented code. Follow best practices.\n\n" + prompt);

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject body = new JsonObject();
        body.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.7);
        generationConfig.addProperty("maxOutputTokens", 8192);
        body.add("generationConfig", generationConfig);

        String url = GEMINI_URL + "?key=" + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Gemini API Error (" + response.code() + "): " + responseBody);
            }
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            return json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        }
    }

    private String callProxyPal(String prompt) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", "You are NeuroApp AI, a powerful code generation and analysis engine. "
                + "You generate clean, efficient, well-documented code. "
                + "You always follow best practices and modern patterns.");
        messages.add(systemMsg);
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", getProxyPalModel());
        body.add("messages", messages);
        body.addProperty("max_tokens", 4096);
        body.addProperty("temperature", 0.7);

        String proxyUrl = getProxyPalUrl();

        Request request = new Request.Builder()
                .url(proxyUrl)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("ProxyPal Error (" + response.code() + "): " + responseBody);
            }
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        }
    }

    public void fetchProxyPalModels(AICallback callback) {
        String proxyUrl = getProxyPalUrl().replace("/chat/completions", "/models");
        // fallback if user entered base url
        if (!proxyUrl.endsWith("/models")) {
            proxyUrl = getProxyPalUrl().replace("/v1/chat/completions", "/v1/models");
        }

        Request request = new Request.Builder()
                .url(proxyUrl)
                .get()
                .build();

        executor.execute(() -> {
            try {
                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    if (!response.isSuccessful()) {
                        throw new IOException("Error fetching models (" + response.code() + ")");
                    }
                    mainHandler.post(() -> callback.onSuccess(responseBody));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}

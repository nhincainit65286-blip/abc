package com.neuroapp.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import fi.iki.elonen.NanoHTTPD;

public class ProxyServer extends NanoHTTPD {

    private final AIEngine aiEngine;
    private final Gson gson;
    private final java.util.LinkedList<ProxyLog> logs = new java.util.LinkedList<>();
    private static final int MAX_LOGS = 50;

    // Default Config
    private ProxyConfig config;

    public ProxyServer(AIEngine aiEngine, int port) {
        super(port);
        this.aiEngine = aiEngine;
        this.gson = new Gson();
        this.config = new ProxyConfig();
        // Default Routes
        config.routes.add(new ProxyConfig.ProxyRoute("gpt-.*", "openai"));
        config.routes.add(new ProxyConfig.ProxyRoute("claude-.*", "anthropic"));
        config.routes.add(new ProxyConfig.ProxyRoute("gemini-.*", "gemini"));
    }

    public void updateConfig(ProxyConfig newConfig) {
        this.config = newConfig;
    }

    public ProxyConfig getConfig() {
        return config;
    }

    public synchronized java.util.List<ProxyLog> getLogs() {
        return new java.util.ArrayList<>(logs);
    }

    public synchronized void clearLogs() {
        logs.clear();
    }

    private synchronized void addLog(ProxyLog log) {
        logs.addFirst(log);
        if (logs.size() > MAX_LOGS) {
            logs.removeLast();
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        if (Method.POST.equals(method) && "/v1/chat/completions".equals(uri)) {
            return handleChatCompletions(session);
        } else if (Method.GET.equals(method) && "/v1/models".equals(uri)) {
            return handleModels(session);
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }

    private Response handleModels(IHTTPSession session) {
        return handleModels();
    }

    private Response handleModels() {
        JsonObject response = new JsonObject();
        response.addProperty("object", "list");

        JsonArray data = new JsonArray();
        data.add(createModelObject("gpt-4o"));
        data.add(createModelObject("gpt-4o-mini"));
        data.add(createModelObject("claude-3-5-sonnet-20240620"));
        data.add(createModelObject("gemini-2.0-flash"));

        response.add("data", data);

        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(response));
    }

    private JsonObject createModelObject(String id) {
        JsonObject model = new JsonObject();
        model.addProperty("id", id);
        model.addProperty("object", "model");
        model.addProperty("created", System.currentTimeMillis() / 1000);
        model.addProperty("owned_by", "neuroapp");
        return model;
    }

    private Response handleChatCompletions(IHTTPSession session) {
        Map<String, String> files = new HashMap<>();
        long startTime = System.currentTimeMillis();
        String logId = java.util.UUID.randomUUID().toString().substring(0, 8);
        String model = "unknown";
        String provider = "unknown";
        String requestBodyStr = "";

        try {
            session.parseBody(files);
            String postData = files.get("postData");

            if (postData == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing body");
            }

            requestBodyStr = postData;
            JsonObject body = JsonParser.parseString(postData).getAsJsonObject();
            model = body.has("model") ? body.get("model").getAsString() : "gpt-4o";

            String prompt = "";
            if (body.has("messages")) {
                JsonArray messages = body.getAsJsonArray("messages");
                for (int i = messages.size() - 1; i >= 0; i--) {
                    JsonObject msg = messages.get(i).getAsJsonObject();
                    if ("user".equals(msg.get("role").getAsString())) {
                        prompt = msg.get("content").getAsString();
                        break;
                    }
                }
            }

            if (prompt.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No user message found");
            }

            // ROUTING LOGIC
            String targetProvider = "openai"; // default fallback
            String targetApiKey = null;
            String targetUrl = null;
            String originalModel = model;

            // Match route
            for (ProxyConfig.ProxyRoute route : config.routes) {
                if (model.matches(route.pattern)) {
                    targetProvider = route.provider;
                    targetApiKey = route.apiKey;
                    targetUrl = route.targetUrl;
                    if (route.modelOverride != null && !route.modelOverride.isEmpty()) {
                        model = route.modelOverride;
                    }
                    break;
                }
            }

            provider = targetProvider;

            ProxyLog log = new ProxyLog(logId, "POST", "/v1/chat/completions", originalModel, provider);
            log.requestBody = requestBodyStr.length() > 200 ? requestBodyStr.substring(0, 200) + "..." : requestBodyStr;

            CompletableFuture<String> future = new CompletableFuture<>();

            // NOTE: AIEngine currently doesn't support passing custom URL/Key dynamically
            // per request easily.
            // For now, we support Provider switching. Real implementation would require
            // updating AIEngine to accept transient context.
            // We will use the existing generateCodeWithProvider but in future we should
            // pass a "RequestContext" object.

            aiEngine.generateCodeWithProvider(prompt, targetProvider, new AIEngine.AICallback() {
                @Override
                public void onSuccess(String result) {
                    future.complete(result);
                }

                @Override
                public void onError(String error) {
                    future.completeExceptionally(new RuntimeException(error));
                }
            });

            String aiResult;
            try {
                aiResult = future.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.status = 500;
                log.durationMs = System.currentTimeMillis() - startTime;
                log.responseBody = "Error: " + e.getMessage();
                addLog(log);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                        "AI processing failed: " + e.getMessage());
            }

            JsonObject response = new JsonObject();
            String id = "chatcmpl-" + System.currentTimeMillis();
            response.addProperty("id", id);
            response.addProperty("object", "chat.completion");
            response.addProperty("created", System.currentTimeMillis() / 1000);
            response.addProperty("model", model);

            JsonArray choices = new JsonArray();
            JsonObject choice = new JsonObject();
            choice.addProperty("index", 0);

            JsonObject message = new JsonObject();
            message.addProperty("role", "assistant");
            message.addProperty("content", aiResult);

            choice.add("message", message);
            choice.addProperty("finish_reason", "stop");

            choices.add(choice);
            response.add("choices", choices);

            JsonObject usage = new JsonObject();
            int pTokens = prompt.length() / 4;
            int cTokens = aiResult.length() / 4;
            usage.addProperty("prompt_tokens", pTokens);
            usage.addProperty("completion_tokens", cTokens);
            usage.addProperty("total_tokens", pTokens + cTokens);
            response.add("usage", usage);

            String responseStr = gson.toJson(response);

            log.status = 200;
            log.durationMs = System.currentTimeMillis() - startTime;
            log.responseBody = responseStr.length() > 200 ? responseStr.substring(0, 200) + "..." : responseStr;
            log.promptTokens = pTokens;
            log.completionTokens = cTokens;
            addLog(log);

            return newFixedLengthResponse(Response.Status.OK, "application/json", responseStr);

        } catch (Exception e) {
            ProxyLog log = new ProxyLog(logId, "POST", "/v1/chat/completions", model, provider);
            log.status = 500;
            log.durationMs = System.currentTimeMillis() - startTime;
            log.responseBody = "Exception: " + e.getMessage();
            addLog(log);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                    "Server error: " + e.getMessage());
        }
    }
}

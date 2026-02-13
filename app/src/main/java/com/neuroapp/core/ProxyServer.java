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

    public ProxyServer(AIEngine aiEngine, int port) {
        super(port);
        this.aiEngine = aiEngine;
        this.gson = new Gson();
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

    private Response handleModels(IHTTPSession session) {
        return handleModels();
    }

    private Response handleChatCompletions(IHTTPSession session) {
        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
            String postData = files.get("postData");

            if (postData == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing body");
            }

            JsonObject body = JsonParser.parseString(postData).getAsJsonObject();
            String model = body.has("model") ? body.get("model").getAsString() : "gpt-4o";

            // Extract the last user message to use as prompt
            // NOTE: AIEngine currently supports only single prompt.
            // Future update should pass full history.
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

            // Determine provider based on model alias or settings if internal
            // Assume external client calls us, so we use internal keys

            String targetProvider = "openai"; // default
            if (model.contains("claude"))
                targetProvider = "anthropic";
            else if (model.contains("gemini"))
                targetProvider = "gemini";
            else if (model.contains("gpt"))
                targetProvider = "openai";

            CompletableFuture<String> future = new CompletableFuture<>();

            // Use a specific method that allows passing provider
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
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                        "AI processing failed: " + e.getMessage());
            }

            // Construct OpenAI-compatible response
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

            // Add usage dummy
            JsonObject usage = new JsonObject();
            usage.addProperty("prompt_tokens", prompt.length() / 4);
            usage.addProperty("completion_tokens", aiResult.length() / 4);
            usage.addProperty("total_tokens", (prompt.length() + aiResult.length()) / 4);
            response.add("usage", usage);

            return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(response));

        } catch (IOException | ResponseException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                    "Server error: " + e.getMessage());
        }
    }
}

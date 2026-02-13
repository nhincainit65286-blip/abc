package com.neuroapp.core;

public class ProxyLog {
    public String id;
    public long timestamp;
    public String method;
    public String endpoint;
    public String model;
    public String provider;
    public int status;
    public long durationMs;
    public String requestBody; // truncated
    public String responseBody; // truncated
    public int promptTokens;
    public int completionTokens;

    public ProxyLog(String id, String method, String endpoint, String model, String provider) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
        this.method = method;
        this.endpoint = endpoint;
        this.model = model;
        this.provider = provider;
    }
}

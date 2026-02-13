package com.neuroapp.core;

import java.util.ArrayList;
import java.util.List;

public class ProxyConfig {
    public List<ProxyRoute> routes = new ArrayList<>();

    public static class ProxyRoute {
        public String id;
        public String pattern; // Regex for model name
        public String provider; // openai, anthropic, gemini, custom
        public String targetUrl; // Optional override
        public String apiKey; // Optional override
        public String modelOverride; // Optional: change model name sent to upstream

        public ProxyRoute(String pattern, String provider) {
            this.id = java.util.UUID.randomUUID().toString().substring(0, 8);
            this.pattern = pattern;
            this.provider = provider;
        }
    }
}

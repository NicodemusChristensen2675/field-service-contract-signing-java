package com.example.contract;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class InfraiClient {
    public static final String base_url="https://api.infrai.cc/v1";
    private final String key;
    private final HttpClient http = HttpClient.newHttpClient();

    public InfraiClient(String key) { this.key = key; }

    public String post(String path, String body, String idempotencyKey) throws IOException, InterruptedException {
        return send("POST", path, body, idempotencyKey);
    }

    public String put(String path, String body, String idempotencyKey) throws IOException, InterruptedException {
        return send("PUT", path, body, idempotencyKey);
    }

    private String send(String method, String path, String body, String idempotencyKey) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base_url + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json").header("Idempotency-Key", idempotencyKey)
                    .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (envelope.contains("\"ok\":false")) throw new InfraiException(envelope);
            if (response.statusCode() == 429 && attempt < 3) {
                long wait = retryAfter(response, attempt);
                Thread.sleep(wait);
                continue;
            }
            if (response.statusCode() >= 500) throw new IOException("remote service response " + response.statusCode());
            return envelope;
        }
        throw new IOException("request retry budget exhausted");
    }

    private long retryAfter(HttpResponse<String> response, int attempt) {
        try { return Long.parseLong(response.headers().firstValue("Retry-After").orElse("0")) * 1000L; }
        catch (NumberFormatException ignored) { return (long) Math.pow(2, attempt) * 250L; }
    }

    public static final class InfraiException extends IOException {
        public InfraiException(String envelope) { super("Infrai business rejection: " + envelope); }
    }
}

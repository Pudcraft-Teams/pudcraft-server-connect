package org.pudcraft.pudcraftServerConnect.network;

import org.pudcraft.pudcraftServerConnect.config.PluginConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

public class ApiClient {
    private static final String BASE_URL = "https://servers.pudcraft.top";
    private final HttpClient httpClient;
    private final String serverId;
    private final String apiKey;
    private final Logger logger;

    public ApiClient(PluginConfig config, Logger logger) {
        this.serverId = config.getServerId();
        this.apiKey = config.getApiKey();
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public ApiResponse get(String path) {
        return request("GET", path, null);
    }

    public ApiResponse post(String path, String jsonBody) {
        return request("POST", path, jsonBody);
    }

    public ApiResponse postEmpty(String path) {
        return request("POST", path, "{}");
    }

    private ApiResponse request(String method, String path, String jsonBody) {
        try {
            String url = BASE_URL + path.replace("{id}", serverId);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15));

            if ("GET".equals(method)) {
                builder.GET();
            } else if ("POST".equals(method) && jsonBody != null) {
                builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());

            return new ApiResponse(response.statusCode(), response.body());
        } catch (Exception e) {
            logger.warning("API request failed: " + method + " " + path + " - " + e.getMessage());
            return new ApiResponse(-1, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public String getServerId() { return serverId; }
    public String getApiKey() { return apiKey; }

    /**
     * Test API connectivity by performing a handshake.
     */
    public boolean testConnection() {
        ApiResponse response = post("/api/servers/{id}/sync/handshake", "{}");
        return response.isSuccess();
    }
}

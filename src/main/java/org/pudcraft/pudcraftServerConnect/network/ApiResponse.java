package org.pudcraft.pudcraftServerConnect.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ApiResponse {
    private final int statusCode;
    private final String body;

    public ApiResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() { return statusCode; }
    public String getBody() { return body; }
    public boolean isSuccess() { return statusCode >= 200 && statusCode < 300; }

    public JsonObject getJsonObject() {
        return JsonParser.parseString(body).getAsJsonObject();
    }

    public JsonArray getJsonArray() {
        return JsonParser.parseString(body).getAsJsonArray();
    }

    public String getError() {
        try {
            JsonObject json = getJsonObject();
            if (json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (Exception ignored) {}
        return "HTTP " + statusCode;
    }
}

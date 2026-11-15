package dev.argus.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.Map;

/** Test helper: a small HTTP client for the Admin API, speaking JSON over real HTTP. */
public final class AdminClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;
    private final String token;

    private AdminClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    /** A client that sends no token at all. */
    public static AdminClient anonymous(int port) {
        return new AdminClient("http://127.0.0.1:" + port, null);
    }

    /** A client that sends the given token, valid or not. */
    public static AdminClient withToken(int port, String token) {
        return new AdminClient("http://127.0.0.1:" + port, token);
    }

    /** Signs in through POST /admin/login and uses the returned token from then on. */
    public static AdminClient signedIn(int port, String username, String password)
            throws Exception {
        Response login = anonymous(port).post(
                "/admin/login", Map.of("username", username, "password", password));
        if (login.status() != 200) {
            throw new IllegalStateException("sign-in failed with status " + login.status());
        }
        return withToken(port, login.body().get("token").asText());
    }

    public Response get(String path) throws Exception {
        return send(request(path).GET());
    }

    public Response delete(String path) throws Exception {
        return send(request(path).DELETE());
    }

    public Response post(String path, Object body) throws Exception {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    public Response post(String path) throws Exception {
        return send(request(path).POST(BodyPublishers.noBody()));
    }

    public Response put(String path, Object body) throws Exception {
        return send(request(path)
                .header("Content-Type", "application/json")
                .PUT(BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    /** Sends a body as it is, with the given content type (for example a YAML document). */
    public Response postRaw(String path, String contentType, String body) throws Exception {
        return send(request(path)
                .header("Content-Type", contentType)
                .POST(BodyPublishers.ofString(body)));
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Response send(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        String text = response.body();
        JsonNode body = null;
        if (text != null && !text.isBlank() && text.stripLeading().matches("(?s)^[\\[{].*")) {
            body = JSON.readTree(text);
        }
        return new Response(response.statusCode(), body, text);
    }

    /** Status, the body as JSON (null if it is not JSON) and the raw body text. */
    public record Response(int status, JsonNode body, String text) { }
}

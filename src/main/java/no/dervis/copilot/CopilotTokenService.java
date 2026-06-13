package no.dervis.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Exchanges a GitHub user OAuth token for a short-lived GitHub Copilot bearer
 * token (returned by {@code https://api.github.com/copilot_internal/v2/token}),
 * and caches it in-memory until close to expiry.
 */
public final class CopilotTokenService {

    private static final String TOKEN_URL = "https://api.github.com/copilot_internal/v2/token";
    // Refresh a bit before actual expiry to avoid edge-of-window 401s.
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);

    private final CopilotDeviceFlow deviceFlow;
    private final HttpClient http;
    private final ObjectMapper mapper;

    private volatile String cachedToken;
    private volatile Instant cachedExpiresAt = Instant.EPOCH;

    public CopilotTokenService(CopilotDeviceFlow deviceFlow, ObjectMapper mapper) {
        this.deviceFlow = deviceFlow;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Returns a Copilot API bearer token valid for use against api.githubcopilot.com. */
    public synchronized String getToken() throws IOException, InterruptedException {
        if (cachedToken != null && Instant.now().isBefore(cachedExpiresAt.minus(REFRESH_MARGIN))) {
            return cachedToken;
        }
        return refresh();
    }

    private String refresh() throws IOException, InterruptedException {
        String oauth = deviceFlow.getOrAuthenticate();

        HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Authorization", "token " + oauth)
                .header("Accept", "application/json")
                .header("Editor-Version", "vscode/1.95.0")
                .header("Editor-Plugin-Version", "copilot-chat/0.23.0")
                .header("User-Agent", "GitHubCopilotChat/0.23.0")
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            // OAuth token may be invalid/revoked — clear cache so next call re-auths.
            CopilotDeviceFlow.clearCache();
            throw new IOException("Copilot token exchange failed (" + resp.statusCode()
                    + "). The cached GitHub OAuth token was cleared; please re-run to sign in again. Body: "
                    + resp.body());
        }
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Copilot token exchange failed: "
                    + resp.statusCode() + " " + resp.body());
        }

        JsonNode json = mapper.readTree(resp.body());
        String token = json.get("token").asText();
        long expiresAt = json.has("expires_at") ? json.get("expires_at").asLong() : 0L;

        cachedToken = token;
        cachedExpiresAt = expiresAt > 0
                ? Instant.ofEpochSecond(expiresAt)
                : Instant.now().plus(Duration.ofMinutes(25));
        return token;
    }
}

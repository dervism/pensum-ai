package no.dervis.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Handles GitHub OAuth device flow to obtain a user access token usable for
 * exchanging into a short-lived GitHub Copilot bearer token.
 *
 * <p>Uses the VS Code Copilot client_id, which is the public client id used by
 * the official VS Code Copilot extension. The resulting token is cached in the
 * user's home directory and reused across runs.
 *
 * <p>NOTE: GitHub Copilot's chat API is not officially public and may be subject
 * to Copilot's Terms of Service. Only use this with your own Copilot subscription.
 */
public final class CopilotDeviceFlow {

    // Public client_id of the VS Code GitHub Copilot extension.
    private static final String COPILOT_CLIENT_ID = "Iv1.b507a08c87ecfe98";
    private static final String DEVICE_CODE_URL = "https://github.com/login/device/code";
    private static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String SCOPE = "read:user";

    private static final Path TOKEN_CACHE = Path.of(
            System.getProperty("user.home"), ".config", "pensumai", "github-oauth.token");

    private final HttpClient http;
    private final ObjectMapper mapper;

    public CopilotDeviceFlow(ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Returns a GitHub OAuth access token, either from cache or by initiating
     * the device-flow interactively.
     */
    public String getOrAuthenticate() throws IOException, InterruptedException {
        String cached = readCachedToken();
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String token = runDeviceFlow();
        writeCachedToken(token);
        return token;
    }

    private String readCachedToken() {
        try {
            if (Files.exists(TOKEN_CACHE)) {
                return Files.readString(TOKEN_CACHE).trim();
            }
        } catch (IOException ignored) {
            // fall through
        }
        return null;
    }

    private void writeCachedToken(String token) throws IOException {
        Files.createDirectories(TOKEN_CACHE.getParent());
        Files.writeString(TOKEN_CACHE, token,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            TOKEN_CACHE.toFile().setReadable(false, false);
            TOKEN_CACHE.toFile().setReadable(true, true);
            TOKEN_CACHE.toFile().setWritable(false, false);
            TOKEN_CACHE.toFile().setWritable(true, true);
        } catch (SecurityException ignored) {
            // best effort
        }
    }

    private String runDeviceFlow() throws IOException, InterruptedException {
        // 1. Request a device + user code
        HttpRequest codeReq = HttpRequest.newBuilder(URI.create(DEVICE_CODE_URL))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "client_id=" + COPILOT_CLIENT_ID + "&scope=" + SCOPE))
                .build();

        HttpResponse<String> codeResp = http.send(codeReq, HttpResponse.BodyHandlers.ofString());
        if (codeResp.statusCode() / 100 != 2) {
            throw new IOException("Device code request failed: " + codeResp.statusCode()
                    + " " + codeResp.body());
        }

        JsonNode codeJson = mapper.readTree(codeResp.body());
        String deviceCode = codeJson.get("device_code").asText();
        String userCode = codeJson.get("user_code").asText();
        String verificationUri = codeJson.get("verification_uri").asText();
        int interval = codeJson.has("interval") ? codeJson.get("interval").asInt() : 5;
        int expiresIn = codeJson.has("expires_in") ? codeJson.get("expires_in").asInt() : 900;

        System.out.println();
        System.out.println("================ GitHub Copilot sign-in ================");
        System.out.println(" 1. Open: " + verificationUri);
        System.out.println(" 2. Enter code: " + userCode);
        System.out.println("  (waiting for authorisation, expires in "
                + expiresIn + "s)");
        System.out.println("========================================================");
        System.out.println();

        // 2. Poll for the access token
        long deadline = System.currentTimeMillis() + (expiresIn * 1000L);
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L);

            HttpRequest tokenReq = HttpRequest.newBuilder(URI.create(ACCESS_TOKEN_URL))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "client_id=" + COPILOT_CLIENT_ID
                                    + "&device_code=" + deviceCode
                                    + "&grant_type=urn:ietf:params:oauth:grant-type:device_code"))
                    .build();

            HttpResponse<String> tokenResp = http.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            JsonNode tokenJson = mapper.readTree(tokenResp.body());

            if (tokenJson.has("access_token")) {
                return tokenJson.get("access_token").asText();
            }

            String error = tokenJson.path("error").asText("");
            switch (error) {
                case "authorization_pending" -> {
                    // keep polling
                }
                case "slow_down" -> interval += 5;
                case "expired_token", "access_denied" ->
                        throw new IOException("Authorisation failed: " + error);
                default -> {
                    if (!error.isEmpty()) {
                        throw new IOException("Device flow error: " + error
                                + " — " + tokenJson.path("error_description").asText(""));
                    }
                }
            }
        }
        throw new IOException("Device flow timed out waiting for user authorisation");
    }

    /** Deletes the cached OAuth token. */
    public static void clearCache() throws IOException {
        Files.deleteIfExists(TOKEN_CACHE);
    }
}

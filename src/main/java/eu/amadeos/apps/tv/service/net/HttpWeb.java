package eu.amadeos.apps.tv.service.net;

import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.commons.net.Network;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * {@link Web} with the JDK's HTTP client. Every request names the app ({@code amade-tv/<version>}), so the
 * services can see who asks, and gives up after 20 seconds.
 */
public final class HttpWeb implements Web {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient client = Network.proxy().httpClient()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String userAgent;

    public HttpWeb(AppInfo app) {
        String version = HttpWeb.class.getPackage().getImplementationVersion();
        this.userAgent = app.id() + "/" + (version == null ? "dev" : version)
                + " (+https://github.com/amadeos-eu/amade-tv)";
    }

    @Override
    public String get(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder request = request(url, headers).GET();
        return new String(send(request.build()), StandardCharsets.UTF_8);
    }

    @Override
    public String postJson(String url, String json, Map<String, String> headers) throws IOException {
        HttpRequest.Builder request = request(url, headers)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        return new String(send(request.build()), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] bytes(String url) throws IOException {
        return send(request(url, Map.of()).GET().build());
    }

    private HttpRequest.Builder request(String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).header("User-Agent", userAgent);
        headers.forEach(b::header);
        return b;
    }

    private byte[] send(HttpRequest request) throws IOException {
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " for " + request.uri());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted: " + request.uri(), e);
        }
    }
}

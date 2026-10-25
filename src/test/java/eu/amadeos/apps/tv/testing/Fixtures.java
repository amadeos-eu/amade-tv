package eu.amadeos.apps.tv.testing;

import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.service.net.Web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Recorded-style answers of the services (src/test/resources/fixtures) and a {@link Web} that serves them. */
public final class Fixtures {
    private Fixtures() {
    }

    public static String read(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) throw new IllegalArgumentException("No fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Channel channel(String id, String name, Channel.GuideSourceId guide, String... guideNames) {
        return new Channel(id, name, "https://example.org/" + id + ".m3u8", "", 0, guide, List.of(guideNames));
    }

    /** Answers by URL prefix; records every request; an answer of null means "fails". */
    public static final class FakeWeb implements Web {
        public final Map<String, Function<String, String>> answers = new LinkedHashMap<>();
        public final Map<String, byte[]> pictures = new LinkedHashMap<>();
        public final List<String> requests = new ArrayList<>();
        public final List<Map<String, String>> headers = new ArrayList<>();
        public final List<String> bodies = new ArrayList<>();

        public FakeWeb answer(String urlPrefix, String body) {
            answers.put(urlPrefix, url -> body);
            return this;
        }

        @Override
        public String get(String url, Map<String, String> h) throws IOException {
            requests.add("GET " + url);
            headers.add(h);
            return find(url);
        }

        @Override
        public String postJson(String url, String json, Map<String, String> h) throws IOException {
            requests.add("POST " + url);
            headers.add(h);
            bodies.add(json);
            return find(url);
        }

        @Override
        public byte[] bytes(String url) throws IOException {
            requests.add("GET " + url);
            byte[] b = pictures.get(url);
            if (b == null) throw new IOException("HTTP 404 for " + url);
            return b;
        }

        private String find(String url) throws IOException {
            for (var e : answers.entrySet()) {
                if (url.startsWith(e.getKey())) {
                    String body = e.getValue().apply(url);
                    if (body == null) throw new IOException("HTTP 500 for " + url);
                    return body;
                }
            }
            throw new IOException("HTTP 404 for " + url);
        }
    }
}

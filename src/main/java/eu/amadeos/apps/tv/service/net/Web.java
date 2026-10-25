package eu.amadeos.apps.tv.service.net;

import java.io.IOException;
import java.util.Map;

/**
 * The few HTTP requests the app makes: program data, the channel list and logos. An interface, so the
 * guide sources can be tested with recorded answers.
 */
public interface Web {
    /** GET, the body as text; IOException on a status other than 2xx. */
    String get(String url, Map<String, String> headers) throws IOException;

    /** POST of a JSON body, the answer as text. */
    String postJson(String url, String json, Map<String, String> headers) throws IOException;

    /** GET, the body as bytes (pictures). */
    byte[] bytes(String url) throws IOException;

    default String get(String url) throws IOException {
        return get(url, Map.of());
    }
}

package com.gjp.asset.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Shared GET with Chrome UA, ~8s timeout. Failures return null; callers must fall back.
 * Does not persist response bodies.
 */
final class QuoteHttp {

    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final Logger log = LoggerFactory.getLogger(QuoteHttp.class);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private QuoteHttp() {
    }

    static String get(String url, Charset charset) {
        try {
            URI uri = URI.create(url);
            String referer = uri.getScheme() + "://" + uri.getHost() + "/";
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/json,application/javascript,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Referer", referer)
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 400) {
                log.debug("quote GET {} -> {}", hostOf(url), resp.statusCode());
                return null;
            }
            byte[] body = resp.body();
            if (body == null || body.length == 0) {
                return null;
            }
            Charset cs = charset != null ? charset : StandardCharsets.UTF_8;
            return new String(body, cs);
        } catch (Exception e) {
            log.debug("quote GET {} failed: {}", hostOf(url), e.toString());
            return null;
        }
    }

    static String getUtf8(String url) {
        return get(url, StandardCharsets.UTF_8);
    }

    static String getGbk(String url) {
        return get(url, Charset.forName("GBK"));
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "?";
        }
    }
}

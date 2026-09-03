package com.gjp.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjp.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调用 Dify 工作流（/workflows/run）。智能分析、账单搜索共用，
 * 各自传入自己的 API Key，绝不复用账单导入的 DIFY_API_KEY。
 * 只读文本、不写 MySQL。
 */
@Component
public class DifyWorkflowClient {

    private static final Logger log = LoggerFactory.getLogger(DifyWorkflowClient.class);

    private final ObjectMapper mapper;
    private final HttpClient http;

    public DifyWorkflowClient(ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String run(String baseUrl, String apiKey, String user, int timeoutSeconds,
                      Map<String, Object> inputs) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException("尚未配置 Dify 工作流 Key");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("inputs", inputs == null ? Map.of() : inputs);
            body.put("response_mode", "blocking");
            body.put("user", user == null || user.isBlank() ? "gjp" : user);
            JsonNode data = postJson(baseUrl, apiKey, timeoutSeconds, body);
            return readResult(data);
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("已取消");
        } catch (Exception e) {
            log.warn("调用 Dify 工作流失败：{}", e.getMessage());
            throw new BizException("调用智能体失败：" + e.getMessage());
        }
    }

    private JsonNode postJson(String baseUrl, String apiKey, int timeoutSeconds,
                              Map<String, Object> body) throws Exception {
        int timeout = Math.max(15, timeoutSeconds);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(baseUrl) + "/workflows/run"))
                .timeout(Duration.ofSeconds(timeout))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 300) {
            throw new BizException("Dify 调用失败（" + resp.statusCode() + "）：" + cut(resp.body()));
        }
        return mapper.readTree(resp.body());
    }

    private String readResult(JsonNode data) throws Exception {
        JsonNode outputs = data.path("data").path("outputs");
        if (outputs.isMissingNode() || outputs.isNull()) {
            outputs = data.path("outputs");
        }
        JsonNode result = outputs.path("result");
        if (result.isTextual() && !result.asText().isBlank()) {
            return result.asText();
        }
        if (result.isObject() || result.isArray()) {
            return mapper.writeValueAsString(result);
        }
        if (outputs.isTextual() && !outputs.asText().isBlank()) {
            return outputs.asText();
        }
        if (outputs.isObject()) {
            Iterator<String> names = outputs.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                JsonNode v = outputs.get(name);
                if (v.isTextual() && !v.asText().isBlank()) {
                    return v.asText();
                }
                if (v.isObject() || v.isArray()) {
                    return mapper.writeValueAsString(v);
                }
            }
        }
        String wfError = firstText(data, "data.error", "error");
        if (wfError != null && !wfError.isBlank()) {
            throw new BizException("智能体失败：" + cut(wfError));
        }
        throw new BizException("工作流没有返回可解析的输出");
    }

    private static String firstText(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode n = root;
            for (String p : path.split("\\.")) {
                n = n.path(p);
            }
            if (n.isTextual() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        return null;
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String cut(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 240 ? s : s.substring(0, 240) + "...";
    }
}

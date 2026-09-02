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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 直连通义 OpenAI 兼容接口，只处理已经抽出的文字。读文件交给 Dify 工作流里的工具。
 */
@Component
public class QwenClient {

    private static final Logger log = LoggerFactory.getLogger(QwenClient.class);
    static final String TEXT_PROMPT = """
            从账单表格或小票文字抽出收支流水。只输出一个 JSON 对象，不要解释。
            {"relevant":true,"reason":"","records":[{"type":2,"amount":28.5,"recordDate":"2026-08-01","categoryName":"正餐","merchant":"海底捞","area":"","payMethod":"微信","isGift":0,"remark":""}]}
            规则：type 1收入2支出；amount>0；日期 YYYY-MM-DD；categoryName 必须选自分类名单；payMethod 仅现金/微信/支付宝/银行卡/其他；礼金 isGift=1；无关文件 relevant=false。逐笔抽取，不要合并。
            """;

    private final QwenProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final DifyClient difyClient;

    public QwenClient(QwenProperties props, ObjectMapper mapper, DifyClient difyClient) {
        this.props = props;
        this.mapper = mapper;
        this.difyClient = difyClient;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean configured() {
        return props.configured();
    }

    public DifyParseResult parseText(String text, String categories) {
        return chat(props.getTextModel(), List.of(
                message("system", TEXT_PROMPT),
                message("user", "分类名单：\n" + nullToEmpty(categories) + "\n\n表格：\n" + nullToEmpty(text))
        ), true);
    }

    public DifyParseResult parseImage(byte[] image, String mime, String categories) {
        String dataUrl = "data:" + (mime == null || mime.isBlank() ? "image/jpeg" : mime)
                + ";base64," + java.util.Base64.getEncoder().encodeToString(image);
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", Map.of("url", dataUrl));
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", TEXT_PROMPT + "\n分类名单：\n" + nullToEmpty(categories) + "\n请识别这张图里的账单。");
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", List.of(imagePart, textPart));
        return chat(props.getVisionModel(), List.of(user), false);
    }

    private DifyParseResult chat(String model, List<Map<String, Object>> messages, boolean jsonObject) {
        if (!props.configured()) {
            throw new BizException("尚未配置通义 API Key");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.2);
            body.put("max_tokens", 8192);
            if (jsonObject) {
                body.put("response_format", Map.of("type", "json_object"));
            }
            long t0 = System.nanoTime();
            JsonNode data = post(body);
            String raw = data.path("choices").path(0).path("message").path("content").asText("");
            if (raw.isBlank()) {
                throw new BizException("通义没有返回解析结果");
            }
            log.info("通义 {} 耗时 {}ms", model, Math.round((System.nanoTime() - t0) / 1_000_000.0));
            return difyClient.parseAnswer(raw);
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("已取消");
        } catch (Exception e) {
            log.warn("通义调用失败：{}", e.getMessage());
            throw new BizException("调用通义失败：" + e.getMessage());
        }
    }

    private JsonNode post(Map<String, Object> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(props.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(20, props.getTimeoutSeconds())))
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 300) {
            throw new BizException("通义调用失败（" + resp.statusCode() + "）：" + cut(resp.body()));
        }
        return mapper.readTree(resp.body());
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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

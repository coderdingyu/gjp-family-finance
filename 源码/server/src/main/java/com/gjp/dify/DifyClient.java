package com.gjp.dify;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用本地 Dify。智能体只负责从文件/文本里抽出流水 JSON，不直接写 MySQL。
 */
@Component
public class DifyClient {

    private static final Logger log = LoggerFactory.getLogger(DifyClient.class);

    public static final String SYSTEM_PROMPT = """
            你是「管家婆」家庭收支管理系统的账单解析智能体。
            用户会给你一张图片、一份 PDF，或一段从 Excel/CSV 抽出来的表格文本。
            你的任务是抽出其中的收支流水。文件里可能有几十上百笔，必须尽量逐笔抽取，不要合并、不要只给汇总。

            只输出一个 JSON 对象，不要 Markdown，不要解释。格式：
            {
              "relevant": true,
              "reason": "",
              "records": [
                {
                  "type": 2,
                  "amount": 28.50,
                  "recordDate": "2026-08-01",
                  "categoryName": "正餐",
                  "merchant": "海底捞",
                  "area": "",
                  "payMethod": "微信",
                  "isGift": 0,
                  "remark": ""
                }
              ]
            }

            规则：
            1. type：1=收入，2=支出。看不出时按支出。
            2. amount 必须是大于 0 的数字。
            3. recordDate 用 YYYY-MM-DD。没有年份时用当年；完全没有日期则跳过该行。
            4. categoryName 必须从用户提供的分类名单里选一个最接近的末级分类，不要自造分类。
            5. payMethod 只能是：现金 / 微信 / 支付宝 / 银行卡 / 其他，不确定就留空。
            6. isGift：礼金、红包、随份子、请客送礼为 1，否则 0。
            7. 如果文件与家庭账单无关（风景照、论文、合同、纯文字小说、空白表等）：
               返回 {"relevant": false, "reason": "一句话说明为什么不是账单", "records": []}
            8. 能看懂一部分就抽一部分；完全看不懂按无关处理。
            9. 商家、片区、备注、分类名一律用中文原文，禁止翻译成英文。categoryName 必须是分类名单里的中文末级名。
            """;

    private final DifyProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public DifyClient(DifyProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean configured() {
        return props.configured();
    }

    public String mode() {
        return props.getMode();
    }

    public DifyParseResult parse(byte[] fileBytes, String filename, String mime, String fileKind,
                                 String textContent, String categories) {
        if (!props.configured()) {
            throw new BizException("尚未配置 Dify API Key，请在环境变量 DIFY_API_KEY 中填写");
        }
        try {
            long t0 = System.nanoTime();
            String uploadId = null;
            String fileType = "document";
            if (fileBytes != null && fileBytes.length > 0) {
                fileType = "image".equals(fileKind) ? "image" : "document";
                uploadId = uploadFile(fileBytes, filename, mime);
            }
            String raw = props.workflowMode()
                    ? runWorkflow(uploadId, fileType, textContent, categories)
                    : runChat(uploadId, fileType, textContent, categories);
            log.info("Dify {} 耗时 {}ms", fileKind == null ? "text" : fileKind,
                    Math.round((System.nanoTime() - t0) / 1_000_000.0));
            return parseAnswer(raw);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("调用 Dify 失败：{}", e.getMessage());
            throw new BizException("调用智能体失败：" + e.getMessage());
        }
    }

    private String uploadFile(byte[] bytes, String filename, String mime) throws Exception {
        String boundary = "----gjp" + System.nanoTime();
        String safeName = filename == null || filename.isBlank() ? "bill.bin" : filename.replace("\"", "");
        String type = mime == null || mime.isBlank() ? "application/octet-stream" : mime;
        byte[] body = buildMultipart(boundary, bytes, safeName, type, props.getUser());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(props.getBaseUrl()) + "/files/upload"))
                .timeout(Duration.ofSeconds(Math.max(30, props.getTimeoutSeconds())))
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("已取消");
        }
        if (resp.statusCode() >= 300) {
            throw new BizException("Dify 上传文件失败（" + resp.statusCode() + "）：" + cut(resp.body()));
        }
        JsonNode node = mapper.readTree(resp.body());
        String id = text(node, "id");
        if (id == null) {
            throw new BizException("Dify 上传成功但没有返回文件 ID");
        }
        return id;
    }

    private String runChat(String uploadId, String fileType, String textContent, String categories) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", Map.of());
        body.put("query", buildQuery(textContent, categories));
        body.put("response_mode", "blocking");
        body.put("user", props.getUser());
        if (uploadId != null) {
            body.put("files", List.of(fileRef(fileType, uploadId)));
        }
        JsonNode data = postJson("/chat-messages", body);
        String answer = firstText(data, "answer", "data.answer");
        if (answer == null || answer.isBlank()) {
            throw new BizException("智能体没有返回解析结果");
        }
        return answer;
    }

    private String runWorkflow(String uploadId, String fileType, String textContent, String categories) throws Exception {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(props.getTextVar(), textContent == null ? "" : textContent);
        inputs.put(props.getCategoriesVar(), categories == null ? "" : categories);
        if (uploadId != null) {
            inputs.put(props.getFileVar(), fileRef(fileType, uploadId));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        body.put("response_mode", "blocking");
        body.put("user", props.getUser());
        if (uploadId != null) {
            body.put("files", List.of(fileRef(fileType, uploadId)));
        }
        JsonNode data = postJson("/workflows/run", body);
        JsonNode outputs = data.path("data").path("outputs");
        if (outputs.isMissingNode() || outputs.isNull()) {
            outputs = data.path("outputs");
        }
        if (outputs.isTextual()) {
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
        String answer = firstText(data, "data.outputs.result", "data.outputs.text", "answer");
        if (answer == null || answer.isBlank()) {
            String wfError = firstText(data, "data.error", "error");
            if (wfError != null && !wfError.isBlank()) {
                throw new BizException("智能体失败：" + cut(wfError));
            }
            throw new BizException("工作流没有返回可解析的输出");
        }
        return answer;
    }

    private JsonNode postJson(String path, Map<String, Object> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(props.getBaseUrl()) + path))
                .timeout(Duration.ofSeconds(Math.max(30, props.getTimeoutSeconds())))
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("已取消");
        }
        if (resp.statusCode() >= 300) {
            throw new BizException("Dify 调用失败（" + resp.statusCode() + "）：" + cut(resp.body()));
        }
        return mapper.readTree(resp.body());
    }

    DifyParseResult parseAnswer(String raw) throws Exception {
        String json = extractJson(raw);
        JsonNode node = mapper.readTree(json);
        DifyParseResult result = new DifyParseResult();
        if (node.has("relevant")) {
            result.setRelevant(node.get("relevant").asBoolean(true));
        }
        result.setReason(text(node, "reason"));
        JsonNode recs = node.get("records");
        List<Map<String, Object>> list = new ArrayList<>();
        if (recs != null && recs.isArray()) {
            for (JsonNode rec : recs) {
                list.add(mapper.convertValue(rec, new TypeReference<Map<String, Object>>() { }));
            }
        }
        result.setRecords(list);
        if (result.isRelevant() && list.isEmpty()) {
            result.setRelevant(false);
            if (result.getReason() == null || result.getReason().isBlank()) {
                result.setReason("智能体没有抽出任何流水，按无关文件处理");
            }
        }
        return result;
    }

    private String buildQuery(String textContent, String categories) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PROMPT).append("\n\n");
        sb.append("可选末级分类名单：\n").append(categories == null ? "" : categories).append("\n\n");
        if (textContent != null && !textContent.isBlank()) {
            sb.append("以下是从表格文件抽出的文本，请逐行抽取：\n").append(textContent);
        } else {
            sb.append("请识别附件中的账单。如果附件与收支无关，按无关文件返回。");
        }
        return sb.toString();
    }

    private Map<String, Object> fileRef(String type, String uploadId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("transfer_method", "local_file");
        map.put("upload_file_id", uploadId);
        return map;
    }

    /**
     * 通义思考模式会把草稿 JSON、自然语言和最终 JSON 混在一段里。
     * Jackson 不能吃整段，必须先剥 think / 代码块，再按括号匹配取出第一个完整对象。
     */
    public static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = stripThink(raw).trim();
        text = unwrapFence(text);
        String obj = firstJsonObject(text);
        return obj != null ? obj : text;
    }

    static String stripThink(String raw) {
        String text = raw.replaceAll("(?is)<think\\b[^>]*>.*?</think>", " ");
        int close = text.lastIndexOf("</think>");
        if (close >= 0) {
            String after = text.substring(close + "</think>".length());
            if (after.indexOf('{') >= 0) {
                text = after;
            }
        }
        return text.replace("</think>", " ").replace("<think>", " ");
    }

    static String unwrapFence(String text) {
        int fence = text.indexOf("```");
        if (fence < 0) {
            return text.trim();
        }
        int start = text.indexOf('\n', fence);
        int end = text.indexOf("```", fence + 3);
        if (start > 0 && end > start) {
            text = text.substring(start + 1, end).trim();
            if (text.regionMatches(true, 0, "json", 0, 4)) {
                text = text.substring(4).trim();
            }
        }
        return text;
    }

    static String firstJsonObject(String text) {
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (start < 0) {
                if (c == '{') {
                    start = i;
                    depth = 1;
                }
                continue;
            }
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static byte[] buildMultipart(String boundary, byte[] file, String filename,
                                         String mime, String user) {
        String dash = "--" + boundary;
        String partFile = dash + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        String partUser = "\r\n" + dash + "\r\n"
                + "Content-Disposition: form-data; name=\"user\"\r\n\r\n"
                + user + "\r\n" + dash + "--\r\n";
        byte[] head = partFile.getBytes(StandardCharsets.UTF_8);
        byte[] tail = partUser.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[head.length + file.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(file, 0, all, head.length, file.length);
        System.arraycopy(tail, 0, all, head.length + file.length, tail.length);
        return all;
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

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
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

package com.gjp.dify;

import com.gjp.common.BizException;
import com.gjp.imp.BillTextParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 账单智能体：本机能抽出的表格不走这里。
 * 本机认不出时把原文件交给 Dify，由工作流里的读文件 / OCR 工具读内容。
 */
@Component
public class BillAgent {

    private static final Logger log = LoggerFactory.getLogger(BillAgent.class);

    private final QwenClient qwenClient;
    private final DifyClient difyClient;

    public BillAgent(QwenClient qwenClient, DifyClient difyClient) {
        this.qwenClient = qwenClient;
        this.difyClient = difyClient;
    }

    public boolean configured() {
        return qwenClient.configured() || difyClient.configured();
    }

    public String mode() {
        if (qwenClient.configured()) {
            return difyClient.configured() ? "qwen+dify" : "qwen";
        }
        return difyClient.mode();
    }

    public DifyParseResult parseText(String text, String filename, String categories) {
        if (qwenClient.configured()) {
            try {
                return qwenClient.parseText(text, categories);
            } catch (BizException e) {
                if ("已取消".equals(e.getMessage())) {
                    throw e;
                }
                log.warn("通义文本失败，改走 Dify：{}", e.getMessage());
            }
        }
        if (difyClient.configured()) {
            log.warn("文本分块不再送 Dify（智能体要原文件），改走本机规则：{}", filename);
        }
        return BillTextParser.parse(text);
    }

    public DifyParseResult parseFile(byte[] bytes, String filename, String mime, String kind, String categories) {
        if (difyClient.configured()) {
            return difyClient.parse(bytes, filename, mime, kind, null, categories);
        }
        if (qwenClient.configured() && "image".equals(kind)) {
            log.info("未配 Dify，图片回退通义视觉");
            byte[] slim = ImagePrep.forVision(bytes);
            String sendMime = slim != bytes ? "image/jpeg" : (mime == null ? "image/jpeg" : mime);
            return qwenClient.parseImage(slim, sendMime, categories);
        }
        throw new BizException("请先配置 Dify API Key。本机认不出的文件由智能体读原文件");
    }
}

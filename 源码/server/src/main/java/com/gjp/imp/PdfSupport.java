package com.gjp.imp;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 文本型 PDF 抽出文字；没有文字层时把第一页渲成 PNG，再交给视觉模型。
 */
public final class PdfSupport {

    private static final int MIN_TEXT = 12;

    private PdfSupport() {
    }

    public static Prepared prepare(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Prepared.empty();
        }
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(bytes))) {
            if (doc.getNumberOfPages() <= 0) {
                return Prepared.empty();
            }
            String text = strip(doc);
            if (hasText(text)) {
                return new Prepared(text, null);
            }
            return new Prepared(null, renderFirstPage(doc));
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取该 PDF：" + e.getMessage());
        }
    }

    private static String strip(PDDocument doc) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        // 微信证明用阅读顺序抽字才能保住「日期+时间」分行；按坐标排序会把表格挤乱。
        stripper.setSortByPosition(false);
        String raw = stripper.getText(doc);
        if (raw == null) {
            return "";
        }
        return raw.replace('\u00a0', ' ').trim();
    }

    private static byte[] renderFirstPage(PDDocument doc) throws IOException {
        PDFRenderer renderer = new PDFRenderer(doc);
        BufferedImage image = renderer.renderImageWithDPI(0, 144);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static boolean hasText(String text) {
        if (text == null) {
            return false;
        }
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                letters++;
            }
        }
        return letters >= MIN_TEXT;
    }

    public record Prepared(String text, byte[] imagePng) {
        static Prepared empty() {
            return new Prepared(null, null);
        }

        boolean hasText() {
            return text != null && !text.isBlank();
        }

        boolean hasImage() {
            return imagePng != null && imagePng.length > 0;
        }
    }
}

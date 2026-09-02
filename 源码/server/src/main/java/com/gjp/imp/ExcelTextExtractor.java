package com.gjp.imp;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把 Excel / CSV 拆成「真正的表头 + 行」。
 * 微信/支付宝导出会先写一长段说明，必须跳过；单号列又宽又没用，送给模型容易超时。
 */
public final class ExcelTextExtractor {

    /** Dify 回退时每块行数。微信一行很宽，太大一块会超时。 */
    public static final int CHUNK_ROWS = 20;

    private ExcelTextExtractor() {
    }

    public static List<String> chunks(byte[] bytes, String filename) {
        List<String> lines = readLines(bytes, filename);
        return chunkLines(slim(dropPreamble(lines)));
    }

    /** 去掉说明、丢掉单号后的完整表，给本地规则解析用。 */
    public static String tableText(byte[] bytes, String filename) {
        List<String> lines = slim(dropPreamble(readLines(bytes, filename)));
        if (lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines);
    }

    private static List<String> readLines(byte[] bytes, String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv") || name.endsWith(".txt")) {
            return splitCsv(decodeText(bytes));
        }
        return readWorkbook(bytes);
    }

    private static List<String> readWorkbook(byte[] bytes) {
        List<String> lines = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) {
                return lines;
            }
            int last = sheet.getLastRowNum();
            for (int i = 0; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                short lastCell = row.getLastCellNum();
                boolean any = false;
                for (int c = 0; c < lastCell; c++) {
                    if (c > 0) {
                        sb.append('\t');
                    }
                    Cell cell = row.getCell(c);
                    String v = cell == null ? "" : fmt.formatCellValue(cell).trim();
                    sb.append(v);
                    if (!v.isEmpty()) {
                        any = true;
                    }
                }
                if (any) {
                    lines.add(sb.toString());
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取该 Excel：" + e.getMessage());
        }
        return lines;
    }

    private static List<String> splitCsv(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) {
                lines.add(line.contains("\t") ? line : line.replace(',', '\t'));
            }
        }
        return lines;
    }

    static List<String> dropPreamble(List<String> lines) {
        int headerAt = findHeader(lines);
        if (headerAt <= 0) {
            return lines;
        }
        return new ArrayList<>(lines.subList(headerAt, lines.size()));
    }

    static int findHeader(List<String> lines) {
        int limit = Math.min(lines.size(), 40);
        for (int i = 0; i < limit; i++) {
            if (looksBillHeader(lines.get(i))) {
                return i;
            }
        }
        return 0;
    }

    static boolean looksBillHeader(String line) {
        String h = compact(line).toLowerCase(Locale.ROOT);
        boolean date = h.contains("日期") || h.contains("时间") || h.contains("交易日");
        boolean money = h.contains("金额") || h.contains("amount");
        return date && money;
    }

    static List<String> slim(List<String> lines) {
        if (lines.isEmpty()) {
            return lines;
        }
        String[] headers = lines.get(0).split("\t", -1);
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < headers.length; i++) {
            String h = compact(headers[i]);
            if (h.contains("交易单号") || h.contains("商户单号") || h.equals("单号")
                    || h.contains("订单号") || h.contains("流水号")) {
                continue;
            }
            keep.add(i);
        }
        if (keep.size() == headers.length) {
            return lines;
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            String[] cells = line.split("\t", -1);
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < keep.size(); k++) {
                if (k > 0) {
                    sb.append('\t');
                }
                int idx = keep.get(k);
                if (idx < cells.length) {
                    sb.append(cells[idx]);
                }
            }
            out.add(sb.toString());
        }
        return out;
    }

    static List<String> chunkLines(List<String> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        String header = lines.get(0);
        List<String> chunks = new ArrayList<>();
        if (lines.size() == 1) {
            chunks.add(header);
            return chunks;
        }
        for (int i = 1; i < lines.size(); i += CHUNK_ROWS) {
            StringBuilder sb = new StringBuilder();
            sb.append(header).append('\n');
            int end = Math.min(lines.size(), i + CHUNK_ROWS);
            for (int j = i; j < end; j++) {
                sb.append(lines.get(j)).append('\n');
            }
            chunks.add(sb.toString());
        }
        return chunks;
    }

    private static String decodeText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        return new String(bytes, Charset.forName("GBK"));
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }
}

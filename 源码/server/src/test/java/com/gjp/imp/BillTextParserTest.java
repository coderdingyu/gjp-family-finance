package com.gjp.imp;

import com.gjp.dify.DifyClient;
import com.gjp.dify.DifyParseResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillTextParserTest {

    @Test
    void extractBillRows() {
        String tsv = "日期\t类型\t分类\t金额\t商家\n"
                + "2026-08-01\t支出\t正餐\t128.5\t海底捞\n"
                + "2026-08-03\t收入\t基本工资\t12000\t\n";
        DifyParseResult r = BillTextParser.parse(tsv);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        Map<String, Object> first = r.getRecords().get(0);
        assertEquals(2, first.get("type"));
        assertEquals(new BigDecimal("128.5"), first.get("amount"));
        assertEquals("海底捞", first.get("merchant"));
    }

    @Test
    void rejectUnrelatedTable() {
        DifyParseResult r = BillTextParser.parse("姓名,学号,成绩\n张三,1,88\n");
        assertFalse(r.isRelevant());
    }

    @Test
    void parseDateAndPay() {
        assertEquals(LocalDate.of(2026, 8, 1), BillTextParser.parseDate("2026/08/01 12:00:00"));
        assertEquals("微信", BillTextParser.normalizePay("微信支付"));
        assertEquals(new BigDecimal("18.00"), BillTextParser.parseAmount("¥18.00"));
    }

    @Test
    void parseWeChatExport() {
        String tsv = "交易时间\t交易类型\t交易对方\t商品\t收/支\t金额(元)\t支付方式\t当前状态\t备注\n"
                + "2026-09-02 12:01:22\t商户消费\t聚惠时光超市福雷德店\t总部\t支出\t¥10.00\t零钱通\t支付成功\t/\n"
                + "2026-09-01 20:42:27\t转账\t姚天凯\t转账备注:微信转账\t收入\t¥35.00\t/\t已转入零钱通\t/\n"
                + "2026-08-20 10:00:00\t零钱通转出\t零钱通\t/\t/\t¥200.00\t零钱通\t提现已到账\t/\n";
        DifyParseResult r = BillTextParser.parse(tsv);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        assertEquals(2, r.getRecords().get(0).get("type"));
        assertEquals(new BigDecimal("10.00"), r.getRecords().get(0).get("amount"));
        assertEquals("聚惠时光超市福雷德店", r.getRecords().get(0).get("merchant"));
        assertEquals("微信", r.getRecords().get(0).get("payMethod"));
        assertEquals(1, r.getRecords().get(1).get("type"));
        assertEquals("家庭买菜", BillCategoryHints.guess(2, "聚惠时光超市福雷德店", "总部"));
    }

    @Test
    void wechatPreambleIsNotHeader() {
        List<String> lines = List.of(
                "微信支付账单明细",
                "共187笔记录",
                "交易时间\t交易类型\t交易对方\t商品\t收/支\t金额(元)\t支付方式\t当前状态\t交易单号\t商户单号\t备注",
                "2026-09-02 12:01:22\t商户消费\t超市\t买菜\t支出\t10\t零钱通\t支付成功\t4200\t8362\t/"
        );
        List<String> slim = ExcelTextExtractor.slim(ExcelTextExtractor.dropPreamble(lines));
        assertTrue(slim.get(0).startsWith("交易时间"));
        assertFalse(slim.get(0).contains("交易单号"));
        assertEquals(2, slim.size());
        List<String> chunks = ExcelTextExtractor.chunkLines(slim);
        assertEquals(1, chunks.size());
        assertTrue(BillTextParser.parse(chunks.get(0)).isRelevant());
    }

    @Test
    void parseWeChatProofPdfText() {
        String text = """
                微信支付交易明细证明
                交易时间
                2026-09-02
                12:01:22
                商户消费
                支出
                零钱通
                10.00
                测试超市
                福雷德店
                83620260902930534
                2026-09-01
                20:42:27
                转账
                收入
                /
                35.00
                同学甲
                /
                2026-08-27
                16:32:49
                零钱通转出
                其他
                零钱通
                588.35
                /
                /
                """;
        assertTrue(WeChatPdfParser.looksLike(text));
        DifyParseResult r = WeChatPdfParser.parse(text);
        assertTrue(r.isRelevant());
        assertEquals(2, r.getRecords().size());
        assertEquals("测试超市福雷德店", r.getRecords().get(0).get("merchant"));
        assertEquals("微信", r.getRecords().get(0).get("payMethod"));
        assertEquals(1, r.getRecords().get(1).get("type"));
    }

    @Test
    void parseWeChatProofCombinedLine() {
        String text = """
                微信支付交易明细证明
                2026-09-02
                12:01:22
                商户消费 支出 零钱通 10.00 测试超市
                福雷德店
                83620260902930534
                2026-09-01
                20:42:27
                转账 收入 / 35.00 同学甲
                /
                """;
        DifyParseResult r = WeChatPdfParser.parse(text);
        assertEquals(2, r.getRecords().size());
        assertEquals("测试超市福雷德店", r.getRecords().get(0).get("merchant"));
        assertEquals("微信", r.getRecords().get(0).get("payMethod"));
        assertEquals(1, r.getRecords().get(1).get("type"));
    }

    @Test
    void parseWeChatProofWrappedType() {
        String text = """
                微信支付交易明细证明
                2026-08-31
                09:14:42
                测试商户-退
                款
                收入 零钱通 83.00 测试商户
                83620260831930534
                9306
                2026-08-29
                15:30:47
                扫二维码付
                款
                支出 零钱 10.00 校园食堂
                10001073012026082
                2026-08-27
                16:32:49
                零钱通转出-
                到零钱
                其他 零钱通 588.35 / /
                4200000000000000
                """;
        DifyParseResult r = WeChatPdfParser.parse(text);
        assertEquals(2, r.getRecords().size());
        assertEquals(1, r.getRecords().get(0).get("type"));
        assertEquals(new BigDecimal("83.00"), r.getRecords().get(0).get("amount"));
        assertEquals("测试商户", r.getRecords().get(0).get("merchant"));
        assertEquals(2, r.getRecords().get(1).get("type"));
        assertEquals("校园食堂", r.getRecords().get(1).get("merchant"));
    }

    @Test
    void extractJsonFromFence() {
        String raw = "好的，结果如下：\n```json\n{\"relevant\":true,\"records\":[]}\n```\n";
        assertEquals("{\"relevant\":true,\"records\":[]}", DifyClient.extractJson(raw));
    }
}
